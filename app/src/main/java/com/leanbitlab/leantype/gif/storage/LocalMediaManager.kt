package com.leanbitlab.leantype.gif.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.leanbitlab.leantype.gif.Contract
import com.leanbitlab.leantype.gif.GifItem
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object LocalMediaManager {
    fun getMediaDir(context: Context): File {
        return File(context.filesDir, "local_media").apply { mkdirs() }
    }

    fun getItems(context: Context, registry: ThumbRegistry): List<GifItem> {
        val dir = getMediaDir(context)
        val files = dir.listFiles { f ->
            val ext = f.extension.lowercase()
            ext in listOf("gif", "webp", "png", "jpg", "jpeg")
        }?.sortedByDescending { it.lastModified() } ?: return emptyList()

        return files.map { file ->
            val cleanExt = file.extension.lowercase()
            val safeBase = file.nameWithoutExtension.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val safeThumbName = "loc_${safeBase}_${file.name.hashCode()}.$cleanExt"
            registry.register(safeThumbName, "file://${file.absolutePath}", "local")
            val kind = detectKind(file)
            GifItem(
                id = "loc_${file.name.hashCode()}",
                provider = "local",
                kind = kind,
                previewUri = "content://${ThumbRegistry.AUTHORITY}/$safeThumbName",
                width = 200,
                height = 200,
                isPack = false,
                attribution = file.nameWithoutExtension.replace('_', ' '),
                extra = file.absolutePath
            )
        }
    }

    private fun detectKind(file: File): Int {
        val name = file.name.lowercase()
        return when {
            name.contains("sticker") -> Contract.KIND_STICKER
            name.contains("meme") -> Contract.KIND_MEME
            name.contains("emoji") -> Contract.KIND_ANIMATED_EMOJI
            else -> Contract.KIND_GIF
        }
    }

    fun importFiles(context: Context, uris: List<Uri>): Int {
        val dir = getMediaDir(context)
        var count = 0
        val contentResolver = context.contentResolver
        for (uri in uris) {
            runCatching {
                val mime = contentResolver.getType(uri) ?: ""
                val ext = when {
                    mime.contains("gif") -> "gif"
                    mime.contains("webp") -> "webp"
                    mime.contains("png") -> "png"
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                    else -> queryDisplayName(context, uri).substringAfterLast('.', "gif")
                }.lowercase()
                val rawName = queryDisplayName(context, uri).substringBeforeLast('.')
                val safeBase = rawName.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifEmpty { "media" }
                val targetFile = File(dir, "${safeBase}_${System.currentTimeMillis()}.$ext")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.length() > 32) count++ else targetFile.delete()
            }
        }
        return count
    }

    fun importZip(context: Context, zipUri: Uri): Int {
        val dir = getMediaDir(context)
        var count = 0
        runCatching {
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (!entry.isDirectory && ext in listOf("gif", "webp", "png", "jpg", "jpeg")) {
                            val rawBase = name.substringAfterLast('/').substringBeforeLast('.')
                            val safeBase = rawBase.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifEmpty { "pack" }
                            val targetFile = File(dir, "${safeBase}_${System.currentTimeMillis()}.$ext")
                            FileOutputStream(targetFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            if (targetFile.length() > 32) count++ else targetFile.delete()
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        }
        return count
    }

    fun clearAll(context: Context): Boolean {
        return getMediaDir(context).deleteRecursively().also {
            getMediaDir(context).mkdirs()
        }
    }

    fun deleteFile(file: File): Boolean {
        return runCatching { file.delete() }.getOrDefault(false)
    }

    fun getTotalBytes(context: Context): Long {
        val files = getMediaDir(context).listFiles() ?: return 0L
        return files.sumOf { if (it.isFile) it.length() else 0L }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "media.gif"
    }
}
