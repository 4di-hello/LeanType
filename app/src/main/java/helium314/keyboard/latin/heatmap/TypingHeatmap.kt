// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import android.content.Context
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Collects where the user actually touches keys, labelled with the key the user meant to hit.
 *
 * The intended key is taken from the text that was finally used: the committed word (after
 * autocorrection or a picked suggestion), or the typed word if the autocorrection was reverted.
 * Touches are stored per key, relative to the key center and in units of key width / height.
 *
 * Privacy: data stays on the device, and samples are stored grouped by key without any order
 * across keys, so the typed text cannot be reconstructed from the stored data.
 */
object TypingHeatmap {
    private const val TAG = "TypingHeatmap"
    private const val FILE_NAME = "typing_heatmap.json"
    private const val MAX_SAMPLES_PER_KEY = 400
    /** Touches further away from the intended key center than this (in key sizes) are ignored */
    private const val MAX_OFFSET = 1.6f
    private const val SAVE_EVERY_SAMPLES = 40

    /** Position of a key, normalized to the keyboard size (0..1) */
    data class KeyShape(val code: Int, val label: String, val x: Float, val y: Float, val width: Float, val height: Float) {
        val centerX get() = x + width / 2
        val centerY get() = y + height / 2
    }

    /** Touch relative to the intended key center, in units of key width / height */
    data class Sample(val dx: Float, val dy: Float, val touchedCode: Int)

    class LayoutData(val id: String) {
        var shapes: List<KeyShape> = emptyList()
        /** keyboard height / width, for drawing */
        var aspectRatio: Float = 0.35f
        val samples = HashMap<Int, ArrayDeque<Sample>>()
        /** keyboard the shapes were taken from (not persisted) */
        internal var shapesSource: Any? = null
        val sampleCount get() = samples.values.sumOf { it.size }
    }

    /** Geometry of the currently shown keyboard, in keyboard pixels */
    interface KeyGeometry {
        val layoutId: String
        /** key that produces this (lowercase) code point, as x, y, width, height in keyboard pixels */
        fun keyRect(codePoint: Int): IntArray?
        fun snapshot(): List<KeyShape>
        val aspectRatio: Float
        /** identity of the keyboard, to avoid taking a new snapshot for every word */
        val source: Any
    }

    private class PendingWord(
        val geometry: KeyGeometry,
        val typedWord: String,
        val finalWord: String,
        val xs: IntArray,
        val ys: IntArray,
    )

    private val layouts = HashMap<String, LayoutData>()
    private var loaded = false
    private var pending: PendingWord? = null
    private var unsavedSamples = 0
    private var appContext: Context? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()

    // ---------------- recording ----------------

    /** A word was committed. It is only used once we know it was not reverted. */
    @Synchronized
    fun onWordCommitted(context: Context, geometry: KeyGeometry, typedWord: String, finalWord: String, xs: IntArray, ys: IntArray) {
        appContext = context.applicationContext
        finalizePending()
        if (typedWord.isEmpty() || finalWord.isEmpty()) return
        pending = PendingWord(geometry, typedWord, finalWord, xs.copyOf(), ys.copyOf())
    }

    /** The user reverted the autocorrection: the typed word was what they wanted */
    @Synchronized
    fun onCommitReverted() {
        val p = pending ?: return
        pending = PendingWord(p.geometry, p.typedWord, p.typedWord, p.xs, p.ys)
        finalizePending()
    }

    /** The user deletes into the committed word, so we don't know the final word */
    @Synchronized
    fun discardPending() {
        pending = null
    }

    /** Called when other input follows, i.e. the last committed word is final */
    @Synchronized
    fun finalizePending() {
        val p = pending ?: return
        pending = null
        val newSamples = computeSamples(p.typedWord, p.finalWord, p.xs, p.ys) { p.geometry.keyRect(it) }
        if (newSamples.isEmpty()) return
        ensureLoaded()
        val layout = layouts.getOrPut(p.geometry.layoutId) { LayoutData(p.geometry.layoutId) }
        if (layout.shapesSource !== p.geometry.source) {
            layout.shapes = p.geometry.snapshot()
            layout.aspectRatio = p.geometry.aspectRatio
            layout.shapesSource = p.geometry.source
        }
        for ((code, sample) in newSamples) {
            val list = layout.samples.getOrPut(code) { ArrayDeque() }
            list.addLast(sample)
            while (list.size > MAX_SAMPLES_PER_KEY) list.removeFirst()
        }
        unsavedSamples += newSamples.size
        if (unsavedSamples >= SAVE_EVERY_SAMPLES) save()
    }

    /** Input finished (keyboard closed or other field): finalize and write to disk */
    @Synchronized
    fun onFinishInput() {
        finalizePending()
        if (unsavedSamples > 0) save()
    }

    // ---------------- reading (settings screen) ----------------

    @Synchronized
    fun getLayouts(context: Context): List<LayoutData> {
        appContext = context.applicationContext
        ensureLoaded()
        return layouts.values.filter { it.sampleCount > 0 }.sortedByDescending { it.sampleCount }
    }

    @Synchronized
    fun clear(context: Context) {
        appContext = context.applicationContext
        layouts.clear()
        pending = null
        unsavedSamples = 0
        loaded = true
        val file = getFile(context)
        ioExecutor.execute { file.delete() }
    }

    // ---------------- pure logic (tested) ----------------

    /**
     * Pairs of (index in typed, index in final) for code points that correspond to each other,
     * i.e. matches and substitutions of a minimal edit alignment (case insensitive).
     */
    fun align(typed: IntArray, final: IntArray): List<Pair<Int, Int>> {
        val n = typed.size
        val m = final.size
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val cost = if (sameLetter(typed[i - 1], final[j - 1])) 0 else 1
            d[i][j] = minOf(d[i - 1][j - 1] + cost, d[i - 1][j] + 1, d[i][j - 1] + 1)
        }
        val pairs = ArrayList<Pair<Int, Int>>()
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            val cost = if (sameLetter(typed[i - 1], final[j - 1])) 0 else 1
            when {
                d[i][j] == d[i - 1][j - 1] + cost -> { pairs.add(i - 1 to j - 1); i--; j-- }
                d[i][j] == d[i - 1][j] + 1 -> i--
                else -> j--
            }
        }
        pairs.reverse()
        return pairs
    }

    /**
     * Samples (intended code to sample) from the touches of a typed word, using the final word
     * as ground truth. [keyRect] gives x, y, width, height for a lowercase code point.
     */
    fun computeSamples(typedWord: String, finalWord: String, xs: IntArray, ys: IntArray, keyRect: (Int) -> IntArray?): List<Pair<Int, Sample>> {
        val typed = StringUtils.toCodePointArray(typedWord)
        val final = StringUtils.toCodePointArray(finalWord)
        val pairs = align(typed, final)
        val substitutions = pairs.count { !sameLetter(typed[it.first], final[it.second]) }
        // unrelated word (e.g. a picked prediction): don't learn from it
        if (pairs.isEmpty() || substitutions > maxOf(1, pairs.size / 3)) return emptyList()
        val result = ArrayList<Pair<Int, Sample>>()
        for ((ti, fi) in pairs) {
            if (ti >= xs.size || ti >= ys.size) continue
            val x = xs[ti]
            val y = ys[ti]
            if (x < 0 || y < 0) continue // no coordinates
            val intended = lower(final[fi])
            if (!Character.isLetterOrDigit(intended)) continue
            val rect = keyRect(intended) ?: continue
            if (rect[2] <= 0 || rect[3] <= 0) continue
            val dx = (x - (rect[0] + rect[2] / 2f)) / rect[2]
            val dy = (y - (rect[1] + rect[3] / 2f)) / rect[3]
            if (abs(dx) > MAX_OFFSET || abs(dy) > MAX_OFFSET) continue
            result.add(intended to Sample(dx, dy, lower(typed[ti])))
        }
        return result
    }

    private fun lower(cp: Int) = Character.toLowerCase(cp)
    private fun sameLetter(a: Int, b: Int) = lower(a) == lower(b)

    // ---------------- persistence ----------------

    private fun getFile(context: Context) = File(DeviceProtectedUtils.getFilesDir(context), FILE_NAME)

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val context = appContext ?: return
        val file = getFile(context)
        if (!file.exists()) return
        try {
            fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            Log.w(TAG, "could not read typing heatmap", e)
        }
    }

    private fun save() {
        val context = appContext ?: return
        unsavedSamples = 0
        val text = toJson().toString()
        val file = getFile(context)
        ioExecutor.execute {
            try {
                val tmp = File(file.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(text)
                tmp.renameTo(file)
            } catch (e: Exception) {
                Log.w(TAG, "could not write typing heatmap", e)
            }
        }
    }

    private fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        val layoutArray = JSONArray()
        for (layout in layouts.values) {
            val l = JSONObject()
            l.put("id", layout.id)
            l.put("aspect", layout.aspectRatio.toDouble())
            val shapes = JSONArray()
            layout.shapes.forEach {
                shapes.put(JSONArray().put(it.code).put(it.label).put(r(it.x)).put(r(it.y)).put(r(it.width)).put(r(it.height)))
            }
            l.put("keys", shapes)
            val samples = JSONObject()
            for ((code, list) in layout.samples) {
                val flat = JSONArray()
                list.forEach { flat.put(r(it.dx)).put(r(it.dy)).put(it.touchedCode) }
                samples.put(code.toString(), flat)
            }
            l.put("samples", samples)
            layoutArray.put(l)
        }
        root.put("layouts", layoutArray)
        return root
    }

    private fun fromJson(root: JSONObject) {
        val layoutArray = root.optJSONArray("layouts") ?: return
        for (i in 0 until layoutArray.length()) {
            val l = layoutArray.getJSONObject(i)
            val layout = LayoutData(l.getString("id"))
            layout.aspectRatio = l.optDouble("aspect", 0.35).toFloat()
            val shapes = l.optJSONArray("keys") ?: JSONArray()
            layout.shapes = (0 until shapes.length()).map {
                val s = shapes.getJSONArray(it)
                KeyShape(s.getInt(0), s.getString(1), s.getDouble(2).toFloat(), s.getDouble(3).toFloat(),
                    s.getDouble(4).toFloat(), s.getDouble(5).toFloat())
            }
            val samples = l.optJSONObject("samples") ?: JSONObject()
            for (key in samples.keys()) {
                val flat = samples.getJSONArray(key)
                val list = ArrayDeque<Sample>()
                var j = 0
                while (j + 2 < flat.length()) {
                    list.addLast(Sample(flat.getDouble(j).toFloat(), flat.getDouble(j + 1).toFloat(), flat.getInt(j + 2)))
                    j += 3
                }
                layout.samples[key.toInt()] = list
            }
            layouts[layout.id] = layout
        }
    }

    private fun r(f: Float): Double = String.format(Locale.US, "%.3f", f).toDouble()

    /** for tests */
    @Synchronized
    internal fun resetForTest() {
        layouts.clear()
        pending = null
        unsavedSamples = 0
        loaded = true
        appContext = null
    }

    @Synchronized
    internal fun layoutForTest(id: String): LayoutData? = layouts[id]
}
