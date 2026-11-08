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
import org.junit.Assert.assertFalse
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
            // an emulator may report a hardware keyboard: the whole keyboard is needed here
            putBoolean(Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD, false)
            // LeanType capitalizes sentences also in fields that don't ask for it: lower case keeps the tests simple
            putBoolean(Settings.PREF_AUTO_CAP, false)
        }
        Settings.applyAutoCorrectAggressivenessPreset(E2e.prefs, Settings.AUTO_CORRECT_LEVEL_BALANCED)
        LearnedTypos.clear(E2e.prefs)
    }

    @After
    fun tearDown() {
        Settings.applyAutoCorrectAggressivenessPreset(E2e.prefs, Settings.AUTO_CORRECT_LEVEL_BALANCED)
        E2e.setPrefs { putBoolean(Settings.PREF_AUTO_CAP, true) }
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
        // first a few typos, to see what the dictionary corrects (written to the log)
        val tries = listOf("teh" to "the", "tgis" to "this", "helli" to "hello", "wprld" to "world", "becuase" to "because")
        val corrected = mutableListOf<String>()
        TextField().use { field ->
            for ((typo, word) in tries) {
                val before = field.text
                field.type(typo)
                SystemClock.sleep(500)
                val state = "typo \"$typo\" known=${field.isKnownWord(typo)} strip=${field.suggestions} autocorrect=${field.willAutoCorrect}"
                if (typo == "tgis") E2e.screenshot("b_strip_tgis")
                field.type(" ")
                SystemClock.sleep(500)
                val result = field.text.removePrefix(before).trim()
                E2e.log("$state -> \"$result\"")
                if (result == word) corrected += typo
            }
        }
        E2e.log("corrected: $corrected of ${tries.map { it.first }}")
        assertTrue("no typo was auto-corrected", corrected.isNotEmpty())

        // then backspace right after a correction, with the statistics
        val typo = corrected.first()
        val word = tries.first { it.first == typo }.second
        val before = counts()
        TextField().use { field ->
            field.type("$typo ")
            field.waitForText("$word ")
            field.type("\b")
            E2e.waitFor({ "reverted, is \"${field.text}\"" }) { field.text.trimEnd() == typo }
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

    /**
     * The check that keeps AI results out of other fields: the text of the field is taken before the
     * selection is changed for the request, and compared when the result arrives. Here without an AI
     * plugin, through the same functions on a real text field.
     */
    @Test
    fun g_aiResultFieldCheckOnARealTextField() {
        TextField().use { field ->
            field.type("hello world")
            field.waitForText("hello world")
            val inputLogic = E2e.onMain { helium314.keyboard.latin.LatinIME.getInstance()!!.mInputLogic }
            val connection = inputLogic.javaClass.getDeclaredField("mConnection").apply { isAccessible = true }
                .get(inputLogic) as helium314.keyboard.latin.RichInputConnection
            val currentTarget = inputLogic.javaClass.getDeclaredMethod("currentAiTarget").apply { isAccessible = true }
            fun isCurrent(target: Any): Boolean = E2e.onMain {
                target.javaClass.getDeclaredMethod("isCurrent").apply { isAccessible = true }.invoke(target) as Boolean
            }

            // like proofreading without a selection: everything is selected after the text was taken
            val target = E2e.onMain { currentTarget.invoke(inputLogic)!! }
            E2e.onMain { connection.selectAll() }
            SystemClock.sleep(1000)
            E2e.screenshot("g_all_selected")
            assertTrue("same field and text, but not recognized", isCurrent(target))

            // the cursor moved somewhere else: still the same text
            E2e.onMain { connection.setSelection(3, 3) }
            SystemClock.sleep(500)
            assertTrue("cursor moved, text the same", isCurrent(target))

            // the text was changed meanwhile: the result must not replace it
            E2e.onMain { connection.setSelection(11, 11) }
            SystemClock.sleep(300)
            field.type("x")
            E2e.waitFor({ "x typed, is \"${field.text}\"" }) { field.text.endsWith("x") }
            assertFalse("text changed, but still recognized as the same", isCurrent(target))
        }
    }
}
