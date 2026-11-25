package cz.helicode.hondaescanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import cz.helicode.hondaescanner.model.AppThemeMode

@Immutable
data class HondaExtendedColors(
    val background: Color,
    val card: Color,
    val navBg: Color,
    val navDivider: Color,
    val highlight: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val regen: Color,
    val charging: Color,
    val discharge: Color,
    val warning: Color
)

private val DarkHondaColors = HondaExtendedColors(
    background = DarkBg,
    card = DarkCard,
    navBg = DarkNavBg,
    navDivider = DarkNavDivider,
    highlight = DarkHighlight,
    accent = DarkAccent,
    onAccent = DarkOnAccent,
    textPrimary = DarkText,
    textSecondary = DarkTextSecondary,
    regen = DarkRegen,
    charging = DarkCharging,
    discharge = DarkDischarge,
    warning = DarkWarning
)

private val LightHondaColors = HondaExtendedColors(
    background = LightBg,
    card = LightCard,
    navBg = LightNavBg,
    navDivider = LightNavDivider,
    highlight = LightHighlight,
    accent = LightAccent,
    onAccent = LightOnAccent,
    textPrimary = LightText,
    textSecondary = LightTextSecondary,
    regen = LightRegen,
    charging = LightCharging,
    discharge = LightDischarge,
    warning = LightWarning
)

private val DarkMaterialScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    secondary = DarkRegen,
    onSecondary = DarkOnAccent,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkCard,
    onSurface = DarkText,
    surfaceVariant = DarkHighlight,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkNavDivider,
    error = DarkWarning,
    onError = Color.White
)

private val LightMaterialScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    secondary = LightRegen,
    onSecondary = Color.White,
    background = LightBg,
    onBackground = LightText,
    surface = LightCard,
    onSurface = LightText,
    surfaceVariant = LightHighlight,
    onSurfaceVariant = LightTextSecondary,
    outline = LightNavDivider,
    error = LightWarning,
    onError = Color.White
)

val LocalHondaColors = staticCompositionLocalOf { DarkHondaColors }

object HondaTheme {
    val colors: HondaExtendedColors
        @Composable
        get() = LocalHondaColors.current
}

@Composable
fun HondaEScannerTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val materialScheme = if (isDark) DarkMaterialScheme else LightMaterialScheme
    val extendedColors = if (isDark) DarkHondaColors else LightHondaColors

    CompositionLocalProvider(LocalHondaColors provides extendedColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography,
            content = content
        )
    }
}
