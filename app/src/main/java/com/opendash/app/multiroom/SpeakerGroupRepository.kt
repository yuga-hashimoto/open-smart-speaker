package com.opendash.app.multiroom

import com.opendash.app.data.db.SpeakerGroupDao
import com.opendash.app.data.db.SpeakerGroupEntity
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CRUD wrapper around [SpeakerGroupDao]. Handles JSON (de)serialisation of
 * the member `serviceName` set so callers can work in terms of
 * [SpeakerGroup].
 *
 * Groups are device-local (see ADR); this repo is the single source of
 * truth for them. UI uses [flow] to observe live updates, the broadcaster
 * calls [get] on a per-send basis.
 */
@Singleton
class SpeakerGroupRepository @Inject constructor(
    private val dao: SpeakerGroupDao,
    moshi: Moshi
) {

    private val listAdapter: JsonAdapter<List<String>> = moshi.adapter(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    suspend fun list(): List<SpeakerGroup> =
        dao.listAll().map { it.toDomain() }

    suspend fun get(name: String): SpeakerGroup? =
        dao.getByName(name)?.toDomain()

    suspend fun save(group: SpeakerGroup) {
        require(group.name.isNotBlank()) { "group name must not be blank" }
        dao.upsert(
            SpeakerGroupEntity(
                name = group.name,
                memberServiceNames = listAdapter.toJson(group.memberServiceNames.toList()),
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(name: String): Boolean = dao.delete(name) > 0

    fun flow(): Flow<List<SpeakerGroup>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    private fun SpeakerGroupEntity.toDomain(): SpeakerGroup {
        val members = try {
            listAdapter.fromJson(memberServiceNames) ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse speaker group $name — treating as empty")
            emptyList()
        }
        return SpeakerGroup(
            name = name,
            memberServiceNames = members.toSet()
        )
    }
}
