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
    fun resultBetweenTextIsSeparatedOnBothSides() {
        assertEquals("\n\nAntwort\n\n", CustomAIKeyText.resultToInsert("Antwort", "erster Absatz", "zweiter Absatz"))
    }

    @Test
    fun correctionsAreApplied() {
        val text = "ich habe ein grose Haus und ein grose Garten"
        val output = "- ich → Ich\n\"grose Haus\" → \"großes Haus\"\n2. ein grose Garten -> einen großen Garten.\nGarage → Garagen"
        val (result, count) = CustomAIKeyText.applyCorrections(text, output)
        assertEquals("Ich habe ein großes Haus und einen großen Garten.", result)
        assertEquals(3, count)
    }

    @Test
    fun noMistakesChangeNothing() {
        assertEquals("Alles gut." to 0, CustomAIKeyText.applyCorrections("Alles gut.", "OK"))
        assertEquals("Alles gut." to 0, CustomAIKeyText.applyCorrections("Alles gut.", "gut → gut"))
    }

    @Test
    fun sameMistakeTwiceIsFixedInOrder() {
        val (result, count) = CustomAIKeyText.applyCorrections("das das ist das", "das → Das\ndas → dass")
        assertEquals("Das dass ist das", result)
        assertEquals(2, count)
    }

    @Test
    fun correctionsPreferWholeWords() {
        assertEquals("mich freut, dass Ich komme" to 1, CustomAIKeyText.applyCorrections("mich freut, dass ich komme", "ich → Ich"))
    }
}
