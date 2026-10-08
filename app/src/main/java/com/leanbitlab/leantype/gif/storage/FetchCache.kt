package com.leanbitlab.leantype.gif.storage

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.util.Log
import com.leanbitlab.leantype.gif.net.Downloader
import com.leanbitlab.leantype.gif.net.UrlFilter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

class FetchCache(private val context: Context) {
    companion object {
        private const val TAG = "FetchCache"
        private const val MAX_CACHE_BYTES = 40 * 1024 * 1024L // 40 MB
        private const val MIN_STORAGE_FREE_BYTES = 50 * 1024 * 1024L // 50 MB
        private const val MAX_FILE_SIZE = 15 * 1024 * 1024L // 15 MB max per GIF
    }

    private val cacheDir = File(context.cacheDir, "full_media").apply { mkdirs() }
    private val flightLocks = ConcurrentHashMap<String, Mutex>()

    data class FetchResult(
        val pfd: ParcelFileDescriptor,
        val mimeType: String,
        val length: Long
    )

    fun hasLowStorage(): Boolean {
        return try {
            val stat = StatFs(context.cacheDir.absolutePath)
            stat.availableBytes < MIN_STORAGE_FREE_BYTES
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getOrFetch(id: String, rawUrl: String): FetchResult? {
        val httpUrl = UrlFilter.validateUrl(rawUrl) ?: return null
        val fileName = "media_${id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.bin"
        val targetFile = File(cacheDir, fileName)

        val lock = flightLocks.computeIfAbsent(id) { Mutex() }
        lock.withLock {
            if (!targetFile.exists() || targetFile.length() < 32) {
                if (hasLowStorage()) {
                    Log.w(TAG, "Storage low (< 50MB free), aborting fetch")
                    return null
                }
                val ok = Downloader.downloadToFile(httpUrl, targetFile, MAX_FILE_SIZE)
                if (!ok || !targetFile.exists()) {
                    if (targetFile.exists()) targetFile.delete()
                    return null
                }
            }

            val mime = sniffMime(targetFile)
            if (mime == null) {
                targetFile.delete()
                return null
            }

            trimIfNeeded()

            return try {
                val pfd = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
                FetchResult(pfd, mime, targetFile.length())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open PFD for $targetFile", e)
                null
            }
        }
    }

    private fun sniffMime(file: File): String? {
        return try {
            val header = ByteArray(12)
            FileInputStream(file).use { it.read(header) }
            when {
                // GIF87a or GIF89a
                header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() -> "image/gif"
                // WebP (RIFF....WEBP)
                header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() && header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte() -> "image/webp"
                // PNG
                header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte() -> "image/png"
                // JPEG
                header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> "image/jpeg"
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun trimIfNeeded() {
        val files = cacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize > MAX_CACHE_BYTES) {
            val sorted = files.sortedBy { it.lastModified() }
            for (f in sorted) {
                if (totalSize <= MAX_CACHE_BYTES) break
                val len = f.length()
                if (f.delete()) {
                    totalSize -= len
                }
            }
        }
    }
}
