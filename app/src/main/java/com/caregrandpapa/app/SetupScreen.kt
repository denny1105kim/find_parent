package com.caregrandpapa.app


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.caregrandpapa.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


@Composable
fun SetupScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("${stringResource(R.string.app_name)} · 시작하기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("가족을 연결하기 전에\n준비가 필요해요", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("이 앱은 개인 Firebase 프로젝트 등록과 설정을 마친 뒤 두 휴대폰에 설치해서 사용합니다.")
            listOf(
                "01" to ("Firebase 등록" to "Android 앱을 등록하고 익명 로그인·Firestore·Storage를 활성화하세요. 본인 google-services.json을 앱에 추가합니다."),
                "02" to ("보안 설정과 지도 준비" to "Firestore·Storage 규칙과 Cloud Functions를 배포하고 App Check를 설정하세요. 카카오 앱 키와 서명 키 해시도 등록합니다."),
                "03" to ("두 휴대폰에 설치" to "설정을 넣어 다시 빌드한 앱을 양쪽에 설치하세요. 가족 폰의 QR을 당사자가 스캔하고 공유 범위에 동의합니다."),
            ).forEach { (number, content) ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(content.first, style = MaterialTheme.typography.titleLarge)
                        Text(content.second, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text("설치 담당자에게 설정을 요청해 주세요. 자세한 순서는 저장소의 설치 안내에서 확인할 수 있습니다.", style = MaterialTheme.typography.bodyMedium)
            Text("현재 설정으로는 로그인하거나 가족 데이터를 전송하지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
