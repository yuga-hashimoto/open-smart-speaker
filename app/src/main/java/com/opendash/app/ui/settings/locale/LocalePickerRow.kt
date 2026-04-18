package com.opendash.app.ui.settings.locale

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opendash.app.R

/**
 * Settings row that lets the user pick a UI language. On Android 13+ the
 * selection is pushed to the platform's per-app locale service so the
 * change takes effect without a restart; on API 28-32 the row renders
 * disabled with a "Requires Android 13+" hint (see PR body for UX note).
 *
 * Invoked from [com.opendash.app.ui.settings.SettingsScreen] —
 * uses its own [hiltViewModel] so the locale row stays decoupled from
 * the much larger SettingsViewModel graph.
 */
@Composable
fun LocalePickerRow(
    modifier: Modifier = Modifier,
    viewModel: LocaleSettingsViewModel = hiltViewModel()
) {
    val currentTag by viewModel.currentTag.collectAsState()
    val options = viewModel.options
    val supported = viewModel.isOverrideSupported

    var showDialog by remember { mutableStateOf(false) }

    val systemDefaultLabel = stringResource(R.string.locale_picker_system_default)
    val currentLabel = remember(currentTag, options, systemDefaultLabel) {
        options.firstOrNull { it.tag == currentTag }?.label ?: systemDefaultLabel
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (supported) it.clickable { showDialog = true } else it }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.padding(end = 16.dp)) {
            Text(
                text = stringResource(R.string.locale_picker_title),
                style = MaterialTheme.typography.bodyLarge,
                color = if (supported) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!supported) {
                Text(
                    text = stringResource(R.string.locale_picker_requires_android_13),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = currentLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.locale_picker_title), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.applyLocale(option.tag)
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = option.tag == currentTag,
                                onClick = {
                                    viewModel.applyLocale(option.tag)
                                    showDialog = false
                                }
                            )
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.locale_picker_close)) }
            }
        )
    }
}
