package com.opendash.app.multiroom

import com.google.common.truth.Truth.assertThat
import com.opendash.app.data.db.SpeakerGroupDao
import com.opendash.app.data.db.SpeakerGroupEntity
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * DAO is mocked (same approach as [RoomRoutineStoreTest] — our unit
 * suite doesn't spin up a real Room instance because Room's JVM test
 * variant pulls in androidx runtime deps that we don't want in the unit
 * classpath). The assertions focus on what the repository owns —
 * JSON (de)serialisation and the domain <-> entity mapping.
 */
class SpeakerGroupRepositoryTest {

    private val moshi = Moshi.Builder().build()

    private fun repo(dao: SpeakerGroupDao) = SpeakerGroupRepository(dao, moshi)

    @Test
    fun `save encodes member service names as JSON and upserts`() = runTest {
        val dao = mockk<SpeakerGroupDao>(relaxed = true)
        val slot = slot<SpeakerGroupEntity>()
        coEvery { dao.upsert(capture(slot)) } returns Unit

        repo(dao).save(
            SpeakerGroup(
                name = "kitchen",
                memberServiceNames = setOf("speaker-a", "speaker-b")
            )
        )

        assertThat(slot.captured.name).isEqualTo("kitchen")
        // JSON is an array of strings — order within a set isn't guaranteed,
        // so just assert both values are present.
        assertThat(slot.captured.memberServiceNames).contains("speaker-a")
        assertThat(slot.captured.memberServiceNames).contains("speaker-b")
        assertThat(slot.captured.updatedAtMs).isGreaterThan(0L)
    }

    @Test
    fun `save rejects blank name`() = runTest {
        val dao = mockk<SpeakerGroupDao>(relaxed = true)
        assertThrows<IllegalArgumentException> {
            repo(dao).save(SpeakerGroup(name = "   ", memberServiceNames = emptySet()))
        }
    }

    @Test
    fun `get decodes members back into a set`() = runTest {
        val dao = mockk<SpeakerGroupDao>()
        coEvery { dao.getByName("kitchen") } returns SpeakerGroupEntity(
            name = "kitchen",
            memberServiceNames = """["a","b","b"]""",
            updatedAtMs = 1L
        )

        val group = repo(dao).get("kitchen")

        assertThat(group).isNotNull()
        // Dupe "b" in the JSON should collapse to a single entry in the set.
        assertThat(group!!.memberServiceNames).containsExactly("a", "b")
    }

    @Test
    fun `get returns null for missing group`() = runTest {
        val dao = mockk<SpeakerGroupDao>()
        coEvery { dao.getByName(any()) } returns null
        assertThat(repo(dao).get("nope")).isNull()
    }

    @Test
    fun `list maps every row to the domain model`() = runTest {
        val dao = mockk<SpeakerGroupDao>()
        coEvery { dao.listAll() } returns listOf(
            SpeakerGroupEntity("a", """["x"]""", 1L),
            SpeakerGroupEntity("b", """[]""", 2L)
        )

        val groups = repo(dao).list()
        assertThat(groups.map { it.name }).containsExactly("a", "b")
        assertThat(groups[0].memberServiceNames).containsExactly("x")
        assertThat(groups[1].memberServiceNames).isEmpty()
    }

    @Test
    fun `delete returns true only when a row was removed`() = runTest {
        val dao = mockk<SpeakerGroupDao>()
        coEvery { dao.delete("kitchen") } returns 1
        coEvery { dao.delete("ghost") } returns 0

        assertThat(repo(dao).delete("kitchen")).isTrue()
        assertThat(repo(dao).delete("ghost")).isFalse()
    }

    @Test
    fun `corrupted JSON yields empty members instead of crashing`() = runTest {
        val dao = mockk<SpeakerGroupDao>()
        coEvery { dao.getByName("broken") } returns SpeakerGroupEntity(
            name = "broken",
            memberServiceNames = "not-json",
            updatedAtMs = 0L
        )

        val group = repo(dao).get("broken")
        assertThat(group).isNotNull()
        assertThat(group!!.memberServiceNames).isEmpty()
    }

    @Test
    fun `flow forwards dao rows mapped to domain`() = runTest {
        val dao = mockk<SpeakerGroupDao>()
        every { dao.observeAll() } returns flowOf(
            listOf(SpeakerGroupEntity("kitchen", """["a"]""", 1L))
        )

        val emitted = repo(dao).flow().toList()
        assertThat(emitted).hasSize(1)
        assertThat(emitted.first().map { it.name }).containsExactly("kitchen")
    }

    @Test
    fun `round trip preserves members`() = runTest {
        val dao = mockk<SpeakerGroupDao>()
        val saved = slot<SpeakerGroupEntity>()
        coEvery { dao.upsert(capture(saved)) } returns Unit
        coEvery { dao.getByName("kitchen") } answers { saved.captured }

        val r = repo(dao)
        r.save(SpeakerGroup("kitchen", setOf("sp-a", "sp-b", "sp-c")))
        val fetched = r.get("kitchen")

        assertThat(fetched!!.memberServiceNames)
            .containsExactly("sp-a", "sp-b", "sp-c")
        coVerify { dao.upsert(any()) }
    }
}
