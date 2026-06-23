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
  fun playingSessionWinsOverPausedSpotify() {
    val pausedSpotify = MediaSessionCandidate(
      packageName = MediaPackages.Spotify,
      isPlaying = false,
      hasMetadata = true,
      supportsTransportControls = true,
    )
    val playingGeneric = MediaSessionCandidate(
      packageName = "com.example.player",
      isPlaying = true,
      hasMetadata = true,
      supportsTransportControls = true,
    )

    assertEquals(playingGeneric, chooseBestMediaSession(listOf(pausedSpotify, playingGeneric)))
  }

  @Test
  fun ignoresSessionsWithoutTransportControls() {
    val unavailable = MediaSessionCandidate(
      packageName = MediaPackages.Spotify,
      isPlaying = true,
      hasMetadata = true,
      supportsTransportControls = false,
    )

    assertNull(chooseBestMediaSession(listOf(unavailable)))
  }
}
