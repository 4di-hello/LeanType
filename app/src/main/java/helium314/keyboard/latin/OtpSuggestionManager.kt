// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.databinding.OtpSuggestionBinding
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey

/**
 * Optional, opt-in helper that surfaces one-time passcodes (OTPs) from incoming SMS as a
 * suggestion-strip chip the user can tap to insert (similar to the clipboard/screenshot
 * suggestions, see [ClipboardHistoryManager.getClipboardSuggestionView]).
 *
 * Privacy: OTPs are extracted strictly from SMS app notifications via [OtpNotificationListenerService]
 * when enabled in Settings. No SMS reading permissions are required.
 */
class OtpSuggestionManager(private val latinIME: LatinIME) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var otpSuggestionView: View? = null

    fun start() {
        activeInstance = this
        if (latestOtp == null && latinIME.mSettings.current.mAutoReadOtp) {
            OtpNotificationListenerService.instance?.checkActiveNotifications()
        }
    }

    fun stop() {
        if (activeInstance === this) {
            activeInstance = null
        }
    }

    /**
     * Build the OTP suggestion chip if a recent code is available, else null.
     * Called from [LatinIME.tryShowOtpSuggestion].
     */
    fun getOtpSuggestionView(parent: ViewGroup?): View? {
        otpSuggestionView = null
        if (parent == null) return null
        if (!latinIME.mSettings.current.mAutoReadOtp) return null
        if (latestOtp == null) {
            OtpNotificationListenerService.instance?.checkActiveNotifications()
        }
        val otp = latestOtp ?: return null
        if (otp == dismissedOtp) return null
        if (System.currentTimeMillis() - latestOtpTimestamp > RECENT_OTP_MILLIS) return null

        val binding = OtpSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        val textView = binding.otpSuggestionText
        latinIME.mSettings.getCustomTypeface()?.let { textView.typeface = it }
        textView.text = otp
        val icon = latinIME.mKeyboardSwitcher.keyboard?.mIconsSet?.getIconDrawable(ToolbarKey.NUMPAD.name.lowercase())
        textView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        textView.setOnClickListener {
            dismissedOtp = otp
            latinIME.onTextInput(otp)
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
        }
        val closeButton = binding.otpSuggestionClose
        closeButton.setImageDrawable(latinIME.mKeyboardSwitcher.keyboard?.mIconsSet?.getIconDrawable(ToolbarKey.CLOSE_HISTORY.name.lowercase()))
        closeButton.setOnClickListener { removeOtpSuggestion() }

        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        icon?.let { colors.setColor(it, ColorType.KEY_ICON) }
        colors.setColor(closeButton, ColorType.REMOVE_SUGGESTION_ICON)
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)

        otpSuggestionView = binding.root
        return otpSuggestionView
    }

    private fun removeOtpSuggestion() {
        dismissedOtp = latestOtp
        val view = otpSuggestionView ?: return
        if (view.parent != null && !view.isGone) {
            latinIME.setNeutralSuggestionStrip()
            latinIME.mHandler.postResumeSuggestions(false)
        }
        view.isGone = true
    }

    companion object {
        private const val TAG = "OtpSuggestionManager"
        private const val RECENT_OTP_MILLIS = 10 * 60 * 1000L // 10 minutes

        private val webOtpRegex = Regex("""(?:^|\s)@[\w.-]+\s+#(\d{4,8})\b""")
        private val keywordPrefixOtpRegex = Regex(
            """(?i)\b(?:is|otp|code|passcode|password|pin|verification|verify|2fa|auth)\b[\s:=-]+(\d{4,8})\b"""
        )
        private val reverseOtpRegex = Regex(
            """(?i)\b(\d{4,8})\b[\s:=-]+(?:is\s+)?(?:[a-z0-9_-]{1,15}\s+){0,5}(?:otp|code|passcode|password|pin|verification|verify|2fa|auth)\b"""
        )
        private val hashtagOtpRegex = Regex("""(?i)#(\d{4,8})\b""")
        private val codeRegex = Regex("""(?<![\.\d])\b\d{4,8}\b(?!\.\d)""")
        private val otpKeywordRegex = Regex(
            """otp|code|passcode|password|pin|verification|verify|one[- ]?time|2fa|auth""",
            RegexOption.IGNORE_CASE
        )

        @Volatile private var latestOtp: String? = null
        @Volatile private var latestOtpTimestamp: Long = 0L
        @Volatile private var dismissedOtp: String? = null
        @Volatile private var activeInstance: OtpSuggestionManager? = null

        /**
         * Called by [OtpNotificationListenerService] when an OTP is detected from an SMS notification.
         */
        fun onOtpReceived(otp: String) {
            Log.i(TAG, "onOtpReceived: otp=$otp")
            latestOtp = otp
            latestOtpTimestamp = System.currentTimeMillis()
            dismissedOtp = null
            val instance = activeInstance ?: return
            instance.mainHandler.post {
                if (instance.latinIME.isInputViewShown) {
                    instance.latinIME.setNeutralSuggestionStrip()
                }
            }
        }

        /**
         * Extract an OTP from notification or SMS body text.
         */
        fun extractOtp(body: String): String? {
            if (body.isBlank()) return null

            // 1. WebOTP standard format: "@domain.com #123456"
            webOtpRegex.find(body)?.let { match ->
                return match.groupValues[1]
            }

            // 2. Keyword prefix: "OTP: 123456", "code is 482910", "is 789012"
            keywordPrefixOtpRegex.find(body)?.let { match ->
                return match.groupValues[1]
            }

            // 3. Reverse pattern: "123456 is your code", "789101 is your OTP"
            reverseOtpRegex.find(body)?.let { match ->
                return match.groupValues[1]
            }

            // 4. Hashtag format with OTP keyword: "#123456"
            if (otpKeywordRegex.containsMatchIn(body)) {
                hashtagOtpRegex.find(body)?.let { match ->
                    return match.groupValues[1]
                }
            }

            // 5. Fallback: if message mentions an OTP keyword, find candidate numbers (excluding decimal amounts)
            if (otpKeywordRegex.containsMatchIn(body)) {
                val matches = codeRegex.findAll(body).map { it.value }.toList()
                if (matches.isNotEmpty()) {
                    // Filter out likely 4-digit years (2020..2035) if multiple candidates exist
                    val filtered = if (matches.size > 1) {
                        matches.filterNot { it.length == 4 && it.toIntOrNull() in 2020..2035 }
                    } else matches
                    if (filtered.isNotEmpty()) {
                        return filtered.last() // The OTP is almost always at the end after the context/metadata
                    }
                }
            }

            // 6. Single candidate number in the entire message
            val allGroups = codeRegex.findAll(body).map { it.value }.toList()
            if (allGroups.size == 1) {
                return allGroups.first()
            }

            return null
        }
    }
}
