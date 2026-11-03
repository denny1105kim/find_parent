package com.caregrandpapa.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * 1초마다 갱신되는 현재 시각.
 *
 * 신선도("3분 전")는 새 데이터가 오지 않아도 시간이 지나면 바뀌어야 한다.
 * 마지막 스냅샷만 보고 있으면 "방금"에서 영원히 멈춰 버린다.
 */
@Composable
fun rememberNowMillis(intervalMs: Long = 1_000L): State<Long> =
    produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(intervalMs)
        }
    }

/**
 * 이 컴포저블이 화면에 있는 동안 화면이 꺼지지 않게 한다.
 * 영상을 보는 중에 화면이 잠기면 통화가 사실상 끊기므로 카메라 화면에서 쓴다.
 */
@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
