// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLearnerTest {

    @Test
    fun sentencesAndBreaks() {
        val segments = TextLearner.segments("Hallo Anna, wie geht's? Wir treffen uns um 18 Uhr im Büro.\nViele Grüße")
        assertEquals(listOf("Hallo", "Anna"), segments[0].words)
        assertTrue(segments[0].startsSentence)
        assertEquals(listOf("wie", "geht's"), segments[1].words)
        assertFalse(segments[1].startsSentence)
        assertEquals(listOf("Wir", "treffen", "uns", "um"), segments[2].words)
        assertTrue(segments[2].startsSentence)
        // the number breaks the word sequence
        assertEquals(listOf("Uhr", "im", "Büro"), segments[3].words)
        assertEquals(listOf("Viele", "Grüße"), segments.last().words)
    }

    @Test
    fun hyphenatedWordsStayTogether() {
        assertEquals(listOf("das", "Orga-Team", "kommt"), TextLearner.segments("das Orga-Team kommt").single().words)
    }

    @Test
    fun singleUnknownWordsAreNotLearned() {
        val segments = TextLearner.segments("Das Hasu ist groß. Die Firma Kolibrix hat Pläne. Kolibrix wächst.")
        val known = setOf("Das", "ist", "groß", "Die", "Firma", "hat", "Pläne", "wächst")
        val learnable = TextLearner.learnableWords(segments) { it in known }
        assertTrue("Kolibrix" in learnable) // unknown, but twice
        assertFalse("Hasu" in learnable) // a single typo
        assertTrue("groß" in learnable)
    }
}
