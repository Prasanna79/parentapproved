package com.kidswatch.tv.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed class PlaybackCommand {
    object Stop : PlaybackCommand()
    object SkipNext : PlaybackCommand()
    object SkipPrev : PlaybackCommand()
    object TogglePause : PlaybackCommand()
}

object PlaybackCommandBus {
    private val _commands = MutableSharedFlow<PlaybackCommand>(replay = 0, extraBufferCapacity = 1)
    val commands: SharedFlow<PlaybackCommand> = _commands
    fun send(command: PlaybackCommand) { _commands.tryEmit(command) }
}
