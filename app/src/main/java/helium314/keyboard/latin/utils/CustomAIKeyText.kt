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

    private val PLACEHOLDERS = Regex("""\{\s*(text|clipboard)\s*\}""", RegexOption.IGNORE_CASE)

    /** `{Text}`, `{ TEXT }` and the like are meant as placeholders too */
    fun normalizePlaceholders(prompt: String): String =
        PLACEHOLDERS.replace(prompt) { "{" + it.groupValues[1].lowercase() + "}" }

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

    /**
     * For "only corrections" (#corrections): the model lists the mistakes instead of writing the whole
     * text again, which takes a fraction of the time. [applyCorrections] then fixes them in the text.
     */
    const val CORRECTIONS_INSTRUCTION = "List only the mistakes in the text, one per line, in the form: wrong → right. " +
        "Copy the wrong part exactly as it is in the text; if it occurs more than once, include the neighbouring words. " +
        "Do not write the whole text. If there are no mistakes, write only: OK"

    /** Used if a corrections key has no prompt of its own */
    const val CORRECTIONS_DEFAULT_PROMPT = "Correct spelling, grammar, punctuation and capitalization. Keep the language of the text."

    private val ARROW = Regex("""\s*(?:→|->|=>|⇒|➔)\s*""")
    private val LIST_MARK = Regex("""^\s*(?:[-*•]|\d+[.)])\s*""")

    /** wrong → right pairs from the model output, in their order */
    fun parseCorrections(output: String): List<Pair<String, String>> = output.lines().mapNotNull { line ->
        val parts = line.replace(LIST_MARK, "").split(ARROW, limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val wrong = unquote(parts[0])
        val right = unquote(parts[1])
        if (wrong.isEmpty() || wrong == right) null else wrong to right
    }

    private fun unquote(s: String): String {
        // double quotes also one-sided: the output cleanup may remove the first and last one of the output
        var t = s.trim().trim('"', '„', '“', '”', '«', '»', '`').trim()
        if (t.length >= 2 && t.startsWith("'") && t.endsWith("'")) t = t.substring(1, t.length - 1).trim()
        return t
    }

    /**
     * Applies the corrections in [output] to [text]. Each one replaces the next occurrence after the
     * previous correction, or the first one in the text; corrections not found in the text are skipped.
     * Returns the corrected text and the number of corrections applied.
     */
    fun applyCorrections(text: String, output: String): Pair<String, Int> {
        var result = text
        var position = 0
        var count = 0
        for ((wrong, right) in parseCorrections(output)) {
            val index = findOccurrence(result, wrong, position)
            if (index < 0) continue
            result = result.substring(0, index) + right + result.substring(index + wrong.length)
            position = index + right.length
            count++
        }
        return result to count
    }

    /** Where [part] is in [text]: preferably as whole words and after [from], -1 if not at all */
    private fun findOccurrence(text: String, part: String, from: Int): Int {
        fun wholeWord(index: Int): Boolean {
            val before = text.getOrNull(index - 1)
            val after = text.getOrNull(index + part.length)
            return !(before != null && before.isLetterOrDigit() && part.first().isLetterOrDigit())
                && !(after != null && after.isLetterOrDigit() && part.last().isLetterOrDigit())
        }
        fun search(start: Int, wordsOnly: Boolean): Int {
            var index = text.indexOf(part, start)
            while (index >= 0 && wordsOnly && !wholeWord(index)) index = text.indexOf(part, index + 1)
            return index
        }
        for (wordsOnly in listOf(true, false)) {
            val index = search(from.coerceIn(0, text.length), wordsOnly).takeIf { it >= 0 } ?: search(0, wordsOnly)
            if (index >= 0) return index
        }
        return -1
    }
}
