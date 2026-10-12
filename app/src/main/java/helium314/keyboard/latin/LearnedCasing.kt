// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import org.json.JSONObject
import java.util.Locale

/**
 * Remembers how the user writes words, learned from changing the case of a word with shift
 * (see [Settings.PREF_SHIFT_RECAPITALIZES_WORD]). After the same word was changed to the same
 * form [TIMES_TO_LEARN] times, it is written like this automatically (as a revertible correction).
 * Only forms containing upper case letters are learned, e.g. "Du" or "NASA".
 */
object LearnedCasing {
    private const val TAG = "LearnedCasing"
    const val TIMES_TO_LEARN = 2
    private const val MAX_ENTRIES = 300
    private const val MAX_WORD_LENGTH = 48

    class Entry(val form: String, val count: Int)

    private var cachedJson: String? = null
    private var cache: LinkedHashMap<String, Entry> = LinkedHashMap()

    private fun key(word: String) = word.lowercase(Locale.ROOT)

    @Synchronized
    private fun read(prefs: SharedPreferences): LinkedHashMap<String, Entry> {
        val json = prefs.getString(Settings.PREF_LEARNED_CASING, "") ?: ""
        if (json == cachedJson) return cache
        val map = LinkedHashMap<String, Entry>()
        if (json.isNotEmpty()) {
            try {
                val o = JSONObject(json)
                for (k in o.keys()) {
                    val a = o.optJSONArray(k) ?: continue
                    val form = a.optString(0, "")
                    val count = a.optInt(1, 0)
                    if (isLearnableForm(form) && key(form) == k && count > 0) map[k] = Entry(form, count)
                    if (map.size >= MAX_ENTRIES) break
                }
            } catch (t: Throwable) {
                Log.w(TAG, "could not read learned casing: ${t.javaClass.simpleName}")
            }
        }
        cachedJson = json
        cache = map
        return map
    }

    @Synchronized
    private fun write(prefs: SharedPreferences, map: Map<String, Entry>) {
        val o = JSONObject()
        map.forEach { (k, e) -> o.put(k, org.json.JSONArray().put(e.form).put(e.count)) }
        val json = o.toString()
        prefs.edit { putString(Settings.PREF_LEARNED_CASING, json) }
        cachedJson = json
        cache = LinkedHashMap(map)
    }

    /** words with upper case letters, nothing else than letters, apostrophes and hyphens */
    fun isLearnableForm(form: String): Boolean =
        form.isNotEmpty() && form.length <= MAX_WORD_LENGTH
            && form.any { Character.isUpperCase(it) }
            && form.all { Character.isLetter(it) || it == '\'' || it == '-' }

    /** The user changed the case of [form] with shift and used it */
    @Synchronized
    fun onRecapitalizedWordUsed(prefs: SharedPreferences, form: String) {
        if (!isLearnableForm(form)) return
        val map = LinkedHashMap(read(prefs))
        val k = key(form)
        val old = map.remove(k)
        val count = if (old != null && old.form == form) old.count + 1 else 1
        map[k] = Entry(form, count) // re-insert: most recent last
        while (map.size > MAX_ENTRIES) map.remove(map.keys.first())
        write(prefs, map)
    }

    /** Learned form for [word] if it differs from [word], null otherwise */
    @Synchronized
    fun learnedFormFor(prefs: SharedPreferences, word: String): String? {
        if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return null
        val entry = read(prefs)[key(word)] ?: return null
        if (entry.count < TIMES_TO_LEARN || entry.form == word) return null
        return entry.form
    }

    /** All learned forms (also those not yet used automatically) */
    @Synchronized
    fun getAll(prefs: SharedPreferences): List<Entry> = read(prefs).values.sortedBy { it.form.lowercase(Locale.ROOT) }

    @Synchronized
    fun forget(prefs: SharedPreferences, word: String) {
        val map = LinkedHashMap(read(prefs))
        if (map.remove(key(word)) != null) write(prefs, map)
    }

    @Synchronized
    fun clear(prefs: SharedPreferences) {
        write(prefs, emptyMap())
    }
}
