package com.caregrandpapa

import android.content.Context
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * 디버그 빌드용 App Check 설치자.
 *
 * 실기기/에뮬레이터에서 개발할 때는 Play Integrity 검증이 통과되지 않으므로 **디버그 토큰**을 쓴다.
 * 앱을 처음 실행하면 Logcat 에 디버그 토큰이 찍힌다. 그 값을
 * Firebase 콘솔 → App Check → 앱 → [디버그 토큰 관리] 에 등록하면 디버그 빌드도 통과한다.
 */
object AppCheckInstaller {
    fun install(context: Context) {
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        Log.i("AppCheck", "디버그 App Check 공급자 설치됨 (Logcat 의 디버그 토큰을 콘솔에 등록하세요)")
    }
}
