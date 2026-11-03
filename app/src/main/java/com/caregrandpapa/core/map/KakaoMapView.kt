package com.caregrandpapa.core.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

private const val TAG = "KakaoMapView"

/** 지도상의 좌표. Compose 레이어에서 SDK 타입을 직접 노출하지 않기 위한 래퍼. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** 현재 위치 마커용 빨간 원(흰 테두리) 비트맵을 그린다. */
private fun redDotBitmap(sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val center = sizePx / 2f
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E53935") }
    canvas.drawCircle(center, center, center, white)          // 흰 테두리
    canvas.drawCircle(center, center, center * 0.72f, red)     // 빨간 원
    return bmp
}

/** 위치 데이터가 아직 없을 때 지도의 초기 중심(서울시청). */
val DEFAULT_CENTER = GeoPoint(37.5665, 126.9780)

/**
 * 카카오맵을 Compose 로 감싼 컴포저블.
 *
 * - [target] 위치에 마커를 표시하고 카메라를 이동한다. target 이 바뀌면 마커도 이동한다.
 *   target 이 null 이면(아직 위치 수신 전) 마커 없이 [initialCenter] 를 보여준다.
 * - MapView 의 resume/pause/finish 를 화면 생명주기에 맞춰 호출한다(미호출 시 크래시 위험).
 */
@Composable
fun KakaoMapView(
    target: GeoPoint?,
    modifier: Modifier = Modifier,
    zoomLevel: Int = 15,
    initialCenter: GeoPoint = DEFAULT_CENTER,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    val kakaoMapState = remember { mutableStateOf<KakaoMap?>(null) }
    val markerState = remember { mutableStateOf<Label?>(null) }
    // 카카오 라벨은 벡터 드로어블을 렌더링하지 못하므로 비트맵으로 직접 그린다.
    val markerBitmap = remember {
        redDotBitmap((context.resources.displayMetrics.density * 22).toInt())
    }

    // 생명주기에 맞춰 지도 렌더링 resume/pause, 화면 이탈 시 finish.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() {
                        Log.d(TAG, "onMapDestroy")
                    }

                    override fun onMapError(error: Exception) {
                        // 대표적으로 앱 키/키 해시 미등록 시 여기로 온다.
                        Log.e(TAG, "onMapError: ${error.message}", error)
                    }
                },
                object : KakaoMapReadyCallback() {
                    override fun onMapReady(kakaoMap: KakaoMap) {
                        kakaoMapState.value = kakaoMap
                    }

                    // 지도가 처음 열릴 때의 중심 좌표.
                    override fun getPosition(): LatLng {
                        val c = target ?: initialCenter
                        return LatLng.from(c.latitude, c.longitude)
                    }

                    override fun getZoomLevel(): Int = zoomLevel
                },
            )
            mapView
        },
    )

    // 지도가 준비된 뒤 & target 이 바뀔 때마다 마커/카메라 갱신.
    LaunchedEffect(kakaoMapState.value, target) {
        val map = kakaoMapState.value ?: return@LaunchedEffect
        val current = target ?: return@LaunchedEffect // 위치 없으면 마커 표시 안 함
        val latLng = LatLng.from(current.latitude, current.longitude)
        val labelManager = map.labelManager ?: return@LaunchedEffect

        val existing = markerState.value
        if (existing == null) {
            val styles = labelManager.addLabelStyles(
                LabelStyles.from(LabelStyle.from(markerBitmap)),
            )
            val options = LabelOptions.from(latLng).setStyles(styles)
            markerState.value = labelManager.layer?.addLabel(options)
        } else {
            existing.moveTo(latLng)
        }
        map.moveCamera(CameraUpdateFactory.newCenterPosition(latLng, zoomLevel))
    }
}
