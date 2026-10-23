package dev.pishi.player.input

import android.view.KeyEvent
import dev.pishi.player.api.SeekPolicy

sealed interface RemoteAction {
    data object TogglePlayback : RemoteAction
    data object Play : RemoteAction
    data object Pause : RemoteAction
    data object Back : RemoteAction
    data class Seek(val deltaMs: Long) : RemoteAction
    data object Confirm : RemoteAction
    data object Up : RemoteAction
    data object Down : RemoteAction
    data object Left : RemoteAction
    data object Right : RemoteAction
    data object Menu : RemoteAction
    data object Unknown : RemoteAction
}

/**
 * Stateless key-to-action mapping. This mapper is the single routing path for keys that
 * reach the video surface. It is only consulted when the video surface holds focus:
 * keys aimed at a focused button, slider, or dialog are handled by that control and
 * must never be routed here (in particular DPAD_CENTER/ENTER, which activate controls).
 *
 * Seek steps are bounded: the first press seeks [SeekPolicy.shortPressMs]; each key
 * repeat seeks [SeekPolicy.longPressIntervalMs]. Total seek therefore grows linearly
 * with hold time at a predictable rate instead of multiplying per repeat.
 */
class RemoteActionMapper(private val policy: SeekPolicy = SeekPolicy()) {
    fun map(keyCode: Int, repeatCount: Int = 0): RemoteAction = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> RemoteAction.TogglePlayback
        KeyEvent.KEYCODE_MEDIA_PLAY -> RemoteAction.Play
        KeyEvent.KEYCODE_MEDIA_PAUSE -> RemoteAction.Pause
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> RemoteAction.TogglePlayback
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> RemoteAction.Back
        KeyEvent.KEYCODE_DPAD_UP -> RemoteAction.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> RemoteAction.Down
        KeyEvent.KEYCODE_DPAD_LEFT -> RemoteAction.Seek(-seekStepMs(repeatCount))
        KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteAction.Seek(seekStepMs(repeatCount))
        KeyEvent.KEYCODE_MEDIA_REWIND -> RemoteAction.Seek(-seekStepMs(repeatCount))
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> RemoteAction.Seek(seekStepMs(repeatCount))
        KeyEvent.KEYCODE_MEDIA_NEXT -> RemoteAction.Seek(CHAPTER_SEEK_MS)
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> RemoteAction.Seek(-CHAPTER_SEEK_MS)
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> RemoteAction.Menu
        else -> RemoteAction.Unknown
    }

    private fun seekStepMs(repeatCount: Int): Long =
        if (repeatCount <= 0) policy.shortPressMs else policy.longPressIntervalMs

    private companion object { const val CHAPTER_SEEK_MS = 30_000L }
}
