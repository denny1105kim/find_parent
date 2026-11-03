package com.caregrandpapa.feature.guardian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caregrandpapa.R
import com.caregrandpapa.data.RequestStatus
import com.caregrandpapa.data.RequestType
import com.caregrandpapa.core.map.KakaoMapView
import com.caregrandpapa.core.util.FreshnessLevel
import com.caregrandpapa.core.util.freshnessOf
import com.caregrandpapa.core.util.rememberNowMillis

/**
 * 위치 화면.
 *
 * 상시 추적을 없앴으므로 지도에 보이는 것은 **마지막으로 확인된 위치**다. 그래서 신선도
 * 표시가 특히 중요하다 — 새로고침을 누르지 않으면 몇 시간 전 위치일 수 있다.
 * 새로고침을 누르면 환자 폰이 그때 한 번 위치를 측정해 올린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(pairId: String, onBack: () -> Unit) {
    val vm: GuardianViewModel = viewModel(factory = GuardianViewModel.factory(pairId))
    val location by vm.lastLocation.collectAsStateWithLifecycle()
    val requests by vm.requests.collectAsStateWithLifecycle()
    val now by rememberNowMillis()

    val freshness = freshnessOf(location?.updatedAtMillis, now)
    val refreshing = requests.any {
        it.type == RequestType.LOCATION && it.isInFlight && !it.isTimedOut(now)
    }
    val refreshFailed = requests.firstOrNull {
        it.type == RequestType.LOCATION && (it.status == RequestStatus.FAILED || it.isTimedOut(now))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("위치") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "뒤로",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            KakaoMapView(
                target = location?.point,
                modifier = Modifier.fillMaxSize(),
            )

            Surface(
                shape = MaterialTheme.shapes.large,
                shadowElevation = 4.dp,
                color = when (freshness.level) {
                    FreshnessLevel.STALE -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = freshness.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = when (freshness.level) {
                            FreshnessLevel.LIVE -> MaterialTheme.colorScheme.primary
                            FreshnessLevel.STALE -> MaterialTheme.colorScheme.onErrorContainer
                            else -> Color.Unspecified
                        },
                    )
                    val detail = when {
                        refreshFailed != null ->
                            refreshFailed.errorMessage
                                ?: "환자 폰이 응답하지 않습니다"
                        freshness.level == FreshnessLevel.UNKNOWN ->
                            "새로고침을 눌러 환자 위치를 확인하세요"
                        else -> location?.let { "정확도 약 ${it.accuracy.toInt()}m" }
                    }
                    if (detail != null) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                refreshFailed != null -> MaterialTheme.colorScheme.error
                                freshness.level == FreshnessLevel.STALE ->
                                    MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Button(
                onClick = { vm.request(RequestType.LOCATION) },
                enabled = !refreshing,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(if (refreshing) "위치를 확인하는 중…" else "지금 위치 새로고침")
                }
            }
        }
    }
}
