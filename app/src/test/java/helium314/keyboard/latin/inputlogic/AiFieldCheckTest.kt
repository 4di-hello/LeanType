// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFieldCheckTest {
    private val pkg = "com.example.chat"
    private val target = AiFieldCheck.fingerprint(pkg, 1, false, "Hallo Welt")!!

    @Test
    fun sameFieldAndTextMatches() {
        assertTrue(target.matches(pkg, 1, false, "Hallo Welt") { null })
    }

    @Test
    fun otherChatOrChangedTextDoesNotMatch() {
        // same app and field id, but another chat with other text
        assertFalse(target.matches(pkg, 1, false, "Bis morgen") { "Bis morgen" })
        assertFalse(target.matches(pkg, 1, false, "Hallo Welt!") { null })
        assertFalse(target.matches("com.example.mail", 1, false, "Hallo Welt") { null })
        assertFalse(target.matches(pkg, 2, false, "Hallo Welt") { null })
        assertFalse(target.matches(pkg, 1, true, "Hallo Welt") { null })
    }

    @Test
    fun wholeTextIsOnlyReadWhenNeeded() {
        var reads = 0
        assertTrue(target.matches(pkg, 1, false, "Hallo Welt") { reads++; "Hallo Welt" })
        assertEquals(0, reads)
        // the app reports the text around the cursor unreliably, the whole text is the same
        assertTrue(target.matches(pkg, 1, false, "Hallo ") { reads++; "Hallo Welt" })
        assertEquals(1, reads)
    }

    @Test
    fun passwordFieldsAreNeverRemembered() {
        assertNull(AiFieldCheck.fingerprint(pkg, 1, true, "secret"))
    }
}
