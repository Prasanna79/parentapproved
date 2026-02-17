package com.kidswatch.tv.playback

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class DpadKeyHandlerTest {

    @Test
    fun dpadCenter_returnsTogglePause() {
        assertEquals(PlaybackCommand.TogglePause, DpadKeyHandler.mapKeyToCommand(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun mediaPlayPause_returnsTogglePause() {
        assertEquals(PlaybackCommand.TogglePause, DpadKeyHandler.mapKeyToCommand(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    @Test
    fun dpadRight_returnsNull() {
        assertNull(DpadKeyHandler.mapKeyToCommand(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun mediaNext_returnsSkipNext() {
        assertEquals(PlaybackCommand.SkipNext, DpadKeyHandler.mapKeyToCommand(KeyEvent.KEYCODE_MEDIA_NEXT))
    }

    @Test
    fun dpadLeft_returnsNull() {
        assertNull(DpadKeyHandler.mapKeyToCommand(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun back_returnsStop() {
        assertEquals(PlaybackCommand.Stop, DpadKeyHandler.mapKeyToCommand(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun unknownKey_returnsNull() {
        assertNull(DpadKeyHandler.mapKeyToCommand(KeyEvent.KEYCODE_A))
    }
}
