package dev.pishi.player.app

import android.content.Context
import dev.pishi.player.api.EnginePreference

/** Persists the user's engine preference (Auto / Hardware / Software). */
class EngineSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("engine_settings", Context.MODE_PRIVATE)

    fun load(): EnginePreference =
        runCatching { EnginePreference.valueOf(prefs.getString("preference", null) ?: EnginePreference.AUTO.name) }
            .getOrDefault(EnginePreference.AUTO)

    fun save(preference: EnginePreference) {
        prefs.edit().putString("preference", preference.name).apply()
    }
}
