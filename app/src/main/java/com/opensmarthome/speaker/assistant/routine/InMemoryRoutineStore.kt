package com.opensmarthome.speaker.assistant.routine

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryRoutineStore : RoutineStore {

    private val routines = ConcurrentHashMap<String, Routine>()

    override suspend fun save(routine: Routine) {
        val id = routine.id.ifBlank { UUID.randomUUID().toString() }
        routines[id] = routine.copy(id = id)
    }

    override suspend fun get(id: String): Routine? = routines[id]

    override suspend fun getByName(name: String): Routine? =
        routines.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun delete(id: String): Boolean = routines.remove(id) != null

    override suspend fun listAll(): List<Routine> = routines.values.toList()
}
