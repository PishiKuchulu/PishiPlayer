package dev.pishi.player.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteActionMapperTest {
    private val mapper = RemoteActionMapper()

    @Test fun dedicatedPlayAndPauseKeysAreNotToggles() {
        assertEquals(RemoteAction.Play, mapper.map(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(RemoteAction.Pause, mapper.map(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(RemoteAction.TogglePlayback, mapper.map(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    @Test fun surfaceConfirmKeysTogglePlayback() {
        // The mapper is only consulted when the video surface holds focus; the same
        // keys on a focused button are the button's own click and never reach here.
        assertEquals(RemoteAction.TogglePlayback, mapper.map(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(RemoteAction.TogglePlayback, mapper.map(KeyEvent.KEYCODE_ENTER))
    }

    @Test fun firstSeekPressUsesShortStepAndRepeatsUseBoundedIntervalStep() {
        assertEquals(RemoteAction.Seek(10_000), mapper.map(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 0))
        assertEquals(RemoteAction.Seek(-10_000), mapper.map(KeyEvent.KEYCODE_DPAD_LEFT, repeatCount = 0))
        assertEquals(RemoteAction.Seek(5_000), mapper.map(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 1))
        // Even after many repeats a single event never seeks more than one interval step.
        assertEquals(RemoteAction.Seek(5_000), mapper.map(KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 40))
        assertEquals(RemoteAction.Seek(-5_000), mapper.map(KeyEvent.KEYCODE_MEDIA_REWIND, repeatCount = 7))
    }

    @Test fun chapterKeysSeekThirtySeconds() {
        assertEquals(RemoteAction.Seek(30_000), mapper.map(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(RemoteAction.Seek(-30_000), mapper.map(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
    }

    @Test fun navigationMenuAndBackKeys() {
        assertEquals(RemoteAction.Up, mapper.map(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(RemoteAction.Down, mapper.map(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(RemoteAction.Menu, mapper.map(KeyEvent.KEYCODE_MENU))
        assertEquals(RemoteAction.Menu, mapper.map(KeyEvent.KEYCODE_INFO))
        assertEquals(RemoteAction.Back, mapper.map(KeyEvent.KEYCODE_BACK))
    }

    @Test fun unknownKeysStayUnknown() {
        assertTrue(mapper.map(KeyEvent.KEYCODE_VOLUME_UP) is RemoteAction.Unknown)
        assertTrue(mapper.map(KeyEvent.KEYCODE_0) is RemoteAction.Unknown)
    }
}
