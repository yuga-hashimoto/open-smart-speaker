package com.opendash.app.tool.system

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.view.KeyEvent
import com.opendash.app.tool.ToolCall
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.ToolParameter
import com.opendash.app.tool.ToolResult
import com.opendash.app.tool.ToolSchema
import timber.log.Timber

/**
 * Plays and controls music via the device's default music app — no Home
 * Assistant required.
 *
 * Dispatch strategy:
 * - `play` with a `query` → [MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH]
 *   (Spotify / YouTube Music / Play Music / Amazon Music all register for
 *   this).
 * - `play` without a query → [KeyEvent.KEYCODE_MEDIA_PLAY] (resume whatever
 *   was last playing on the focused media session).
 * - `pause` / `next` / `previous` → the matching `KEYCODE_MEDIA_*` event
 *   dispatched through [AudioManager.dispatchMediaKeyEvent], which targets
 *   the currently focused media session regardless of which app owns it.
 *
 * `intentFactory` and `keyEventDispatcher` are injected so unit tests can
 * verify dispatch without Robolectric.
 */
class NativeMediaPlayerToolExecutor(
    private val context: Context,
    private val intentFactory: (String) -> Intent = { query ->
        Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
        }
    },
    private val keyEventDispatcher: (Int) -> Boolean = { keyCode ->
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am == null) {
            false
        } else {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        }
    }
) : ToolExecutor {

    override suspend fun availableTools(): List<ToolSchema> = listOf(
        ToolSchema(
            name = "play_music",
            description = "Play or control music using the device's default music app " +
                "(Spotify, YouTube Music, etc.). Use action='play' with an optional " +
                "query to search and play a specific song/artist/album; omit the query " +
                "to resume. Use pause/next/previous to control the currently playing session.",
            parameters = mapOf(
                "action" to ToolParameter(
                    type = "string",
                    description = "One of play, pause, next, previous",
                    required = true,
                    enum = ACTIONS.toList()
                ),
                "query" to ToolParameter(
                    type = "string",
                    description = "Optional search query (song/artist/album). " +
                        "Used only with action='play'. Empty query = play anything.",
                    required = false
                )
            )
        )
    )

    override suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.name) {
                "play_music" -> executePlayMusic(call)
                else -> ToolResult(call.id, false, "", "Unknown tool: ${call.name}")
            }
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No app registered for MEDIA_PLAY_FROM_SEARCH")
            ToolResult(
                call.id,
                false,
                "",
                "No music app is installed to handle this request."
            )
        } catch (e: Exception) {
            Timber.e(e, "play_music failed")
            ToolResult(call.id, false, "", e.message ?: "play_music failed")
        }
    }

    private fun executePlayMusic(call: ToolCall): ToolResult {
        val action = (call.arguments["action"] as? String)?.trim()?.lowercase()
            ?: return ToolResult(call.id, false, "", "Missing action parameter")
        if (action !in ACTIONS) {
            return ToolResult(
                call.id,
                false,
                "",
                "Unsupported action '$action'. Expected one of: ${ACTIONS.joinToString()}"
            )
        }

        return when (action) {
            "play" -> dispatchPlay(call)
            "pause" -> dispatchKey(call, KeyEvent.KEYCODE_MEDIA_PAUSE, "pause")
            "next" -> dispatchKey(call, KeyEvent.KEYCODE_MEDIA_NEXT, "next")
            "previous" -> dispatchKey(call, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "previous")
            else -> ToolResult(call.id, false, "", "Unsupported action: $action")
        }
    }

    private fun dispatchPlay(call: ToolCall): ToolResult {
        val query = (call.arguments["query"] as? String)?.trim().orEmpty()
        if (query.isEmpty()) {
            return dispatchKey(call, KeyEvent.KEYCODE_MEDIA_PLAY, "play")
        }
        val intent = intentFactory(query).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Timber.d("play_music: started PLAY_FROM_SEARCH query='$query'")
        return ToolResult(
            call.id,
            true,
            """{"action":"play","query":${quoteJson(query)}}"""
        )
    }

    private fun dispatchKey(call: ToolCall, keyCode: Int, action: String): ToolResult {
        val ok = keyEventDispatcher(keyCode)
        return if (ok) {
            Timber.d("play_music: dispatched key event for action=$action")
            ToolResult(call.id, true, """{"action":"$action"}""")
        } else {
            ToolResult(
                call.id,
                false,
                "",
                "Audio service unavailable; could not dispatch $action."
            )
        }
    }

    private fun quoteJson(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    companion object {
        val ACTIONS: Set<String> = setOf("play", "pause", "next", "previous")
    }
}
