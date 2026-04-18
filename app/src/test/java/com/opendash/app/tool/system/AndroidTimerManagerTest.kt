package com.opendash.app.tool.system

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * JVM unit tests for [AndroidTimerManager]'s firing lifecycle.
 *
 * The countdown timer itself relies on a live Android [android.os.Looper]
 * and is not exercised here; instead we drive [AndroidTimerManager.fireTimer]
 * directly to simulate the moment [android.os.CountDownTimer.onFinish] would
 * invoke it in production. [setTimer] is also skipped because
 * `CountDownTimer` construction requires a Looper.
 *
 * We hand-seed [AndroidTimerManager] entries via a "pre-fire" helper that
 * installs an entry as if `setTimer` had just succeeded, then call
 * `fireTimer` to transition to the ringing state.
 */
class AndroidTimerManagerTest {

    private class FakeAlarmPlayer : AlarmPlayer {
        var started = 0
        var stopped = 0
        override fun startLooping() {
            started += 1
        }
        override fun stop() {
            stopped += 1
        }
    }

    private class ImmediateScheduler : SafetyScheduler {
        data class Pending(val delayMs: Long, val action: () -> Unit, var cancelled: Boolean = false)
        val pending = mutableListOf<Pending>()
        override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
            val entry = Pending(delayMs, action)
            pending.add(entry)
            return { entry.cancelled = true }
        }
        fun runLast() {
            val last = pending.lastOrNull() ?: return
            if (!last.cancelled) last.action()
        }
    }

    private fun newManager(
        player: FakeAlarmPlayer = FakeAlarmPlayer(),
        scheduler: ImmediateScheduler = ImmediateScheduler(),
        maxFiringMs: Long = 5_000L
    ): Triple<AndroidTimerManager, FakeAlarmPlayer, ImmediateScheduler> {
        val context = mockk<Context>(relaxed = true)
        val factory = AlarmPlayerFactory { player }
        val mgr = AndroidTimerManager(
            context = context,
            alarmPlayerFactory = factory,
            safetyScheduler = scheduler,
            maxFiringMs = maxFiringMs
        )
        return Triple(mgr, player, scheduler)
    }

    /**
     * Seed an entry into [AndroidTimerManager] using reflection on the
     * private [activeTimers] map, bypassing [setTimer] (which needs a Looper).
     * This matches the state [setTimer] would produce minus the real
     * CountDownTimer; firing uses only the id so the absent CountDownTimer
     * is tolerated.
     */
    private fun seedCountingDownTimer(mgr: AndroidTimerManager, id: String, label: String, totalSeconds: Int) {
        val field = AndroidTimerManager::class.java.getDeclaredField("activeTimers").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val map = field.get(mgr) as java.util.concurrent.ConcurrentHashMap<String, Any>

        val activeTimerClass = Class.forName(
            "com.opendash.app.tool.system.AndroidTimerManager\$ActiveTimer"
        )
        val ctor = activeTimerClass.declaredConstructors.first().apply { isAccessible = true }
        // Params order: id, label, totalSeconds, startTimeMs, countDownTimer, isFiring, alarmPlayer, cancelSafety
        val entry = ctor.newInstance(
            id,
            label,
            totalSeconds,
            System.currentTimeMillis(),
            null, // countDownTimer
            false, // isFiring
            null, // alarmPlayer
            null // cancelSafety
        )
        map[id] = entry
    }

    @Test
    fun `firing a timer keeps it in active list with isFiring=true`() = runTest {
        val (mgr, player, scheduler) = newManager()
        seedCountingDownTimer(mgr, "tid1", "pasta", 60)

        mgr.fireTimer("tid1")

        val active = mgr.getActiveTimers()
        assertThat(active).hasSize(1)
        assertThat(active[0].id).isEqualTo("tid1")
        assertThat(active[0].isFiring).isTrue()
        assertThat(active[0].remainingSeconds).isEqualTo(0)
        assertThat(player.started).isEqualTo(1)
        assertThat(player.stopped).isEqualTo(0)
        assertThat(scheduler.pending).hasSize(1)
    }

    @Test
    fun `firing twice is idempotent - does not double-start the alarm`() = runTest {
        val (mgr, player, _) = newManager()
        seedCountingDownTimer(mgr, "tid1", "tea", 30)

        mgr.fireTimer("tid1")
        mgr.fireTimer("tid1")

        assertThat(player.started).isEqualTo(1)
    }

    @Test
    fun `cancelTimer while firing stops alarm and removes entry`() = runTest {
        val (mgr, player, scheduler) = newManager()
        seedCountingDownTimer(mgr, "tid1", "bread", 45)

        mgr.fireTimer("tid1")
        val cancelled = mgr.cancelTimer("tid1")

        assertThat(cancelled).isTrue()
        assertThat(mgr.getActiveTimers()).isEmpty()
        assertThat(player.stopped).isEqualTo(1)
        assertThat(scheduler.pending.first().cancelled).isTrue()
    }

    @Test
    fun `cancelAllTimers stops all firing alarms`() = runTest {
        val player1 = FakeAlarmPlayer()
        val player2 = FakeAlarmPlayer()
        val context = mockk<Context>(relaxed = true)
        val scheduler = ImmediateScheduler()
        val players = ArrayDeque(listOf<AlarmPlayer>(player1, player2))
        val factory = AlarmPlayerFactory { players.removeFirst() }
        val mgr = AndroidTimerManager(
            context = context,
            alarmPlayerFactory = factory,
            safetyScheduler = scheduler,
            maxFiringMs = 5_000L
        )

        seedCountingDownTimer(mgr, "tid1", "pasta", 10)
        seedCountingDownTimer(mgr, "tid2", "chicken", 20)

        mgr.fireTimer("tid1")
        mgr.fireTimer("tid2")

        val cancelled = mgr.cancelAllTimers()

        assertThat(cancelled).isEqualTo(2)
        assertThat(mgr.getActiveTimers()).isEmpty()
        assertThat(player1.stopped).isEqualTo(1)
        assertThat(player2.stopped).isEqualTo(1)
    }

    @Test
    fun `cancelTimer on unknown id returns false`() = runTest {
        val (mgr, _, _) = newManager()
        assertThat(mgr.cancelTimer("missing")).isFalse()
    }

    @Test
    fun `safety cap auto-stops alarm after max firing duration`() = runTest {
        val (mgr, player, scheduler) = newManager()
        seedCountingDownTimer(mgr, "tid1", "", 5)

        mgr.fireTimer("tid1")
        assertThat(scheduler.pending).hasSize(1)
        assertThat(scheduler.pending[0].delayMs).isEqualTo(5_000L)

        // Simulate the delay elapsing.
        scheduler.runLast()

        assertThat(mgr.getActiveTimers()).isEmpty()
        assertThat(player.stopped).isEqualTo(1)
    }

    @Test
    fun `counting-down timer reports isFiring=false in active list`() = runTest {
        val (mgr, _, _) = newManager()
        seedCountingDownTimer(mgr, "tid1", "soup", 120)

        val active = mgr.getActiveTimers()
        assertThat(active).hasSize(1)
        assertThat(active[0].isFiring).isFalse()
        assertThat(active[0].remainingSeconds).isAtMost(120)
    }
}
