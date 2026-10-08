// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TypingHeatmapTest {
    // simple keyboard: one row "qwertzuiop", keys 100 px wide and 150 px high, row starts at y = 0
    private val row = "qwertzuiop"
    private fun rect(cp: Int): IntArray? {
        val i = row.indexOf(cp.toChar())
        return if (i < 0) null else intArrayOf(i * 100, 0, 100, 150)
    }
    private fun center(c: Char) = row.indexOf(c) * 100 + 50

    private val geometry = object : TypingHeatmap.KeyGeometry {
        override val layoutId = "test"
        override fun keyRect(codePoint: Int) = rect(codePoint)
        override fun snapshot() = row.mapIndexed { i, c -> TypingHeatmap.KeyShape(c.code, c.toString(), i / 10f, 0f, 0.1f, 1f) }
        override val aspectRatio = 0.15f
        override val source: Any = this
    }
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() = TypingHeatmap.resetForTest()

    @Test fun `align pairs matches and substitutions`() {
        fun align(a: String, b: String) = TypingHeatmap.align(a.codePoints().toArray(), b.codePoints().toArray())
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), align("tor", "tür"))
        // missing letter in typed word: no pair for the inserted letter
        assertEquals(listOf(0 to 0, 1 to 2), align("pt", "pot"))
        // extra letter typed
        assertEquals(listOf(0 to 0, 2 to 1), align("pqo", "po"))
        // case does not matter
        assertEquals(listOf(0 to 0, 1 to 1), align("Wo", "wO"))
    }

    @Test fun `samples are relative to the intended key`() {
        // "tir" typed, "tor" meant: the i touch is a miss for o
        val xs = intArrayOf(center('t') + 10, center('i') + 30, center('r'))
        val ys = intArrayOf(75, 75 + 15, 0)
        val samples = TypingHeatmap.computeSamples("tir", "tor", xs, ys, ::rect)
        assertEquals(3, samples.size)
        val (code, miss) = samples[1]
        assertEquals('o'.code, code)
        assertEquals('i'.code, miss.touchedCode)
        assertEquals(-0.7f, miss.dx, 0.001f) // 70 px left of the o center
        assertEquals(0.1f, miss.dy, 0.001f)
        assertEquals(0.1f, samples[0].second.dx, 0.001f)
        assertEquals(-0.5f, samples[2].second.dy, 0.001f)
    }

    @Test fun `unrelated words and far touches are ignored`() {
        val xs = intArrayOf(center('q'), center('w'), center('e'))
        val ys = intArrayOf(75, 75, 75)
        assertTrue(TypingHeatmap.computeSamples("qwe", "pou", xs, ys, ::rect).isEmpty())
        // touch 3 keys away from intended key
        val far = TypingHeatmap.computeSamples("ie", "te", intArrayOf(center('i'), center('e')), intArrayOf(75, 75), ::rect)
        assertEquals(1, far.size)
        assertEquals('e'.code, far[0].first)
        // no coordinates
        assertTrue(TypingHeatmap.computeSamples("we", "we", intArrayOf(-1, -1), intArrayOf(-1, -1), ::rect).isEmpty())
    }

    @Test fun `committed word is recorded when the next input follows`() {
        val xs = intArrayOf(center('w'), center('o') - 80) // second touch hits i
        TypingHeatmap.onWordCommitted(context, geometry, "wi", "wo", xs, intArrayOf(75, 75))
        assertNull(TypingHeatmap.layoutForTest("test"))
        TypingHeatmap.finalizePending()
        val layout = TypingHeatmap.layoutForTest("test")!!
        assertEquals(2, layout.sampleCount)
        assertEquals('i'.code, layout.samples['o'.code]!!.first().touchedCode)
    }

    @Test fun `reverted autocorrection uses the typed word`() {
        val xs = intArrayOf(center('w'), center('i'))
        TypingHeatmap.onWordCommitted(context, geometry, "wi", "wo", xs, intArrayOf(75, 75))
        TypingHeatmap.onCommitReverted()
        val layout = TypingHeatmap.layoutForTest("test")!!
        assertNull(layout.samples['o'.code])
        assertEquals('i'.code, layout.samples['i'.code]!!.first().touchedCode)
    }

    @Test fun `deleting into the committed word discards it`() {
        TypingHeatmap.onWordCommitted(context, geometry, "wi", "wo", intArrayOf(center('w'), center('i')), intArrayOf(75, 75))
        TypingHeatmap.discardPending()
        TypingHeatmap.finalizePending()
        assertNull(TypingHeatmap.layoutForTest("test"))
    }
}
