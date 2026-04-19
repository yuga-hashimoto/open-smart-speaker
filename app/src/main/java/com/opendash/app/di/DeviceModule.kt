package com.opendash.app.di

import com.opendash.app.device.DeviceManager
import com.opendash.app.device.provider.DeviceProvider
import com.opendash.app.device.provider.homeassistant.HomeAssistantDeviceProvider
import com.opendash.app.device.provider.matter.MatterDeviceProvider
import com.opendash.app.device.provider.mqtt.MqttClientWrapper
import com.opendash.app.device.provider.mqtt.MqttConfig
import com.opendash.app.device.provider.mqtt.MqttDeviceProvider
import com.opendash.app.device.provider.switchbot.SwitchBotApiClient
import com.opendash.app.device.provider.switchbot.SwitchBotConfig
import com.opendash.app.device.provider.switchbot.SwitchBotDeviceProvider
import android.content.Context
import com.opendash.app.device.tool.DeviceToolExecutor
import com.opendash.app.homeassistant.client.HomeAssistantClient
import com.opendash.app.tool.CompositeToolExecutor
import com.opendash.app.tool.ToolExecutor
import com.opendash.app.tool.info.CalculatorToolExecutor
import com.opendash.app.tool.info.DateToolExecutor
import com.opendash.app.tool.info.PercentageToolExecutor
import com.opendash.app.tool.info.RandomToolExecutor
import com.opendash.app.tool.info.CurrencyToolExecutor
import com.opendash.app.voice.fastpath.DefaultFastPathRouter
import com.opendash.app.voice.fastpath.FastPathRouter
import com.opendash.app.tool.info.DuckDuckGoHtmlSearchProvider
import com.opendash.app.tool.info.DuckDuckGoSearchProvider
import com.opendash.app.tool.info.HtmlWebFetcher
import com.opendash.app.tool.info.SearchProviderChain
import com.opendash.app.tool.info.InMemoryKnowledgeStore
import com.opendash.app.tool.info.KnowledgeToolExecutor
import com.opendash.app.tool.info.NewsToolExecutor
import com.opendash.app.tool.info.OpenMeteoWeatherProvider
import com.opendash.app.tool.info.RssNewsProvider
import com.opendash.app.tool.info.SearchToolExecutor
import com.opendash.app.tool.info.UnitConverterToolExecutor
import com.opendash.app.tool.info.WeatherToolExecutor
import com.opendash.app.tool.info.WebFetchToolExecutor
import com.opendash.app.assistant.skills.AssetSkillLoader
import com.opendash.app.assistant.skills.FileSystemSkillLoader
import com.opendash.app.assistant.skills.SkillInstaller
import com.opendash.app.assistant.skills.SkillRegistry
import com.opendash.app.assistant.skills.SkillToolExecutor
import com.opendash.app.assistant.routine.RoomRoutineStore
import com.opendash.app.assistant.routine.RoutineToolExecutor
import com.opendash.app.tool.accessibility.AccessibilityScreenReader
import com.opendash.app.tool.a11y.ReadActiveScreenToolExecutor
import com.opendash.app.tool.a11y.ScrollScreenToolExecutor
import com.opendash.app.tool.a11y.TapByTextToolExecutor
import com.opendash.app.tool.a11y.TypeTextToolExecutor
import com.opendash.app.tool.accessibility.ScreenToolExecutor
import com.opendash.app.data.db.DocumentChunkDao
import com.opendash.app.data.db.ToolUsageDao
import com.opendash.app.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import com.opendash.app.tool.analytics.PersistentToolUsageStats
import com.opendash.app.tool.analytics.ToolUsageRecorder
import com.opendash.app.data.db.MemoryDao
import com.opendash.app.data.db.RoutineDao
import com.opendash.app.tool.memory.MemoryToolExecutor
import com.opendash.app.tool.rag.RagService
import com.opendash.app.tool.rag.RagToolExecutor
import com.opendash.app.tool.system.AndroidAppLauncher
import com.opendash.app.tool.system.AndroidCalendarProvider
import com.opendash.app.tool.system.AndroidContactsProvider
import com.opendash.app.tool.system.AndroidDeviceHealthProvider
import com.opendash.app.tool.system.AndroidLocationProvider
import com.opendash.app.tool.system.AndroidNotificationProvider
import com.opendash.app.tool.system.AndroidPhotosProvider
import com.opendash.app.tool.system.AndroidSmsSender
import com.opendash.app.tool.system.AndroidTimerManager
import com.opendash.app.tool.system.AndroidVolumeManager
import com.opendash.app.tool.system.CalendarToolExecutor
import com.opendash.app.tool.system.CameraProviderHolder
import com.opendash.app.tool.system.CameraToolExecutor
import com.opendash.app.tool.system.ContactsToolExecutor
import com.opendash.app.tool.system.DeviceHealthToolExecutor
import com.opendash.app.tool.system.LocationToolExecutor
import com.opendash.app.tool.system.NotificationReplyToolExecutor
import com.opendash.app.tool.system.NotificationToolExecutor
import com.opendash.app.tool.system.OpenSettingsToolExecutor
import com.opendash.app.tool.system.OpenUrlToolExecutor
import com.opendash.app.tool.system.PhotosToolExecutor
import com.opendash.app.tool.system.ScreenRecorderHolder
import com.opendash.app.tool.system.ScreenRecorderToolExecutor
import com.opendash.app.tool.system.SmsToolExecutor
import com.opendash.app.tool.system.SystemToolExecutor
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
    ): com.opendash.app.tool.system.TimerManager =
        AndroidTimerManager(context)

    @Provides
    @Singleton
    fun provideNotificationProvider(
        @ApplicationContext context: Context
    ): com.opendash.app.tool.system.NotificationProvider =
        AndroidNotificationProvider(context)

    @Provides
    @Singleton
    fun provideAmbientSnapshotBuilder(
        timerManager: com.opendash.app.tool.system.TimerManager,
        notificationProvider: com.opendash.app.tool.system.NotificationProvider
    ): com.opendash.app.ui.ambient.AmbientSnapshotBuilder =
        com.opendash.app.ui.ambient.AmbientSnapshotBuilder(
            timerManager = timerManager,
            notificationProvider = notificationProvider
        )

    @Provides
    @Singleton
    fun provideFastPathRouter(
        batteryMonitor: com.opendash.app.util.BatteryMonitor,
        timerManager: com.opendash.app.tool.system.TimerManager
    ): FastPathRouter {
        // Insert CancelTimerByLabelMatcher between CancelAllTimersMatcher and TimerMatcher.
        // CancelAll must come first (owns "all" / "全部" keyword case).
        // CancelByLabel second so specific labels beat the generic TimerMatcher regex.
        val cancelByLabel = com.opendash.app.voice.fastpath
            .CancelTimerByLabelMatcher(timerManager)
        val defaults = DefaultFastPathRouter.DEFAULT_MATCHERS
        val timerIdx = defaults.indexOf(
            com.opendash.app.voice.fastpath.TimerMatcher
        )
        val matchers = if (timerIdx >= 0) {
            defaults.subList(0, timerIdx) + cancelByLabel + defaults.subList(timerIdx, defaults.size)
        } else {
            defaults + cancelByLabel
        }
        return DefaultFastPathRouter(
            matchers = matchers +
                com.opendash.app.voice.fastpath.BatteryMatcher(batteryMonitor)
        )
    }

    @Provides
    @Singleton
    fun provideAnnouncementParser(
        moshi: Moshi,
        securePreferences: com.opendash.app.data.preferences.SecurePreferences
    ): com.opendash.app.multiroom.AnnouncementParser =
        com.opendash.app.multiroom.AnnouncementParser(
            moshi = moshi,
            sharedSecretProvider = {
                securePreferences.getString(
                    com.opendash.app.data.preferences.SecurePreferences.KEY_MULTIROOM_SECRET
                ).takeIf { it.isNotBlank() }
            }
        )

    @Provides
    @Singleton
    fun provideAnnouncementClient(): com.opendash.app.multiroom.AnnouncementClient =
        com.opendash.app.multiroom.AnnouncementClient()

    @Provides
    @Singleton
    fun provideAnnouncementDispatcher(
        tts: com.opendash.app.voice.tts.TextToSpeech,
        timerManager: com.opendash.app.tool.system.TimerManager,
        announcementState: com.opendash.app.multiroom.AnnouncementState,
        peerLivenessTracker: com.opendash.app.multiroom.PeerLivenessTracker,
        trafficRecorder: com.opendash.app.multiroom.MultiroomTrafficRecorder
    ): com.opendash.app.multiroom.AnnouncementDispatcher =
        com.opendash.app.multiroom.AnnouncementDispatcher(
            tts = tts,
            // TODO(P17.5 follow-up): wire historyProvider to the live
            // ConversationHistoryManager once VoicePipeline exposes it so
            // session_handoff messages can actually seed future turns.
            historyProvider = { null },
            timerManagerProvider = { timerManager },
            announcementState = announcementState,
            onHeartbeat = { envelope -> peerLivenessTracker.onHeartbeat(envelope) },
            trafficRecorder = trafficRecorder
        )

    @Provides
    @Singleton
    fun provideAnnouncementBroadcaster(
        discovery: com.opendash.app.util.MulticastDiscovery,
        client: com.opendash.app.multiroom.AnnouncementClient,
        webSocketClient: com.opendash.app.multiroom.AnnouncementWebSocketClient,
        securePreferences: com.opendash.app.data.preferences.SecurePreferences,
        moshi: Moshi,
        speakerGroupRepository: com.opendash.app.multiroom.SpeakerGroupRepository,
        trafficRecorder: com.opendash.app.multiroom.MultiroomTrafficRecorder
    ): com.opendash.app.multiroom.AnnouncementBroadcaster =
        com.opendash.app.multiroom.AnnouncementBroadcaster(
            discovery = discovery,
            client = client,
            securePreferences = securePreferences,
            moshi = moshi,
            selfServiceName = { discovery.registeredName.value },
            groupLookup = { name -> speakerGroupRepository.get(name) },
            webSocketClient = webSocketClient,
            trafficRecorder = trafficRecorder
        )

    @Provides
    @Singleton
    fun provideSuggestionEngine(
        peerLivenessTracker: com.opendash.app.multiroom.PeerLivenessTracker,
        deviceManager: DeviceManager,
        batteryMonitor: com.opendash.app.util.BatteryMonitor,
    ): com.opendash.app.assistant.proactive.SuggestionEngine =
        com.opendash.app.assistant.proactive.SuggestionEngine(
            rules = listOf(
                com.opendash.app.assistant.proactive.MorningGreetingRule(),
                com.opendash.app.assistant.proactive.MorningBriefingSuggestionRule(),
                com.opendash.app.assistant.proactive.WeekendMorningRule(),
                com.opendash.app.assistant.proactive.EveningLightsRule(),
                com.opendash.app.assistant.proactive.EveningBriefingRule(),
                com.opendash.app.assistant.proactive.NightQuietRule(),
                com.opendash.app.assistant.proactive.StalePeerRule(peerLivenessTracker),
                com.opendash.app.assistant.proactive.LowBatteryRule(
                    statusSupplier = { batteryMonitor.status.value }
                ),
                com.opendash.app.assistant.proactive.ForgotLightsAtBedtimeRule(
                    devicesSupplier = { deviceManager.devices.value.values }
                ),
                com.opendash.app.assistant.proactive.ChargingCompleteRule(
                    statusSupplier = { batteryMonitor.status.value }
                ),
            )
        )

    @Provides
    @Singleton
    fun provideSuggestionState(
        engine: com.opendash.app.assistant.proactive.SuggestionEngine
    ): com.opendash.app.assistant.proactive.SuggestionState =
        com.opendash.app.assistant.proactive.SuggestionState(engine).apply { start() }

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
    ): com.opendash.app.assistant.skills.SkillRepository {
        val userSkillsDir = java.io.File(context.filesDir, "skills").apply { mkdirs() }
        return com.opendash.app.assistant.skills.SkillRepository(registry, userSkillsDir)
    }

    @Provides
    @Singleton
    fun provideMemoryRepository(
        dao: com.opendash.app.data.db.MemoryDao
    ): com.opendash.app.tool.memory.MemoryRepository =
        com.opendash.app.tool.memory.MemoryRepository(dao)

    @Provides
    @Singleton
    fun provideRoutineRepository(
        dao: RoutineDao,
        moshi: Moshi
    ): com.opendash.app.assistant.routine.RoutineRepository =
        com.opendash.app.assistant.routine.RoutineRepository(
            com.opendash.app.assistant.routine.RoomRoutineStore(dao, moshi)
        )

    @Provides
    @Singleton
    fun provideAnalyticsRepository(
        dao: ToolUsageDao,
        stats: PersistentToolUsageStats
    ): com.opendash.app.tool.analytics.AnalyticsRepository =
        com.opendash.app.tool.analytics.AnalyticsRepository(dao, stats)

    @Provides
    @Singleton
    fun providePermissionManager(
        @ApplicationContext context: Context
    ): com.opendash.app.permission.PermissionManager =
        com.opendash.app.permission.PermissionManager(
            context = context,
            notificationListenerClasses = listOf(
                com.opendash.app.tool.system.OpenDashNotificationListener::class.java
            ),
            // Accept grant on EITHER accessibility service — the legacy
            // OpenSmartAccessibilityService (tool/accessibility) and the
            // newer OpenDashA11yService (a11y/) coexist during the
            // Phase 15 migration. Granting either satisfies onboarding.
            accessibilityServiceClasses = listOf(
                com.opendash.app.tool.accessibility.OpenSmartAccessibilityService::class.java,
                com.opendash.app.a11y.OpenDashA11yService::class.java
            )
        )

    @Provides
    @Singleton
    fun providePermissionRepository(
        manager: com.opendash.app.permission.PermissionManager
    ): com.opendash.app.permission.PermissionRepository =
        com.opendash.app.permission.PermissionRepository(manager)

    @Provides
    @Singleton
    fun provideRagRepository(
        dao: DocumentChunkDao
    ): com.opendash.app.tool.rag.RagRepository {
        val service = com.opendash.app.tool.rag.RagService(dao)
        return com.opendash.app.tool.rag.RagRepository(service, dao)
    }

    @Provides
    @Singleton
    fun provideWeatherProvider(
        client: OkHttpClient,
        moshi: Moshi
    ): com.opendash.app.tool.info.WeatherProvider =
        com.opendash.app.tool.info.OpenMeteoWeatherProvider(client, moshi)

    /**
     * City search repository for the Settings weather-location picker. Uses
     * the same Open-Meteo endpoint family as [OpenMeteoWeatherProvider] so
     * both the forecast tool and the picker share one provider (and one
     * External Service Review).
     */
    @Provides
    @Singleton
    fun provideCitySearchRepository(
        client: OkHttpClient,
        moshi: Moshi
    ): com.opendash.app.data.location.CitySearchRepository =
        com.opendash.app.data.location.OpenMeteoCitySearchRepository(client, moshi)

    @Provides
    @Singleton
    fun provideNewsProvider(
        client: OkHttpClient
    ): com.opendash.app.tool.info.NewsProvider =
        com.opendash.app.tool.info.RssNewsProvider(client)

    @Provides
    @Singleton
    fun provideOnlineBriefingSource(
        weatherProvider: com.opendash.app.tool.info.WeatherProvider,
        newsProvider: com.opendash.app.tool.info.NewsProvider,
        appPreferences: AppPreferences,
    ): com.opendash.app.ui.home.OnlineBriefingSource =
        com.opendash.app.ui.home.DefaultOnlineBriefingSource(
            weatherProvider,
            newsProvider,
            appPreferences,
        )

    @Provides
    @Singleton
    fun provideCalendarProvider(
        @ApplicationContext context: Context
    ): com.opendash.app.tool.system.CalendarProvider =
        AndroidCalendarProvider(context)

    @Provides
    @Singleton
    fun provideUpcomingEventSource(
        calendarProvider: com.opendash.app.tool.system.CalendarProvider
    ): com.opendash.app.ui.home.UpcomingEventSource =
        com.opendash.app.ui.home.DefaultUpcomingEventSource(calendarProvider)

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
        timerManager: com.opendash.app.tool.system.TimerManager,
        notificationProvider: com.opendash.app.tool.system.NotificationProvider,
        a11yServiceHolder: com.opendash.app.a11y.A11yServiceHolder,
        announcementBroadcaster: com.opendash.app.multiroom.AnnouncementBroadcaster,
        multicastDiscovery: com.opendash.app.util.MulticastDiscovery,
        localeManager: com.opendash.app.util.LocaleManager,
        appPreferences: AppPreferences
    ): ToolExecutor {
        val routineStore = RoomRoutineStore(routineDao, moshi)
        val compositeHolder = arrayOfNulls<CompositeToolExecutor>(1)
        // RoutineToolExecutor needs a reference to the full tool executor so
        // saved routines can invoke any other tool. We build composite first,
        // then inject it back via a proxy lambda-free wrapper.
        val delegatingExecutor = object : ToolExecutor {
            override suspend fun availableTools() = compositeHolder[0]!!.availableTools()
            override suspend fun execute(call: com.opendash.app.tool.ToolCall) =
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
                OpenMeteoWeatherProvider(client, moshi),
                appPreferences
            ),
            SearchToolExecutor(
                // HTML scrape first (real SERP results); Instant-Answer API
                // as a secondary for the few queries it handles well
                // (unit conversions, math, well-known topics). Wikipedia
                // fallback removed per user feedback — DDG HTML covers it.
                // Stolen from openclaw: extensions/duckduckgo/src/ddg-client.ts
                SearchProviderChain(
                    providers = listOf(
                        DuckDuckGoHtmlSearchProvider(client),
                        DuckDuckGoSearchProvider(client, moshi)
                    )
                )
            ),
            WebFetchToolExecutor(HtmlWebFetcher(client)),
            UnitConverterToolExecutor(),
            CalculatorToolExecutor(),
            DateToolExecutor(),
            PercentageToolExecutor(),
            RandomToolExecutor(),
            CurrencyToolExecutor(),
            NewsToolExecutor(
                RssNewsProvider(client),
                defaultFeedUrlProvider = {
                    runCatching {
                        appPreferences.observe(
                            com.opendash.app.data.preferences.PreferenceKeys.DEFAULT_NEWS_FEED_URL
                        ).first()
                    }.getOrNull()
                }
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
            com.opendash.app.tool.system.PhoneCallToolExecutor(
                context,
                AndroidContactsProvider(context),
            ),
            com.opendash.app.tool.system.BluetoothToolExecutor(
                com.opendash.app.tool.system.AndroidBluetoothInfoProvider(context)
            ),
            com.opendash.app.tool.system.WifiToolExecutor(
                com.opendash.app.tool.system.AndroidWifiInfoProvider(context)
            ),
            com.opendash.app.tool.system.SetAlarmToolExecutor(context),
            com.opendash.app.tool.system.MediaLibraryToolExecutor(
                com.opendash.app.tool.system.AndroidMediaLibraryProvider(context)
            ),
            DeviceHealthToolExecutor(
                AndroidDeviceHealthProvider(context)
            ),
            OpenSettingsToolExecutor(context),
            OpenUrlToolExecutor(context),
            com.opendash.app.tool.system.NativeMediaPlayerToolExecutor(context),
            com.opendash.app.tool.system.LocaleToolExecutor(localeManager),
            com.opendash.app.tool.system.LockScreenToolExecutor(context),
            com.opendash.app.tool.system.BroadcastTtsToolExecutor(announcementBroadcaster),
            com.opendash.app.tool.multiroom.BroadcastAnnouncementToolExecutor(announcementBroadcaster),
            com.opendash.app.tool.multiroom.BroadcastTimerToolExecutor(announcementBroadcaster),
            com.opendash.app.tool.multiroom.BroadcastCancelTimerToolExecutor(announcementBroadcaster),
            com.opendash.app.tool.multiroom.ListPeersToolExecutor(multicastDiscovery),
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
            com.opendash.app.tool.system.FindDeviceTool(context),
            // Composites — call back into the executor they're part of via
            // lambda to avoid a Hilt cycle.
            com.opendash.app.tool.composite.MorningBriefingTool { delegatingExecutor },
            com.opendash.app.tool.composite.EveningBriefingTool { delegatingExecutor },
            com.opendash.app.tool.composite.GoodnightTool { delegatingExecutor },
            com.opendash.app.tool.composite.PresenceTool { delegatingExecutor },
            // TODO(P17.5 follow-up): wire historyProvider to VoicePipeline's
            // live conversation history once the pipeline exposes it. For now
            // the tool ships the structural plumbing with an empty history
            // provider so the envelope/dispatch path is testable end-to-end.
            com.opendash.app.tool.multiroom.HandoffToolExecutor(
                broadcaster = announcementBroadcaster,
                historyProvider = { emptyList() }
            )
            )
        )
        compositeHolder[0] = composite
        return composite
    }
}
