package dev.pishi.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

class SubtitleDecoderTest {
    @Test fun decodesUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "سلام".toByteArray(StandardCharsets.UTF_8)
        val decoded = SubtitleDecoder.decode(bytes)
        assertEquals("سلام", decoded.text)
        assertTrue(decoded.hadBom)
        assertEquals("UTF-8", decoded.charsetName)
    }

    @Test fun decodesUtf16LittleEndianBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "سلام".toByteArray(StandardCharsets.UTF_16LE)
        assertEquals("سلام", SubtitleDecoder.decode(bytes).text)
    }

    @Test fun supportsExplicitLegacyCharset() {
        val bytes = "سلام".toByteArray(Charset.forName("windows-1256"))
        val decoded = SubtitleDecoder.decode(bytes, "windows-1256")
        assertEquals("سلام", decoded.text)
        assertEquals("windows-1256", decoded.charsetName)
    }


    @Test fun usesPersianFirstLegacyFallbackWhenUtf8IsInvalid() {
        val bytes = "سلام".toByteArray(Charset.forName("windows-1256"))
        val decoded = SubtitleDecoder.decode(bytes)
        assertEquals("سلام", decoded.text)
        assertTrue(decoded.usedFallback)
        assertEquals("windows-1256", decoded.charsetName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedSubtitle() {
        SubtitleDecoder.decode(ByteArray(SubtitleDecoder.MAX_SUBTITLE_BYTES + 1))
    }
}
