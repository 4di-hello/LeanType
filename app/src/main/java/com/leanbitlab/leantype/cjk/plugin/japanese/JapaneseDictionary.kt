// SPDX-License-Identifier: GPL-3.0-only
package com.leanbitlab.leantype.cjk.plugin.japanese

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Memory-mapped Japanese dictionary providing instant binary-search lookup,
 * prefix prediction, and user LRU adaptation.
 */
class JapaneseDictionary {

    private var mappedBuffer: MappedByteBuffer? = null
    private var bufferSize: Long = 0L
    private var randomAccessFile: RandomAccessFile? = null

    // User LRU cache: reading -> list of preferred candidates
    private val userDict = LinkedHashMap<String, MutableList<String>>(128, 0.75f, true)
    private var userDictFile: File? = null
    private var isUserDictDirty = false

    fun init(dictFile: File, userFile: File): Boolean {
        try {
            if (!dictFile.exists() || dictFile.length() == 0L) {
                Log.e(TAG, "Dictionary file missing: ${dictFile.absolutePath}")
                return false
            }

            randomAccessFile = RandomAccessFile(dictFile, "r")
            val channel = randomAccessFile!!.channel
            bufferSize = channel.size()
            mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, bufferSize)

            userDictFile = userFile
            loadUserDict(userFile)

            Log.i(TAG, "Japanese dictionary memory-mapped ($bufferSize bytes)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Japanese dictionary", e)
            return false
        }
    }

    /**
     * Looks up exact conversion candidates for the given reading.
     * Prefixes results with any user-learned candidates.
     */
    fun lookup(reading: String, maxCount: Int = 30): List<String> {
        val buf = mappedBuffer ?: return emptyList()
        val results = ArrayList<String>()

        // 1. Prepend user learned candidates if any
        val userCands = synchronized(userDict) { userDict[reading]?.toList() }
        if (!userCands.isNullOrEmpty()) {
            results.addAll(userCands)
        }

        // 2. Binary search on mapped buffer
        val targetBytes = reading.toByteArray(Charsets.UTF_8)
        var low = 0L
        var high = bufferSize

        while (low < high) {
            val mid = (low + high) / 2
            val lineStart = findLineStart(buf, mid)
            val lineBytes = readLineBytes(buf, lineStart)
            val tabIndex = findByte(lineBytes, '\t'.code.toByte())

            if (tabIndex == -1) break

            val keyBytes = lineBytes.copyOfRange(0, tabIndex)
            val cmp = compareByteArrays(keyBytes, targetBytes)

            if (cmp == 0) {
                // Exact match found! Extract candidate list
                val candsStr = String(lineBytes, tabIndex + 1, lineBytes.size - tabIndex - 1, Charsets.UTF_8)
                val cands = candsStr.split(',')
                for (c in cands) {
                    val trimmed = c.trim()
                    if (trimmed.isNotEmpty() && !results.contains(trimmed)) {
                        results.add(trimmed)
                        if (results.size >= maxCount) break
                    }
                }
                break
            } else if (cmp < 0) {
                low = lineStart + lineBytes.size + 1
            } else {
                high = lineStart
            }
        }

        return results
    }

    /**
     * Finds predictive word completions matching the given reading prefix.
     */
    fun predict(prefix: String, maxCount: Int = 10): List<Pair<String, String>> {
        val buf = mappedBuffer ?: return emptyList()
        if (prefix.isEmpty()) return emptyList()

        val results = ArrayList<Pair<String, String>>()
        val prefixBytes = prefix.toByteArray(Charsets.UTF_8)

        var low = 0L
        var high = bufferSize
        var firstMatchPos = -1L

        // Binary search for any line matching prefix
        while (low < high) {
            val mid = (low + high) / 2
            val lineStart = findLineStart(buf, mid)
            val lineBytes = readLineBytes(buf, lineStart)
            val tabIndex = findByte(lineBytes, '\t'.code.toByte())

            if (tabIndex == -1) break

            val keyBytes = lineBytes.copyOfRange(0, tabIndex)
            val cmp = comparePrefix(keyBytes, prefixBytes)

            if (cmp == 0) {
                firstMatchPos = lineStart
                high = lineStart // keep looking backwards for first occurrence
            } else if (cmp < 0) {
                low = lineStart + lineBytes.size + 1
            } else {
                high = lineStart
            }
        }

        if (firstMatchPos == -1L) return emptyList()

        // Scan forwards from first occurrence
        var curPos = firstMatchPos
        while (curPos < bufferSize && results.size < maxCount) {
            val lineBytes = readLineBytes(buf, curPos)
            val tabIndex = findByte(lineBytes, '\t'.code.toByte())
            if (tabIndex == -1) break

            val keyBytes = lineBytes.copyOfRange(0, tabIndex)
            if (comparePrefix(keyBytes, prefixBytes) != 0) break

            val reading = String(keyBytes, Charsets.UTF_8)
            // Skip exact match as it's already shown in primary candidates
            if (reading != prefix) {
                val candsStr = String(lineBytes, tabIndex + 1, lineBytes.size - tabIndex - 1, Charsets.UTF_8)
                val topCand = candsStr.substringBefore(',')
                if (topCand.isNotEmpty()) {
                    results.add(Pair(reading, topCand))
                }
            }

            curPos += lineBytes.size + 1
        }

        return results
    }

    /**
     * Records a chosen candidate for a reading into the user adaptation dictionary.
     */
    fun recordWord(reading: String, candidate: String) {
        if (reading.isEmpty() || candidate.isEmpty()) return
        synchronized(userDict) {
            val list = userDict.getOrPut(reading) { ArrayList() }
            list.remove(candidate)
            list.add(0, candidate)
            while (list.size > MAX_USER_CANDS_PER_KEY) {
                list.removeAt(list.size - 1)
            }
            if (userDict.size > MAX_USER_KEYS) {
                val firstKey = userDict.keys.iterator().next()
                userDict.remove(firstKey)
            }
            isUserDictDirty = true
        }
    }

    fun flush() {
        if (!isUserDictDirty) return
        val file = userDictFile ?: return
        try {
            synchronized(userDict) {
                file.bufferedWriter(Charsets.UTF_8).use { writer ->
                    for ((k, cands) in userDict) {
                        writer.write("$k\t${cands.joinToString(",")}\n")
                    }
                }
                isUserDictDirty = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush user dictionary", e)
        }
    }

    fun close() {
        flush()
        mappedBuffer = null
        try {
            randomAccessFile?.close()
        } catch (_: Exception) {}
        randomAccessFile = null
    }

    private fun loadUserDict(file: File) {
        if (!file.exists()) return
        try {
            synchronized(userDict) {
                file.forEachLine(Charsets.UTF_8) { line ->
                    val tab = line.indexOf('\t')
                    if (tab != -1) {
                        val key = line.substring(0, tab)
                        val cands = line.substring(tab + 1).split(',').filter { it.isNotEmpty() }
                        if (cands.isNotEmpty()) {
                            userDict[key] = cands.toMutableList()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load user dictionary", e)
        }
    }

    private fun findLineStart(buf: MappedByteBuffer, pos: Long): Long {
        var p = pos
        while (p > 0L) {
            if (buf.get((p - 1).toInt()) == '\n'.code.toByte()) {
                return p
            }
            p--
        }
        return 0L
    }

    private fun readLineBytes(buf: MappedByteBuffer, start: Long): ByteArray {
        var end = start
        val maxLen = minOf(bufferSize, start + 2048)
        while (end < maxLen) {
            if (buf.get(end.toInt()) == '\n'.code.toByte()) {
                break
            }
            end++
        }
        val len = (end - start).toInt()
        val bytes = ByteArray(len)
        buf.position(start.toInt())
        buf.get(bytes, 0, len)
        return bytes
    }

    private fun findByte(bytes: ByteArray, target: Byte): Int {
        for (i in bytes.indices) {
            if (bytes[i] == target) return i
        }
        return -1
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val byteA = a[i].toInt() and 0xFF
            val byteB = b[i].toInt() and 0xFF
            if (byteA != byteB) {
                return byteA - byteB
            }
        }
        return a.size - b.size
    }

    private fun comparePrefix(key: ByteArray, prefix: ByteArray): Int {
        val minLen = minOf(key.size, prefix.size)
        for (i in 0 until minLen) {
            val byteK = key[i].toInt() and 0xFF
            val byteP = prefix[i].toInt() and 0xFF
            if (byteK != byteP) {
                return byteK - byteP
            }
        }
        if (key.size >= prefix.size) return 0
        return -1
    }

    companion object {
        private const val TAG = "JapaneseDictionary"
        private const val MAX_USER_KEYS = 500
        private const val MAX_USER_CANDS_PER_KEY = 5
    }
}
