// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupCryptoTest {
    private fun encrypt(data: ByteArray, password: String): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(out, password.toCharArray()).use { it.write(data) }
        return out.toByteArray()
    }

    @Test fun encryptedBackupCanBeDecryptedWithThePassword() {
        val data = Random(1).nextBytes(50_000)
        val encrypted = encrypt(data, "geheim 123")
        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertFalse(encrypted.toList().windowed(16).any { it == data.take(16) }, "data is visible in the file")
        val target = File.createTempFile("backup", ".zip")
        try {
            BackupCrypto.decryptToFile(ByteArrayInputStream(encrypted), "geheim 123".toCharArray(), target)
            assertContentEquals(data, target.readBytes())
        } finally {
            target.delete()
        }
    }

    @Test fun wrongPasswordOrDamagedFileIsRejectedAndNothingIsLeft() {
        val encrypted = encrypt("settings".toByteArray(), "richtig")
        val target = File.createTempFile("backup", ".zip")
        assertFailsWith<BackupCrypto.WrongPasswordException> {
            BackupCrypto.decryptToFile(ByteArrayInputStream(encrypted), "falsch".toCharArray(), target)
        }
        assertFalse(target.exists())
        val damaged = encrypted.copyOf().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }
        assertFailsWith<BackupCrypto.WrongPasswordException> {
            BackupCrypto.decryptToFile(ByteArrayInputStream(damaged), "richtig".toCharArray(), target)
        }
        assertFalse(target.exists())
    }

    @Test fun normalZipIsNotTakenForEncrypted() {
        assertFalse(BackupCrypto.isEncrypted(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0)))
        assertFalse(BackupCrypto.isEncrypted(byteArrayOf()))
    }
}
