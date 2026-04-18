package com.opendash.app.a11y

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class A11yServiceHolderTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `starts with null serviceRef and null currentPackage`() = runTest {
        val holder = A11yServiceHolder(context)

        assertThat(holder.serviceRef).isNull()
        assertThat(holder.currentPackage.value).isNull()
        assertThat(holder.isConnected()).isFalse()
    }

    @Test
    fun `attach sets serviceRef and marks connected`() = runTest {
        val holder = A11yServiceHolder(context)
        val service: OpenDashA11yService = mockk(relaxed = true)

        holder.attach(service)

        assertThat(holder.serviceRef).isSameInstanceAs(service)
        assertThat(holder.isConnected()).isTrue()
    }

    @Test
    fun `detach with matching instance clears serviceRef and package`() = runTest {
        val holder = A11yServiceHolder(context)
        val service: OpenDashA11yService = mockk(relaxed = true)
        holder.attach(service)
        holder.updateCurrentPackage("com.example.foo")

        holder.detach(service)

        assertThat(holder.serviceRef).isNull()
        assertThat(holder.currentPackage.value).isNull()
        assertThat(holder.isConnected()).isFalse()
    }

    @Test
    fun `detach with mismatched instance is ignored`() = runTest {
        val holder = A11yServiceHolder(context)
        val first: OpenDashA11yService = mockk(relaxed = true)
        val second: OpenDashA11yService = mockk(relaxed = true)
        holder.attach(first)

        holder.detach(second)

        assertThat(holder.serviceRef).isSameInstanceAs(first)
        assertThat(holder.isConnected()).isTrue()
    }

    @Test
    fun `updateCurrentPackage emits transitions on currentPackage flow`() = runTest {
        val holder = A11yServiceHolder(context)

        holder.currentPackage.test {
            assertThat(awaitItem()).isNull()

            holder.updateCurrentPackage("com.example.one")
            assertThat(awaitItem()).isEqualTo("com.example.one")

            holder.updateCurrentPackage("com.example.two")
            assertThat(awaitItem()).isEqualTo("com.example.two")

            holder.updateCurrentPackage(null)
            assertThat(awaitItem()).isNull()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateCurrentPackage with same value does not re-emit`() = runTest {
        val holder = A11yServiceHolder(context)
        holder.updateCurrentPackage("com.example.app")

        holder.currentPackage.test {
            assertThat(awaitItem()).isEqualTo("com.example.app")
            holder.updateCurrentPackage("com.example.app") // duplicate
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
