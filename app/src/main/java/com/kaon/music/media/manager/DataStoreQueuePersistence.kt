package com.kaon.music.media.manager

import com.kaon.music.core.config.ConfigStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class DataStoreQueuePersistence(
    private val configStore: ConfigStore
) : QueuePersistence {

    private val KEY_QUEUE = "queue_snapshot"

    override suspend fun save(snapshot: QueueSnapshot) {
        val json = JSONObject().apply {
            put("currentIndex", snapshot.currentIndex)
            put("repeatMode", snapshot.repeatMode)
            put("shuffleMode", snapshot.shuffleMode)
            put("playbackPosition", snapshot.playbackPosition)
            put("currentSongId", snapshot.currentSongId)
            put("timestamp", snapshot.timestamp)

            val array = JSONArray()
            snapshot.songIds.forEach { array.put(it) }
            put("songIds", array)
        }

        configStore.putString(KEY_QUEUE, json.toString())
    }

    override suspend fun restore(): QueueSnapshot? {
        val jsonStr = configStore.getString(KEY_QUEUE).first()
        if (jsonStr.isEmpty()) return null

        return try {
            val json = JSONObject(jsonStr)
            val array = json.getJSONArray("songIds")
            val ids = mutableListOf<Long>()
            for (i in 0 until array.length()) {
                ids.add(array.getLong(i))
            }

            QueueSnapshot(
                songIds = ids,
                currentIndex = json.getInt("currentIndex"),
                repeatMode = json.optString("repeatMode", "OFF"),
                shuffleMode = json.optString("shuffleMode", "OFF"),
                playbackPosition = json.optLong("playbackPosition", 0L),
                currentSongId = json.optLong("currentSongId", -1L),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }
}
