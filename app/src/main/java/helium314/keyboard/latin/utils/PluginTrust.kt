// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.security.MessageDigest

/**
 * Plugins run inside the keyboard and see everything that is typed. Only official LeanType plugins
 * are loaded: signed with a known LeanBitLab key, or (the handwriting plugin, whose releases are not
 * signed) one of the known release files.
 */
object PluginTrust {
    private const val TAG = "PluginTrust"

    /** SHA-256 of the signing certificates of the LeanBitLab plugin releases */
    private val TRUSTED_SIGNERS = setOf(
        // CJK, GIF, translation
        "01CFC72120C539EC4CC846CD70D25F83E3FC694D8BDCD2211454A8C993C4F5A3",
        // OCR, offline AI
        "963156A08CA5DD16325F8F49FBD906F5AE16A51B2D0D08C6D68913C392105B4D",
        // voice
        "C537F49420355FF10423355BB7345CAF83CA844D6416E364C53CA3DB4494F119",
    )

    /** SHA-256 of the unsigned handwriting plugin releases v1.0.0 to v1.0.6 */
    private val TRUSTED_UNSIGNED_FILES = setOf(
        "7b666ed6f020561182529e30ce106b34ea977cc355bb54d66c9a4f8306f230ed", // 1.0.0
        "264d2c1242cf0e9ae43d867370d0b01cb95ade0471b4ef8ec619bca2b6568338", // 1.0.1
        "3565cb3d7e53180d25f8ad320efa43f2ce5fc29d891ee3fa3d4c2b2224a42edb", // 1.0.2
        "5f7b96a7bf2dd7beb9cb4eae120819e3c73273cf326b500123fd492efb8ef807", // 1.0.3
        "f626910eaea998788863a3462d657cfaeedd7e38e0f2672823371a01ea733792", // 1.0.4
        "00e65a445922d11d5014a91e032f13cbdae2c538575140f4883627361bfe81a6", // 1.0.5
        "75fd358279492ee82e07587ec73708c99a7f17f1402cb8a2c45a1df3c6abc1cc", // 1.0.6 arm64-v8a
        "ec6bc10d102ea77c90e5fa4c60dc773877ecaaadd18711e931407b1bb50709cb", // 1.0.6 armeabi-v7a
        "bb209cce3a870b14249d6bb6395136c961499127b7f64d57f8a48a5b804fac76", // 1.0.6 x86
        "0a4d7ee666c902d5bbd9a86b09eae7375b2af952c3237aa94bf7c27ecb6a111a", // 1.0.6 x86_64
    )

    /** files already checked in this process, by path, size and modification time */
    private val checked = HashSet<String>()

    /** Throws a [SecurityException] if [apk] is not an official plugin; call before loading any code of it */
    fun requireTrusted(context: Context, apk: File) {
        val id = "${apk.absolutePath}:${apk.length()}:${apk.lastModified()}"
        synchronized(checked) { if (id in checked) return }
        val reason = untrustedReason(context, apk)
        if (reason == null) {
            synchronized(checked) { checked.add(id) }
            return
        }
        Log.e(TAG, "not loading ${apk.name}: $reason")
        val appContext = context.applicationContext ?: context
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, "Plugin not loaded: $reason", Toast.LENGTH_LONG).show()
        }
        throw SecurityException("untrusted plugin ${apk.name}: $reason")
    }

    /** null if [apk] is an official plugin, otherwise why not */
    fun untrustedReason(context: Context, apk: File): String? {
        val signers = try {
            signerDigests(context, apk)
        } catch (e: Exception) {
            Log.w(TAG, "could not read the signature of ${apk.name}", e)
            emptySet()
        }
        if (signers.isNotEmpty()) {
            if (signers.all { it in TRUSTED_SIGNERS }) return null
            return "unknown signing key ${signers.first().take(16)}…"
        }
        val fileHash = try { sha256(apk) } catch (e: Exception) { return "file can't be read" }
        if (fileHash in TRUSTED_UNSIGNED_FILES) return null
        return "not signed and not a known release (${fileHash.take(16)}…)"
    }

    /** SHA-256 of the certificates that signed [apk], verified by Android; empty if it is not (validly) signed */
    private fun signerDigests(context: Context, apk: File): Set<String> {
        val pm = context.packageManager
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("WrongConstant")
            val info = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info?.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)?.signatures
        } ?: return emptySet()
        return certs.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())).uppercase() }.toSet()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
