package com.caregrandpapa.data

import android.content.Context
import com.caregrandpapa.data.model.Role

/**
 * 로컬 설정 저장소. 역할과 페어 ID 를 보관한다.
 *
 * 역할도 영속화한다. 그래야 환자 폰이 재부팅된 뒤에도 역할 선택 화면으로 돌아가지 않고,
 * [com.caregrandpapa.core.service.BootReceiver] 가 살려 둔 서비스와 UI 상태가 어긋나지 않는다.
 */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("care_grandpapa_prefs", Context.MODE_PRIVATE)

    /** 페어 ID(UUID). QR 로 주고받는다. 없으면 아직 페어링 전. */
    var pairId: String?
        get() = sp.getString(KEY_PAIR_ID, null)
        set(value) = sp.edit().putString(KEY_PAIR_ID, value).apply()

    /**
     * 간병인 폰이 만들어 두고 아직 환자 폰이 찍지 않은 페어 ID.
     *
     * 이걸 기억하지 않으면 QR 화면에 들어갈 때마다 새 페어 문서가 생기고, 버려진 문서가
     * Firestore 에 계속 쌓인다(정리 경로도 없다). 재진입 시 이 값을 재사용하고,
     * 역할을 바꿔 QR 화면을 떠날 때 문서를 지운다.
     */
    var pendingPairId: String?
        get() = sp.getString(KEY_PENDING_PAIR_ID, null)
        set(value) = sp.edit().putString(KEY_PENDING_PAIR_ID, value).apply()

    /** 이 폰의 역할. 없으면 아직 선택 전. */
    var role: Role?
        get() = sp.getString(KEY_ROLE, null)?.let { name ->
            Role.entries.firstOrNull { it.name == name }
        }
        set(value) = sp.edit().putString(KEY_ROLE, value?.name).apply()

    var sharingPaused: Boolean
        get() = sp.getBoolean("sharing_paused", false)
        set(value) = sp.edit().putBoolean("sharing_paused", value).apply()

    /** 역할·페어링을 모두 지운다(초기화). */
    fun clearAll() {
        sp.edit()
            .remove(KEY_PAIR_ID)
            .remove(KEY_PENDING_PAIR_ID)
            .remove(KEY_ROLE)
            .remove("sharing_paused")
            .apply()
    }

    companion object {
        private const val KEY_PAIR_ID = "pair_id"
        private const val KEY_PENDING_PAIR_ID = "pending_pair_id"
        private const val KEY_ROLE = "role"
    }
}
