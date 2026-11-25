package cz.helicode.hondaescanner.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import cz.helicode.hondaescanner.R
import java.util.Date
import java.util.Locale

data class BluetoothDeviceItem(
    val name: String,
    val macAddress: String
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class ChargingConnection {
    NC,
    AC,
    DC
}

enum class DriveGear {
    UNKNOWN,
    PARK,
    DRIVE,
    REVERSE,
    NEUTRAL
}

enum class AppThemeMode(val prefValue: Int) {
    // Values match legacy AppCompatDelegate night mode constants for seamless upgrades
    DARK(2),
    LIGHT(1),
    SYSTEM(-1);

    companion object {
        fun fromPrefValue(mode: Int): AppThemeMode =
            entries.firstOrNull { it.prefValue == mode } ?: DARK
    }
}

enum class AppLanguage(val tag: String) {
    CZECH("cs"),
    ENGLISH("en")
}

enum class AppIconColor(
    val key: String,
    val aliasClassName: String,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val labelRes: Int
) {
    WHITE("white", "cz.helicode.hondaescanner.MainActivityWhite", R.mipmap.logo_white, R.string.color_white),
    BLACK("black", "cz.helicode.hondaescanner.MainActivityBlack", R.mipmap.logo_black, R.string.color_black),
    GREY("grey", "cz.helicode.hondaescanner.MainActivityGrey", R.mipmap.logo_grey, R.string.color_grey),
    RED("red", "cz.helicode.hondaescanner.MainActivityRed", R.mipmap.logo_red, R.string.color_red),
    BLUE("blue", "cz.helicode.hondaescanner.MainActivityBlue", R.mipmap.logo_blue, R.string.color_blue),
    YELLOW("yellow", "cz.helicode.hondaescanner.MainActivityYellow", R.mipmap.logo_yellow, R.string.color_yellow);

    companion object {
        fun fromKey(key: String?): AppIconColor =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: WHITE
    }
}

data class CanLogInfo(
    val exists: Boolean = false,
    val isRecording: Boolean = false,
    val startDate: Date? = null,
    val fileSizeBytes: Long = 0L
) {
    fun formattedSize(): String = when {
        fileSizeBytes < 1024 -> "$fileSizeBytes B"
        fileSizeBytes < 1024 * 1024 -> String.format(Locale.ENGLISH, "%.1f KB", fileSizeBytes / 1024.0)
        else -> String.format(Locale.ENGLISH, "%.2f MB", fileSizeBytes / (1024.0 * 1024.0))
    }
}

data class VehicleTelemetry(
    val soc: Double = 0.0,
    val socMin: Double = 0.0,
    val socMax: Double = 0.0,
    val socDelta: Double = 0.0,
    val soh: Double = 0.0,
    val usableEnergyKwh: Double = 0.0,
    val rangeFromDash: Int = 0,

    val cellVoltages: List<Double> = List(96) { 0.0 },
    val cellMinV: Double = 0.0,
    val cellMaxV: Double = 0.0,
    val cellAvgV: Double = 0.0,
    val cellMinMv: Int = 0,
    val cellMaxMv: Int = 0,
    val cellDeltaMv: Int = 0,

    val chargingConnection: ChargingConnection = ChargingConnection.NC,
    val isCharging: Boolean = false,
    val powerKw: Double = 0.0,
    val amp: Double = 0.0,
    val volt: Double = 0.0,
    val instantConsumption: Double = 0.0,

    val moduleTemps: List<Double> = List(6) { 0.0 },
    val batTemp: Double = -100.0,
    val batTempMin: Double = -100.0,
    val batTempMax: Double = -100.0,
    val batTempSpread: Double = 0.0,
    val invTemp: Int = 0,
    val ambientTemp: Int = -100,
    val auxBat: Double = 0.0,

    val odo: Int = 0,
    val speedGps: Double = 0.0,
    val speedCan: Int = 0,
    val motorRpm: Int = 0,
    val lat: String = "",
    val lon: String = "",
    val elevation: Double = 0.0,
    val vin: String = ""
) {
    val effectiveSpeed: Double
        get() = if (speedCan > 0) speedCan.toDouble() else speedGps

    val hasCellVoltageData: Boolean
        get() = cellVoltages.firstOrNull()?.let { it > 0.0 } == true || cellMinV > 0.0

    val hasModuleTempData: Boolean
        get() = moduleTemps.any { it > -40.0 && it < 100.0 && it != 0.0 } || batTempMin > -50.0

    fun computeGear(isActiveSession: Boolean): DriveGear {
        if (!isActiveSession) return DriveGear.UNKNOWN
        return when {
            motorRpm > 50 || speedGps > 2.0 -> DriveGear.DRIVE
            motorRpm < -50 -> DriveGear.REVERSE
            speedGps <= 1.0 && kotlin.math.abs(motorRpm) < 20 -> DriveGear.PARK
            else -> DriveGear.NEUTRAL
        }
    }
}

data class SohTestResult(
    val calculatedSohPct: Double,
    val cNettoKwh: Double,
    val deltaSocPct: Double,
    val startSoc: Double,
    val stopSoc: Double,
    val netEnergyKwh: Double,
    val dischargeKwh: Double,
    val regenKwh: Double,
    val gpsDistanceKm: Double,
    val canDeltaKm: Int,
    val startOdo: Int,
    val stopOdo: Int,
    val avgConsumptionKwh100km: Double?,
    val durationMs: Long,
    val sampleCount: Int
)

data class SohTestState(
    val isRunning: Boolean = false,
    val isStopping: Boolean = false,
    val showLiveCard: Boolean = false,
    val elapsedMs: Long = 0L,
    val sampleCount: Int = 0,
    val startSoc: Double = 0.0,
    val stopSoc: Double? = null,
    val startOdo: Int = 0,
    val stopOdo: Int? = null,
    val totalNetEnergyKwh: Double = 0.0,
    val totalDischargeKwh: Double = 0.0,
    val totalRegenKwh: Double = 0.0,
    val gpsDistanceKm: Double = 0.0,
    val result: SohTestResult? = null
)

sealed interface StatusMessage {
    data object Idle : StatusMessage
    data object CanConnecting : StatusMessage
    data class CanWaiting(val intervalMs: Int) : StatusMessage
    data class Updated(val timeStr: String) : StatusMessage
    data class UpdatedDemo(val timeStr: String) : StatusMessage
    data class CanError(val errorDetail: String?) : StatusMessage
}

data class ToastEvent(
    val id: Long = System.nanoTime(),
    val message: String,
    val isLong: Boolean = false
)
