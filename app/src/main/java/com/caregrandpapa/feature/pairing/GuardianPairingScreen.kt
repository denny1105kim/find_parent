package com.caregrandpapa.feature.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.caregrandpapa.data.PairRepository
import com.caregrandpapa.data.Prefs
import com.caregrandpapa.core.util.AppScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 간병인 폰에서 페어를 만들고 QR 을 띄운다. 환자 폰이 이 QR 을 찍으면 페어링이 끝난다.
 *
 * 페어 생성은 ViewModel 이 맡는다. 화면을 돌려도 새 페어가 또 만들어지지 않는다.
 */
class GuardianPairingViewModel(private val prefs: Prefs) : ViewModel() {

    private val _pairId = MutableStateFlow<String?>(null)
    val pairId: StateFlow<String?> = _pairId

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** 환자 폰이 등록을 마쳤는지. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val claimed: StateFlow<Boolean> = _pairId
        .flatMapLatest { id ->
            if (id == null) flowOf(false) else PairRepository.observe(id).map { it?.isPaired == true }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 쓸 수 있는 페어를 확보한다. 화면에 들어올 때마다 호출된다.
     *
     * 아직 환자 폰이 찍지 않은 페어가 이미 있으면 **그것을 다시 쓴다.** 매번 새로 만들면
     * 앱을 다시 열 때마다 버려진 문서가 Firestore 에 쌓이고, 지우는 경로가 없다.
     *
     * `init` 이 아니라 화면 쪽 `LaunchedEffect` 에서 부르는 이유: 페어링 화면은 NavHost 밖에
     * 있어 이 ViewModel 이 Activity 범위로 살아남는다. `init` 에만 두면 화면을 나갔다 와도
     * 다시 돌지 않아, 이미 지운 페어의 QR 을 계속 보여주게 된다.
     */
    fun ensurePair() {
        if (_pairId.value != null) return // 이미 확보됨(회전 등)
        viewModelScope.launch {
            _error.value = null

            prefs.pendingPairId?.let { existing ->
                val info = PairRepository.get(existing)
                when {
                    // 아직 짝이 없는 내 페어 → 그대로 재사용
                    info != null && !info.isPaired -> {
                        _pairId.value = existing
                        return@launch
                    }
                    // 이미 짝이 맺혔다면 페어링이 끝난 것이므로 그대로 알린다
                    info != null -> {
                        _pairId.value = existing
                        return@launch
                    }
                    // 문서가 사라졌다(다른 기기에서 해제 등) → 기억을 버리고 새로 만든다
                    else -> prefs.pendingPairId = null
                }
            }

            runCatching { PairRepository.createPair() }
                .onSuccess {
                    prefs.pendingPairId = it
                    _pairId.value = it
                }
                .onFailure { _error.value = it.message ?: "페어 생성에 실패했습니다" }
        }
    }

    /** 생성 실패 후 사용자가 다시 시도할 때. */
    fun retry() {
        _pairId.value = null
        ensurePair()
    }

    /**
     * QR 화면을 떠날 때(역할 다시 선택) 호출.
     * 아직 짝이 없는 페어 문서를 지워 쓰레기가 남지 않게 한다.
     *
     * 들고 있던 ID 도 함께 비운다. 그러지 않으면 다시 들어왔을 때 **이미 지운 페어의 QR** 을
     * 보여주고, 환자 폰이 그걸 찍으면 "연결 정보를 찾을 수 없습니다" 가 된다.
     */
    fun abandonPendingPair() {
        val id = prefs.pendingPairId
        prefs.pendingPairId = null
        _pairId.value = null
        if (id == null) return
        AppScope.launch { PairRepository.discardUnclaimed(id) }
    }

    /** 페어링이 끝났으니 "대기 중" 기억을 정리한다. */
    fun onClaimed() {
        prefs.pendingPairId = null
    }

    companion object {
        fun factory(prefs: Prefs) = viewModelFactory {
            initializer { GuardianPairingViewModel(prefs) }
        }
    }
}

@Composable
fun GuardianPairingScreen(onPaired: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val vm: GuardianPairingViewModel = viewModel(factory = GuardianPairingViewModel.factory(prefs))
    val pairId by vm.pairId.collectAsStateWithLifecycle()
    val claimed by vm.claimed.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    // 화면에 들어올 때마다 페어를 확보한다(ViewModel 이 Activity 범위로 살아남기 때문).
    LaunchedEffect(Unit) { vm.ensurePair() }

    LaunchedEffect(claimed, pairId) {
        val id = pairId
        if (claimed && id != null) {
            vm.onClaimed()
            onPaired(id)
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "환자 폰으로 이 QR 을 찍어주세요",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "환자 폰에서 앱을 열고 [환자] → [QR 코드 찍기] 를 누르면 됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(28.dp))

            when {
                error != null -> {
                    Text(
                        text = "페어 생성 실패\n$error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { vm.retry() }) { Text("다시 시도") }
                }

                pairId == null -> CircularProgressIndicator()

                else -> {
                    // QR 생성은 순수 계산이라 pairId 가 바뀔 때만 다시 만든다.
                    val qr = remember(pairId) { generateQrBitmap(PairPayload.encode(pairId!!)) }
                    // QR 은 어두운 테마에서도 스캔되도록 항상 흰 배경 위에 둔다.
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = androidx.compose.ui.graphics.Color.White,
                        shadowElevation = 3.dp,
                    ) {
                        Image(
                            bitmap = qr,
                            contentDescription = "페어링 QR 코드",
                            filterQuality = FilterQuality.None,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .aspectRatio(1f)
                                .padding(18.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "환자 폰의 등록을 기다리는 중…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    // QR 을 못 찍는 상황(카메라 고장 등)을 위한 수동 입력용 코드.
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "직접 입력용 코드",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = pairId!!,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            TextButton(
                onClick = {
                    // 아직 안 찍힌 페어 문서는 남기지 않고 지운다.
                    vm.abandonPendingPair()
                    onBack()
                },
            ) { Text("역할 다시 선택") }
        }
    }
}
