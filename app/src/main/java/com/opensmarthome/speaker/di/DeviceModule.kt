package com.opensmarthome.speaker.di

import com.opensmarthome.speaker.device.DeviceManager
import com.opensmarthome.speaker.device.provider.DeviceProvider
import com.opensmarthome.speaker.device.provider.homeassistant.HomeAssistantDeviceProvider
import com.opensmarthome.speaker.device.provider.matter.MatterDeviceProvider
import com.opensmarthome.speaker.device.provider.mqtt.MqttClientWrapper
import com.opensmarthome.speaker.device.provider.mqtt.MqttConfig
import com.opensmarthome.speaker.device.provider.mqtt.MqttDeviceProvider
import com.opensmarthome.speaker.device.provider.switchbot.SwitchBotApiClient
import com.opensmarthome.speaker.device.provider.switchbot.SwitchBotConfig
import com.opensmarthome.speaker.device.provider.switchbot.SwitchBotDeviceProvider
import android.content.Context
import com.opensmarthome.speaker.device.tool.DeviceToolExecutor
import com.opensmarthome.speaker.homeassistant.client.HomeAssistantClient
import com.opensmarthome.speaker.tool.CompositeToolExecutor
import com.opensmarthome.speaker.tool.ToolExecutor
import com.opensmarthome.speaker.tool.info.CalculatorToolExecutor
import com.opensmarthome.speaker.tool.info.CurrencyToolExecutor
import com.opensmarthome.speaker.voice.fastpath.DefaultFastPathRouter
import com.opensmarthome.speaker.voice.fastpath.FastPathRouter
import com.opensmarthome.speaker.tool.info.DuckDuckGoSearchProvider
import com.opensmarthome.speaker.tool.info.HtmlWebFetcher
import com.opensmarthome.speaker.tool.info.InMemoryKnowledgeStore
import com.opensmarthome.speaker.tool.info.KnowledgeToolExecutor
import com.opensmarthome.speaker.tool.info.NewsToolExecutor
import com.opensmarthome.speaker.tool.info.OpenMeteoWeatherProvider
import com.opensmarthome.speaker.tool.info.RssNewsProvider
import com.opensmarthome.speaker.tool.info.SearchToolExecutor
import com.opensmarthome.speaker.tool.info.UnitConverterToolExecutor
import com.opensmarthome.speaker.tool.info.WeatherToolExecutor
import com.opensmarthome.speaker.tool.info.WebFetchToolExecutor
import com.opensmarthome.speaker.assistant.skills.AssetSkillLoader
import com.opensmarthome.speaker.assistant.skills.FileSystemSkillLoader
import com.opensmarthome.speaker.assistant.skills.SkillInstaller
import com.opensmarthome.speaker.assistant.skills.SkillRegistry
import com.opensmarthome.speaker.assistant.skills.SkillToolExecutor
import com.opensmarthome.speaker.assistant.routine.RoomRoutineStore
import com.opensmarthome.speaker.assistant.routine.RoutineToolExecutor
import com.opensmarthome.speaker.tool.accessibility.AccessibilityScreenReader
import com.opensmarthome.speaker.tool.a11y.ReadActiveScreenToolExecutor
import com.opensmarthome.speaker.tool.a11y.ScrollScreenToolExecutor
import com.opensmarthome.speaker.tool.a11y.TapByTextToolExecutor
import com.opensmarthome.speaker.tool.a11y.TypeTextToolExecutor
import com.opensmarthome.speaker.tool.accessibility.ScreenToolExecutor
import com.opensmarthome.speaker.data.db.DocumentChunkDao
import com.opensmarthome.speaker.data.db.ToolUsageDao
import com.opensmarthome.speaker.tool.analytics.PersistentToolUsageStats
import com.opensmarthome.speaker.tool.analytics.ToolUsageRecorder
import com.opensmarthome.speaker.data.db.MemoryDao
import com.opensmarthome.speaker.data.db.RoutineDao
import com.opensmarthome.speaker.tool.memory.MemoryToolExecutor
import com.opensmarthome.speaker.tool.rag.RagService
import com.opensmarthome.speaker.tool.rag.RagToolExecutor
import com.opensmarthome.speaker.tool.system.AndroidAppLauncher
import com.opensmarthome.speaker.tool.system.AndroidCalendarProvider
import com.opensmarthome.speaker.tool.system.AndroidContactsProvider
import com.opensmarthome.speaker.tool.system.AndroidDeviceHealthProvider
import com.opensmarthome.speaker.tool.system.AndroidLocationProvider
import com.opensmarthome.speaker.tool.system.AndroidNotificationProvider
import com.opensmarthome.speaker.tool.system.AndroidPhotosProvider
import com.opensmarthome.speaker.tool.system.AndroidSmsSender
import com.opensmarthome.speaker.tool.system.AndroidTimerManager
import com.opensmarthome.speaker.tool.system.AndroidVolumeManager
import com.opensmarthome.speaker.tool.system.CalendarToolExecutor
import com.opensmarthome.speaker.tool.system.CameraProviderHolder
import com.opensmarthome.speaker.tool.system.CameraToolExecutor
import com.opensmarthome.speaker.tool.system.ContactsToolExecutor
import com.opensmarthome.speaker.tool.system.DeviceHealthToolExecutor
import com.opensmarthome.speaker.tool.system.LocationToolExecutor
import com.opensmarthome.speaker.tool.system.NotificationReplyToolExecutor
import com.opensmarthome.speaker.tool.system.NotificationToolExecutor
import com.opensmarthome.speaker.tool.system.OpenSettingsToolExecutor
import com.opensmarthome.speaker.tool.system.OpenUrlToolExecutor
import com.opensmarthome.speaker.tool.system.PhotosToolExecutor
import com.opensmarthome.speaker.tool.system.ScreenRecorderHolder
import com.opensmarthome.speaker.tool.system.ScreenRecorderToolExecutor
import com.opensmarthome.speaker.tool.system.SmsToolExecutor
import com.opensmarthome.speaker.tool.system.SystemToolExecutor
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeviceModule {

    @Provides
    @IntoSet
    fun provideHomeAssistantDeviceProvider(
        haClient: HomeAssistantClient
    ): DeviceProvider = HomeAssistantDeviceProvider(haClient)

    @Provides
    @IntoSet
    fun provideMatterDeviceProvider(): DeviceProvider = MatterDeviceProvider()

    @Provides
    @IntoSet
    fun provideSwitchBotDeviceProvider(
        client: OkHttpClient,
        moshi: Moshi
    ): DeviceProvider = SwitchBotDeviceProvider(
        SwitchBotApiClient(client, moshi, SwitchBotConfig())
    )

    @Provides
    @IntoSet
    fun provideMqttDeviceProvider(moshi: Moshi): DeviceProvider =
        MqttDeviceProvider(MqttClientWrapper(MqttConfig()), moshi)

    @Provides
    @Singleton
    fun provideDeviceManager(
        providers: Set<@JvmSuppressWildcards DeviceProvider>
    ): DeviceManager = DeviceManager(providers)

    @Provides
    @Singleton
    fun provideSkillRegistry(@ApplicationContext context: Context): SkillRegistry {
        val registry = SkillRegistry()
        // Bundled skills from assets
        registry.registerAll(AssetSkillLoader(context).loadAll())
        // User-installed skills from internal storage
        val userSkillsDir = java.io.File(context.filesDir, "skills").apply { mkdirs() }
        registry.registerAll(FileSystemSkillLoader(userSkillsDir).loadAll())
        return registry
    }

    @Provides
    @Singleton
    fun provideCameraProviderHolder(): CameraProviderHolder = CameraProviderHolder()

    @Provides
    @Singleton
    fun provideTimerManager(
        @ApplicationContext context: Context
    ): com.opensmarthome.speaker.tool.system.TimerManager =
        AndroidTimerManager(context)

    @Provides
    @Singleton
    fun provideNotificationProvider(
        @ApplicationContext context: Context
    ): com.opensmarthome.speaker.tool.system.NotificationProvider =
        AndroidNotificationProvider(context)

    @Provides
    @Singleton
    fun provideAmbientSnapshotBuilder(
        timerManager: com.opensmarthome.speaker.tool.system.TimerManager,
        notificationProvider: com.opensmarthome.speaker.tool.system.NotificationProvider
    ): com.opensmarthome.speaker.ui.ambient.AmbientSnapshotBuilder =
        com.opensmarthome.speaker.ui.ambient.AmbientSnapshotBuilder(
            timerManager = timerManager,
            notificationProvider = notificationProvider
        )

    @Provides
    @Singleton
    fun provideFastPathRouter(
        batteryMonitor: com.opensmarthome.speaker.util.BatteryMonitor
    ): FastPathRouter = DefaultFastPathRouter(
        matchers = DefaultFastPathRouter.DEFAULT_MATCHERS +
            com.opensmarthome.speaker.voice.fastpath.BatteryMatcher(batteryMonitor)
    )

    @Provides
    @Singleton
    fun provideAnnouncementParser(
        moshi: Moshi,
        securePreferences: com.opensmarthome.speaker.data.preferences.SecurePreferences
    ): com.opensmarthome.speaker.multiroom.AnnouncementParser =
        com.opensmarthome.speaker.multiroom.AnnouncementParser(
            moshi = moshi,
            sharedSecretProvider = {
                securePreferences.getString(
                    com.opensmarthome.speaker.data.preferences.SecurePreferences.KEY_MULTIROOM_SECRET
                ).takeIf { it.isNotBlank() }
            }
        )

    @Provides
    @Singleton
    fun provideAnnouncementClient(): com.opensmarthome.speaker.multiroom.AnnouncementClient =
        com.opensmarthome.speaker.multiroom.AnnouncementClient()

    @Provides
    @Singleton
    fun provideAnnouncementDispatcher(
        tts: com.opensmarthome.speaker.voice.tts.TextToSpeech,
        timerManager: com.opensmarthome.speaker.tool.system.TimerManager,
        announcementState: com.opensmarthome.speaker.multiroom.AnnouncementState,
        peerLivenessTracker: com.opensmarthome.speaker.multiroom.PeerLivenessTracker
    ): com.opensmarthome.speaker.multiroom.AnnouncementDispatcher =
        com.opensmarthome.speaker.multiroom.AnnouncementDispatcher(
            tts = tts,
            // TODO(P17.5 follow-up): wire historyProvider to the live
            // ConversationHistoryManager once VoicePipeline exposes it so
            // session_handoff messages can actually seed future turns.
            historyProvider = { null },
            timerManagerProvider = { timerManager },
            announcementState = announcementState,
            onHeartbeat = { envelope -> peerLivenessTracker.onHeartbeat(envelope) }
        )

    @Provides
    @Singleton
    fun provideAnnouncementBroadcaster(
        discovery: com.opensmarthome.speaker.util.MulticastDiscovery,
        client: com.opensmarthome.speaker.multiroom.AnnouncementClient,
        webSocketClient: com.opensmarthome.speaker.multiroom.AnnouncementWebSocketClient,
        securePreferences: com.opensmarthome.speaker.data.preferences.SecurePreferences,
        moshi: Moshi,
        speakerGroupRepository: com.opensmarthome.speaker.multiroom.SpeakerGroupRepository
    ): com.opensmarthome.speaker.multiroom.AnnouncementBroadcaster =
        com.opensmarthome.speaker.multiroom.AnnouncementBroadcaster(
            discovery = discovery,
            client = client,
            securePreferences = securePreferences,
            moshi = moshi,
            selfServiceName = { discovery.registeredName.value },
            groupLookup = { name -> speakerGroupRepository.get(name) },
            webSocketClient = webSocketClient
        )

    @Provides
    @Singleton
    fun provideSuggestionEngine(
        peerLivenessTracker: com.opensmarthome.speaker.multiroom.PeerLivenessTracker
    ): com.opensmarthome.speaker.assistant.proactive.SuggestionEngine =
        com.opensmarthome.speaker.assistant.proactive.SuggestionEngine(
            rules = listOf(
                com.opensmarthome.speaker.assistant.proactive.MorningGreetingRule(),
                com.opensmarthome.speaker.assistant.proactive.MorningBriefingSuggestionRule(),
                com.opensmarthome.speaker.assistant.proactive.WeekendMorningRule(),
                com.opensmarthome.speaker.assistant.proactive.EveningLightsRule(),
                com.opensmarthome.speaker.assistant.proactive.EveningBriefingRule(),
                com.opensmarthome.speaker.assistant.proactive.NightQuietRule(),
                com.opensmarthome.speaker.assistant.proactive.StalePeerRule(peerLivenessTracker)
            )
        )

    @Provides
    @Singleton
    fun provideSuggestionState(
        engine: com.opensmarthome.speaker.assistant.proactive.SuggestionEngine
    ): com.opensmarthome.speaker.assistant.proactive.SuggestionState =
        com.opensmarthome.speaker.assistant.proactive.SuggestionState(engine).apply { start() }

    @Provides
    @Singleton
    fun provideScreenRecorderHolder(): ScreenRecorderHolder = ScreenRecorderHolder()

    @Provides
    @Singleton
    fun providePersistentToolUsageStats(dao: ToolUsageDao): PersistentToolUsageStats =
        PersistentToolUsageStats(dao)

    @Provides
    @Singleton
    fun provideSkillInstaller(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        registry: SkillRegistry
    ): SkillInstaller {
        val userSkillsDir = java.io.File(context.filesDir, "skills").apply { mkdirs() }
        return SkillInstaller(client, userSkillsDir, registry)
    }

    @Provides
    @Singleton
    fun provideSkillRepository(
        @ApplicationContext context: Context,
        registry: SkillRegistry
    ): com.opensmarthome.speaker.assistant.skills.SkillRepository {
        val userSkillsDir = java.io.File(context.filesDir, "skills").apply { mkdirs() }
        return com.opensmarthome.speaker.assistant.skills.SkillRepository(registry, userSkillsDir)
    }

    @Provides
    @Singleton
    fun provideMemoryRepository(
        dao: com.opensmarthome.speaker.data.db.MemoryDao
    ): com.opensmarthome.speaker.tool.memory.MemoryRepository =
        com.opensmarthome.speaker.tool.memory.MemoryRepository(dao)

    @Provides
    @Singleton
    fun provideRoutineRepository(
        dao: RoutineDao,
        moshi: Moshi
    ): com.opensmarthome.speaker.assistant.routine.RoutineRepository =
        com.opensmarthome.speaker.assistant.routine.RoutineRepository(
            com.opensmarthome.speaker.assistant.routine.RoomRoutineStore(dao, moshi)
        )

    @Provides
    @Singleton
    fun provideAnalyticsRepository(
        dao: ToolUsageDao,
        stats: PersistentToolUsageStats
    ): com.opensmarthome.speaker.tool.analytics.AnalyticsRepository =
        com.opensmarthome.speaker.tool.analytics.AnalyticsRepository(dao, stats)

    @Provides
    @Singleton
    fun providePermissionManager(
        @ApplicationContext context: Context
    ): com.opensmarthome.speaker.permission.PermissionManager =
        com.opensmarthome.speaker.permission.PermissionManager(
            context = context,
            notificationListenerClasses = listOf(
                com.opensmarthome.speaker.tool.system.OpenSmartSpeakerNotificationListener::class.java
            ),
            // Accept grant on EITHER accessibility service — the legacy
            // OpenSmartAccessibilityService (tool/accessibility) and the
            // newer OpenSmartSpeakerA11yService (a11y/) coexist during the
            // Phase 15 migration. Granting either satisfies onboarding.
            accessibilityServiceClasses = listOf(
                com.opensmarthome.speaker.tool.accessibility.OpenSmartAccessibilityService::class.java,
                com.opensmarthome.speaker.a11y.OpenSmartSpeakerA11yService::class.java
            )
        )

    @Provides
    @Singleton
    fun providePermissionRepository(
        manager: com.opensmarthome.speaker.permission.PermissionManager
    ): com.opensmarthome.speaker.permission.PermissionRepository =
        com.opensmarthome.speaker.permission.PermissionRepository(manager)

    @Provides
    @Singleton
    fun provideRagRepository(
        dao: DocumentChunkDao
    ): com.opensmarthome.speaker.tool.rag.RagRepository {
        val service = com.opensmarthome.speaker.tool.rag.RagService(dao)
        return com.opensmarthome.speaker.tool.rag.RagRepository(service, dao)
    }

    @Provides
    @Singleton
    fun provideToolExecutor(
        deviceManager: DeviceManager,
        moshi: Moshi,
        @ApplicationContext context: Context,
        client: OkHttpClient,
        skillRegistry: SkillRegistry,
        skillInstaller: SkillInstaller,
        memoryDao: MemoryDao,
        routineDao: RoutineDao,
        documentChunkDao: DocumentChunkDao,
        cameraProviderHolder: CameraProviderHolder,
        screenRecorderHolder: ScreenRecorderHolder,
        toolUsageStats: PersistentToolUsageStats,
        timerManager: com.opensmarthome.speaker.tool.system.TimerManager,
        notificationProvider: com.opensmarthome.speaker.tool.system.NotificationProvider,
        a11yServiceHolder: com.opensmarthome.speaker.a11y.A11yServiceHolder,
        announcementBroadcaster: com.opensmarthome.speaker.multiroom.AnnouncementBroadcaster,
        multicastDiscovery: com.opensmarthome.speaker.util.MulticastDiscovery
    ): ToolExecutor {
        val routineStore = RoomRoutineStore(routineDao, moshi)
        val compositeHolder = arrayOfNulls<CompositeToolExecutor>(1)
        // RoutineToolExecutor needs a reference to the full tool executor so
        // saved routines can invoke any other tool. We build composite first,
        // then inject it back via a proxy lambda-free wrapper.
        val delegatingExecutor = object : ToolExecutor {
            override suspend fun availableTools() = compositeHolder[0]!!.availableTools()
            override suspend fun execute(call: com.opensmarthome.speaker.tool.ToolCall) =
                compositeHolder[0]!!.execute(call)
        }
        val composite = CompositeToolExecutor(
            stats = ToolUsageRecorder { name, ok -> toolUsageStats.record(name, ok) },
            executors = listOf(
            DeviceToolExecutor(deviceManager, moshi),
            SystemToolExecutor(
                timerManager,
                AndroidVolumeManager(context),
                AndroidAppLauncher(context)
            ),
            WeatherToolExecutor(
                OpenMeteoWeatherProvider(client, moshi)
            ),
            SearchToolExecutor(
                DuckDuckGoSearchProvider(client, moshi)
            ),
            WebFetchToolExecutor(HtmlWebFetcher(client)),
            UnitConverterToolExecutor(),
            CalculatorToolExecutor(),
            CurrencyToolExecutor(),
            NewsToolExecutor(
                RssNewsProvider(client)
            ),
            KnowledgeToolExecutor(InMemoryKnowledgeStore()),
            NotificationToolExecutor(notificationProvider),
            NotificationReplyToolExecutor(notificationProvider),
            CalendarToolExecutor(
                AndroidCalendarProvider(context)
            ),
            LocationToolExecutor(
                AndroidLocationProvider(context)
            ),
            ContactsToolExecutor(
                AndroidContactsProvider(context)
            ),
            DeviceHealthToolExecutor(
                AndroidDeviceHealthProvider(context)
            ),
            OpenSettingsToolExecutor(context),
            OpenUrlToolExecutor(context),
            com.opensmarthome.speaker.tool.system.LockScreenToolExecutor(context),
            com.opensmarthome.speaker.tool.system.BroadcastTtsToolExecutor(announcementBroadcaster),
            com.opensmarthome.speaker.tool.multiroom.BroadcastAnnouncementToolExecutor(announcementBroadcaster),
            com.opensmarthome.speaker.tool.multiroom.BroadcastTimerToolExecutor(announcementBroadcaster),
            com.opensmarthome.speaker.tool.multiroom.BroadcastCancelTimerToolExecutor(announcementBroadcaster),
            com.opensmarthome.speaker.tool.multiroom.ListPeersToolExecutor(multicastDiscovery),
            PhotosToolExecutor(
                AndroidPhotosProvider(context)
            ),
            SmsToolExecutor(
                AndroidSmsSender(context)
            ),
            CameraToolExecutor(cameraProviderHolder),
            ScreenRecorderToolExecutor(screenRecorderHolder),
            ScreenToolExecutor(
                AccessibilityScreenReader()
            ),
            ReadActiveScreenToolExecutor(a11yServiceHolder),
            TapByTextToolExecutor(a11yServiceHolder),
            ScrollScreenToolExecutor(a11yServiceHolder),
            TypeTextToolExecutor(a11yServiceHolder),
            MemoryToolExecutor(memoryDao),
            RagToolExecutor(RagService(documentChunkDao)),
            RoutineToolExecutor(routineStore, delegatingExecutor),
            SkillToolExecutor(skillRegistry, skillInstaller),
            com.opensmarthome.speaker.tool.system.FindDeviceTool(context),
            // Composites — call back into the executor they're part of via
            // lambda to avoid a Hilt cycle.
            com.opensmarthome.speaker.tool.composite.MorningBriefingTool { delegatingExecutor },
            com.opensmarthome.speaker.tool.composite.EveningBriefingTool { delegatingExecutor },
            com.opensmarthome.speaker.tool.composite.GoodnightTool { delegatingExecutor },
            com.opensmarthome.speaker.tool.composite.PresenceTool { delegatingExecutor },
            // TODO(P17.5 follow-up): wire historyProvider to VoicePipeline's
            // live conversation history once the pipeline exposes it. For now
            // the tool ships the structural plumbing with an empty history
            // provider so the envelope/dispatch path is testable end-to-end.
            com.opensmarthome.speaker.tool.multiroom.HandoffToolExecutor(
                broadcaster = announcementBroadcaster,
                historyProvider = { emptyList() }
            )
            )
        )
        compositeHolder[0] = composite
        return composite
    }
}
