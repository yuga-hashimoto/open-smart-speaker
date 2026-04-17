package com.opensmarthome.speaker.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.ui.theme.DeviceMediaPlaying
import com.opensmarthome.speaker.ui.theme.SpeakerSurfaceElevated
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary

/** HA `repeat` attribute: off / one (current track) / all (queue). */
enum class RepeatMode(val haValue: String) {
    OFF("off"), ONE("one"), ALL("all");

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    companion object {
        fun fromHa(raw: String?): RepeatMode = when (raw?.lowercase()) {
            "one" -> ONE
            "all" -> ALL
            else -> OFF
        }
    }
}

data class NowPlayingInfo(
    val deviceId: String,
    val deviceName: String,
    val mediaTitle: String?,
    val mediaArtist: String?,
    val isPlaying: Boolean,
    /** Current volume on 0.0-1.0 scale, matching HA `volume_level` attribute. Null = unknown. */
    val volumeLevel: Float? = null,
    /** HA `shuffle` attribute. Null = not reported. */
    val shuffle: Boolean? = null,
    /** HA `repeat` attribute. Null = not reported. */
    val repeatMode: RepeatMode? = null,
    /** HA `media_playlist` attribute — the current playlist/source label. Null = not reported. */
    val playlist: String? = null,
    /**
     * HA `source_list` attribute — available input sources for this media player.
     * Best-effort; many integrations don't report this, in which case the list is empty.
     */
    val sources: List<String> = emptyList()
)

/** Service actions the bar dispatches to its host. Names match HA media_player services. */
enum class MediaAction(val haService: String) {
    PLAY("media_play"),
    PAUSE("media_pause"),
    NEXT("media_next_track"),
    PREVIOUS("media_previous_track")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingBar(
    nowPlaying: NowPlayingInfo,
    onMediaAction: (MediaAction) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onShuffleToggle: (Boolean) -> Unit = {},
    onRepeatChange: (RepeatMode) -> Unit = {},
    onSourceSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSourceSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = SpeakerSurfaceElevated.copy(alpha = 0.85f),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = DeviceMediaPlaying,
                    modifier = Modifier.size(24.dp)
                )
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = nowPlaying.mediaTitle ?: "Unknown",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpeakerTextPrimary,
                        maxLines = 1
                    )
                    if (nowPlaying.mediaArtist != null) {
                        Text(
                            text = nowPlaying.mediaArtist,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpeakerTextSecondary,
                            maxLines = 1
                        )
                    }
                }
                nowPlaying.shuffle?.let { enabled ->
                    IconButton(onClick = { onShuffleToggle(!enabled) }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = if (enabled) "Shuffle on" else "Shuffle off",
                            tint = if (enabled) DeviceMediaPlaying else SpeakerTextSecondary
                        )
                    }
                }
                nowPlaying.repeatMode?.let { mode ->
                    IconButton(onClick = { onRepeatChange(mode.next()) }) {
                        Icon(
                            imageVector = if (mode == RepeatMode.ONE) Icons.Filled.RepeatOne
                            else Icons.Filled.Repeat,
                            contentDescription = "Repeat ${mode.haValue}",
                            tint = if (mode == RepeatMode.OFF) SpeakerTextSecondary else DeviceMediaPlaying
                        )
                    }
                }
                IconButton(onClick = { onMediaAction(MediaAction.PREVIOUS) }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = SpeakerTextPrimary)
                }
                IconButton(onClick = {
                    onMediaAction(if (nowPlaying.isPlaying) MediaAction.PAUSE else MediaAction.PLAY)
                }) {
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
                        tint = SpeakerTextPrimary
                    )
                }
                IconButton(onClick = { onMediaAction(MediaAction.NEXT) }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = SpeakerTextPrimary)
                }
            }
            if (nowPlaying.volumeLevel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val volume = nowPlaying.volumeLevel.coerceIn(0f, 1f)
                    Icon(
                        imageVector = if (volume <= 0f) Icons.AutoMirrored.Filled.VolumeOff
                        else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = SpeakerTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Slider(
                        value = volume,
                        onValueChange = { onVolumeChange(it.coerceIn(0f, 1f)) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = DeviceMediaPlaying,
                            activeTrackColor = DeviceMediaPlaying
                        )
                    )
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpeakerTextSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (nowPlaying.playlist != null || nowPlaying.sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (nowPlaying.playlist != null) {
                        Text(
                            text = "Now: ${nowPlaying.playlist}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpeakerTextSecondary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    IconButton(
                        onClick = { showSourceSheet = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = "Show source list",
                            tint = SpeakerTextSecondary
                        )
                    }
                }
            }
        }
    }

    if (showSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSourceSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.titleMedium,
                    color = SpeakerTextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (nowPlaying.sources.isEmpty()) {
                    Text(
                        text = "No source list from this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpeakerTextSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    nowPlaying.sources.forEach { source ->
                        Text(
                            text = source,
                            style = MaterialTheme.typography.bodyLarge,
                            color = SpeakerTextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSourceSelected(source)
                                    showSourceSheet = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
