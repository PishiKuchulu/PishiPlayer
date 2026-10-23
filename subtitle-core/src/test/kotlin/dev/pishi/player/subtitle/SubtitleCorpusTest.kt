package dev.pishi.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Golden-corpus tests for the cases the product actually promises: Persian and
 * Arabic text, mixed-direction lines, legacy encodings, malformed input, and
 * format edge cases. All fixtures are inline so the corpus travels with the code.
 */
class SubtitleCorpusTest {

    @Test fun parsesArabicSrt() {
        val cues = SubtitleParser.parse(
            """
            1
            00:00:01,000 --> 00:00:03,000
            مرحبا بالعالم

            2
            00:00:04,000 --> 00:00:06,500
            كيف حالك اليوم؟
            """.trimIndent(),
            SubtitleParser.Format.SRT,
        )
        assertEquals(2, cues.size)
        assertEquals("مرحبا بالعالم", cues[0].text)
        assertEquals(6500L, cues[1].endMs)
    }

    @Test fun keepsMixedDirectionPersianWithLatinAndDigitsIntact() {
        val line = "قسمت 12 از Game of Thrones رو دیدی؟"
        val cues = SubtitleParser.parse(
            "1\n00:00:10,000 --> 00:00:12,000\n$line\n",
            SubtitleParser.Format.SRT,
        )
        assertEquals(1, cues.size)
        assertEquals(line, cues[0].text)
        // Normalization must not rewrite Latin words, digits, or punctuation.
        assertEquals(line, SubtitleText.normalizePersianLetters(line))
    }

    @Test fun skipsMalformedSrtBlocksWithoutInventingCues() {
        val cues = SubtitleParser.parse(
            """
            1
            not a timestamp line
            orphan text

            2
            00:00:01,000 --> 00:00:02,000
            Valid cue
            """.trimIndent(),
            SubtitleParser.Format.SRT,
        )
        assertEquals(1, cues.size)
        assertEquals("Valid cue", cues[0].text)
    }

    @Test fun emptyAndWhitespaceFilesYieldNoCues() {
        assertTrue(SubtitleParser.parse("", SubtitleParser.Format.SRT).isEmpty())
        assertTrue(SubtitleParser.parse("   \n\n  ", SubtitleParser.Format.WEBVTT).isEmpty())
    }

    @Test fun skipsWebVttNoteBlocks() {
        val cues = SubtitleParser.parse(
            """
            WEBVTT

            NOTE
            This is a comment block
            spanning two lines

            00:00:01.000 --> 00:00:02.000
            Real cue
            """.trimIndent(),
            SubtitleParser.Format.WEBVTT,
        )
        assertEquals(1, cues.size)
        assertEquals("Real cue", cues[0].text)
    }

    @Test fun assDialogueTextMayContainCommas() {
        val cues = SubtitleParser.parse(
            """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello, world, again
            """.trimIndent(),
            SubtitleParser.Format.ASS,
        )
        assertEquals(1, cues.size)
        assertEquals("Hello, world, again", cues[0].text)
    }

    @Test fun decodesUtf16BigEndianBom() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "سلام".toByteArray(StandardCharsets.UTF_16BE)
        val decoded = SubtitleDecoder.decode(bytes)
        assertEquals("سلام", decoded.text)
        assertEquals("UTF-16BE", decoded.charsetName)
        assertTrue(decoded.hadBom)
    }

    @Test fun explicitWindows1252OverrideDecodesWesternText() {
        val text = "Bonjour café élève"
        val bytes = text.toByteArray(Charset.forName("windows-1252"))
        val decoded = SubtitleDecoder.decode(bytes, "windows-1252")
        assertEquals(text, decoded.text)
        assertEquals("windows-1252", decoded.charsetName)
    }

    @Test fun detectFormatUsesContentWhenExtensionIsMissing() {
        assertEquals(
            SubtitleParser.Format.WEBVTT,
            SubtitleParser.detectFormat("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi"),
        )
        assertEquals(
            SubtitleParser.Format.SRT,
            SubtitleParser.detectFormat("1\n00:00:01,000 --> 00:00:02,000\nHi"),
        )
    }
}
