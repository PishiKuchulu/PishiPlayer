package dev.pishi.player.storage

data class PlaybackBookmark(val mediaKey: String, val positionMs: Long, val durationMs: Long? = null, val updatedAtEpochMs: Long) {
    init { require(mediaKey.isNotBlank()); require(positionMs >= 0); require(durationMs == null || durationMs >= 0) }
}

interface PlaybackBookmarkStore {
    fun get(mediaKey: String): PlaybackBookmark?
    fun save(bookmark: PlaybackBookmark)
    fun remove(mediaKey: String)
}
