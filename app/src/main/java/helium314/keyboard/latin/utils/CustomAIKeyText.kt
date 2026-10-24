// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.ClipboardManager
import android.content.Context

/** Text handling of the custom AI keys that does not need an input connection */
object CustomAIKeyText {
    const val CLIPBOARD_PLACEHOLDER = "{clipboard}"

    /**
     * Instructions of the selected keywords go before the user's prompt, so a prompt that ends
     * with an answer start (e.g. "Answer:") or embeds the text with `{text}` keeps its ending.
     */
    fun withInstructions(prompt: String, instructions: String): String {
        val trimmed = instructions.trim()
        return if (trimmed.isEmpty()) prompt else "$trimmed\n\n$prompt"
    }

    fun usesClipboard(prompt: String) = prompt.contains(CLIPBOARD_PLACEHOLDER)

    /** Text in the clipboard, or null if it is empty or can't be read */
    fun readClipboard(context: Context): String? = runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()

    fun insertClipboard(prompt: String, clipboard: String): String =
        prompt.replace(CLIPBOARD_PLACEHOLDER, clipboard.trim())

    /** Line breaks to put before a result appended after [textBefore], so it starts in a new paragraph */
    fun separatorBefore(textBefore: CharSequence): String = when {
        textBefore.isBlank() -> ""
        textBefore.endsWith("\n\n") -> ""
        textBefore.endsWith("\n") -> "\n"
        else -> "\n\n"
    }

    /** Line breaks to put after a result inserted before [textAfter], so the original starts in a new paragraph */
    fun separatorAfter(textAfter: CharSequence): String = when {
        textAfter.isBlank() -> ""
        textAfter.startsWith("\n\n") -> ""
        textAfter.startsWith("\n") -> "\n"
        else -> "\n\n"
    }

    /** The result as it is inserted, with paragraph breaks to the text around it */
    fun resultToInsert(result: String, textBefore: CharSequence, textAfter: CharSequence): String =
        separatorBefore(textBefore) + result.trim() + separatorAfter(textAfter)
}
