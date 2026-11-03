package com.caregrandpapa.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * [MonitoringService] 의 실행 상태를 UI(PatientHomeScreen)에 공유하기 위한 전역 홀더.
 * 서비스가 값을 쓰고, Compose 가 관찰한다.
 */
object MonitoringState {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    /**
     * 마이크/카메라로 녹음·녹화까지 할 수 있는 상태인지.
     *
     * 부팅 직후나 FCM 으로 깨어난 경우 안드로이드가 카메라·마이크 타입 Foreground Service
     * 시작을 막기 때문에 위치만 가능한 상태가 될 수 있다. 이때는 앱을 한 번 열면 정상화되므로,
     * 그 사실을 환자 화면과 (Firestore 를 통해) 간병인 화면 양쪽에 알린다.
     */
    private val _mediaReady = MutableStateFlow(false)
    val mediaReady: StateFlow<Boolean> = _mediaReady

    /** 지금 처리 중인 요청 설명("소리를 녹음하고 있습니다"). 없으면 null. */
    private val _busyLabel = MutableStateFlow<String?>(null)
    val busyLabel: StateFlow<String?> = _busyLabel

    /**
     * 연결이 끝난 이유. null 이면 정상.
     *
     * 간병인이 연결을 해제하거나 이 폰의 신원이 바뀌면 서비스가 스스로 멈추고 여기에
     * 이유를 남긴다. 이 값이 있으면 환자 화면은 홈 대신 안내를 띄운다.
     */
    private val _disconnectReason = MutableStateFlow<String?>(null)
    val disconnectReason: StateFlow<String?> = _disconnectReason

    internal fun setDisconnectReason(reason: String?) {
        _disconnectReason.value = reason
    }

    /** 환자가 안내를 확인한 뒤 호출. */
    fun clearDisconnectReason() {
        _disconnectReason.value = null
    }

    internal fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    internal fun setMediaReady(ready: Boolean) {
        _mediaReady.value = ready
    }

    internal fun setBusyLabel(label: String?) {
        _busyLabel.value = label
    }
}
