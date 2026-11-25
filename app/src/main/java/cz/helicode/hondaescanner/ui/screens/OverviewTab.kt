package cz.helicode.hondaescanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.helicode.hondaescanner.R
import cz.helicode.hondaescanner.model.ChargingConnection
import cz.helicode.hondaescanner.model.DriveGear
import cz.helicode.hondaescanner.ui.components.HondaActionButton
import cz.helicode.hondaescanner.ui.components.MetricRow
import cz.helicode.hondaescanner.ui.components.SectionCard
import cz.helicode.hondaescanner.ui.theme.HondaTheme
import cz.helicode.hondaescanner.viewmodel.ScannerUiState
import java.util.Locale
import kotlin.math.abs

@Composable
fun OverviewTab(
    uiState: ScannerUiState,
    onShowCellsClick: () -> Unit,
    onShowModuleTempsClick: () -> Unit
) {
    val colors = HondaTheme.colors
    val t = uiState.telemetry
    val isActiveSession = uiState.carConnected || uiState.isDemoMode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. BATTERY & RANGE CARD
        SectionCard(title = stringResource(R.string.battery_and_range)) {
            val socText = if (t.soc > 0) String.format(Locale.ENGLISH, "%.2f %%", t.soc)
            else stringResource(R.string.soc_placeholder)

            val socRangeText = if (t.socMin > 0 && t.socMax > 0) {
                String.format(Locale.ENGLISH, "%.2f %% / %.2f %%", t.socMin, t.socMax)
            } else stringResource(R.string.soc_range_placeholder)

            val socDeltaText = if (t.socMin > 0 && t.socMax > 0) {
                String.format(Locale.ENGLISH, "%.2f %%", t.socDelta)
            } else stringResource(R.string.soc_delta_placeholder)

            val (cellVoltText, cellDeltaText) = when {
                t.cellMinV > 0.0 && t.cellMaxV > 0.0 -> {
                    String.format(Locale.ENGLISH, "%.3f V / %.3f V", t.cellMinV, t.cellMaxV) to
                        String.format(Locale.ENGLISH, "%d mV", t.cellDeltaMv)
                }
                t.volt > 0.0 -> {
                    val avgCellV = t.volt / 96.0
                    if (t.socDelta > 0.0) {
                        val halfDeltaV = t.cellDeltaMv / 2000.0
                        String.format(Locale.ENGLISH, "%.3f V / %.3f V", avgCellV - halfDeltaV, avgCellV + halfDeltaV) to
                            String.format(Locale.ENGLISH, "%d mV", t.cellDeltaMv)
                    } else {
                        String.format(Locale.ENGLISH, "%.3f V / %.3f V", avgCellV, avgCellV) to "0 mV"
                    }
                }
                else -> stringResource(R.string.cell_volt_placeholder) to stringResource(R.string.cell_delta_placeholder)
            }

            val sohText = if (t.soh > 0) String.format(Locale.ENGLISH, "%.2f %% (%.2f Ah)", t.soh, t.soh)
            else stringResource(R.string.soh_placeholder)

            val energyText = if (t.usableEnergyKwh > 0) String.format(Locale.ENGLISH, "%.1f kWh", t.usableEnergyKwh)
            else stringResource(R.string.remaining_energy_placeholder)

            val rangeText = if (t.rangeFromDash > 0) "${t.rangeFromDash} km"
            else stringResource(R.string.range_placeholder)

            MetricRow(label = stringResource(R.string.soc_dash_title), value = socText, valueColor = colors.accent)
            MetricRow(label = stringResource(R.string.soc_cells_title), value = socRangeText)
            MetricRow(label = stringResource(R.string.soc_delta_title), value = socDeltaText)
            MetricRow(label = stringResource(R.string.cell_voltage_title), value = cellVoltText)
            MetricRow(label = stringResource(R.string.cell_delta_title), value = cellDeltaText, showDivider = false)

            Spacer(modifier = Modifier.height(6.dp))
            HondaActionButton(
                text = stringResource(R.string.btn_show_cells),
                onClick = onShowCellsClick,
                containerColor = colors.highlight,
                contentColor = colors.accent
            )
            Spacer(modifier = Modifier.height(8.dp))

            MetricRow(label = stringResource(R.string.soh), value = sohText)
            MetricRow(label = stringResource(R.string.remaining_energy), value = energyText)
            MetricRow(label = stringResource(R.string.range_dash_title), value = rangeText, showDivider = false)
        }

        // 2. POWER & CHARGING CARD
        SectionCard(title = stringResource(R.string.power_and_charging)) {
            val chargingLabel = if (t.isCharging) {
                when (t.chargingConnection) {
                    ChargingConnection.DC -> stringResource(R.string.charging_dc)
                    else -> stringResource(R.string.charging_ac)
                }
            } else {
                stringResource(R.string.not_charging)
            }
            val chargingColor = if (t.isCharging) colors.charging else colors.textPrimary

            val isReceivingCharge = t.isCharging || (t.powerKw < -0.1 && (t.chargingConnection != ChargingConnection.NC || uiState.isDemoMode))
            val chargePowerText = if (isReceivingCharge) {
                String.format(Locale.ENGLISH, "+%.2f kW", abs(t.powerKw))
            } else stringResource(R.string.kw_placeholder)
            val chargePowerColor = if (isReceivingCharge) colors.charging else colors.textPrimary

            val powerText = if (t.powerKw != 0.0 || t.volt > 0.0) {
                val sign = if (t.powerKw > 0) "+" else ""
                String.format(Locale.ENGLISH, "%s%.2f kW", sign, t.powerKw)
            } else stringResource(R.string.kw_placeholder)
            val powerColor = if (t.powerKw < -0.05 || t.isCharging) colors.regen else colors.textPrimary

            val speed = t.effectiveSpeed
            val (consumptionText, consumptionColor) = when {
                speed > 2.0 -> {
                    val cons = (t.powerKw / speed) * 100.0
                    String.format(Locale.ENGLISH, "%+.1f kWh/100km", cons) to
                        if (cons < -0.1) colors.regen else colors.textPrimary
                }
                isActiveSession -> stringResource(R.string.consumption_stopped) to colors.textPrimary
                else -> stringResource(R.string.consumption_placeholder) to colors.textPrimary
            }

            val ampText = if (t.volt > 0) String.format(Locale.ENGLISH, "%.2f A", t.amp)
            else stringResource(R.string.amp_placeholder)

            val voltText = if (t.volt > 0) {
                String.format(Locale.ENGLISH, stringResource(R.string.battery_voltage_format), t.volt, t.volt / 96.0)
            } else stringResource(R.string.battery_voltage_placeholder)

            MetricRow(label = stringResource(R.string.charging_status_title), value = chargingLabel, valueColor = chargingColor)
            MetricRow(label = stringResource(R.string.charge_power_title), value = chargePowerText, valueColor = chargePowerColor)
            MetricRow(label = stringResource(R.string.power_instant_title), value = powerText, valueColor = powerColor)
            MetricRow(label = stringResource(R.string.consumption_instant_title), value = consumptionText, valueColor = consumptionColor)
            MetricRow(label = stringResource(R.string.battery_current_title), value = ampText)
            MetricRow(label = stringResource(R.string.battery_voltage_title), value = voltText, showDivider = false)
        }

        // 3. TEMPERATURES & DIAGNOSTICS CARD
        SectionCard(title = stringResource(R.string.temperatures_and_diagnostics)) {
            val hasBatTemp = t.batTemp > -50.0 && (t.batTempMin > -50.0 || t.batTempMax > -50.0)
            val batTempText = if (hasBatTemp) String.format(Locale.ENGLISH, "%.1f °C", t.batTemp)
            else stringResource(R.string.temp_placeholder)

            val batTempRangeText = if (hasBatTemp) {
                if (t.batTempMin != t.batTempMax) {
                    String.format(Locale.ENGLISH, "%.1f °C / %.1f °C", t.batTempMin, t.batTempMax)
                } else {
                    String.format(Locale.ENGLISH, "%.1f °C / %.1f °C", t.batTemp, t.batTemp)
                }
            } else stringResource(R.string.temp_range_placeholder)

            val batTempDeltaText = if (hasBatTemp) {
                if (t.batTempMin != t.batTempMax) String.format(Locale.ENGLISH, "%.1f °C", t.batTempSpread)
                else "0.0 °C"
            } else stringResource(R.string.temp_delta_placeholder)

            val ambientTempText = if (t.ambientTemp > -50) String.format(Locale.ENGLISH, "%d.0 °C", t.ambientTemp)
            else stringResource(R.string.ambient_temp_placeholder)

            val (auxText, auxColor) = when {
                t.auxBat >= 13.8 -> String.format(Locale.ENGLISH, stringResource(R.string.aux_bat_charging), t.auxBat) to colors.textPrimary
                t.auxBat >= 12.2 -> String.format(Locale.ENGLISH, stringResource(R.string.aux_bat_ok), t.auxBat) to colors.textPrimary
                t.auxBat > 0.0 -> String.format(Locale.ENGLISH, stringResource(R.string.aux_bat_low), t.auxBat) to colors.warning
                else -> stringResource(R.string.aux_bat_placeholder) to colors.textPrimary
            }

            MetricRow(label = stringResource(R.string.bat_temp_title), value = batTempText)
            MetricRow(label = stringResource(R.string.bat_temp_range_title), value = batTempRangeText)
            MetricRow(label = stringResource(R.string.bat_temp_delta_title), value = batTempDeltaText, showDivider = false)

            Spacer(modifier = Modifier.height(6.dp))
            HondaActionButton(
                text = stringResource(R.string.btn_show_module_temps),
                onClick = onShowModuleTempsClick,
                containerColor = colors.highlight,
                contentColor = colors.accent
            )
            Spacer(modifier = Modifier.height(8.dp))

            MetricRow(label = stringResource(R.string.amb_temp), value = ambientTempText)
            MetricRow(label = stringResource(R.string.aux_bat), value = auxText, valueColor = auxColor, showDivider = false)
        }

        // 4. VEHICLE INFO & DRIVE CARD
        SectionCard(title = stringResource(R.string.vehicle_info)) {
            val odoText = if (t.odo > 0) "${t.odo} km" else stringResource(R.string.odo_placeholder)
            val speedText = if (isActiveSession) {
                val spd = t.effectiveSpeed
                if (spd > 1.0) String.format(Locale.ENGLISH, "%.0f km/h", spd) else "0 km/h"
            } else stringResource(R.string.speed_unit_placeholder)

            val gearText = when (t.computeGear(isActiveSession)) {
                DriveGear.PARK -> stringResource(R.string.gear_park)
                DriveGear.DRIVE -> stringResource(R.string.gear_drive)
                DriveGear.REVERSE -> stringResource(R.string.gear_reverse)
                DriveGear.NEUTRAL -> stringResource(R.string.gear_neutral)
                DriveGear.UNKNOWN -> stringResource(R.string.gear_placeholder)
            }

            val dash = stringResource(R.string.dash_placeholder)
            val coordsText = if (isActiveSession && t.lat.isNotBlank() && t.lon.isNotBlank()) {
                "${t.lat}\n${t.lon}"
            } else {
                "$dash\n$dash"
            }

            val elevationText = if (t.elevation != 0.0) String.format(Locale.ENGLISH, "%.0f m", t.elevation)
            else stringResource(R.string.elevation_placeholder)

            val vinText = if (t.vin.isNotBlank() && t.vin != "HONDA_E") t.vin else "---"

            MetricRow(label = stringResource(R.string.odo), value = odoText)
            MetricRow(label = stringResource(R.string.speed), value = speedText)
            MetricRow(label = stringResource(R.string.gear_direction), value = gearText)
            MetricRow(label = stringResource(R.string.location_phone_title), value = coordsText)
            MetricRow(label = stringResource(R.string.height_phone_title), value = elevationText)
            MetricRow(label = stringResource(R.string.vin), value = vinText, showDivider = false)
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}
