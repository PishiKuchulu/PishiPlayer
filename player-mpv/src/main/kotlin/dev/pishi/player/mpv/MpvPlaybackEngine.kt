package dev.pishi.player.mpv

import android.content.Context
import android.net.Uri
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import dev.pishi.player.api.DecodePath
import dev.pishi.player.api.MediaSource
import dev.pishi.player.api.MediaTrack
import dev.pishi.player.api.PlaybackEngine
import dev.pishi.player.api.PlaybackSnapshot
import dev.pishi.player.api.PlaybackState
import dev.pishi.player.api.TRACK_ID_SUBTITLES_OFF
import dev.pishi.player.api.TrackType

/**
 * Software-decoding engine backed by libmpv (mpv + FFmpeg, via
 * `dev.jdtech.mpv:libmpv`). This is the engine for TVs whose hardware lacks a
 * decoder for a file's codec: FFmpeg decodes in software (`hwdec=no` is set
 * deliberately — if MediaCodec failed inside Media3, retrying the same
 * MediaCodec path here would fail the same way).
 *
 * MPVLib is a process-global static API, so the native core is reference
 * counted in the companion: engine instances may briefly overlap during a
 * Compose-driven engine switch, and the core must only be created/destroyed
 * on the 0↔1 transitions.
 *
 * The app supplies the video [Surface] (from its own SurfaceView) through
 * [attachSurface]/[detachSurface]; every playback command still flows through
 * [PlaybackEngine].
 *
 * Not yet device-verified (see BUILD_STATUS.md): surface reconfiguration
 * timing, `fdclose://` handling of `content://` sources, and destroy→create
 * cycles across engine switches all need a real TV run.
 */
class MpvPlaybackEngine(context: Context) : PlaybackEngine, MPVLib.EventObserver {

    private val appContext = context.applicationContext
    private var listener: PlaybackEngine.Listener? = null

    @Volatile private var lastSource: MediaSource? = null
    @Volatile private var lastErrorCode: String? = null
    @Volatile private var lastErrorMessage: String? = null
    @Volatile private var sawFileLoaded = false
    @Volatile private var pendingStartMs = 0L

    // Cached property values, written from mpv's event thread.
    @Volatile private var positionSec = 0.0
    @Volatile private var durationSec: Double? = null
    @Volatile private var paused = true
    @Volatile private var pausedForCache = false
    @Volatile private var eofReached = false
    @Volatile private var speedValue = 1.0

    init {
        acquireCore(appContext)
        MPVLib.addObserver(this)
        MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("speed", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("paused-for-cache", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("track-list/count", MPVLib.MPV_FORMAT_INT64)
    }

    /** Attach the video output surface. Call from SurfaceHolder.Callback.surfaceCreated. */
    fun attachSurface(surface: Surface) {
        runCatching { MPVLib.attachSurface(surface) }
    }

    /** Detach the video output surface. Call from SurfaceHolder.Callback.surfaceDestroyed. */
    fun detachSurface() {
        runCatching { MPVLib.detachSurface() }
    }

    override fun setListener(listener: PlaybackEngine.Listener?) {
        this.listener = listener
        publishSnapshot()
    }

    override fun prepare(source: MediaSource, startPositionMs: Long) {
        lastSource = source
        lastErrorCode = null
        lastErrorMessage = null
        sawFileLoaded = false
        eofReached = false
        positionSec = 0.0
        durationSec = null
        pendingStartMs = startPositionMs.coerceAtLeast(0L)
        val url = resolvePlayableUrl(source) ?: run {
            publishSnapshot()
            return
        }
        MPVLib.command(arrayOf("loadfile", url))
        publishSnapshot()
    }

    /**
     * file/http(s) URIs play directly. A `content://` URI cannot be opened by
     * FFmpeg, so it is handed over as an OS file descriptor through mpv's
     * `fdclose://` stream (mpv takes ownership and closes it). If the provider
     * refuses a descriptor, the error is surfaced, never hidden.
     */
    private fun resolvePlayableUrl(source: MediaSource): String? {
        return when (source.uri.scheme) {
            "file", "http", "https" -> source.uri.toString()
            "content" -> {
                val pfd = runCatching {
                    appContext.contentResolver.openFileDescriptor(Uri.parse(source.uri.toString()), "r")
                }.getOrNull()
                if (pfd == null) {
                    lastErrorCode = "MPV_CONTENT_OPEN_FAILED"
                    lastErrorMessage = "Could not open a file descriptor for the content URI"
                    listener?.onError(lastErrorCode!!, lastErrorMessage!!)
                    null
                } else {
                    // Ownership of the fd moves to mpv (fdclose:// closes it);
                    // the ParcelFileDescriptor wrapper is intentionally not closed.
                    "fdclose://${pfd.detachFd()}"
                }
            }
            else -> {
                lastErrorCode = "MPV_UNSUPPORTED_SCHEME"
                lastErrorMessage = "Unsupported URI scheme: ${source.uri.scheme}"
                listener?.onError(lastErrorCode!!, lastErrorMessage!!)
                null
            }
        }
    }

    override fun play() { runCatching { MPVLib.setPropertyBoolean("pause", false) } }
    override fun pause() { runCatching { MPVLib.setPropertyBoolean("pause", true) } }

    override fun seekTo(positionMs: Long) {
        runCatching { MPVLib.command(arrayOf("seek", (positionMs.coerceAtLeast(0L) / 1000.0).toString(), "absolute")) }
    }

    override fun seekBy(deltaMs: Long) {
        runCatching { MPVLib.command(arrayOf("seek", (deltaMs / 1000.0).toString(), "relative")) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        require(speed in 0.25f..4f)
        runCatching { MPVLib.setPropertyDouble("speed", speed.toDouble()) }
    }

    override fun selectTrack(trackId: String) {
        if (trackId == TRACK_ID_SUBTITLES_OFF) {
            runCatching { MPVLib.setPropertyString("sid", "no") }
            listener?.onTracksChanged(readTracks())
            return
        }
        // Track IDs are adapter-generated "mpv:<type>:<mpvTrackId>" tokens.
        val parts = trackId.split(":")
        require(parts.size == 3 && parts[0] == "mpv") { "Invalid mpv track id" }
        val mpvTrackId = parts[2].toIntOrNull() ?: error("Invalid mpv track index")
        val property = when (parts[1]) {
            "video" -> "vid"
            "audio" -> "aid"
            "sub" -> "sid"
            else -> error("Invalid mpv track type")
        }
        runCatching { MPVLib.setPropertyInt(property, mpvTrackId) }
        listener?.onTracksChanged(readTracks())
    }

    override fun tracks(): List<MediaTrack> = readTracks()

    private fun readTracks(): List<MediaTrack> {
        val count = runCatching { MPVLib.getPropertyInt("track-list/count") }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until count) {
                val mpvType = runCatching { MPVLib.getPropertyString("track-list/$i/type") }.getOrNull() ?: continue
                val type = when (mpvType) {
                    "video" -> TrackType.VIDEO
                    "audio" -> TrackType.AUDIO
                    "sub" -> TrackType.TEXT
                    else -> continue
                }
                val mpvId = runCatching { MPVLib.getPropertyInt("track-list/$i/id") }.getOrNull() ?: continue
                val title = runCatching { MPVLib.getPropertyString("track-list/$i/title") }.getOrNull()
                val lang = runCatching { MPVLib.getPropertyString("track-list/$i/lang") }.getOrNull()
                val selected = runCatching { MPVLib.getPropertyBoolean("track-list/$i/selected") }.getOrNull() ?: false
                add(MediaTrack("mpv:$mpvType:$mpvId", type, title ?: lang ?: "$mpvType ${i + 1}", lang, selected))
            }
        }
    }

    override fun snapshot(): PlaybackSnapshot = makeSnapshot()

    private fun makeSnapshot(): PlaybackSnapshot {
        val state = when {
            lastErrorCode != null -> PlaybackState.ERROR
            eofReached -> PlaybackState.ENDED
            pausedForCache -> PlaybackState.BUFFERING
            lastSource == null -> PlaybackState.IDLE
            !sawFileLoaded -> PlaybackState.PREPARING
            else -> PlaybackState.READY
        }
        val videoCodec = runCatching { MPVLib.getPropertyString("video-codec") }.getOrNull()
        val audioCodec = runCatching { MPVLib.getPropertyString("audio-codec") }.getOrNull()
        return PlaybackSnapshot(
            state = state,
            positionMs = (positionSec * 1000).toLong().coerceAtLeast(0L),
            durationMs = durationSec?.let { (it * 1000).toLong() },
            isPlaying = lastSource != null && !paused && !eofReached,
            speed = speedValue.toFloat(),
            decodePath = DecodePath.SOFTWARE,
            errorCode = lastErrorCode,
            errorMessage = lastErrorMessage,
            videoDecoderName = videoCodec?.let { "FFmpeg software ($it)" },
            audioDecoderName = audioCodec?.let { "FFmpeg software ($it)" },
        )
    }

    private fun publishSnapshot() { listener?.onSnapshot(makeSnapshot()) }

    override fun release() {
        runCatching { MPVLib.removeObserver(this) }
        listener = null
        lastSource = null
        releaseCore()
    }

    // --- MPVLib.EventObserver (callbacks arrive on mpv's event thread) ---

    override fun eventProperty(property: String) {
        if (property == "track-list/count") listener?.onTracksChanged(readTracks())
        publishSnapshot()
    }

    override fun eventProperty(property: String, value: Long) {
        if (property == "track-list/count") listener?.onTracksChanged(readTracks())
        publishSnapshot()
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> positionSec = value
            "duration" -> durationSec = value
            "speed" -> speedValue = value
        }
        publishSnapshot()
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> paused = value
            "paused-for-cache" -> pausedForCache = value
            "eof-reached" -> eofReached = value
        }
        publishSnapshot()
    }

    override fun eventProperty(property: String, value: String) {
        publishSnapshot()
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MPV_EVENT_FILE_LOADED -> {
                sawFileLoaded = true
                val start = pendingStartMs
                pendingStartMs = 0L
                if (start > 0) seekTo(start)
                listener?.onTracksChanged(readTracks())
                publishSnapshot()
            }
            MPVLib.MPV_EVENT_END_FILE -> {
                if (!sawFileLoaded && lastSource != null && lastErrorCode == null) {
                    // END_FILE without a successful load = the file could not be
                    // opened/decoded. The simple event API carries no reason
                    // string, so the code stays generic but honest.
                    lastErrorCode = "MPV_LOAD_FAILED"
                    lastErrorMessage = "mpv could not load or decode this file"
                    listener?.onError(lastErrorCode!!, lastErrorMessage!!)
                } else if (sawFileLoaded) {
                    eofReached = true
                }
                publishSnapshot()
            }
        }
    }

    companion object {
        private var coreUsers = 0

        @Synchronized
        private fun acquireCore(context: Context) {
            if (coreUsers == 0) {
                MPVLib.create(context)
                // Software-only by design (see class doc). Options must be set
                // between create() and init().
                MPVLib.setOptionString("vo", "gpu")
                MPVLib.setOptionString("gpu-context", "android")
                MPVLib.setOptionString("ao", "audiotrack")
                MPVLib.setOptionString("hwdec", "no")
                MPVLib.setOptionString("sid", "no")
                MPVLib.init()
            }
            coreUsers++
        }

        @Synchronized
        private fun releaseCore() {
            coreUsers--
            if (coreUsers == 0) {
                runCatching { MPVLib.destroy() }
            }
        }
    }
}
