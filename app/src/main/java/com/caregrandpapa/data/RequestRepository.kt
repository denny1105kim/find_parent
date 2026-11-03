package com.caregrandpapa.data

import android.util.Log
import com.caregrandpapa.core.util.suspendResult
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * `pairs/{pairId}/requests/{requestId}` 를 다룬다.
 *
 * 간병인이 문서를 만들면(pending) 환자 폰이 집어가 상태를 갱신하고, 결과 파일 경로나
 * 위치 좌표를 같은 문서에 써 넣는다. 실시간 연결이 없으므로 이 문서가 통신 수단 전부다.
 *
 * `createdAt` 은 **간병인 폰의 시계** 기준 epoch millis 다. 서버 타임스탬프를 쓰면 방금 만든
 * 문서의 로컬 스냅샷에서 값이 null 이라 정렬과 타임아웃 판정이 흔들린다. 타임아웃을 재는
 * 쪽도 간병인 폰이므로 같은 시계를 쓰는 편이 정확하다.
 */
object RequestRepository {

    private const val TAG = "RequestRepository"
    private const val PAIRS = "pairs"
    private const val REQUESTS = "requests"

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun collection(pairId: String) =
        db.collection(PAIRS).document(pairId).collection(REQUESTS)

    /** 간병인: 새 요청을 만든다. 반환값은 요청 ID. */
    suspend fun create(pairId: String, type: RequestType): String {
        Auth.awaitUid()
        val id = UUID.randomUUID().toString()
        collection(pairId).document(id).set(
            mapOf(
                "type" to type.wireName,
                "status" to RequestStatus.PENDING.wireName,
                "createdAt" to System.currentTimeMillis(),
            ),
        ).await()
        Log.d(TAG, "요청 생성: ${type.wireName} ($id)")
        return id
    }

    /** 간병인: 최근 요청 목록(최신순). */
    fun observeRecent(pairId: String, limit: Long = 60): Flow<List<MediaRequest>> = callbackFlow {
        Auth.awaitUidOrNull()
        val registration = collection(pairId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "요청 목록 구독 오류: ${error.message}", error)
                    return@addSnapshotListener
                }
                trySend(snapshots?.documents?.mapNotNull { it.toMediaRequest() } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    /**
     * 환자: 아직 집어가지 않은 요청.
     *
     * 상태 하나만 걸러 복합 색인이 필요 없게 했다. 정렬은 받아서 직접 한다.
     */
    fun observePending(pairId: String): Flow<List<MediaRequest>> = callbackFlow {
        Auth.awaitUidOrNull()
        val registration = collection(pairId)
            .whereEqualTo("status", RequestStatus.PENDING.wireName)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "대기 요청 구독 오류: ${error.message}", error)
                    return@addSnapshotListener
                }
                val pending = snapshots?.documents
                    ?.mapNotNull { it.toMediaRequest() }
                    ?.sortedBy { it.createdAtMillis ?: 0L }
                    ?: emptyList()
                trySend(pending)
            }
        awaitClose { registration.remove() }
    }

    /** 환자: 진행 상태만 갱신한다. */
    suspend fun setStatus(
        pairId: String,
        requestId: String,
        status: RequestStatus,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        suspendResult {
            Auth.awaitUid()
            collection(pairId).document(requestId)
                .update(extras + ("status" to status.wireName))
                .await()
        }.onFailure { Log.w(TAG, "상태 갱신 실패($status): ${it.message}") }
    }

    /** 환자: 실패로 마감한다. */
    suspend fun setFailed(pairId: String, requestId: String, message: String) {
        setStatus(pairId, requestId, RequestStatus.FAILED, mapOf("errorMessage" to message))
    }

    /** 간병인: 내려받아 로컬에 보관을 마쳤다는 표시. */
    suspend fun markDelivered(pairId: String, requestId: String) {
        suspendResult {
            Auth.awaitUid()
            collection(pairId).document(requestId)
                .update("status", RequestStatus.DELIVERED.wireName)
                .await()
        }.onFailure { Log.w(TAG, "delivered 표시 실패: ${it.message}") }
    }

    /** 간병인: 요청 문서를 지운다. */
    suspend fun delete(pairId: String, requestId: String) {
        suspendResult {
            Auth.awaitUid()
            collection(pairId).document(requestId).delete().await()
        }.onFailure { Log.w(TAG, "요청 삭제 실패: ${it.message}") }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toMediaRequest(): MediaRequest? {
        val type = RequestType.fromWire(getString("type")) ?: return null
        val status = RequestStatus.fromWire(getString("status")) ?: return null
        return MediaRequest(
            id = id,
            type = type,
            status = status,
            createdAtMillis = getLong("createdAt"),
            storagePath = getString("storagePath"),
            sizeBytes = getLong("sizeBytes"),
            latitude = getDouble("lat"),
            longitude = getDouble("lng"),
            accuracy = getDouble("accuracy")?.toFloat(),
            errorMessage = getString("errorMessage"),
        )
    }
}
