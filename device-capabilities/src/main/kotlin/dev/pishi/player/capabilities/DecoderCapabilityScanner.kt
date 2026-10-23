package dev.pishi.player.capabilities

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

data class DecoderCapability(val codecName: String, val mimeType: String, val hardwareAccelerated: Boolean?, val securePlayback: Boolean, val maxInstances: Int?)

/** Inventory only: advertised codec support does not guarantee real-time playback of a specific file. */
object DecoderCapabilityScanner {
    fun scan(): List<DecoderCapability> = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        .filterNot { it.isEncoder }
        .flatMap { info -> info.supportedTypes.map { mime ->
            // Feature and instance queries live on CodecCapabilities (per MIME type),
            // not on MediaCodecInfo itself. getCapabilitiesForType may throw for an
            // unsupported type on some devices; treat that as "no data", not a crash.
            val capabilities = runCatching { info.getCapabilitiesForType(mime) }.getOrNull()
            DecoderCapability(
                codecName = info.name,
                mimeType = mime,
                hardwareAccelerated = if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else null,
                securePlayback = capabilities?.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback) ?: false,
                maxInstances = if (Build.VERSION.SDK_INT >= 23) capabilities?.maxSupportedInstances else null,
            )
        } }.sortedWith(compareBy(DecoderCapability::mimeType, DecoderCapability::codecName))
}
