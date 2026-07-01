package com.kaon.music.media.engine

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import com.kaon.music.core.playback.PlayerController

class KaonForwardingPlayer(
    player: Player,
    private val controller: PlayerController
) : ForwardingPlayer(player) {

    override fun hasNextMediaItem(): Boolean {
        return controller.currentQueue().value.hasNext
    }

    override fun seekToNextMediaItem() {
        controller.next()
    }

    override fun seekToNext() {
        controller.next()
    }

    override fun hasPreviousMediaItem(): Boolean {
        return controller.currentQueue().value.hasPrevious
    }

    override fun seekToPreviousMediaItem() {
        controller.previous()
    }

    override fun seekToPrevious() {
        controller.previous()
    }

    override fun getShuffleModeEnabled(): Boolean {
        return controller.currentQueue().value.shuffle
    }

    override fun getRepeatMode(): Int {
        return when (controller.currentQueue().value.repeatMode) {
            com.kaon.music.media.manager.RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            com.kaon.music.media.manager.RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            com.kaon.music.media.manager.RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        controller.setShuffle(shuffleModeEnabled)
    }

    override fun setRepeatMode(repeatMode: Int) {
        val mode = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> com.kaon.music.media.manager.RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> com.kaon.music.media.manager.RepeatMode.ALL
            else -> com.kaon.music.media.manager.RepeatMode.OFF
        }
        controller.setRepeatMode(mode)
    }

    override fun getAvailableCommands(): Player.Commands {
        val state = controller.currentQueue().value
        return super.getAvailableCommands().buildUpon().apply {
            add(Player.COMMAND_SET_SHUFFLE_MODE)
            add(Player.COMMAND_SET_REPEAT_MODE)
            
            if (state.hasNext) {
                add(Player.COMMAND_SEEK_TO_NEXT)
                add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            } else {
                remove(Player.COMMAND_SEEK_TO_NEXT)
                remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            if (state.hasPrevious) {
                add(Player.COMMAND_SEEK_TO_PREVIOUS)
                add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            } else {
                remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }
        }.build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        val state = controller.currentQueue().value
        return when (command) {
            Player.COMMAND_SET_SHUFFLE_MODE, Player.COMMAND_SET_REPEAT_MODE -> true
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> state.hasNext
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> state.hasPrevious
            else -> super.isCommandAvailable(command)
        }
    }
}
