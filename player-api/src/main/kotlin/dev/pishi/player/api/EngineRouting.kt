package dev.pishi.player.api

/** Playback engine implementations the app can route between. */
enum class EngineKind { MEDIA3, MPV }

/**
 * User-facing engine preference.
 *
 * - [AUTO]: start on the hardware path (Media3) and fall back to the software
 *   engine (mpv/FFmpeg) when Media3 reports a decoder-class failure.
 * - [HARDWARE]: Media3 only; never fall back (useful to prove a TV's hardware
 *   decoders really are the problem).
 * - [SOFTWARE]: always start on the software engine (mpv/FFmpeg).
 */
enum class EnginePreference { AUTO, HARDWARE, SOFTWARE }

/**
 * Pure routing policy for the two-engine setup. Kept free of Android and
 * engine types so the decision table is unit-testable on the JVM.
 *
 * The fallback decision consumes the failing engine's error *code string*.
 * Media3 adapter codes look like `MEDIA3_ERROR_CODE_DECODER_INIT_FAILED`;
 * only decoder-class failures qualify — I/O, network, and parser errors must
 * surface as errors instead of silently retrying on the other engine.
 */
object EngineFallbackPolicy {

    private val DECODER_ERROR_MARKERS = listOf("DECODER", "DECODING")

    /** Engine to start playback with for a fresh prepare under [preference]. */
    fun initialKind(preference: EnginePreference): EngineKind = when (preference) {
        EnginePreference.SOFTWARE -> EngineKind.MPV
        EnginePreference.AUTO, EnginePreference.HARDWARE -> EngineKind.MEDIA3
    }

    /**
     * Engine to retry with after [failing] reported [errorCode], or null to
     * surface the error. Fallback fires at most once per prepare
     * ([alreadyFellBack]) so two broken engines cannot ping-pong.
     */
    fun fallbackKind(
        preference: EnginePreference,
        failing: EngineKind,
        errorCode: String?,
        alreadyFellBack: Boolean,
    ): EngineKind? {
        if (preference == EnginePreference.HARDWARE) return null
        if (failing != EngineKind.MEDIA3 || alreadyFellBack) return null
        val code = errorCode ?: return null
        return if (DECODER_ERROR_MARKERS.any { code.contains(it) }) EngineKind.MPV else null
    }
}
