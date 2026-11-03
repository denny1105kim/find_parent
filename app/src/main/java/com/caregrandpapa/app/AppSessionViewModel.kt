package com.caregrandpapa.app


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.caregrandpapa.data.ClipSync
import com.caregrandpapa.data.PairRepository
import com.caregrandpapa.data.Prefs
import com.caregrandpapa.data.model.Role
import com.caregrandpapa.core.service.MonitoringService
import com.caregrandpapa.core.util.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Owns local pairing state and the shared disconnect flow for both roles. */
class AppSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = Prefs(context)
    var role by mutableStateOf(prefs.role)
        private set
    var pairId by mutableStateOf(prefs.pairId)
        private set
    private var disconnecting = false

    fun selectRole(value: Role?) {
        prefs.role = value
        role = value
    }

    fun pair(value: String) {
        prefs.pairId = value
        pairId = value
    }

    fun disconnect() {
        val id = pairId ?: return
        if (disconnecting) return
        disconnecting = true
        if (role == Role.PATIENT) MonitoringService.stop(context)
        AppScope.launch(Dispatchers.Main.immediate) {
            try {
                PairRepository.unpair(id)
                ClipSync.stop()
                prefs.clearAll()
                pairId = null
                role = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "연결 해제 실패: 온라인에서 다시 시도하세요", Toast.LENGTH_LONG).show()
            } finally {
                disconnecting = false
            }
        }
    }
}
