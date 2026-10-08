// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import android.content.Context
import android.os.Build
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Learns where the user actually touches keys, and adapts key detection to it.
 *
 * The intended key is taken from the text that was finally used: the committed word (after
 * autocorrection or a picked suggestion), the typed word if the autocorrection was reverted,
 * or the retyped letter if the user deleted a letter and typed a neighboring one instead.
 * Touches are stored per key, relative to the key center and in units of key width / height.
 *
 * From the touches, each key gets an average offset, which is used to decide which key a touch
 * was meant for ([adaptedKeyCode]) and to move the touch coordinates passed to the dictionary
 * ([adjustForDictionary]). Learning always uses the raw touch positions, so adaptation does not
 * feed back into what is learned.
 *
 * Privacy: data stays on the device (no network, excluded from Android cloud backup), and
 * samples are stored grouped by key without any order across keys, so the typed text cannot be
 * reconstructed from the stored data.
 */
object TypingHeatmap {
    private const val TAG = "TypingHeatmap"
    const val FILE_NAME = "typing_heatmap.json"
    private const val MAX_SAMPLES_PER_KEY = 400
    /** Touches further away from the intended key center than this (in key sizes) are ignored */
    private const val MAX_OFFSET = 1.6f
    private const val SAVE_EVERY_SAMPLES = 40
    /** a key is adapted only with at least this many samples ... */
    const val MIN_SAMPLES_FOR_ADAPTATION = 30
    /** ... and fully from this number of samples on */
    const val FULL_SAMPLES_FOR_ADAPTATION = 100
    /** maximum shift of a key center for strength 1..5, in key sizes */
    private val MAX_SHIFT_FOR_STRENGTH = floatArrayOf(0.2f, 0.27f, 0.33f, 0.42f, 0.5f)
    const val DEFAULT_STRENGTH = 3

    /** Position of a key, normalized to the keyboard size (0..1) */
    data class KeyShape(val code: Int, val label: String, val x: Float, val y: Float, val width: Float, val height: Float) {
        val centerX get() = x + width / 2
        val centerY get() = y + height / 2
    }

    /**
     * Touch relative to the intended key center, in units of key width / height.
     * [adapted]: whether adaptation was active when the touch was made.
     */
    data class Sample(val dx: Float, val dy: Float, val touchedCode: Int, val adapted: Boolean = false)

    class LayoutData(val id: String) {
        var shapes: List<KeyShape> = emptyList()
        /** keyboard height / width, for drawing */
        var aspectRatio: Float = 0.35f
        val samples = HashMap<Int, ArrayDeque<Sample>>()
        val sampleCount get() = samples.values.sumOf { it.size }
        /** keyboard the shapes were taken from (not persisted) */
        internal var shapesSource: Any? = null
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

    /** A key considered for adapted detection: code and x, y, width, height in any unit */
    class Candidate(val code: Int, val x: Float, val y: Float, val width: Float, val height: Float)

    private class PendingWord(
        val geometry: KeyGeometry,
        val typedWord: String,
        val finalWord: String,
        val xs: IntArray,
        val ys: IntArray,
        val adapted: Boolean,
    )

    private val layouts = HashMap<String, LayoutData>()
    private var loaded = false
    private var pending: PendingWord? = null
    private var unsavedSamples = 0
    private var appContext: Context? = null
    private val ioExecutor = Executors.newSingleThreadExecutor()
    /** device the data was learned on, null if unknown (no data yet) */
    private var learnedOnDevice: String? = null
    val currentDevice: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /** cache: layout id + strength -> offsets per code as [dx, dy] */
    @Volatile private var offsetCache = HashMap<String, Map<Int, FloatArray>>()

    /** adjusted dictionary coordinates -> raw touch coordinates, for the last touches */
    private val rawTouches = object : LinkedHashMap<Long, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?) = size > 128
    }

    /** Make sure stored data can be loaded, e.g. for adapting key detection */
    @Synchronized
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    // ---------------- recording ----------------

    /** A word was committed. It is only used once we know it was not reverted. */
    @Synchronized
    fun onWordCommitted(context: Context, geometry: KeyGeometry, typedWord: String, finalWord: String,
                        xs: IntArray, ys: IntArray, adapted: Boolean = false) {
        appContext = context.applicationContext
        finalizePending()
        if (typedWord.isEmpty() || finalWord.isEmpty()) return
        val rawXs = xs.copyOf()
        val rawYs = ys.copyOf()
        for (i in rawXs.indices) {
            if (i >= rawYs.size) break
            val raw = rawTouches[pack(rawXs[i], rawYs[i])] ?: continue
            rawXs[i] = unpackX(raw)
            rawYs[i] = unpackY(raw)
        }
        pending = PendingWord(geometry, typedWord, finalWord, rawXs, rawYs, adapted)
    }

    /** The user reverted the autocorrection: the typed word was what they wanted */
    @Synchronized
    fun onCommitReverted() {
        val p = pending ?: return
        pending = PendingWord(p.geometry, p.typedWord, p.typedWord, p.xs, p.ys, p.adapted)
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
        val newSamples = computeSamples(p.typedWord, p.finalWord, p.xs, p.ys, p.adapted) { p.geometry.keyRect(it) }
        addSamples(p.geometry, newSamples)
    }

    /**
     * The user deleted [deletedCodePoint] (touched at [x], [y]) and typed [newCodePoint] instead.
     * If the keys are neighbors, the touch was a miss for the new letter.
     */
    @Synchronized
    fun onLetterRetyped(context: Context, geometry: KeyGeometry, deletedCodePoint: Int, x: Int, y: Int,
                        newCodePoint: Int, adapted: Boolean = false) {
        appContext = context.applicationContext
        val raw = rawTouches[pack(x, y)]
        val rawX = if (raw == null) x else unpackX(raw)
        val rawY = if (raw == null) y else unpackY(raw)
        val sample = selfCorrectionSample(deletedCodePoint, rawX, rawY, newCodePoint, adapted) { geometry.keyRect(it) } ?: return
        addSamples(geometry, listOf(sample))
    }

    private fun addSamples(geometry: KeyGeometry, newSamples: List<Pair<Int, Sample>>) {
        if (newSamples.isEmpty()) return
        ensureLoaded()
        if (learnedOnDevice == null) learnedOnDevice = currentDevice
        val layout = layouts.getOrPut(geometry.layoutId) { LayoutData(geometry.layoutId) }
        if (layout.shapesSource !== geometry.source) {
            layout.shapes = geometry.snapshot()
            layout.aspectRatio = geometry.aspectRatio
            layout.shapesSource = geometry.source
        }
        for ((code, sample) in newSamples) {
            val list = layout.samples.getOrPut(code) { ArrayDeque() }
            list.addLast(sample)
            while (list.size > MAX_SAMPLES_PER_KEY) list.removeFirst()
        }
        offsetCache = HashMap()
        unsavedSamples += newSamples.size
        if (unsavedSamples >= SAVE_EVERY_SAMPLES) save()
    }

    /** Input finished (keyboard closed or other field): finalize and write to disk */
    @Synchronized
    fun onFinishInput() {
        finalizePending()
        if (unsavedSamples > 0) save()
    }

    // ---------------- adaptation ----------------

    /** Effective offsets per key code for a layout, empty if there is not enough data */
    fun getOffsets(context: Context?, layoutId: String, strength: Int): Map<Int, FloatArray> {
        val cacheKey = "$layoutId|$strength"
        offsetCache[cacheKey]?.let { return it }
        synchronized(this) {
            if (context != null && appContext == null) appContext = context.applicationContext
            ensureLoaded()
            if (!loaded) return emptyMap() // no context yet, try again later
            val maxShift = maxShift(strength)
            val result = HashMap<Int, FloatArray>()
            layouts[layoutId]?.samples?.forEach { (code, list) ->
                effectiveOffset(list, maxShift)?.let { result[code] = it }
            }
            val newCache = HashMap(offsetCache)
            newCache[cacheKey] = result
            offsetCache = newCache
            return result
        }
    }

    fun maxShift(strength: Int) = MAX_SHIFT_FOR_STRENGTH[(strength - 1).coerceIn(0, MAX_SHIFT_FOR_STRENGTH.size - 1)]

    /**
     * Average offset of the touches meant for a key, weighted by the amount of data and limited
     * to [maxShift]. Null if there is not enough data.
     */
    fun effectiveOffset(samples: Collection<Sample>, maxShift: Float): FloatArray? {
        val n = samples.size
        if (n < MIN_SAMPLES_FOR_ADAPTATION) return null
        val weight = ((n - MIN_SAMPLES_FOR_ADAPTATION + 1).toFloat() /
                (FULL_SAMPLES_FOR_ADAPTATION - MIN_SAMPLES_FOR_ADAPTATION + 1)).coerceIn(0f, 1f)
        // limit single outliers
        var dx = samples.sumOf { it.dx.coerceIn(-1f, 1f).toDouble() }.toFloat() / n * weight
        var dy = samples.sumOf { it.dy.coerceIn(-1f, 1f).toDouble() }.toFloat() / n * weight
        val length = hypot(dx, dy)
        if (length > maxShift) {
            dx *= maxShift / length
            dy *= maxShift / length
        }
        return floatArrayOf(dx, dy)
    }

    /**
     * Code of the key a touch at [x], [y] is meant for, if adaptation decides differently than
     * plain key centers would. Null means: keep the normal detection.
     * Only the candidates are considered, distances are relative to the key size.
     */
    fun adaptedKeyCode(x: Float, y: Float, candidates: List<Candidate>, offsets: Map<Int, FloatArray>): Int? {
        if (offsets.isEmpty() || candidates.size < 2) return null
        var bestPlain: Candidate? = null
        var bestPlainDistance = Float.MAX_VALUE
        var bestAdapted: Candidate? = null
        var bestAdaptedDistance = Float.MAX_VALUE
        for (c in candidates) {
            val cx = c.x + c.width / 2
            val cy = c.y + c.height / 2
            val plain = sq((x - cx) / c.width) + sq((y - cy) / c.height)
            val offset = offsets[c.code]
            val adapted = if (offset == null) plain
                else sq((x - cx - offset[0] * c.width) / c.width) + sq((y - cy - offset[1] * c.height) / c.height)
            if (plain < bestPlainDistance) { bestPlainDistance = plain; bestPlain = c }
            if (adapted < bestAdaptedDistance) { bestAdaptedDistance = adapted; bestAdapted = c }
        }
        if (bestAdapted == null || bestAdapted === bestPlain) return null
        // don't take touches that are far away from the adapted key
        if (bestAdaptedDistance > 1f) return null
        return bestAdapted.code
    }

    /**
     * Touch coordinates for the dictionary: moved by the learned offset of the detected key, so
     * the spatial model of the dictionary sees the touch where an average touch for the key is.
     * Remembers the raw position, so learning is not affected.
     */
    @Synchronized
    fun adjustForDictionary(x: Int, y: Int, keyX: Int, keyY: Int, keyWidth: Int, keyHeight: Int, offset: FloatArray?): IntArray {
        if (offset == null) return intArrayOf(x, y)
        val ax = (x - offset[0] * keyWidth).toInt().coerceAtLeast(0)
        val ay = (y - offset[1] * keyHeight).toInt().coerceAtLeast(0)
        if (ax != x || ay != y) rawTouches[pack(ax, ay)] = pack(x, y)
        return intArrayOf(ax, ay)
    }

    private fun sq(f: Float) = f * f
    private fun pack(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
    private fun unpackX(p: Long) = (p shr 32).toInt()
    private fun unpackY(p: Long) = p.toInt()

    // ---------------- reading (settings screen) ----------------

    @Synchronized
    fun getLayouts(context: Context): List<LayoutData> {
        appContext = context.applicationContext
        ensureLoaded()
        return layouts.values.filter { it.sampleCount > 0 }.sortedByDescending { it.sampleCount }
    }

    /** Device the data was learned on, if it is not this device */
    @Synchronized
    fun getOtherLearnedOnDevice(context: Context): String? {
        appContext = context.applicationContext
        ensureLoaded()
        val device = learnedOnDevice ?: return null
        return if (device == currentDevice || layouts.isEmpty()) null else device
    }

    /** Keep data that was learned on another device, and continue learning on this one */
    @Synchronized
    fun acceptDataFromOtherDevice(context: Context) {
        appContext = context.applicationContext
        learnedOnDevice = currentDevice
        save()
    }

    @Synchronized
    fun clear(context: Context) {
        appContext = context.applicationContext
        layouts.clear()
        pending = null
        unsavedSamples = 0
        loaded = true
        learnedOnDevice = null
        offsetCache = HashMap()
        val file = getFile(context)
        ioExecutor.execute { file.delete() }
    }

    /** Data file was replaced, e.g. by restoring a backup */
    @Synchronized
    fun reload() {
        layouts.clear()
        pending = null
        unsavedSamples = 0
        loaded = false
        learnedOnDevice = null
        offsetCache = HashMap()
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
    fun computeSamples(typedWord: String, finalWord: String, xs: IntArray, ys: IntArray,
                       adapted: Boolean = false, keyRect: (Int) -> IntArray?): List<Pair<Int, Sample>> {
        val typed = StringUtils.toCodePointArray(typedWord)
        val final = StringUtils.toCodePointArray(finalWord)
        val pairs = align(typed, final)
        val substitutions = pairs.count { !sameLetter(typed[it.first], final[it.second]) }
        // unrelated word (e.g. a picked prediction): don't learn from it
        if (pairs.isEmpty() || substitutions > maxOf(1, pairs.size / 3)) return emptyList()
        val result = ArrayList<Pair<Int, Sample>>()
        for ((ti, fi) in pairs) {
            if (ti >= xs.size || ti >= ys.size) continue
            val sample = sampleFor(lower(final[fi]), lower(typed[ti]), xs[ti], ys[ti], adapted, keyRect) ?: continue
            result.add(sample)
        }
        return result
    }

    /** Sample for a touch at [x], [y] that was meant for [newCodePoint], if the keys are neighbors */
    fun selfCorrectionSample(deletedCodePoint: Int, x: Int, y: Int, newCodePoint: Int, adapted: Boolean = false,
                             keyRect: (Int) -> IntArray?): Pair<Int, Sample>? {
        val deleted = lower(deletedCodePoint)
        val intended = lower(newCodePoint)
        if (deleted == intended) return null
        val deletedRect = keyRect(deleted) ?: return null
        val intendedRect = keyRect(intended) ?: return null
        // neighbor keys: centers at most about one key apart (also diagonally)
        val distX = abs((deletedRect[0] + deletedRect[2] / 2f) - (intendedRect[0] + intendedRect[2] / 2f)) / intendedRect[2]
        val distY = abs((deletedRect[1] + deletedRect[3] / 2f) - (intendedRect[1] + intendedRect[3] / 2f)) / intendedRect[3]
        if (distX > 1.1f || distY > 1.1f) return null
        return sampleFor(intended, deleted, x, y, adapted, keyRect)
    }

    private fun sampleFor(intended: Int, touched: Int, x: Int, y: Int, adapted: Boolean, keyRect: (Int) -> IntArray?): Pair<Int, Sample>? {
        if (x < 0 || y < 0) return null // no coordinates
        if (!Character.isLetterOrDigit(intended)) return null
        val rect = keyRect(intended) ?: return null
        if (rect[2] <= 0 || rect[3] <= 0) return null
        val dx = (x - (rect[0] + rect[2] / 2f)) / rect[2]
        val dy = (y - (rect[1] + rect[3] / 2f)) / rect[3]
        if (abs(dx) > MAX_OFFSET || abs(dy) > MAX_OFFSET) return null
        return intended to Sample(dx, dy, touched, adapted)
    }

    private fun lower(cp: Int) = Character.toLowerCase(cp)
    private fun sameLetter(a: Int, b: Int) = lower(a) == lower(b)

    // ---------------- persistence ----------------

    private fun getFile(context: Context) = File(DeviceProtectedUtils.getFilesDir(context), FILE_NAME)

    private fun ensureLoaded() {
        if (loaded) return
        val context = appContext ?: return
        loaded = true
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
        root.put("version", 2)
        root.put("device", learnedOnDevice ?: currentDevice)
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
                list.forEach { flat.put(r(it.dx)).put(r(it.dy)).put(it.touchedCode).put(if (it.adapted) 1 else 0) }
                samples.put(code.toString(), flat)
            }
            l.put("samples", samples)
            layoutArray.put(l)
        }
        root.put("layouts", layoutArray)
        return root
    }

    private fun fromJson(root: JSONObject) {
        val version = root.optInt("version", 1)
        val valuesPerSample = if (version >= 2) 4 else 3
        learnedOnDevice = root.optString("device", "").ifEmpty { null }
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
                while (j + valuesPerSample - 1 < flat.length()) {
                    val adapted = valuesPerSample == 4 && flat.getInt(j + 3) == 1
                    list.addLast(Sample(flat.getDouble(j).toFloat(), flat.getDouble(j + 1).toFloat(), flat.getInt(j + 2), adapted))
                    j += valuesPerSample
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
        learnedOnDevice = null
        offsetCache = HashMap()
        rawTouches.clear()
    }

    @Synchronized
    internal fun layoutForTest(id: String): LayoutData? = layouts[id]
}
