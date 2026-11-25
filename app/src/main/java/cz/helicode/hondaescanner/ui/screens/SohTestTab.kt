package cz.helicode.hondaescanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.helicode.hondaescanner.R
import cz.helicode.hondaescanner.ui.components.HondaActionButton
import cz.helicode.hondaescanner.ui.components.MetricRow
import cz.helicode.hondaescanner.ui.components.SectionCard
import cz.helicode.hondaescanner.ui.theme.HondaTheme
import cz.helicode.hondaescanner.viewmodel.ScannerUiState
import java.util.Locale

@Composable
fun SohTestTab(
    uiState: ScannerUiState,
    onToggleSohTest: () -> Unit
) {
    val colors = HondaTheme.colors
    val s = uiState.sohTest
    val t = uiState.telemetry

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. SOH TEST CONTROL CARD
        SectionCard(title = stringResource(R.string.soh_test_control_title)) {
            val statusText = when {
                s.isStopping -> stringResource(R.string.soh_test_status_finalizing)
                s.isRunning -> stringResource(R.string.soh_test_status_running)
                s.result != null -> stringResource(R.string.soh_test_status_finished)
                else -> stringResource(R.string.soh_test_status_idle)
            }

            Text(
                text = statusText,
                color = if (s.isRunning) colors.accent else colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            HondaActionButton(
                text = if (s.isRunning) stringResource(R.string.soh_test_btn_stop)
                else stringResource(R.string.soh_test_btn_start),
                onClick = onToggleSohTest,
                enabled = !s.isStopping,
                containerColor = if (s.isRunning) colors.warning else colors.accent,
                contentColor = if (s.isRunning) Color.White else colors.onAccent
            )
        }

        // 2. LIVE TEST PROGRESS CARD
        if (s.showLiveCard) {
            SectionCard(title = stringResource(R.string.soh_test_live_title)) {
                val hours = s.elapsedMs / (1000L * 60 * 60)
                val minutes = (s.elapsedMs / (1000L * 60)) % 60
                val seconds = (s.elapsedMs / 1000L) % 60
                val durationStr = String.format(
                    Locale.ENGLISH,
                    stringResource(R.string.soh_test_samples_format),
                    hours, minutes, seconds, s.sampleCount
                )

                val energyStr = String.format(
                    Locale.ENGLISH,
                    "%.2f kWh (+%.2f / -%.2f)",
                    s.totalNetEnergyKwh, s.totalDischargeKwh, s.totalRegenKwh
                )

                val effectiveSoc = if (s.isRunning) t.soc else (s.stopSoc ?: t.soc)
                val liveDeltaSoc = if (s.startSoc > 0 && effectiveSoc > 0) s.startSoc - effectiveSoc else 0.0
                val deltaSocStr = String.format(Locale.ENGLISH, "%.2f %%", liveDeltaSoc)

                val effectiveOdo = if (s.isRunning) t.odo else (s.stopOdo ?: t.odo)
                val canDeltaKm = if (effectiveOdo > 0 && s.startOdo > 0 && effectiveOdo >= s.startOdo) {
                    effectiveOdo - s.startOdo
                } else 0
                val distanceStr = String.format(Locale.ENGLISH, "%.1f km / %d km", s.gpsDistanceKm, canDeltaKm)

                val powerStr = if (t.volt > 0) {
                    String.format(Locale.ENGLISH, "%+.2f kW (%.1f A, %.1f V)", t.powerKw, t.amp, t.volt)
                } else stringResource(R.string.live_power_placeholder)
                val powerColor = if (t.volt > 0 && (t.powerKw < -0.05 || t.isCharging)) colors.regen else colors.textPrimary

                MetricRow(label = stringResource(R.string.soh_test_result_duration_title), value = durationStr)
                MetricRow(label = stringResource(R.string.soh_test_live_energy_title), value = energyStr)
                MetricRow(label = stringResource(R.string.soh_test_live_deltasoc_title), value = deltaSocStr)
                MetricRow(label = stringResource(R.string.soh_test_live_distance_title), value = distanceStr)
                MetricRow(
                    label = stringResource(R.string.soh_test_power_title),
                    value = powerStr,
                    valueColor = powerColor,
                    showDivider = false
                )
            }
        }

        // 3. RESULTS CARD
        val result = s.result
        if (result != null) {
            SectionCard(title = stringResource(R.string.soh_test_result_title)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.highlight)
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.soh_test_result_soh_label),
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.ENGLISH, "%.2f %%", result.calculatedSohPct),
                        color = colors.accent,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val cNettoStr = String.format(
                    Locale.ENGLISH,
                    stringResource(R.string.soh_test_cnetto_format),
                    result.cNettoKwh
                )
                val deltaSocStr = String.format(
                    Locale.ENGLISH,
                    "%.2f %%\n(%.2f %% → %.2f %%)",
                    result.deltaSocPct, result.startSoc, result.stopSoc
                )
                val energyStr = String.format(Locale.ENGLISH, "%.2f kWh", result.netEnergyKwh)
                val dischargeRegenStr = String.format(
                    Locale.ENGLISH,
                    "+%.2f kWh / -%.2f kWh",
                    result.dischargeKwh, result.regenKwh
                )
                val distanceStr = when {
                    result.startOdo > 0 && result.stopOdo >= result.startOdo ->
                        String.format(
                            Locale.ENGLISH,
                            "%.1f km / %d km\n(%d km → %d km)",
                            result.gpsDistanceKm, result.canDeltaKm, result.startOdo, result.stopOdo
                        )
                    result.startOdo > 0 ->
                        String.format(
                            Locale.ENGLISH,
                            "%.1f km / %d km\n(%d km)",
                            result.gpsDistanceKm, result.canDeltaKm, result.startOdo
                        )
                    else ->
                        String.format(Locale.ENGLISH, "%.1f km / %d km", result.gpsDistanceKm, result.canDeltaKm)
                }
                val consumptionStr = result.avgConsumptionKwh100km?.let {
                    String.format(Locale.ENGLISH, "%.2f kWh/100 km", it)
                } ?: stringResource(R.string.soh_test_result_consumption_placeholder)

                val hours = result.durationMs / (1000L * 60 * 60)
                val minutes = (result.durationMs / (1000L * 60)) % 60
                val seconds = (result.durationMs / 1000L) % 60
                val durationStr = String.format(
                    Locale.ENGLISH,
                    stringResource(R.string.soh_test_samples_format),
                    hours, minutes, seconds, result.sampleCount
                )

                MetricRow(label = stringResource(R.string.soh_test_result_cnetto_title), value = cNettoStr)
                MetricRow(label = stringResource(R.string.soh_test_result_energy_title), value = energyStr)
                MetricRow(label = stringResource(R.string.soh_test_discharge_regen_title), value = dischargeRegenStr)
                MetricRow(label = stringResource(R.string.soh_test_result_consumption_title), value = consumptionStr)
                MetricRow(label = stringResource(R.string.soh_test_result_deltasoc_title), value = deltaSocStr)
                MetricRow(label = stringResource(R.string.soh_test_result_distance_title), value = distanceStr)
                MetricRow(label = stringResource(R.string.soh_test_result_duration_title), value = durationStr, showDivider = false)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}
