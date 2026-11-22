// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

/**
 * Whether an AI result can still be inserted into the field it was asked for, after the keyboard was
 * hidden or another field was opened meanwhile. Many apps use the same field id for all their text
 * fields (e.g. every chat), so the field is identified by the app, the input type and its text as it
 * was read for the request. Only a hash and the length of the text are kept.
 */
internal object AiFieldCheck {
    class Fingerprint internal constructor(
        private val packageName: String?,
        private val inputType: Int,
        private val textHash: Int,
        private val textLength: Int,
    ) {
        /**
         * [aroundCursor]: text before the cursor + selected text + text after the cursor. [extracted]
         * gives the whole text as the app gives it for extraction; it is only asked for if the text
         * around the cursor differs, as some apps report it unreliably.
         */
        fun matches(packageName: String?, inputType: Int, isPassword: Boolean, aroundCursor: String, extracted: () -> String?): Boolean {
            if (isPassword || packageName != this.packageName || inputType != this.inputType) return false
            if (isSameText(aroundCursor)) return true
            return extracted()?.let { isSameText(it) } == true
        }

        private fun isSameText(text: String) = text.length == textLength && text.hashCode() == textHash
    }

    /** [text]: the whole text of the field as read for the request. Null for password fields: nothing is inserted there later. */
    fun fingerprint(packageName: String?, inputType: Int, isPassword: Boolean, text: String): Fingerprint? =
        if (isPassword) null else Fingerprint(packageName, inputType, text.hashCode(), text.length)
}
