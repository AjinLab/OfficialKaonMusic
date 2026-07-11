package com.kaon.music

import com.kaon.music.media.manager.SleepTimerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

    private class TestClock : Clock() {
        private var timeMs = 1000L

        fun advanceBy(ms: Long) {
            timeMs += ms
        }

        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(timeMs)
        override fun millis(): Long = timeMs
    }

    @Test
    fun testStartTimerAndCountdown() {
        runTest {
            val testClock = TestClock()
            var expiredCalled = false
            val manager = SleepTimerManager(
                clock = testClock,
                scope = this,
                onTimerExpired = { expiredCalled = true }
            )

            manager.startTimer(5.seconds)
            val initial = manager.sleepTimerState.value
            assertTrue(initial.enabled)
            assertEquals(5.seconds, initial.remaining)
            assertFalse(initial.endOfSong)

            testClock.advanceBy(1000)
            testScheduler.advanceTimeBy(1000)
            testScheduler.runCurrent()
            
            val tick = manager.sleepTimerState.value
            println("Tick remaining: ${tick.remaining}, expected: 4.seconds")
            assertTrue(tick.enabled)
            assertEquals(4.seconds, tick.remaining)

            testClock.advanceBy(4000)
            testScheduler.advanceTimeBy(4000)
            testScheduler.runCurrent()

            val end = manager.sleepTimerState.value
            assertFalse(end.enabled)
            assertNull(end.remaining)
            assertTrue(expiredCalled)
        }
    }

    @Test
    fun testEndOfSongTimer() {
        runTest {
            val testClock = TestClock()
            val manager = SleepTimerManager(
                clock = testClock,
                scope = this,
                onTimerExpired = {}
            )

            manager.startEndOfSongTimer()
            val state = manager.sleepTimerState.value
            assertTrue(state.enabled)
            assertNull(state.remaining)
            assertTrue(state.endOfSong)

            manager.stopTimer()
            assertFalse(manager.sleepTimerState.value.enabled)
        }
    }
}
