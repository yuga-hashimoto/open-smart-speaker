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
import com.opensmarthome.speaker.tool.info.DuckDuckGoSearchProvider
import com.opensmarthome.speaker.tool.info.InMemoryKnowledgeStore
import com.opensmarthome.speaker.tool.info.KnowledgeToolExecutor
import com.opensmarthome.speaker.tool.info.NewsToolExecutor
import com.opensmarthome.speaker.tool.info.OpenMeteoWeatherProvider
import com.opensmarthome.speaker.tool.info.RssNewsProvider
import com.opensmarthome.speaker.tool.info.SearchToolExecutor
import com.opensmarthome.speaker.tool.info.WeatherToolExecutor
import com.opensmarthome.speaker.assistant.skills.AssetSkillLoader
import com.opensmarthome.speaker.assistant.skills.SkillRegistry
import com.opensmarthome.speaker.assistant.skills.SkillToolExecutor
import com.opensmarthome.speaker.assistant.routine.InMemoryRoutineStore
import com.opensmarthome.speaker.assistant.routine.RoutineToolExecutor
import com.opensmarthome.speaker.data.db.MemoryDao
import com.opensmarthome.speaker.tool.memory.MemoryToolExecutor
import com.opensmarthome.speaker.tool.system.AndroidAppLauncher
import com.opensmarthome.speaker.tool.system.AndroidCalendarProvider
import com.opensmarthome.speaker.tool.system.AndroidContactsProvider
import com.opensmarthome.speaker.tool.system.AndroidLocationProvider
import com.opensmarthome.speaker.tool.system.AndroidNotificationProvider
import com.opensmarthome.speaker.tool.system.AndroidTimerManager
import com.opensmarthome.speaker.tool.system.AndroidVolumeManager
import com.opensmarthome.speaker.tool.system.CalendarToolExecutor
import com.opensmarthome.speaker.tool.system.ContactsToolExecutor
import com.opensmarthome.speaker.tool.system.LocationToolExecutor
import com.opensmarthome.speaker.tool.system.NotificationToolExecutor
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
        val loader = AssetSkillLoader(context)
        registry.registerAll(loader.loadAll())
        return registry
    }

    @Provides
    @Singleton
    fun provideToolExecutor(
        deviceManager: DeviceManager,
        moshi: Moshi,
        @ApplicationContext context: Context,
        client: OkHttpClient,
        skillRegistry: SkillRegistry,
        memoryDao: MemoryDao
    ): ToolExecutor {
        val routineStore = InMemoryRoutineStore()
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
            listOf(
            DeviceToolExecutor(deviceManager, moshi),
            SystemToolExecutor(
                AndroidTimerManager(context),
                AndroidVolumeManager(context),
                AndroidAppLauncher(context)
            ),
            WeatherToolExecutor(
                OpenMeteoWeatherProvider(client, moshi)
            ),
            SearchToolExecutor(
                DuckDuckGoSearchProvider(client, moshi)
            ),
            NewsToolExecutor(
                RssNewsProvider(client)
            ),
            KnowledgeToolExecutor(InMemoryKnowledgeStore()),
            NotificationToolExecutor(
                AndroidNotificationProvider(context)
            ),
            CalendarToolExecutor(
                AndroidCalendarProvider(context)
            ),
            LocationToolExecutor(
                AndroidLocationProvider(context)
            ),
            ContactsToolExecutor(
                AndroidContactsProvider(context)
            ),
            MemoryToolExecutor(memoryDao),
            RoutineToolExecutor(routineStore, delegatingExecutor),
            SkillToolExecutor(skillRegistry)
            )
        )
        compositeHolder[0] = composite
        return composite
    }
}
