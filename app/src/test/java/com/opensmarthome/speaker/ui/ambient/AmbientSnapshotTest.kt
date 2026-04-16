package com.opensmarthome.speaker.ui.ambient

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.Locale

class AmbientSnapshotTest {

    @Test
    fun `formattedTime uses 24h format`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 16, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val snapshot = AmbientSnapshot(nowMs = cal.timeInMillis)
        assertThat(snapshot.formattedTime(Locale.US)).isEqualTo("14:30")
    }

    @Test
    fun `formattedDate includes weekday`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 16, 14, 30)
        }
        val snapshot = AmbientSnapshot(nowMs = cal.timeInMillis)
        val date = snapshot.formattedDate(Locale.US)
        assertThat(date).contains("Apr")
        assertThat(date).contains("16")
    }

    @Test
    fun `greeting at 7am is good morning`() {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 7) }
        assertThat(AmbientSnapshot(cal.timeInMillis).greeting()).isEqualTo("Good morning")
    }

    @Test
    fun `greeting at 14 is good afternoon`() {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 14) }
        assertThat(AmbientSnapshot(cal.timeInMillis).greeting()).isEqualTo("Good afternoon")
    }

    @Test
    fun `greeting at 20 is good evening`() {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 20) }
        assertThat(AmbientSnapshot(cal.timeInMillis).greeting()).isEqualTo("Good evening")
    }

    @Test
    fun `greeting at 2am is good night`() {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 2) }
        assertThat(AmbientSnapshot(cal.timeInMillis).greeting()).isEqualTo("Good night")
    }

    @Test
    fun `default values are zero or empty`() {
        val snap = AmbientSnapshot(nowMs = 0L)
        assertThat(snap.activeNotificationCount).isEqualTo(0)
        assertThat(snap.unreadTaskCount).isEqualTo(0)
        assertThat(snap.activeTimerCount).isEqualTo(0)
        assertThat(snap.recentDeviceActivity).isEmpty()
        assertThat(snap.upcomingCalendarEvents).isEmpty()
    }
}
