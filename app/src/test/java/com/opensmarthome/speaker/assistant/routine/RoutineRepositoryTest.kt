package com.opensmarthome.speaker.assistant.routine

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoutineRepositoryTest {

    private fun repo() = RoutineRepository(InMemoryRoutineStore())

    private fun sampleActions() = listOf(
        RoutineAction(
            toolName = "execute_command",
            arguments = mapOf("device_type" to "light", "action" to "turn_off")
        )
    )

    @Test
    fun `create assigns an id and stores the routine`() = runTest {
        val repo = repo()
        val id = repo.create("goodnight", "all off", sampleActions())
        assertThat(id).isNotEmpty()

        val stored = repo.get(id)
        assertThat(stored).isNotNull()
        assertThat(stored!!.name).isEqualTo("goodnight")
        assertThat(stored.description).isEqualTo("all off")
        assertThat(stored.actions).hasSize(1)
    }

    @Test
    fun `create rejects blank name`() = runTest {
        val repo = repo()
        assertThrows<IllegalArgumentException> {
            repo.create("   ", "x", sampleActions())
        }
    }

    @Test
    fun `all returns every saved routine`() = runTest {
        val repo = repo()
        repo.create("a", "", sampleActions())
        repo.create("b", "", sampleActions())
        repo.create("c", "", sampleActions())

        val names = repo.all().map { it.name }
        assertThat(names).containsExactly("a", "b", "c")
    }

    @Test
    fun `update overwrites an existing routine`() = runTest {
        val repo = repo()
        val id = repo.create("goodnight", "v1", sampleActions())
        val updated = Routine(
            id = id,
            name = "goodnight",
            description = "v2",
            actions = sampleActions() + RoutineAction("set_volume", mapOf("level" to 20))
        )

        repo.update(updated)

        val stored = repo.get(id)!!
        assertThat(stored.description).isEqualTo("v2")
        assertThat(stored.actions).hasSize(2)
    }

    @Test
    fun `update rejects blank id`() = runTest {
        val repo = repo()
        val r = Routine(id = "", name = "x", description = "", actions = sampleActions())
        assertThrows<IllegalArgumentException> { repo.update(r) }
    }

    @Test
    fun `update rejects blank name`() = runTest {
        val repo = repo()
        val id = repo.create("original", "", sampleActions())
        val r = Routine(id = id, name = "   ", description = "", actions = sampleActions())
        assertThrows<IllegalArgumentException> { repo.update(r) }
    }

    @Test
    fun `delete removes a routine and returns true`() = runTest {
        val repo = repo()
        val id = repo.create("gone", "", sampleActions())
        assertThat(repo.delete(id)).isTrue()
        assertThat(repo.get(id)).isNull()
    }

    @Test
    fun `delete missing returns false`() = runTest {
        val repo = repo()
        assertThat(repo.delete("does-not-exist")).isFalse()
    }

    @Test
    fun `get missing id returns null`() = runTest {
        val repo = repo()
        assertThat(repo.get("nope")).isNull()
    }
}
