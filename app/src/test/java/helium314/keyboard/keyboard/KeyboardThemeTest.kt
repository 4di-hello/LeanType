// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard

import android.content.Context
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class KeyboardThemeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun builtInThemesHaveReadableText() {
        KeyboardTheme.simpleThemes.forEach { (name, theme) ->
            val onKey = ColorUtils.calculateContrast(theme.text, theme.key)
            val onFunctional = ColorUtils.calculateContrast(theme.text, theme.functionalKey)
            val hint = ColorUtils.calculateContrast(theme.hint, theme.key)
            assertTrue(onKey >= 7.0, "$name: text on keys has contrast $onKey")
            assertTrue(onFunctional >= 4.5, "$name: text on functional keys has contrast $onFunctional")
            assertTrue(hint >= 3.0, "$name: hint text has contrast $hint")
        }
    }

    @Test fun themeListHasNoDuplicatesAndEveryThemeHasAName() {
        for (night in listOf(false, true)) {
            val themes = KeyboardTheme.getAvailableDefaultColors(context.prefs(), night)
            assertTrue(themes.size == themes.toSet().size, "duplicate theme in $themes")
            themes.forEach {
                val id = context.resources.getIdentifier("theme_name_$it", "string", context.packageName)
                assertTrue(id != 0, "no name for theme $it")
            }
        }
    }

    @Test fun everyBuiltInThemeIsSelectable() {
        val all = KeyboardTheme.getAvailableDefaultColors(context.prefs(), false) +
            KeyboardTheme.getAvailableDefaultColors(context.prefs(), true)
        KeyboardTheme.simpleThemes.keys.forEach { assertTrue(it in all, "$it is not in the theme list") }
    }
}
