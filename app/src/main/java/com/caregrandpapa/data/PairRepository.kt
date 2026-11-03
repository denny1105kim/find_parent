package com.caregrandpapa.data

import android.util.Log
import com.caregrandpapa.core.util.suspendResult
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** `pairs/{pairId}` 문서의 내용. 이 문서가 접근 권한의 유일한 원천이다. */
data class PairInfo(
    val pairId: String,
    val guardianUid: String,
    val patientUid: String?,
    /** 간병인이 위치 화면을 보고 있는 시각(epoch millis). 지나면 환자는 저전력 모드로 내린다. */
    val watchingUntilMillis: Long?,
    /** 환자 폰이 마이크/카메라 대기까지 올릴 수 있었는지. false 면 위치만 가능. */
    val mediaReady: Boolean,
    val checkIn: String? = null,
    val checkInAtMillis: Long? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val lastSeenAtMillis: Long? = null,
) {
    val isPaired: Boolean get() = patientUid != null
}

/**
 * 환자 폰이 보는 페어의 현재 상태.
 *
 * "문서가 없다"와 "지금 확인할 수 없다"를 반드시 구분해야 한다. 오프라인이나 일시적 오류를
 * 해제로 오인하면 잘 쓰던 페어링을 스스로 지워 버린다.
 */
sealed interface PairStatus {
    /** 정상 연결 상태. */
    data class Active(val info: PairInfo) : PairStatus

    /** 문서가 확실히 없다 = 간병인이 연결을 해제했다. */
    data object Removed : PairStatus

    /** 이 폰이 더 이상 이 페어의 환자가 아니다(앱 데이터 초기화 등으로 신원이 바뀜). */
    data class NotMine(val registeredPatientUid: String?) : PairStatus

    /** 네트워크·권한 문제로 지금은 알 수 없다. 아무 조치도 하지 않는다. */
    data class Unavailable(val message: String) : PairStatus
}

/** 환자 폰이 QR 을 찍고 페어에 합류할 때의 결과. */
sealed interface ClaimResult {
    data object Success : ClaimResult

    /** 이미 이 폰이 등록된 페어. 그대로 쓰면 된다. */
    data object AlreadyMine : ClaimResult

    /** 다른 폰이 먼저 등록했다. 간병인이 QR 을 새로 만들어야 한다. */
    data object TakenByAnother : ClaimResult

    /** QR 이 가리키는 페어가 없다(간병인이 해제했거나 잘못된 QR). */
    data object NotFound : ClaimResult

    data class Failed(val message: String) : ClaimResult
}

/**
 * 페어링 문서(`pairs/{pairId}`)를 다룬다.
 *
 * 흐름:
 *  1. 간병인 폰이 [createPair] 로 UUID 페어를 만들고 QR 로 띄운다.
 *  2. 환자 폰이 QR 을 찍고 동의한 뒤 [claim] 으로 자신을 등록한다.
 *  3. 이후 locations/sessions/tokens 접근은 모두 이 문서를 근거로 허용된다(firestore.rules).
 */
object PairRepository {

    private const val TAG = "PairRepository"
    private const val COLLECTION = "pairs"

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** 간병인 폰: 새 페어를 만들고 페어 ID 를 돌려준다. */
    suspend fun createPair(): String {
        val uid = Auth.awaitUid()
        val pairId = UUID.randomUUID().toString()
        db.collection(COLLECTION).document(pairId).set(
            mapOf(
                "guardianUid" to uid,
                "patientUid" to null,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        Log.d(TAG, "페어 생성: $pairId")
        return pairId
    }

    /**
     * 환자 폰: QR 로 받은 페어에 자신을 등록한다.
     *
     * 트랜잭션으로 처리해 두 폰이 동시에 같은 QR 을 찍는 경우를 막는다.
     * (보안 규칙에서도 `patientUid == null` 일 때만 허용하므로 이중으로 막힌다.)
     */
    suspend fun claim(pairId: String): ClaimResult {
        val uid = try {
            Auth.awaitUid()
        } catch (e: Exception) {
            return ClaimResult.Failed(e.message ?: "로그인 실패")
        }
        val ref = db.collection(COLLECTION).document(pairId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) return@runTransaction ClaimResult.NotFound
                when (val existing = snap.getString("patientUid")) {
                    null -> {
                        tx.update(
                            ref,
                            mapOf(
                                "patientUid" to uid,
                                "consentAcceptedAt" to FieldValue.serverTimestamp(),
                            ),
                        )
                        ClaimResult.Success
                    }
                    uid -> ClaimResult.AlreadyMine
                    else -> {
                        Log.w(TAG, "이미 다른 폰이 등록됨: $existing")
                        ClaimResult.TakenByAnother
                    }
                }
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "claim 실패: ${e.message}", e)
            ClaimResult.Failed(e.message ?: "알 수 없는 오류")
        }
    }

    /** 페어 상태를 실시간 구독한다. 문서가 없으면 null(해제됨). */
    fun observe(pairId: String): Flow<PairInfo?> = callbackFlow {
        Auth.awaitUidOrNull()
        val registration = db.collection(COLLECTION).document(pairId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observe 오류: ${error.message}", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toPairInfo(pairId))
            }
        awaitClose { registration.remove() }
    }

    /**
     * 환자 폰 전용: 페어가 아직 살아 있는지 지켜본다.
     *
     * [observe] 는 오류 때도 null 을 내보내므로 "해제됨" 판정에 쓸 수 없다.
     * 여기서는 스냅샷의 존재 여부와 오류를 분리해 [PairStatus] 로 알린다.
     */
    fun observeStatus(pairId: String, myUid: () -> String?): Flow<PairStatus> = callbackFlow {
        Auth.awaitUidOrNull()
        val registration = db.collection(COLLECTION).document(pairId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "페어 상태 확인 불가: ${error.message}")
                    trySend(PairStatus.Unavailable(error.message ?: "확인할 수 없습니다"))
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                if (!snapshot.exists() || snapshot.getBoolean("revoked") == true) {
                    trySend(PairStatus.Removed)
                    return@addSnapshotListener
                }
                val info = snapshot.toPairInfo(pairId)
                if (info == null) {
                    trySend(PairStatus.Unavailable("페어 정보를 읽을 수 없습니다"))
                    return@addSnapshotListener
                }
                val uid = myUid()
                // uid 를 아직 모르는 상태에서는 판단을 보류한다(로그인 완료 전).
                if (uid != null && info.patientUid != null && info.patientUid != uid) {
                    trySend(PairStatus.NotMine(info.patientUid))
                    return@addSnapshotListener
                }
                trySend(PairStatus.Active(info))
            }
        awaitClose { registration.remove() }
    }

    suspend fun get(pairId: String): PairInfo? = suspendResult {
        Auth.awaitUid()
        db.collection(COLLECTION).document(pairId).get().await().toPairInfo(pairId)
    }.onFailure { Log.e(TAG, "get 실패: ${it.message}", it) }.getOrNull()

    /**
     * 간병인 폰: "지금 위치를 보고 있다"는 신호를 남긴다.
     * 환자 폰은 이 값이 살아 있을 때만 고정확도 위치로 올려 배터리를 아낀다.
     */
    suspend fun setWatchingUntil(pairId: String, untilMillis: Long) {
        suspendResult {
            Auth.awaitUid()
            db.collection(COLLECTION).document(pairId)
                .update("watchingUntil", untilMillis).await()
        }.onFailure { Log.w(TAG, "watchingUntil 갱신 실패: ${it.message}") }
    }

    /** 환자 폰: 마이크/카메라 대기까지 올라갔는지를 간병인에게 알린다. */
    suspend fun setMediaReady(pairId: String, ready: Boolean) {
        suspendResult {
            Auth.awaitUid()
            db.collection(COLLECTION).document(pairId).update(
                mapOf(
                    "mediaReady" to ready,
                    "mediaReadyAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }.onFailure { Log.w(TAG, "mediaReady 갱신 실패: ${it.message}") }
    }

    /**
     * 간병인 폰: 환자 폰이 아직 찍지 않은 페어 문서를 버린다.
     *
     * QR 화면을 떠날 때 호출한다. 짝이 이미 맺혔다면 실수로 지우지 않도록 확인한 뒤 지운다.
     */
    suspend fun discardUnclaimed(pairId: String) {
        suspendResult {
            Auth.awaitUid()
            val info = get(pairId)
            if (info == null) return@suspendResult
            if (info.isPaired) {
                Log.w(TAG, "이미 짝이 맺힌 페어라 지우지 않음: $pairId")
                return@suspendResult
            }
            db.collection(COLLECTION).document(pairId).update("revoked", true).await()
            Log.d(TAG, "미사용 페어 정리: $pairId")
        }.onFailure { Log.w(TAG, "미사용 페어 정리 실패: ${it.message}") }
    }

    /** 양쪽 사용자: 먼저 권한을 취소하고 서버에서 잔존 자료를 정리한다. */
    suspend fun unpair(pairId: String) {
        Auth.awaitUid()
        // Revoke immediately; server cleanup retains a tombstone to prevent ID reuse.
        val ref = db.collection(COLLECTION).document(pairId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            if (snapshot.exists() && snapshot.getBoolean("revoked") != true) {
                transaction.update(ref, "revoked", true)
            }
        }.await()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toPairInfo(
        pairId: String,
    ): PairInfo? {
        if (!exists()) return null
        val guardianUid = getString("guardianUid") ?: return null
        return PairInfo(
            pairId = pairId,
            guardianUid = guardianUid,
            patientUid = getString("patientUid"),
            watchingUntilMillis = getLong("watchingUntil"),
            mediaReady = getBoolean("mediaReady") ?: false,
            checkIn = getString("checkIn"),
            checkInAtMillis = getTimestamp("checkInAt")?.toDate()?.time,
            batteryPercent = getLong("batteryPercent")?.toInt()?.takeIf { it in 0..100 },
            charging = getBoolean("charging") ?: false,
            lastSeenAtMillis = getTimestamp("lastSeenAt")?.toDate()?.time,
        )
    }
}
