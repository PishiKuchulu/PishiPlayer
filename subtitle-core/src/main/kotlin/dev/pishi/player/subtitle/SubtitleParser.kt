package dev.pishi.player.subtitle

/** Format-aware parser for common text subtitle formats. Times are milliseconds. */
object SubtitleParser {
    enum class Format { SRT, WEBVTT, ASS, SSA, UNKNOWN }

    fun detectFormat(text: String, fileName: String? = null): Format {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase()
        when (ext) {
            "srt" -> return Format.SRT
            "vtt" -> return Format.WEBVTT
            "ass" -> return Format.ASS
            "ssa" -> return Format.SSA
        }
        val trimmed = SubtitleText.stripLeadingBom(text).trimStart()
        return when {
            trimmed.startsWith("WEBVTT", ignoreCase = true) -> Format.WEBVTT
            trimmed.contains("[Events]", ignoreCase = true) && trimmed.contains("Dialogue:", ignoreCase = true) -> Format.ASS
            TIMESTAMP_REGEX.containsMatchIn(trimmed) -> Format.SRT
            else -> Format.UNKNOWN
        }
    }

    fun parse(text: String, format: Format = detectFormat(text)): List<SubtitleCue> {
        val clean = SubtitleText.stripLeadingBom(text).replace("\r\n", "\n").replace('\r', '\n')
        val cues = when (format) {
            Format.SRT -> parseSrt(clean)
            Format.WEBVTT -> parseWebVtt(clean)
            Format.ASS, Format.SSA -> parseAss(clean)
            Format.UNKNOWN -> emptyList()
        }
        return cues.sortedWith(compareBy<SubtitleCue> { it.startMs }.thenBy { it.endMs })
    }

    private fun parseSrt(text: String): List<SubtitleCue> {
        val lines = text.split('\n')
        val result = mutableListOf<SubtitleCue>()
        var i = 0
        while (i < lines.size) {
            if (!TIMESTAMP_REGEX.containsMatchIn(lines[i])) { i++; continue }
            val match = TIMESTAMP_REGEX.find(lines[i])!!
            val start = parseTimestamp(match.groupValues[1])
            val end = parseTimestamp(match.groupValues[2])
            i++
            val body = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank()) body += lines[i++]
            if (end >= start && body.isNotEmpty()) {
                result += SubtitleCue(start, end, body.joinToString("\n") { cleanInlineMarkup(it) })
            }
        }
        return result
    }

    private fun parseWebVtt(text: String): List<SubtitleCue> {
        val result = mutableListOf<SubtitleCue>()
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("NOTE") || line == "STYLE" || line == "REGION") {
                i++
                while (i < lines.size && lines[i].isNotBlank()) i++
                continue
            }
            val timestampLine = if (line.contains("-->") ) line else if (i + 1 < lines.size && lines[i + 1].contains("-->")) lines[++i].trim() else ""
            val match = WEBVTT_TIME_REGEX.find(timestampLine)
            if (match == null) { i++; continue }
            val start = parseTimestamp(match.groupValues[1])
            val end = parseTimestamp(match.groupValues[2])
            i++
            val body = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank()) body += lines[i++]
            if (end >= start && body.isNotEmpty()) {
                val cleaned = body.map { cleanInlineMarkup(it) }
                result += SubtitleCue(start, end, cleaned.joinToString("\n"))
            }
        }
        return result
    }

    private fun parseAss(text: String): List<SubtitleCue> {
        val result = mutableListOf<SubtitleCue>()
        var inEvents = false
        var formatFields = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("[")) { inEvents = line.equals("[Events]", true); continue }
            if (!inEvents) continue
            if (line.startsWith("Format:", true)) {
                formatFields = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                continue
            }
            if (!line.startsWith("Dialogue:", true)) continue
            val values = splitAssDialogue(line.substringAfter(':'), formatFields.size)
            if (values.size < formatFields.size) continue
            fun field(name: String): String? = formatFields.indexOf(name).takeIf { it >= 0 }?.let(values::get)
            val start = field("start")?.let(::parseAssTimestamp) ?: continue
            val end = field("end")?.let(::parseAssTimestamp) ?: continue
            if (end < start) continue
            val rawText = field("text") ?: continue
            val alignment = ASS_ALIGNMENT_REGEX.find(rawText)?.groupValues?.get(1)?.toIntOrNull()
            val style = field("style")?.takeIf { it.isNotBlank() }
            // Keep a conservative text representation: preserve line breaks, strip override blocks.
            // Full ASS typography (karaoke, vector drawings, per-word animation) needs a native renderer.
            val rendered = rawText.replace(Regex("\\{[^}]*}"), "")
                .replace("\\N", "\n", true).replace("\\n", "\n", true).replace("\\h", " ", true)
                .replace(Regex("<[^>]+>"), "")
            if (rendered.isNotBlank()) result += SubtitleCue(start, end, rendered, style, alignment)
        }
        return result
    }

    private fun splitAssDialogue(raw: String, expectedFields: Int): List<String> {
        if (expectedFields <= 1) return listOf(raw.trim())
        val parts = raw.split(',', limit = expectedFields)
        return parts.map { it.trim() }
    }

    private fun parseTimestamp(value: String): Long {
        val normalized = value.trim().replace(',', '.')
        val parts = normalized.split(':')
        if (parts.size !in 2..3) return 0L
        val secondsPart = parts.last().toDoubleOrNull() ?: return 0L
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return 0L
        val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return 0L else 0L
        return ((hours * 3600 + minutes * 60) * 1000 + (secondsPart * 1000).toLong()).coerceAtLeast(0)
    }

    private fun parseAssTimestamp(value: String): Long? {
        val parts = value.trim().split(':')
        if (parts.size != 3) return null
        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val seconds = parts[2].toDoubleOrNull() ?: return null
        if (hours < 0 || minutes !in 0..59 || seconds < 0 || seconds >= 60) return null
        return ((hours * 3600 + minutes * 60) * 1000 + (seconds * 1000).toLong())
    }

    /**
     * Resolves inline markup in SRT/WebVTT cue text to the plain text the renderer
     * should show. Order matters: real tags are stripped FIRST, then HTML entities
     * are decoded — so an escaped tag (`&lt;i&gt;`) survives as literal "<i>" text
     * instead of being eaten as markup. Not applied to ASS/SSA: ASS has its own
     * override-block handling and does not use HTML entities.
     *
     * A "tag" must start with a letter right after `<` or `</`, so plain text such
     * as `5 < 6` or `I <3 you` is preserved. `<br>` becomes a space.
     */
    private fun cleanInlineMarkup(text: String): String {
        var result = BR_TAG_REGEX.replace(text, " ")
        result = VTT_TIMESTAMP_TAG_REGEX.replace(result, "")
        result = INLINE_TAG_REGEX.replace(result, "")
        return decodeHtmlEntities(result)
    }

    private fun decodeHtmlEntities(text: String): String = ENTITY_REGEX.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) -> codePointString(body.drop(2), 16) ?: match.value
            body.startsWith("#") -> codePointString(body.drop(1), 10) ?: match.value
            else -> NAMED_ENTITIES[body.lowercase()] ?: match.value
        }
    }

    private fun codePointString(digits: String, radix: Int): String? =
        digits.toIntOrNull(radix)
            ?.takeIf { it in 0..0x10FFFF && it !in 0xD800..0xDFFF }
            ?.let { String(Character.toChars(it)) }

    private val TIMESTAMP_REGEX = Regex("(?<!\\d)(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})")
    private val WEBVTT_TIME_REGEX = Regex("(?<![\\d:])((?:\\d{1,2}:)?\\d{2}:\\d{2}\\.\\d{1,3})\\s*-->\\s*((?:\\d{1,2}:)?\\d{2}:\\d{2}\\.\\d{1,3})")
    private val INLINE_TAG_REGEX = Regex("</?[a-zA-Z][^>]*>")
    private val BR_TAG_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val VTT_TIMESTAMP_TAG_REGEX = Regex("</?\\d{1,2}:\\d{2}(?::\\d{2})?\\.\\d{1,3}>")
    private val ENTITY_REGEX = Regex("&(#x[0-9a-fA-F]+|#X[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "lrm" to "‎",
        "rlm" to "‏",
    )
    private val ASS_ALIGNMENT_REGEX = Regex("\\\\an([1-9])")
}
