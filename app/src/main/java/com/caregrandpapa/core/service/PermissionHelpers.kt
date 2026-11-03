package com.caregrandpapa.core.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 권한 확인/요청에 필요한 자잘한 것들.
 *
 * 백그라운드 위치(`ACCESS_BACKGROUND_LOCATION`)는 안드로이드 11(API 30)부터
 * **런타임 다이얼로그로 받을 수 없다.** 앱 설정 화면으로 보내 "항상 허용"을 직접
 * 고르게 해야 한다. SETUP 안내와 실제 동작이 어긋나지 않도록 여기서 분기한다.
 */
object PermissionHelpers {

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** 위치·마이크(필수) 권한이 모두 있는지. */
    fun hasCorePermissions(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) &&
            isGranted(context, Manifest.permission.RECORD_AUDIO)

    fun hasBackgroundLocation(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            // API 28 이하는 전경 위치 권한만으로 백그라운드 수집이 된다.
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** API 30+ 는 다이얼로그가 아니라 설정 화면으로 보내야 한다. */
    fun backgroundLocationNeedsSettings(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** 앱 상세 설정 화면(권한 항목 포함) 인텐트. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 안드로이드 버전에 맞춰 처음에 요청할 권한 목록. */
    fun initialPermissionRequest(): Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // API 29 에서는 백그라운드 위치를 같은 다이얼로그에서 받을 수 있다.
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }.toTypedArray()
}
