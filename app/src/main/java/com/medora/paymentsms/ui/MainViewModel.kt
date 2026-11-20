package com.medora.paymentsms.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medora.paymentsms.MedoraApp
import com.medora.paymentsms.data.db.PaymentEventEntity
import com.medora.paymentsms.data.repository.PairResult
import com.medora.paymentsms.service.ListeningForegroundService
import com.medora.paymentsms.sync.EventSyncWorker
import com.medora.paymentsms.sync.SyncHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val paired: Boolean = false,
    val deviceName: String? = null,
    val listening: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val authRevoked: Boolean = false,
    val pendingCount: Int = 0,
    val sentCount: Int = 0,
    val lastSyncAt: Long = 0L,
    val lastHeartbeatAt: Long = 0L,
    val healthLabel: String = "Degraded — no recent successful sync",
    val lastError: String? = null,
    val events: List<PaymentEventEntity> = emptyList(),
    val pairMessage: String? = null,
    val busy: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MedoraApp
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val dao = app.repository.dao
        val lastSyncAt = app.stateStore.lastSyncAt
        val pendingCount = dao.pendingCount()
        _ui.value = UiState(
            paired = app.tokenStore.isPaired(),
            deviceName = app.tokenStore.deviceName(),
            listening = app.stateStore.listeningEnabled,
            smsPermissionGranted = ContextCompat.checkSelfPermission(
                getApplication(), Manifest.permission.RECEIVE_SMS,
            ) == PackageManager.PERMISSION_GRANTED,
            authRevoked = app.stateStore.authRevoked,
            pendingCount = pendingCount,
            sentCount = dao.sentCount(),
            lastSyncAt = lastSyncAt,
            lastHeartbeatAt = app.stateStore.lastHeartbeatAt,
            healthLabel = SyncHealth.healthLabel(lastSyncAt, pendingCount),
            lastError = app.stateStore.lastError,
            events = dao.recent(100),
        )
    }

    fun pair(code: String, name: String) = viewModelScope.launch {
        _ui.value = _ui.value.copy(busy = true, pairMessage = null)
        when (val result = app.repository.pair(code, name)) {
            is PairResult.Success -> {
                _ui.value = _ui.value.copy(busy = false, pairMessage = "Paired: ${result.deviceName}")
                refresh()
            }
            is PairResult.InvalidCode -> {
                _ui.value = _ui.value.copy(busy = false, pairMessage = result.message)
            }
            is PairResult.Failure -> {
                _ui.value = _ui.value.copy(busy = false, pairMessage = result.message)
            }
        }
    }

    fun setListening(enabled: Boolean) {
        if (enabled && (!_ui.value.paired || !_ui.value.smsPermissionGranted || _ui.value.authRevoked)) return
        app.stateStore.listeningEnabled = enabled
        if (enabled) {
            EventSyncWorker.schedulePeriodic(getApplication())
            ListeningForegroundService.start(getApplication())
        } else {
            ListeningForegroundService.stop(getApplication())
        }
        refresh()
    }

    fun permissionChanged() = refresh()

    fun syncNow() = EventSyncWorker.enqueue(getApplication())

    fun disconnect() = viewModelScope.launch {
        app.repository.disconnect()
        ListeningForegroundService.stop(getApplication())
        EventSyncWorker.cancelAll(getApplication())
        refresh()
    }
}
