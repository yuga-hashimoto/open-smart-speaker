package com.opensmarthome.speaker.di

import com.opensmarthome.speaker.assistant.router.ConversationRouter
import com.opensmarthome.speaker.assistant.router.ConversationRouterImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {

    @Binds
    @Singleton
    abstract fun bindConversationRouter(impl: ConversationRouterImpl): ConversationRouter
}
