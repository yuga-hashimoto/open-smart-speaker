package com.opensmarthome.speaker.assistant.routine

interface RoutineStore {
    suspend fun save(routine: Routine)
    suspend fun get(id: String): Routine?
    suspend fun getByName(name: String): Routine?
    suspend fun delete(id: String): Boolean
    suspend fun listAll(): List<Routine>
}
