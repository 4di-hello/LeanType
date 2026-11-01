// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.personalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorrectionStatsTest {
    private fun entry(start: Int, word: String, typed: String = word, autocorrected: Boolean = false, valid: Boolean = true, time: Long = 1000L) =
        CorrectionTracker.Entry(start, word, typed, autocorrected, valid, time)

    @Test
    fun autocorrectionChangedBackIsUndone() {
        val tracker = CorrectionTracker()
        tracker.onCommitted(entry(0, "Haus", typed = "Hais", autocorrected = true))
        tracker.onCommitted(entry(5, "ist"))
        // the user goes back and types what was meant
        assertEquals(CorrectionTracker.Change.AUTOCORRECT_UNDONE, tracker.onCommitted(entry(0, "Hais", time = 2000L)))
    }

    @Test
    fun unknownWordFixedByHandIsMissedTypo() {
        val tracker = CorrectionTracker()
        tracker.onCommitted(entry(10, "Bescheud", valid = false))
        assertEquals(CorrectionTracker.Change.MISSED_TYPO, tracker.onCommitted(entry(10, "Bescheid", time = 2000L)))
    }

    @Test
    fun otherChangesAreNotCounted() {
        val tracker = CorrectionTracker()
        // a known word replaced by another one: a change of mind, not a typo
        tracker.onCommitted(entry(0, "heute"))
        assertEquals(CorrectionTracker.Change.NONE, tracker.onCommitted(entry(0, "morgen", time = 2000L)))
        // the same word again, e.g. the cursor was only moved into it
        tracker.onCommitted(entry(20, "Tasse", valid = false))
        assertEquals(CorrectionTracker.Change.NONE, tracker.onCommitted(entry(20, "tasse", time = 2000L)))
        // too old
        tracker.onCommitted(entry(40, "Bescheud", valid = false, time = 3000L))
        assertEquals(CorrectionTracker.Change.NONE,
            tracker.onCommitted(entry(40, "Bescheid", time = 3000L + CorrectionTracker.MAX_AGE_MS + 1)))
    }

    @Test
    fun revertedWordIsNotCountedAgain() {
        val tracker = CorrectionTracker()
        tracker.onCommitted(entry(0, "Haus", typed = "Hais", autocorrected = true))
        tracker.removeLast() // reverted with backspace, counted there
        assertEquals(CorrectionTracker.Change.NONE, tracker.onCommitted(entry(0, "Hais", time = 2000L)))
    }

    @Test
    fun distanceIgnoresCase() {
        assertEquals(0, CorrectionTracker.distance("Haus", "haus"))
        assertEquals(1, CorrectionTracker.distance("Bescheud", "Bescheid"))
        assertEquals(2, CorrectionTracker.distance("ab", "ba"))
    }

    private fun counts(words: Int = 0, autocorrected: Int = 0, reverted: Int = 0, missed: Int = 0) = IntArray(CorrectionStats.SIZE).also {
        it[CorrectionStats.WORDS] = words
        it[CorrectionStats.AUTOCORRECTED] = autocorrected
        it[CorrectionStats.REVERTED] = reverted
        it[CorrectionStats.MISSED] = missed
    }

    @Test
    fun tunerGetsMoreCarefulWhenCorrectionsAreTakenBack() {
        assertEquals(1 to AutoCorrectTuner.REASON_TAKEN_BACK, AutoCorrectTuner.decide(counts(500, 50, 8), 2))
        // already the most careful step
        assertNull(AutoCorrectTuner.decide(counts(500, 50, 8), 0))
        // not enough data
        assertNull(AutoCorrectTuner.decide(counts(100, 20, 6), 2))
    }

    @Test
    fun tunerGetsBolderWhenTyposAreMissed() {
        assertEquals(3 to AutoCorrectTuner.REASON_MISSED, AutoCorrectTuner.decide(counts(500, 30, 1, 10), 2))
        // but not if corrections are taken back as well
        assertNull(AutoCorrectTuner.decide(counts(500, 30, 3, 10), 2))
        // few missed typos
        assertNull(AutoCorrectTuner.decide(counts(1000, 30, 0, 9), 2))
    }

    @Test
    fun stepsMatchThePresets() {
        assertEquals(0, AutoCorrectTuner.stepFor(0.185f))
        assertEquals(2, AutoCorrectTuner.stepFor(0.067f))
        assertEquals(3, AutoCorrectTuner.stepFor(-1f))
    }
}
