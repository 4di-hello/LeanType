package com.leanbitlab.leantype.gif.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ThumbRegistry(context: Context) : SQLiteOpenHelper(context, "thumbs.db", null, 1) {
    companion object {
        const val AUTHORITY = "com.leanbitlab.leantype.gif.thumbs"
        private const val TABLE_NAME = "thumbs"
        private const val MAX_ROWS = 2000
        private const val PIN_DURATION_MS = 2 * 60 * 1000L // 2 minutes
    }

    private val pinnedNames = ConcurrentHashMap<String, Long>()
    val thumbDir = File(context.cacheDir, "thumbs").apply { mkdirs() }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                name TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                provider TEXT NOT NULL,
                lastAccess INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun pin(name: String) {
        pinnedNames[name] = SystemClock.elapsedRealtime() + PIN_DURATION_MS
    }

    fun isPinned(name: String): Boolean {
        val expiry = pinnedNames[name] ?: return false
        if (SystemClock.elapsedRealtime() > expiry) {
            pinnedNames.remove(name)
            return false
        }
        return true
    }

    fun getFile(name: String): File = File(thumbDir, name)

    fun register(name: String, url: String, provider: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("name", name)
            put("url", url)
            put("provider", provider)
            put("lastAccess", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        trimIfNeeded()
    }

    fun updateAccess(name: String) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("lastAccess", System.currentTimeMillis())
        }
        db.update(TABLE_NAME, cv, "name = ?", arrayOf(name))
    }

    private fun trimIfNeeded() {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()

        if (count > MAX_ROWS) {
            val toDelete = count - MAX_ROWS
            val queryCursor = db.rawQuery(
                "SELECT name FROM $TABLE_NAME ORDER BY lastAccess ASC",
                null
            )
            var deleted = 0
            while (queryCursor.moveToNext() && deleted < toDelete) {
                val name = queryCursor.getString(0)
                if (!isPinned(name)) {
                    val file = getFile(name)
                    if (file.exists()) file.delete()
                    db.delete(TABLE_NAME, "name = ?", arrayOf(name))
                    deleted++
                }
            }
            queryCursor.close()
        }
    }
}
