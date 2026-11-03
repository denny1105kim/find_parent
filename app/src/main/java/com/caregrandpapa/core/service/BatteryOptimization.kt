package com.caregrandpapa.core.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * 배터리 최적화 예외 처리. 갤럭시(One UI)는 최적화가 공격적이라, 예외로 등록하지 않으면
 * 백그라운드 요청 대기 서비스가 도중에 종료될 수 있다.
 */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 시스템의 "배터리 최적화 무시" 요청 다이얼로그를 여는 인텐트. */
    fun requestIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
