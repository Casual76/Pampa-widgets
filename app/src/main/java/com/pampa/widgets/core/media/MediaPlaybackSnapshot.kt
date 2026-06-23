package com.pampa.widgets.core.media

import android.graphics.Bitmap

enum class MediaPlaybackAvailability {
  PermissionRequired,
  NoSession,
  Active,
}

enum class MediaControlAction {
  TogglePlayPause,
  Next,
  Previous,
}

data class MediaPlaybackSnapshot(
  val availability: MediaPlaybackAvailability,
  val title: String = "",
  val artist: String = "",
  val sourceLabel: String = "",
  val packageName: String = "",
  val isPlaying: Boolean = false,
  val canPlayPause: Boolean = false,
  val canSkipNext: Boolean = false,
  val canSkipPrevious: Boolean = false,
  val artwork: Bitmap? = null,
)
