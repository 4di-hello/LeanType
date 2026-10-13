// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.cjk

import android.content.Context
import android.net.Uri
import android.os.Build
import dalvik.system.DexClassLoader
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.util.zip.ZipFile

/**
 * Dynamic in-process loader for CJK companion plugins (e.g. LeanType-CJK-Plugin).
 * Loads native libraries and the ICjkEngine implementation directly into the process heap
 * via DexClassLoader while strictly respecting Android 14 W^X security policies.
 */
object CjkPluginLoader {
    private const val CURRENT_INTERFACE_VERSION = 1
    private const val PLUGIN_PACKAGE_NAME = "com.leanbitlab.leantype.cjk.plugin"
    private const val PLUGIN_FILENAME = "cjk_plugin.apk"
    private const val PLUGIN_CLASS_NAME = "com.leanbitlab.leantype.cjk.plugin.CjkEngineImpl"
    private const val PREF_HAS_PLUGIN = "pref_cjk_has_plugin"
    private const val PREF_ENABLE_MOCK = "pref_cjk_enable_mock"
    private const val TAG = "CjkPluginLoader"

    private var activeEngine: ICjkEngine? = null
    private var lastApkModified: Long = 0L

    fun getTargetAbi(): String {
        for (abi in Build.SUPPORTED_ABIS) {
            when (abi) {
                "arm64-v8a" -> return "arm64-v8a"
                "x86_64" -> return "x86_64"
                "armeabi-v7a" -> return "armeabi-v7a"
            }
        }
        return "arm64-v8a"
    }

    fun getInstalledPluginApk(context: Context): File? {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, 0)
            val path = pkgInfo.applicationInfo?.sourceDir
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                if (file.exists() && file.length() > 0L) file else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun hasPlugin(context: Context): Boolean {
        if (getInstalledPluginApk(context) != null) return true
        val apkFile = File(context.filesDir, PLUGIN_FILENAME)
        if (apkFile.exists() && apkFile.length() > 0L) return true
        return context.prefs().getBoolean(PREF_HAS_PLUGIN, false)
    }

    fun isMockEnabled(context: Context): Boolean {
        return context.prefs().getBoolean(PREF_ENABLE_MOCK, BuildConfig.DEBUG)
    }

    fun setMockEnabled(context: Context, enabled: Boolean) {
        context.prefs().edit().putBoolean(PREF_ENABLE_MOCK, enabled).apply()
    }

    fun getEngine(context: Context): ICjkEngine? {
        val installedApk = getInstalledPluginApk(context)
        val apkModified = installedApk?.lastModified() ?: 0L
        val cached = activeEngine
        if (cached != null && cached.isAvailable() && (installedApk == null || apkModified == lastApkModified)) {
            return cached
        }
        if (cached != null && installedApk != null && apkModified != lastApkModified) {
            Log.i(TAG, "Companion plugin APK updated on disk (timestamp changed), reloading engine")
            try { cached.cleanup() } catch (_: Exception) {}
            activeEngine = null
        }

        // 1. Try loading companion plugin from installed package
        if (installedApk != null) {
            Log.i(TAG, "Attempting to load CJK engine from installed app package: ${installedApk.absolutePath}")
            val engine = loadEngineInternal(context, installedApk)
            if (engine != null && engine.isAvailable()) {
                activeEngine = engine
                lastApkModified = apkModified
                return engine
            }
        }

        // 2. Try loading companion plugin from storage
        val internalApk = File(context.filesDir, PLUGIN_FILENAME)
        if (internalApk.exists() && internalApk.length() > 0L) {
            Log.i(TAG, "Attempting to load CJK engine from internal storage: ${internalApk.absolutePath}")
            val engine = loadEngineInternal(context, internalApk)
            if (engine != null && engine.isAvailable()) {
                activeEngine = engine
                return engine
            }
        }

        // 3. Fall back to in-memory MockPinyinEngine if mock is enabled
        if (isMockEnabled(context)) {
            Log.i(TAG, "Using in-memory MockPinyinEngine fallback")
            val mock = MockPinyinEngine()
            val sharedDir = File(context.filesDir, "cjk/shared").apply { mkdirs() }
            val userDir = File(context.filesDir, "cjk/user").apply { mkdirs() }
            if (mock.init(context, sharedDir.absolutePath, userDir.absolutePath)) {
                activeEngine = mock
                return mock
            }
        }

        return null
    }

    private fun loadEngineInternal(context: Context, apkFile: File): ICjkEngine? {
        return try {
            val sharedDir = File(context.filesDir, "cjk/shared").apply { mkdirs() }
            val userDir = File(context.filesDir, "cjk/user").apply { mkdirs() }
            val nativeLibDir = getNativeLibDir(context, apkFile)

            extractPluginAssetsAndLibs(apkFile, nativeLibDir, sharedDir)

            val classLoader = PluginClassLoader(
                apkFile.absolutePath,
                context.codeCacheDir.absolutePath,
                nativeLibDir.absolutePath,
                context.classLoader
            )

            val clazz = classLoader.loadClass(PLUGIN_CLASS_NAME)
            val engine = clazz.getDeclaredConstructor().newInstance() as ICjkEngine

            if (engine.getInterfaceVersion() > CURRENT_INTERFACE_VERSION) {
                Log.w(TAG, "Plugin interface version ${engine.getInterfaceVersion()} newer than host $CURRENT_INTERFACE_VERSION")
                return null
            }

            if (!engine.init(context, sharedDir.absolutePath, userDir.absolutePath)) {
                Log.w(TAG, "Engine initialization returned false")
                return null
            }

            Log.i(TAG, "CJK engine loaded successfully from companion plugin")
            engine
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load CJK plugin", e)
            null
        }
    }

    private fun getNativeLibDir(context: Context, apkFile: File): File {
        val baseDir = File(context.filesDir, "plugin_libs")
        if (!baseDir.exists()) baseDir.mkdirs()
        val targetName = "cjk_${apkFile.lastModified()}"
        val targetDir = File(baseDir, targetName)
        baseDir.listFiles()?.forEach { f ->
            if (f.isDirectory && (f.name.startsWith("cjk_") || f.name == "cjk") && f.name != targetName) {
                try { f.deleteRecursively() } catch (_: Exception) {}
            }
        }
        return targetDir
    }

    private fun extractPluginAssetsAndLibs(apkFile: File, destLibDir: File, destSharedDir: File) {
        if (!destLibDir.exists()) destLibDir.mkdirs()
        if (!destSharedDir.exists()) destSharedDir.mkdirs()
        try {
            val zip = ZipFile(apkFile)
            val allEntries = zip.entries().asSequence().toList()
            val targetAbi = Build.SUPPORTED_ABIS.firstOrNull { abi ->
                allEntries.any { it.name.startsWith("lib/$abi/") && it.name.endsWith(".so") }
            } ?: getTargetAbi()
            for (entry in allEntries) {
                if (entry.name.startsWith("lib/$targetAbi/") && entry.name.endsWith(".so")) {
                    val libName = entry.name.substringAfterLast("/")
                    val outFile = File(destLibDir, libName)
                    if (outFile.exists() && outFile.length() != entry.size) {
                        outFile.delete()
                    }
                    if (!outFile.exists()) {
                        zip.getInputStream(entry).use { input ->
                            java.io.FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    outFile.setReadable(true, false)
                    outFile.setExecutable(true, false)
                    outFile.setReadOnly()
                } else if (entry.name.endsWith(".dat") && entry.name.contains("dict")) {
                    val dictName = entry.name.substringAfterLast("/")
                    val outFile = File(destSharedDir, dictName)
                    if (outFile.exists() && outFile.length() != entry.size) {
                        outFile.delete()
                    }
                    if (!outFile.exists()) {
                        zip.getInputStream(entry).use { input ->
                            java.io.FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        outFile.setReadable(true, false)
                        outFile.setReadOnly()
                    }
                }
            }
            zip.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting native libs and assets from CJK plugin", e)
        }
    }

    fun loadPluginFromUri(context: Context, sourceUri: Uri): Boolean {
        return try {
            try {
                context.codeCacheDir.deleteRecursively()
            } catch (_: Exception) {}

            val targetFile = File(context.filesDir, PLUGIN_FILENAME)
            if (targetFile.exists()) targetFile.delete()

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                java.io.FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return false

            targetFile.setReadOnly()
            activeEngine?.cleanup()
            activeEngine = null

            val engine = loadEngineInternal(context, targetFile)
            val success = engine != null && engine.isAvailable()
            if (success) {
                activeEngine = engine
                context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, true).apply()
                Log.i(TAG, "CJK plugin imported and verified successfully")
            } else {
                targetFile.delete()
                context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
                Log.w(TAG, "CJK plugin verification failed")
            }
            success
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load CJK plugin from URI", e)
            false
        }
    }

    fun removePlugin(context: Context) {
        try {
            activeEngine?.cleanup()
            activeEngine = null
            val apkFile = File(context.filesDir, PLUGIN_FILENAME)
            if (apkFile.exists()) apkFile.delete()
            val nativeLibBase = File(context.filesDir, "plugin_libs")
            nativeLibBase.listFiles()?.forEach { f ->
                if (f.isDirectory && (f.name.startsWith("cjk_") || f.name == "cjk")) {
                    try { f.deleteRecursively() } catch (_: Exception) {}
                }
            }
            context.prefs().edit().putBoolean(PREF_HAS_PLUGIN, false).apply()
            Log.i(TAG, "CJK plugin removed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing CJK plugin", e)
        }
    }

    class PluginClassLoader(
        dexPath: String,
        optimizedDirectory: String?,
        librarySearchPath: String?,
        parent: ClassLoader
    ) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent)
}
