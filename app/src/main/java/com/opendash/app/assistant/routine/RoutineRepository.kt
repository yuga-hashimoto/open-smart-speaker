package com.opendash.app.assistant.routine

/**
 * UI-facing CRUD wrapper over RoutineStore with convenience builders
 * for the routine-creation wizard.
 */
class RoutineRepository(
    private val store: RoutineStore
) {

    suspend fun all(): List<Routine> = store.listAll()

    suspend fun get(id: String): Routine? = store.get(id)

    suspend fun create(
        name: String,
        description: String,
        actions: List<RoutineAction>
    ): String {
        require(name.isNotBlank()) { "name must not be blank" }
        val routine = Routine(id = "", name = name, description = description, actions = actions)
        store.save(routine)
        // Find the saved routine (by name, since id was generated)
        return store.getByName(name)?.id ?: ""
    }

    suspend fun update(routine: Routine) {
        require(routine.id.isNotBlank()) { "id must not be blank" }
        require(routine.name.isNotBlank()) { "name must not be blank" }
        store.save(routine)
    }

    suspend fun delete(id: String): Boolean = store.delete(id)
}
