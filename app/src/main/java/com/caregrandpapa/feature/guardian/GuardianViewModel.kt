package com.caregrandpapa.feature.guardian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.caregrandpapa.data.ClipSync
import com.caregrandpapa.data.LocationRepository
import com.caregrandpapa.data.MediaRequest
import com.caregrandpapa.data.PairInfo
import com.caregrandpapa.data.PairRepository
import com.caregrandpapa.data.PatientLocation
import com.caregrandpapa.data.RequestRepository
import com.caregrandpapa.data.RequestType
import com.caregrandpapa.data.StoredClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 간병인 화면의 상태 소유자.
 *
 * 요청을 만들고, 진행 상태와 보관된 클립을 노출한다. 다운로드/서버 정리는 화면보다 오래
 * 살아야 하므로 [ClipSync] 가 앱 수준에서 따로 처리한다.
 */
class GuardianViewModel(private val pairId: String) : ViewModel() {

    val requests: StateFlow<List<MediaRequest>> = RequestRepository.observeRecent(pairId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pair: StateFlow<PairInfo?> = PairRepository.observe(pairId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lastLocation: StateFlow<PatientLocation?> = LocationRepository.observe(pairId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val clips: StateFlow<List<StoredClip>> = ClipSync.clips
    val usedBytes: StateFlow<Long> = ClipSync.usedBytes

    /** 방금 누른 버튼을 잠시 비활성화하기 위한 표시. */
    private val _submitting = MutableStateFlow<RequestType?>(null)
    val submitting: StateFlow<RequestType?> = _submitting

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun request(type: RequestType) {
        if (_submitting.value != null) return
        viewModelScope.launch {
            _submitting.value = type
            _error.value = null
            runCatching { RequestRepository.create(pairId, type) }
                .onFailure { _error.value = it.message ?: "요청을 보내지 못했습니다" }
            _submitting.value = null
        }
    }

    /** 실패했거나 응답이 없는 요청을 목록에서 치운다. */
    fun dismiss(requestId: String) {
        viewModelScope.launch { RequestRepository.delete(pairId, requestId) }
    }

    fun deleteClip(requestId: String) {
        ClipSync.deleteClip(requestId)
        viewModelScope.launch { RequestRepository.delete(pairId, requestId) }
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        fun factory(pairId: String) = viewModelFactory {
            initializer { GuardianViewModel(pairId) }
        }
    }
}
