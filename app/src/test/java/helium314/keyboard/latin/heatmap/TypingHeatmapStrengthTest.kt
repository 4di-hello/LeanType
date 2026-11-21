// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TypingHeatmapStrengthTest {
    private val shapes = listOf(
        TypingHeatmap.KeyShape('a'.code, "a", 0f, 0f, 0.1f, 0.25f),
        TypingHeatmap.KeyShape('b'.code, "b", 0.1f, 0f, 0.1f, 0.25f),
    )

    @Test
    fun touchesRightOfTheKeyAreFixedByAdaptation() {
        // 'a' is always hit far right, beyond its border; 'b' is hit normally
        val samples = mapOf(
            'a'.code to List(200) { TypingHeatmap.Sample(0.55f, 0f, 'b'.code) },
            'b'.code to List(200) { TypingHeatmap.Sample(0.2f, 0f, 'b'.code) },
        )
        val evaluation = TypingHeatmap.evaluateStrengths(shapes, samples)
        assertNotNull(evaluation)
        assertEquals(400, evaluation!!.touches)
        assertEquals(200, evaluation.misses[0]) // without adaptation every 'a' becomes 'b'
        assertEquals(0, evaluation.misses[evaluation.best])
        assertEquals(1, evaluation.best) // the lowest strength that works
    }

    @Test
    fun tooFewTouchesGiveNoEvaluation() {
        val samples = mapOf('a'.code to List(50) { TypingHeatmap.Sample(0f, 0f, 'a'.code) })
        assertNull(TypingHeatmap.evaluateStrengths(shapes, samples))
    }

    @Test
    fun autoUsesDefaultWithoutData() {
        assertEquals(TypingHeatmap.DEFAULT_STRENGTH, TypingHeatmap.resolveStrength("unknown layout", TypingHeatmap.AUTO_STRENGTH))
        assertEquals(5, TypingHeatmap.resolveStrength("unknown layout", 5))
    }

    @Test
    fun keysHitLessPreciselyGetALargerZone() {
        // six keys, "c" is hit with a wide spread, the others precisely
        val row = "abcdef"
        fun touches(spread: Float) = List(100) { i ->
            val sign = if (i % 2 == 0) 1f else -1f
            TypingHeatmap.Sample(sign * spread, sign * spread, 0)
        }
        val stats = row.associate { it.code to TypingHeatmap.keyStats(touches(if (it == 'c') 0.45f else 0.1f)) }
        val reference = TypingHeatmap.layoutSpread(stats.values)!!
        assertEquals(0.1f, reference[0], 0.001f)
        val wide = TypingHeatmap.offsetFromStats(stats['c'.code]!!, 0.33f, reference)!!
        val narrow = TypingHeatmap.offsetFromStats(stats['d'.code]!!, 0.33f, reference)!!
        assertEquals(5, wide.size)
        assertEquals(1.35f, wide[2], 0.001f) // limited
        assertEquals(1f, narrow[2], 0.001f)
        // without the reference only the offset
        assertEquals(2, TypingHeatmap.offsetFromStats(stats['c'.code]!!, 0.33f, null)!!.size)

        // keys 100 px wide: a touch 5 px inside "d" is meant for "c", which is hit less precisely
        val c = TypingHeatmap.Candidate('c'.code, 200f, 0f, 100f, 150f)
        val d = TypingHeatmap.Candidate('d'.code, 300f, 0f, 100f, 150f)
        val offsets = mapOf('c'.code to wide, 'd'.code to narrow)
        assertEquals('c'.code, TypingHeatmap.adaptedKeyCode(305f, 75f, listOf(c, d), offsets))
        // a touch well inside "d" stays there
        assertNull(TypingHeatmap.adaptedKeyCode(340f, 75f, listOf(c, d), offsets))
    }

    @Test
    fun spreadIsOnlyUsedIfItHelps() {
        // all keys hit alike: the spread can't reduce misses
        val row = "abcdef"
        val shapes = row.mapIndexed { i, ch -> TypingHeatmap.KeyShape(ch.code, ch.toString(), i * 0.1f, 0f, 0.1f, 0.25f) }
        val samples = row.associate { ch -> ch.code to List(100) { i -> TypingHeatmap.Sample(if (i % 2 == 0) 0.2f else -0.2f, 0f, ch.code) } }
        val evaluation = TypingHeatmap.evaluateStrengths(shapes, samples)!!
        for (strength in 1..5) assertEquals(false, evaluation.usesSpread(strength))
    }
}
