package dev.pishi.player.storage

/** Test/demo implementation. Production Android persistence will use DataStore in a later step. */
class InMemoryBookmarkStore : PlaybackBookmarkStore {
    private val items = mutableMapOf<String, PlaybackBookmark>()
    override fun get(mediaKey: String) = items[mediaKey]
    override fun save(bookmark: PlaybackBookmark) { items[bookmark.mediaKey] = bookmark }
    override fun remove(mediaKey: String) { items.remove(mediaKey) }
}
