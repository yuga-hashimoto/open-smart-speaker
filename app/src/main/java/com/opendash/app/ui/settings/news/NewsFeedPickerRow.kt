package com.opendash.app.ui.settings.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.opendash.app.tool.info.BundledFeed
import com.opendash.app.tool.info.NewsFeedSource

/**
 * Settings row that lets the user pick which RSS feed the Home
 * dashboard headlines card pulls from. Also shared by the `get_news`
 * tool as its fallback when the utterance doesn't name a specific
 * source.
 *
 * Mirrors [com.opendash.app.ui.settings.locale.LocalePickerRow]
 * exactly — a clickable row that opens an AlertDialog whose body is a
 * LazyColumn of bundled feeds grouped by source ("NHK" / "BBC" /
 * "Other"), plus a "Custom URL" section at the bottom with an inline
 * text field.
 */
@Composable
fun NewsFeedPickerRow(
    modifier: Modifier = Modifier,
    viewModel: NewsFeedSettingsViewModel = hiltViewModel()
) {
    val currentUrl by viewModel.currentFeedUrl.collectAsState()
    val currentLabel by viewModel.currentLabel.collectAsState()
    val feeds = viewModel.bundled

    var showDialog by remember { mutableStateOf(false) }

    // Pre-resolve every bundled feed's translated label — stringResource
    // can only be invoked from composable context, so we build the
    // lookup map here and pass the result into the remember block below.
    val bundledLabels: Map<String, String> = feeds.associate { feed ->
        feed.url to stringResource(feed.labelResId)
    }
    val defaultLabel = stringResource(R.string.news_feed_nhk_general)
    val customLabelFallback = stringResource(R.string.news_feed_custom)

    val displayLabel = remember(currentUrl, currentLabel, defaultLabel, customLabelFallback, bundledLabels) {
        when {
            // User-authored label wins — covers custom feeds and any
            // manual override set by a future voice command.
            currentLabel.isNotBlank() -> currentLabel
            // Nothing picked yet: show the built-in default (NHK 総合).
            currentUrl.isBlank() -> defaultLabel
            // URL matches a bundled feed: reuse its translated label.
            bundledLabels.containsKey(currentUrl) -> bundledLabels.getValue(currentUrl)
            // Custom URL with no stored label: localized "Custom URL".
            else -> customLabelFallback
        }
    }

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
                text = stringResource(R.string.news_feed_picker_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        NewsFeedPickerDialog(
            feeds = feeds,
            currentUrl = currentUrl,
            onSelectBundled = { feed, label ->
                viewModel.applyBundled(feed, label)
                showDialog = false
            },
            onApplyCustom = { url, label ->
                viewModel.applyCustom(url, label)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun NewsFeedPickerDialog(
    feeds: List<BundledFeed>,
    currentUrl: String,
    onSelectBundled: (BundledFeed, String) -> Unit,
    onApplyCustom: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val customLabel = stringResource(R.string.news_feed_custom)
    val customHint = stringResource(R.string.news_feed_picker_custom_url_hint)

    // Pre-resolve labels so grouping + selection can happen without
    // dragging stringResource into non-Composable code.
    val labeled = feeds.map { it to stringResource(it.labelResId) }

    // When the current URL is a bundled one, the custom-URL row is
    // collapsed by default; when it's a non-bundled URL the row opens
    // pre-populated so the user can edit in place.
    val isCustomSelected = remember(feeds, currentUrl) {
        currentUrl.isNotBlank() && feeds.none { it.url == currentUrl }
    }
    var customExpanded by remember(isCustomSelected) { mutableStateOf(isCustomSelected) }
    var customUrl by remember(currentUrl, isCustomSelected) {
        mutableStateOf(if (isCustomSelected) currentUrl else "")
    }

    val nhkHeader = stringResource(R.string.news_feed_category_nhk)
    val bbcHeader = stringResource(R.string.news_feed_category_bbc)
    val otherHeader = stringResource(R.string.news_feed_category_other)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.news_feed_picker_title),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 480.dp)
            ) {
                val nhk = labeled.filter { it.first.source == NewsFeedSource.Nhk }
                val bbc = labeled.filter { it.first.source == NewsFeedSource.Bbc }
                val other = labeled.filter { it.first.source == NewsFeedSource.Other }

                if (nhk.isNotEmpty()) {
                    item(key = "hdr_nhk") { CategoryHeader(text = nhkHeader) }
                    items(items = nhk, key = { "nhk_${it.first.id}" }) { (feed, label) ->
                        FeedOptionRow(
                            label = label,
                            selected = feed.url == currentUrl,
                            onClick = { onSelectBundled(feed, label) }
                        )
                    }
                }

                if (bbc.isNotEmpty()) {
                    item(key = "hdr_bbc") { CategoryHeader(text = bbcHeader) }
                    items(items = bbc, key = { "bbc_${it.first.id}" }) { (feed, label) ->
                        FeedOptionRow(
                            label = label,
                            selected = feed.url == currentUrl,
                            onClick = { onSelectBundled(feed, label) }
                        )
                    }
                }

                if (other.isNotEmpty()) {
                    item(key = "hdr_other") { CategoryHeader(text = otherHeader) }
                    items(items = other, key = { "other_${it.first.id}" }) { (feed, label) ->
                        FeedOptionRow(
                            label = label,
                            selected = feed.url == currentUrl,
                            onClick = { onSelectBundled(feed, label) }
                        )
                    }
                }

                item(key = "custom_toggle") {
                    FeedOptionRow(
                        label = customLabel,
                        selected = isCustomSelected || customExpanded,
                        onClick = { customExpanded = !customExpanded }
                    )
                }

                if (customExpanded) {
                    item(key = "custom_input") {
                        Column(modifier = Modifier.padding(start = 32.dp, top = 4.dp, end = 8.dp)) {
                            OutlinedTextField(
                                value = customUrl,
                                onValueChange = { customUrl = it },
                                label = {
                                    Text(
                                        text = customHint,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    val trimmed = customUrl.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onApplyCustom(trimmed, customLabel)
                                    }
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.news_feed_picker_custom_url),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.news_feed_picker_close))
            }
        }
    )
}

@Composable
private fun CategoryHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun FeedOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
