// SPDX-License-Identifier: GPL-3.0-only
package com.leanbitlab.leantype.cjk.plugin

import android.content.Context
import android.util.Log
import com.leanbitlab.leantype.cjk.plugin.japanese.JapaneseDictionary
import com.leanbitlab.leantype.cjk.plugin.japanese.JapaneseEngine
import helium314.keyboard.latin.cjk.CjkCandidate
import helium314.keyboard.latin.cjk.CjkContextData
import helium314.keyboard.latin.cjk.ICjkEngine
import java.io.File
import java.io.FileOutputStream

class CjkEngineImpl : ICjkEngine {

    private var activeLanguage = "zh"
    private var isPinyinReady = false
    private var isJapaneseReady = false

    // Chinese Pinyin Engine state
    private val pinyinBuffer = StringBuilder()
    private var pendingPinyinCommit: String? = null
    private var cachedPinyinContext: CjkContextData? = null

    // Japanese Engine
    private var japaneseDictionary: JapaneseDictionary? = null
    private var japaneseEngine: JapaneseEngine? = null

    override fun getInterfaceVersion(): Int = 2

    override fun setLocale(locale: String): Boolean {
        val lang = locale.lowercase().split("_", "-")[0]
        if (lang == "ja" || lang == "zh") {
            if (activeLanguage != lang) {
                clearComposition()
                activeLanguage = lang
                Log.d(TAG, "Switched CJK engine active language to $activeLanguage")
            }
            return true
        }
        return false
    }

    override fun supportsLanguage(language: String): Boolean {
        val lang = language.lowercase().split("_", "-")[0]
        return lang == "zh" || lang == "ja"
    }

    override fun init(context: Context, sharedDir: String, userDir: String): Boolean {
        try {
            val sharedFolder = File(sharedDir).apply { mkdirs() }
            val userFolder = File(userDir).apply { mkdirs() }

            // 1. Initialize Chinese Pinyin Engine
            if (PinyinDecoderJni.isNativeLoaded()) {
                val pinyinDictFile = File(sharedFolder, DICT_PINYIN_FILENAME)
                if (extractAsset(context, DICT_PINYIN_FILENAME, pinyinDictFile)) {
                    val userDictFile = File(userFolder, USER_PINYIN_FILENAME)
                    val success = PinyinDecoderJni.nativeOpenDecoder(
                        pinyinDictFile.absolutePath,
                        userDictFile.absolutePath
                    )
                    if (success) {
                        isPinyinReady = true
                        Log.i(TAG, "Pinyin decoder engine initialized successfully")
                    } else {
                        Log.e(TAG, "Failed to open Pinyin decoder: ${pinyinDictFile.absolutePath}")
                    }
                }
            } else {
                Log.w(TAG, "Native library jni_pinyin is not loaded")
            }

            // 2. Initialize Japanese Engine
            val jaDictFile = File(sharedFolder, DICT_JAPANESE_FILENAME)
            if (extractAsset(context, DICT_JAPANESE_FILENAME, jaDictFile)) {
                val jaUserDictFile = File(userFolder, USER_JAPANESE_FILENAME)
                val jaDict = JapaneseDictionary()
                if (jaDict.init(jaDictFile, jaUserDictFile)) {
                    japaneseDictionary = jaDict
                    japaneseEngine = JapaneseEngine(jaDict)
                    isJapaneseReady = true
                    Log.i(TAG, "Japanese engine initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize Japanese dictionary: ${jaDictFile.absolutePath}")
                }
            }

            return isPinyinReady || isJapaneseReady
        } catch (e: Exception) {
            Log.e(TAG, "Exception during CJK engine initialization", e)
            return false
        }
    }

    override fun isAvailable(): Boolean {
        return if (activeLanguage == "ja") isJapaneseReady else isPinyinReady
    }

    override fun processKey(keysym: Int, mask: Int): Boolean {
        if (!isAvailable()) return false

        if (activeLanguage == "ja") {
            return japaneseEngine?.processKey(keysym, mask) ?: false
        }

        // Chinese Pinyin processing
        when (keysym) {
            XK_BACKSPACE -> {
                if (pinyinBuffer.isNotEmpty()) {
                    pinyinBuffer.deleteCharAt(pinyinBuffer.length - 1)
                    if (pinyinBuffer.isEmpty()) {
                        clearComposition()
                    } else {
                        val count = PinyinDecoderJni.nativeSearch(pinyinBuffer.toString())
                        updatePinyinContext(count)
                    }
                    return true
                }
                return false
            }

            XK_RETURN -> {
                if (pinyinBuffer.isNotEmpty()) {
                    pendingPinyinCommit = pinyinBuffer.toString()
                    clearComposition()
                    return true
                }
                return false
            }

            XK_SPACE -> {
                if (pinyinBuffer.isNotEmpty()) {
                    return selectCandidate(0)
                }
                return false
            }

            // Quick selection with numeric keys 1..9
            in '1'.code..'9'.code -> {
                if (pinyinBuffer.isNotEmpty()) {
                    val index = keysym - '1'.code
                    return selectCandidate(index)
                }
                return false
            }

            // Letters 'a'..'z', 'A'..'Z', or single quote '\'' delimiter
            in 'a'.code..'z'.code, in 'A'.code..'Z'.code, '\''.code -> {
                val ch = if (keysym in 'A'.code..'Z'.code) (keysym + 32).toByte() else keysym.toByte()
                pinyinBuffer.append(ch.toInt().toChar())
                val count = PinyinDecoderJni.nativeSearch(pinyinBuffer.toString())
                updatePinyinContext(count)
                return true
            }
        }

        return false
    }

    override fun getContext(): CjkContextData? {
        return if (activeLanguage == "ja") {
            japaneseEngine?.getContext()
        } else {
            cachedPinyinContext
        }
    }

    override fun getCommit(): String? {
        return if (activeLanguage == "ja") {
            japaneseEngine?.getCommit()
        } else {
            val commit = pendingPinyinCommit
            pendingPinyinCommit = null
            commit
        }
    }

    override fun selectCandidate(index: Int): Boolean {
        if (!isAvailable()) return false

        if (activeLanguage == "ja") {
            return japaneseEngine?.selectCandidate(index) ?: false
        }

        // Chinese Pinyin candidate selection
        if (pinyinBuffer.isEmpty()) return false
        val candidates = cachedPinyinContext?.candidates ?: return false
        if (index !in candidates.indices) return false

        val chosenText = candidates[index].text
        val newCandCount = PinyinDecoderJni.nativeChoose(index)
        val fixedLen = PinyinDecoderJni.nativeGetFixedLen()
        val pyStr = PinyinDecoderJni.nativeGetPyStr(false)

        if (newCandCount <= 1 || fixedLen >= pyStr.length || newCandCount == 0) {
            val fullText = if (newCandCount == 1) {
                PinyinDecoderJni.nativeGetCandidate(0) ?: chosenText
            } else {
                chosenText
            }
            pendingPinyinCommit = fullText
            clearComposition()
            return true
        }

        updatePinyinContext(newCandCount)
        return true
    }

    override fun clearComposition() {
        if (activeLanguage == "ja") {
            japaneseEngine?.clearComposition()
        } else {
            pinyinBuffer.clear()
            cachedPinyinContext = null
            if (isPinyinReady) {
                PinyinDecoderJni.nativeResetSearch()
            }
        }
    }

    override fun destroySession() {
        clearComposition()
        if (isPinyinReady) {
            PinyinDecoderJni.nativeFlushCache()
        }
        japaneseDictionary?.flush()
    }

    override fun cleanup() {
        clearComposition()
        if (isPinyinReady) {
            PinyinDecoderJni.nativeCloseDecoder()
            isPinyinReady = false
        }
        japaneseDictionary?.close()
        japaneseDictionary = null
        japaneseEngine = null
        isJapaneseReady = false
    }

    private fun extractAsset(context: Context, filename: String, destinationFile: File): Boolean {
        if (destinationFile.exists() && destinationFile.length() > 0L) {
            return true
        }

        Log.i(TAG, "Extracting $filename to ${destinationFile.absolutePath}")
        val inputStream = javaClass.classLoader?.getResourceAsStream("assets/$filename")
            ?: try {
                context.createPackageContext("com.leanbitlab.leantype.cjk.plugin", 0).assets.open(filename)
            } catch (_: Exception) {
                null
            }
            ?: try {
                context.assets.open(filename)
            } catch (_: Exception) {
                null
            }

        if (inputStream != null) {
            inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            return true
        } else {
            Log.e(TAG, "Could not locate $filename in plugin bundle")
            return false
        }
    }

    private fun updatePinyinContext(candidateCount: Int) {
        if (pinyinBuffer.isEmpty()) {
            cachedPinyinContext = null
            return
        }

        val pyStr = PinyinDecoderJni.nativeGetPyStr(false)
        val maxFetch = if (candidateCount > 0) minOf(candidateCount, MAX_CANDIDATES_PER_PAGE) else 0
        val candidateList = ArrayList<CjkCandidate>(maxFetch)

        for (i in 0 until maxFetch) {
            val candText = PinyinDecoderJni.nativeGetCandidate(i)
            if (!candText.isNullOrEmpty()) {
                candidateList.add(CjkCandidate(index = i, text = candText, comment = null))
            }
        }

        cachedPinyinContext = CjkContextData(
            preedit = if (pyStr.isNotEmpty()) pyStr else pinyinBuffer.toString(),
            candidates = candidateList,
            selectedIndex = 0,
            hasPrevPage = false,
            hasNextPage = candidateCount > MAX_CANDIDATES_PER_PAGE
        )
    }

    companion object {
        private const val TAG = "CjkEngineImpl"
        private const val DICT_PINYIN_FILENAME = "dict_pinyin.dat"
        private const val USER_PINYIN_FILENAME = "usr_pinyin.dat"
        private const val DICT_JAPANESE_FILENAME = "dict_japanese.dat"
        private const val USER_JAPANESE_FILENAME = "usr_japanese.dat"
        private const val MAX_CANDIDATES_PER_PAGE = 30

        private const val XK_BACKSPACE = 0xff08
        private const val XK_RETURN = 0xff0d
        private const val XK_SPACE = 0x0020
    }
}
