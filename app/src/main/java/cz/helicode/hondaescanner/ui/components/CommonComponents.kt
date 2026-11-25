package cz.helicode.hondaescanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.helicode.hondaescanner.R
import cz.helicode.hondaescanner.model.AppIconColor
import cz.helicode.hondaescanner.model.ToastEvent
import cz.helicode.hondaescanner.model.VehicleTelemetry
import cz.helicode.hondaescanner.ui.theme.HondaTheme
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = HondaTheme.colors
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = colors.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = HondaTheme.colors.textPrimary,
    showDivider: Boolean = true
) {
    val colors = HondaTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = colors.navDivider
            )
        }
    }
}

@Composable
fun HondaActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = HondaTheme.colors.accent,
    contentColor: Color = HondaTheme.colors.onAccent
) {
    val colors = HondaTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = colors.highlight,
            disabledContentColor = colors.textSecondary
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BrandedToastOverlay(
    toastEvent: ToastEvent?,
    selectedIcon: AppIconColor,
    modifier: Modifier = Modifier
) {
    val colors = HondaTheme.colors
    var lastEvent by remember { mutableStateOf<ToastEvent?>(null) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(toastEvent?.id) {
        if (toastEvent != null) {
            lastEvent = toastEvent
            isVisible = true
            delay(if (toastEvent.isLong) 3500L else 2200L)
            if (lastEvent?.id == toastEvent.id) {
                isVisible = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 84.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isVisible && lastEvent != null,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200))
        ) {
            val event = lastEvent ?: return@AnimatedVisibility
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.card,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = selectedIcon.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = event.message,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CellVoltagesDialog(
    telemetry: VehicleTelemetry,
    onDismiss: () -> Unit
) {
    val colors = HondaTheme.colors
    val minLabel = stringResource(R.string.dialog_label_min)
    val avgLabel = stringResource(R.string.dialog_label_avg)
    val maxLabel = stringResource(R.string.dialog_label_max)
    val deltaLabel = stringResource(R.string.dialog_label_delta)

    val formattedContent = remember(telemetry.cellVoltages, telemetry.cellMinV, telemetry.cellMaxV, telemetry.cellAvgV, telemetry.cellDeltaMv) {
        buildString {
            append(String.format(Locale.ENGLISH, "%-14s %.3f V\n", minLabel, telemetry.cellMinV))
            append(String.format(Locale.ENGLISH, "%-14s %.3f V\n", avgLabel, telemetry.cellAvgV))
            append(String.format(Locale.ENGLISH, "%-14s %.3f V\n", maxLabel, telemetry.cellMaxV))
            append(String.format(Locale.ENGLISH, "%-14s %d mV\n", deltaLabel, telemetry.cellDeltaMv))
            append("────────────────────────────\n\n")
            for (i in 0 until 96 step 2) {
                val c1 = String.format(Locale.ENGLISH, "#%02d: %.3f V", i + 1, telemetry.cellVoltages.getOrElse(i) { 0.0 })
                val c2 = if (i + 1 < 96) {
                    String.format(Locale.ENGLISH, "#%02d: %.3f V", i + 2, telemetry.cellVoltages.getOrElse(i + 1) { 0.0 })
                } else ""
                append(String.format(Locale.ENGLISH, "%-15s   %-15s\n", c1, c2))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.accent,
        textContentColor = colors.textPrimary,
        title = {
            Text(
                text = stringResource(R.string.cell_voltages_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = formattedContent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(android.R.string.ok),
                    color = colors.accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun ModuleTempsDialog(
    telemetry: VehicleTelemetry,
    onDismiss: () -> Unit
) {
    val colors = HondaTheme.colors
    val minLabel = stringResource(R.string.dialog_label_min)
    val avgLabel = stringResource(R.string.dialog_label_avg)
    val maxLabel = stringResource(R.string.dialog_label_max)
    val deltaLabel = stringResource(R.string.dialog_label_delta)
    val moduleFmt = stringResource(R.string.dialog_module_format)

    val formattedContent = remember(telemetry.moduleTemps, telemetry.batTempMin, telemetry.batTemp, telemetry.batTempMax, telemetry.batTempSpread) {
        buildString {
            append(String.format(Locale.ENGLISH, "%-14s %.1f °C\n", minLabel, telemetry.batTempMin))
            append(String.format(Locale.ENGLISH, "%-14s %.1f °C\n", avgLabel, telemetry.batTemp))
            append(String.format(Locale.ENGLISH, "%-14s %.1f °C\n", maxLabel, telemetry.batTempMax))
            append(String.format(Locale.ENGLISH, "%-14s %.1f °C\n", deltaLabel, telemetry.batTempSpread))
            append("──────────────────────────\n\n")
            for (i in 0 until 6) {
                val temp = telemetry.moduleTemps.getOrElse(i) { 0.0 }
                val marker = when {
                    abs(temp - telemetry.batTempMin) < 0.05 && telemetry.batTempSpread > 0.1 -> "  (Min)"
                    abs(temp - telemetry.batTempMax) < 0.05 && telemetry.batTempSpread > 0.1 -> "  (Max)"
                    else -> ""
                }
                append(String.format(Locale.ENGLISH, moduleFmt, i + 1, temp, marker))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        titleContentColor = colors.accent,
        textContentColor = colors.textPrimary,
        title = {
            Text(
                text = stringResource(R.string.module_temps_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = formattedContent,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(android.R.string.ok),
                    color = colors.accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
