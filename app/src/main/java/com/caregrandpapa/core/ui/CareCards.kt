package com.caregrandpapa.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caregrandpapa.data.CareStatusRepository
import com.caregrandpapa.data.PairInfo
import com.caregrandpapa.core.util.formatAge
import kotlinx.coroutines.launch

@Composable
fun CheckInCard(pairId: String) {
    var sending by remember { mutableStateOf(false) }
    var message by rememberSaveable(pairId) { mutableStateOf<String?>(null) }
    var confirmHelp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun send(help: Boolean) {
        scope.launch {
            sending = true
            message = null
            try {
                CareStatusRepository.checkIn(pairId, help)
                message = if (help) "도움 요청을 저장했어요. 가족에게 전화도 해 주세요." else "안부를 보냈어요. 가족이 앱에서 확인할 수 있어요."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = "보내지 못했어요. 인터넷을 확인하고 다시 눌러 주세요." }
            finally { sending = false }
        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("오늘의 안부", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("가족에게\n마음을 전해요", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("녹음이나 위치 없이, 버튼 한 번으로 알려 주세요.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { send(false) }, enabled = !sending, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = MaterialTheme.shapes.large) {
                Text("괜찮아요", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(onClick = { confirmHelp = true }, enabled = !sending, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = MaterialTheme.shapes.large) {
                Text("도움이 필요해요", style = MaterialTheme.typography.titleMedium)
            }
            if (sending) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("전송 확인 중… 인터넷 연결이 필요해요.", style = MaterialTheme.typography.bodySmall)
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("가족이 앱을 열면 확인할 수 있어요. 자동 전화·긴급 신고 기능은 아닙니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmHelp) AlertDialog(
        onDismissRequest = { confirmHelp = false },
        title = { Text("도움이 필요하신가요?") },
        text = { Text("가족 앱에 도움 요청을 남깁니다. 바로 확인하지 못할 수 있으니 급한 상황에는 직접 전화해 주세요.") },
        confirmButton = { TextButton(onClick = { confirmHelp = false; send(true) }) { Text("도움 요청 보내기") } },
        dismissButton = { TextButton(onClick = { confirmHelp = false }) { Text("취소") } },
    )
}

@Composable
fun FamilyCareCard(pair: PairInfo?, nowMillis: Long) {
    val help = pair?.checkIn == "help"
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (help) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
            contentColor = if (help) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("가족의 안부", style = MaterialTheme.typography.labelLarge)
                Text(
                    when (pair?.checkIn) {
                        "help" -> "도움이 필요해요"
                        "okay" -> "괜찮아요, 안심하세요"
                        else -> "첫 안부를 기다리고 있어요"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(pair?.checkInAtMillis?.let { "마지막 안부 · ${formatAge(((nowMillis - it) / 1000).coerceAtLeast(0))}" }
                    ?: "당사자가 안부 버튼을 누르면 여기에 표시됩니다.", style = MaterialTheme.typography.bodyMedium)
                if (help) Text("최근 남긴 도움 요청입니다. 직접 연락해 상황을 확인해 주세요.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("배터리", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(pair?.batteryPercent?.let { "$it%" } ?: "확인 전", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (pair?.charging == true) Text("충전 중", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("마지막 기기 응답", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(pair?.lastSeenAtMillis?.let { formatAge(((nowMillis - it) / 1000).coerceAtLeast(0)) } ?: "아직 없음",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Text("마지막으로 받은 상태입니다. 현재 연결 여부를 보장하지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
