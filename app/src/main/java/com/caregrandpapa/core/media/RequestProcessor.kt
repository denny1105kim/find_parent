package com.caregrandpapa.core.media

import android.Manifest
import android.content.Context
import android.util.Log
import com.caregrandpapa.data.ClipStorage
import com.caregrandpapa.data.LocationFetcher
import com.caregrandpapa.data.LocationRepository
import com.caregrandpapa.data.MediaRequest
import com.caregrandpapa.data.RequestRepository
import com.caregrandpapa.data.RequestStatus
import com.caregrandpapa.data.RequestType
import com.caregrandpapa.core.map.GeoPoint
import com.caregrandpapa.core.service.MonitoringState
import com.caregrandpapa.core.service.PermissionHelpers
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * 환자 폰에서 간병인의 요청을 하나씩 처리한다.
 *
 * `pairs/{pairId}/requests` 의 pending 문서를 구독하다가, 들어온 순서대로
 * 녹음 / 녹화 / 위치 측정을 수행하고 결과를 같은 문서에 써 넣는다.
 *
 * **순차 처리**가 중요하다. 카메라와 마이크는 동시에 두 작업을 못 하므로, 간병인이 버튼을
 * 연달아 눌러도 [mutex] 로 한 건씩 끝낸다.
 */
class RequestProcessor(
    private val context: Context,
    private val pairId: String,
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** 이미 집어간 요청. 스냅샷이 다시 와도 두 번 처리하지 않는다. */
    private val handled = mutableSetOf<String>()

    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            RequestRepository.observePending(pairId).collect { pending ->
                pending.forEach { request ->
                    if (handled.add(request.id)) {
                        // 각 요청을 순차적으로. mutex 안에서 처리하므로 겹치지 않는다.
                        handle(request)
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun handle(request: MediaRequest) = mutex.withLock {
        Log.d(TAG, "요청 처리 시작: ${request.type.wireName} (${request.id})")
        MonitoringState.setBusyLabel(busyLabelFor(request.type))
        try {
            val blocked = blockedReason(request.type)
            if (blocked != null) {
                RequestRepository.setFailed(pairId, request.id, blocked)
                return@withLock
            }
            RequestRepository.setStatus(pairId, request.id, RequestStatus.RUNNING)

            when (request.type) {
                RequestType.LOCATION -> handleLocation(request)
                RequestType.AUDIO -> handleClip(request, video = false, front = false)
                RequestType.VIDEO_FRONT -> handleClip(request, video = true, front = true)
                RequestType.VIDEO_BACK -> handleClip(request, video = true, front = false)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 취소는 실패가 아니다. 그대로 전파해 협조적 취소를 지킨다.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "요청 처리 실패(${request.id}): ${e.message}", e)
            RequestRepository.setFailed(
                pairId,
                request.id,
                e.message ?: "알 수 없는 오류로 실패했습니다",
            )
        } finally {
            MonitoringState.setBusyLabel(null)
        }
    }

    private suspend fun handleLocation(request: MediaRequest) {
        val location = LocationFetcher.fetch(context)
        if (location == null) {
            RequestRepository.setFailed(pairId, request.id, "위치를 확인할 수 없습니다")
            return
        }
        val point = GeoPoint(location.latitude, location.longitude)
        // 요청 문서와 "마지막으로 알려진 위치" 문서를 함께 갱신한다.
        // 후자가 있어야 간병인이 앱을 열자마자 지도에 뭔가 보인다.
        LocationRepository.publish(pairId, point, location.accuracy)
        RequestRepository.setStatus(
            pairId,
            request.id,
            RequestStatus.READY,
            mapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "accuracy" to location.accuracy.toDouble(),
            ),
        )
    }

    private suspend fun handleClip(request: MediaRequest, video: Boolean, front: Boolean) {
        val extension = request.type.fileExtension ?: return
        val temp = File(context.cacheDir, "clip-${request.id}.$extension")
        try {
            if (video) {
                VideoClipRecorder(context, lifecycleOwner)
                    .record(temp, front = front, durationMs = MediaRequest.CLIP_DURATION_MS)
            } else {
                AudioClipRecorder(context)
                    .record(temp, durationMs = MediaRequest.CLIP_DURATION_MS)
            }

            if (!temp.exists() || temp.length() == 0L) {
                RequestRepository.setFailed(pairId, request.id, "녹음 파일이 만들어지지 않았습니다")
                return
            }

            RequestRepository.setStatus(pairId, request.id, RequestStatus.UPLOADING)
            val path = ClipStorage.pathFor(pairId, request.id, extension)
            val size = ClipStorage.upload(path, temp, ClipStorage.contentTypeFor(request.type))

            RequestRepository.setStatus(
                pairId,
                request.id,
                RequestStatus.READY,
                mapOf("storagePath" to path, "sizeBytes" to size),
            )
            Log.d(TAG, "업로드 완료: $path ($size bytes)")
        } finally {
            // 서버로 올렸으면 환자 폰에는 남기지 않는다.
            temp.delete()
        }
    }

    /**
     * 지금 이 요청을 처리할 수 없는 이유. 처리 가능하면 null.
     *
     * 문장은 간병인 화면에 그대로 보이므로, 무엇을 해야 하는지 알 수 있게 쓴다.
     */
    private fun blockedReason(type: RequestType): String? {
        // 부팅 직후 등에는 마이크/카메라 타입 Foreground Service 승격이 막혀,
        // 권한이 있어도 녹음·녹화를 시작하는 순간 실패한다. 미리 걸러 안내한다.
        if (type.isClip && !MonitoringState.mediaReady.value) {
            return "환자 폰에서 앱을 한 번 열어야 소리·영상을 보낼 수 있습니다"
        }
        return missingPermissionMessage(type)
    }

    /** 권한이 없으면 간병인이 읽을 수 있는 문장을 돌려준다. */
    private fun missingPermissionMessage(type: RequestType): String? = when (type) {
        RequestType.LOCATION ->
            if (PermissionHelpers.isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                null
            } else {
                "환자 폰에 위치 권한이 없습니다"
            }

        RequestType.AUDIO ->
            if (PermissionHelpers.isGranted(context, Manifest.permission.RECORD_AUDIO)) {
                null
            } else {
                "환자 폰에 마이크 권한이 없습니다"
            }

        RequestType.VIDEO_FRONT, RequestType.VIDEO_BACK ->
            if (PermissionHelpers.isGranted(context, Manifest.permission.CAMERA)) {
                null
            } else {
                "환자 폰에 카메라 권한이 없습니다"
            }
    }

    private fun busyLabelFor(type: RequestType): String = when (type) {
        RequestType.AUDIO -> "소리를 녹음하고 있습니다"
        RequestType.VIDEO_FRONT -> "전면 카메라로 녹화하고 있습니다"
        RequestType.VIDEO_BACK -> "후면 카메라로 녹화하고 있습니다"
        RequestType.LOCATION -> "위치를 확인하고 있습니다"
    }

    private companion object {
        const val TAG = "RequestProcessor"
    }
}
