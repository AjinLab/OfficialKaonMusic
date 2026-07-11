package com.kaon.music.core.metrics

import android.app.Activity
import androidx.metrics.performance.JankStats
import com.kaon.music.core.diagnostics.DiagnosticsProvider
import com.kaon.music.core.diagnostics.DiagnosticValue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

data class FrameMetrics(
    val averageFps: Double,
    val percentile99Ms: Double,
    val jankPercentage: Double,
    val worstFrameMs: Long
)

data class JankMetricsSnapshot(
    val rolling: FrameMetrics,
    val lifetime: FrameMetrics
)

data class RollingWindow(val frameCount: Int = 300)

@OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class,
    kotlinx.coroutines.InternalCoroutinesApi::class
)
class JankStatsMonitor(val window: RollingWindow) : DiagnosticsProvider {

    override val id: String = "jank"
    override val displayName: String = "Jank Monitor"

    val metrics: StateFlow<JankMetricsSnapshot> = object : StateFlow<JankMetricsSnapshot> {
        override val value: JankMetricsSnapshot
            get() = synchronized(this@JankStatsMonitor) {
                JankMetricsSnapshot(computeRollingMetrics(), computeLifetimeMetrics())
            }

        override val replayCache: List<JankMetricsSnapshot> get() = listOf(value)

        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<JankMetricsSnapshot>): Nothing {
            collector.emit(value)
            while (currentCoroutineContext().isActive) {
                delay(2000)
                collector.emit(value)
            }
            awaitCancellation()
        }
    }

    override val diagnostics: StateFlow<Map<String, DiagnosticValue>> = object : StateFlow<Map<String, DiagnosticValue>> {
        override val value: Map<String, DiagnosticValue>
            get() = synchronized(this@JankStatsMonitor) {
                val rolling = computeRollingMetrics()
                val lifetime = computeLifetimeMetrics()
                mapOf(
                    "rollingFps" to DiagnosticValue.Number(rolling.averageFps),
                    "rollingJankPct" to DiagnosticValue.Percentage(rolling.jankPercentage / 100.0),
                    "rollingP99Ms" to DiagnosticValue.Number(rolling.percentile99Ms),
                    "rollingWorstFrameMs" to DiagnosticValue.Number(rolling.worstFrameMs),
                    "lifetimeFps" to DiagnosticValue.Number(lifetime.averageFps),
                    "lifetimeJankPct" to DiagnosticValue.Percentage(lifetime.jankPercentage / 100.0),
                    "lifetimeWorstFrameMs" to DiagnosticValue.Number(lifetime.worstFrameMs)
                )
            }

        override val replayCache: List<Map<String, DiagnosticValue>> get() = listOf(value)

        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<Map<String, DiagnosticValue>>): Nothing {
            collector.emit(value)
            while (currentCoroutineContext().isActive) {
                delay(2000)
                collector.emit(value)
            }
            awaitCancellation()
        }
    }

    private val rollingFrameDurations = ArrayDeque<Long>(window.frameCount)
    private val rollingJankFlags = ArrayDeque<Boolean>(window.frameCount)

    private var lifetimeFrameCount = 0L
    private var lifetimeJankCount = 0L
    private var lifetimeWorstFrameNs = 0L
    private var lifetimeTotalDurationNs = 0L

    private var jankStats: JankStats? = null

    fun startTracking(activity: Activity) {
        jankStats = JankStats.createAndTrack(activity.window) { frameData ->
            val durationNs = frameData.frameDurationUiNanos
            val isJank = frameData.isJank

            synchronized(this) {
                lifetimeFrameCount++
                if (isJank) lifetimeJankCount++
                if (durationNs > lifetimeWorstFrameNs) {
                    lifetimeWorstFrameNs = durationNs
                }
                lifetimeTotalDurationNs += durationNs

                if (rollingFrameDurations.size >= window.frameCount) {
                    rollingFrameDurations.pollFirst()
                    rollingJankFlags.pollFirst()
                }
                rollingFrameDurations.addLast(durationNs)
                rollingJankFlags.addLast(isJank)
            }
        }
        jankStats?.isTrackingEnabled = true
    }

    fun stopTracking() {
        jankStats?.isTrackingEnabled = false
        jankStats = null
    }

    private fun computeRollingMetrics(): FrameMetrics {
        if (rollingFrameDurations.isEmpty()) return FrameMetrics(0.0, 0.0, 0.0, 0L)
        val size = rollingFrameDurations.size
        val avgDurationNs = rollingFrameDurations.average()
        val jankCount = rollingJankFlags.count { it }
        val jankPercent = (jankCount.toDouble() / size) * 100.0
        val worst = rollingFrameDurations.maxOrNull() ?: 0L

        val sorted = rollingFrameDurations.sorted()
        val idx99 = (size * 0.99).toInt().coerceAtMost(size - 1)
        val p99Ns = sorted[idx99]

        val fps = 1_000_000_000.0 / avgDurationNs
        return FrameMetrics(
            averageFps = fps,
            percentile99Ms = p99Ns.toDouble() / 1_000_000.0,
            jankPercentage = jankPercent,
            worstFrameMs = worst / 1_000_000
        )
    }

    private fun computeLifetimeMetrics(): FrameMetrics {
        if (lifetimeFrameCount == 0L) return FrameMetrics(0.0, 0.0, 0.0, 0L)
        val avgDurationNs = lifetimeTotalDurationNs.toDouble() / lifetimeFrameCount
        val jankPercent = (lifetimeJankCount.toDouble() / lifetimeFrameCount) * 100.0
        val fps = 1_000_000_000.0 / avgDurationNs

        return FrameMetrics(
            averageFps = fps,
            percentile99Ms = avgDurationNs / 1_000_000.0,
            jankPercentage = jankPercent,
            worstFrameMs = lifetimeWorstFrameNs / 1_000_000
        )
    }
}
