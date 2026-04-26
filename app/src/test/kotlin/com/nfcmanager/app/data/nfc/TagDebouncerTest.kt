package com.nfcmanager.app.data.nfc

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class TagDebouncerTest {

    private var now: Long = 1_000
    private lateinit var debouncer: TagDebouncer

    @Before
    fun setup() {
        now = 1_000L
        debouncer = TagDebouncer().apply { clock = { now } }
    }

    @Test fun `first occurrence of a uid is processed`() {
        assertThat(debouncer.shouldProcess("ABCDEF")).isTrue()
    }

    @Test fun `duplicate within window is suppressed`() {
        debouncer.setWindow(2_500)
        assertThat(debouncer.shouldProcess("AA")).isTrue()
        now += 500
        assertThat(debouncer.shouldProcess("AA")).isFalse()
    }

    @Test fun `duplicate after window is allowed`() {
        debouncer.setWindow(1_000)
        assertThat(debouncer.shouldProcess("BB")).isTrue()
        now += 2_000
        assertThat(debouncer.shouldProcess("BB")).isTrue()
    }

    @Test fun `different uids do not interfere`() {
        debouncer.setWindow(5_000)
        assertThat(debouncer.shouldProcess("AA")).isTrue()
        assertThat(debouncer.shouldProcess("BB")).isTrue()
        assertThat(debouncer.shouldProcess("CC")).isTrue()
    }

    @Test fun `empty uid is always allowed`() {
        assertThat(debouncer.shouldProcess("")).isTrue()
        assertThat(debouncer.shouldProcess("")).isTrue()
    }

    @Test fun `reset clears state`() {
        debouncer.setWindow(10_000)
        debouncer.shouldProcess("AA")
        debouncer.reset()
        assertThat(debouncer.shouldProcess("AA")).isTrue()
    }
}
