package dev.pishi.player.subtitle

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String, val styleId: String? = null, val alignment: Int? = null) {
    init { require(startMs >= 0); require(endMs >= startMs) }
}
