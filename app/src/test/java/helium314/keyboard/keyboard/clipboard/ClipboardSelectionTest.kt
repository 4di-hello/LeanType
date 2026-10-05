// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.clipboard

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.checkAndConvertCode
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.defaultClipboardToolbarPref
import helium314.keyboard.latin.utils.defaultToolbarPref
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardSelectionTest {

    @Test
    fun testClipboardSelectKeyMapping() {
        val code = getCodeForToolbarKey(ToolbarKey.CLIPBOARD_SELECT)
        assertEquals(KeyCode.CLIPBOARD_SELECT_ITEMS, code)
        assertEquals(KeyCode.CLIPBOARD_SELECT_ITEMS, KeyCode.CLIPBOARD_SELECT_ITEMS.checkAndConvertCode())
    }

    @Test
    fun testClipboardToolbarPrefContainsClipboardSelect() {
        assertTrue(
            defaultClipboardToolbarPref.contains("${ToolbarKey.CLIPBOARD_SELECT.name}:true"),
            "defaultClipboardToolbarPref should include CLIPBOARD_SELECT"
        )
    }

    @Test
    fun testMainToolbarPrefExcludesClipboardSelect() {
        assertFalse(
            defaultToolbarPref.contains(ToolbarKey.CLIPBOARD_SELECT.name),
            "Main defaultToolbarPref should not include CLIPBOARD_SELECT"
        )
    }
}
