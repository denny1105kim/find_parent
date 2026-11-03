package com.caregrandpapa

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * 릴리스 빌드용 App Check 설치자.
 *
 * Play Integrity 로 "진짜 우리 앱(정상 서명 + 정상 설치)"의 요청만 Firebase 백엔드를
 * 통과하게 한다. 콘솔에서 App Check 를 enforce 로 켜야 실제 차단이 적용된다.
 */
object AppCheckInstaller {
    fun install(context: Context) {
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
