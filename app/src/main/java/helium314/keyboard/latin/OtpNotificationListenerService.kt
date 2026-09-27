// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.app.Notification
import android.content.SharedPreferences
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

    @Volatile private var isAutoReadEnabled = false
    @Volatile private var cachedAllowedPackage: String? = null
    @Volatile private var cachedDefaultSmsPackage: String? = null

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Settings.PREF_AUTO_READ_OTP || key == Settings.PREF_OTP_ALLOWED_SMS_PACKAGE) {
            refreshCachedPreferences()
        }
    }

    private fun refreshCachedPreferences() {
        try {
            val p = prefs()
            isAutoReadEnabled = p.getBoolean(Settings.PREF_AUTO_READ_OTP, false)
            cachedAllowedPackage = p.getString(Settings.PREF_OTP_ALLOWED_SMS_PACKAGE, null)
            cachedDefaultSmsPackage = SmsPackageProvider.getDefaultSmsPackage(this)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh cached preferences", e)
        }
    }

    private fun isAllowedPackage(pkg: String): Boolean {
        val allowed = cachedAllowedPackage
        if (!allowed.isNullOrBlank()) {
            return pkg == allowed
        }
        val defaultSms = cachedDefaultSmsPackage ?: SmsPackageProvider.getDefaultSmsPackage(this)
        return (!defaultSms.isNullOrBlank() && pkg == defaultSms) || pkg in SmsPackageProvider.KNOWN_SMS_PACKAGES
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        processNotification(sbn)
    }

    fun checkActiveNotifications() {
        refreshCachedPreferences()
        if (!isAutoReadEnabled) return
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

        // 1. Strict package filter: if user selected an SMS app, ONLY accept that one; otherwise check default/allowlist
        if (!isAllowedPackage(pkg)) return

        // 2. In-memory check if feature is enabled
        if (!isAutoReadEnabled) return

        Log.d(TAG, "Processing notification from package: $pkg")

        // 4. Extract notification text safely from all possible notification components
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
        refreshCachedPreferences()
        try {
            prefs().registerOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register prefChangeListener", e)
        }
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
        try {
            prefs().unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister prefChangeListener", e)
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
        try {
            prefs().unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (e: Exception) {
            // ignore
        }
    }

    companion object {
        private const val TAG = "OtpNotificationListener"
        const val PREF_NLS_DISCONNECTED = "nls_disconnected"
        @Volatile var instance: OtpNotificationListenerService? = null
    }
}
