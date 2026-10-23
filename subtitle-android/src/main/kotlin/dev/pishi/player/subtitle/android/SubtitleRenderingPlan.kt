package dev.pishi.player.subtitle.android

/** Explicit rendering capabilities keep unsupported styling from silently pretending to work. */
data class SubtitleRenderingPlan(
    val rtlBidi: Boolean,
    val complexTextShaping: Boolean,
    val customFontSupported: Boolean,
    val perCueAlignmentSupported: Boolean,
    val lineSpacingSupported: Boolean,
    val outlineSupported: Boolean,
    val shadowSupported: Boolean,
)

/**
 * Capabilities of the app's Compose text overlay used for EXTERNAL subtitle files
 * (parsed by subtitle-core). The overlay draws one cue at a time with the platform
 * text stack, so glyph shaping and bidi come from Android itself; cue-level ASS
 * positioning/alignment and text outline are NOT reproduced by this renderer, and
 * the settings UI must not offer controls for them. Embedded tracks still render
 * through Media3's own subtitle view, not this plan.
 */
val ComposeOverlayRenderingPlan = SubtitleRenderingPlan(
    rtlBidi = true,
    complexTextShaping = true,
    customFontSupported = true,
    perCueAlignmentSupported = false,
    lineSpacingSupported = true,
    outlineSupported = false,
    shadowSupported = true,
)
