package cz.helicode.hondaescanner.data.prefs

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import cz.helicode.hondaescanner.model.AppIconColor
import cz.helicode.hondaescanner.model.AppLanguage
import cz.helicode.hondaescanner.model.AppThemeMode
import cz.helicode.hondaescanner.model.BluetoothDeviceItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SettingsRepository(private val context: Context) {

    companion object {
        const val PREF_NAME = "app_settings"
        const val KEY_THEME = "pref_theme_mode"
        const val KEY_APP_ICON = "pref_app_icon"
        const val KEY_APP_LANGUAGE = "pref_app_language"
        const val KEY_VIN = "vehicle_vin"
        private const val LAST_DEVICE_PREFS = "MainActivity"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_DEVICE_MAC = "device_mac"

        /**
         * Wraps an Activity base context with the saved language on Android 8-12 (API 26-32).
         * On Android 13+ (API 33+), system [LocaleManager] manages per-app locales automatically.
         */
        fun wrapContextWithLocale(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return base
            }
            val savedTag = base.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_APP_LANGUAGE, null)
                ?: return base

            val locale = Locale.forLanguageTag(savedTag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration).apply {
                setLocale(locale)
                setLocales(LocaleList(locale))
            }
            return base.createConfigurationContext(config)
        }
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val lastDevicePrefs = context.getSharedPreferences(LAST_DEVICE_PREFS, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _selectedIcon = MutableStateFlow(loadSelectedIcon())
    val selectedIcon: StateFlow<AppIconColor> = _selectedIcon.asStateFlow()

    fun loadThemeMode(): AppThemeMode {
        val saved = prefs.getInt(KEY_THEME, AppThemeMode.DARK.prefValue)
        return AppThemeMode.fromPrefValue(saved)
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit { putInt(KEY_THEME, mode.prefValue) }
        _themeMode.value = mode
    }

    fun loadSelectedIcon(): AppIconColor {
        return AppIconColor.fromKey(prefs.getString(KEY_APP_ICON, AppIconColor.WHITE.key))
    }

    fun saveSelectedIcon(icon: AppIconColor) {
        prefs.edit { putString(KEY_APP_ICON, icon.key) }
        _selectedIcon.value = icon
    }

    fun getCurrentLanguage(): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                return if (locales.toLanguageTags().startsWith("en", ignoreCase = true)) {
                    AppLanguage.ENGLISH
                } else {
                    AppLanguage.CZECH
                }
            }
        } else {
            val savedTag = prefs.getString(KEY_APP_LANGUAGE, null)
            if (!savedTag.isNullOrBlank()) {
                return if (savedTag.startsWith("en", ignoreCase = true)) {
                    AppLanguage.ENGLISH
                } else {
                    AppLanguage.CZECH
                }
            }
        }
        return if (Locale.getDefault().language.equals("en", ignoreCase = true)) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.CZECH
        }
    }

    /**
     * Applies the chosen language. Returns `true` if the host Activity should recreate()
     * (only needed on Android 8-12; Android 13+ LocaleManager recreates automatically).
     */
    fun setLanguage(language: AppLanguage, activity: Activity? = null): Boolean {
        if (getCurrentLanguage() == language) return false
        prefs.edit { putString(KEY_APP_LANGUAGE, language.tag) }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales = LocaleList.forLanguageTags(language.tag)
            false
        } else {
            activity?.recreate()
            true
        }
    }

    fun getSavedVin(): String? = prefs.getString(KEY_VIN, null)?.takeIf { it.isNotBlank() }

    fun saveVin(vin: String) {
        if (vin.isNotBlank() && vin != "HONDA_E") {
            prefs.edit { putString(KEY_VIN, vin) }
        }
    }

    fun getLastConnectedDevice(): BluetoothDeviceItem? {
        val name = lastDevicePrefs.getString(KEY_DEVICE_NAME, null)
        val mac = lastDevicePrefs.getString(KEY_DEVICE_MAC, null)
        return if (!name.isNullOrBlank() && !mac.isNullOrBlank()) {
            BluetoothDeviceItem(name = name, macAddress = mac)
        } else null
    }

    fun saveLastConnectedDevice(device: BluetoothDeviceItem) {
        lastDevicePrefs.edit {
            putString(KEY_DEVICE_NAME, device.name)
            putString(KEY_DEVICE_MAC, device.macAddress)
        }
    }
}
