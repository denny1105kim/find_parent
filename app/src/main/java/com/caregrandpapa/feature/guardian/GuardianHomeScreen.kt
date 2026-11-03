package com.caregrandpapa.feature.guardian

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caregrandpapa.R
import com.caregrandpapa.data.PairInfo
import com.caregrandpapa.data.MediaRequest
import com.caregrandpapa.data.RequestStatus
import com.caregrandpapa.data.RequestType
import com.caregrandpapa.data.StoredClip
import com.caregrandpapa.core.ui.DotReady
import com.caregrandpapa.core.ui.DotWarn
import com.caregrandpapa.core.ui.IconBadge
import com.caregrandpapa.core.util.formatAge
import com.caregrandpapa.core.util.rememberNowMillis

/**
 * 간병인 홈 화면.
 *
 * 요청 기반이다. 타일을 누르면 환자 폰이 15초 녹음/녹화를 한 번 하고, 그 파일이 이 폰으로
 * 내려와 아래 목록에 쌓인다. 위치는 지도에서 새로고침으로 확인한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianHomeScreen(
    pairId: String,
    onOpenLocation: () -> Unit,
    onOpenClip: (String) -> Unit,
    onUnpaired: () -> Unit,
) {
    val vm: GuardianViewModel = viewModel(factory = GuardianViewModel.factory(pairId))
    val requests by vm.requests.collectAsStateWithLifecycle()
    val clips by vm.clips.collectAsStateWithLifecycle()
    val usedBytes by vm.usedBytes.collectAsStateWithLifecycle()
    val pair by vm.pair.collectAsStateWithLifecycle()
    val submitting by vm.submitting.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val now by rememberNowMillis()

    GuardianHomeContent(pair, requests, clips, usedBytes, submitting, error, now,
        onOpenLocation, onOpenClip, onUnpaired, vm::request, vm::dismiss, vm::clearError, vm::deleteClip)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GuardianHomeContent(
    pair: PairInfo?, requests: List<MediaRequest>, clips: List<StoredClip>, usedBytes: Long,
    submitting: RequestType?, error: String?, now: Long,
    onOpenLocation: () -> Unit, onOpenClip: (String) -> Unit, onUnpaired: () -> Unit,
    onRequest: (RequestType) -> Unit, onDismiss: (String) -> Unit,
    onClearError: () -> Unit, onDeleteClip: (String) -> Unit,
) {
    var confirmUnpair by remember { mutableStateOf(false) }

    val inFlight = requests.firstOrNull { it.isInFlight && !it.isTimedOut(now) }
    val problems = requests.filter { it.status == RequestStatus.FAILED || it.isTimedOut(now) }
    val busy = inFlight != null || submitting != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("가족 돌봄", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = { confirmUnpair = true }) { Text("연결 해제") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
        ) {
            item {
                com.caregrandpapa.core.ui.FamilyCareCard(pair, now)
                Spacer(Modifier.height(16.dp))
                StatusBanner(isPaired = pair?.isPaired, mediaReady = pair?.mediaReady)
                Spacer(Modifier.height(20.dp))
            }

            // 2×2 액션 타일
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(
                        iconRes = R.drawable.ic_voice,
                        label = "소리 듣기",
                        sub = "15초 녹음",
                        enabled = !busy,
                        onClick = { onRequest(RequestType.AUDIO) },
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        iconRes = R.drawable.ic_location,
                        label = "위치 보기",
                        sub = "지도에서 확인",
                        enabled = true,
                        onClick = onOpenLocation,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(
                        iconRes = R.drawable.ic_camera,
                        label = "전면 카메라",
                        sub = "환자 모습",
                        enabled = !busy,
                        onClick = { onRequest(RequestType.VIDEO_FRONT) },
                        modifier = Modifier.weight(1f),
                    )
                    ActionTile(
                        iconRes = R.drawable.ic_camera,
                        label = "후면 카메라",
                        sub = "주변 상황",
                        enabled = !busy,
                        onClick = { onRequest(RequestType.VIDEO_BACK) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            inFlight?.let { request ->
                item {
                    InFlightCard(request = request, nowMillis = now)
                    Spacer(Modifier.height(12.dp))
                }
            }

            items(problems, key = { "problem-${it.id}" }) { request ->
                ProblemCard(
                    request = request,
                    nowMillis = now,
                    onDismiss = { onDismiss(request.id) },
                )
                Spacer(Modifier.height(12.dp))
            }

            error?.let { message ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onClearError() }) { Text("확인") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // 기록 섹션 헤더
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "받아 둔 기록",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${clips.size}건 · ${formatSize(usedBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            if (clips.isEmpty()) {
                item { EmptyRecords() }
            } else {
                items(clips, key = { it.requestId }) { clip ->
                    ClipRow(
                        clip = clip,
                        nowMillis = now,
                        onOpen = { onOpenClip(clip.requestId) },
                        onDelete = { onDeleteClip(clip.requestId) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("연결을 해제할까요?") },
            text = {
                Text(
                    "환자 폰의 요청 대기가 중단되고, 서버에 남은 정보도 지워집니다. " +
                        "이 폰에 받아 둔 기록은 그대로 남습니다. " +
                        "다시 쓰려면 QR 로 처음부터 연결해야 합니다.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnpair = false
                        onUnpaired()
                    },
                ) { Text("해제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("취소") }
            },
        )
    }
}

/** 상단 연결 상태 배너. 색 점 + 제목 + 안내. */
@Composable
private fun StatusBanner(isPaired: Boolean?, mediaReady: Boolean?) {
    val (dot, title, subtitle) = when {
        isPaired == null -> Triple(
            MaterialTheme.colorScheme.outline,
            "환자 폰 상태 확인 중…",
            "잠시만 기다려 주세요",
        )
        !isPaired -> Triple(
            MaterialTheme.colorScheme.outline,
            "환자 폰 연결 대기 중",
            "환자 폰에서 QR 을 찍으면 연결됩니다",
        )
        mediaReady == false -> Triple(
            DotWarn,
            "위치만 확인 가능",
            "환자 폰에서 앱을 한 번 열면 소리·영상도 됩니다",
        )
        else -> Triple(
            DotReady,
            "환자 폰 준비됨",
            "소리·영상·위치를 받을 수 있어요",
        )
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(Modifier.size(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 요청 타일(정사각형에 가까운 카드). 아이콘 배지 + 라벨. */
@Composable
private fun ActionTile(
    iconRes: Int,
    label: String,
    sub: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.heightIn(min = 132.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            IconBadge(iconRes = iconRes, size = 52.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InFlightCard(request: MediaRequest, nowMillis: Long) {
    val elapsed = request.createdAtMillis?.let { (nowMillis - it) / 1000 } ?: 0L
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    text = "${request.type.label} — ${statusText(request.status)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "${elapsed}초 경과 · 보통 20~40초 걸립니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ProblemCard(request: MediaRequest, nowMillis: Long, onDismiss: () -> Unit) {
    val message = when {
        request.status == RequestStatus.FAILED ->
            request.errorMessage ?: "환자 폰에서 처리하지 못했습니다"
        request.isTimedOut(nowMillis) ->
            "환자 폰이 응답하지 않습니다. 폰이 꺼져 있거나 인터넷이 끊겼을 수 있습니다."
        else -> return
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.type.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onDismiss) { Text("지우기") }
        }
    }
}

@Composable
private fun EmptyRecords() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(iconRes = R.drawable.ic_favorite, size = 56.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "아직 받아 둔 기록이 없습니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "위 버튼으로 소리·영상·위치를 요청해 보세요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ClipRow(
    clip: StoredClip,
    nowMillis: Long,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val ageSeconds = ((nowMillis - clip.createdAtMillis) / 1000).coerceAtLeast(0)
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                iconRes = if (clip.type.isVideo) R.drawable.ic_camera else R.drawable.ic_voice,
                size = 44.dp,
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clip.type.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${formatAge(ageSeconds)} · ${formatSize(clip.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun statusText(status: RequestStatus): String = when (status) {
    RequestStatus.PENDING -> "환자 폰을 깨우는 중"
    RequestStatus.RUNNING -> "녹음/촬영 중"
    RequestStatus.UPLOADING -> "전송 중"
    RequestStatus.READY -> "받는 중"
    RequestStatus.DELIVERED -> "완료"
    RequestStatus.FAILED -> "실패"
}

/** 바이트를 사람이 읽는 단위로. */
fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    bytes < 1024L * 1024 * 1024 -> "%.1fMB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2fGB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
