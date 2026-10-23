package dev.pishi.player.api

import java.net.URI

enum class PlaybackState { IDLE, PREPARING, READY, BUFFERING, ENDED, ERROR }
enum class DecodePath { UNKNOWN, HARDWARE, SOFTWARE, TRANSCODED }
enum class TrackType { AUDIO, TEXT, VIDEO }
enum class SubtitleFormat { SRT, ASS, SSA, WEBVTT, TTML, SUBRIP, UNKNOWN }

data class MediaSource(
    val uri: URI,
    val mimeType: String? = null,
    val title: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val subtitleSources: List<SubtitleSource> = emptyList(),
) {
    init { require(uri.scheme in setOf("file", "content", "http", "https")) { "Unsupported URI scheme: ${uri.scheme}" } }
}

data class SubtitleSource(val uri: URI, val format: SubtitleFormat? = null, val languageTag: String? = null, val label: String? = null)
data class MediaTrack(val id: String, val type: TrackType, val label: String, val languageTag: String? = null, val selected: Boolean = false)
data class PlaybackSnapshot(val state: PlaybackState, val positionMs: Long, val durationMs: Long?, val isPlaying: Boolean, val speed: Float = 1f, val decodePath: DecodePath = DecodePath.UNKNOWN, val errorCode: String? = null, val errorMessage: String? = null, val videoDecoderName: String? = null, val audioDecoderName: String? = null)

/** Well-known pseudo track id understood by PlaybackEngine.selectTrack: disables the text renderer (subtitles off). */
const val TRACK_ID_SUBTITLES_OFF = "off:text"

data class SeekPolicy(val shortPressMs: Long = 10_000, val longPressIntervalMs: Long = 5_000, val longPressInitialDelayMs: Long = 450) {
    init { require(shortPressMs > 0); require(longPressIntervalMs > 0); require(longPressInitialDelayMs >= 0) }
}

interface PlaybackEngine {
    fun setListener(listener: Listener?)
    fun prepare(source: MediaSource, startPositionMs: Long = 0L)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun selectTrack(trackId: String)
    fun tracks(): List<MediaTrack>
    fun snapshot(): PlaybackSnapshot
    fun release()

    interface Listener {
        fun onSnapshot(snapshot: PlaybackSnapshot)
        fun onTracksChanged(tracks: List<MediaTrack>)
        fun onError(code: String, message: String, cause: Throwable? = null)
    }
}
