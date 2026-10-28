// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenProfileTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.prefs().edit().clear().apply()
        ScreenProfileProvider.invalidateCache()
    }

    @Test
    fun testScreenProfileDefaultIsCompact() {
        // By default (pref_foldable_mode = false), getScreenProfile must return COMPACT
        val config = Configuration().apply {
            screenWidthDp = 800
            smallestScreenWidthDp = 800
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.COMPACT, profile)
    }

    @Test
    fun testScreenProfileOptInLargeWhenFoldableModeEnabled() {
        // When pref_foldable_mode is true and width >= 600 dp, getScreenProfile returns LARGE
        context.prefs().edit().putBoolean(Settings.PREF_FOLDABLE_MODE, true).apply()
        ScreenProfileProvider.invalidateCache()

        val config = Configuration().apply {
            screenWidthDp = 800
            smallestScreenWidthDp = 800
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.LARGE, profile)
    }

    @Test
    fun testScreenProfileFoldedOuterScreenIsCompact() {
        // When pref_foldable_mode is true, but device is folded (screenWidthDp < 600, though smallestScreenWidthDp >= 600),
        // it must return COMPACT
        context.prefs().edit().putBoolean(Settings.PREF_FOLDABLE_MODE, true).apply()
        ScreenProfileProvider.invalidateCache()

        val config = Configuration().apply {
            screenWidthDp = 400
            smallestScreenWidthDp = 800
        }
        val profile = ScreenProfileProvider.getScreenProfile(context, config)
        assertEquals(ScreenProfile.COMPACT, profile)
    }

    @Test
    fun testUnfoldedHeightScaleDecoupledFromCompact() {
        val prefs = context.prefs()
        // Set compact (folded) portrait scale to 0.90f
        prefs.edit().putFloat("keyboard_height_scale_false", 0.90f).apply()
        // Set unfolded portrait scale to 0.60f
        prefs.edit().putFloat("keyboard_height_scale_large_false", 0.60f).apply()

        // COMPACT profile reads folded portrait scale
        val compactHeight = Settings.readHeightScale(prefs, landscape = false, profile = ScreenProfile.COMPACT)
        assertEquals(0.90f, compactHeight, 0.001f)

        // LARGE profile reads unfolded portrait scale
        val unfoldedHeight = Settings.readHeightScale(prefs, landscape = false, profile = ScreenProfile.LARGE)
        assertEquals(0.60f, unfoldedHeight, 0.001f)

        // Unfolded landscape defaults independently without inheriting compact portrait
        val unfoldedLandscape = Settings.readHeightScale(prefs, landscape = true, profile = ScreenProfile.LARGE)
        assertEquals(helium314.keyboard.latin.settings.Defaults.PREF_UNFOLDED_KEYBOARD_HEIGHT_SCALE[1], unfoldedLandscape, 0.001f)
    }

    @Test
    fun testUnfoldedSplitKeyboardPreference() {
        val prefs = context.prefs()
        prefs.edit().putBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false).apply()
        prefs.edit().putBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_UNFOLDED, true).apply()

        val compactSplit = Settings.readSplitKeyboardEnabled(prefs, isLandscape = false, profile = ScreenProfile.COMPACT)
        assertEquals(false, compactSplit)

        val unfoldedSplit = Settings.readSplitKeyboardEnabled(prefs, isLandscape = false, profile = ScreenProfile.LARGE)
        assertEquals(true, unfoldedSplit)
    }

    @Test
    fun testUnfoldedBottomPaddingScale() {
        val prefs = context.prefs()
        prefs.edit().putFloat("bottom_padding_scale_false", 1.2f).apply()
        prefs.edit().putFloat("bottom_padding_scale_large_false", 0.3f).apply()

        val compactPadding = Settings.readBottomPaddingScale(prefs, landscape = false, profile = ScreenProfile.COMPACT)
        assertEquals(1.2f, compactPadding, 0.001f)

        val unfoldedPadding = Settings.readBottomPaddingScale(prefs, landscape = false, profile = ScreenProfile.LARGE)
        assertEquals(0.3f, unfoldedPadding, 0.001f)
    }
}
