package com.opensmarthome.speaker.ui.settings.providers

import com.google.common.truth.Truth.assertThat
import com.opensmarthome.speaker.assistant.provider.AssistantProvider
import com.opensmarthome.speaker.assistant.provider.ProviderCapabilities
import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.assistant.router.RoutingPolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProvidersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rows reflect registered providers and highlight active`() = runTest {
        val local = fakeProvider("local", true)
        val remote = fakeProvider("remote", false)
        val router = mockk<ConversationRouter>()
        every { router.availableProviders } returns MutableStateFlow(listOf(local, remote))
        every { router.activeProvider } returns MutableStateFlow(local)
        every { router.policy } returns MutableStateFlow(RoutingPolicy.Auto)

        val vm = ProvidersViewModel(router)
        advanceUntilIdle()

        val rows = vm.rows.value
        assertThat(rows).hasSize(2)
        assertThat(rows.first { it.id == "local" }.isActive).isTrue()
        assertThat(rows.first { it.id == "local" }.isLocal).isTrue()
        assertThat(rows.first { it.id == "remote" }.isActive).isFalse()
        assertThat(rows.first { it.id == "remote" }.isLocal).isFalse()
    }

    @Test
    fun `select calls router selectProvider`() = runTest {
        val router = mockk<ConversationRouter>(relaxed = true)
        every { router.availableProviders } returns MutableStateFlow(emptyList())
        every { router.activeProvider } returns MutableStateFlow(null)
        coEvery { router.selectProvider(any()) } returns Unit

        val vm = ProvidersViewModel(router)
        vm.select("remote")
        advanceUntilIdle()

        coVerify { router.selectProvider("remote") }
    }

    private fun fakeProvider(id: String, isLocal: Boolean): AssistantProvider {
        val provider = mockk<AssistantProvider>()
        every { provider.id } returns id
        every { provider.displayName } returns id.replaceFirstChar { it.uppercase() }
        every { provider.capabilities } returns ProviderCapabilities(
            supportsStreaming = true,
            supportsTools = true,
            maxContextTokens = 8192,
            modelName = "$id-model",
            isLocal = isLocal
        )
        return provider
    }
}
