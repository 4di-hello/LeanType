/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.OtpNotificationListenerService
import helium314.keyboard.latin.utils.Log

/**
 * Utility class for permissions.
 */
object PermissionsUtil {
    private const val TAG = "PermissionsUtil"

    /**
     * Queries if al the permissions are granted for the given permission strings.
     */
    fun checkAllPermissionsGranted(context: Context?, vararg permissions: String): Boolean {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            // For all pre-M devices, we should have all the permissions granted on install.
            return true
        }
        if (context == null) return false

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun isNotificationListenerEnabled(context: Context?): Boolean {
        if (context == null) return false
        val pkg = context.packageName
        if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(pkg)) {
            return true
        }
        val component = ComponentName(context, OtpNotificationListenerService::class.java)
        val requiredComponent = component.flattenToString()
        val shortComponent = component.flattenToShortString()

        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false

        for (listener in enabledListeners.split(":")) {
            val trimmed = listener.trim()
            if (trimmed == requiredComponent || trimmed == shortComponent) {
                return true
            }
        }
        return false
    }

    fun openNotificationListenerSettings(context: Context) {
        val component = ComponentName(context, OtpNotificationListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val detailIntent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(detailIntent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Could not open detail settings, falling back to general listener settings", e)
            }
        }
        try {
            val generalIntent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(generalIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open notification listener settings", e)
        }
    }
}
