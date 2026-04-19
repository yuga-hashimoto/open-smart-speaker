package com.opendash.app.tool.system

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SetAlarmToolExecutorTest {

    private val executor = SetAlarmToolExecutor(mockk<Context>(relaxed = true))

    @Test
    fun `availableTools exposes set_alarm and set_quick_timer`() = runTest {
        val names = executor.availableTools().map { it.name }
        assertThat(names).containsExactly("set_alarm", "set_quick_timer")
    }

    @Test
    fun `set_alarm schema has hour minute and label`() = runTest {
        val schema = executor.availableTools().first { it.name == "set_alarm" }
        assertThat(schema.parameters.keys).containsExactly("hour", "minute", "label")
        assertThat(schema.parameters["hour"]?.required).isTrue()
        assertThat(schema.parameters["minute"]?.required).isTrue()
        assertThat(schema.parameters["label"]?.required).isFalse()
    }

    @Test
    fun `set_quick_timer schema has seconds and label`() = runTest {
        val schema = executor.availableTools().first { it.name == "set_quick_timer" }
        assertThat(schema.parameters.keys).containsExactly("seconds", "label")
        assertThat(schema.parameters["seconds"]?.required).isTrue()
    }
}
