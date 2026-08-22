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
  val album: String = "",
  val mediaId: String = "",
  val sourceLabel: String = "",
  val packageName: String = "",
  val isPlaying: Boolean = false,
  val canPlayPause: Boolean = false,
  val canSkipNext: Boolean = false,
  val canSkipPrevious: Boolean = false,
  val artwork: Bitmap? = null,
  val artworkUri: String = "",
  val artworkKey: String = "",
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val lastPositionUpdateTimeMs: Long = 0L,
  val playbackSpeed: Float = 0f,
  val isFromCache: Boolean = false,
) {
  /** Stable identity used to reject late artwork and cache entries from another track. */
  val trackKey: String
    get() {
      val packagePart = packageName.identityPart()
      val mediaPart = mediaId.identityPart()
      if (packagePart.isNotBlank() && mediaPart.isNotBlank()) {
        return "$packagePart|id:$mediaPart"
      }
      val titlePart = title.identityPart()
      val artistPart = artist.identityPart()
      val albumPart = album.identityPart()
      if (packagePart.isBlank() && titlePart.isBlank() && artistPart.isBlank()) return ""
      return "$packagePart|track:$titlePart|$artistPart|$albumPart"
    }
}

private fun String.identityPart(): String =
  trim().lowercase().replace(Regex("\\s+"), " ")
