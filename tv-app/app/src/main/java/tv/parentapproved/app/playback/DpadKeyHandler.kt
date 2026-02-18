package tv.parentapproved.app.playback

import android.view.KeyEvent

object DpadKeyHandler {
    fun mapKeyToCommand(keyCode: Int): PlaybackCommand? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlaybackCommand.TogglePause

        KeyEvent.KEYCODE_MEDIA_NEXT -> PlaybackCommand.SkipNext

        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlaybackCommand.SkipPrev

        KeyEvent.KEYCODE_BACK -> PlaybackCommand.Stop

        else -> null
    }
}
