// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.Log
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.ln
import kotlin.math.pow

/**
 * Silent key correction (LeanType+): in fields without suggestions (address bar, search fields in
 * browsers), the keyboard can't correct whole words. Here a touch close to the border of two keys
 * is given to the key whose letter fits better to the letters typed before it in the same word,
 * using letter statistics of the dictionary ([LetterModel]). Only borderline touches are
 * changed: the advantage of the letter is limited to [MAX_LETTER_ADVANTAGE].
 *
 * Nothing is stored: the letter statistics are built from the dictionary in memory, and only the
 * last two letters of the word being typed are kept, also only in memory.
 */
object SilentKeyCorrection {
    private const val TAG = "SilentKeyCorrection"
    /** largest advantage of a letter from the letter statistics, in log likelihood */
    const val MAX_LETTER_ADVANTAGE = 1.5f
    /** the other key must be better by at least this much, in log likelihood */
    private const val MIN_GAIN = 0.1f
    /** typical variance of touches around the key center, in key sizes, if not known from the touches */
    private const val DEFAULT_VARIANCE = 0.09f
    /** start of a word in the context */
    const val WORD_START = '^'

    // ---------------- letters typed before the touch ----------------

    /** The last letters of the word left of the cursor, starting with [WORD_START] if the word start is among them */
    @Volatile private var context: String = ""
    /** cursor position [context] belongs to, -1 if unknown */
    @Volatile private var contextCursor = -1

    /** Letters left of the cursor at [cursor], or null if not known (e.g. the cursor was moved) */
    fun contextAt(cursor: Int): String? {
        val c = context
        return if (cursor >= 0 && cursor == contextCursor && c.isNotEmpty()) c else null
    }

    /** A field is opened: take the context from the text before the cursor, if the app provides it */
    fun onStartInput(editorInfo: EditorInfo?, cursor: Int) {
        context = ""
        contextCursor = -1
        if (editorInfo == null || cursor < 0) return
        val before = try { EditorInfoCompat.getInitialTextBeforeCursor(editorInfo, 3, 0) } catch (e: Exception) { null }
        val start = minOf(editorInfo.initialSelStart, editorInfo.initialSelEnd)
        context = when {
            before != null -> contextFromText(before)
            start == 0 -> WORD_START.toString()
            else -> return
        }
        contextCursor = cursor
    }

    /** The cursor was at [cursorBefore] before an input event, and is at [cursorAfter] now */
    fun onInput(codePoint: Int, isDelete: Boolean, isFunctional: Boolean, cursorBefore: Int, cursorAfter: Int) {
        val known = cursorBefore >= 0 && cursorBefore == contextCursor
        val newContext: String? = when {
            isDelete -> if (known && context.length > 1) context.dropLast(1) else null
            isFunctional -> if (known) context else null // shift, symbols etc.: nothing changes
            codePoint <= 0 -> null
            Character.isLetter(codePoint) -> if (known) append(context, codePoint) else null
            else -> WORD_START.toString() // separator, digit or symbol: a new word starts
        }
        if (newContext == null || cursorAfter < 0) {
            context = ""
            contextCursor = -1
        } else {
            context = newContext
            contextCursor = cursorAfter
        }
    }

    internal fun contextFromText(text: CharSequence): String {
        var result = WORD_START.toString()
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            result = if (Character.isLetter(cp)) append(result, cp) else WORD_START.toString()
            i += Character.charCount(cp)
        }
        return result
    }

    private fun append(context: String, codePoint: Int): String =
        (context + String(Character.toChars(Character.toLowerCase(codePoint)))).takeLast(2)

    /** Fields where it is used: text fields without word correction, not for passwords and email addresses */
    fun isSuitableField(settingsValues: SettingsValues): Boolean {
        val attributes = settingsValues.mInputAttributes
        if (attributes.mIsPasswordField) return false
        val inputType = attributes.mInputType
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        if (InputTypeUtils.isEmailVariation(inputType and InputType.TYPE_MASK_VARIATION)) return false
        return !settingsValues.needsToLookupSuggestions()
    }

    // ---------------- choosing the key ----------------

    /**
     * The key a touch at [x], [y] is meant for, if the letter statistics decide differently than
     * the spatial model alone, otherwise null. [offsets] are the learned offsets (may be empty),
     * [logProb] gives the log probability of a letter after the context.
     */
    fun choose(x: Float, y: Float, candidates: List<TypingHeatmap.Candidate>, offsets: Map<Int, FloatArray>,
               logProb: (Int) -> Float?): Int? {
        if (candidates.size < 2) return null
        val scores = candidates.map { TypingHeatmap.adaptedScore(x, y, it, offsets[it.code]) }
        val bestIndex = scores.indices.minByOrNull { scores[it] } ?: return null
        val best = candidates[bestIndex]
        val bestLetter = logProb(best.code) ?: return null
        // the typical variance of the layout is known if the offsets contain the spread
        val variance = offsets.values.firstOrNull { it.size >= 5 }?.get(4)?.coerceIn(0.04f, 0.16f) ?: DEFAULT_VARIANCE
        var result: Int? = null
        var resultGain = MIN_GAIN
        for (i in candidates.indices) {
            if (i == bestIndex) continue
            val c = candidates[i]
            // only keys the touch is close to
            if (TypingHeatmap.adaptedDistance(x, y, c, offsets[c.code]) > 1f) continue
            val letter = logProb(c.code) ?: continue
            val advantage = (letter - bestLetter).coerceAtMost(MAX_LETTER_ADVANTAGE)
            // scores are squared distances in key sizes: as log likelihood with the typical variance
            val spatialLoss = (scores[i] - scores[bestIndex]) / (2f * variance)
            val gain = advantage - spatialLoss
            if (gain > resultGain) {
                resultGain = gain
                result = c.code
            }
        }
        return result
    }

    // ---------------- letter statistics ----------------

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile var model: LetterModel? = null
        private set
    @Volatile private var building: Locale? = null

    /** Builds the letter statistics for the main dictionary in background, if not done yet */
    fun ensureModel(facilitator: DictionaryFacilitator) {
        val locale = try { facilitator.mainLocale } catch (e: Exception) { return }
        if (model?.locale == locale || building == locale) return
        building = locale
        executor.execute {
            try {
                val builder = LetterModel.Builder(locale)
                facilitator.forEachMainDictionaryWord { word, frequency -> builder.add(word, frequency) }
                // dictionaries may not be loaded yet: try again later
                if (builder.words >= LetterModel.MIN_WORDS) model = builder.build()
                Log.i(TAG, "letter statistics for $locale from ${builder.words} words")
            } catch (e: Exception) {
                Log.w(TAG, "could not build letter statistics", e)
            } finally {
                building = null
            }
        }
    }
}

/** How likely a letter is after the two letters before it in a word, from the words of a dictionary */
class LetterModel private constructor(
    val locale: Locale,
    private val index: Map<Int, Int>,
    private val tri: FloatArray, private val triTotal: FloatArray,
    private val bi: FloatArray, private val biTotal: FloatArray,
    private val uni: FloatArray, private val uniTotal: Float,
) {
    /**
     * Natural log of the probability of [codePoint] (lower case) after [context] (see
     * [SilentKeyCorrection.contextAt]), null if the letter is unknown.
     */
    fun logProb(context: String, codePoint: Int): Float? {
        val c = index[Character.toLowerCase(codePoint)] ?: return null
        val cps = StringUtils.toCodePointArray(context)
        if (cps.isEmpty()) return null
        val p1 = indexOf(cps[cps.size - 1]) ?: return null
        val p2 = if (cps.size >= 2) indexOf(cps[cps.size - 2]) else null
        var p = 0.1f * uni[c] / uniTotal
        val biSum = biTotal[p1]
        if (biSum > 0f) p += 0.3f * bi[p1 * SIZE + c] / biSum
        val triSum = if (p2 == null) 0f else triTotal[p2 * SIZE + p1]
        p += if (p2 != null && triSum > 0f) 0.6f * tri[(p2 * SIZE + p1) * SIZE + c] / triSum
            else if (biSum > 0f) 0.6f * bi[p1 * SIZE + c] / biSum // no trigram data: bigram only
            else 0.6f * uni[c] / uniTotal
        return ln(p + 1e-6f)
    }

    private fun indexOf(cp: Int): Int? = if (cp == SilentKeyCorrection.WORD_START.code) START else index[cp]

    class Builder(private val locale: Locale) {
        private val index = HashMap<Int, Int>()
        private val tri = FloatArray(SIZE * SIZE * SIZE)
        private val bi = FloatArray(SIZE * SIZE)
        private val uni = FloatArray(SIZE)
        var words = 0
            private set

        /** [frequency]: as in the dictionary, 0..255 on a log scale */
        fun add(word: String, frequency: Int) {
            if (word.isEmpty()) return
            // frequent words are typed much more often
            val weight = 2.0.pow(frequency.coerceIn(0, 255) / 24.0).toFloat()
            var p2 = START
            var p1 = START
            var i = 0
            var counted = false
            while (i < word.length) {
                val cp = Character.toLowerCase(word.codePointAt(i))
                i += Character.charCount(cp)
                val c = if (Character.isLetter(cp)) letterIndex(cp) else null
                if (c == null) { // not a letter, or too many different letters: a new word starts
                    p2 = START
                    p1 = START
                    continue
                }
                uni[c] += weight
                bi[p1 * SIZE + c] += weight
                tri[(p2 * SIZE + p1) * SIZE + c] += weight
                p2 = p1
                p1 = c
                counted = true
            }
            if (counted) words++
        }

        private fun letterIndex(cp: Int): Int? = index[cp] ?: if (index.size < SIZE - 1) (index.size + 1).also { index[cp] = it } else null

        fun build(): LetterModel {
            val triTotal = FloatArray(SIZE * SIZE)
            for (ctx in 0 until SIZE * SIZE) {
                var sum = 0f
                for (c in 0 until SIZE) sum += tri[ctx * SIZE + c]
                triTotal[ctx] = sum
            }
            val biTotal = FloatArray(SIZE)
            for (p in 0 until SIZE) {
                var sum = 0f
                for (c in 0 until SIZE) sum += bi[p * SIZE + c]
                biTotal[p] = sum
            }
            val uniTotal = uni.sum().coerceAtLeast(1f)
            return LetterModel(locale, HashMap(index), tri, triTotal, bi, biTotal, uni, uniTotal)
        }
    }

    companion object {
        /** letters per language, including the start of a word */
        private const val SIZE = 64
        private const val START = 0
        /** fewer words: the dictionary is not loaded (yet) */
        const val MIN_WORDS = 1000
    }
}
