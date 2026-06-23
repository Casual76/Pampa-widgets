package com.pampa.widgets.core.media

data class MediaSessionCandidate(
  val packageName: String,
  val isPlaying: Boolean,
  val hasMetadata: Boolean,
  val supportsTransportControls: Boolean,
  val lastPositionUpdateTime: Long = 0L,
)

object MediaPackages {
  const val Spotify = "com.spotify.music"
  const val YouTubeMusic = "com.google.android.apps.youtube.music"
}

fun chooseBestMediaSession(candidates: List<MediaSessionCandidate>): MediaSessionCandidate? {
  return candidates
    .filter { it.supportsTransportControls }
    .maxWithOrNull(
      compareBy<MediaSessionCandidate> { it.isPlaying }
        .thenBy { appPriority(it.packageName) }
        .thenBy { it.hasMetadata }
        .thenBy { it.lastPositionUpdateTime },
    )
}

private fun appPriority(packageName: String): Int {
  return when (packageName) {
    MediaPackages.Spotify -> 3
    MediaPackages.YouTubeMusic -> 2
    else -> 1
  }
}
