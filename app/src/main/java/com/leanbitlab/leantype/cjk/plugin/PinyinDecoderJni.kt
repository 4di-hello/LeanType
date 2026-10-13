// SPDX-License-Identifier: GPL-3.0-only
package com.leanbitlab.leantype.cjk.plugin

import android.util.Log

object PinyinDecoderJni {
    private const val TAG = "PinyinDecoderJni"
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("jni_pinyin")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load jni_pinyin native library", e)
        }
    }

    fun isNativeLoaded(): Boolean = isLoaded

    external fun nativeOpenDecoder(sysDictPath: String, usrDictPath: String?): Boolean
    external fun nativeCloseDecoder()
    external fun nativeResetSearch()
    external fun nativeAddLetter(ch: Byte): Int
    external fun nativeDelSearch(): Int
    external fun nativeGetPyStr(decodedOnly: Boolean): String
    external fun nativeGetCandidate(index: Int): String?
    external fun nativeChoose(index: Int): Int
    external fun nativeGetFixedLen(): Int
    external fun nativeCancelLastChoice(): Int
    external fun nativeFlushCache()
}
