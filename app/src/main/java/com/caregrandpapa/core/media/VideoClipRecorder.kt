package com.caregrandpapa.core.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 15초 영상 녹화(소리 포함). 전면/후면을 골라 찍는다.
 *
 * CameraX 를 쓰는 이유는 미리보기 없이 녹화만 하는 구성이 Camera2 + MediaRecorder 조합보다
 * 훨씬 단순하고 기기별 예외가 적기 때문이다. 대신 `bindToLifecycle` 이 LifecycleOwner 를
 * 요구하므로, 서비스를 [androidx.lifecycle.LifecycleService] 로 만들어 그걸 넘긴다.
 *
 * 화질은 SD(480p) 로 낮춰 잡는다. 환자 상태와 주변을 확인하는 용도라 충분하다.
 *
 * 비트레이트도 명시적으로 제한한다. 기기의 CamcorderProfile 기본값을 그대로 쓰면
 * (갤럭시 S24 실측) 480p 인데도 3.6Mbps 가 나와 15초에 6.8MB 가 된다. 확인용 영상에
 * 그만한 화질은 필요 없고, 환자 폰의 데이터와 업로드 시간, 간병인 폰 용량만 잡아먹는다.
 * [TARGET_BITRATE] 로 제한하면 15초에 약 2.8MB 다.
 *
 * 인스턴스는 녹화 한 번에 하나씩 쓴다(내부 상태를 들고 있다).
 */
class VideoClipRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    /**
     * 녹화 종료 신호.
     *
     * `stop()` 을 부른 뒤 Finalize 이벤트가 와야 파일이 완성된다. 즉시 실패하는 경우
     * 이벤트가 [awaitFinalize] 보다 먼저 도착할 수 있으므로, 단순 플래그가 아니라
     * 결과(성공/예외)를 담아 두는 Deferred 로 받는다.
     */
    private val finalizeSignal = CompletableDeferred<Unit>()

    /**
     * [output] 에 [durationMs] 만큼 녹화한다.
     *
     * @param front true 면 전면 카메라
     * @throws Exception 카메라를 열 수 없거나 녹화가 실패한 경우
     */
    suspend fun record(output: File, front: Boolean, durationMs: Long) {
        output.parentFile?.mkdirs()
        output.delete() // FileOutputOptions 는 기존 파일에 덮어쓰지 않는다

        val provider = ProcessCameraProvider.getInstance(context).awaitResult()
        val selector = if (front) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.SD,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD),
                ),
            )
            .setTargetVideoEncodingBitRate(TARGET_BITRATE)
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)

        // 카메라 바인딩은 메인 스레드에서만 가능하다.
        withContext(Dispatchers.Main) {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, videoCapture)
        }

        var recording: Recording? = null
        try {
            recording = startRecording(videoCapture, output)
            delay(durationMs)
            recording.stop()
            awaitFinalize(output)
        } finally {
            runCatching { recording?.close() }
            // 코루틴이 이미 취소됐어도 카메라는 반드시 해제해야 LED/센서가 꺼진다.
            withContext(NonCancellable + Dispatchers.Main) { runCatching { provider.unbindAll() } }
        }
    }

    @SuppressLint("MissingPermission") // 아래에서 직접 확인한 뒤에만 오디오를 켠다
    private fun startRecording(videoCapture: VideoCapture<Recorder>, output: File): Recording {
        val options = FileOutputOptions.Builder(output).build()
        val executor = ContextCompat.getMainExecutor(context)

        var pending = videoCapture.output.prepareRecording(context, options)
        // 소리까지 담아야 상황 파악이 된다. 권한이 없으면 영상만 담는다.
        if (hasAudioPermission()) {
            pending = pending.withAudioEnabled()
        } else {
            Log.w(TAG, "마이크 권한 없음 → 소리 없이 녹화")
        }

        return pending.start(executor) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) {
                    Log.e(TAG, "녹화 실패 코드 ${event.error}", event.cause)
                    finalizeSignal.completeExceptionally(
                        event.cause ?: IllegalStateException("녹화 실패(코드 ${event.error})"),
                    )
                } else {
                    finalizeSignal.complete(Unit)
                }
            }
        }
    }

    /**
     * Finalize 이벤트를 기다린다.
     *
     * 실패로 끝났으면 예외가 그대로 올라온다. 이벤트가 아예 오지 않는 기기도 있어,
     * 시간이 지났는데 파일이 정상 크기로 남아 있으면 성공으로 인정한다.
     */
    private suspend fun awaitFinalize(output: File) {
        val completed = withTimeoutOrNull(FINALIZE_TIMEOUT_MS) { finalizeSignal.await() }
        if (completed == null) {
            if (!output.exists() || output.length() == 0L) {
                error("녹화 마무리가 시간 내에 끝나지 않았습니다")
            }
            Log.w(TAG, "Finalize 이벤트 지연 — 파일은 생성됨")
        }
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "VideoClipRecorder"
        const val FINALIZE_TIMEOUT_MS = 15_000L

        /** 480p 확인용으로 충분한 비트레이트. 15초에 약 2.8MB. */
        const val TARGET_BITRATE = 1_500_000
    }
}

/** `ListenableFuture` 를 코루틴에서 기다린다. CameraX 초기화가 이 형태를 쓴다. */
private suspend fun <T> ListenableFuture<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            Runnable::run,
        )
        continuation.invokeOnCancellation { cancel(false) }
    }
