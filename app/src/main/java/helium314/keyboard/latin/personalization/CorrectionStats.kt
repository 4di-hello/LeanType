// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.compat.isUserLocked
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.min

/**
 * LeanType+: counts how well the autocorrection works for the user, only numbers, never words:
 * autocorrections, how many of them the user took back (right away with backspace, later by
 * changing the word back, or by tapping the typed word in the strip so it was not corrected),
 * typos the user corrected by hand that the autocorrection missed, and how often suggestions
 * were used. With the automatic autocorrection strength, the threshold is moved one step when
 * the numbers clearly say so, see [AutoCorrectTuner]. Nothing is counted in incognito mode and
 * password fields.
 */
object CorrectionStats {
    private const val TAG = "CorrectionStats"
    private const val PREFS_NAME = "correction_stats"
    private const val DAY_PREFIX = "day_"
    private const val WINDOW_KEY = "window"
    private const val LAST_CHANGE_KEY = "last_change"
    const val DAYS_KEPT = 30

    const val WORDS = 0
    const val AUTOCORRECTED = 1
    /** taken back right away with backspace */
    const val REVERTED = 2
    /** changed back to the typed word later */
    const val UNDONE = 3
    /** typed word tapped in the strip, so a pending autocorrection was not done */
    const val KEPT_TYPED = 4
    /** a typo that was not autocorrected, corrected by the user */
    const val MISSED = 5
    const val COMPLETIONS = 6
    const val PREDICTIONS = 7
    const val SIZE = 8

    /** level of the autocorrection strength setting that lets the keyboard choose the threshold */
    const val AUTO_LEVEL = 0

    private var appContext: Context? = null
    private var loaded = false
    private val days = HashMap<Int, IntArray>()
    private var window = IntArray(SIZE)
    private var dirty = false

    /** words of the last minutes, in memory only, to recognize later corrections */
    val tracker = CorrectionTracker()

    @Synchronized
    fun count(context: Context, what: Int) {
        if (!ensureLoaded(context)) return
        val today = today()
        days.getOrPut(today) { IntArray(SIZE) }[what]++
        window[what]++
        dirty = true
        if (what != WORDS && what != COMPLETIONS && what != PREDICTIONS) maybeTune(context)
    }

    /** Sums of the last [numDays] days, null if the stats can't be read (e.g. phone not unlocked yet) */
    @Synchronized
    fun lastDays(context: Context, numDays: Int = DAYS_KEPT): IntArray? {
        if (!ensureLoaded(context)) return null
        val today = today()
        val result = IntArray(SIZE)
        for ((day, counts) in days) {
            if (today - day < numDays) for (i in 0 until SIZE) result[i] += counts[i]
        }
        return result
    }

    /** last change of the automatic strength: day, old step, new step, reason; null if none */
    @Synchronized
    fun lastChange(context: Context): IntArray? {
        val prefs = statsPrefs(context) ?: return null
        val values = parse(prefs.getString(LAST_CHANGE_KEY, null)) ?: return null
        return values.takeIf { it.size == 4 }
    }

    fun dayToMillis(day: Int): Long = day.toLong() * 86_400_000L

    @Synchronized
    fun reset(context: Context) {
        days.clear()
        window = IntArray(SIZE)
        tracker.clear()
        dirty = false
        statsPrefs(context)?.edit { clear() }
    }

    /** The automatic strength starts over, e.g. after the strength was changed by hand */
    @Synchronized
    fun resetWindow(context: Context) {
        if (!ensureLoaded(context)) return
        window = IntArray(SIZE)
        dirty = true
        save()
    }

    /** Writes the counts, called when the input is finished */
    @Synchronized
    fun save() {
        if (!dirty) return
        val prefs = appContext?.let { statsPrefs(it) } ?: return
        dirty = false
        val today = today()
        days.keys.filter { today - it >= DAYS_KEPT }.forEach { days.remove(it) }
        prefs.edit {
            prefs.all.keys.filter { it.startsWith(DAY_PREFIX) }.forEach { remove(it) }
            for ((day, counts) in days) putString(DAY_PREFIX + day, counts.joinToString(","))
            putString(WINDOW_KEY, window.joinToString(","))
        }
    }

    private fun ensureLoaded(context: Context): Boolean {
        if (loaded) return true
        val app = context.applicationContext ?: context
        if (isUserLocked(app)) return false // credential encrypted storage is not available yet
        appContext = app
        val prefs = statsPrefs(app) ?: return false
        try {
            for ((key, value) in prefs.all) {
                if (!key.startsWith(DAY_PREFIX)) continue
                val day = key.removePrefix(DAY_PREFIX).toIntOrNull() ?: continue
                parseCounts(value as? String)?.let { days[day] = it }
            }
            parseCounts(prefs.getString(WINDOW_KEY, null))?.let { window = it }
        } catch (e: Exception) {
            Log.w(TAG, "could not read correction stats: ${e.javaClass.simpleName}")
        }
        loaded = true
        return true
    }

    private fun parse(value: String?): IntArray? {
        if (value.isNullOrEmpty()) return null
        val parts = value.split(",").map { it.trim().toIntOrNull() ?: return null }
        return parts.toIntArray()
    }

    /** counts as stored, with zeros for counts added in later versions */
    private fun parseCounts(value: String?): IntArray? {
        val parts = parse(value) ?: return null
        return IntArray(SIZE) { parts.getOrElse(it) { 0 }.coerceAtLeast(0) }
    }

    private fun statsPrefs(context: Context): SharedPreferences? = try {
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: Exception) {
        null // locked, see ensureLoaded
    }

    private fun today(): Int {
        val calendar = Calendar.getInstance()
        val offset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
        return ((System.currentTimeMillis() + offset) / 86_400_000L).toInt()
    }

    // ---------------- automatic autocorrection strength ----------------

    /** Moves the autocorrection threshold one step if the automatic strength is chosen and the numbers say so */
    private fun maybeTune(context: Context) {
        val prefs = context.prefs()
        if (!prefs.getBoolean(Settings.PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION)) return
        if (prefs.getInt(Settings.PREF_AUTO_CORRECT_AGGRESSIVENESS, Defaults.PREF_AUTO_CORRECT_AGGRESSIVENESS) != AUTO_LEVEL) return
        val current = AutoCorrectTuner.stepFor(prefs.getFloat(Settings.PREF_AUTO_CORRECT_THRESHOLD, Defaults.PREF_AUTO_CORRECT_THRESHOLD))
        val decision = AutoCorrectTuner.decide(window, current)
        if (decision == null) {
            if (window[WORDS] >= AutoCorrectTuner.WINDOW_MAX_WORDS) window = IntArray(SIZE) { window[it] / 2 }
            return
        }
        val (newStep, reason) = decision
        Log.i(TAG, "automatic autocorrection strength: step $current -> $newStep")
        prefs.edit { putFloat(Settings.PREF_AUTO_CORRECT_THRESHOLD, AutoCorrectTuner.STEPS[newStep]) }
        statsPrefs(context)?.edit { putString(LAST_CHANGE_KEY, "${today()},$current,$newStep,$reason") }
        window = IntArray(SIZE)
        dirty = true
    }
}

/**
 * Chooses the autocorrection threshold from the counts of [CorrectionStats]: more careful if the
 * user often takes corrections back, bolder if the user often corrects typos that were missed
 * and hardly takes any correction back. Only one step at a time, and only with enough data.
 */
object AutoCorrectTuner {
    /** thresholds from careful to bold; 0.185 and 0.067 are the "modest" and "aggressive" presets */
    val STEPS = floatArrayOf(0.185f, 0.12f, 0.067f, 0.04f)
    const val REASON_TAKEN_BACK = 1
    const val REASON_MISSED = 2

    private const val MIN_CORRECTIONS = 40
    private const val MIN_TAKEN_BACK = 5
    private const val TAKEN_BACK_RATE_TOO_HIGH = 0.12f
    private const val TAKEN_BACK_RATE_LOW = 0.06f
    private const val MIN_WORDS = 400
    private const val MIN_MISSED = 8
    private const val MISSED_RATE_TOO_HIGH = 0.012f
    /** older counts are halved from this number of words on, so the strength follows changes */
    const val WINDOW_MAX_WORDS = 3000

    fun stepFor(threshold: Float): Int {
        if (threshold < 0f) return STEPS.size - 1
        return STEPS.indices.minBy { abs(STEPS[it] - threshold) }
    }

    fun takenBack(counts: IntArray) = counts[CorrectionStats.REVERTED] + counts[CorrectionStats.UNDONE] + counts[CorrectionStats.KEPT_TYPED]

    /** autocorrections done or about to be done (the typed word was tapped instead) */
    fun corrections(counts: IntArray) = counts[CorrectionStats.AUTOCORRECTED] + counts[CorrectionStats.KEPT_TYPED]

    /** new step and reason, or null to keep the current step */
    fun decide(counts: IntArray, step: Int): Pair<Int, Int>? {
        val corrections = corrections(counts)
        val takenBack = takenBack(counts)
        val takenBackRate = if (corrections == 0) 0f else takenBack.toFloat() / corrections
        if (step > 0 && corrections >= MIN_CORRECTIONS && takenBack >= MIN_TAKEN_BACK && takenBackRate >= TAKEN_BACK_RATE_TOO_HIGH)
            return (step - 1) to REASON_TAKEN_BACK
        val words = counts[CorrectionStats.WORDS]
        val missed = counts[CorrectionStats.MISSED]
        if (step < STEPS.size - 1 && words >= MIN_WORDS && missed >= MIN_MISSED
            && missed.toFloat() / words >= MISSED_RATE_TOO_HIGH && takenBackRate <= TAKEN_BACK_RATE_LOW)
            return (step + 1) to REASON_MISSED
        return null
    }
}

/**
 * Remembers where the last words were committed (in memory only, for a few minutes), to notice
 * when the user goes back and changes one of them: an autocorrected word changed back to what
 * was typed means a wrong autocorrection, an unknown typed word changed slightly means a missed typo.
 */
class CorrectionTracker {
    class Entry(
        val start: Int, val word: String, val typed: String,
        /** [word] is known to the dictionaries, not needed for autocorrected words */
        val autocorrected: Boolean, val wordValid: Boolean, val time: Long
    )

    enum class Change { NONE, AUTOCORRECT_UNDONE, MISSED_TYPO }

    private val entries = ArrayList<Entry>()

    @Synchronized
    fun clear() = entries.clear()

    /** the last word was reverted with backspace, it is counted already */
    @Synchronized
    fun removeLast() {
        if (entries.isNotEmpty()) entries.removeAt(entries.size - 1)
    }

    /** A word was committed at [Entry.start]: what does it say about an earlier word at the same place? */
    @Synchronized
    fun onCommitted(entry: Entry): Change {
        entries.removeAll { entry.time - it.time > MAX_AGE_MS || it.time > entry.time }
        val old = entries.firstOrNull { it.start == entry.start }
        // words from here on were replaced, deleted or moved
        entries.removeAll { it.start >= entry.start }
        entries.add(entry)
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
        if (old == null || old.word.equals(entry.word, ignoreCase = true)) return Change.NONE
        return classify(old, entry.word)
    }

    companion object {
        const val MAX_AGE_MS = 10 * 60_000L
        private const val MAX_ENTRIES = 20
        private const val MAX_LENGTH = 48

        fun classify(old: Entry, newWord: String): Change {
            if (old.autocorrected) {
                // back to what was typed (maybe with a small fix): the autocorrection was not wanted
                return if (newWord.equals(old.typed, ignoreCase = true) || distance(newWord, old.typed) <= 1)
                    Change.AUTOCORRECT_UNDONE else Change.NONE
            }
            // an unknown word was changed a little: a typo the autocorrection did not fix
            if (!old.wordValid && distance(old.word, newWord) in 1..2) return Change.MISSED_TYPO
            return Change.NONE
        }

        /** edit distance ignoring case, large for long or very different words */
        fun distance(a: String, b: String): Int {
            if (a.length > MAX_LENGTH || b.length > MAX_LENGTH) return Int.MAX_VALUE
            val s = a.lowercase()
            val t = b.lowercase()
            if (abs(s.length - t.length) > 2) return abs(s.length - t.length)
            var previous = IntArray(t.length + 1) { it }
            var current = IntArray(t.length + 1)
            for (i in 1..s.length) {
                current[0] = i
                for (j in 1..t.length) {
                    val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                    current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
                }
                val swap = previous
                previous = current
                current = swap
            }
            return previous[t.length]
        }
    }
}
