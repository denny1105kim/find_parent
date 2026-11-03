package com.caregrandpapa.feature.pairing

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.caregrandpapa.R
import com.caregrandpapa.data.ClaimResult
import com.caregrandpapa.data.PairRepository
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

private const val TAG = "PatientPairing"

/**
 * 환자 폰의 페어링 화면.
 *
 * 1. 간병인 폰의 QR 을 찍는다 (Google Play 제공 스캐너 사용 — 별도 카메라 권한 불필요).
 * 2. **무엇이 공유되는지 명시한 동의 화면**을 보여준다.
 * 3. 동의하면 `pairs/{pairId}` 에 자신을 등록한다.
 *
 * QR 을 못 찍는 상황(카메라 불량 등)을 위해 코드 직접 입력도 남겨 둔다.
 */
@Composable
fun PatientPairingScreen(onPaired: (String) -> Unit, onBack: () -> Unit) {
    var scannedPairId by rememberSaveable { mutableStateOf<String?>(null) }

    val pendingId = scannedPairId
    if (pendingId == null) {
        ScanStep(
            onScanned = { scannedPairId = it },
            onBack = onBack,
        )
    } else {
        ConsentStep(
            pairId = pendingId,
            onAgreed = onPaired,
            onCancel = { scannedPairId = null },
        )
    }
}

@Composable
private fun ScanStep(onScanned: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var manualCode by rememberSaveable { mutableStateOf("") }
    var showManual by rememberSaveable { mutableStateOf(false) }

    val scanner: GmsBarcodeScanner = remember {
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build(),
        )
    }

    fun startScan() {
        message = null
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val pairId = PairPayload.decode(barcode.rawValue)
                if (pairId == null) {
                    message = "이 앱의 페어링 QR 이 아닙니다. 간병인 폰의 QR 을 찍어주세요."
                } else {
                    onScanned(pairId)
                }
            }
            .addOnCanceledListener { message = null }
            .addOnFailureListener { e ->
                Log.e(TAG, "스캔 실패: ${e.message}", e)
                message = "스캐너를 열 수 없습니다. 아래 [코드 직접 입력] 을 사용해 주세요."
                showManual = true
            }
    }

    // 스캐너 모듈을 미리 내려받아 두면 처음 누를 때 기다리지 않는다.
    LaunchedEffect(scanner) {
        ModuleInstall.getClient(context)
            .installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
            .addOnFailureListener { Log.w(TAG, "스캐너 모듈 준비 실패: ${it.message}") }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            com.caregrandpapa.core.ui.IconBadge(iconRes = R.drawable.ic_camera, size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "간병인 폰의 QR 을 찍어주세요",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "간병인 폰 화면에 나온 사각형 코드를 카메라로 비추면 됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            message?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(32.dp))

            Button(onClick = ::startScan, modifier = Modifier.fillMaxWidth()) {
                Text("QR 코드 찍기")
            }

            Spacer(Modifier.height(8.dp))

            if (!showManual) {
                TextButton(onClick = { showManual = true }) { Text("코드 직접 입력") }
            } else {
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    label = { Text("간병인 폰에 표시된 코드") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val trimmed = manualCode.trim()
                        if (PairPayload.isUuid(trimmed)) {
                            onScanned(trimmed)
                        } else {
                            message = "코드 형식이 올바르지 않습니다."
                        }
                    },
                    enabled = manualCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("이 코드로 연결")
                }
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onBack) { Text("역할 다시 선택") }
        }
    }
}

/**
 * 동의 화면.
 *
 * 어떤 정보가 언제 간병인에게 전달되는지 숨김 없이 나열한다. 가족 간 돌봄이라도
 * 감시 대상이 되는 당사자가 범위를 알고 시작하는 것이 원칙이고, 나중에 오해가 생기지 않는다.
 *
 * 특히 **녹음·녹화가 파일로 남는다**는 점을 분명히 적는다. 흘려보내는 실시간 방식과 달리
 * 간병인 폰에 기록이 쌓이므로, 그 사실을 모른 채 동의하는 일이 없어야 한다.
 */
@Composable
private fun ConsentStep(
    pairId: String,
    onAgreed: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "이 폰을 간병인과 연결합니다",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text = "간병인이 버튼을 누를 때마다 이 폰이 다음을 보냅니다.",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            ConsentItem(
                title = "주변 소리 15초 녹음",
                body = "간병인이 요청하면 이 폰의 마이크가 15초 동안 켜져 주변 소리를 " +
                    "녹음해 보냅니다. 이 폰에서 벨이 울리거나 화면이 켜지지는 않습니다.",
            )
            ConsentItem(
                title = "카메라 15초 녹화",
                body = "간병인이 요청하면 앞 또는 뒤 카메라로 15초 동안 영상(소리 포함)을 " +
                    "찍어 보냅니다. 어느 쪽 카메라를 쓸지는 간병인이 고릅니다.",
            )
            ConsentItem(
                title = "현재 위치",
                body = "간병인이 새로고침을 누를 때마다 이 폰의 현재 위치를 한 번 보냅니다. " +
                    "누르지 않는 동안에는 위치를 보내지 않습니다.",
            )
            ConsentItem(
                title = "안부와 기기 상태",
                body = "직접 누른 안부와 도움 요청을 가족에게 전달합니다. 공유 중에는 배터리 잔량·충전 여부·최근 응답 시각을 약 5분마다 보냅니다. 각각 최신 상태만 저장하며 연결 해제 후 정리합니다.",
            )
            ConsentItem(
                title = "기록은 파일로 남습니다",
                body = "녹음과 영상은 간병인 폰에 저장되어 나중에 다시 볼 수 있습니다. " +
                    "서버 원본은 다운로드 후 삭제합니다. 전송 실패나 간병인 미접속 시 서버에 더 오래 남을 수 있습니다.",
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "요청이 오면 이 폰 화면에 무엇을 하고 있는지 표시됩니다.\n" +
                    "공유를 멈추거나 연결을 완전히 끊는 버튼이 이 폰 홈 화면에 늘 있습니다. 언제든 직접 끄실 수 있습니다.\n" +
                    "동의하지 않으시면 [동의하지 않음] 을 눌러주세요. 아무것도 전달되지 않습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(28.dp))

            if (working) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
            } else {
                Button(
                    onClick = {
                        working = true
                        error = null
                        scope.launch {
                            when (val result = PairRepository.claim(pairId)) {
                                ClaimResult.Success, ClaimResult.AlreadyMine -> onAgreed(pairId)
                                ClaimResult.TakenByAnother -> {
                                    working = false
                                    error = "이 QR 은 이미 다른 폰에 연결되어 있습니다. " +
                                        "간병인에게 QR 을 새로 띄워 달라고 해주세요."
                                }
                                ClaimResult.NotFound -> {
                                    working = false
                                    error = "연결 정보를 찾을 수 없습니다. QR 을 다시 찍어주세요."
                                }
                                is ClaimResult.Failed -> {
                                    working = false
                                    error = "연결 실패: ${result.message}"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("위 내용에 동의하고 연결")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("동의하지 않음")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ConsentItem(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(text = "· $title", style = MaterialTheme.typography.titleSmall)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp),
        )
    }
}
