// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import android.content.Context
import android.os.Handler
import android.os.Looper
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * LeanType+: learns words and word pairs from the user's own texts (pasted or from a file), like
 * "personalize from your messages" in other keyboards. The text is queued in app storage and learned
 * into the user history of the current keyboard language the next time the keyboard opens, then it is
 * deleted. Words the dictionary doesn't know are learned only if they appear at least twice, so single
 * typos in the text are not learned.
 */
object TextLearner {
    private const val TAG = "TextLearner"
    private const val QUEUE_FILE = "learn_from_text.txt"
    const val MAX_CHARS = 500_000
    /** unknown words must appear this often, single typos are not learned */
    const val MIN_COUNT_UNKNOWN = 2
    /** words learned per step on the main thread, so typing is not blocked */
    private const val WORDS_PER_STEP = 40
    private const val START_DELAY_MS = 1500L

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var running = false

    private val SENTENCE_END = Regex("""[.!?…]+(?=\s|$)|\n\s*\n""")
    private val SEGMENT_BREAK = Regex("""[,;:()\[\]{}"“”„«»/\\|*_#<>=+]|\d|\s[-–—]\s|\n""")
    private val WORD = Regex("""\p{L}[\p{L}\p{M}'’-]*\p{L}|\p{L}""")

    /** A run of words without punctuation between them; the first one may start a sentence. */
    class Segment(val words: List<String>, val startsSentence: Boolean)

    fun segments(text: String): List<Segment> {
        val result = ArrayList<Segment>()
        for (sentence in text.split(SENTENCE_END)) {
            var first = true
            for (part in sentence.split(SEGMENT_BREAK)) {
                val words = WORD.findAll(part).map { it.value.trim('-', '\'', '’') }.filter { it.isNotEmpty() }.toList()
                if (words.isEmpty()) continue
                result.add(Segment(words, first))
                first = false
            }
        }
        return result
    }

    /** Words that are learned: known to the dictionary, or appearing at least [MIN_COUNT_UNKNOWN] times. */
    fun learnableWords(segments: List<Segment>, isKnownWord: (String) -> Boolean): Set<String> {
        val counts = HashMap<String, Int>()
        segments.forEach { s -> s.words.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts.filter { (word, count) -> count >= MIN_COUNT_UNKNOWN || isKnownWord(word) }.keys
    }

    /** Stores [text] to be learned when the keyboard opens next. Returns the number of words found. */
    fun queue(context: Context, text: String): Int {
        val trimmed = text.take(MAX_CHARS)
        val count = segments(trimmed).sumOf { it.words.size }
        if (count == 0) return 0
        val file = File(context.filesDir, QUEUE_FILE)
        val existing = if (file.exists()) file.readText() else ""
        file.writeText((existing + "\n\n" + trimmed).takeLast(MAX_CHARS))
        return count
    }

    fun hasPending(context: Context) = File(context.filesDir, QUEUE_FILE).let { it.exists() && it.length() > 0 }

    /** Learns a queued text a moment after the keyboard was opened, when the dictionaries are ready. */
    fun processPendingLater(ime: LatinIME) {
        if (running || !hasPending(ime)) return
        handler.postDelayed({ processPending(ime) }, START_DELAY_MS)
    }

    private fun processPending(ime: LatinIME) {
        if (running) return
        val settings = ime.settings.current
        val facilitator = ime.getDictionaryFacilitator()
        // keep the text for later: dictionaries not ready, or a private field
        if (!facilitator.isActive() || !facilitator.hasAtLeastOneInitializedMainDictionary() || settings.mIncognitoModeEnabled) return
        if (!settings.mUsePersonalizedDicts) {
            KeyboardSwitcher.getInstance().showToast(ime.getString(R.string.learn_from_text_personalization_off), false)
            return
        }
        val file = File(ime.filesDir, QUEUE_FILE)
        val text = try { file.readText() } catch (e: Exception) { return }
        file.delete()
        val segments = segments(text)
        val learnable = learnableWords(segments) { word ->
            facilitator.isValidSpellingWord(word) || facilitator.isValidSpellingWord(word.lowercase(settings.mLocale))
        }
        Log.i(TAG, "learning ${learnable.size} different words from ${segments.size} segments")
        running = true
        val timestamp = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        var segmentIndex = 0
        var wordIndex = 0
        var context = NgramContext.EMPTY_PREV_WORDS_INFO
        var learned = 0
        val step = object : Runnable {
            override fun run() {
                if (!facilitator.isActive()) { running = false; return }
                var done = 0
                while (done < WORDS_PER_STEP && segmentIndex < segments.size) {
                    val segment = segments[segmentIndex]
                    if (wordIndex == 0) context = if (segment.startsSentence) NgramContext.BEGINNING_OF_SENTENCE else NgramContext.EMPTY_PREV_WORDS_INFO
                    val word = segment.words[wordIndex]
                    if (word in learnable) {
                        val autoCapitalized = wordIndex == 0 && segment.startsSentence && word.first().isUpperCase()
                        facilitator.addToUserHistory(word, autoCapitalized, context, timestamp, settings.mBlockPotentiallyOffensive)
                        context = context.getNextNgramContext(NgramContext.WordInfo(word))
                        learned++
                    } else {
                        // a word that is not learned (probably a typo) must not become part of a word pair
                        context = NgramContext.EMPTY_PREV_WORDS_INFO
                    }
                    done++
                    wordIndex++
                    if (wordIndex >= segment.words.size) { segmentIndex++; wordIndex = 0 }
                }
                if (segmentIndex < segments.size) {
                    handler.postDelayed(this, 5)
                } else {
                    running = false
                    Log.i(TAG, "learned $learned words")
                    KeyboardSwitcher.getInstance().showToast(ime.getString(R.string.learn_from_text_done, learned), false)
                }
            }
        }
        handler.post(step)
    }
}
