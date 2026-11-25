package cz.helicode.hondaescanner.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.helicode.hondaescanner.HondaEApplication
import cz.helicode.hondaescanner.R
import cz.helicode.hondaescanner.model.BluetoothDeviceItem
import cz.helicode.hondaescanner.model.ToastEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceSelectionUiState(
    val pairedDevices: List<BluetoothDeviceItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val isBluetoothAvailable: Boolean = true
)

class DeviceSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HondaEApplication
    private val bluetoothClient = app.bluetoothClient
    private val settingsRepository = app.settingsRepository

    private val _uiState = MutableStateFlow(
        DeviceSelectionUiState(isBluetoothAvailable = bluetoothClient.isBluetoothAvailable)
    )
    val uiState: StateFlow<DeviceSelectionUiState> = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 4)
    val toastEvents: SharedFlow<ToastEvent> = _toastEvents.asSharedFlow()

    private val _autoConnectEvent = MutableSharedFlow<BluetoothDeviceItem>(extraBufferCapacity = 1)
    val autoConnectEvent: SharedFlow<BluetoothDeviceItem> = _autoConnectEvent.asSharedFlow()

    private var autoConnectChecked = false

    init {
        if (!bluetoothClient.isBluetoothAvailable) {
            _toastEvents.tryEmit(
                ToastEvent(
                    message = app.getString(R.string.no_bluetooth),
                    isLong = true
                )
            )
        }
        refreshPairedDevices()
        checkAutoConnectLastDevice()
    }

    fun refreshPairedDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val defaultName = app.getString(R.string.default_adapter_name)
            val devices = bluetoothClient.getPairedDevices(defaultName)
            _uiState.update {
                it.copy(
                    pairedDevices = devices,
                    isRefreshing = false,
                    isBluetoothAvailable = bluetoothClient.isBluetoothAvailable
                )
            }
        }
    }

    fun onDeviceSelected(device: BluetoothDeviceItem) {
        settingsRepository.saveLastConnectedDevice(device)
    }

    private fun checkAutoConnectLastDevice() {
        if (autoConnectChecked) return
        autoConnectChecked = true
        viewModelScope.launch {
            delay(300L)
            val lastDevice = settingsRepository.getLastConnectedDevice()
            if (lastDevice != null) {
                _autoConnectEvent.emit(lastDevice)
            }
        }
    }
}
