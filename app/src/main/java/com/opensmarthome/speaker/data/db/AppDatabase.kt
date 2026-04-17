package com.opensmarthome.speaker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        RoutineEntity::class,
        DocumentChunkEntity::class,
        ToolUsageEntity::class,
        SpeakerGroupEntity::class,
        MultiroomTrafficEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun routineDao(): RoutineDao
    abstract fun documentChunkDao(): DocumentChunkDao
    abstract fun toolUsageDao(): ToolUsageDao
    abstract fun speakerGroupDao(): SpeakerGroupDao
    abstract fun multiroomTrafficDao(): MultiroomTrafficDao
}
