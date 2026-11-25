package cz.helicode.hondaescanner.data.demo

import cz.helicode.hondaescanner.model.ChargingConnection
import cz.helicode.hondaescanner.model.SohTestState
import cz.helicode.hondaescanner.model.VehicleTelemetry
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates realistic synthetic Honda e driving telemetry and SoH test progression for offline Demo Mode.
 */
class DemoSimulator {

    private var tickCounter = 0
    private var simulatedDistanceKm = 0.0

    fun reset(): VehicleTelemetry {
        tickCounter = 0
        simulatedDistanceKm = 0.0

        val cells = List(96) { idx ->
            when (idx) {
                0 -> 3.834
                95 -> 3.840
                else -> 3.834 + (Random.nextDouble() * 0.006)
            }
        }
        val moduleTemps = listOf(20.2, 19.8, 20.6, 20.1, 20.4, 19.9)

        return VehicleTelemetry(
            vin = "JHMZC7DEMO000001",
            odo = 15000,
            rangeFromDash = 142,
            soc = 76.50,
            socMin = 78.20,
            socMax = 78.70,
            socDelta = 0.50,
            soh = 93.40,
            usableEnergyKwh = 20.4,
            powerKw = 0.35,
            amp = 0.95,
            volt = 368.4,
            instantConsumption = 0.0,
            cellVoltages = cells,
            cellMinV = 3.834,
            cellMaxV = 3.840,
            cellAvgV = 3.837,
            cellMinMv = 3834,
            cellMaxMv = 3840,
            cellDeltaMv = 6,
            moduleTemps = moduleTemps,
            batTempMin = 19.8,
            batTempMax = 20.6,
            batTemp = 20.2,
            batTempSpread = 0.8,
            ambientTemp = 21,
            auxBat = 14.3,
            speedGps = 0.0,
            speedCan = 0,
            motorRpm = 0,
            chargingConnection = ChargingConnection.NC,
            isCharging = false,
            lat = String.format(Locale.ENGLISH, "%.7f", 35.6653),
            lon = String.format(Locale.ENGLISH, "%.7f", 139.7214),
            elevation = 25.0
        )
    }

    fun stepOverview(current: VehicleTelemetry): VehicleTelemetry {
        tickCounter++
        val cycleTick = tickCounter % 60

        val targetSpeed: Double
        val targetPower: Double
        val targetVolt: Double
        val motorRpm: Int

        when {
            cycleTick < 8 -> {
                targetSpeed = 0.0
                targetPower = 0.32 + Random.nextDouble() * 0.08
                targetVolt = 368.4 + Random.nextDouble() * 0.3
                motorRpm = 0
            }
            cycleTick < 22 -> {
                val progress = (cycleTick - 8) / 14.0
                targetSpeed = (52.0 * progress * (0.95 + Random.nextDouble() * 0.1)).roundToInt().toDouble()
                targetPower = 12.0 + (16.0 * sin(progress * PI)) + (Random.nextDouble() * 2.5)
                targetVolt = 365.2 - (progress * 1.5) + (Random.nextDouble() * 0.4)
                motorRpm = (targetSpeed * 50).toInt()
            }
            cycleTick < 40 -> {
                targetSpeed = 50.0 + (sin(cycleTick * 0.5) * 3.5) + ((Random.nextDouble() - 0.5) * 1.5)
                targetPower = 6.5 + (sin(cycleTick * 0.4) * 2.5) + (Random.nextDouble() * 1.2)
                targetVolt = 367.2 + (Random.nextDouble() * 0.4)
                motorRpm = (targetSpeed * 50).toInt()
            }
            cycleTick < 54 -> {
                val regenProgress = (cycleTick - 40) / 14.0
                targetSpeed = max(0.0, (50.0 * (1.0 - regenProgress)).roundToInt().toDouble())
                if (targetSpeed > 3.0) {
                    targetPower = -(7.5 + (12.0 * sin(regenProgress * PI)) + (Random.nextDouble() * 2.0))
                    targetVolt = 370.5 + (regenProgress * 1.5) + (Random.nextDouble() * 0.4)
                } else {
                    targetPower = 0.35
                    targetVolt = 368.4
                }
                motorRpm = (targetSpeed * 50).toInt()
            }
            else -> {
                targetSpeed = 0.0
                targetPower = 0.35 + (Random.nextDouble() * 0.05)
                targetVolt = 368.4 + (Random.nextDouble() * 0.2)
                motorRpm = 0
            }
        }

        val speed = max(0.0, (targetSpeed * 10.0).roundToInt() / 10.0)
        val power = (targetPower * 100.0).roundToInt() / 100.0
        val volt = (targetVolt * 10.0).roundToInt() / 10.0
        val amp = ((power * 1000.0 / volt) * 100.0).roundToInt() / 100.0

        var odo = current.odo
        if (speed > 0) {
            simulatedDistanceKm += (speed * (0.6 / 3600.0))
            if (simulatedDistanceKm >= 1.0) {
                val wholeKm = simulatedDistanceKm.toInt()
                odo += wholeKm
                simulatedDistanceKm -= wholeKm
            }
        }

        val avgCellV = volt / 96.0
        var minCell = avgCellV
        var maxCell = avgCellV
        val cells = List(96) { i ->
            val offset = (((i * 7) % 11) - 5) * 0.0006
            val jitter = (Random.nextDouble() - 0.5) * 0.002
            val v = ((avgCellV + offset + jitter) * 1000.0).roundToInt() / 1000.0
            if (v < minCell) minCell = v
            if (v > maxCell) maxCell = v
            v
        }
        val minMv = (minCell * 1000.0).roundToInt()
        val maxMv = (maxCell * 1000.0).roundToInt()

        val tBase = 20.2
        val moduleTemps = listOf(
            ((tBase + 0.1 + sin(tickCounter * 0.06) * 0.2) * 10.0).roundToInt() / 10.0,
            ((tBase - 0.4 + cos(tickCounter * 0.05) * 0.2) * 10.0).roundToInt() / 10.0,
            ((tBase + 0.5 + sin(tickCounter * 0.07) * 0.2) * 10.0).roundToInt() / 10.0,
            ((tBase - 0.1 + cos(tickCounter * 0.06) * 0.2) * 10.0).roundToInt() / 10.0,
            ((tBase + 0.3 + sin(tickCounter * 0.05) * 0.2) * 10.0).roundToInt() / 10.0,
            ((tBase - 0.3 + cos(tickCounter * 0.07) * 0.2) * 10.0).roundToInt() / 10.0
        )
        val batTempMin = moduleTemps.min()
        val batTempMax = moduleTemps.max()
        val batTempSpread = ((batTempMax - batTempMin) * 10.0).roundToInt() / 10.0
        val batTempAvg = (((batTempMin + batTempMax) / 2.0) * 10.0).roundToInt() / 10.0

        val auxBat = ((14.2 + sin(tickCounter * 0.1) * 0.15 + Random.nextDouble() * 0.05) * 10.0).roundToInt() / 10.0
        val simLat = 35.6653 + (sin(tickCounter * 0.03) * 0.00035)
        val simLon = 139.7214 + (cos(tickCounter * 0.03) * 0.00035)
        val elevation = (25.0 + sin(tickCounter * 0.08) * 3.0).roundToInt().toDouble()

        val instantConsumption = if (speed > 2.0) (power / speed) * 100.0 else 0.0

        return current.copy(
            odo = odo,
            speedGps = speed,
            speedCan = speed.roundToInt(),
            motorRpm = motorRpm,
            powerKw = power,
            volt = volt,
            amp = amp,
            instantConsumption = instantConsumption,
            cellVoltages = cells,
            cellMinV = minCell,
            cellMaxV = maxCell,
            cellAvgV = avgCellV,
            cellMinMv = minMv,
            cellMaxMv = maxMv,
            cellDeltaMv = maxMv - minMv,
            moduleTemps = moduleTemps,
            batTempMin = batTempMin,
            batTempMax = batTempMax,
            batTemp = batTempAvg,
            batTempSpread = batTempSpread,
            auxBat = auxBat,
            lat = String.format(Locale.ENGLISH, "%.7f", simLat),
            lon = String.format(Locale.ENGLISH, "%.7f", simLon),
            elevation = elevation
        )
    }

    fun stepSohTest(
        telemetry: VehicleTelemetry,
        sohState: SohTestState,
        elapsedMs: Long
    ): Pair<VehicleTelemetry, SohTestState> {
        tickCounter++
        val currentAmp = if ((sohState.sampleCount % 20) >= 16) {
            -(45.0 + Random.nextDouble() * 25.0)
        } else {
            115.0 + Random.nextDouble() * 35.0
        }
        val volt = 360.0 + Random.nextDouble() * 6.0
        val power = ((volt * currentAmp / 1000.0) * 100.0).roundToInt() / 100.0

        val dtSeconds = 0.4
        val deltaKwh = (power * 1000.0 * dtSeconds) / 3_600_000.0
        val netKwh = sohState.totalNetEnergyKwh + deltaKwh
        val dischargeKwh = sohState.totalDischargeKwh + if (deltaKwh > 0) deltaKwh else 0.0
        val regenKwh = sohState.totalRegenKwh + if (deltaKwh < 0) abs(deltaKwh) else 0.0
        val samples = sohState.sampleCount + 1
        val gpsDist = sohState.gpsDistanceKm + 0.035

        val odo = sohState.startOdo + gpsDist.roundToInt()
        val speed = 125.0 + Random.nextDouble() * 12.0
        val deltaSocPct = (netKwh / 26.39) * 100.0
        val soc = max(10.0, ((sohState.startSoc - deltaSocPct) * 100.0).roundToInt() / 100.0)
        val instantConsumption = (power / speed) * 100.0

        val updatedTelemetry = telemetry.copy(
            volt = volt,
            amp = currentAmp,
            powerKw = power,
            odo = odo,
            speedGps = speed,
            speedCan = speed.roundToInt(),
            motorRpm = (speed * 50).toInt(),
            soc = soc,
            instantConsumption = instantConsumption
        )

        val updatedSoh = sohState.copy(
            elapsedMs = elapsedMs,
            sampleCount = samples,
            totalNetEnergyKwh = netKwh,
            totalDischargeKwh = dischargeKwh,
            totalRegenKwh = regenKwh,
            gpsDistanceKm = gpsDist
        )

        return updatedTelemetry to updatedSoh
    }
}
