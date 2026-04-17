package com.opensmarthome.speaker.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.opensmarthome.speaker.MainActivity
import com.opensmarthome.speaker.R
import com.opensmarthome.speaker.assistant.routine.Routine
import com.opensmarthome.speaker.assistant.routine.RoutineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the top-N user routines as dynamic launcher shortcuts. Users
 * long-press the app icon to pin "Run morning routine" / "Run goodnight"
 * to the home screen without touching the speaker.
 *
 * Target click opens [MainActivity] with [EXTRA_ROUTINE_ID] — the activity
 * dispatches to the existing `run_routine` tool on resume.
 *
 * The launcher quota is 4-5 shortcuts on most devices (Google recommends ≤4);
 * we cap at [MAX_SHORTCUTS].
 */
@Singleton
class RoutineShortcutPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routines: RoutineRepository
) {

    companion object {
        const val EXTRA_ROUTINE_ID = "com.opensmarthome.speaker.extra.ROUTINE_ID"
        const val EXTRA_ROUTINE_NAME = "com.opensmarthome.speaker.extra.ROUTINE_NAME"
        private const val MAX_SHORTCUTS = 4
        private const val SHORTCUT_ID_PREFIX = "routine_"
    }

    /**
     * Rebuild the dynamic shortcut list from the current routine repo. Safe
     * to call repeatedly — replaces the entire dynamic set.
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val all = routines.all()
            val shortcuts = buildShortcuts(all.take(MAX_SHORTCUTS))
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
            Timber.d("Published ${shortcuts.size} routine shortcuts")
        } catch (e: Exception) {
            Timber.w(e, "Failed to publish routine shortcuts")
        }
    }

    internal fun buildShortcuts(source: List<Routine>): List<ShortcutInfoCompat> =
        source.map { routine ->
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_ROUTINE_ID, routine.id)
                putExtra(EXTRA_ROUTINE_NAME, routine.name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            ShortcutInfoCompat.Builder(context, SHORTCUT_ID_PREFIX + routine.id)
                .setShortLabel(routine.name.take(10).ifBlank { "Routine" })
                .setLongLabel(routine.name.ifBlank { "Run routine" })
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent)
                .build()
        }
}
