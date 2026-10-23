package dev.pishi.player.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkTest {
 @Test fun storesAndRemovesBookmarks() { val s = InMemoryBookmarkStore(); val b = PlaybackBookmark("file:///movie.mkv", 12_000, 90_000, 1L); s.save(b); assertEquals(b, s.get(b.mediaKey)); s.remove(b.mediaKey); assertNull(s.get(b.mediaKey)) }
}
