package com.caregrandpapa.data

import android.util.Log
import com.caregrandpapa.core.util.suspendResult
import com.caregrandpapa.core.map.GeoPoint
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** 간병인이 보는 환자의 위치 스냅샷. */
data class PatientLocation(
    val point: GeoPoint,
    val accuracy: Float,
    /** 서버 기준 마지막 갱신 시각. 신선도 판정에 쓴다. null 이면 아직 서버 확정 전. */
    val updatedAtMillis: Long?,
)

/**
 * 환자의 실시간 위치를 Firestore 로 주고받는다.
 *
 * 문서 경로: `locations/{pairId}`
 *  - 환자 앱: [publish] 로 자신의 위치를 계속 덮어쓴다.
 *  - 간병인 앱: [observe] 로 실시간 구독한다.
 *
 * 보안 규칙상 쓰기는 페어의 환자 UID 만, 읽기는 페어 멤버만 가능하다(firestore.rules).
 * 따라서 모든 접근은 익명 로그인([Auth])이 끝난 뒤에 이뤄져야 한다.
 */
object LocationRepository {

    private const val TAG = "LocationRepository"
    private const val COLLECTION = "locations"

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** 환자 앱: 현재 위치를 업로드한다(문서 통째로 덮어씀). */
    suspend fun publish(pairId: String, point: GeoPoint, accuracy: Float) {
        suspendResult {
            Auth.awaitUid()
            db.collection(COLLECTION).document(pairId).set(
                mapOf(
                    "lat" to point.latitude,
                    "lng" to point.longitude,
                    "accuracy" to accuracy,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }.onFailure { Log.e(TAG, "publish 실패: ${it.message}", it) }
    }

    /** 간병인 앱: 위치 변화를 실시간 구독한다. 값이 없으면 null 을 방출. */
    fun observe(pairId: String): Flow<PatientLocation?> = callbackFlow {
        // 규칙 평가에 UID 가 필요하므로 리스너 등록 전에 로그인을 보장한다.
        Auth.awaitUidOrNull()

        val registration = db.collection(COLLECTION).document(pairId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observe 오류: ${error.message}", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val lat = snapshot.getDouble("lat")
                val lng = snapshot.getDouble("lng")
                if (lat == null || lng == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(
                    PatientLocation(
                        point = GeoPoint(lat, lng),
                        accuracy = snapshot.getDouble("accuracy")?.toFloat() ?: 0f,
                        updatedAtMillis = snapshot.getTimestamp("updatedAt")?.toDate()?.time,
                    ),
                )
            }
        awaitClose { registration.remove() }
    }
}
