package com.pampa.widgets.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSessionSelectorTest {
  @Test
  fun spotifyWinsWhenMultipleMediaAppsAreActive() {
    val spotify = MediaSessionCandidate(
      packageName = MediaPackages.Spotify,
      isPlaying = true,
      hasMetadata = true,
      hasSongIdentity = true,
      supportsTransportControls = true,
      lastPositionUpdateTime = 10L,
    )
    val youtubeMusic = spotify.copy(
      packageName = MediaPackages.YouTubeMusic,
      lastPositionUpdateTime = 20L,
    )

    assertEquals(spotify, chooseBestMediaSession(listOf(youtubeMusic, spotify)))
  }

  @Test
  fun unsupportedGenericSessionDoesNotBeatSpotify() {
    val pausedSpotify = MediaSessionCandidate(
      packageName = MediaPackages.Spotify,
      isPlaying = false,
      hasMetadata = true,
      hasSongIdentity = true,
      supportsTransportControls = true,
    )
    val playingGeneric = MediaSessionCandidate(
      packageName = "com.example.player",
      isPlaying = true,
      hasMetadata = true,
      hasSongIdentity = true,
      supportsTransportControls = true,
    )

    assertEquals(pausedSpotify, chooseBestMediaSession(listOf(pausedSpotify, playingGeneric)))
  }

  @Test
  fun playingMusicAppWinsOverPausedSpotify() {
    val pausedSpotify = MediaSessionCandidate(
      packageName = MediaPackages.Spotify,
      isPlaying = false,
      hasMetadata = true,
      hasSongIdentity = true,
      supportsTransportControls = true,
    )
    val appleMusic = MediaSessionCandidate(
      packageName = MediaPackages.AppleMusic,
      isPlaying = true,
      hasMetadata = true,
      hasSongIdentity = true,
      supportsTransportControls = true,
    )

    assertEquals(appleMusic, chooseBestMediaSession(listOf(pausedSpotify, appleMusic)))
  }

  @Test
  fun ignoresSessionsWithoutTransportControls() {
    val unavailable = MediaSessionCandidate(
      packageName = MediaPackages.Spotify,
      isPlaying = true,
      hasMetadata = true,
      hasSongIdentity = true,
      supportsTransportControls = false,
    )

    assertNull(chooseBestMediaSession(listOf(unavailable)))
  }

  @Test
  fun ignoresNonMusicPackagesEvenWhenPlaying() {
    val youtubeVideo = MediaSessionCandidate(
      packageName = "com.google.android.youtube",
      isPlaying = true,
      hasMetadata = true,
      hasSongIdentity = true,
      supportsTransportControls = true,
    )

    assertNull(chooseBestMediaSession(listOf(youtubeVideo)))
  }
}
