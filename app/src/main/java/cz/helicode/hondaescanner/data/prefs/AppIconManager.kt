package cz.helicode.hondaescanner.data.prefs

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import cz.helicode.hondaescanner.R
import cz.helicode.hondaescanner.model.AppIconColor

object AppIconManager {

    private const val TAG = "AppIconManager"

    fun applyTaskDescription(activity: Activity, icon: AppIconColor) {
        try {
            val primaryColor = ContextCompat.getColor(activity, R.color.colorPrimary)
            val label = activity.getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val td = ActivityManager.TaskDescription.Builder()
                    .setIcon(icon.iconRes)
                    .setLabel(label)
                    .setPrimaryColor(primaryColor)
                    .build()
                activity.setTaskDescription(td)
            } else {
                @Suppress("DEPRECATION")
                val bitmap = BitmapFactory.decodeResource(activity.resources, icon.iconRes)
                @Suppress("DEPRECATION")
                val td = ActivityManager.TaskDescription(label, bitmap, primaryColor)
                activity.setTaskDescription(td)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply task description", e)
        }
    }

    fun switchLauncherIcon(context: Context, newIcon: AppIconColor) {
        val pm = context.packageManager

        // 1. Enable target alias first so at least one launcher entry is always enabled
        try {
            pm.setComponentEnabledSetting(
                ComponentName(context, newIcon.aliasClassName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable alias: ${newIcon.aliasClassName}", e)
        }

        // 2. Disable all other aliases
        AppIconColor.entries.forEach { candidate ->
            if (candidate != newIcon) {
                try {
                    pm.setComponentEnabledSetting(
                        ComponentName(context, candidate.aliasClassName),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to disable alias: ${candidate.aliasClassName}", e)
                }
            }
        }
    }
}
