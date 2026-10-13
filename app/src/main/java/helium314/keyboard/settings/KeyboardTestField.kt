// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R

/**
 * Text field to try the keyboard directly in the settings, e.g. to see a changed theme
 * or key size without leaving the settings.
 */
@Composable
fun KeyboardTestField(modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.keyboard_test_field)) },
        supportingText = { Text(stringResource(R.string.keyboard_test_field_hint)) },
        trailingIcon = if (text.isEmpty()) null else {
            {
                IconButton(onClick = { text = "" }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.delete))
                }
            }
        },
        textStyle = contentTextDirectionStyle.merge(MaterialTheme.typography.bodyLarge),
        maxLines = 3,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
