package com.opendash.app.di

import com.opendash.app.homeassistant.client.HomeAssistantClient
import com.opendash.app.homeassistant.client.HomeAssistantConfig
import com.opendash.app.homeassistant.client.HomeAssistantRestClient
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
}
