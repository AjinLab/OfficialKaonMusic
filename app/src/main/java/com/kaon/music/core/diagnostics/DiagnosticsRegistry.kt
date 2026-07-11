package com.kaon.music.core.diagnostics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant

sealed interface DiagnosticValue {
    data class Number(val value: kotlin.Number) : DiagnosticValue
    data class Percentage(val fraction: Double) : DiagnosticValue
    data class Duration(val duration: kotlin.time.Duration) : DiagnosticValue
    data class Timestamp(val epochMs: Long) : DiagnosticValue
    data class Text(val message: String) : DiagnosticValue
}

interface DiagnosticsProvider {
    val id: String
    val displayName: String
    val diagnostics: StateFlow<Map<String, DiagnosticValue>>
}

data class ProviderSnapshot(
    val id: String,
    val displayName: String,
    val diagnostics: Map<String, DiagnosticValue>
)

data class DiagnosticsSnapshot(
    val providers: List<ProviderSnapshot>,
    val timestamp: Instant
)

@OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class,
    kotlinx.coroutines.InternalCoroutinesApi::class
)
class DiagnosticsRegistry(private val clock: Clock) {
    private val providers = MutableStateFlow<List<DiagnosticsProvider>>(emptyList())

    val diagnosticsSnapshot: StateFlow<DiagnosticsSnapshot> = object : StateFlow<DiagnosticsSnapshot> {
        override val value: DiagnosticsSnapshot
            get() = DiagnosticsSnapshot(
                providers.value.map { provider ->
                    ProviderSnapshot(provider.id, provider.displayName, provider.diagnostics.value)
                },
                clock.instant()
            )

        override val replayCache: List<DiagnosticsSnapshot> get() = listOf(value)

        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<DiagnosticsSnapshot>): Nothing {
            collector.emit(value)
            while (currentCoroutineContext().isActive) {
                delay(2000)
                collector.emit(value)
            }
            awaitCancellation()
        }
    }

    fun register(provider: DiagnosticsProvider) {
        providers.update { it + provider }
    }
    
    fun unregister(provider: DiagnosticsProvider) {
        providers.update { it - provider }
    }

    fun provider(id: String): Flow<ProviderSnapshot>? {
        val activeList = providers.value
        val found = activeList.firstOrNull { it.id == id } ?: return null
        return found.diagnostics.map { map ->
            ProviderSnapshot(found.id, found.displayName, map)
        }
    }

    fun dump(): String {
        val snapshot = diagnosticsSnapshot.value
        val sb = StringBuilder()
        sb.append("--- Diagnostics Dump @ ${snapshot.timestamp} ---\n")
        for (provider in snapshot.providers) {
            sb.append("[${provider.displayName} (${provider.id})]\n")
            for ((key, value) in provider.diagnostics) {
                sb.append("  $key: $value\n")
            }
        }
        return sb.toString()
    }
}
