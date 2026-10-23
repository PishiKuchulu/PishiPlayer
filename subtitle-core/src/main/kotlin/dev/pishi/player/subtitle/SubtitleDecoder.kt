package dev.pishi.player.subtitle

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Decodes subtitle bytes conservatively and reports which charset was actually used. */
data class DecodedSubtitle(val text: String, val charsetName: String, val hadBom: Boolean, val usedFallback: Boolean)

object SubtitleDecoder {
    private val utf8 = StandardCharsets.UTF_8

    /**
     * BOM wins, then an explicitly requested charset, then strict UTF-8. If bytes are not UTF-8,
     * use Windows-1256 as the Persian-first legacy fallback. Legacy encodings cannot be reliably
     * inferred from arbitrary bytes; callers should expose the charset override for ambiguous files.
     */
    fun decode(bytes: ByteArray, preferredCharset: String? = null): DecodedSubtitle {
        require(bytes.size <= MAX_SUBTITLE_BYTES) { "Subtitle exceeds the 16 MiB safety limit" }
        detectBom(bytes)?.let { bom ->
            val payload = bytes.copyOfRange(bom.offset, bytes.size)
            return DecodedSubtitle(decodeStrict(payload, bom.charset), bom.charset.name(), true, false)
        }
        if (!preferredCharset.isNullOrBlank()) {
            val charset = try { Charset.forName(preferredCharset) } catch (e: Exception) {
                throw IllegalArgumentException("Unsupported subtitle charset: $preferredCharset", e)
            }
            return DecodedSubtitle(decodeStrict(bytes, charset), charset.name(), false, false)
        }
        try {
            return DecodedSubtitle(decodeStrict(bytes, utf8), utf8.name(), false, false)
        } catch (_: CharacterCodingException) {
            val fallback = Charset.forName("windows-1256")
            return DecodedSubtitle(String(bytes, fallback), fallback.name(), false, true)
        }
    }

    private data class Bom(val offset: Int, val charset: Charset)
    private fun detectBom(bytes: ByteArray): Bom? = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Bom(3, utf8)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Bom(2, StandardCharsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Bom(2, StandardCharsets.UTF_16BE)
        else -> null
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    const val MAX_SUBTITLE_BYTES = 16 * 1024 * 1024
}
