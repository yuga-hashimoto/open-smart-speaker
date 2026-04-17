package com.opensmarthome.speaker.ui.home

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class GreetingResolverTest {

    private val en = Locale.ENGLISH
    private val ja = Locale.JAPANESE

    @Test
    fun `bucketForHour covers every hour`() {
        assertThat(GreetingResolver.bucketForHour(0)).isEqualTo(GreetingResolver.Bucket.NIGHT)
        assertThat(GreetingResolver.bucketForHour(4)).isEqualTo(GreetingResolver.Bucket.NIGHT)
        assertThat(GreetingResolver.bucketForHour(5)).isEqualTo(GreetingResolver.Bucket.MORNING)
        assertThat(GreetingResolver.bucketForHour(10)).isEqualTo(GreetingResolver.Bucket.MORNING)
        assertThat(GreetingResolver.bucketForHour(11)).isEqualTo(GreetingResolver.Bucket.AFTERNOON)
        assertThat(GreetingResolver.bucketForHour(16)).isEqualTo(GreetingResolver.Bucket.AFTERNOON)
        assertThat(GreetingResolver.bucketForHour(17)).isEqualTo(GreetingResolver.Bucket.EVENING)
        assertThat(GreetingResolver.bucketForHour(21)).isEqualTo(GreetingResolver.Bucket.EVENING)
        assertThat(GreetingResolver.bucketForHour(22)).isEqualTo(GreetingResolver.Bucket.NIGHT)
        assertThat(GreetingResolver.bucketForHour(23)).isEqualTo(GreetingResolver.Bucket.NIGHT)
    }

    @Test
    fun `bucketForHour coerces out-of-range values back into 0-23`() {
        assertThat(GreetingResolver.bucketForHour(-1)).isEqualTo(GreetingResolver.Bucket.NIGHT)
        assertThat(GreetingResolver.bucketForHour(99)).isEqualTo(GreetingResolver.Bucket.NIGHT)
    }

    @Test
    fun `english greetings`() {
        assertThat(GreetingResolver.greetingFor(8, en)).isEqualTo("Good morning")
        assertThat(GreetingResolver.greetingFor(14, en)).isEqualTo("Good afternoon")
        assertThat(GreetingResolver.greetingFor(19, en)).isEqualTo("Good evening")
        assertThat(GreetingResolver.greetingFor(23, en)).isEqualTo("Good night")
    }

    @Test
    fun `japanese greetings`() {
        assertThat(GreetingResolver.greetingFor(8, ja)).isEqualTo("おはようございます")
        assertThat(GreetingResolver.greetingFor(14, ja)).isEqualTo("こんにちは")
        assertThat(GreetingResolver.greetingFor(19, ja)).isEqualTo("こんばんは")
        assertThat(GreetingResolver.greetingFor(23, ja)).isEqualTo("お疲れさまでした")
    }

    @Test
    fun `unsupported locale falls back to english`() {
        val fi = Locale("fi")
        assertThat(GreetingResolver.greetingFor(8, fi)).isEqualTo("Good morning")
        assertThat(GreetingResolver.greetingFor(14, fi)).isEqualTo("Good afternoon")
    }
}
