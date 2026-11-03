package com.caregrandpapa

import com.caregrandpapa.app.ServiceSetup
import android.app.Application
import com.caregrandpapa.data.Auth
import com.caregrandpapa.core.util.AppScope
import com.kakao.vectormap.KakaoMapSdk
import kotlinx.coroutines.launch

/**
 * 앱 전역 초기화.
 * - Firebase App Check (다른 Firebase 사용보다 먼저 설치해야 토큰이 첨부된다)
 * - 카카오맵 SDK (네이티브 앱 키는 secrets.properties → BuildConfig)
 * - Firebase 익명 로그인 (Firestore 보안 규칙이 UID 를 요구한다)
 *
 * 로그인은 여기서 미리 걸어 둔다. 서비스가 부팅 직후 깨어나는 경로에서도
 * 첫 Firestore 쓰기 전에 UID 가 준비되어 있어야 한다.
 */
class CareGrandpapaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!ServiceSetup.isConfigured()) return
        AppCheckInstaller.install(this)
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        AppScope.launch { Auth.awaitUidOrNull() }
    }
}
