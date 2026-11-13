// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PluginTrustTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun packagesThatAreNotInstalledAreNotTrustedButNotRefusedEither() {
        // Android cannot bind to them, so there is nothing to refuse
        assertFalse(PluginTrust.isTrustedPackage(context, "not.installed.plugin"))
        assertFalse(PluginTrust.isInstalledButUntrusted(context, "not.installed.plugin"))
    }

    @Test
    fun ownPackageIsNeverRefused() {
        assertFalse(PluginTrust.isInstalledButUntrusted(context, context.packageName))
    }
}
