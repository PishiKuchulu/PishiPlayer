package dev.pishi.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {
    @Test fun parsesSrtPersianAndMultilineCues() {
        val cues = SubtitleParser.parse("""1
00:00:01,250 --> 00:00:03,500
سلام دنیا
خط دوم

2
00:00:04,000 --> 00:00:05,000
تمام
""".trimIndent(), SubtitleParser.Format.SRT)
        assertEquals(2, cues.size)
        assertEquals(1250L, cues[0].startMs)
        assertEquals(3500L, cues[0].endMs)
        assertEquals("سلام دنیا\nخط دوم", cues[0].text)
    }

    @Test fun parsesWebVttWithCueIdentifiersAndTags() {
        val cues = SubtitleParser.parse("""WEBVTT

intro
00:01.000 --> 00:02.250 align:start
<b>Hello</b> <i>world</i>

""".trimIndent(), SubtitleParser.Format.WEBVTT)
        assertEquals(1, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(2250L, cues[0].endMs)
        assertEquals("Hello world", cues[0].text)
    }

    @Test fun parsesAssTextAndRetainsStyleAndAlignmentMetadata() {
        val cues = SubtitleParser.parse("""[Script Info]
Title: sample
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,{\an8\i1}سلام\Nworld
""".trimIndent(), SubtitleParser.Format.ASS)
        assertEquals(1, cues.size)
        assertEquals("سلام\nworld", cues[0].text)
        assertEquals("Default", cues[0].styleId)
        assertEquals(8, cues[0].alignment)
    }

    @Test fun sortsOverlappingOutOfOrderCuesByStartTime() {
        val cues = SubtitleParser.parse("""00:00:04,000 --> 00:00:05,000
B

00:00:01,000 --> 00:00:02,000
A""", SubtitleParser.Format.SRT)
        assertEquals(listOf("A", "B"), cues.map { it.text })
    }

    @Test fun unknownInputDoesNotInventCues() {
        assertTrue(SubtitleParser.parse("not a subtitle", SubtitleParser.Format.UNKNOWN).isEmpty())
    }
}
