package com.opendash.app.ui.ambient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.opendash.app.tool.system.TimerInfo

/**
 * Echo Show-style "active timers" card.
 *
 * Renders one row per active timer. Rows come in two variants:
 *  - **Counting down**: label (optional) + live mm:ss countdown + small ⨉
 *    icon to cancel.
 *  - **Firing** (alarm ringing): label + "TIME'S UP" headline + a large
 *    prominent "Stop" button. The row uses the error color scheme to draw
 *    attention. This is the only way for the user to silence the alarm
 *    short of the 5-minute safety cap.
 *
 * Renders nothing when [timers] is empty — the caller is expected to
 * include/exclude this composable based on the same list so we don't leak
 * an empty card into the layout.
 */
@Composable
fun ActiveTimersCard(
    timers: List<TimerInfo>,
    onCancelTimer: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (timers.isEmpty()) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Active timers",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(8.dp))
            timers.forEach { timer ->
                if (timer.isFiring) {
                    FiringTimerRow(
                        timer = timer,
                        onStop = { onCancelTimer(timer.id) }
                    )
                } else {
                    TimerRow(
                        timer = timer,
                        onCancel = { onCancelTimer(timer.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerRow(
    timer: TimerInfo,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val label = timer.label.takeIf { it.isNotBlank() }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
            }
            Text(
                text = formatRemaining(timer.remainingSeconds),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(
            onClick = onCancel,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Cancel timer ${timer.label.ifBlank { timer.id }}"
            )
        }
    }
}

/**
 * Row shown while an alarm is ringing. Uses the error color scheme to grab
 * attention and a large "Stop" button (minimum tap target ~56 dp) because
 * this is the escape hatch for a loud looping alarm — needs to be easy to
 * hit without thinking.
 */
@Composable
private fun FiringTimerRow(
    timer: TimerInfo,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorColors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = errorColors.errorContainer,
            contentColor = errorColors.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timer.label.ifBlank { "Timer" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = "Time's up",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = errorColors.error,
                    contentColor = errorColors.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text("Stop")
            }
        }
    }
}

/**
 * Format remaining seconds as mm:ss (or h:mm:ss when >= 1 hour).
 * Public so the call site can share one formatter with tests.
 */
internal fun formatRemaining(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3_600
    val minutes = (safe % 3_600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActiveTimersCardPreview() {
    ActiveTimersCard(
        timers = listOf(
            TimerInfo(id = "t1", label = "pasta", remainingSeconds = 185, totalSeconds = 300),
            TimerInfo(id = "t2", label = "", remainingSeconds = 5_400, totalSeconds = 7_200)
        ),
        onCancelTimer = {}
    )
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActiveTimersCardFiringPreview() {
    ActiveTimersCard(
        timers = listOf(
            TimerInfo(
                id = "t1",
                label = "pasta",
                remainingSeconds = 0,
                totalSeconds = 300,
                isFiring = true
            ),
            TimerInfo(id = "t2", label = "laundry", remainingSeconds = 420, totalSeconds = 1_800)
        ),
        onCancelTimer = {}
    )
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ActiveTimersCardEmptyPreview() {
    // Empty list — composable returns early, so preview renders blank by design.
    ActiveTimersCard(timers = emptyList(), onCancelTimer = {})
}
