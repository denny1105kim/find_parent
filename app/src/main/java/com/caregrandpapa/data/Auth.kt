package com.caregrandpapa.data

import android.util.Log
import com.caregrandpapa.core.util.suspendResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Firebase 익명 인증 게이트.
 *
 * Firestore 보안 규칙이 `request.auth != null` 과 문서에 기록된 UID 를 요구하므로,
 * 어떤 읽기/쓰기보다 **먼저** 로그인이 끝나 있어야 한다.
 *
 * 익명 계정은 앱을 지우거나 데이터를 초기화하지 않는 한 유지된다. 계정이 바뀌면
 * 기존 페어링의 소유권을 잃으므로([PairRepository]), 재페어링이 필요하다.
 */
object Auth {

    private const val TAG = "Auth"

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val mutex = Mutex()

    private val _uid = MutableStateFlow(auth.currentUser?.uid)

    /** 로그인된 UID. 아직 로그인 전이면 null. */
    val uid: StateFlow<String?> = _uid

    /** 마지막 로그인 시도의 실패 메시지(UI 안내용). 성공하면 null. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    /**
     * 익명 로그인을 보장한다. 이미 로그인되어 있으면 즉시 UID 를 돌려준다.
     * 동시에 여러 곳에서 불러도 로그인은 한 번만 수행된다.
     *
     * @throws Exception 네트워크 오류 등으로 로그인에 실패한 경우
     */
    suspend fun awaitUid(): String = mutex.withLock {
        auth.currentUser?.uid?.let {
            _uid.value = it
            _lastError.value = null
            return@withLock it
        }
        try {
            val user = auth.signInAnonymously().await().user
                ?: error("익명 로그인 결과에 사용자가 없습니다")
            Log.d(TAG, "익명 로그인 완료")
            _uid.value = user.uid
            _lastError.value = null
            user.uid
        } catch (e: Exception) {
            Log.e(TAG, "익명 로그인 실패: ${e.message}", e)
            _lastError.value = e.message ?: "알 수 없는 오류"
            throw e
        }
    }

    /** 예외를 던지지 않는 버전. 실패하면 null. */
    suspend fun awaitUidOrNull(): String? = suspendResult { awaitUid() }.getOrNull()

    /** 현재 UID(로그인 전이면 null). 규칙 검사용 비교에만 쓴다. */
    fun currentUid(): String? = _uid.value
}
