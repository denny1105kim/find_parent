package com.caregrandpapa.core.messaging

import android.util.Log
import com.caregrandpapa.data.Auth
import com.caregrandpapa.data.Prefs
import com.caregrandpapa.core.service.MonitoringService
import com.caregrandpapa.core.util.AppScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * FCM 데이터 메시지를 받아 환자 폰의 요청 대기 서비스를 깨운다.
 *
 * 간병인(또는 Cloud Function)가 환자의 FCM 토큰으로 데이터 메시지를 보내면,
 * 앱이 종료되어 있어도 이 서비스가 깨어나 [MonitoringService] 를 시작한다.
 *
 * 토큰은 [uploadToken] 으로 Firestore `tokens/{pairId}` 에 저장되어,
 * Cloud Function 이 대상 토큰을 조회할 수 있게 한다. 보안 규칙상 이 문서는
 * 환자 본인만 쓸 수 있고 클라이언트는 읽을 수 없다(전송은 Admin SDK 가 한다).
 */
class WakeMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val pairId = Prefs(this).pairId ?: return
        if (message.data["type"] != "wake" || message.data["pairId"] != pairId) return
        Log.d(TAG, "wake 메시지 수신 → 서비스 시작")
        // 안드로이드 12+ 는 백그라운드에서 FGS 시작을 막을 수 있다(예외 창 밖/Doze).
        // 여기서 던지면 서비스 콜백이라 프로세스가 죽으므로 반드시 감싼다.
        runCatching { MonitoringService.start(this, pairId) }
            .onFailure { Log.w(TAG, "백그라운드 서비스 시작 실패(정상적 제한일 수 있음): ${it.message}") }
    }

    override fun onNewToken(token: String) {
        val pairId = Prefs(this).pairId ?: return
        uploadToken(pairId, token)
    }

    companion object {
        private const val TAG = "WakeMessagingService"

        /** 현재 FCM 토큰을 Firestore 에 저장한다(환자 폰에서 페어링 후 호출). */
        fun uploadToken(pairId: String, token: String) {
            AppScope.launch {
                runCatching {
                    Auth.awaitUid()
                    FirebaseFirestore.getInstance()
                        .collection("tokens").document(pairId)
                        .set(mapOf("token" to token))
                        .await()
                }.onFailure { Log.e(TAG, "토큰 저장 실패: ${it.message}", it) }
            }
        }

        /** 최신 토큰을 조회해 업로드. 환자 홈 진입 시 호출. */
        fun registerCurrentToken(pairId: String) {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> uploadToken(pairId, token) }
                .addOnFailureListener { Log.w(TAG, "토큰 조회 실패: ${it.message}") }
        }
    }
}
