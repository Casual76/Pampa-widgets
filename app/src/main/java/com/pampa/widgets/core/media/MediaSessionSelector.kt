package com.pampa.widgets.core.media

data class MediaSessionCandidate(
  val packageName: String,
  val isPlaying: Boolean,
  val hasMetadata: Boolean,
  val hasSongIdentity: Boolean = false,
  val supportsTransportControls: Boolean,
  val lastPositionUpdateTime: Long = 0L,
)

object MediaPackages {
  const val Spotify = "com.spotify.music"
  const val YouTubeMusic = "com.google.android.apps.youtube.music"
  const val AppleMusic = "com.apple.android.music"
  const val SamsungMusic = "com.sec.android.app.music"
  const val AmazonMusic = "com.amazon.mp3"
  const val Deezer = "deezer.android.app"
  const val Tidal = "com.aspiro.tidal"
  const val SoundCloud = "com.soundcloud.android"
  const val VLC = "org.videolan.vlc"
}

fun chooseBestMediaSession(candidates: List<MediaSessionCandidate>): MediaSessionCandidate? {
  return candidates
    .filter { it.supportsTransportControls && isSupportedMusicPackage(it.packageName) }
    .maxWithOrNull(
      compareBy<MediaSessionCandidate> { it.isPlaying }
        .thenBy { it.hasSongIdentity }
        .thenBy { appPriority(it.packageName) }
        .thenBy { it.hasMetadata }
        .thenBy { it.lastPositionUpdateTime },
    )
}

fun isSupportedMusicPackage(packageName: String): Boolean {
  return packageName in setOf(
    MediaPackages.Spotify,
    MediaPackages.YouTubeMusic,
    MediaPackages.AppleMusic,
    MediaPackages.SamsungMusic,
    MediaPackages.AmazonMusic,
    MediaPackages.Deezer,
    MediaPackages.Tidal,
    MediaPackages.SoundCloud,
    MediaPackages.VLC,
  )
}

private fun appPriority(packageName: String): Int {
  return when (packageName) {
    MediaPackages.Spotify -> 9
    MediaPackages.AppleMusic -> 8
    MediaPackages.YouTubeMusic -> 7
    MediaPackages.SamsungMusic -> 6
    MediaPackages.Tidal -> 5
    MediaPackages.Deezer -> 4
    MediaPackages.AmazonMusic -> 3
    MediaPackages.SoundCloud -> 2
    else -> 1
  }
}
