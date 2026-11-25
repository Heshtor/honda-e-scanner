package cz.helicode.hondaescanner.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.helicode.hondaescanner.HondaEApplication
import cz.helicode.hondaescanner.MainActivity
import cz.helicode.hondaescanner.R
import cz.helicode.hondaescanner.data.can.CanMessageParser
import cz.helicode.hondaescanner.data.can.CanProtocol
import cz.helicode.hondaescanner.data.can.CanTrafficLogger
import cz.helicode.hondaescanner.data.demo.DemoSimulator
import cz.helicode.hondaescanner.data.prefs.AppIconManager
import cz.helicode.hondaescanner.model.AppIconColor
import cz.helicode.hondaescanner.model.AppLanguage
import cz.helicode.hondaescanner.model.AppThemeMode
import cz.helicode.hondaescanner.model.CanLogInfo
import cz.helicode.hondaescanner.model.ConnectionStatus
import cz.helicode.hondaescanner.model.SohTestResult
import cz.helicode.hondaescanner.model.SohTestState
import cz.helicode.hondaescanner.model.StatusMessage
import cz.helicode.hondaescanner.model.ToastEvent
import cz.helicode.hondaescanner.model.VehicleTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class DashboardTab {
    OVERVIEW,
    SOH_TEST,
    SETTINGS
}

data class ScannerUiState(
    val deviceName: String = "",
    val deviceMac: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val carConnected: Boolean = false,
    val isDemoMode: Boolean = false,
    val statusMessage: StatusMessage = StatusMessage.Idle,
    val selectedTab: DashboardTab = DashboardTab.OVERVIEW,
    val telemetry: VehicleTelemetry = VehicleTelemetry(),
    val sohTest: SohTestState = SohTestState(),
    val logInfo: CanLogInfo = CanLogInfo(),
    val currentLanguage: AppLanguage = AppLanguage.CZECH,
    val showCellsDialog: Boolean = false,
    val showModuleTempsDialog: Boolean = false
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ScannerViewModel"
        private const val NOTIFICATION_CHANNEL_ID = "SoC"
        private const val NOTIFICATION_ID = 23
    }

    private val app = application as HondaEApplication
    private val bluetoothClient = app.bluetoothClient
    private val settingsRepository = app.settingsRepository
    private val locationTracker = app.locationTracker
    private val demoSimulator = DemoSimulator()

    private val _uiState = MutableStateFlow(
        ScannerUiState(
            currentLanguage = settingsRepository.getCurrentLanguage(),
            logInfo = CanTrafficLogger.getLogInfo(app)
        )
    )
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = settingsRepository.themeMode
    val selectedIcon: StateFlow<AppIconColor> = settingsRepository.selectedIcon

    private val _toastEvents = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<ToastEvent> = _toastEvents.asSharedFlow()

    private var sessionInitialized = false
    private var connectionJob: Job? = null
    private var demoJob: Job? = null
    private var locationJob: Job? = null
    private var retryEnabled = true

    private var lastLocation: Location? = null
    private var sohTestLastGpsLocation: Location? = null
    private var sohTestStartSysTimeMs: Long = 0L
    private var sohTestLastElapsedRealtimeMs: Long = 0L
    private var sohTestCycleCounter: Int = 0
    private var isEcu15Configured: Boolean = false
    private var lastEpochNotification: Long = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    private val notificationManagerCompat = NotificationManagerCompat.from(app)

    init {
        createNotificationChannel()
    }

    fun initSession(deviceName: String, deviceMac: String, startInDemo: Boolean) {
        if (sessionInitialized &&
            _uiState.value.deviceMac == deviceMac &&
            _uiState.value.isDemoMode == startInDemo
        ) {
            return
        }
        sessionInitialized = true

        val savedVin = settingsRepository.getSavedVin().orEmpty()
        _uiState.update {
            it.copy(
                deviceName = deviceName,
                deviceMac = deviceMac,
                telemetry = if (savedVin.isNotEmpty()) it.telemetry.copy(vin = savedVin) else it.telemetry,
                logInfo = CanTrafficLogger.getLogInfo(app),
                currentLanguage = settingsRepository.getCurrentLanguage()
            )
        }

        startLocationTracking()

        if (startInDemo) {
            enableDemoMode()
        }
    }

    fun selectTab(tab: DashboardTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == DashboardTab.SETTINGS) {
            refreshLogInfo()
        }
    }

    fun refreshLogInfo() {
        _uiState.update {
            it.copy(
                logInfo = CanTrafficLogger.getLogInfo(app),
                currentLanguage = settingsRepository.getCurrentLanguage()
            )
        }
    }

    fun startLocationTracking() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationTracker.locationUpdates()
                .catch { e -> Log.e(TAG, "Location flow error", e) }
                .collect { location -> handleLocationUpdate(location) }
        }
    }

    private fun handleLocationUpdate(location: Location) {
        if (_uiState.value.isDemoMode) return

        val lat = String.format(Locale.ENGLISH, "%.7f", location.latitude)
        val lon = String.format(Locale.ENGLISH, "%.7f", location.longitude)
        val elevation = if (location.hasAltitude()) {
            (location.altitude * 10.0).roundToInt() / 10.0
        } else {
            _uiState.value.telemetry.elevation
        }

        var speedKmh = 0.0
        if (location.hasSpeed()) {
            speedKmh = location.speed * 3.6
        } else {
            val prev = lastLocation
            if (prev != null) {
                val dtMs = location.time - prev.time
                if (dtMs in 401..9999) {
                    val distMeters = prev.distanceTo(location)
                    speedKmh = (distMeters / (dtMs / 1000.0)) * 3.6
                }
            }
        }
        lastLocation = location

        val validSpeed = if (speedKmh in 0.0..219.9) {
            (speedKmh * 10.0).roundToInt() / 10.0
        } else {
            _uiState.value.telemetry.speedGps
        }

        // Accumulate GPS distance during active SoH test
        var addedDistanceKm = 0.0
        if (_uiState.value.sohTest.isRunning) {
            val prevSohLoc = sohTestLastGpsLocation
            if (prevSohLoc != null) {
                val d = prevSohLoc.distanceTo(location)
                if (d > 1.0f && d < 600.0f && location.accuracy < 60.0f) {
                    addedDistanceKm = d / 1000.0
                }
            }
            sohTestLastGpsLocation = location
        }

        _uiState.update { state ->
            val currentPower = state.telemetry.powerKw
            val effectiveSpeed = if (state.telemetry.speedCan > 0) state.telemetry.speedCan.toDouble() else validSpeed
            val instantConsumption = if (effectiveSpeed > 2.0) (currentPower / effectiveSpeed) * 100.0 else 0.0

            state.copy(
                telemetry = state.telemetry.copy(
                    lat = lat,
                    lon = lon,
                    elevation = elevation,
                    speedGps = validSpeed,
                    instantConsumption = instantConsumption
                ),
                sohTest = if (addedDistanceKm > 0.0) {
                    state.sohTest.copy(gpsDistanceKm = state.sohTest.gpsDistanceKm + addedDistanceKm)
                } else state.sohTest
            )
        }
    }

    // region Bluetooth & CAN Loop

    fun onConnectActionClicked() {
        when (_uiState.value.connectionStatus) {
            ConnectionStatus.CONNECTED -> disconnect()
            ConnectionStatus.CONNECTING -> {
                retryEnabled = false
                disconnect()
            }
            ConnectionStatus.DISCONNECTED -> {
                if (_uiState.value.isDemoMode) {
                    disableDemoMode()
                }
                connect()
            }
        }
    }

    fun connect() {
        val mac = _uiState.value.deviceMac
        if (mac.isBlank() || mac == "DEMO_MODE") return
        if (!bluetoothClient.isBluetoothAvailable) {
            emitToast(R.string.bluetooth_unavailable, isLong = true)
            return
        }

        connectionJob?.cancel()
        retryEnabled = true

        connectionJob = viewModelScope.launch {
            var attempt = 0
            while (isActive && retryEnabled && attempt < CanProtocol.MAX_RETRY) {
                attempt++
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }

                val result = bluetoothClient.connect(mac)
                if (result.isSuccess) {
                    _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTED) }
                    emitToast(R.string.connected, isLong = false)
                    runCanSessionLoop()
                    return@launch
                } else {
                    emitToast(R.string.connection_failed, isLong = true)
                    if (!retryEnabled || attempt >= CanProtocol.MAX_RETRY) {
                        break
                    }
                    delay(500L)
                }
            }
            disconnectInternal()
        }
    }

    fun disconnect() {
        retryEnabled = false
        connectionJob?.cancel()
        connectionJob = null
        viewModelScope.launch {
            disconnectInternal()
        }
    }

    private suspend fun disconnectInternal() {
        bluetoothClient.disconnect()
        CanTrafficLogger.stopLogging()
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                carConnected = false,
                logInfo = CanTrafficLogger.getLogInfo(app)
            )
        }
    }

    private suspend fun runCanSessionLoop() = withContext(Dispatchers.IO) {
        try {
            CanTrafficLogger.startLogging(app, _uiState.value.telemetry.vin)
            refreshLogInfo()
            updateStatusMessage(StatusMessage.CanConnecting)

            for (command in CanProtocol.CONNECTION_COMMANDS) {
                if (!isActive) return@withContext
                sendAndParseCanCommand(command)
                if (command.length <= 6) {
                    delay(CanProtocol.COMMAND_DELAY_MS)
                }
            }

            _uiState.update { state ->
                val displayVin = state.telemetry.vin.ifBlank { "HONDA_E" }
                state.copy(
                    carConnected = true,
                    telemetry = state.telemetry.copy(vin = displayVin)
                )
            }

            delay(250L)

            while (isActive) {
                if (_uiState.value.sohTest.isRunning) {
                    runSohTestStep()
                } else {
                    isEcu15Configured = false
                    var parsedCount = 0
                    for (command in CanProtocol.LOOP_COMMANDS) {
                        if (!isActive) return@withContext
                        if (sendAndParseCanCommand(command)) {
                            parsedCount++
                        }
                        if (command.length <= 7) {
                            delay(CanProtocol.COMMAND_DELAY_MS)
                        }
                    }

                    val t = _uiState.value.telemetry
                    val gearName = gearString(t.computeGear(isActiveSession = true))
                    CanTrafficLogger.logInfo(
                        String.format(
                            Locale.ENGLISH,
                            "ODO=%dkm, SoC=%.1f%%, kW=%.2f, Volt=%.1fV, Amp=%.2fA, Range=%dkm, Cells min/max=%d/%dmV (Δ%dmV), InvTemp=%d°C, RPM=%d, Gear=%s",
                            t.odo, t.soc, t.powerKw, t.volt, t.amp, t.rangeFromDash,
                            t.cellMinMv, t.cellMaxMv, t.cellDeltaMv, t.invTemp, t.motorRpm, gearName
                        )
                    )

                    if (parsedCount > 2) {
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        updateStatusMessage(StatusMessage.Updated(timeStr))
                        val epoch = System.currentTimeMillis() / 1000L
                        if (lastEpochNotification + 10L < epoch) {
                            postStatusNotification()
                        }
                    } else {
                        updateStatusMessage(
                            StatusMessage.CanWaiting(CanProtocol.CAN_BUS_SCAN_INTERVAL_MS.toInt())
                        )
                        delay(CanProtocol.CAN_BUS_SCAN_INTERVAL_MS)
                    }
                }
            }
        } catch (_: CancellationException) {
            Log.d(TAG, "CAN loop cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Exception in CAN loop", e)
            updateStatusMessage(StatusMessage.CanError(e.message))
            emitToast(R.string.message_send_error, isLong = true)
            disconnectInternal()
        }
    }

    private suspend fun sendAndParseCanCommand(command: String): Boolean {
        val rawLines = bluetoothClient.sendCommandAndReadLines(
            command = command,
            timeoutMs = CanProtocol.COMMAND_RESPONSE_TIMEOUT_MS
        )
        if (rawLines.isEmpty()) return false

        val payload = CanMessageParser.reassembleResponse(rawLines)
        val result = CanMessageParser.parsePayload(_uiState.value.telemetry, payload)

        if (result.detectedVin != null) {
            settingsRepository.saveVin(result.detectedVin)
            CanTrafficLogger.updateVin(result.detectedVin)
        }

        if (result.parsedValidMetric) {
            _uiState.update { state ->
                val updatedTelemetry = result.telemetry.let { t ->
                    val spd = t.effectiveSpeed
                    val cons = if (spd > 2.0) (t.powerKw / spd) * 100.0 else 0.0
                    t.copy(instantConsumption = cons)
                }
                val updatedSoh = if (state.sohTest.isRunning && state.sohTest.startOdo <= 0 && updatedTelemetry.odo > 0) {
                    state.sohTest.copy(startOdo = updatedTelemetry.odo)
                } else state.sohTest

                state.copy(
                    carConnected = true,
                    telemetry = updatedTelemetry,
                    sohTest = updatedSoh
                )
            }
        }
        return result.parsedValidMetric
    }

    private suspend fun runSohTestStep() {
        val state = _uiState.value
        if (state.sohTest.isStopping) {
            runCatching {
                CanProtocol.ECU_60_HEADER_COMMANDS.forEach { sendAndParseCanCommand(it) }
                sendAndParseCanCommand("227022") // ODO
                CanProtocol.ECU_15_HEADER_COMMANDS.forEach { sendAndParseCanCommand(it) }
                sendAndParseCanCommand("222029") // SoC
            }
            val latest = _uiState.value.telemetry
            finishSohTestCalculations(stopSoc = latest.soc, stopOdo = latest.odo)
            return
        }

        if (!isEcu15Configured) {
            CanProtocol.ECU_15_HEADER_COMMANDS.forEach { sendAndParseCanCommand(it) }
            isEcu15Configured = true
        }

        // 1. High-priority voltage & current query (DID 0x2021)
        sendAndParseCanCommand("222021")

        val now = SystemClock.elapsedRealtime()
        if (sohTestLastElapsedRealtimeMs > 0L) {
            val dtSeconds = (now - sohTestLastElapsedRealtimeMs) / 1000.0
            if (dtSeconds > 0.0 && dtSeconds < 10.0) {
                val t = _uiState.value.telemetry
                val powerWatts = t.volt * t.amp
                val deltaKwh = (powerWatts * dtSeconds) / 3_600_000.0

                _uiState.update { current ->
                    val s = current.sohTest
                    val newNet = s.totalNetEnergyKwh + deltaKwh
                    val newDis = s.totalDischargeKwh + if (deltaKwh > 0) deltaKwh else 0.0
                    val newReg = s.totalRegenKwh + if (deltaKwh < 0) abs(deltaKwh) else 0.0
                    val newSamples = s.sampleCount + 1
                    current.copy(
                        sohTest = s.copy(
                            elapsedMs = System.currentTimeMillis() - sohTestStartSysTimeMs,
                            sampleCount = newSamples,
                            totalNetEnergyKwh = newNet,
                            totalDischargeKwh = newDis,
                            totalRegenKwh = newReg
                        )
                    )
                }

                val sAfter = _uiState.value.sohTest
                CanTrafficLogger.logInfo(
                    String.format(
                        Locale.ENGLISH,
                        "[SOH_TEST] dt=%.3fs, V=%.1fV, A=%.2fA, kW=%.2f, netKwh=%.4f, samples=%d",
                        dtSeconds, t.volt, t.amp, t.powerKw, sAfter.totalNetEnergyKwh, sAfter.sampleCount
                    )
                )
            }
        }
        sohTestLastElapsedRealtimeMs = now
        sohTestCycleCounter++

        // 2. Periodic secondary diagnostics every ~40 samples (~15-16s)
        if (sohTestCycleCounter % 40 == 0) {
            runCatching {
                CanProtocol.ECU_60_HEADER_COMMANDS.forEach { sendAndParseCanCommand(it) }
                sendAndParseCanCommand("227022")
                sendAndParseCanCommand("22702A")
                sendAndParseCanCommand("227028")

                CanProtocol.ECU_01_HEADER_COMMANDS.forEach { sendAndParseCanCommand(it) }
                sendAndParseCanCommand("22202A")
                sendAndParseCanCommand("22202C")
                sendAndParseCanCommand("222028")

                sendAndParseCanCommand("ATRV")

                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                updateStatusMessage(StatusMessage.Updated(timeStr))
            }
            isEcu15Configured = false
        } else if (sohTestCycleCounter % 10 == 0) {
            sendAndParseCanCommand("222029") // SoC
        }

        val epoch = System.currentTimeMillis() / 1000L
        if (lastEpochNotification + 10L < epoch) {
            postStatusNotification()
        }

        delay(300L)
    }

    // endregion

    // region SoH Test Controls

    fun onToggleSohTestClicked() {
        val state = _uiState.value
        if (!state.sohTest.isRunning) {
            if (!state.carConnected && !state.isDemoMode) {
                emitToast(R.string.soh_test_not_connected, isLong = false)
                return
            }
            startSohTest()
        } else {
            stopSohTest()
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun startSohTest() {
        val state = _uiState.value
        if (!state.carConnected && !state.isDemoMode) {
            emitToast(R.string.soh_test_not_connected, isLong = false)
            return
        }

        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm != null && (wakeLock == null || wakeLock?.isHeld == false)) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HondaEScanner:SohTestWakeLock").apply {
                acquire(4 * 3600 * 1000L)
            }
        }

        sohTestStartSysTimeMs = System.currentTimeMillis()
        sohTestLastElapsedRealtimeMs = 0L
        sohTestLastGpsLocation = lastLocation
        sohTestCycleCounter = 0
        isEcu15Configured = false

        var currentSoc = state.telemetry.soc
        var currentOdo = state.telemetry.odo
        if (state.isDemoMode) {
            if (currentSoc < 25.0) currentSoc = 76.50
            if (currentOdo <= 0) currentOdo = 15000
        }
        val startSoc = if (currentSoc > 0.0) currentSoc else 76.5
        val startOdo = if (currentOdo > 0) currentOdo else 0

        CanTrafficLogger.logInfo(
            String.format(Locale.ENGLISH, "[SOH_TEST_STARTED] StartSoC=%.2f%%, StartOdo=%dkm", startSoc, startOdo)
        )

        _uiState.update { current ->
            current.copy(
                telemetry = current.telemetry.copy(soc = currentSoc, odo = currentOdo),
                sohTest = SohTestState(
                    isRunning = true,
                    isStopping = false,
                    showLiveCard = true,
                    startSoc = startSoc,
                    startOdo = startOdo,
                    result = null
                )
            )
        }
    }

    private fun stopSohTest() {
        releaseWakeLock()
        val state = _uiState.value

        if (state.isDemoMode) {
            var s = state.sohTest
            if (s.sampleCount < 10 || s.gpsDistanceKm < 0.2) {
                s = s.copy(
                    sampleCount = max(s.sampleCount, 38),
                    gpsDistanceKm = 1.8,
                    totalNetEnergyKwh = 0.30,
                    totalRegenKwh = 0.04,
                    totalDischargeKwh = 0.34
                )
            }
            val demoDeltaOdo = max(1, s.gpsDistanceKm.roundToInt())
            val stopOdo = s.startOdo + demoDeltaOdo
            val targetNetCapacity = 26.39
            val calcDeltaSoc = max(1.05, (((s.totalNetEnergyKwh / targetNetCapacity) * 100.0) * 100.0).roundToInt() / 100.0)
            val stopSoc = ((s.startSoc - calcDeltaSoc) * 100.0).roundToInt() / 100.0

            _uiState.update {
                it.copy(
                    telemetry = it.telemetry.copy(odo = stopOdo, soc = stopSoc),
                    sohTest = s.copy(isRunning = false)
                )
            }
            finishSohTestCalculations(stopSoc = stopSoc, stopOdo = stopOdo)
        } else if (state.connectionStatus == ConnectionStatus.CONNECTED && state.carConnected) {
            _uiState.update {
                it.copy(sohTest = it.sohTest.copy(isStopping = true))
            }
        } else {
            finishSohTestCalculations(
                stopSoc = state.telemetry.soc,
                stopOdo = state.telemetry.odo
            )
        }
    }

    private fun finishSohTestCalculations(stopSoc: Double, stopOdo: Int) {
        val state = _uiState.value
        val s = state.sohTest

        var rawDurationMs = System.currentTimeMillis() - sohTestStartSysTimeMs
        if (state.isDemoMode && s.sampleCount >= 40 && rawDurationMs < 15_000L) {
            rawDurationMs = s.sampleCount * 400L
        }

        val deltaSocPct = s.startSoc - stopSoc
        val deltaKmCan = if (stopOdo > 0 && s.startOdo > 0 && stopOdo >= s.startOdo) stopOdo - s.startOdo else 0
        val netEnergyKwh = s.totalNetEnergyKwh
        val isGpsConsistentWithOdo = if (deltaKmCan > 0) {
            val minExpectedGps = minOf(deltaKmCan - 1.2, deltaKmCan * 0.85)
            s.gpsDistanceKm >= minExpectedGps
        } else {
            true
        }
        val effectiveDistKm = when {
            s.gpsDistanceKm >= 0.2 && isGpsConsistentWithOdo -> s.gpsDistanceKm
            deltaKmCan > 0 -> deltaKmCan.toDouble()
            s.gpsDistanceKm > 0.05 -> s.gpsDistanceKm
            else -> 0.0
        }

        CanTrafficLogger.logInfo(
            String.format(
                Locale.ENGLISH,
                "[SOH_TEST_STOPPED] StopSoC=%.2f%%, StopOdo=%dkm, ΔSoC=%.2f%%, ΔkmCan=%dkm, gpsKm=%.2f, netKwh=%.4f, samples=%d",
                stopSoc, stopOdo, deltaSocPct, deltaKmCan, s.gpsDistanceKm, netEnergyKwh, s.sampleCount
            )
        )

        val result = if (deltaSocPct >= 1.0) {
            val deltaSocRatio = deltaSocPct / 100.0
            val cNettoKwh = netEnergyKwh / deltaSocRatio
            val calculatedSohPct = (cNettoKwh / CanProtocol.HONDA_E_NET_CAPACITY_KWH) * 100.0
            val avgConsumption = if (effectiveDistKm > 0.05) (netEnergyKwh / effectiveDistKm) * 100.0 else null

            SohTestResult(
                calculatedSohPct = calculatedSohPct,
                cNettoKwh = cNettoKwh,
                deltaSocPct = deltaSocPct,
                startSoc = s.startSoc,
                stopSoc = stopSoc,
                netEnergyKwh = netEnergyKwh,
                dischargeKwh = s.totalDischargeKwh,
                regenKwh = s.totalRegenKwh,
                gpsDistanceKm = s.gpsDistanceKm,
                canDeltaKm = deltaKmCan,
                startOdo = s.startOdo,
                stopOdo = stopOdo,
                avgConsumptionKwh100km = avgConsumption,
                durationMs = rawDurationMs,
                sampleCount = s.sampleCount
            )
        } else {
            emitToast(R.string.soh_test_not_enough_soc, isLong = true)
            null
        }

        _uiState.update { current ->
            current.copy(
                sohTest = current.sohTest.copy(
                    isRunning = false,
                    isStopping = false,
                    elapsedMs = rawDurationMs,
                    stopSoc = stopSoc,
                    stopOdo = stopOdo,
                    result = result
                )
            )
        }
    }

    // endregion

    // region Demo Mode

    fun toggleDemoMode() {
        if (_uiState.value.isDemoMode) {
            disableDemoMode()
        } else {
            enableDemoMode()
        }
    }

    private fun enableDemoMode() {
        connectionJob?.cancel()
        connectionJob = null
        demoJob?.cancel()

        val initialTelemetry = demoSimulator.reset()
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        _uiState.update {
            it.copy(
                isDemoMode = true,
                carConnected = false,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                telemetry = initialTelemetry,
                statusMessage = StatusMessage.UpdatedDemo(timeStr)
            )
        }
        emitToast(R.string.demo_active_toast, isLong = false)

        demoJob = viewModelScope.launch {
            var tick = 0
            while (isActive && _uiState.value.isDemoMode) {
                val runningSoh = _uiState.value.sohTest.isRunning
                delay(if (runningSoh) 400L else 600L)
                tick++

                if (_uiState.value.sohTest.isRunning) {
                    val elapsed = System.currentTimeMillis() - sohTestStartSysTimeMs
                    val (nextTel, nextSoh) = demoSimulator.stepSohTest(
                        telemetry = _uiState.value.telemetry,
                        sohState = _uiState.value.sohTest,
                        elapsedMs = elapsed
                    )
                    _uiState.update { state ->
                        val msg = if (nextSoh.sampleCount % 38 == 0) {
                            StatusMessage.UpdatedDemo(
                                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                            )
                        } else state.statusMessage
                        state.copy(telemetry = nextTel, sohTest = nextSoh, statusMessage = msg)
                    }
                } else {
                    val nextTel = demoSimulator.stepOverview(_uiState.value.telemetry)
                    val nowStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    _uiState.update { state ->
                        state.copy(
                            telemetry = nextTel,
                            statusMessage = StatusMessage.UpdatedDemo(nowStr)
                        )
                    }
                }

                if (tick % 6 == 0) {
                    postStatusNotification()
                }
            }
        }
    }

    private fun disableDemoMode() {
        demoJob?.cancel()
        demoJob = null
        if (_uiState.value.sohTest.isRunning) {
            stopSohTest()
        }

        _uiState.update {
            it.copy(
                isDemoMode = false,
                carConnected = false,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                telemetry = VehicleTelemetry(),
                sohTest = SohTestState(),
                statusMessage = StatusMessage.Idle
            )
        }
        emitToast(R.string.demo_stopped_toast, isLong = false)
    }

    // endregion

    // region Dialogs & Settings

    fun onShowCellsClicked() {
        if (!_uiState.value.telemetry.hasCellVoltageData) {
            emitToast(R.string.cells_no_data, isLong = false)
            return
        }
        _uiState.update { it.copy(showCellsDialog = true) }
    }

    fun dismissCellsDialog() {
        _uiState.update { it.copy(showCellsDialog = false) }
    }

    fun onShowModuleTempsClicked() {
        if (!_uiState.value.telemetry.hasModuleTempData) {
            emitToast(R.string.module_temps_no_data, isLong = false)
            return
        }
        _uiState.update { it.copy(showModuleTempsDialog = true) }
    }

    fun dismissModuleTempsDialog() {
        _uiState.update { it.copy(showModuleTempsDialog = false) }
    }

    fun onLanguageSelected(language: AppLanguage, activity: android.app.Activity?) {
        settingsRepository.setLanguage(language, activity)
        _uiState.update { it.copy(currentLanguage = language) }
    }

    fun onThemeModeSelected(mode: AppThemeMode) {
        settingsRepository.setThemeMode(mode)
    }

    fun onAppIconSelected(icon: AppIconColor) {
        if (selectedIcon.value == icon) return
        settingsRepository.saveSelectedIcon(icon)
        AppIconManager.switchLauncherIcon(app, icon)
        postStatusNotification()
        val colorName = app.getString(icon.labelRes)
        emitToast(app.getString(R.string.icon_changed_toast, colorName), isLong = false)
    }

    fun prepareShareLogUri(): Uri? {
        val uri = CanTrafficLogger.getShareUri(app)
        refreshLogInfo()
        if (uri == null) {
            emitToast(R.string.no_log_to_export, isLong = false)
        }
        return uri
    }

    fun notifySohTestBackBlocked() {
        emitToast(R.string.soh_test_locked_toast, isLong = false)
    }

    fun notifyShareLogError(message: String) {
        emitToast(app.getString(R.string.log_export_error, message), isLong = true)
    }

    // endregion

    // region Notifications & Helpers

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            app.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = app.getString(R.string.channel_description)
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun postStatusNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        try {
            val state = _uiState.value
            val t = state.telemetry
            val s = state.sohTest
            val iconRes = selectedIcon.value.iconRes

            val contentIntent = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                app,
                0,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val contentText = if (s.isRunning) {
                String.format(
                    Locale.ENGLISH,
                    app.getString(R.string.notification_soh_test_format),
                    s.totalNetEnergyKwh,
                    max(0.0, s.startSoc - t.soc),
                    t.powerKw
                )
            } else {
                String.format(
                    Locale.ENGLISH,
                    app.getString(R.string.notification_status_format),
                    t.soc,
                    t.powerKw,
                    t.rangeFromDash
                )
            }

            val notification = NotificationCompat.Builder(app, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(app.getString(R.string.app_name))
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            notificationManagerCompat.notify(NOTIFICATION_ID, notification)
            lastEpochNotification = System.currentTimeMillis() / 1000L
        } catch (e: Exception) {
            Log.e(TAG, "Notification post error", e)
        }
    }

    private fun gearString(gear: cz.helicode.hondaescanner.model.DriveGear): String = when (gear) {
        cz.helicode.hondaescanner.model.DriveGear.PARK -> app.getString(R.string.gear_park)
        cz.helicode.hondaescanner.model.DriveGear.DRIVE -> app.getString(R.string.gear_drive)
        cz.helicode.hondaescanner.model.DriveGear.REVERSE -> app.getString(R.string.gear_reverse)
        cz.helicode.hondaescanner.model.DriveGear.NEUTRAL -> app.getString(R.string.gear_neutral)
        cz.helicode.hondaescanner.model.DriveGear.UNKNOWN -> app.getString(R.string.gear_placeholder)
    }

    private fun updateStatusMessage(message: StatusMessage) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    private fun emitToast(resId: Int, isLong: Boolean) {
        emitToast(app.getString(resId), isLong)
    }

    private fun emitToast(message: String, isLong: Boolean) {
        _toastEvents.tryEmit(ToastEvent(message = message, isLong = isLong))
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    fun cleanupSession() {
        retryEnabled = false
        connectionJob?.cancel()
        demoJob?.cancel()
        locationJob?.cancel()
        releaseWakeLock()
        CanTrafficLogger.stopLogging()
        bluetoothClient.closeBlocking()
        runCatching { notificationManagerCompat.cancel(NOTIFICATION_ID) }
    }

    override fun onCleared() {
        cleanupSession()
    }
}
