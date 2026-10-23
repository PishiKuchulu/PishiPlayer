package dev.pishi.player.subtitle

/** Text-only helpers. Do not run these over ASS/SSA event markup or style headers. */
object SubtitleText {
    /** Canonicalize Unicode composition without changing Arabic/Persian letters or punctuation. */
    fun normalizeUnicode(text: String): String = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)

    /** Conservative normalization for common Persian Yeh/Kaf variants in cue text only. */
    fun normalizePersianLetters(text: String): String = normalizeUnicode(text)
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')

    /** Strip a UTF-8 BOM only; charset detection/decoding must happen before this stage. */
    fun stripLeadingBom(text: String): String = if (text.startsWith('\uFEFF')) text.drop(1) else text
}
