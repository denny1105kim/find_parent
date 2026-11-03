package com.caregrandpapa.data

import android.content.Context
import android.util.Log
import com.caregrandpapa.core.util.AppScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 간병인 폰의 클립 동기화.
 *
 * 하는 일은 세 가지다.
 *  1. `ready` 가 된 요청의 파일을 내려받아 이 폰에 보관하고([ClipStore]),
 *     **서버 원본을 지운 뒤** 문서를 `delivered` 로 표시한다.
 *  2. 위치 요청은 파일이 없으므로 바로 `delivered` 로 넘긴다.
 *  3. 오래된 요청 문서와 (아직 못 받아간) 서버 파일을 정리한다.
 *
 * 화면마다 각자 돌리면 같은 파일을 여러 번 내려받게 되므로 앱 전체에 하나만 둔다.
 * 화면보다 오래 살아야 하니(다운로드 중 화면 이동) [AppScope] 에서 돌린다.
 */
object ClipSync {

    private const val TAG = "ClipSync"

    /** 받아가지 않은 요청과 서버 파일을 이 기간이 지나면 정리한다. */
    private const val STALE_AFTER_MS = 3L * 24 * 60 * 60 * 1000

    private val mutex = Mutex()
    private val inFlight = mutableSetOf<String>()

    private var job: Job? = null
    private var store: ClipStore? = null
    private var activePairId: String? = null

    private val _clips = MutableStateFlow<List<StoredClip>>(emptyList())

    /** 이 폰에 보관된 클립(최신순). */
    val clips: StateFlow<List<StoredClip>> = _clips

    private val _usedBytes = MutableStateFlow(0L)
    val usedBytes: StateFlow<Long> = _usedBytes

    fun start(context: Context, pairId: String) {
        if (job?.isActive == true && activePairId == pairId) return
        stop()

        val clipStore = ClipStore(context.applicationContext, pairId)
        store = clipStore
        activePairId = pairId
        refresh()

        job = AppScope.launch {
            RequestRepository.observeRecent(pairId).collect { requests ->
                requests.forEach { request ->
                    try {
                        process(pairId, request, clipStore)
                        pruneStale(pairId, listOf(request))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "기록 동기화 실패; 다음 갱신에서 재시도", e)
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        activePairId = null
        store = null
        _clips.value = emptyList()
        _usedBytes.value = 0L
    }

    /** 파일 시스템을 다시 읽어 목록을 갱신한다. */
    fun refresh() {
        val clipStore = store ?: return
        _clips.value = clipStore.list()
        _usedBytes.value = clipStore.totalBytes()
    }

    /** 보관된 클립을 지운다(간병인이 직접 삭제). */
    fun deleteClip(requestId: String) {
        store?.delete(requestId)
        refresh()
    }

    private suspend fun process(pairId: String, request: MediaRequest, clipStore: ClipStore) {
        if (request.status != RequestStatus.READY) return

        if (!request.type.isClip) {
            // 위치는 결과가 문서 안에 이미 들어 있다.
            RequestRepository.markDelivered(pairId, request.id)
            return
        }

        val path = request.storagePath ?: return

        // 이미 받아 둔 것이면 서버 정리만 마무리한다.
        if (clipStore.has(request.id)) {
            finishDelivery(pairId, request.id, path)
            return
        }

        val shouldStart = mutex.withLock { inFlight.add(request.id) }
        if (!shouldStart) return

        try {
            val temp = clipStore.tempFileFor(request.id)
            ClipStorage.download(path, temp)
            val saved = clipStore.save(
                requestId = request.id,
                type = request.type,
                createdAtMillis = request.createdAtMillis ?: System.currentTimeMillis(),
                downloaded = temp,
            )
            if (saved == null) {
                Log.e(TAG, "로컬 보관 실패: ${request.id}")
                return
            }
            if (store === clipStore) refresh()
            finishDelivery(pairId, request.id, path)
            Log.d(TAG, "클립 보관 완료: ${request.id} (${saved.sizeBytes} bytes)")
        } catch (e: kotlinx.coroutines.CancellationException) {
            clipStore.tempFileFor(request.id).delete()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "다운로드 실패(${request.id}): ${e.message}", e)
            clipStore.tempFileFor(request.id).delete()
        } finally {
            mutex.withLock { inFlight.remove(request.id) }
        }
    }

    /** 서버 원본을 지우고 문서를 delivered 로 표시한다. */
    private suspend fun finishDelivery(pairId: String, requestId: String, storagePath: String) {
        ClipStorage.delete(storagePath)
        RequestRepository.markDelivered(pairId, requestId)
    }

    /**
     * 오래된 요청 문서를 정리한다.
     *
     * 간병인이 며칠 앱을 열지 않으면 환자 폰이 올린 파일이 서버에 남는다. 클라우드에
     * 오래 두지 않는다는 원칙에 맞게, 못 받아간 파일도 이 시점에 지운다.
     */
    private suspend fun pruneStale(pairId: String, requests: List<MediaRequest>) {
        val now = System.currentTimeMillis()
        requests.forEach { request ->
            val created = request.createdAtMillis ?: return@forEach
            if (now - created < STALE_AFTER_MS) return@forEach
            if (request.id in inFlight) return@forEach

            request.storagePath
                ?.takeIf { request.status != RequestStatus.DELIVERED }
                ?.let { ClipStorage.delete(it) }
            RequestRepository.delete(pairId, request.id)
        }
    }
}
