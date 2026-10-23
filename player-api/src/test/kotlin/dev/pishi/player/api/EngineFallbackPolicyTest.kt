package dev.pishi.player.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineFallbackPolicyTest {

    @Test
    fun `auto and hardware start on media3`() {
        assertEquals(EngineKind.MEDIA3, EngineFallbackPolicy.initialKind(EnginePreference.AUTO))
        assertEquals(EngineKind.MEDIA3, EngineFallbackPolicy.initialKind(EnginePreference.HARDWARE))
    }

    @Test
    fun `software preference starts on mpv`() {
        assertEquals(EngineKind.MPV, EngineFallbackPolicy.initialKind(EnginePreference.SOFTWARE))
    }

    @Test
    fun `decoder init failure falls back to mpv in auto`() {
        assertEquals(
            EngineKind.MPV,
            EngineFallbackPolicy.fallbackKind(
                EnginePreference.AUTO, EngineKind.MEDIA3,
                "MEDIA3_ERROR_CODE_DECODER_INIT_FAILED", alreadyFellBack = false,
            ),
        )
    }

    @Test
    fun `decoding failure falls back to mpv in auto`() {
        assertEquals(
            EngineKind.MPV,
            EngineFallbackPolicy.fallbackKind(
                EnginePreference.AUTO, EngineKind.MEDIA3,
                "MEDIA3_ERROR_CODE_DECODING_FAILED", alreadyFellBack = false,
            ),
        )
    }

    @Test
    fun `hardware preference never falls back`() {
        assertNull(
            EngineFallbackPolicy.fallbackKind(
                EnginePreference.HARDWARE, EngineKind.MEDIA3,
                "MEDIA3_ERROR_CODE_DECODER_INIT_FAILED", alreadyFellBack = false,
            ),
        )
    }

    @Test
    fun `fallback fires at most once`() {
        assertNull(
            EngineFallbackPolicy.fallbackKind(
                EnginePreference.AUTO, EngineKind.MEDIA3,
                "MEDIA3_ERROR_CODE_DECODER_INIT_FAILED", alreadyFellBack = true,
            ),
        )
    }

    @Test
    fun `io and parser errors do not fall back`() {
        assertNull(
            EngineFallbackPolicy.fallbackKind(
                EnginePreference.AUTO, EngineKind.MEDIA3,
                "MEDIA3_ERROR_CODE_IO_FILE_NOT_FOUND", alreadyFellBack = false,
            ),
        )
        assertNull(
            EngineFallbackPolicy.fallbackKind(
                EnginePreference.AUTO, EngineKind.MEDIA3,
                "MEDIA3_ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED", alreadyFellBack = false,
            ),
        )
    }

    @Test
    fun `mpv failures never fall back`() {
        assertNull(
            EngineFallbackPolicy.fallbackKind(
                EnginePreference.AUTO, EngineKind.MPV,
                "MPV_LOAD_FAILED", alreadyFellBack = false,
            ),
        )
    }

    @Test
    fun `missing error code does not fall back`() {
        assertNull(
            EngineFallbackPolicy.fallbackKind(
                EnginePreference.AUTO, EngineKind.MEDIA3, null, alreadyFellBack = false,
            ),
        )
    }
}
