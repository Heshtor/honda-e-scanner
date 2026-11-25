package cz.helicode.hondaescanner.data.can

object CanProtocol {
    const val CAN_BUS_SCAN_INTERVAL_MS = 1000L
    const val COMMAND_RESPONSE_TIMEOUT_MS = 350L
    const val COMMAND_DELAY_MS = 40L
    const val HONDA_E_NET_CAPACITY_KWH = 28.5
    const val MAX_RETRY = 5

    // Service 0x22 -> Positive response 0x62 DIDs
    const val VIN_ID = "62F190"
    const val AMBIENT_ID = "627028"
    const val ODO_ID = "627022"
    const val RANGE_DASH_ID = "62702A"
    const val SOH_ID = "622021"
    const val SOC_ID = "622029"
    const val CELLS_V_ID = "622028"
    const val BAT_MODULE_TEMPS_ID = "62202C"
    const val BATTEMP_ID = "62202A"
    const val MOTOR_RPM_ID = "622001"

    val CONNECTION_COMMANDS = listOf(
        "ATWS",
        "ATE0",
        "ATSP7",
        "ATAT1",
        "ATH1",
        "ATL0",
        "ATS0",
        "ATRV",
        "ATAL",
        "ATCAF1",
        "ATSHDA01F1",
        "ATFCSH18DA01F1",
        "ATFCSD300000",
        "ATFCSM1",
        "ATCFC1",
        "ATCP18",
        "ATSHDA07F1",
        "ATFCSH18DA07F1",
        "ATCRA18DAF107",
        "22F190" // VIN
    )

    val LOOP_COMMANDS = listOf(
        // 1. METER / CLUSTER (ECU 60)
        "ATSHDA60F1",
        "ATFCSH18DA60F1",
        "ATCRA18DAF160",
        "227028", // Ambient temperature (°C)
        "227022", // ODO / Odometer in km
        "22702A", // Instrument cluster dashboard range in km

        // 2. BMS / TRACTION BATTERY (ECU 15)
        "ATSHDA15F1",
        "ATFCSH18DA15F1",
        "ATCRA18DAF115",
        "222021", // SOH (%), Pack Voltage (V), Current (A), Power (kW)
        "222029", // SOC (%), Charging status (AC/DC)

        // 3. POWERTRAIN / VCU / MOTOR (ECU 01)
        "ATSHDA01F1",
        "ATFCSH18DA01F1",
        "ATCRA18DAF101",
        "222028", // 96 individual cell voltages (0.0002 V/bit)
        "22202C", // 6 battery module temperatures (°C)
        "22202A", // Traction battery telemetry / temperatures on ECU 01

        // 4. 12V AUXILIARY BATTERY
        "ATRV"    // 12V auxiliary system voltage
    )

    val ECU_60_HEADER_COMMANDS = listOf(
        "ATSHDA60F1",
        "ATFCSH18DA60F1",
        "ATCRA18DAF160"
    )

    val ECU_15_HEADER_COMMANDS = listOf(
        "ATSHDA15F1",
        "ATFCSH18DA15F1",
        "ATCRA18DAF115"
    )

    val ECU_01_HEADER_COMMANDS = listOf(
        "ATSHDA01F1",
        "ATFCSH18DA01F1",
        "ATCRA18DAF101"
    )
}
