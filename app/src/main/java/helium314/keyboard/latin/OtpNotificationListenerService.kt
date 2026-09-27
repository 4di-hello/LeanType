// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SmsPackageProvider
import helium314.keyboard.latin.utils.prefs

/**
 * Service that reads incoming OTPs strictly from SMS notifications of allowed SMS messaging apps.
 * Requires zero SMS reading permissions in AndroidManifest.xml.
 */
class OtpNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        processNotification(sbn)
    }

    fun checkActiveNotifications() {
        try {
            val notifications = activeNotifications ?: return
            Log.d(TAG, "checkActiveNotifications: scanning ${notifications.size} active notifications")
            for (sbn in notifications) {
                processNotification(sbn)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect active notifications", e)
        }
    }

    private fun processNotification(sbn: StatusBarNotification?) {
        val sbnNonNull = sbn ?: return
        val pkg = sbnNonNull.packageName ?: return

        // 1. Check if feature is enabled
        val autoReadEnabled = try {
            prefs().getBoolean(Settings.PREF_AUTO_READ_OTP, false)
        } catch (e: Exception) {
            false
        }
        if (!autoReadEnabled) return

        // 2. Package filtering: check specific allowed package or fallback allowlist / default SMS app
        val allowedPackage = try {
            prefs().getString(Settings.PREF_OTP_ALLOWED_SMS_PACKAGE, null)
        } catch (e: Exception) {
            null
        }

        if (!allowedPackage.isNullOrBlank()) {
            if (pkg != allowedPackage) return
        } else {
            val defaultSms = SmsPackageProvider.getDefaultSmsPackage(this)
            val isKnown = pkg in SmsPackageProvider.KNOWN_SMS_PACKAGES
            val isDefault = !defaultSms.isNullOrBlank() && pkg == defaultSms
            if (!isKnown && !isDefault) return
        }

        Log.d(TAG, "Processing notification from package: $pkg")

        // 3. Extract notification text safely from all possible notification components
        val textPieces = mutableListOf<String>()

        sbnNonNull.notification?.tickerText?.let { textPieces.add(it.toString()) }

        try {
            val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbnNonNull.notification)
            messagingStyle?.messages?.forEach { message ->
                message.text?.let { textPieces.add(it.toString()) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse MessagingStyle", e)
        }

        val extras = sbnNonNull.notification?.extras
        if (extras != null) {
            extras.getCharSequence(Notification.EXTRA_TITLE)?.let { textPieces.add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { textPieces.add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { textPieces.add(it.toString()) }
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let { textPieces.add(it.toString()) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { textPieces.add(it.toString()) }

            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?: (extras.get(Notification.EXTRA_MESSAGES) as? Array<*>)
            if (messages != null) {
                for (msg in messages) {
                    if (msg is Bundle) {
                        msg.getCharSequence("text")?.let { textPieces.add(it.toString()) }
                    }
                }
            }
        }

        if (textPieces.isEmpty()) return
        val fullText = textPieces.joinToString("\n")

        val otp = OtpSuggestionManager.extractOtp(fullText) ?: return
        Log.i(TAG, "OTP detected from notification (pkg=$pkg): $otp")
        OtpSuggestionManager.onOtpReceived(otp)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification listener connected")
        try {
            prefs().edit().putBoolean(PREF_NLS_DISCONNECTED, false).apply()
            checkActiveNotifications()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update NLS connected state", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) {
            instance = null
        }
        Log.i(TAG, "Notification listener disconnected")
        try {
            prefs().edit().putBoolean(PREF_NLS_DISCONNECTED, true).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update NLS disconnected state", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    companion object {
        private const val TAG = "OtpNotificationListener"
        const val PREF_NLS_DISCONNECTED = "nls_disconnected"
        @Volatile var instance: OtpNotificationListenerService? = null
    }
}
