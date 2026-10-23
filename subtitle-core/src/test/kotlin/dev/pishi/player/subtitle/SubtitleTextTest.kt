package dev.pishi.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTextTest {
    @Test fun stripsOnlyLeadingBom() = assertEquals("سلام", SubtitleText.stripLeadingBom("\uFEFFسلام"))
    @Test fun normalizesPersianYehAndKaf() = assertEquals("علی کتاب", SubtitleText.normalizePersianLetters("علي كتاب"))
    @Test fun preservesMixedDirectionContent() = assertEquals("سلام (Movie 2024)!", SubtitleText.normalizePersianLetters("سلام (Movie 2024)!"))
}
