package com.caregrandpapa.feature.patient

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.stringResource
import com.caregrandpapa.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caregrandpapa.core.messaging.WakeMessagingService
import com.caregrandpapa.core.service.BatteryOptimization
import com.caregrandpapa.core.service.MonitoringService
import com.caregrandpapa.core.service.MonitoringState
import com.caregrandpapa.core.service.PermissionHelpers

/**
 * 돌봄 공유를 켠 당사자(이 폰 주인)의 홈 화면.
 *
 * 이 앱은 당사자에게 **아무것도 숨기지 않는다.** 화면에는 돌봄 공유가 켜져 있다는 사실,
 * 지금 무엇이 공유되는지, 그리고 **언제든 끄거나 연결을 해제할 수 있는 버튼**이 늘 보인다.
 * 권한/배터리 설정도 이 화면에서 바로 연다(숨은 메뉴 없음).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(pairId: String, onUnpaired: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(PermissionHelpers.hasCorePermissions(context)) }
    var batteryIgnored by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    var backgroundLocation by remember {
        mutableStateOf(PermissionHelpers.hasBackgroundLocation(context))
    }

    val isRunning by MonitoringState.isRunning.collectAsStateWithLifecycle()
    val mediaReady by MonitoringState.mediaReady.collectAsStateWithLifecycle()
    val busyLabel by MonitoringState.busyLabel.collectAsStateWithLifecycle()
    val disconnectReason by MonitoringState.disconnectReason.collectAsStateWithLifecycle()

    // 가족이 연결을 해제하면 서비스가 스스로 멈추고 이유를 남긴다.
    disconnectReason?.let { reason ->
        DisconnectedScreen(
            reason = reason,
            onConfirm = {
                MonitoringState.clearDisconnectReason()
                onUnpaired()
            },
        )
        return
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = PermissionHelpers.hasCorePermissions(context)
        backgroundLocation = PermissionHelpers.hasBackgroundLocation(context)
        if (granted) {
            MonitoringService.start(context, pairId)
            WakeMessagingService.registerCurrentToken(pairId)
        }
    }

    // 최초 진입 시 이미 권한이 있으면 서비스 시작 + FCM 토큰 등록.
    LaunchedEffect(pairId) {
        if (PermissionHelpers.hasCorePermissions(context)) {
            granted = true
            MonitoringService.start(context, pairId)
            WakeMessagingService.registerCurrentToken(pairId)
        }
    }

    // 설정에서 돌아왔을 때 상태를 다시 확인.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryIgnored = BatteryOptimization.isIgnoring(context)
                backgroundLocation = PermissionHelpers.hasBackgroundLocation(context)
                granted = PermissionHelpers.hasCorePermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PatientStatusScreen(
        pairId = pairId,
        granted = granted,
        batteryIgnored = batteryIgnored,
        backgroundLocation = backgroundLocation,
        isRunning = isRunning,
        mediaReady = mediaReady,
        busyLabel = busyLabel,
        onRequestPermissions = {
            permissionLauncher.launch(PermissionHelpers.initialPermissionRequest())
        },
        onOpenBatterySettings = {
            context.startActivity(BatteryOptimization.requestIntent(context))
        },
        onOpenAppSettings = {
            context.startActivity(PermissionHelpers.appDetailsIntent(context))
        },
        onRequestBackgroundLocation = {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        },
        onStopSharing = { MonitoringService.stop(context) },
        onResumeSharing = {
            com.caregrandpapa.data.Prefs(context).sharingPaused = false
            MonitoringService.start(context, pairId)
        },
        onUnpaired = {
            MonitoringService.stop(context)
            onUnpaired()
        },
    )
}

/**
 * 가족이 연결을 해제했을 때 뜨는 안내.
 *
 * 이 화면 없이 조용히 멈추면, 당사자는 여전히 연결된 줄 알고 가족은 이 폰이
 * 왜 응답하지 않는지 모른다.
 */
@Composable
private fun DisconnectedScreen(reason: String, onConfirm: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            com.caregrandpapa.core.ui.IconBadge(iconRes = R.drawable.ic_favorite, size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "연결이 해제되었습니다",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$reason\n이제 이 폰은 아무것도 보내지 않습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("확인")
            }
        }
    }
}

/**
 * 당사자 폰의 상태 화면. 돌봄 공유 상태·권한·연결 해제를 모두 **드러내 놓고** 보여준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PatientStatusScreen(
    pairId: String,
    granted: Boolean,
    batteryIgnored: Boolean,
    backgroundLocation: Boolean,
    isRunning: Boolean,
    mediaReady: Boolean,
    busyLabel: String?,
    onRequestPermissions: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onStopSharing: () -> Unit,
    onResumeSharing: () -> Unit,
    onUnpaired: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.caregrandpapa.core.ui.CheckInCard(pairId)
            Spacer(Modifier.height(24.dp))
            com.caregrandpapa.core.ui.IconBadge(iconRes = R.drawable.ic_favorite, size = 64.dp)
            Spacer(Modifier.height(12.dp))

            val headline = when {
                !granted -> "권한이 필요합니다"
                busyLabel != null -> busyLabel!!
                isRunning -> "돌봄 공유 켜짐"
                else -> "돌봄 공유 꺼짐"
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when {
                    !granted ->
                        "가족이 요청할 때 이 폰의 위치·소리·영상을 보내려면 권한을 허용해 주세요."
                    isRunning ->
                        "가족이 요청할 때만 위치·소리·영상을 1회 보냅니다. 공유 중에는 배터리와 최근 응답 시각을 약 5분마다 보냅니다."
                    else ->
                        "자동 공유는 중지되었습니다. 안부 버튼으로 직접 메시지를 보낼 수 있어요."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(24.dp))

            if (!granted) {
                Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                    Text("권한 허용 (위치 · 마이크 · 카메라)")
                }
                Spacer(Modifier.height(8.dp))
            }

            // 공유 켜기/끄기 — 당사자가 자기 폰에서 언제든 직접 조작할 수 있다.
            if (granted) {
                if (isRunning) {
                    FilledTonalButton(onClick = onStopSharing, modifier = Modifier.fillMaxWidth()) {
                        Text("공유 잠시 중지")
                    }
                } else {
                    Button(onClick = onResumeSharing, modifier = Modifier.fillMaxWidth()) {
                        Text("공유 다시 켜기")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (granted && isRunning && !mediaReady) {
                Text(
                    text = "지금은 위치만 공유할 수 있습니다. 안드로이드가 백그라운드에서 시작한 " +
                        "마이크·카메라 사용을 막았기 때문입니다. 이 앱을 한 번 열면 소리·영상 공유도 켜집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (granted && !backgroundLocation) {
                FilledTonalButton(
                    onClick = {
                        if (PermissionHelpers.backgroundLocationNeedsSettings()) {
                            onOpenAppSettings()
                        } else {
                            onRequestBackgroundLocation()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("위치 '항상 허용' 하기")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (PermissionHelpers.backgroundLocationNeedsSettings()) {
                        "설정이 열리면 [권한] → [위치] → [항상 허용] 을 선택해 주세요."
                    } else {
                        "앱을 보고 있지 않을 때도 위치가 전달되도록 해줍니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (granted && !batteryIgnored) {
                FilledTonalButton(
                    onClick = onOpenBatterySettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("배터리 최적화 해제 (백그라운드 유지에 중요)")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "삼성 폰은 [설정 > 배터리 > 백그라운드 사용 제한]에서도 이 앱을 " +
                        "'제한 없음'으로 두어야 안정적으로 유지됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "연결 코드\n$pairId",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onUnpaired) { Text("연결 완전 해제") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
