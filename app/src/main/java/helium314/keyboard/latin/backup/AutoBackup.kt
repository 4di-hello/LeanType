// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import helium314.keyboard.compat.isUserLocked
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.settings.preferences.BackupCategory
import helium314.keyboard.settings.preferences.writeBackup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Regular backups into a folder chosen by the user. The settings are stored separately and are not
 * part of backups: the folder permission and the password (encrypted with a key of this device)
 * can't be used on another device anyway.
 */
object AutoBackup {
    private const val TAG = "AutoBackup"
    private const val PREFS_NAME = "auto_backup"
    private const val WORK_NAME = "auto_backup"
    private const val WORK_NAME_NOW = "auto_backup_now"
    const val FILE_PREFIX = "LeanType_auto_"

    const val INTERVAL_OFF = 0
    const val INTERVAL_DAILY = 1
    const val INTERVAL_WEEKLY = 7

    private const val KEY_FOLDER = "folder"
    private const val KEY_INTERVAL = "interval_days"
    private const val KEY_KEEP = "keep"
    private const val KEY_PASSWORD = "password"
    private const val KEY_LAST_TIME = "last_time"
    private const val KEY_LAST_ERROR = "last_error"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFolder(context: Context): Uri? = prefs(context).getString(KEY_FOLDER, null)?.let { Uri.parse(it) }

    fun setFolder(context: Context, uri: Uri?) {
        val old = getFolder(context)
        if (old != null && old != uri) runCatching {
            context.contentResolver.releasePersistableUriPermission(old,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs(context).edit { if (uri == null) remove(KEY_FOLDER) else putString(KEY_FOLDER, uri.toString()) }
        schedule(context)
    }

    fun getInterval(context: Context) = prefs(context).getInt(KEY_INTERVAL, INTERVAL_OFF)

    fun setInterval(context: Context, days: Int) {
        prefs(context).edit { putInt(KEY_INTERVAL, days) }
        schedule(context)
    }

    fun getKeep(context: Context) = prefs(context).getInt(KEY_KEEP, 3).coerceIn(1, 20)

    fun setKeep(context: Context, keep: Int) = prefs(context).edit { putInt(KEY_KEEP, keep.coerceIn(1, 20)) }

    fun hasPassword(context: Context) = prefs(context).contains(KEY_PASSWORD)

    /** stored encrypted with a key that never leaves this device */
    fun setPassword(context: Context, password: String?): Boolean {
        if (password.isNullOrEmpty()) {
            prefs(context).edit { remove(KEY_PASSWORD) }
            return true
        }
        val encrypted = LocalCrypto.encryptString(password) ?: return false
        prefs(context).edit { putString(KEY_PASSWORD, encrypted) }
        return true
    }

    private fun getPassword(context: Context): CharArray? =
        prefs(context).getString(KEY_PASSWORD, null)?.let { LocalCrypto.decryptString(it) }?.toCharArray()

    fun getLastTime(context: Context) = prefs(context).getLong(KEY_LAST_TIME, 0L)

    fun getLastError(context: Context): String? = prefs(context).getString(KEY_LAST_ERROR, null)

    /** (re)schedule the regular backup according to the settings */
    fun schedule(context: Context) {
        val workManager = try { WorkManager.getInstance(context) } catch (e: Exception) { return }
        val days = getInterval(context)
        if (days == INTERVAL_OFF || getFolder(context) == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(days.toLong(), TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun backupNow(context: Context) {
        val workManager = try { WorkManager.getInstance(context) } catch (e: Exception) { return }
        workManager.enqueueUniqueWork(WORK_NAME_NOW, androidx.work.ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }

    /** Writes a backup into the folder and removes old ones. Returns null on success, or the error. */
    fun run(context: Context): String? {
        val tree = getFolder(context) ?: return "no folder"
        val resolver = context.contentResolver
        val password = getPassword(context)
        if (hasPassword(context) && password == null) return "password not readable, please set it again"
        val categories = mutableSetOf(
            BackupCategory.LAYOUTS, BackupCategory.THEME_APPEARANCE, BackupCategory.DICTIONARIES,
            BackupCategory.TYPING_HISTORY, BackupCategory.GENERAL_SETTINGS, BackupCategory.TYPING_PROFILE,
        )
        // copied texts may contain private data, they are only saved in encrypted backups
        if (password != null) categories.add(BackupCategory.CLIPBOARD)
        val date = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).format(Date())
        val name = FILE_PREFIX + date + if (password != null) "." + BackupCrypto.FILE_EXTENSION else ".zip"
        val mime = if (password != null) "application/octet-stream" else "application/zip"
        return try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val file = DocumentsContract.createDocument(resolver, parent, mime, name) ?: return "could not create file"
            try {
                resolver.openOutputStream(file)?.use { writeBackup(context, it, categories, password) }
                    ?: return "could not write file"
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, file) }
                throw e
            }
            removeOldBackups(context, tree)
            null
        } catch (e: SecurityException) {
            "no access to the folder, please choose it again"
        } catch (e: Exception) {
            Log.w(TAG, "auto backup failed", e)
            e.message ?: e.javaClass.simpleName
        } finally {
            password?.fill(' ')
        }
    }

    private fun removeOldBackups(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val backups = mutableListOf<Pair<String, String>>() // name, document id
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(FILE_PREFIX)) backups.add(name to c.getString(0))
            }
        }
        // names contain the date, so sorting by name sorts by age
        backups.sortedByDescending { it.first }.drop(getKeep(context)).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    internal fun saveResult(context: Context, error: String?) {
        prefs(context).edit {
            if (error == null) {
                putLong(KEY_LAST_TIME, System.currentTimeMillis())
                remove(KEY_LAST_ERROR)
            } else putString(KEY_LAST_ERROR, error)
        }
    }

    fun isUserUnlocked(context: Context) = !isUserLocked(context)
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        // data of the user is only available after unlocking the device
        if (!AutoBackup.isUserUnlocked(applicationContext)) return Result.retry()
        val error = AutoBackup.run(applicationContext)
        AutoBackup.saveResult(applicationContext, error)
        return Result.success()
    }
}
