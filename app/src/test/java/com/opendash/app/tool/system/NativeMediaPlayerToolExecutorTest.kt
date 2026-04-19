package com.opendash.app.tool.system

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.opendash.app.tool.ToolCall
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class NativeMediaPlayerToolExecutorTest {

    private data class Harness(
        val executor: NativeMediaPlayerToolExecutor,
        val context: Context,
        val intent: Intent,
        val requestedQuery: () -> String?,
        val flagsSet: () -> List<Int>,
        val startedIntents: List<Intent>,
        val dispatchedKeys: List<Int>
    )

    private fun newHarness(keyDispatchOk: Boolean = true): Harness {
        val ctx = mockk<Context>(relaxed = true)
        val intent = mockk<Intent>(relaxed = true)
        var requestedQuery: String? = null
        val flagsSet = mutableListOf<Int>()
        val startedIntents = mutableListOf<Intent>()
        val dispatchedKeys = mutableListOf<Int>()

        every { intent.addFlags(any()) } answers {
            flagsSet.add(firstArg())
            intent
        }
        every { ctx.startActivity(any()) } answers {
            startedIntents.add(firstArg())
            Unit
        }

        val exec = NativeMediaPlayerToolExecutor(
            context = ctx,
            intentFactory = { query ->
                requestedQuery = query
                intent
            },
            keyEventDispatcher = { keyCode ->
                dispatchedKeys.add(keyCode)
                keyDispatchOk
            }
        )
        return Harness(
            exec, ctx, intent,
            { requestedQuery }, { flagsSet.toList() },
            startedIntents, dispatchedKeys
        )
    }

    @Test
    fun `availableTools exposes play_music with action and query params`() = runTest {
        val h = newHarness()
        val tools = h.executor.availableTools()
        assertThat(tools.map { it.name }).containsExactly("play_music")
        val params = tools.first().parameters
        assertThat(params["action"]).isNotNull()
        assertThat(params["action"]!!.required).isTrue()
        assertThat(params["query"]).isNotNull()
        assertThat(params["query"]!!.required).isFalse()
    }

    @Test
    fun `play with query dispatches PLAY_FROM_SEARCH intent with NEW_TASK flag`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(
                id = "1",
                name = "play_music",
                arguments = mapOf("action" to "play", "query" to "despacito")
            )
        )
        assertThat(result.success).isTrue()
        assertThat(h.requestedQuery()).isEqualTo("despacito")
        assertThat(h.flagsSet()).contains(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThat(h.startedIntents).hasSize(1)
        assertThat(h.dispatchedKeys).isEmpty()
    }

    @Test
    fun `play without query dispatches MEDIA_PLAY key event`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "1", name = "play_music", arguments = mapOf("action" to "play"))
        )
        assertThat(result.success).isTrue()
        assertThat(h.dispatchedKeys).containsExactly(KeyEvent.KEYCODE_MEDIA_PLAY)
        assertThat(h.startedIntents).isEmpty()
    }

    @Test
    fun `play with blank query is treated as no query`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(
                id = "1",
                name = "play_music",
                arguments = mapOf("action" to "play", "query" to "   ")
            )
        )
        assertThat(result.success).isTrue()
        assertThat(h.dispatchedKeys).containsExactly(KeyEvent.KEYCODE_MEDIA_PLAY)
        assertThat(h.startedIntents).isEmpty()
    }

    @Test
    fun `pause dispatches MEDIA_PAUSE key event`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "2", name = "play_music", arguments = mapOf("action" to "pause"))
        )
        assertThat(result.success).isTrue()
        assertThat(h.dispatchedKeys).containsExactly(KeyEvent.KEYCODE_MEDIA_PAUSE)
        assertThat(h.startedIntents).isEmpty()
    }

    @Test
    fun `next dispatches MEDIA_NEXT key event`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "3", name = "play_music", arguments = mapOf("action" to "next"))
        )
        assertThat(result.success).isTrue()
        assertThat(h.dispatchedKeys).containsExactly(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    @Test
    fun `previous dispatches MEDIA_PREVIOUS key event`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "4", name = "play_music", arguments = mapOf("action" to "previous"))
        )
        assertThat(result.success).isTrue()
        assertThat(h.dispatchedKeys).containsExactly(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    @Test
    fun `unknown action returns failure`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "play_music", arguments = mapOf("action" to "fast_forward"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Unsupported action")
        assertThat(h.startedIntents).isEmpty()
        assertThat(h.dispatchedKeys).isEmpty()
    }

    @Test
    fun `missing action returns failure`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "play_music", arguments = emptyMap())
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Missing action")
    }

    @Test
    fun `unknown tool name returns failure`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "some_other_tool", arguments = mapOf("action" to "play"))
        )
        assertThat(result.success).isFalse()
    }

    @Test
    fun `action is case-insensitive and trimmed`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(id = "x", name = "play_music", arguments = mapOf("action" to "  PAUSE  "))
        )
        assertThat(result.success).isTrue()
        assertThat(h.dispatchedKeys).containsExactly(KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    @Test
    fun `audio service unavailable returns failure`() = runTest {
        val h = newHarness(keyDispatchOk = false)
        val result = h.executor.execute(
            ToolCall(id = "x", name = "play_music", arguments = mapOf("action" to "pause"))
        )
        assertThat(result.success).isFalse()
        assertThat(result.error).contains("Audio service unavailable")
    }

    @Test
    fun `play query with quotes escaped in result`() = runTest {
        val h = newHarness()
        val result = h.executor.execute(
            ToolCall(
                id = "x",
                name = "play_music",
                arguments = mapOf("action" to "play", "query" to """a "quoted" song""")
            )
        )
        assertThat(result.success).isTrue()
        // The unescaped query reaches the intent factory untouched.
        assertThat(h.requestedQuery()).isEqualTo("""a "quoted" song""")
        // But the JSON result is valid (quotes escaped).
        assertThat(result.data).contains("""\"quoted\"""")
    }
}
