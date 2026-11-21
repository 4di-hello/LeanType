// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class SilentKeyCorrectionTest {
    private fun model(): LetterModel {
        val builder = LetterModel.Builder(Locale.GERMAN)
        listOf("wetter" to 200, "wer" to 200, "wenn" to 200, "weg" to 180, "wir" to 150, "rot" to 150, "rat" to 120)
            .forEach { (word, frequency) -> builder.add(word, frequency) }
        return builder.build()
    }

    @Test fun `letter statistics prefer common letter sequences`() {
        val model = model()
        // "w" at the start of a word is followed by "e" much more often than by "r"
        assertTrue(model.logProb("^w", 'e'.code)!! > model.logProb("^w", 'r'.code)!!)
        // upper case letters count like lower case
        assertEquals(model.logProb("^w", 'e'.code), model.logProb("^w", 'E'.code))
        // only the last letter known (e.g. after deleting): bigram statistics
        assertTrue(model.logProb("e", 't'.code)!! > model.logProb("e", 'z'.code) ?: -100f)
        // letters the dictionary doesn't have
        assertNull(model.logProb("^w", 'ж'.code))
        assertNull(model.logProb("^ж", 'e'.code))
    }

    // two keys side by side, 100 px wide and 150 px high
    private val e = TypingHeatmap.Candidate('e'.code, 200f, 0f, 100f, 150f)
    private val r = TypingHeatmap.Candidate('r'.code, 300f, 0f, 100f, 150f)
    private fun prefersE(cp: Int): Float? = when (cp) { 'e'.code -> -0.5f; 'r'.code -> -3f; else -> null }

    @Test fun `touch close to the border goes to the better letter`() {
        // 5 px inside "r", but "e" fits much better
        assertEquals('e'.code, SilentKeyCorrection.choose(305f, 75f, listOf(e, r), emptyMap(), ::prefersE))
    }

    @Test fun `touch clearly inside a key is not changed`() {
        assertNull(SilentKeyCorrection.choose(340f, 75f, listOf(e, r), emptyMap(), ::prefersE))
        // the better letter is hit anyway
        assertNull(SilentKeyCorrection.choose(290f, 75f, listOf(e, r), emptyMap(), ::prefersE))
        // without letter statistics nothing changes
        assertNull(SilentKeyCorrection.choose(305f, 75f, listOf(e, r), emptyMap()) { null })
    }

    @Test fun `context follows typing and is dropped when unknown`() {
        val editorInfo = EditorInfo().apply { initialSelStart = 0; initialSelEnd = 0 }
        SilentKeyCorrection.onStartInput(editorInfo, 0)
        assertEquals("^", SilentKeyCorrection.contextAt(0))
        SilentKeyCorrection.onInput('W'.code, false, false, 0, 1)
        assertEquals("^w", SilentKeyCorrection.contextAt(1))
        SilentKeyCorrection.onInput('e'.code, false, false, 1, 2)
        assertEquals("we", SilentKeyCorrection.contextAt(2))
        // shift changes nothing
        SilentKeyCorrection.onInput(0, false, true, 2, 2)
        assertEquals("we", SilentKeyCorrection.contextAt(2))
        SilentKeyCorrection.onInput(0, true, true, 2, 1)
        assertEquals("w", SilentKeyCorrection.contextAt(1))
        // the cursor is somewhere else
        assertNull(SilentKeyCorrection.contextAt(5))
        SilentKeyCorrection.onInput(' '.code, false, false, 1, 2)
        assertEquals("^", SilentKeyCorrection.contextAt(2))
        // input at an unknown position: not known afterwards
        SilentKeyCorrection.onInput('a'.code, false, false, 7, 8)
        assertNull(SilentKeyCorrection.contextAt(8))
    }

    @Test fun `context from text before the cursor`() {
        assertEquals("^", SilentKeyCorrection.contextFromText(""))
        assertEquals("^h", SilentKeyCorrection.contextFromText("ein h"))
        assertEquals("lo", SilentKeyCorrection.contextFromText("hallo"))
        assertEquals("^", SilentKeyCorrection.contextFromText("www."))
    }
}
