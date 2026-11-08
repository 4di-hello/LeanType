// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.e2e

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import helium314.keyboard.latin.backup.BackupCrypto
import helium314.keyboard.latin.utils.PluginTrust
import helium314.keyboard.settings.SettingsActivity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import kotlin.random.Random

/** Parts that need Android itself: its crypto provider, its package manager, and the settings screens. */
@RunWith(AndroidJUnit4::class)
class DeviceE2eTest {
    private val cache get() = E2e.context.cacheDir

    /** Plaintext of [size] pseudo-random bytes written to [write], returns its SHA-256 */
    private fun writeData(size: Int, seed: Int, write: (ByteArray, Int) -> Unit): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val random = Random(seed)
        val chunk = ByteArray(64 * 1024)
        var left = size
        while (left > 0) {
            val n = minOf(left, chunk.size)
            random.nextBytes(chunk, 0, n)
            write(chunk, n)
            digest.update(chunk, 0, n)
            left -= n
        }
        return digest.digest()
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest()
    }

    @Test
    fun largeBackupIsEncryptedAndDecryptedWithTheAndroidCryptoProvider() {
        E2e.log("GCM provider: ${Cipher.getInstance("AES/GCM/NoPadding").provider.name}")
        val encrypted = File(cache, "e2e-backup.ltbak")
        val decrypted = File(cache, "e2e-backup.zip")
        val size = 40 * 1024 * 1024
        val runtime = Runtime.getRuntime()
        runtime.gc()
        val usedBefore = runtime.totalMemory() - runtime.freeMemory()
        var start = SystemClock.elapsedRealtime()
        val hash = encrypted.outputStream().use { out ->
            BackupCrypto.encryptingStream(out, "e2e password".toCharArray()).use { stream ->
                writeData(size, 1) { bytes, n -> stream.write(bytes, 0, n) }
            }
        }
        val encryptMs = SystemClock.elapsedRealtime() - start
        val usedAfter = runtime.totalMemory() - runtime.freeMemory()
        start = SystemClock.elapsedRealtime()
        encrypted.inputStream().use { BackupCrypto.decryptToFile(it, "e2e password".toCharArray(), decrypted) }
        val decryptMs = SystemClock.elapsedRealtime() - start
        E2e.log("40 MB backup: encrypted in $encryptMs ms, decrypted in $decryptMs ms, " +
                "heap ${usedBefore / 1048576} -> ${usedAfter / 1048576} MB, max ${runtime.maxMemory() / 1048576} MB")
        assertArrayEquals(hash, sha256(decrypted))

        // wrong password, and a file cut off at the end
        try {
            encrypted.inputStream().use { BackupCrypto.decryptToFile(it, "wrong".toCharArray(), decrypted) }
            fail("wrong password accepted")
        } catch (e: BackupCrypto.WrongPasswordException) { }
        assertFalse("nothing is left of a failed decryption", decrypted.exists())
        val cut = File(cache, "e2e-cut.ltbak")
        encrypted.inputStream().use { input -> cut.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            var left = encrypted.length() - 100
            while (left > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buffer, 0, n)
                left -= n
            }
        } }
        try {
            cut.inputStream().use { BackupCrypto.decryptToFile(it, "e2e password".toCharArray(), decrypted) }
            fail("cut off backup accepted")
        } catch (e: BackupCrypto.WrongPasswordException) { }
        listOf(encrypted, decrypted, cut).forEach { it.delete() }
    }

    @Test
    fun backupOfAnOlderVersionCanStillBeRead() {
        val encrypted = File(cache, "e2e-old.ltbak")
        val decrypted = File(cache, "e2e-old.zip")
        val hash = encrypted.outputStream().use { out ->
            BackupCrypto.encryptingStreamFormat1(out, "old".toCharArray()).use { stream ->
                writeData(2 * 1024 * 1024, 2) { bytes, n -> stream.write(bytes, 0, n) }
            }
        }
        encrypted.inputStream().use { BackupCrypto.decryptToFile(it, "old".toCharArray(), decrypted) }
        assertArrayEquals(hash, sha256(decrypted))
        encrypted.delete()
        decrypted.delete()
    }

    @Test
    fun onlyOfficialPluginsAreTrusted() {
        // this test build is signed with a debug key, like a plugin from somewhere else would be
        val ownApk = File(E2e.context.applicationInfo.sourceDir)
        val reason = PluginTrust.untrustedReason(E2e.context, ownApk)
        E2e.log("own APK as plugin: $reason")
        assertNotNull(reason)
        assertFalse(PluginTrust.isTrustedPackage(E2e.context, E2e.context.packageName))
        assertFalse(PluginTrust.isTrustedPackage(E2e.context, "com.android.settings"))
        assertFalse(PluginTrust.isTrustedPackage(E2e.context, "not.installed.package"))
    }

    private fun find(selector: BySelector, timeoutMs: Long = 5000): UiObject2 =
        E2e.device.wait(Until.findObject(selector), timeoutMs) ?: run {
            E2e.screenshot("missing-" + selector.toString().takeLast(40).replace(Regex("[^A-Za-z0-9]+"), "_"))
            throw AssertionError("not found: $selector")
        }

    /** Scrolls the list down until [text] is shown (also within a row that has more text, like a switch) */
    private fun scrollTo(text: String): UiObject2 {
        E2e.device.wait(Until.findObject(By.textContains(text)), 2000)?.let { return it }
        val list = E2e.device.findObject(By.scrollable(true)) ?: run {
            E2e.screenshot("missing-" + text.replace(Regex("[^A-Za-z0-9]+"), "_"))
            throw AssertionError("not found and nothing to scroll: $text")
        }
        repeat(15) {
            // the list on screen may be a new object after scrolling (Compose)
            (E2e.device.findObject(By.scrollable(true)) ?: list).scroll(Direction.DOWN, 0.6f)
            E2e.device.waitForIdle()
            // the emulator draws slowly at times (frames of up to 4 s), and the accessibility tree comes later
            E2e.device.wait(Until.findObject(By.textContains(text)), 1500)?.let { return it }
        }
        E2e.device.wait(Until.findObject(By.textContains(text)), 10_000)?.let { return it }
        E2e.screenshot("missing-" + text.replace(Regex("[^A-Za-z0-9]+"), "_"))
        File(E2e.context.filesDir, "e2e/missing-" + text.replace(Regex("[^A-Za-z0-9]+"), "_") + ".xml")
            .outputStream().use { E2e.device.dumpWindowHierarchy(it) }
        throw AssertionError("not found when scrolling: $text")
    }

    private fun open(text: String) {
        scrollTo(text).click()
        E2e.device.waitForIdle()
        SystemClock.sleep(600)
    }

    private fun back() {
        E2e.device.pressBack()
        E2e.device.waitForIdle()
        SystemClock.sleep(500)
    }

    @Test
    fun newSettingsScreensOpen() {
        E2e.selectKeyboard()
        // the settings for learned typos are only shown with auto-correction
        E2e.setPrefs {
            putBoolean(helium314.keyboard.latin.settings.Settings.PREF_AUTO_CORRECTION, true)
            // LeanType asks for sponsoring the second time the main page is shown, that dialog would cover the list
            putBoolean(helium314.keyboard.latin.settings.Settings.PREF_DONT_SHOW_SPONSOR_DIALOG, true)
        }
        val intent = Intent(E2e.context, SettingsActivity::class.java)
            .putExtra("from_ime", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        E2e.context.startActivity(intent)
        find(By.text("Plugins"), 10_000)
        E2e.screenshot("s1_main")

        open("Text correction")
        scrollTo("Correction statistics")
        E2e.screenshot("s2_text_correction")
        open("Correction statistics")
        find(By.text("Reset"))
        E2e.screenshot("s3_statistics_dialog")
        find(By.text("Close")).click()
        SystemClock.sleep(400)

        open("Learned typos")
        E2e.screenshot("s4_learned_typos")
        find(By.text("Close")).click()
        SystemClock.sleep(400)

        // the section header and the item below it have the same text: the item is the lower one
        scrollTo("Typing heatmap")
        val heatmapItem = E2e.device.findObjects(By.text("Typing heatmap")).maxByOrNull { it.visibleBounds.top }!!
        heatmapItem.click()
        // wait for something only the heatmap screen shows
        find(By.textContains("type a few sentences"), 10_000)
        E2e.screenshot("s5_typing_heatmap")
        back()
        back()

        open("Advanced")
        scrollTo("Allow switching by other apps")
        E2e.screenshot("s6_advanced")
        open("Automatic backup")
        E2e.screenshot("s7_auto_backup")
        back()
        back()

        // the app is still there, nothing crashed
        assertTrue(E2e.device.hasObject(By.pkg(E2e.context.packageName)))
        assertEquals(0, crashReports())
    }

    /** LeanType keeps crash reports as files, there should be none */
    private fun crashReports(): Int =
        E2e.context.filesDir.listFiles()?.count { it.name.startsWith("crash_report") } ?: 0
}
