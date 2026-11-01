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
}
