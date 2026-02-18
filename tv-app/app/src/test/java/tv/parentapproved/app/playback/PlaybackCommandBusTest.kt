package tv.parentapproved.app.playback

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlaybackCommandBusTest {

    @Test
    fun emit_stop_isCollectedBySubscriber() = runTest {
        val bus = PlaybackCommandBus
        var received: PlaybackCommand? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            received = bus.commands.first()
        }
        bus.send(PlaybackCommand.Stop)
        job.join()
        assertEquals(PlaybackCommand.Stop, received)
    }

    @Test
    fun emit_skipNext_isCollectedBySubscriber() = runTest {
        val bus = PlaybackCommandBus
        var received: PlaybackCommand? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            received = bus.commands.first()
        }
        bus.send(PlaybackCommand.SkipNext)
        job.join()
        assertEquals(PlaybackCommand.SkipNext, received)
    }

    @Test
    fun emit_togglePause_isCollectedBySubscriber() = runTest {
        val bus = PlaybackCommandBus
        var received: PlaybackCommand? = null
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            received = bus.commands.first()
        }
        bus.send(PlaybackCommand.TogglePause)
        job.join()
        assertEquals(PlaybackCommand.TogglePause, received)
    }

    @Test
    fun emit_whenNoCollector_doesNotBlock() = runTest {
        // Should not throw or deadlock — fire-and-forget
        PlaybackCommandBus.send(PlaybackCommand.Stop)
        PlaybackCommandBus.send(PlaybackCommand.SkipNext)
        PlaybackCommandBus.send(PlaybackCommand.TogglePause)
        // If we get here, it didn't block
        assertTrue(true)
    }

    @Test
    fun multipleCommands_deliveredInOrder() = runTest {
        val bus = PlaybackCommandBus
        val received = mutableListOf<PlaybackCommand>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.commands.take(3).toList(received)
        }
        bus.send(PlaybackCommand.Stop)
        bus.send(PlaybackCommand.SkipNext)
        bus.send(PlaybackCommand.TogglePause)
        job.join()
        assertEquals(
            listOf(PlaybackCommand.Stop, PlaybackCommand.SkipNext, PlaybackCommand.TogglePause),
            received,
        )
    }
}
