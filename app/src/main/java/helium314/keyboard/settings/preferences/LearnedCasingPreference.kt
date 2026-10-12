// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.LearnedCasing
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog

/** Shows the forms learned by [LearnedCasing], each can be removed */
@Composable
fun LearnedCasingPreference(setting: Setting) {
    val prefs = LocalContext.current.prefs()
    var showDialog by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val entries = remember(refresh, showDialog) { LearnedCasing.getAll(prefs) }
    val active = entries.count { it.count >= LearnedCasing.TIMES_TO_LEARN }
    Preference(
        name = setting.title,
        onClick = { showDialog = true },
        description = stringResource(R.string.learned_casing_summary, active, entries.size - active)
    )
    if (showDialog) {
        ThreeButtonAlertDialog(
            onDismissRequest = { showDialog = false },
            onConfirmed = { },
            title = { Text(setting.title) },
            confirmButtonText = null,
            cancelButtonText = stringResource(R.string.dialog_close),
            neutralButtonText = if (entries.isEmpty()) null else stringResource(R.string.learned_casing_forget_all),
            onNeutral = { LearnedCasing.clear(prefs); refresh++ },
            content = {
                if (entries.isEmpty()) {
                    Text(stringResource(R.string.learned_casing_empty))
                } else {
                    Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        entries.forEach { entry ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.form, style = MaterialTheme.typography.bodyLarge)
                                    if (entry.count < LearnedCasing.TIMES_TO_LEARN)
                                        Text(stringResource(R.string.learned_casing_not_yet), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { LearnedCasing.forget(prefs, entry.form); refresh++ }) {
                                    Icon(painterResource(R.drawable.ic_bin), stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}
