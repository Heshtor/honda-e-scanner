package cz.helicode.hondaescanner.data.can

import cz.helicode.hondaescanner.model.ChargingConnection
import cz.helicode.hondaescanner.model.VehicleTelemetry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure, stateless Honda e UDS / ISO-TP response reassembler and telemetry decoder.
 */
object CanMessageParser {

    private val AUX_BAT_REGEX = Regex("""(\d+\.\d)V""")
    private val HEX_FRAME_REGEX = Regex("""^[0-9A-Fa-f]{10,}$""")

    data class ParseResult(
        val telemetry: VehicleTelemetry,
        val parsedValidMetric: Boolean = false,
        val detectedVin: String? = null
    )

    /**
     * Reassembles raw ELM327 response lines (with ATH1, ATS0, ATL0) into a single continuous hex payload
     * matching the exact byte offsets used by Honda e UDS DIDs.
     */
    fun reassembleResponse(rawLines: List<String>): String {
        val cleanedLines = rawLines
            .map { it.trim().removePrefix(">").trim() }
            .filter { it.isNotEmpty() }

        if (cleanedLines.isEmpty()) return ""

        // Check for AT / status / voltage responses (e.g. OK, ELM327, 14.5V, NO DATA)
        val nonHexLine = cleanedLines.firstOrNull { line ->
            line == "OK" ||
                line.contains("ELM327") ||
                line.contains("NO DATA") ||
                line.contains("CAN ERROR") ||
                line == "?" ||
                line.contains("STOPPED") ||
                AUX_BAT_REGEX.containsMatchIn(line)
        }
        if (nonHexLine != null && cleanedLines.none { HEX_FRAME_REGEX.matches(it) }) {
            return nonHexLine
        }

        // Reassemble 29-bit CAN frames: each line starts with 8-char CAN ID + 2-char PCI byte = 10 chars.
        val payload = StringBuilder()
        for (line in cleanedLines) {
            val hexOnly = line.replace(" ", "")
            if (HEX_FRAME_REGEX.matches(hexOnly)) {
                payload.append(hexOnly.substring(10))
            }
        }
        return if (payload.isNotEmpty()) payload.toString().uppercase() else cleanedLines.joinToString(" ")
    }

    /**
     * Parses a reassembled payload string and returns an updated [VehicleTelemetry] snapshot.
     */
    fun parsePayload(current: VehicleTelemetry, payload: String): ParseResult {
        if (payload.isBlank()) return ParseResult(current)

        var updated = current
        var validMetric = false
        var detectedVin: String? = null

        // 0. VIN (DID 0xF190 on ECU 07)
        if (payload.contains(CanProtocol.VIN_ID)) {
            val vin = parseVin(payload)
            if (vin != null) {
                updated = updated.copy(vin = vin)
                detectedVin = vin
                validMetric = true
            }
        }

        // 1. Ambient Temperature (DID 0x7028 on ECU 60)
        if (payload.contains(CanProtocol.AMBIENT_ID)) {
            if (payload.length >= 44) {
                val amb = payload.substring(42, 44).toIntOrNull(16)?.toByte()?.toInt()
                if (amb != null) {
                    updated = updated.copy(ambientTemp = amb)
                    validMetric = true
                }
            }
        }
        // 2. Battery SOH, Pack Volts, Pack Amps, kW (DID 0x2021 on ECU 15)
        else if (payload.contains(CanProtocol.SOH_ID)) {
            if (payload.length >= 284) {
                val sohRaw = payload.substring(198, 202).toIntOrNull(16)
                val ampRaw = payload.substring(280, 284).toIntOrNull(16)?.toShort()
                val voltRaw = payload.substring(76, 80).toIntOrNull(16)
                if (sohRaw != null && ampRaw != null && voltRaw != null) {
                    val soh = sohRaw / 100.0
                    val amp = ((ampRaw / 34.0) * 100.0).roundToInt() / 100.0
                    val volt = voltRaw / 10.0
                    val power = ((amp * volt / 1000.0) * 100.0).roundToInt() / 100.0
                    val usableEnergy = if (soh > 0 && updated.soc > 0) {
                        ((CanProtocol.HONDA_E_NET_CAPACITY_KWH * (soh / 100.0) * (updated.soc / 100.0)) * 10.0).roundToInt() / 10.0
                    } else updated.usableEnergyKwh
                    updated = updated.copy(
                        soh = soh,
                        amp = amp,
                        volt = volt,
                        powerKw = power,
                        usableEnergyKwh = usableEnergy
                    )
                    validMetric = true
                }
            }
        }
        // 3. Battery SOC, Min/Max cell SOC, Charging Status (DID 0x2029 on ECU 15)
        else if (payload.contains(CanProtocol.SOC_ID)) {
            if (payload.length >= 278) {
                val socMinRaw = payload.substring(142, 146).toIntOrNull(16)
                val socMaxRaw = payload.substring(138, 142).toIntOrNull(16)
                val socRaw = payload.substring(156, 160).toIntOrNull(16)
                if (socMinRaw != null && socMaxRaw != null && socRaw != null) {
                    val socMin = socMinRaw / 100.0
                    val socMax = socMaxRaw / 100.0
                    val socDelta = ((socMax - socMin) * 100.0).roundToInt() / 100.0
                    val soc = socRaw / 100.0
                    val isCharging = payload[161] == '1'
                    val chargingConnection = when (payload.substring(277, 278)) {
                        "2" -> ChargingConnection.AC
                        "3" -> ChargingConnection.DC
                        else -> ChargingConnection.NC
                    }
                    val usableEnergy = if (updated.soh > 0 && soc > 0) {
                        ((CanProtocol.HONDA_E_NET_CAPACITY_KWH * (updated.soh / 100.0) * (soc / 100.0)) * 10.0).roundToInt() / 10.0
                    } else updated.usableEnergyKwh

                    updated = updated.copy(
                        socMin = socMin,
                        socMax = socMax,
                        socDelta = socDelta,
                        soc = soc,
                        isCharging = isCharging,
                        chargingConnection = chargingConnection,
                        usableEnergyKwh = usableEnergy
                    )
                    validMetric = true
                }
            }
        }
        // 4. Battery Individual Cell Voltages (DID 0x2028 on ECU 01) - 96 cells
        else if (payload.contains(CanProtocol.CELLS_V_ID)) {
            val idx = payload.indexOf(CanProtocol.CELLS_V_ID)
            val start = idx + 60 // 30 bytes header offset before Cell 1
            if (idx >= 0 && payload.length >= start + (96 * 4)) {
                val cells = MutableList(96) { 0.0 }
                var minV = 999.0
                var maxV = 0.0
                var sumV = 0.0
                for (i in 0 until 96) {
                    val offset = start + (i * 4)
                    val rawWord = payload.substring(offset, offset + 4).toIntOrNull(16) ?: continue
                    val cellV = rawWord * 0.0002
                    cells[i] = cellV
                    if (cellV > 0.5 && cellV < 5.0) {
                        if (cellV < minV) minV = cellV
                        if (cellV > maxV) maxV = cellV
                        sumV += cellV
                    }
                }
                if (minV < 100.0 && maxV > 0.0) {
                    updated = updated.copy(
                        cellVoltages = cells,
                        cellMinV = minV,
                        cellMaxV = maxV,
                        cellAvgV = sumV / 96.0,
                        cellMinMv = (minV * 1000.0).roundToInt(),
                        cellMaxMv = (maxV * 1000.0).roundToInt(),
                        cellDeltaMv = ((maxV - minV) * 1000.0).roundToInt()
                    )
                    validMetric = true
                }
            }
        }
        // 5. Battery Module Temperatures (DID 0x202C on ECU 01) - 6 sensors
        else if (payload.contains(CanProtocol.BAT_MODULE_TEMPS_ID)) {
            val idx = payload.indexOf(CanProtocol.BAT_MODULE_TEMPS_ID)
            val start = idx + 124 // 62 bytes header offset
            if (idx >= 0 && payload.length >= start + (6 * 4)) {
                val temps = MutableList(6) { 0.0 }
                var minT = 999.0
                var maxT = -999.0
                var sumT = 0.0
                var validCount = 0
                for (i in 0 until 6) {
                    val offset = start + (i * 4)
                    val rawVal = payload.substring(offset, offset + 4).toIntOrNull(16)?.toShort() ?: continue
                    val t = rawVal / 10.0
                    temps[i] = t
                    if (t > -40.0 && t < 100.0) {
                        if (t < minT) minT = t
                        if (t > maxT) maxT = t
                        sumT += t
                        validCount++
                    }
                }
                if (validCount > 0) {
                    val avgT = ((sumT / validCount) * 10.0).roundToInt() / 10.0
                    val spread = ((maxT - minT) * 10.0).roundToInt() / 10.0
                    updated = updated.copy(
                        moduleTemps = temps,
                        batTempMin = minT,
                        batTempMax = maxT,
                        batTemp = avgT,
                        batTempSpread = spread
                    )
                    validMetric = true
                }
            }
        }
        // 6. Battery Pack Telemetry / Temperatures fallback (DID 0x202A on ECU 01)
        else if (payload.contains(CanProtocol.BATTEMP_ID)) {
            if (updated.batTempMin <= -50.0) {
                if (payload.length >= 418) {
                    val t1 = payload.substring(410, 414).toIntOrNull(16)?.toShort()?.let { it / 10.0 }
                    val t2 = payload.substring(414, 418).toIntOrNull(16)?.toShort()?.let { it / 10.0 }
                    if (t1 != null && t2 != null) {
                        val bMax = max(t1, t2)
                        val bMin = min(t1, t2)
                        val bAvg = (((bMin + bMax) / 2.0) * 10.0).roundToInt() / 10.0
                        val spread = ((bMax - bMin) * 10.0).roundToInt() / 10.0
                        updated = updated.copy(
                            batTempMin = bMin,
                            batTempMax = bMax,
                            batTemp = bAvg,
                            batTempSpread = spread
                        )
                        validMetric = true
                    }
                } else if (payload.length >= 414) {
                    val t1 = payload.substring(410, 414).toIntOrNull(16)?.toShort()?.let { it / 10.0 }
                    if (t1 != null) {
                        updated = updated.copy(
                            batTempMin = t1,
                            batTempMax = t1,
                            batTemp = t1,
                            batTempSpread = 0.0
                        )
                        validMetric = true
                    }
                }
            }
        }
        // 7. Electric Motor RPM (DID 0x2001 on ECU 01)
        else if (payload.contains(CanProtocol.MOTOR_RPM_ID)) {
            val idx = payload.indexOf(CanProtocol.MOTOR_RPM_ID)
            if (idx >= 0 && payload.length >= idx + 10) {
                val rpm = payload.substring(idx + 6, idx + 10).toIntOrNull(16)?.toShort()?.toInt()
                if (rpm != null) {
                    updated = updated.copy(motorRpm = rpm)
                    validMetric = true
                }
            }
        }
        // 8. ODO / Odometer (DID 0x7022 on ECU 60)
        else if (payload.contains(CanProtocol.ODO_ID)) {
            if (payload.length >= 26) {
                val odo = payload.substring(18, 26).toIntOrNull(16)
                if (odo != null) {
                    updated = updated.copy(odo = odo)
                    validMetric = true
                }
            }
        }
        // 9. Dashboard Estimated Range (DID 0x702A on ECU 60)
        else if (payload.contains(CanProtocol.RANGE_DASH_ID)) {
            val idx = payload.indexOf(CanProtocol.RANGE_DASH_ID)
            if (idx >= 0 && payload.length >= idx + 38) {
                val range = payload.substring(idx + 34, idx + 38).toIntOrNull(16)
                if (range != null) {
                    updated = updated.copy(rangeFromDash = range)
                    validMetric = true
                }
            }
        }
        // 10. 12V Auxiliary Battery Voltage (ATRV)
        else {
            val match = AUX_BAT_REGEX.find(payload)
            val auxV = match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            if (auxV != null) {
                updated = updated.copy(auxBat = auxV)
                validMetric = true
            }
        }

        // Compute fallback cell delta from SoC delta if real 96-cell frame not yet received
        if (!updated.hasCellVoltageData && updated.volt > 0.0) {
            val avgCellV = updated.volt / 96.0
            if (updated.socDelta > 0.0) {
                val deltaMv = (updated.socDelta * 6.5).roundToInt()
                val halfDeltaV = deltaMv / 2000.0
                val minV = avgCellV - halfDeltaV
                val maxV = avgCellV + halfDeltaV
                updated = updated.copy(
                    cellMinMv = (minV * 1000.0).roundToInt(),
                    cellMaxMv = (maxV * 1000.0).roundToInt(),
                    cellDeltaMv = deltaMv
                )
            }
        }

        return ParseResult(updated, validMetric, detectedVin)
    }

    private fun parseVin(payload: String): String? {
        val idx = payload.indexOf(CanProtocol.VIN_ID)
        val hexPart = when {
            idx >= 0 && payload.length >= idx + 42 -> payload.substring(idx + 8, idx + 42)
            payload.length >= 44 -> payload.substring(10, 44)
            else -> return null
        }
        val decoded = hexToAscii(hexPart).trim()
        return decoded.takeIf { it.isNotEmpty() && it != "HONDA_E" }
    }

    private fun hexToAscii(hexStr: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 2 <= hexStr.length) {
            val code = hexStr.substring(i, i + 2).toIntOrNull(16)
            if (code != null && code in 32..126) {
                sb.append(code.toChar())
            }
            i += 2
        }
        return sb.toString()
    }
}
