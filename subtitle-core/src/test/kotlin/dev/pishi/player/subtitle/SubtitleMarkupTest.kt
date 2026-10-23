package dev.pishi.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Inline-markup polish: SRT cues in the wild carry HTML-style formatting tags,
 * and WebVTT cues carry HTML entities and timestamp tags. The parser's text
 * output is what the overlay renders, so markup must be resolved to plain text
 * here. ASS/SSA is deliberately NOT processed by this cleanup (its override
 * blocks are handled by the ASS path; ASS does not use HTML entities).
 */
class SubtitleMarkupTest {

    @Test fun srtStripsInlineFormattingTags() {
        val cues = SubtitleParser.parse(
            "1\n00:00:01,000 --> 00:00:03,000\n<i>Hello</i> <b>bold</b> <font color=\"#FF0000\">red</font> <u>under</u>\n",
            SubtitleParser.Format.SRT,
        )
        assertEquals("Hello bold red under", cues[0].text)
    }

    @Test fun srtStripsTagsAcrossMultilineCues() {
        val cues = SubtitleParser.parse(
            "1\n00:00:01,000 --> 00:00:03,000\n<i>line one\nline two</i>\n",
            SubtitleParser.Format.SRT,
        )
        assertEquals("line one\nline two", cues[0].text)
    }

    @Test fun srtPreservesAngleBracketsThatAreNotTags() {
        val cues = SubtitleParser.parse(
            "1\n00:00:01,000 --> 00:00:03,000\n5 < 6, I <3 you, a < b > c\n",
            SubtitleParser.Format.SRT,
        )
        assertEquals("5 < 6, I <3 you, a < b > c", cues[0].text)
    }

    @Test fun srtDecodesHtmlEntities() {
        val cues = SubtitleParser.parse(
            "1\n00:00:01,000 --> 00:00:03,000\nFish &amp; Chips &quot;best&quot; &lt;ever&gt;\n",
            SubtitleParser.Format.SRT,
        )
        assertEquals("Fish & Chips \"best\" <ever>", cues[0].text)
    }

    @Test fun webVttStripsTimestampTagsAndDecodesEntities() {
        val cues = SubtitleParser.parse(
            "WEBVTT\n\n00:00:01.000 --> 00:00:04.000\n<00:00:01.500>Tom &amp; Jerry <00:00:02.500>speak\n",
            SubtitleParser.Format.WEBVTT,
        )
        assertEquals(1, cues.size)
        assertEquals("Tom & Jerry speak", cues[0].text)
    }

    @Test fun webVttDecodesNumericAndHexEntities() {
        val cues = SubtitleParser.parse(
            "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n&#72;&#105; &#x645;&#x631;&#x62D;&#x628;&#x627;\n",
            SubtitleParser.Format.WEBVTT,
        )
        // Decimal 72/105 = "Hi"; hex 645/631/62D/628/627 spell the Arabic word for "welcome".
        assertEquals("Hi مرحبا", cues[0].text)
    }

    @Test fun webVttDecodesDirectionalMarksUsedByRealWorldFiles() {
        val cues = SubtitleParser.parse(
            "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n&lrm;سلام&rlm;\n",
            SubtitleParser.Format.WEBVTT,
        )
        // &lrm; is U+200E (LEFT-TO-RIGHT MARK), &rlm; is U+200F (RIGHT-TO-LEFT MARK).
        assertEquals("‎سلام‏", cues[0].text)
    }

    @Test fun unknownEntitiesAreLeftAsIs() {
        val cues = SubtitleParser.parse(
            "1\n00:00:01,000 --> 00:00:02,000\nA &unknown; B &notanentity C\n",
            SubtitleParser.Format.SRT,
        )
        assertEquals("A &unknown; B &notanentity C", cues[0].text)
    }

    @Test fun escapedTagsDecodeToLiteralTextNotMarkup() {
        // &lt;i&gt; is the author escaping a tag: it must become literal "<i>" text,
        // which is why tag stripping runs BEFORE entity decoding.
        val cues = SubtitleParser.parse(
            "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nUse &lt;i&gt; for italic\n",
            SubtitleParser.Format.WEBVTT,
        )
        assertEquals("Use <i> for italic", cues[0].text)
    }

    @Test fun assTextIsNotEntityDecoded() {
        val cues = SubtitleParser.parse(
            """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Tom &amp; Jerry
            """.trimIndent(),
            SubtitleParser.Format.ASS,
        )
        assertEquals("Tom &amp; Jerry", cues[0].text)
    }
}
