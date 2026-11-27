// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomAIKeyTextTest {

    @Test
    fun instructionsGoBeforeThePrompt() {
        val prompt = "Nachricht: {text}\nAntwort:"
        assertEquals("Output ONLY the result.\n\n$prompt", CustomAIKeyText.withInstructions(prompt, " Output ONLY the result."))
    }

    @Test
    fun noInstructionsKeepThePrompt() {
        assertEquals("Fasse zusammen.", CustomAIKeyText.withInstructions("Fasse zusammen.", ""))
    }

    @Test
    fun clipboardIsInserted() {
        val prompt = "Nachricht: {clipboard}\nAntwort:"
        assertTrue(CustomAIKeyText.usesClipboard(prompt))
        assertFalse(CustomAIKeyText.usesClipboard("Nachricht: {text}"))
        assertEquals("Nachricht: Hast du Zeit?\nAntwort:", CustomAIKeyText.insertClipboard(prompt, "  Hast du Zeit?\n"))
    }

    @Test
    fun placeholdersIgnoreCase() {
        assertEquals("Nachricht: {text}\nKopie: {clipboard}", CustomAIKeyText.normalizePlaceholders("Nachricht: {Text}\nKopie: { CLIPBOARD }"))
        assertEquals("{texte}", CustomAIKeyText.normalizePlaceholders("{texte}"))
    }

    @Test
    fun appendedResultStartsANewParagraph() {
        assertEquals("\n\nAntwort", CustomAIKeyText.resultToInsert("Antwort", "Gruß, Sandra", ""))
        assertEquals("\nAntwort", CustomAIKeyText.resultToInsert("Antwort", "Gruß, Sandra\n", ""))
        assertEquals("Antwort", CustomAIKeyText.resultToInsert("Antwort", "Gruß, Sandra\n\n", ""))
    }

    @Test
    fun prependedResultIsFollowedByANewParagraph() {
        assertEquals("Antwort\n\n", CustomAIKeyText.resultToInsert("Antwort", "", "Hallo Max"))
        assertEquals("Antwort\n", CustomAIKeyText.resultToInsert("Antwort", "", "\nHallo Max"))
    }

    @Test
    fun resultInEmptyFieldHasNoBreaks() {
        assertEquals("Antwort", CustomAIKeyText.resultToInsert("  Antwort\n", "", ""))
    }

    @Test
    fun wholeTextWithAppendedResult() {
        assertEquals("Hallo Max\n\nAntwort" to 18, CustomAIKeyText.textWithResult("Hallo Max", 9, "Antwort"))
        // after a selection in the middle: the text after it stays
        assertEquals("eins\n\nAntwort\n\nzwei" to 13, CustomAIKeyText.textWithResult("eins\n\nzwei", 4, "Antwort"))
    }

    @Test
    fun wholeTextWithPrependedResult() {
        assertEquals("Antwort\n\nHallo Max" to 9, CustomAIKeyText.textWithResult("Hallo Max", 0, "Antwort"))
    }

    @Test
    fun resultBetweenTextIsSeparatedOnBothSides() {
        assertEquals("\n\nAntwort\n\n", CustomAIKeyText.resultToInsert("Antwort", "erster Absatz", "zweiter Absatz"))
    }
}
