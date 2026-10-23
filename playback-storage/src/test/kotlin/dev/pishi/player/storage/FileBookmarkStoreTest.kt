package dev.pishi.player.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileBookmarkStoreTest {

    private fun tempStore(): Pair<FileBookmarkStore, File> {
        val dir = Files.createTempDirectory("bookmarks").toFile()
        val file = File(dir, "bookmarks.tsv")
        return FileBookmarkStore(file) to file
    }

    @Test fun persistsAcrossStoreInstances() {
        val (store, file) = tempStore()
        val bookmark = PlaybackBookmark("content://media/external/video/42", 65_000, 3_600_000, 123L)
        store.save(bookmark)
        val reloaded = FileBookmarkStore(file)
        assertEquals(bookmark, reloaded.get(bookmark.mediaKey))
    }

    @Test fun keysWithTabsAndUnicodeSurviveRoundTrip() {
        val (store, file) = tempStore()
        val key = "content://x/فیلم\tخاص.mkv"
        val bookmark = PlaybackBookmark(key, 1_000, null, 7L)
        store.save(bookmark)
        assertEquals(bookmark, FileBookmarkStore(file).get(key))
    }

    @Test fun removeDeletesAndMissingKeyReturnsNull() {
        val (store, _) = tempStore()
        assertNull(store.get("nope"))
        store.save(PlaybackBookmark("a", 10, 100, 1L))
        store.remove("a")
        assertNull(store.get("a"))
    }

    @Test fun malformedLinesAreSkippedNotFatal() {
        val (store, file) = tempStore()
        store.save(PlaybackBookmark("good", 500, 900, 3L))
        file.appendText("\ngarbage line without tabs")
        file.appendText("\nonly\ttwo")
        assertEquals(500L, store.get("good")?.positionMs)
    }
}
