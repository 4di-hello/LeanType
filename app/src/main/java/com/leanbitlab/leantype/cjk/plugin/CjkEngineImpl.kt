// SPDX-License-Identifier: GPL-3.0-only
package com.leanbitlab.leantype.cjk.plugin

import android.content.Context
import android.util.Log
import helium314.keyboard.latin.cjk.CjkCandidate
import helium314.keyboard.latin.cjk.CjkContextData
import helium314.keyboard.latin.cjk.ICjkEngine
import java.io.File
import java.io.FileOutputStream

class CjkEngineImpl : ICjkEngine {

    private var isInitialized = false
    private val buffer = StringBuilder()
    private var pendingCommit: String? = null
    private var cachedContext: CjkContextData? = null

    override fun getInterfaceVersion(): Int = 1

    override fun init(context: Context, sharedDir: String, userDir: String): Boolean {
        if (!PinyinDecoderJni.isNativeLoaded()) {
            Log.e(TAG, "Native library jni_pinyin is not loaded")
            return false
        }

        try {
            val sharedFolder = File(sharedDir).apply { mkdirs() }
            val userFolder = File(userDir).apply { mkdirs() }

            val dictFile = File(sharedFolder, DICT_FILENAME)
            if (!dictFile.exists() || dictFile.length() == 0L) {
                Log.i(TAG, "Extracting $DICT_FILENAME to ${dictFile.absolutePath}")
                val inputStream = javaClass.classLoader?.getResourceAsStream("assets/$DICT_FILENAME")
                    ?: try {
                        context.createPackageContext("com.leanbitlab.leantype.cjk.plugin", 0).assets.open(DICT_FILENAME)
                    } catch (_: Exception) {
                        null
                    }
                    ?: try {
                        context.assets.open(DICT_FILENAME)
                    } catch (_: Exception) {
                        null
                    }

                if (inputStream != null) {
                    inputStream.use { input ->
                        FileOutputStream(dictFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    Log.e(TAG, "Could not locate $DICT_FILENAME in plugin bundle")
                    return false
                }
            }

            val userDictFile = File(userFolder, USER_DICT_FILENAME)
            val success = PinyinDecoderJni.nativeOpenDecoder(
                dictFile.absolutePath,
                userDictFile.absolutePath
            )

            if (success) {
                isInitialized = true
                Log.i(TAG, "Pinyin decoder engine initialized successfully")
            } else {
                Log.e(TAG, "Failed to open Pinyin decoder with dictionary: ${dictFile.absolutePath}")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Pinyin decoder initialization", e)
            return false
        }
    }

    override fun isAvailable(): Boolean = isInitialized

    override fun processKey(keysym: Int, mask: Int): Boolean {
        if (!isInitialized) return false

        when (keysym) {
            XK_BACKSPACE -> {
                if (buffer.isNotEmpty()) {
                    buffer.deleteCharAt(buffer.length - 1)
                    if (buffer.isEmpty()) {
                        clearComposition()
                    } else {
                        val count = PinyinDecoderJni.nativeDelSearch()
                        if (count <= 0) {
                            clearComposition()
                        } else {
                            updateContext(count)
                        }
                    }
                    return true
                }
                return false
            }

            XK_RETURN -> {
                if (buffer.isNotEmpty()) {
                    pendingCommit = buffer.toString()
                    clearComposition()
                    return true
                }
                return false
            }

            XK_SPACE -> {
                if (buffer.isNotEmpty()) {
                    return selectCandidate(0)
                }
                return false
            }

            // Quick selection with numeric keys 1..9
            in '1'.code..'9'.code -> {
                if (buffer.isNotEmpty()) {
                    val index = keysym - '1'.code
                    return selectCandidate(index)
                }
                return false
            }

            // Letters 'a'..'z', 'A'..'Z', or single quote '\'' delimiter
            in 'a'.code..'z'.code, in 'A'.code..'Z'.code, '\''.code -> {
                val ch = if (keysym in 'A'.code..'Z'.code) (keysym + 32).toByte() else keysym.toByte()
                buffer.append(ch.toInt().toChar())
                val count = PinyinDecoderJni.nativeAddLetter(ch)
                updateContext(count)
                return true
            }
        }

        return false
    }

    override fun getContext(): CjkContextData? = cachedContext

    override fun getCommit(): String? {
        val commit = pendingCommit
        pendingCommit = null
        return commit
    }

    override fun selectCandidate(index: Int): Boolean {
        if (!isInitialized || buffer.isEmpty()) return false

        val candidates = cachedContext?.candidates ?: return false
        if (index !in candidates.indices) return false

        val chosenText = candidates[index].text
        val newCandCount = PinyinDecoderJni.nativeChoose(index)
        val fixedLen = PinyinDecoderJni.nativeGetFixedLen()
        val pyStr = PinyinDecoderJni.nativeGetPyStr(false)

        if (newCandCount <= 1 || fixedLen >= pyStr.length || newCandCount == 0) {
            // Whole pinyin input resolved; commit chosen text
            pendingCommit = chosenText
            clearComposition()
            return true
        }

        // Partial selection (e.g. prefix match in multi-syllable word); update context for remaining syllables
        updateContext(newCandCount)
        return true
    }

    override fun clearComposition() {
        buffer.clear()
        cachedContext = null
        if (isInitialized) {
            PinyinDecoderJni.nativeResetSearch()
        }
    }

    override fun destroySession() {
        clearComposition()
        if (isInitialized) {
            PinyinDecoderJni.nativeFlushCache()
        }
    }

    override fun cleanup() {
        clearComposition()
        if (isInitialized) {
            PinyinDecoderJni.nativeCloseDecoder()
            isInitialized = false
        }
    }

    private fun updateContext(candidateCount: Int) {
        if (buffer.isEmpty() || candidateCount <= 0) {
            cachedContext = null
            return
        }

        val pyStr = PinyinDecoderJni.nativeGetPyStr(false)
        val maxFetch = minOf(candidateCount, MAX_CANDIDATES_PER_PAGE)
        val candidateList = ArrayList<CjkCandidate>(maxFetch)

        for (i in 0 until maxFetch) {
            val candText = PinyinDecoderJni.nativeGetCandidate(i)
            if (!candText.isNullOrEmpty()) {
                candidateList.add(CjkCandidate(index = i, text = candText))
            }
        }

        cachedContext = CjkContextData(
            preedit = if (pyStr.isNotEmpty()) pyStr else buffer.toString(),
            candidates = candidateList,
            selectedIndex = 0,
            hasPrevPage = false,
            hasNextPage = candidateCount > MAX_CANDIDATES_PER_PAGE
        )
    }

    companion object {
        private const val TAG = "CjkEngineImpl"
        private const val DICT_FILENAME = "dict_pinyin.dat"
        private const val USER_DICT_FILENAME = "usr_pinyin.dat"
        private const val MAX_CANDIDATES_PER_PAGE = 30

        private const val XK_BACKSPACE = 0xff08
        private const val XK_RETURN = 0xff0d
        private const val XK_SPACE = 0x0020
    }
}
