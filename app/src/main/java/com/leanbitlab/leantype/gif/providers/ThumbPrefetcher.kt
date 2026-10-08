package com.leanbitlab.leantype.gif.providers

import com.leanbitlab.leantype.gif.net.Downloader
import com.leanbitlab.leantype.gif.net.UrlFilter
import com.leanbitlab.leantype.gif.sha256Hex
import com.leanbitlab.leantype.gif.storage.ThumbRegistry
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileInputStream

object ThumbPrefetcher {
    private const val MAX_THUMB_BYTES = 150 * 1024L // 150 KB
    private val semaphore = Semaphore(3) // Bounded concurrency of 3

    suspend fun prefetchAndRegister(
        registry: ThumbRegistry,
        providerId: String,
        previewRawUrl: String
    ): String? {
        val httpUrl = UrlFilter.validateUrl(previewRawUrl) ?: return null
        val hash = sha256Hex(previewRawUrl).take(16)
        val ext = when {
            previewRawUrl.contains(".webp", ignoreCase = true) -> "webp"
            previewRawUrl.contains(".png", ignoreCase = true) -> "png"
            previewRawUrl.contains(".jpg", ignoreCase = true) || previewRawUrl.contains(".jpeg", ignoreCase = true) -> "jpg"
            else -> "webp"
        }
        val fileName = "${providerId}_${hash}.$ext"
        val targetFile = registry.getFile(fileName)

        if (!targetFile.exists() || targetFile.length() < 32) {
            val ok = semaphore.withPermit {
                Downloader.downloadToFile(httpUrl, targetFile, MAX_THUMB_BYTES)
            }
            if (!ok || !targetFile.exists() || targetFile.length() < 32) {
                targetFile.delete()
                return null
            }
        }

        if (!isValidMagic(targetFile)) {
            targetFile.delete()
            return null
        }

        registry.pin(fileName)
        registry.register(fileName, previewRawUrl, providerId)
        return "content://${ThumbRegistry.AUTHORITY}/$fileName"
    }

    private fun isValidMagic(file: File): Boolean {
        return try {
            val header = ByteArray(12)
            FileInputStream(file).use { it.read(header) }
            when {
                // GIF87a or GIF89a
                header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() -> true
                // PNG
                header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte() -> true
                // JPEG
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> true
                // WebP (RIFF....WEBP)
                header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() && header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte() -> true
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}
