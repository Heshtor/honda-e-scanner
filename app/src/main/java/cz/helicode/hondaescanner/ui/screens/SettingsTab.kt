package cz.helicode.hondaescanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.helicode.hondaescanner.BuildConfig
import cz.helicode.hondaescanner.R
import cz.helicode.hondaescanner.model.AppIconColor
import cz.helicode.hondaescanner.model.AppLanguage
import cz.helicode.hondaescanner.model.AppThemeMode
import cz.helicode.hondaescanner.ui.components.HondaActionButton
import cz.helicode.hondaescanner.ui.components.MetricRow
import cz.helicode.hondaescanner.ui.components.SectionCard
import cz.helicode.hondaescanner.ui.theme.HondaTheme
import cz.helicode.hondaescanner.viewmodel.ScannerUiState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SettingsTab(
    uiState: ScannerUiState,
    themeMode: AppThemeMode,
    selectedIcon: AppIconColor,
    onToggleDemo: () -> Unit,
    onShareLog: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectAppIcon: (AppIconColor) -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    val colors = HondaTheme.colors
    val logInfo = uiState.logInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. DEMO MODE CARD
        SectionCard(
            title = stringResource(R.string.settings_demo_title),
            description = stringResource(R.string.settings_demo_desc)
        ) {
            HondaActionButton(
                text = if (uiState.isDemoMode) stringResource(R.string.demo_button_stop)
                else stringResource(R.string.demo_button),
                onClick = onToggleDemo,
                containerColor = if (uiState.isDemoMode) colors.warning else colors.accent,
                contentColor = if (uiState.isDemoMode) Color.White else colors.onAccent
            )
        }

        // 2. DIAGNOSTIC CAN LOG CARD
        SectionCard(
            title = stringResource(R.string.settings_log_title),
            description = stringResource(R.string.settings_log_desc)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.highlight)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_log_status_title),
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                val (statusText, statusColor) = if (logInfo.exists) {
                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val dateStr = logInfo.startDate?.let { df.format(it) } ?: "---"
                    val sizeStr = logInfo.formattedSize()
                    if (logInfo.isRecording) {
                        stringResource(R.string.settings_log_status_recording, dateStr, sizeStr) to colors.regen
                    } else {
                        stringResource(R.string.settings_log_status_saved, dateStr, sizeStr) to colors.textPrimary
                    }
                } else {
                    stringResource(R.string.settings_log_none) to colors.textSecondary
                }

                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            HondaActionButton(
                text = stringResource(R.string.share_log_button),
                onClick = onShareLog,
                enabled = logInfo.exists
            )
        }

        // 3. LANGUAGE CARD
        SectionCard(
            title = stringResource(R.string.settings_language_title),
            description = stringResource(R.string.settings_language_desc)
        ) {
            RadioOptionRow(
                label = stringResource(R.string.lang_czech),
                selected = uiState.currentLanguage == AppLanguage.CZECH,
                onClick = { onSelectLanguage(AppLanguage.CZECH) }
            )
            RadioOptionRow(
                label = stringResource(R.string.lang_english),
                selected = uiState.currentLanguage == AppLanguage.ENGLISH,
                onClick = { onSelectLanguage(AppLanguage.ENGLISH) }
            )
        }

        // 4. THEME / APPEARANCE CARD
        SectionCard(
            title = stringResource(R.string.settings_theme_title),
            description = stringResource(R.string.settings_theme_desc)
        ) {
            RadioOptionRow(
                label = stringResource(R.string.theme_dark),
                selected = themeMode == AppThemeMode.DARK,
                onClick = { onSelectTheme(AppThemeMode.DARK) }
            )
            RadioOptionRow(
                label = stringResource(R.string.theme_light),
                selected = themeMode == AppThemeMode.LIGHT,
                onClick = { onSelectTheme(AppThemeMode.LIGHT) }
            )
            RadioOptionRow(
                label = stringResource(R.string.theme_system),
                selected = themeMode == AppThemeMode.SYSTEM,
                onClick = { onSelectTheme(AppThemeMode.SYSTEM) }
            )
        }

        // 5. APP ICON (VEHICLE COLOR) CARD
        SectionCard(
            title = stringResource(R.string.settings_icon_title),
            description = stringResource(R.string.settings_icon_desc)
        ) {
            val rows = AppIconColor.entries.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { icon ->
                            val isSelected = icon == selectedIcon
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectAppIcon(icon) },
                                shape = RoundedCornerShape(10.dp),
                                color = colors.highlight,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) colors.accent else colors.navDivider
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Image(
                                        painter = painterResource(id = icon.iconRes),
                                        contentDescription = stringResource(icon.labelRes),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = stringResource(icon.labelRes),
                                        color = if (isSelected) colors.accent else colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. ABOUT APPLICATION CARD
        SectionCard(
            title = stringResource(R.string.settings_about_title),
            description = stringResource(R.string.settings_about_desc)
        ) {
            MetricRow(
                label = stringResource(R.string.about_app_label),
                value = stringResource(R.string.about_app_value_format, BuildConfig.VERSION_NAME)
            )
            MetricRow(
                label = stringResource(R.string.about_author_label),
                value = stringResource(R.string.about_author_value)
            )
            MetricRow(
                label = stringResource(R.string.about_website_label),
                value = stringResource(R.string.about_website_value),
                valueColor = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenWebsite)
            )
            MetricRow(
                label = stringResource(R.string.about_license_label),
                value = stringResource(R.string.about_license_value),
                showDivider = false
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.about_libraries_label),
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_libraries_list),
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            HondaActionButton(
                text = stringResource(R.string.settings_github_button),
                onClick = onOpenGitHub
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun RadioOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = HondaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.accent,
                unselectedColor = colors.textSecondary
            )
        )
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
