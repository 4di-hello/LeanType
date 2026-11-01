// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * LeanType+: typos the user corrected by hand because the autocorrection missed them, e.g.
 * "Bescheud" changed to "Bescheid". After the same typo was corrected to the same word
 * [TIMES_TO_LEARN] times, it is autocorrected like this (revertible with backspace, which also
 * forgets it). Kept in the settings on this device, like [LearnedCasing].
 */
object LearnedTypos {
    private const val TAG = "LearnedTypos"
    const val TIMES_TO_LEARN = 2
    private const val MAX_ENTRIES = 300
    private const val MAX_WORD_LENGTH = 48

    class Entry(val typo: String, val correction: String, val count: Int)

    private var cachedJson: String? = null
    private var cache: LinkedHashMap<String, Entry> = LinkedHashMap()

    private fun key(word: String) = word.lowercase(Locale.ROOT)

    @Synchronized
    private fun read(prefs: SharedPreferences): LinkedHashMap<String, Entry> {
        val json = prefs.getString(Settings.PREF_LEARNED_TYPOS, "") ?: ""
        if (json == cachedJson) return cache
        val map = LinkedHashMap<String, Entry>()
        if (json.isNotEmpty()) {
            try {
                val o = JSONObject(json)
                for (k in o.keys()) {
                    val a = o.optJSONArray(k) ?: continue
                    val correction = a.optString(0, "")
                    val count = a.optInt(1, 0)
                    if (isLearnable(k, correction) && key(k) == k && count > 0) map[k] = Entry(k, correction, count)
                    if (map.size >= MAX_ENTRIES) break
                }
            } catch (t: Throwable) {
                Log.w(TAG, "could not read learned typos: ${t.javaClass.simpleName}")
            }
        }
        cachedJson = json
        cache = map
        return map
    }

    @Synchronized
    private fun write(prefs: SharedPreferences, map: Map<String, Entry>) {
        val o = JSONObject()
        map.forEach { (k, e) -> o.put(k, JSONArray().put(e.correction).put(e.count)) }
        val json = o.toString()
        prefs.edit { putString(Settings.PREF_LEARNED_TYPOS, json) }
        cachedJson = json
        cache = LinkedHashMap(map)
    }

    private fun isWord(word: String) = word.isNotEmpty() && word.length <= MAX_WORD_LENGTH
            && word.all { Character.isLetter(it) || it == '\'' || it == '’' || it == '-' }
            && Character.isLetter(word.first())

    /** a word typed with a mistake, and a different word (not only in case) to correct it to */
    fun isLearnable(typo: String, correction: String): Boolean =
        isWord(typo) && isWord(correction) && !typo.equals(correction, ignoreCase = true) && typo.length >= 2

    /**
     * The user corrected [typo] to [correction] by hand. [correction] should be in the form it is
     * used in the middle of a sentence, see [withCaseOf].
     */
    @Synchronized
    fun onTypoCorrected(prefs: SharedPreferences, typo: String, correction: String) {
        if (!isLearnable(typo, correction)) return
        val map = LinkedHashMap(read(prefs))
        val k = key(typo)
        val old = map.remove(k)
        val count = if (old != null && old.correction == correction) old.count + 1 else 1
        map[k] = Entry(k, correction, count) // re-insert: most recent last
        while (map.size > MAX_ENTRIES) map.remove(map.keys.first())
        write(prefs, map)
    }

    /** Word to correct [typed] to, null if it was not learned (often enough) */
    @Synchronized
    fun correctionFor(prefs: SharedPreferences, typed: String, locale: Locale): String? {
        if (typed.isEmpty() || typed.length > MAX_WORD_LENGTH) return null
        val entry = read(prefs)[key(typed)] ?: return null
        if (entry.count < TIMES_TO_LEARN) return null
        return withCaseOf(typed, entry.correction, locale)
    }

    /** [correction] capitalized or in upper case like [typed] was, e.g. at the start of a sentence */
    fun withCaseOf(typed: String, correction: String, locale: Locale): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> correction.uppercase(locale)
        typed.first().isUpperCase() && correction.first().isLowerCase() ->
            correction.substring(0, 1).uppercase(locale) + correction.substring(1)
        else -> correction
    }

    /** All learned typos (also those not yet corrected automatically) */
    @Synchronized
    fun getAll(prefs: SharedPreferences): List<Entry> = read(prefs).values.sortedBy { it.typo }

    @Synchronized
    fun forget(prefs: SharedPreferences, typo: String) {
        val map = LinkedHashMap(read(prefs))
        if (map.remove(key(typo)) != null) write(prefs, map)
    }

    @Synchronized
    fun clear(prefs: SharedPreferences) {
        write(prefs, emptyMap())
    }
}
