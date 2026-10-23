package dev.pishi.player.app

import android.content.Context

/**
 * User-facing settings for the external-subtitle overlay renderer. Persisted in
 * SharedPreferences. Only settings the overlay can actually honor are stored —
 * the capability limits live in subtitle-android's ComposeOverlayRenderingPlan
 * (no outline, no per-cue ASS positioning) and this model mirrors them.
 */
data class SubtitleSettings(
    val enabled: Boolean = true,
    val fontSizeSp: Float = 18f,
    val textColorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundAlpha: Float = 0.55f,
    val delayMs: Long = 0L,
    val encodingOverride: String? = null,
    val fontFamilyChoice: String = "default",
    val bold: Boolean = false,
) {
    companion object {
        val COLOR_CHOICES: List<Pair<String, Int>> = listOf(
            "White" to 0xFFFFFFFF.toInt(),
            "Yellow" to 0xFFFFD600.toInt(),
            "Cyan" to 0xFF80DEEA.toInt(),
            "Green" to 0xFFA5D6A7.toInt(),
        )
        val FONT_CHOICES: List<String> = listOf("default", "sans", "serif", "mono")
        val ENCODING_CHOICES: List<Pair<String, String?>> = listOf(
            "Auto" to null,
            "UTF-8" to "UTF-8",
            "UTF-16" to "UTF-16",
            "Windows-1256" to "windows-1256",
            "Windows-1252" to "windows-1252",
        )
    }
}

class SubtitleSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("subtitle_settings", Context.MODE_PRIVATE)

    fun load(): SubtitleSettings = SubtitleSettings(
        enabled = prefs.getBoolean("enabled", true),
        fontSizeSp = prefs.getFloat("font_size_sp", 18f),
        textColorArgb = prefs.getInt("text_color", 0xFFFFFFFF.toInt()),
        backgroundAlpha = prefs.getFloat("bg_alpha", 0.55f),
        delayMs = prefs.getLong("delay_ms", 0L),
        encodingOverride = prefs.getString("encoding", null),
        fontFamilyChoice = prefs.getString("font_family", "default") ?: "default",
        bold = prefs.getBoolean("bold", false),
    )

    fun save(settings: SubtitleSettings) {
        prefs.edit()
            .putBoolean("enabled", settings.enabled)
            .putFloat("font_size_sp", settings.fontSizeSp)
            .putInt("text_color", settings.textColorArgb)
            .putFloat("bg_alpha", settings.backgroundAlpha)
            .putLong("delay_ms", settings.delayMs)
            .putString("encoding", settings.encodingOverride)
            .putString("font_family", settings.fontFamilyChoice)
            .putBoolean("bold", settings.bold)
            .apply()
    }
}
