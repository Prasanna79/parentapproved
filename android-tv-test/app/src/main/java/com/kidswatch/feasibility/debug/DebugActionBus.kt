package com.kidswatch.feasibility.debug

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class DebugAction {
    // Navigation
    data class Navigate(val route: String) : DebugAction()

    // Test 5: Playlist
    data class ResolvePlaylist(val index: Int) : DebugAction()

    // Test 6: Stream Quality
    data class ExtractStreams(val index: Int) : DebugAction()
    data class PlayProgressive(val resolution: String) : DebugAction()
    data class PlayMerged(val resolution: String) : DebugAction()
    object StopPlayer : DebugAction()

    // Common
    object ClearLogs : DebugAction()
}

object DebugActionBus {
    private val _actions = MutableSharedFlow<DebugAction>(extraBufferCapacity = 10)
    val actions = _actions.asSharedFlow()

    fun post(action: DebugAction) {
        _actions.tryEmit(action)
    }
}
