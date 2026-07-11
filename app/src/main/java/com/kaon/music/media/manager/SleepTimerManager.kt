package com.kaon.music.media.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class SleepTimerState(
    val enabled: Boolean,
    val remaining: Duration? = null,
    val endOfSong: Boolean = false
)

class SleepTimerManager(
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val onTimerExpired: () -> Unit
) {
    private val _sleepTimerState = MutableStateFlow(SleepTimerState(enabled = false))
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private var timerJob: Job? = null
    private var endTimeMs: Long? = null

    fun startTimer(duration: Duration) {
        timerJob?.cancel()
        val now = clock.millis()
        endTimeMs = now + duration.inWholeMilliseconds

        _sleepTimerState.update {
            SleepTimerState(
                enabled = true,
                remaining = duration,
                endOfSong = false
            )
        }

        timerJob = scope.launch {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                delay(1000)
                val remainingMs = (endTimeMs ?: 0L) - clock.millis()
                if (remainingMs <= 0) {
                    _sleepTimerState.value = SleepTimerState(enabled = false)
                    onTimerExpired()
                    break
                } else {
                    _sleepTimerState.update {
                        it.copy(remaining = remainingMs.milliseconds)
                    }
                }
            }
        }
    }

    fun startEndOfSongTimer() {
        timerJob?.cancel()
        endTimeMs = null
        _sleepTimerState.value = SleepTimerState(
            enabled = true,
            remaining = null,
            endOfSong = true
        )
    }

    fun stopTimer() {
        timerJob?.cancel()
        endTimeMs = null
        _sleepTimerState.value = SleepTimerState(enabled = false)
    }
}
