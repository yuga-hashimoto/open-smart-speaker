package com.opensmarthome.speaker.ui.settings.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.opensmarthome.speaker.R

/**
 * Settings row that lets the user pick a default weather city from a
 * searchable dropdown instead of typing the free-text string the previous
 * [com.opensmarthome.speaker.ui.settings.SettingsTextField] required.
 *
 * Structure mirrors
 * [com.opensmarthome.speaker.ui.settings.locale.LocalePickerRow] — a
 * clickable row that opens an [AlertDialog] hosting an
 * [OutlinedTextField] and a [LazyColumn] of candidate cities. Selection
 * persists through [WeatherLocationSettingsViewModel.applyLocation].
 *
 * Stolen from:
 * - dicio-android (free-text city input with auto fallback)
 * - home-assistant/android `EntityPicker` (debounced search + candidate list)
 */
@Composable
fun WeatherLocationPickerRow(
    modifier: Modifier = Modifier,
    viewModel: WeatherLocationSettingsViewModel = hiltViewModel()
) {
    val currentLocation by viewModel.currentLocation.collectAsState()
    val results by viewModel.queryResults.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val defaultLabel = stringResource(R.string.weather_location_picker_current_label_default)
    val currentLabel = currentLocation.ifEmpty { defaultLabel }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.padding(end = 16.dp)) {
            Text(
                text = stringResource(R.string.weather_location_picker_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = currentLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                query = ""
            },
            title = {
                Text(
                    stringResource(R.string.weather_location_picker_title),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { newValue ->
                            query = newValue
                            viewModel.updateQuery(newValue)
                        },
                        label = {
                            Text(stringResource(R.string.weather_location_picker_search_hint))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Reset row — lets the user clear their override without
                    // typing the word "Tokyo". Matches LocalePickerRow's
                    // "System default" affordance.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.applyLocation("")
                                showDialog = false
                                query = ""
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.weather_location_picker_use_default),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (query.isNotBlank() && results.isEmpty()) {
                        Text(
                            text = stringResource(R.string.weather_location_picker_no_results),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else if (results.isNotEmpty()) {
                        // Bounded height so the dialog doesn't devour the
                        // screen on tablets when the user searches a very
                        // common city name like "Tokyo".
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            items(
                                items = results,
                                key = { "${it.name}:${it.latitude}:${it.longitude}" }
                            ) { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.applyLocation(suggestion.displayLabel)
                                            showDialog = false
                                            query = ""
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = suggestion.displayLabel,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    query = ""
                }) {
                    Text(stringResource(R.string.weather_location_picker_close))
                }
            }
        )
    }
}
