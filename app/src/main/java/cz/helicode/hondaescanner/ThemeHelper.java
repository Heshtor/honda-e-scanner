package cz.helicode.hondaescanner;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {
    public static final String PREF_NAME = "app_settings";
    public static final String KEY_THEME = "pref_theme_mode";

    /**
     * Applies the saved theme mode to AppCompatDelegate if it differs from current mode.
     */
    public static void applyTheme(Context context) {
        int savedMode = getSavedThemeMode(context);
        if (AppCompatDelegate.getDefaultNightMode() != savedMode) {
            AppCompatDelegate.setDefaultNightMode(savedMode);
        }
    }

    /**
     * Returns the saved theme mode from SharedPreferences. Default is Dark Mode (MODE_NIGHT_YES).
     */
    public static int getSavedThemeMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_YES);
    }

    /**
     * Saves the theme mode and applies it to AppCompatDelegate only if it actually changed.
     */
    public static void setSavedThemeMode(Context context, int mode) {
        int currentSaved = getSavedThemeMode(context);
        int currentNightMode = AppCompatDelegate.getDefaultNightMode();

        if (currentSaved == mode && currentNightMode == mode) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, mode).apply();

        if (currentNightMode != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }
}
