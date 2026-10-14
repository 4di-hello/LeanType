// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.backup.AutoBackup
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import java.text.DateFormat
import java.util.Date

/** Settings for [AutoBackup]: folder, interval, how many backups to keep, password */
@Composable
fun AutoBackupPreference(setting: Setting) {
    val ctx = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val description = remember(refresh, showDialog) { describe(ctx) }
    Preference(name = setting.title, description = description, onClick = { showDialog = true })
    if (!showDialog) return

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        AutoBackup.setFolder(ctx, uri)
        refresh++
    }
    var interval by remember { mutableIntStateOf(AutoBackup.getInterval(ctx)) }
    var keep by remember { mutableIntStateOf(AutoBackup.getKeep(ctx)) }
    var password by remember { mutableStateOf("") }
    var hasPassword by remember { mutableStateOf(AutoBackup.hasPassword(ctx)) }
    var passwordError by remember { mutableStateOf(false) }

    ThreeButtonAlertDialog(
        onDismissRequest = { showDialog = false; refresh++ },
        onConfirmed = { },
        title = { Text(setting.title) },
        confirmButtonText = null,
        cancelButtonText = stringResource(R.string.dialog_close),
        neutralButtonText = if (AutoBackup.getFolder(ctx) != null) stringResource(R.string.auto_backup_now) else null,
        onNeutral = { AutoBackup.backupNow(ctx); refresh++ },
        scrollContent = true,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // folder
                Text(stringResource(R.string.auto_backup_folder), style = MaterialTheme.typography.titleSmall)
                Text(remember(refresh) { folderName(ctx, AutoBackup.getFolder(ctx)) } ?: stringResource(R.string.auto_backup_no_folder),
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { folderPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) }) {
                    Text(stringResource(R.string.auto_backup_choose_folder))
                }

                // interval
                Text(stringResource(R.string.auto_backup_interval), style = MaterialTheme.typography.titleSmall)
                listOf(
                    AutoBackup.INTERVAL_OFF to R.string.auto_backup_off,
                    AutoBackup.INTERVAL_DAILY to R.string.auto_backup_daily,
                    AutoBackup.INTERVAL_WEEKLY to R.string.auto_backup_weekly,
                ).forEach { (days, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                        .selectable(selected = interval == days) { interval = days; AutoBackup.setInterval(ctx, days) }) {
                        RadioButton(selected = interval == days, onClick = null)
                        Text(stringResource(text))
                    }
                }

                // number of backups
                Text(stringResource(R.string.auto_backup_keep), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 3, 5, 10).forEach { n ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.selectable(selected = keep == n) { keep = n; AutoBackup.setKeep(ctx, n) }) {
                            RadioButton(selected = keep == n, onClick = null)
                            Text(n.toString())
                        }
                    }
                }

                // password
                Text(stringResource(R.string.auto_backup_password), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(if (hasPassword) R.string.auto_backup_password_set else R.string.auto_backup_password_not_set),
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; passwordError = false },
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    isError = passwordError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row {
                    TextButton(enabled = password.isNotEmpty(), onClick = {
                        if (AutoBackup.setPassword(ctx, password)) { hasPassword = true; password = "" }
                        else passwordError = true
                    }) { Text(stringResource(R.string.auto_backup_password_save)) }
                    if (hasPassword) TextButton(onClick = { AutoBackup.setPassword(ctx, null); hasPassword = false }) {
                        Text(stringResource(R.string.auto_backup_password_remove))
                    }
                }
                Text(stringResource(R.string.auto_backup_explanation), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

private fun describe(ctx: android.content.Context): String {
    val folder = AutoBackup.getFolder(ctx)
    val interval = AutoBackup.getInterval(ctx)
    if (folder == null || interval == AutoBackup.INTERVAL_OFF) return ctx.getString(R.string.auto_backup_off)
    val base = ctx.getString(if (interval == AutoBackup.INTERVAL_DAILY) R.string.auto_backup_daily else R.string.auto_backup_weekly) +
        if (AutoBackup.hasPassword(ctx)) ", " + ctx.getString(R.string.auto_backup_encrypted) else ""
    AutoBackup.getLastError(ctx)?.let { return base + "\n" + ctx.getString(R.string.auto_backup_error, it) }
    val last = AutoBackup.getLastTime(ctx)
    return if (last == 0L) base
        else base + "\n" + ctx.getString(R.string.auto_backup_last, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last)))
}

private fun folderName(ctx: android.content.Context, uri: Uri?): String? {
    uri ?: return null
    return runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifEmpty { "/" } }.getOrNull()
}
