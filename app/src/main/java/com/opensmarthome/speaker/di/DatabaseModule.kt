package com.opensmarthome.speaker.di

import android.content.Context
import androidx.room.Room
import com.opensmarthome.speaker.data.db.AppDatabase
import com.opensmarthome.speaker.data.db.MessageDao
import com.opensmarthome.speaker.data.db.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "open_smart_speaker.db"
        ).build()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
}
