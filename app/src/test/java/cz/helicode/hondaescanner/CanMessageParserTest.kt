package cz.helicode.hondaescanner

import cz.helicode.hondaescanner.data.can.CanMessageParser
import cz.helicode.hondaescanner.model.ChargingConnection
import cz.helicode.hondaescanner.model.VehicleTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Honda e UDS / ISO-TP frame reassembly and decoding
 * against authentic CAN traffic captured from a real Honda e Advance.
 */
class CanMessageParserTest {

    @Test
    fun parseVin_realHondaEFrame() {
        val rawLines = listOf(
            "18DAF107101862F190114A48",
            "18DAF107214D5A4337383430",
            "18DAF107224C583230353935",
            "18DAF1072331000000555555"
        )
        val payload = CanMessageParser.reassembleResponse(rawLines)
        val result = CanMessageParser.parsePayload(VehicleTelemetry(), payload)

        assertTrue(result.parsedValidMetric)
        assertEquals("JHMZC7840LX205951", result.detectedVin)
        assertEquals("JHMZC7840LX205951", result.telemetry.vin)
    }

    @Test
    fun parseOdo_realHondaEFrame() {
        val rawLines = listOf(
            "18DAF1601039627022EC0000",
            "18DAF16021000000005A5F00",
            "18DAF1602200000000000000"
        )
        val payload = CanMessageParser.reassembleResponse(rawLines)
        val result = CanMessageParser.parsePayload(VehicleTelemetry(), payload)

        assertTrue(result.parsedValidMetric)
        assertEquals(23135, result.telemetry.odo)
    }

    @Test
    fun parseDashRange_realHondaEFrame() {
        val rawLines = listOf(
            "18DAF160103962702A3FC000",
            "18DAF1602100000000000000",
            "18DAF1602200000000004800"
        )
        val payload = CanMessageParser.reassembleResponse(rawLines)
        val result = CanMessageParser.parsePayload(VehicleTelemetry(), payload)

        assertTrue(result.parsedValidMetric)
        assertEquals(72, result.telemetry.rangeFromDash)
    }

    @Test
    fun parseSohVoltAmpPower_realHondaEFrame() {
        val rawLines = listOf(
            "18DAF11510F6622021DFEF3F",
            "18DAF11521FFFFFFFFFFFFFF",
            "18DAF11522FFFFFFFFFFFFFF",
            "18DAF11523FFFFFFFF3CFFFF",
            "18DAF11524F30F0064270000",
            "18DAF1152522FFF10DACFFF9",
            "18DAF1152600060601030000",
            "18DAF1152703020000000000",
            "18DAF115280000FF00FB8228",
            "18DAF1152928282801010101",
            "18DAF1152A0100011F000000",
            "18DAF1152B00000000000000",
            "18DAF1152C00000000000000",
            "18DAF1152D00000000100400",
            "18DAF1152E001FD4000005DC",
            "18DAF1152F000000000005AC",
            "18DAF11520059C05ADEA6027",
            "18DAF1152110114E4E0000FF",
            "18DAF1152200FB20FF280000",
            "18DAF1152300000001000010",
            "18DAF115240023333CD1D20E"
        )
        val payload = CanMessageParser.reassembleResponse(rawLines)
        val result = CanMessageParser.parsePayload(VehicleTelemetry(), payload)

        assertTrue(result.parsedValidMetric)
        assertEquals(350.0, result.telemetry.volt, 0.01)
        assertEquals(1.03, result.telemetry.amp, 0.01)
        assertEquals(0.36, result.telemetry.powerKw, 0.01)
    }

    @Test
    fun parseSocAndCharging_realHondaEFrame() {
        val rawLines = listOf(
            "18DAF11510F6622029FFFFFF",
            "18DAF11521FFFFFFFCFFF9FF",
            "18DAF11522FFFFFFFFFF0000",
            "18DAF1152300000000000000",
            "18DAF1152400000000000000",
            "18DAF1152500000050DC0000",
            "18DAF11526000000FF00FD6C",
            "18DAF11527003C003251B1F8",
            "18DAF115281D47380005FBE0",
            "18DAF115291B54000B002210",
            "18DAF1152AFA109D00000000",
            "18DAF1152B010ED60000001F",
            "18DAF1152C800022F82000FF",
            "18DAF1152DFF000000000000",
            "18DAF1152E12FE0000000000",
            "18DAF1152F00000000000000",
            "18DAF1152000000000000000",
            "18DAF1152100000000900000",
            "18DAF1152200000000000000",
            "18DAF1152300000000000000"
        )
        val payload = CanMessageParser.reassembleResponse(rawLines)
        val result = CanMessageParser.parsePayload(VehicleTelemetry(), payload)

        assertTrue(result.parsedValidMetric)
        assertEquals(37.98, result.telemetry.soc, 0.001)
        assertFalse(result.telemetry.isCharging)
        assertEquals(ChargingConnection.NC, result.telemetry.chargingConnection)
    }

    @Test
    fun parseAuxBatteryVoltage_atrvResponse() {
        val payload = CanMessageParser.reassembleResponse(listOf("14.5V"))
        val result = CanMessageParser.parsePayload(VehicleTelemetry(), payload)

        assertTrue(result.parsedValidMetric)
        assertEquals(14.5, result.telemetry.auxBat, 0.001)
    }
}
