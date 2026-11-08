// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.e2e

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.latin.LearnedTypos
import helium314.keyboard.latin.personalization.AutoCorrectTuner
import helium314.keyboard.latin.personalization.CorrectionStats
import helium314.keyboard.latin.personalization.TextLearner
import helium314.keyboard.latin.settings.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.Locale

/**
 * Typing with the real keyboard into a real text field (English, US, as on the emulator), with the
 * real dictionaries: auto-correction and its statistics, learned typos, learning from texts and the
 * switching by other apps.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TypingE2eTest {
    @Before
    fun setUp() {
        E2e.selectKeyboard()
        E2e.setPrefs {
            putBoolean(Settings.PREF_AUTO_CORRECTION, true)
            putBoolean(Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT, true)
            putBoolean(Settings.PREF_LEARN_TYPOS, true)
            putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false)
            putBoolean(Settings.PREF_AUTOMATION_BROADCASTS, false)
        }
        Settings.applyAutoCorrectAggressivenessPreset(E2e.prefs, Settings.AUTO_CORRECT_LEVEL_BALANCED)
        LearnedTypos.clear(E2e.prefs)
    }

    @After
    fun tearDown() {
        Settings.applyAutoCorrectAggressivenessPreset(E2e.prefs, Settings.AUTO_CORRECT_LEVEL_BALANCED)
    }

    private fun counts() = CorrectionStats.lastDays(E2e.context) ?: error("statistics not readable")

    @Test
    fun a_plainTypingReachesTheField() {
        TextField().use { field ->
            field.type("hello ")
            field.waitForText("hello ")
            E2e.screenshot("a_typing")
        }
    }

    @Test
    fun b_autocorrectionAndBackspaceAreCounted() {
        val before = counts()
        TextField().use { field ->
            field.type("teh")
            E2e.screenshot("b_strip_before_space")
            field.type(" ")
            field.waitForText("the ")
            field.type("\b")
            // backspace right after the correction reverts it
            E2e.waitFor({ "reverted, is \"${field.text}\"" }) { field.text.trimEnd() == "teh" }
            E2e.screenshot("b_reverted")
        }
        val after = counts()
        assertEquals("words", before[CorrectionStats.WORDS] + 1, after[CorrectionStats.WORDS])
        assertEquals("autocorrections", before[CorrectionStats.AUTOCORRECTED] + 1, after[CorrectionStats.AUTOCORRECTED])
        assertEquals("reverted", before[CorrectionStats.REVERTED] + 1, after[CorrectionStats.REVERTED])
    }

    @Test
    fun c_aTypoCorrectedTwiceByHandIsLearned() {
        val before = counts()
        // learning: auto-correction off, so the typo stays and is corrected by hand
        E2e.setPrefs { putBoolean(Settings.PREF_AUTO_CORRECTION, false) }
        TextField().use { field ->
            field.type("hte ")
            field.waitForText("hte ")
            field.type("\b\b\b\b")
            field.waitForText("")
            field.type("hate ")
            field.waitForText("hate ")
            field.type("hte ")
            field.waitForText("hate hte ")
            field.type("\b\b\b\b")
            field.waitForText("hate ")
            field.type("hate ")
            field.waitForText("hate hate ")
        }
        val learned = LearnedTypos.getAll(E2e.prefs).firstOrNull { it.typo == "hte" }
        assertNotNull("learned typo, have ${LearnedTypos.getAll(E2e.prefs).map { "${it.typo}->${it.correction}" }}", learned)
        assertEquals("hate", learned!!.correction)
        assertEquals(LearnedTypos.TIMES_TO_LEARN, learned.count)
        assertEquals("missed typos", before[CorrectionStats.MISSED] + 2, counts()[CorrectionStats.MISSED])

        // using it: with auto-correction "hte" becomes "hate" (the dictionary alone would choose "the")
        E2e.setPrefs { putBoolean(Settings.PREF_AUTO_CORRECTION, true) }
        TextField().use { field ->
            field.type("hte ")
            field.waitForText("hate ")
            E2e.screenshot("c_learned_typo_used")
            // backspace right after it: reverted and forgotten
            field.type("\b")
            E2e.waitFor({ "reverted, is \"${field.text}\"" }) { field.text.trimEnd() == "hte" }
        }
        assertNull(LearnedTypos.correctionFor(E2e.prefs, "hte", Locale.US))
    }

    @Test
    fun d_automaticStrengthGetsCarefulWhenCorrectionsAreTakenBack() {
        E2e.setPrefs {
            putInt(Settings.PREF_AUTO_CORRECT_AGGRESSIVENESS, CorrectionStats.AUTO_LEVEL)
            putFloat(Settings.PREF_AUTO_CORRECT_THRESHOLD, AutoCorrectTuner.STEPS[2])
        }
        CorrectionStats.resetWindow(E2e.context)
        repeat(50) { CorrectionStats.count(E2e.context, CorrectionStats.AUTOCORRECTED) }
        repeat(8) { CorrectionStats.count(E2e.context, CorrectionStats.REVERTED) }
        assertEquals(AutoCorrectTuner.STEPS[1], E2e.prefs.getFloat(Settings.PREF_AUTO_CORRECT_THRESHOLD, 0f))
        val change = CorrectionStats.lastChange(E2e.context)
        assertNotNull(change)
        assertEquals(2, change!![1])
        assertEquals(1, change[2])
        assertEquals(AutoCorrectTuner.REASON_TAKEN_BACK, change[3])
        // the test counts are not real typing
        CorrectionStats.reset(E2e.context)
    }

    @Test
    fun e_otherAppsCanSwitchTheKeyboardOnlyWhenAllowed() {
        TextField().use { field ->
            assertEquals(KeyboardId.ELEMENT_ALPHABET, field.elementId)
            E2e.device.executeShellCommand("am broadcast -a helium314.keyboard.ACTION_SWITCH_MODE --es mode symbols")
            SystemClock.sleep(1500)
            assertEquals("switched although not allowed", KeyboardId.ELEMENT_ALPHABET, field.elementId)

            E2e.setPrefs { putBoolean(Settings.PREF_AUTOMATION_BROADCASTS, true) }
            E2e.device.executeShellCommand("am broadcast -a helium314.keyboard.ACTION_SWITCH_MODE --es mode symbols")
            E2e.waitFor({ "symbols shown, element ${field.elementId}" }) { field.elementId == KeyboardId.ELEMENT_SYMBOLS }
            E2e.screenshot("e_switched_to_symbols")
        }
    }

    @Test
    fun f_wordsFromOwnTextsAreSuggested() {
        val text = "Zorbulon visited us yesterday. Zorbulon was happy and Zorbulon stayed."
        assertTrue(TextLearner.queue(E2e.context, text) > 0)
        TextField().use { field ->
            // learning starts a moment after the keyboard opened
            SystemClock.sleep(4000)
            field.type("zorbul")
            E2e.waitFor({ "suggestion Zorbulon, have ${field.suggestions}" }) {
                field.suggestions.any { it.equals("zorbulon", ignoreCase = true) }
            }
            E2e.screenshot("f_learned_word_suggested")
        }
    }
}
