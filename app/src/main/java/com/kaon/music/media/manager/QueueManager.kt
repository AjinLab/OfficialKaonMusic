package com.kaon.music.media.manager

import com.kaon.music.media.model.Song
import com.kaon.music.core.playback.QueueState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Stack

class QueueManager {
    private var queue = emptyList<Song>()
    private var currentIndex = -1
    private val history = Stack<Int>()

    private var repeatMode = RepeatMode.OFF
    private var shuffleMode = ShuffleMode.OFF
    private var shuffledIndices = emptyList<Int>()

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        updateQueueState()
    }

    private fun updateQueueState() {
        _queueState.value = QueueState(
            queue = queue,
            currentIndex = currentIndex,
            hasNext = hasNext(),
            hasPrevious = hasPrevious(),
            shuffle = shuffleMode == ShuffleMode.ON,
            repeatMode = repeatMode
        )
    }

    fun setQueue(newQueue: List<Song>, startIndex: Int = 0) {
        queue = newQueue
        updateShuffledIndices()
        history.clear()
        currentIndex = if (startIndex in queue.indices) startIndex else if (queue.isNotEmpty()) 0 else -1
        updateQueueState()
    }

    fun play(index: Int): Song? {
        if (index !in queue.indices) return null

        if (currentIndex != -1 && currentIndex != index) {
            pushHistory(currentIndex)
        }

        currentIndex = index
        updateQueueState()
        return queue[currentIndex]
    }

    fun playSong(song: Song): Song? {
        val index = queue.indexOfFirst { it.id == song.id }
        return if (index != -1) {
            play(index)
        } else {
            val newQueue = queue.toMutableList()
            newQueue.add(song)
            setQueue(newQueue, newQueue.size - 1)
            play(newQueue.size - 1)
        }
    }

    fun playNext(song: Song) {
        if (queue.isEmpty()) {
            setQueue(listOf(song))
            play(0)
            return
        }

        val newQueue = queue.toMutableList()
        val insertIndex = if (currentIndex != -1) currentIndex + 1 else newQueue.size
        newQueue.add(insertIndex, song)

        queue = newQueue
        updateShuffledIndices()

        val adjustedHistory = Stack<Int>()
        for (h in history) {
            if (h >= insertIndex) adjustedHistory.push(h + 1)
            else adjustedHistory.push(h)
        }
        history.clear()
        history.addAll(adjustedHistory)

        updateQueueState()
    }

    fun next(): Song? {
        val nextIndex = resolveNext()
        if (nextIndex == -1) return null
        return play(nextIndex)
    }

    fun previous(): Song? {
        val prevIndex = resolvePrevious()
        if (prevIndex == -1) return null

        currentIndex = prevIndex
        updateQueueState()
        return queue[currentIndex]
    }

    private fun resolveNext(): Int {
        if (queue.isEmpty()) return -1

        if (repeatMode == RepeatMode.ONE) {
            return currentIndex
        }

        if (shuffleMode == ShuffleMode.ON) {
            val currentShufflePos = shuffledIndices.indexOf(currentIndex)
            if (currentShufflePos == -1 || currentShufflePos == shuffledIndices.size - 1) {
                return if (repeatMode == RepeatMode.ALL) shuffledIndices.firstOrNull() ?: -1 else -1
            }
            return shuffledIndices[currentShufflePos + 1]
        }

        if (currentIndex + 1 >= queue.size) {
            return if (repeatMode == RepeatMode.ALL) 0 else -1
        }

        return currentIndex + 1
    }

    private fun resolvePrevious(): Int {
        if (queue.isEmpty()) return -1

        if (history.isNotEmpty()) {
            return history.pop()
        }

        if (repeatMode == RepeatMode.ONE) {
            return currentIndex
        }

        if (shuffleMode == ShuffleMode.ON) {
            val currentShufflePos = shuffledIndices.indexOf(currentIndex)
            if (currentShufflePos <= 0) {
                return if (repeatMode == RepeatMode.ALL) shuffledIndices.lastOrNull() ?: -1 else -1
            }
            return shuffledIndices[currentShufflePos - 1]
        }

        if (currentIndex <= 0) {
            return if (repeatMode == RepeatMode.ALL) queue.size - 1 else -1
        }

        return currentIndex - 1
    }

    fun setShuffle(enabled: Boolean) {
        val newMode = if (enabled) ShuffleMode.ON else ShuffleMode.OFF
        if (shuffleMode != newMode) {
            shuffleMode = newMode
            updateShuffledIndices()
            updateQueueState()
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        if (repeatMode != mode) {
            repeatMode = mode
            updateQueueState()
        }
    }

    fun toggleShuffle(): ShuffleMode {
        setShuffle(shuffleMode == ShuffleMode.OFF)
        return shuffleMode
    }

    fun nextRepeatMode(): RepeatMode {
        val nextMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(nextMode)
        return repeatMode
    }

    fun current(): Song? = queue.getOrNull(currentIndex)

    fun queue() = queue
    fun currentIndex() = currentIndex
    fun repeatMode() = repeatMode
    fun shuffleMode() = shuffleMode

    fun hasNext(): Boolean = resolveNext() != -1
    fun hasPrevious(): Boolean = resolvePrevious() != -1

    private fun updateShuffledIndices() {
        shuffledIndices = if (shuffleMode == ShuffleMode.ON) {
            val indices = queue.indices.toMutableList()
            if (currentIndex != -1 && indices.contains(currentIndex)) {
                indices.remove(currentIndex)
                indices.shuffle()
                listOf(currentIndex) + indices
            } else {
                indices.shuffled()
            }
        } else {
            queue.indices.toList()
        }
    }

    private fun pushHistory(index: Int) {
        history.push(index)
        if (history.size > 50) {
            history.removeElementAt(0)
        }
    }

    fun getSnapshot(playbackPosition: Long, currentSongId: Long): QueueSnapshot {
        return QueueSnapshot(
            songIds = queue.map { it.id },
            currentIndex = currentIndex,
            repeatMode = repeatMode.name,
            shuffleMode = shuffleMode.name,
            playbackPosition = playbackPosition,
            currentSongId = currentSongId
        )
    }



    fun restoreFromSnapshot(snapshot: QueueSnapshot, restoredQueue: List<Song>) {
        if (restoredQueue.isNotEmpty()) {
            queue = restoredQueue
            currentIndex = if (snapshot.currentIndex in restoredQueue.indices) snapshot.currentIndex else 0
            repeatMode = try { RepeatMode.valueOf(snapshot.repeatMode) } catch (e: Exception) { RepeatMode.OFF }
            shuffleMode = try { ShuffleMode.valueOf(snapshot.shuffleMode) } catch (e: Exception) { ShuffleMode.OFF }
            updateShuffledIndices()
            updateQueueState()
        }
    }

}