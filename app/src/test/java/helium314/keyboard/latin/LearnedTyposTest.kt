// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LearnedTyposTest {
    private val prefs get() = RuntimeEnvironment.getApplication().getSharedPreferences("learned_typos_test", Context.MODE_PRIVATE)
    private val de = Locale.GERMAN

    @Before
    fun clear() = LearnedTypos.clear(prefs)

    @Test
    fun learnedAfterTheSecondCorrection() {
        LearnedTypos.onTypoCorrected(prefs, "dsa", "das")
        assertNull(LearnedTypos.correctionFor(prefs, "dsa", de))
        LearnedTypos.onTypoCorrected(prefs, "dsa", "das")
        assertEquals("das", LearnedTypos.correctionFor(prefs, "dsa", de))
    }

    @Test
    fun anotherCorrectionStartsAgain() {
        LearnedTypos.onTypoCorrected(prefs, "Bescheud", "Bescheid")
        LearnedTypos.onTypoCorrected(prefs, "Bescheud", "Bescheide")
        assertNull(LearnedTypos.correctionFor(prefs, "Bescheud", de))
    }

    @Test
    fun caseOfTheTypedWordIsKept() {
        repeat(LearnedTypos.TIMES_TO_LEARN) { LearnedTypos.onTypoCorrected(prefs, "ihc", "ich") }
        assertEquals("Ich", LearnedTypos.correctionFor(prefs, "Ihc", de))
        assertEquals("ICH", LearnedTypos.correctionFor(prefs, "IHC", de))
    }

    @Test
    fun forgottenTyposAreNotCorrected() {
        repeat(LearnedTypos.TIMES_TO_LEARN) { LearnedTypos.onTypoCorrected(prefs, "nud", "und") }
        LearnedTypos.forget(prefs, "nud")
        assertNull(LearnedTypos.correctionFor(prefs, "nud", de))
        assertTrue(LearnedTypos.getAll(prefs).isEmpty())
    }

    @Test
    fun onlyRealWordsAreLearned() {
        assertFalse(LearnedTypos.isLearnable("das", "Das")) // only the case differs
        assertFalse(LearnedTypos.isLearnable("a", "an")) // too short
        assertFalse(LearnedTypos.isLearnable("h4llo", "hallo")) // not a word
        assertTrue(LearnedTypos.isLearnable("Bescheud", "Bescheid"))
    }
}
