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

    private fun encryptSegmented(data: ByteArray, password: String, segmentSize: Int): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(out, password.toCharArray(), 100_000, segmentSize).use { stream ->
            // in pieces of different sizes, like a zip stream writes
            var offset = 0
            var piece = 1
            while (offset < data.size) {
                val n = minOf(piece, data.size - offset)
                stream.write(data, offset, n)
                offset += n
                piece = piece * 3 % 1700 + 1
            }
        }
        return out.toByteArray()
    }

    private fun decrypt(encrypted: ByteArray, password: String): ByteArray {
        val target = File.createTempFile("backup", ".zip")
        try {
            BackupCrypto.decryptToFile(ByteArrayInputStream(encrypted), password.toCharArray(), target)
            return target.readBytes()
        } finally {
            target.delete()
        }
    }

    @Test fun segmentsOfAnySizeAreDecrypted() {
        for (size in listOf(0, 1, 999, 1000, 1001, 2500, 3000)) {
            val data = Random(size).nextBytes(size)
            assertContentEquals(data, decrypt(encryptSegmented(data, "pw", 1000), "pw"), "size $size")
        }
    }

    @Test fun missingOrMovedSegmentsAreNoticed() {
        val encrypted = encryptSegmented(Random(2).nextBytes(2500), "pw", 1000)
        val header = 8 + 4 + 16 + 8
        val fullSegment = 4 + 1000 + 16
        // the last segment cut off: the one before is not marked as last
        val cut = encrypted.copyOf(header + 2 * fullSegment)
        assertFailsWith<BackupCrypto.WrongPasswordException> { decrypt(cut, "pw") }
        // two segments swapped: wrong segment numbers
        val swapped = encrypted.copyOf()
        System.arraycopy(encrypted, header + fullSegment, swapped, header, fullSegment)
        System.arraycopy(encrypted, header, swapped, header + fullSegment, fullSegment)
        assertFailsWith<BackupCrypto.WrongPasswordException> { decrypt(swapped, "pw") }
        // something added at the end
        assertFailsWith<BackupCrypto.WrongPasswordException> { decrypt(encrypted + byteArrayOf(0, 0, 0, 20) + ByteArray(20), "pw") }
    }

    @Test fun backupsOfOlderVersionsCanStillBeRead() {
        val data = Random(3).nextBytes(10_000)
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStreamFormat1(out, "alt".toCharArray()).use { it.write(data) }
        assertTrue(BackupCrypto.isEncrypted(out.toByteArray()))
        assertContentEquals(data, decrypt(out.toByteArray(), "alt"))
    }
}

