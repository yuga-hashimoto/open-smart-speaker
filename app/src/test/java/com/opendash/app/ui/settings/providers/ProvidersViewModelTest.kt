package com.opendash.app.ui.settings.providers

import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.opendash.app.assistant.provider.AssistantProvider
import com.opendash.app.assistant.provider.ProviderCapabilities
import com.opendash.app.assistant.router.ConversationRouter
import com.opendash.app.assistant.router.RoutingPolicy
import com.opendash.app.data.preferences.AppPreferences
import com.opendash.app.data.preferences.PreferenceKeys
import com.opendash.app.data.preferences.SecurePreferences
import com.opendash.app.util.DiscoveredSpeaker
import com.opendash.app.util.MulticastDiscovery
import com.opendash.app.util.PeerLivenessTracker
import com.opendash.app.util.Staleness
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

        val vm = buildVm(router = router)
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

        val vm = buildVm(router = router)
        vm.select("remote")
        advanceUntilIdle()

        coVerify { router.selectProvider("remote") }
    }

    @Test
    fun `multiroomState reports broadcast off when preference absent`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        val state = vm.multiroomState.value
        assertThat(state.broadcastEnabled).isFalse()
        assertThat(state.hasSecret).isFalse()
        assertThat(state.broadcastingAs).isNull()
        assertThat(state.peerCount).isEqualTo(0)
    }

    @Test
    fun `multiroomState reports healthy mesh when broadcast + secret + fresh peer`() = runTest {
        val router = idleRouter()
        val prefs = prefsWith(Pair(PreferenceKeys.MULTIROOM_BROADCAST_ENABLED, true))
        val secure = mockk<SecurePreferences>()
        every { secure.getString(SecurePreferences.KEY_MULTIROOM_SECRET, any()) } returns "s3cret"
        val discovery = mockk<MulticastDiscovery>()
        every { discovery.registeredName } returns MutableStateFlow("living-room")
        every { discovery.speakers } returns MutableStateFlow(
            listOf(DiscoveredSpeaker("kitchen"), DiscoveredSpeaker("bedroom"))
        )
        val tracker = PeerLivenessTracker().apply {
            update(
                mapOf(
                    "kitchen" to Staleness.Fresh,
                    "bedroom" to Staleness.Stale
                )
            )
        }

        val vm = ProvidersViewModel(router, prefs, secure, discovery, tracker)
        advanceUntilIdle()

        val state = vm.multiroomState.value
        assertThat(state.broadcastEnabled).isTrue()
        assertThat(state.hasSecret).isTrue()
        assertThat(state.broadcastingAs).isEqualTo("living-room")
        assertThat(state.peerCount).isEqualTo(2)
        assertThat(state.freshCount).isEqualTo(1)
        assertThat(state.staleCount).isEqualTo(1)
        assertThat(state.goneCount).isEqualTo(0)
    }

    @Test
    fun `multiroomState hides broadcastingAs when broadcast is off`() = runTest {
        val router = idleRouter()
        val prefs = prefsWith(Pair(PreferenceKeys.MULTIROOM_BROADCAST_ENABLED, false))
        val secure = mockk<SecurePreferences>()
        every { secure.getString(SecurePreferences.KEY_MULTIROOM_SECRET, any()) } returns ""
        val discovery = mockk<MulticastDiscovery>()
        every { discovery.registeredName } returns MutableStateFlow("living-room")
        every { discovery.speakers } returns MutableStateFlow(emptyList())
        val tracker = PeerLivenessTracker()

        val vm = ProvidersViewModel(router, prefs, secure, discovery, tracker)
        advanceUntilIdle()

        assertThat(vm.multiroomState.value.broadcastingAs).isNull()
    }

    @Test
    fun `multiroomState falls back to mDNS speakers when liveness empty`() = runTest {
        val router = idleRouter()
        val prefs = prefsWith(Pair(PreferenceKeys.MULTIROOM_BROADCAST_ENABLED, true))
        val secure = mockk<SecurePreferences>()
        every { secure.getString(SecurePreferences.KEY_MULTIROOM_SECRET, any()) } returns "x"
        val discovery = mockk<MulticastDiscovery>()
        every { discovery.registeredName } returns MutableStateFlow("foo")
        every { discovery.speakers } returns MutableStateFlow(
            listOf(DiscoveredSpeaker("a"), DiscoveredSpeaker("b"), DiscoveredSpeaker("c"))
        )
        val tracker = PeerLivenessTracker() // no heartbeats yet

        val vm = ProvidersViewModel(router, prefs, secure, discovery, tracker)
        advanceUntilIdle()

        assertThat(vm.multiroomState.value.peerCount).isEqualTo(3)
        assertThat(vm.multiroomState.value.freshCount).isEqualTo(0)
    }

    @Test
    fun `meshHealthHint prioritises broadcast off`() {
        val state = ProvidersViewModel.MultiroomState(
            broadcastEnabled = false,
            hasSecret = true,
            broadcastingAs = null,
            peerCount = 3,
            freshCount = 2,
            staleCount = 0,
            goneCount = 1
        )
        val hint = meshHealthHint(state)
        assertThat(hint.healthy).isFalse()
        assertThat(hint.messageRes).isEqualTo(com.opendash.app.R.string.multiroom_hint_broadcast_off)
    }

    @Test
    fun `meshHealthHint prompts for secret when broadcast on but secret missing`() {
        val state = ProvidersViewModel.MultiroomState(
            broadcastEnabled = true,
            hasSecret = false,
            broadcastingAs = "foo",
            peerCount = 1,
            freshCount = 1,
            staleCount = 0,
            goneCount = 0
        )
        val hint = meshHealthHint(state)
        assertThat(hint.healthy).isFalse()
        assertThat(hint.messageRes).isEqualTo(com.opendash.app.R.string.multiroom_hint_no_secret)
    }

    @Test
    fun `meshHealthHint prompts for peers when broadcast and secret set but no fresh`() {
        val state = ProvidersViewModel.MultiroomState(
            broadcastEnabled = true,
            hasSecret = true,
            broadcastingAs = "foo",
            peerCount = 1,
            freshCount = 0,
            staleCount = 0,
            goneCount = 1
        )
        val hint = meshHealthHint(state)
        assertThat(hint.healthy).isFalse()
        assertThat(hint.messageRes).isEqualTo(com.opendash.app.R.string.multiroom_hint_no_peers)
    }

    @Test
    fun `meshHealthHint reports healthy when all checks pass`() {
        val state = ProvidersViewModel.MultiroomState(
            broadcastEnabled = true,
            hasSecret = true,
            broadcastingAs = "foo",
            peerCount = 2,
            freshCount = 2,
            staleCount = 0,
            goneCount = 0
        )
        val hint = meshHealthHint(state)
        assertThat(hint.healthy).isTrue()
        assertThat(hint.messageRes).isEqualTo(com.opendash.app.R.string.multiroom_hint_healthy)
    }

    private fun buildVm(
        router: ConversationRouter = idleRouter(),
        prefs: AppPreferences = prefsWith(),
        secure: SecurePreferences = emptySecurePrefs(),
        discovery: MulticastDiscovery = emptyDiscovery(),
        tracker: PeerLivenessTracker = PeerLivenessTracker()
    ) = ProvidersViewModel(router, prefs, secure, discovery, tracker)

    private fun idleRouter(): ConversationRouter {
        val router = mockk<ConversationRouter>(relaxed = true)
        every { router.availableProviders } returns MutableStateFlow(emptyList())
        every { router.activeProvider } returns MutableStateFlow(null)
        return router
    }

    private fun emptySecurePrefs(): SecurePreferences {
        val secure = mockk<SecurePreferences>()
        every { secure.getString(any(), any()) } returns ""
        return secure
    }

    private fun emptyDiscovery(): MulticastDiscovery {
        val d = mockk<MulticastDiscovery>()
        every { d.registeredName } returns MutableStateFlow<String?>(null)
        every { d.speakers } returns MutableStateFlow(emptyList())
        return d
    }

    private fun prefsWith(vararg entries: Pair<Preferences.Key<*>, Any?>): AppPreferences {
        val map = entries.toMap()
        val prefs = mockk<AppPreferences>()
        every { prefs.observe<Any>(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val key = firstArg<Preferences.Key<Any>>()
            flowOf(map[key]) as Flow<Any?>
        }
        return prefs
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
