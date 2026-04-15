package com.opensmarthome.speaker.di

import com.opensmarthome.speaker.homeassistant.tool.ToolExecutor
import com.opensmarthome.speaker.homeassistant.tool.ToolExecutorImpl
import com.opensmarthome.speaker.homeassistant.cache.EntityCache
import com.opensmarthome.speaker.homeassistant.cache.EntityCacheImpl
import com.opensmarthome.speaker.homeassistant.client.HomeAssistantClient
import com.opensmarthome.speaker.homeassistant.client.HomeAssistantConfig
import com.opensmarthome.speaker.homeassistant.client.HomeAssistantRestClient
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeAssistantModule {

    @Provides
    @Singleton
    fun provideHomeAssistantConfig(): HomeAssistantConfig =
        HomeAssistantConfig(
            baseUrl = "http://homeassistant.local:8123",
            token = ""
        )

    @Provides
    @Singleton
    fun provideHomeAssistantClient(
        client: OkHttpClient,
        moshi: Moshi,
        config: HomeAssistantConfig
    ): HomeAssistantClient = HomeAssistantRestClient(client, moshi, config)

    @Provides
    @Singleton
    fun provideEntityCache(
        haClient: HomeAssistantClient,
        config: HomeAssistantConfig
    ): EntityCache = EntityCacheImpl(haClient, config)

    @Provides
    @Singleton
    fun provideToolExecutor(
        haClient: HomeAssistantClient,
        entityCache: EntityCache,
        moshi: Moshi
    ): ToolExecutor = ToolExecutorImpl(haClient, entityCache, moshi)
}
