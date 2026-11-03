package com.caregrandpapa.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 위치를 **한 번만** 측정한다.
 *
 * 예전에는 3초 간격으로 계속 위치를 올려 환자 폰 배터리를 크게 깎았다. 지금은 간병인이
 * 새로고침을 눌렀을 때만 이 함수가 한 번 돌아간다.
 *
 * GPS 가 잡히지 않는 실내 등에서 오래 붙잡지 않도록 시간 제한을 두고, 실패하면 마지막으로
 * 알려진 위치라도 돌려준다(그 값의 신선도는 간병인 화면이 표시한다).
 */
object LocationFetcher {

    private const val TAG = "LocationFetcher"
    private const val TIMEOUT_MS = 20_000L

    @SuppressLint("MissingPermission") // 호출 측에서 권한 확인 후 호출
    suspend fun fetch(context: Context): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                client.getCurrentLocation(
                    CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(MAX_AGE_MS)
                        .build(),
                    null,
                ).await()
            }.onFailure { Log.w(TAG, "현재 위치 측정 실패: ${it.message}") }.getOrNull()
        }
        if (fresh != null) return fresh

        Log.w(TAG, "현재 위치를 못 잡음 → 마지막 위치로 대체")
        return runCatching { client.lastLocation.await() }
            .onFailure { Log.w(TAG, "마지막 위치 조회 실패: ${it.message}") }
            .getOrNull()
    }

    /** 이 시간 안에 측정된 값이 이미 있으면 그대로 쓴다(불필요한 GPS 기동 방지). */
    private const val MAX_AGE_MS = 10_000L
}
