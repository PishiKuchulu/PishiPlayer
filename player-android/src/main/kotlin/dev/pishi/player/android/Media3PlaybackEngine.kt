package dev.pishi.player.android

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dev.pishi.player.api.*

/**
 * Media3 adapter backed by the platform decoder inventory. Codec claims must be
 * verified on actual devices; no software decoder beyond the platform's own
 * OMX/Codec2 software components is implied or bundled.
 *
 * The video surface is the only reason [nativePlayer] is exposed; every command
 * still flows through [PlaybackEngine].
 */
@UnstableApi
class Media3PlaybackEngine(context: Context) : PlaybackEngine {
    private val appContext = context.applicationContext
    private var listener: PlaybackEngine.Listener? = null
    private var lastSource: MediaSource? = null
    private var lastErrorCode: String? = null
    private var lastErrorMessage: String? = null
    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null

    /** Exposed for a platform-owned video surface; control remains through PlaybackEngine. */
    val nativePlayer: Player get() = player

    private val player = ExoPlayer.Builder(appContext)
        .setRenderersFactory(
            DefaultRenderersFactory(appContext)
                // If the preferred decoder fails to initialize (common on older TVs
                // with partial codec support), retry with another decoder instead of
                // failing the whole playback.
                .setEnableDecoderFallback(true)
        )
        .setMediaSourceFactory(DefaultMediaSourceFactory(appContext))
        .build()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = publishSnapshot()
            override fun onIsPlayingChanged(isPlaying: Boolean) = publishSnapshot()
            override fun onPlayerError(error: PlaybackException) {
                lastErrorCode = "MEDIA3_${error.errorCodeName}"
                lastErrorMessage = error.message ?: "Playback error"
                listener?.onError(lastErrorCode!!, lastErrorMessage!!, error)
                publishSnapshot()
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                listener?.onTracksChanged(readTracks())
                publishSnapshot()
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                videoDecoderName = decoderName
                publishSnapshot()
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                audioDecoderName = decoderName
                publishSnapshot()
            }
        })
    }

    override fun setListener(listener: PlaybackEngine.Listener?) { this.listener = listener; publishSnapshot() }

    override fun prepare(source: MediaSource, startPositionMs: Long) {
        lastSource = source
        lastErrorCode = null
        lastErrorMessage = null
        videoDecoderName = null
        audioDecoderName = null
        val builder = MediaItem.Builder().setUri(Uri.parse(source.uri.toString()))
        source.mimeType?.let(builder::setMimeType)
        source.title?.let { builder.setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(it).build()) }
        if (source.subtitleSources.isNotEmpty()) {
            val subtitleConfigurations = source.subtitleSources.map { subtitle ->
                val subtitleBuilder = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri.toString()))
                subtitle.format?.let { format ->
                    val mime = when (format) {
                        SubtitleFormat.SRT, SubtitleFormat.SUBRIP -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                        SubtitleFormat.WEBVTT -> androidx.media3.common.MimeTypes.TEXT_VTT
                        SubtitleFormat.TTML -> androidx.media3.common.MimeTypes.APPLICATION_TTML
                        SubtitleFormat.ASS, SubtitleFormat.SSA -> androidx.media3.common.MimeTypes.TEXT_SSA
                        SubtitleFormat.UNKNOWN -> null
                    }
                    mime?.let(subtitleBuilder::setMimeType)
                }
                subtitle.languageTag?.let(subtitleBuilder::setLanguage)
                subtitle.label?.let(subtitleBuilder::setLabel)
                subtitleBuilder.setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT).build()
            }
            builder.setSubtitleConfigurations(subtitleConfigurations)
        }
        // Per-source request headers are intentionally not silently applied to arbitrary hosts.
        player.setMediaItem(builder.build(), startPositionMs.coerceAtLeast(0L))
        player.prepare()
        publishSnapshot()
    }
    override fun play() { player.play() }
    override fun pause() { player.pause() }
    override fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceAtLeast(0L)) }
    override fun seekBy(deltaMs: Long) { seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L)) }
    override fun setPlaybackSpeed(speed: Float) { require(speed in 0.25f..4f); player.setPlaybackSpeed(speed) }
    override fun selectTrack(trackId: String) {
        if (trackId == TRACK_ID_SUBTITLES_OFF) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                .build()
            listener?.onTracksChanged(readTracks())
            return
        }
        // Track IDs are adapter-generated group:index tokens; selection API is kept deliberately explicit.
        val parts = trackId.split(":")
        require(parts.size == 3 && parts[0] == "group") { "Invalid Media3 track id" }
        val groupIndex = parts[1].toIntOrNull() ?: error("Invalid track group")
        val trackIndex = parts[2].toIntOrNull() ?: error("Invalid track index")
        val groups = player.currentTracks.groups
        require(groupIndex in groups.indices)
        val group = groups[groupIndex]
        require(trackIndex in 0 until group.length)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            // Selecting a track of a type also re-enables that renderer (e.g. text after "subtitles off").
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex)).build()
        listener?.onTracksChanged(readTracks())
    }
    override fun tracks(): List<MediaTrack> = readTracks()
    private fun readTracks(): List<MediaTrack> = buildList {
        player.currentTracks.groups.forEachIndexed { gi, group ->
            val type = when (group.type) {
                androidx.media3.common.C.TRACK_TYPE_AUDIO -> TrackType.AUDIO
                androidx.media3.common.C.TRACK_TYPE_TEXT -> TrackType.TEXT
                androidx.media3.common.C.TRACK_TYPE_VIDEO -> TrackType.VIDEO
                // Image, metadata and other group types are not selectable playback
                // tracks for this UI; do not mislabel them as video.
                else -> return@forEachIndexed
            }
            for (ti in 0 until group.length) {
                val f = group.getTrackFormat(ti)
                add(MediaTrack("group:$gi:$ti", type, f.label ?: f.language ?: "$type ${ti + 1}", f.language, group.isTrackSelected(ti)))
            }
        }
    }
    override fun snapshot(): PlaybackSnapshot = makeSnapshot()
    private fun makeSnapshot(): PlaybackSnapshot {
        val state = when {
            lastErrorCode != null -> PlaybackState.ERROR
            player.playbackState == Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            player.playbackState == Player.STATE_READY -> PlaybackState.READY
            player.playbackState == Player.STATE_ENDED -> PlaybackState.ENDED
            else -> if (lastSource == null) PlaybackState.IDLE else PlaybackState.PREPARING
        }
        return PlaybackSnapshot(
            state = state,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it >= 0 },
            isPlaying = player.isPlaying,
            speed = player.playbackParameters.speed,
            decodePath = decodePathFor(videoDecoderName),
            errorCode = lastErrorCode,
            errorMessage = lastErrorMessage,
            videoDecoderName = videoDecoderName,
            audioDecoderName = audioDecoderName,
        )
    }

    /**
     * Heuristic capability report: Android platform software decoders are named
     * `OMX.google.*` / `c2.android.*` / `OMX.*.google.*`. Anything else is treated as
     * a vendor/hardware decoder. This identifies the selected decoder family; it is
     * not a performance guarantee for a specific file.
     */
    private fun decodePathFor(decoderName: String?): DecodePath {
        if (decoderName == null) return DecodePath.UNKNOWN
        val n = decoderName.lowercase()
        return if (n.contains("google") || n.startsWith("c2.android.") || n.contains(".sw.") || n.endsWith(".software")) {
            DecodePath.SOFTWARE
        } else {
            DecodePath.HARDWARE
        }
    }

    private fun publishSnapshot() { listener?.onSnapshot(makeSnapshot()) }
    override fun release() { player.release(); listener = null; lastSource = null }
}
