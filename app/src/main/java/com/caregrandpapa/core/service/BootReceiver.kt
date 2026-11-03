package com.caregrandpapa.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.caregrandpapa.data.Prefs
import com.caregrandpapa.data.model.Role

/**
 * 재부팅 후, 이미 페어링된 환자 폰이라면 요청 대기 서비스를 자동으로 다시 시작한다.
 * 간병인 폰에서는 아무것도 하지 않는다.
 *
 * 참고: Android 14+ 에서는 부팅 직후 카메라/마이크 타입 FGS 시작이 막히므로,
 * [MonitoringService] 가 위치 전용으로 낮춰서라도 살리고 그 사실을 간병인에게 알린다.
 * 환자가 앱을 한 번 열면 미디어 대기까지 정상화된다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val prefs = Prefs(context)
        if (prefs.role != Role.PATIENT) return

        val pairId = prefs.pairId
        if (!pairId.isNullOrBlank()) {
            Log.d(TAG, "부팅 감지 → 요청 대기 서비스 재시작")
            MonitoringService.start(context, pairId)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
