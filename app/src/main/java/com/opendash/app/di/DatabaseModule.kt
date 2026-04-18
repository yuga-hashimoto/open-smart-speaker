package com.opendash.app.di

import android.content.Context
import androidx.room.Room
import com.opendash.app.data.db.AppDatabase
import com.opendash.app.data.db.DocumentChunkDao
import com.opendash.app.data.db.MemoryDao
import com.opendash.app.data.db.MessageDao
import com.opendash.app.data.db.MultiroomRejectionDao
import com.opendash.app.data.db.MultiroomTrafficDao
import com.opendash.app.data.db.RoutineDao
import com.opendash.app.data.db.SessionDao
import com.opendash.app.data.db.SpeakerGroupDao
import com.opendash.app.data.db.ToolUsageDao
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
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideMemoryDao(db: AppDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideDocumentChunkDao(db: AppDatabase): DocumentChunkDao = db.documentChunkDao()

    @Provides
    fun provideToolUsageDao(db: AppDatabase): ToolUsageDao = db.toolUsageDao()

    @Provides
    fun provideSpeakerGroupDao(db: AppDatabase): SpeakerGroupDao = db.speakerGroupDao()

    @Provides
    fun provideMultiroomTrafficDao(db: AppDatabase): MultiroomTrafficDao = db.multiroomTrafficDao()

    @Provides
    fun provideMultiroomRejectionDao(db: AppDatabase): MultiroomRejectionDao = db.multiroomRejectionDao()
}
