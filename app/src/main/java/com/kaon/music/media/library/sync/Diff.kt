package com.kaon.music.media.library.sync

data class Diff<T>(
    val added: List<T>,
    val updated: List<T>,
    val removed: List<T>,
    val unchanged: Int
)

class DiffEngine<T, K>(
    private val keySelector: (T) -> K,
    private val contentEquals: (T, T) -> Boolean
) {
    fun diff(scanned: List<T>, existing: List<T>): Diff<T> {
        val existingMap = HashMap<K, T>(existing.size)
        for (item in existing) {
            existingMap[keySelector(item)] = item
        }

        val estimatedCapacity = maxOf(16, scanned.size / 10)
        val added = ArrayList<T>(estimatedCapacity)
        val updated = ArrayList<T>(estimatedCapacity)
        var unchangedCount = 0

        for (item in scanned) {
            val key = keySelector(item)
            val existingItem = existingMap.remove(key)
            if (existingItem == null) {
                added.add(item)
            } else if (!contentEquals(item, existingItem)) {
                updated.add(item)
            } else {
                unchangedCount++
            }
        }

        val removed = ArrayList<T>(existingMap.values)

        return Diff(added, updated, removed, unchangedCount)
    }
}
