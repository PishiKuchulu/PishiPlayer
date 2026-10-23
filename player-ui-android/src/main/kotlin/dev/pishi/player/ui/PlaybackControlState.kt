package dev.pishi.player.ui

import dev.pishi.player.api.PlaybackSnapshot

data class PlaybackControlState(val snapshot: PlaybackSnapshot, val seekPreviewMs: Long? = null, val controlsVisible: Boolean = true)
