package cz.helicode.hondaescanner;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class AppIconHelper {
    private static final String TAG = "AppIconHelper";
    public static final String PREF_NAME = "app_settings";
    public static final String KEY_APP_ICON = "pref_app_icon";

    public static final String ICON_WHITE = "white";
    public static final String ICON_BLACK = "black";
    public static final String ICON_GREY = "grey";
    public static final String ICON_RED = "red";
    public static final String ICON_BLUE = "blue";
    public static final String ICON_YELLOW = "yellow";

    public static final String DEFAULT_ICON = ICON_WHITE;

    public static final String[] ALL_ICONS = new String[]{
            ICON_WHITE, ICON_BLACK, ICON_GREY, ICON_RED, ICON_BLUE, ICON_YELLOW
    };

    /**
     * Gets the currently selected icon key from SharedPreferences (default is "white").
     */
    public static String getSelectedIcon(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_APP_ICON, DEFAULT_ICON);
    }

    /**
     * Returns full class name of the activity-alias for the given icon color.
     */
    public static String getAliasName(String iconKey) {
        if (iconKey == null) iconKey = DEFAULT_ICON;
        switch (iconKey.toLowerCase()) {
            case ICON_BLACK:
                return "cz.helicode.hondaescanner.MainActivityBlack";
            case ICON_GREY:
                return "cz.helicode.hondaescanner.MainActivityGrey";
            case ICON_RED:
                return "cz.helicode.hondaescanner.MainActivityRed";
            case ICON_BLUE:
                return "cz.helicode.hondaescanner.MainActivityBlue";
            case ICON_YELLOW:
                return "cz.helicode.hondaescanner.MainActivityYellow";
            case ICON_WHITE:
            default:
                return "cz.helicode.hondaescanner.MainActivityWhite";
        }
    }

    /**
     * Returns the mipmap resource ID for the given icon color.
     */
    public static int getIconDrawableId(String iconKey) {
        if (iconKey == null) iconKey = DEFAULT_ICON;
        switch (iconKey.toLowerCase()) {
            case ICON_BLACK:
                return R.mipmap.logo_black;
            case ICON_GREY:
                return R.mipmap.logo_grey;
            case ICON_RED:
                return R.mipmap.logo_red;
            case ICON_BLUE:
                return R.mipmap.logo_blue;
            case ICON_YELLOW:
                return R.mipmap.logo_yellow;
            case ICON_WHITE:
            default:
                return R.mipmap.logo_white;
        }
    }

    /**
     * Returns the mipmap resource ID for the currently selected icon.
     */
    public static int getSelectedIconDrawableId(Context context) {
        return getIconDrawableId(getSelectedIcon(context));
    }

    /**
     * Decodes and returns the Bitmap for the given icon color.
     */
    public static Bitmap getIconBitmap(Context context, String iconKey) {
        return BitmapFactory.decodeResource(context.getResources(), getIconDrawableId(iconKey));
    }

    /**
     * Decodes and returns the Bitmap for the currently selected icon.
     */
    public static Bitmap getSelectedIconBitmap(Context context) {
        return getIconBitmap(context, getSelectedIcon(context));
    }

    /**
     * Applies the selected car icon and theme styling to the system task description (Overview / Recent apps screen).
     */
    public static void applyTaskDescription(Activity activity) {
        if (activity == null) return;
        try {
            int iconRes = getSelectedIconDrawableId(activity);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityManager.TaskDescription td = new ActivityManager.TaskDescription.Builder()
                        .setIcon(iconRes)
                        .setLabel(activity.getString(R.string.app_name))
                        .setPrimaryColor(ContextCompat.getColor(activity, R.color.colorPrimary))
                        .build();
                activity.setTaskDescription(td);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Bitmap bitmap = getSelectedIconBitmap(activity);
                ActivityManager.TaskDescription td = new ActivityManager.TaskDescription(
                        activity.getString(R.string.app_name),
                        bitmap,
                        ContextCompat.getColor(activity, R.color.colorPrimary)
                );
                activity.setTaskDescription(td);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply task description", e);
        }
    }

    /**
     * Switches the launcher icon by enabling the chosen activity-alias and disabling the others.
     * Uses PackageManager.DONT_KILL_APP so the running application is not terminated.
     */
    public static void setAppIcon(Context context, String newIconKey) {
        if (newIconKey == null) newIconKey = DEFAULT_ICON;

        // Persist preference
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_APP_ICON, newIconKey).apply();

        PackageManager pm = context.getPackageManager();

        // 1. Enable the target alias first so at least one launcher alias is always active
        String targetAlias = getAliasName(newIconKey);
        ComponentName targetComponent = new ComponentName(context, targetAlias);
        try {
            pm.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
            );
            Log.d(TAG, "Enabled launcher alias: " + targetAlias);
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable alias: " + targetAlias, e);
        }

        // 2. Disable all other aliases
        for (String iconKey : ALL_ICONS) {
            if (!iconKey.equalsIgnoreCase(newIconKey)) {
                String otherAlias = getAliasName(iconKey);
                ComponentName otherComponent = new ComponentName(context, otherAlias);
                try {
                    pm.setComponentEnabledSetting(
                            otherComponent,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                    );
                    Log.d(TAG, "Disabled launcher alias: " + otherAlias);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to disable alias: " + otherAlias, e);
                }
            }
        }
    }
}
