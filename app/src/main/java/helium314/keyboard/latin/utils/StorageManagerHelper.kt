// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.text.format.Formatter
import helium314.keyboard.latin.ai.OfflineAiLoader
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.handwriting.HandwritingLoader
import helium314.keyboard.latin.handwriting.HandwritingModelImporter
import helium314.keyboard.latin.ocr.OcrPluginLoader
import helium314.keyboard.latin.translation.TranslationLoader
import helium314.keyboard.latin.translation.TranslationModelImporter
import java.io.File
import java.util.Locale

data class StorageItem(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val type: StorageItemType,
    val canDelete: Boolean = true,
    val onDelete: () -> Unit
)

enum class StorageItemType {
    PLUGIN,
    TRANSLATION_MODEL,
    HANDWRITING_MODEL,
    DICTIONARY,
    CACHE
}

data class StorageOverview(
    val totalDataBytes: Long,
    val totalCacheBytes: Long,
    val plugins: List<StorageItem>,
    val translationModels: List<StorageItem>,
    val handwritingModels: List<StorageItem>,
    val dictionaries: List<StorageItem>,
    val cacheItems: List<StorageItem>
) {
    val totalAppBytes: Long get() = totalDataBytes + totalCacheBytes
}

object StorageManagerHelper {

    fun formatBytes(context: Context, bytes: Long): String {
        return Formatter.formatFileSize(context, bytes)
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Throwable) {
            0L
        }
    }

    fun getOverview(context: Context): StorageOverview {
        val plugins = mutableListOf<StorageItem>()
        val translationModels = mutableListOf<StorageItem>()
        val handwritingModels = mutableListOf<StorageItem>()
        val dictionaries = mutableListOf<StorageItem>()
        val cacheItems = mutableListOf<StorageItem>()

        // 1. Plugins
        // Translation Plugin
        val transApk = File(context.filesDir, "translation_plugin.apk")
        val transLibs = File(context.filesDir, "plugin_libs/translation")
        if (transApk.exists()) {
            val size = transApk.length() + getDirSize(transLibs)
            plugins.add(
                StorageItem(
                    id = "plugin_translation",
                    name = "Translation Plugin",
                    description = "Dynamic runtime for offline ML Kit translation",
                    sizeBytes = size,
                    type = StorageItemType.PLUGIN,
                    onDelete = { TranslationLoader.removePlugin(context) }
                )
            )
        }

        // Handwriting Plugin
        val hwApk = File(context.filesDir, "handwriting_plugin.apk")
        val hwLibs = File(context.filesDir, "plugin_libs/handwriting")
        if (hwApk.exists()) {
            val size = hwApk.length() + getDirSize(hwLibs)
            plugins.add(
                StorageItem(
                    id = "plugin_handwriting",
                    name = "Handwriting Plugin",
                    description = "Digital Ink recognition engine",
                    sizeBytes = size,
                    type = StorageItemType.PLUGIN,
                    onDelete = { HandwritingLoader.removePlugin(context) }
                )
            )
        }

        // OCR Plugin
        val ocrApk = File(context.filesDir, "ocr_plugin.apk")
        val ocrLibs = File(context.filesDir, "plugin_libs/ocr")
        if (ocrApk.exists()) {
            val size = ocrApk.length() + getDirSize(ocrLibs)
            plugins.add(
                StorageItem(
                    id = "plugin_ocr",
                    name = "Text Recognition (OCR) Plugin",
                    description = "Camera and image OCR recognition engine",
                    sizeBytes = size,
                    type = StorageItemType.PLUGIN,
                    onDelete = { OcrPluginLoader.removePlugin(context) }
                )
            )
        }

        // Offline AI Plugin
        val aiApk = File(context.filesDir, "offline_ai_plugin.apk")
        val aiLibs = File(context.filesDir, "plugin_libs/offline_ai")
        if (aiApk.exists()) {
            val size = aiApk.length() + getDirSize(aiLibs)
            plugins.add(
                StorageItem(
                    id = "plugin_offline_ai",
                    name = "Offline AI Plugin",
                    description = "On-device LLM proofreading engine",
                    sizeBytes = size,
                    type = StorageItemType.PLUGIN,
                    onDelete = { OfflineAiLoader.removePlugin(context) }
                )
            )
        }

        // 2. Offline Translation Models
        val transBaseDir = context.noBackupFilesDir ?: context.filesDir
        val transModelsDir = File(transBaseDir, "com.google.mlkit.translate.models")
        if (transModelsDir.exists() && transModelsDir.isDirectory) {
            transModelsDir.listFiles()?.filter { it.isDirectory && it.name != "0" }?.forEach { dir ->
                val size = getDirSize(dir)
                if (size > 0) {
                    val pair = dir.name
                    val langCode = if (pair.contains("_")) {
                        val parts = pair.split("_")
                        if (parts[0] == "en") parts[1] else parts[0]
                    } else pair
                    val locale = Locale.forLanguageTag(langCode)
                    val langName = locale.getDisplayName(Locale.getDefault()).ifBlank { langCode }
                    translationModels.add(
                        StorageItem(
                            id = "trans_model_$pair",
                            name = "$langName ($pair)",
                            description = "Offline translation model files",
                            sizeBytes = size,
                            type = StorageItemType.TRANSLATION_MODEL,
                            onDelete = {
                                TranslationModelImporter.deleteModel(context, langCode)
                                dir.deleteRecursively()
                            }
                        )
                    )
                }
            }
        }

        // 3. Offline Handwriting Models
        val hwBaseDir = context.noBackupFilesDir ?: context.filesDir
        val hwModelsDir = File(hwBaseDir, "com.google.mlkit.models")
        if (hwModelsDir.exists() && hwModelsDir.isDirectory) {
            hwModelsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val size = getDirSize(dir)
                if (size > 0) {
                    val tag = dir.name
                    val locale = Locale.forLanguageTag(tag)
                    val langName = locale.getDisplayName(Locale.getDefault()).ifBlank { tag }
                    handwritingModels.add(
                        StorageItem(
                            id = "hw_model_$tag",
                            name = "$langName ($tag)",
                            description = "Digital Ink recognition model",
                            sizeBytes = size,
                            type = StorageItemType.HANDWRITING_MODEL,
                            onDelete = {
                                HandwritingModelImporter.deleteModelForLanguage(context, tag)
                                dir.deleteRecursively()
                            }
                        )
                    )
                }
            }
        }

        // 4. Cached / User Dictionaries
        try {
            val dictDir = File(DictionaryInfoUtils.getWordListCacheDirectory(context))
            if (dictDir.exists() && dictDir.isDirectory) {
                DictionaryInfoUtils.getCacheDirectories(context).forEach { dir ->
                    val size = getDirSize(dir)
                    if (size > 0) {
                        val locale = DictionaryInfoUtils.getWordListIdFromFileName(dir.name).constructLocale()
                        val name = locale.getDisplayName(Locale.getDefault()).ifBlank { dir.name }
                        dictionaries.add(
                            StorageItem(
                                id = "dict_${dir.name}",
                                name = "$name (${dir.name})",
                                description = "Cached wordlist dictionary",
                                sizeBytes = size,
                                type = StorageItemType.DICTIONARY,
                                onDelete = { dir.deleteRecursively() }
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {}

        // 5. Cache and Temporary Files
        val cacheDir = context.cacheDir
        val cacheDirSize = getDirSize(cacheDir)
        if (cacheDirSize > 0) {
            cacheItems.add(
                StorageItem(
                    id = "cache_app_dir",
                    name = "Temporary Cache",
                    description = "Model download staging, temporary extracts, and crash logs",
                    sizeBytes = cacheDirSize,
                    type = StorageItemType.CACHE,
                    onDelete = { clearDirectory(cacheDir) }
                )
            )
        }

        val codeCacheDir = context.codeCacheDir
        val codeCacheSize = getDirSize(codeCacheDir)
        if (codeCacheSize > 0) {
            cacheItems.add(
                StorageItem(
                    id = "cache_code_dir",
                    name = "Code Cache",
                    description = "Optimized dex code and compilation caches",
                    sizeBytes = codeCacheSize,
                    type = StorageItemType.CACHE,
                    onDelete = { clearDirectory(codeCacheDir) }
                )
            )
        }

        // Total Internal Data calculation (filesDir + noBackupFilesDir)
        val dataBytes = getDirSize(context.filesDir) + getDirSize(context.noBackupFilesDir)
        val cacheBytes = cacheDirSize + codeCacheSize

        return StorageOverview(
            totalDataBytes = dataBytes,
            totalCacheBytes = cacheBytes,
            plugins = plugins.sortedByDescending { it.sizeBytes },
            translationModels = translationModels.sortedByDescending { it.sizeBytes },
            handwritingModels = handwritingModels.sortedByDescending { it.sizeBytes },
            dictionaries = dictionaries.sortedByDescending { it.sizeBytes },
            cacheItems = cacheItems.sortedByDescending { it.sizeBytes }
        )
    }

    private fun clearDirectory(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            try {
                file.deleteRecursively()
            } catch (_: Throwable) {}
        }
    }

    fun clearAllCache(context: Context): Long {
        val before = getDirSize(context.cacheDir) + getDirSize(context.codeCacheDir)
        clearDirectory(context.cacheDir)
        clearDirectory(context.codeCacheDir)
        val after = getDirSize(context.cacheDir) + getDirSize(context.codeCacheDir)
        return (before - after).coerceAtLeast(0L)
    }

    fun pruneRedundantFiles(context: Context): Long {
        val before = getDirSize(context.filesDir) + getDirSize(context.noBackupFilesDir) + getDirSize(context.cacheDir)
        try {
            cleanUnusedMainDicts(context)
            TranslationModelImporter.migrateLegacyModels(context)
            HandwritingModelImporter.migrateLegacyModels(context)
            // Clean temp archives in cache
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("import_translation_") || f.name.startsWith("hw_") || f.name.startsWith("temp_")) {
                    f.deleteRecursively()
                }
            }
        } catch (_: Throwable) {}
        val after = getDirSize(context.filesDir) + getDirSize(context.noBackupFilesDir) + getDirSize(context.cacheDir)
        return (before - after).coerceAtLeast(0L)
    }
}
