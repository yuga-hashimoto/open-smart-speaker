package com.opendash.app.assistant.routine

import com.opendash.app.data.db.RoutineDao
import com.opendash.app.data.db.RoutineEntity
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import timber.log.Timber
import java.util.UUID

/**
 * Room-backed implementation of RoutineStore that persists routines
 * across app restarts.
 *
 * Action list is serialized as JSON using Moshi.
 */
class RoomRoutineStore(
    private val dao: RoutineDao,
    moshi: Moshi
) : RoutineStore {

    private val actionListAdapter: JsonAdapter<List<RoutineAction>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, RoutineAction::class.java)
    )

    override suspend fun save(routine: Routine) {
        val id = routine.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(
            RoutineEntity(
                id = id,
                name = routine.name,
                description = routine.description,
                actionsJson = actionListAdapter.toJson(routine.actions),
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    override suspend fun get(id: String): Routine? = dao.get(id)?.toDomain()

    override suspend fun getByName(name: String): Routine? = dao.getByName(name)?.toDomain()

    override suspend fun delete(id: String): Boolean = dao.delete(id) > 0

    override suspend fun listAll(): List<Routine> = dao.listAll().mapNotNull { it.toDomain() }

    private fun RoutineEntity.toDomain(): Routine? {
        val actions = try {
            actionListAdapter.fromJson(actionsJson) ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse routine actions for $id")
            emptyList()
        }
        return Routine(
            id = id,
            name = name,
            description = description,
            actions = actions
        )
    }
}
