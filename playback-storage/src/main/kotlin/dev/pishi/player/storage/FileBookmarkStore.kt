package dev.pishi.player.storage

import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * File-backed [PlaybackBookmarkStore] for production use. One bookmark per line:
 * `url-encoded-key \t positionMs \t durationMs(-1 for null) \t updatedAtEpochMs`.
 *
 * Writes rewrite the whole file (bookmark counts are small) under a lock, via a
 * temp file + rename so a crash mid-write cannot corrupt the previous state.
 * Malformed lines are skipped on read instead of failing the whole store.
 * Pure JVM (java.io only) so the format is unit-testable without Android.
 */
class FileBookmarkStore(private val file: File) : PlaybackBookmarkStore {
    private val lock = Any()

    override fun get(mediaKey: String): PlaybackBookmark? = synchronized(lock) { load()[mediaKey] }

    override fun save(bookmark: PlaybackBookmark) {
        synchronized(lock) {
            val items = load()
            items[bookmark.mediaKey] = bookmark
            persist(items)
        }
    }

    override fun remove(mediaKey: String) {
        synchronized(lock) {
            val items = load()
            if (items.remove(mediaKey) != null) persist(items)
        }
    }

    private fun load(): MutableMap<String, PlaybackBookmark> {
        if (!file.isFile) return mutableMapOf()
        val result = mutableMapOf<String, PlaybackBookmark>()
        file.readLines(Charsets.UTF_8).forEach { line ->
            val parts = line.split('\t')
            if (parts.size != 4) return@forEach
            val position = parts[1].toLongOrNull() ?: return@forEach
            val durationRaw = parts[2].toLongOrNull() ?: return@forEach
            val updated = parts[3].toLongOrNull() ?: return@forEach
            val key = runCatching { URLDecoder.decode(parts[0], "UTF-8") }.getOrNull() ?: return@forEach
            if (key.isBlank() || position < 0) return@forEach
            result[key] = PlaybackBookmark(key, position, if (durationRaw < 0) null else durationRaw, updated)
        }
        return result
    }

    private fun persist(items: Map<String, PlaybackBookmark>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(
            items.values.joinToString("\n") { b ->
                listOf(
                    URLEncoder.encode(b.mediaKey, "UTF-8"),
                    b.positionMs.toString(),
                    (b.durationMs ?: -1L).toString(),
                    b.updatedAtEpochMs.toString(),
                ).joinToString("\t")
            },
            Charsets.UTF_8,
        )
        if (!tmp.renameTo(file)) {
            // Rename can fail across filesystems or when the target is locked; fall back to copy.
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }
}
