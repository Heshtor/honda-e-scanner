package cz.helicode.hondaescanner;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {
    public static final String PREF_NAME = "app_settings";
    public static final String KEY_THEME = "pref_theme_mode";

    /**
     * Applies the saved theme mode to AppCompatDelegate and to the specific activity if applicable.
     */
    public static void applyTheme(Context context) {
        int savedMode = getSavedThemeMode(context);
        if (AppCompatDelegate.getDefaultNightMode() != savedMode) {
            AppCompatDelegate.setDefaultNightMode(savedMode);
        }
        if (context instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) context;
            if (activity.getDelegate().getLocalNightMode() != savedMode) {
                activity.getDelegate().setLocalNightMode(savedMode);
            }
            activity.getDelegate().applyDayNight();
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
     * Saves the theme mode and applies it globally and locally.
     */
    public static void setSavedThemeMode(Context context, int mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, mode).apply();

        AppCompatDelegate.setDefaultNightMode(mode);

        if (context instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) context;
            activity.getDelegate().setLocalNightMode(mode);
            activity.getDelegate().applyDayNight();
        }
    }
}
