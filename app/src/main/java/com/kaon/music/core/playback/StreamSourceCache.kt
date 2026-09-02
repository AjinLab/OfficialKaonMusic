package com.kaon.music.core.playback

/**
 * Bounded, generation-aware in-memory cache for resolved stream sources.
 *
 * Generation counters are the stale-write protection: `invalidate` bumps the
 * generation for a key, so a resolution that started before the invalidation
 * cannot repopulate the cache with its now-obsolete source when it finally
 * completes. Entries are evicted LRU-first and expire by timestamp.
 */
internal class StreamSourceCache<V : Any>(
    private val maxEntries: Int = 200,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be greater than zero" }
    }

    private data class Entry<V>(val value: V, val expiresAtMillis: Long)

    private val entries =
        object : LinkedHashMap<String, Entry<V>>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<V>>): Boolean =
                size > maxEntries
        }
    private val generations = HashMap<String, Long>()

    /**
     * Returns the generation for [key], registering it on first observation so a key that is
     * currently only in-flight (never yet written) is still visible to [invalidatePrefix].
     */
    @Synchronized
    fun generation(key: String): Long = generations.getOrPut(key) { 0L }

    /** Returns the live cached value, or null when absent or expired (expired entries are purged). */
    @Synchronized
    fun get(key: String): V? {
        val entry = entries[key] ?: return null
        if (entry.expiresAtMillis <= currentTimeMillis()) {
            entries.remove(key)
            advanceGeneration(key)
            return null
        }
        return entry.value
    }

    /**
     * Stores [value] only if the generation for [key] still equals [expectedGeneration].
     * Returns false when an invalidation raced the producing resolution.
     */
    @Synchronized
    fun put(key: String, value: V, expiresAtMillis: Long, expectedGeneration: Long = generation(key)): Boolean {
        if ((generations[key] ?: 0L) != expectedGeneration) return false
        entries[key] = Entry(value, expiresAtMillis)
        return true
    }

    /**
     * Invalidates every key starting with [prefix] and bumps each one's generation.
     * Keys known only through [generation] registration (in-flight, never written) are
     * included, so their stale completions are rejected too.
     */
    @Synchronized
    fun invalidatePrefix(prefix: String) {
        val doomed = (entries.keys.asSequence() + generations.keys.asSequence())
            .filter { it.startsWith(prefix) }
            .distinct()
            .toList()
        for (key in doomed) {
            entries.remove(key)
            advanceGeneration(key)
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        generations.clear()
    }

    private fun advanceGeneration(key: String) {
        generations[key] = (generations[key] ?: 0L) + 1L
    }
}
