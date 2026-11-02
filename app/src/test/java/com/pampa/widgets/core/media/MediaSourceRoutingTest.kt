package com.pampa.widgets.core.media

import com.pampa.widgets.core.settings.MediaSourceMode
import org.junit.Assert.*
import org.junit.Test

class MediaSourceRoutingTest {
  private fun candidate(pkg: String, playing: Boolean = true) = MediaSessionCandidate(pkg, playing,
    hasMetadata = true, hasSongIdentity = true, supportsTransportControls = true)

  @Test fun automaticPreservesPlayingAndIdentityPriorityWithFluidifyAfterSpotify() {
    val spotify = candidate(MediaPackages.Spotify)
    val fluidify = candidate(MediaPackages.Fluidify)
    assertEquals(spotify, chooseBestMediaSession(listOf(fluidify, spotify)))
    assertEquals(fluidify, chooseBestMediaSession(listOf(candidate(MediaPackages.AppleMusic), fluidify)))
    assertEquals(fluidify, chooseBestMediaSession(listOf(spotify.copy(isPlaying = false), fluidify)))
    assertEquals(fluidify, chooseBestMediaSession(listOf(spotify.copy(hasSongIdentity = false), fluidify)))
  }

  @Test fun explicitModesNeverFallBackToAnotherApp() {
    val sessions = listOf(candidate(MediaPackages.Spotify), candidate(MediaPackages.Fluidify, false))
    assertEquals(MediaPackages.Fluidify, chooseBestMediaSession(sessions, MediaSourceMode.Fluidify)?.packageName)
    assertEquals(MediaPackages.Spotify, chooseBestMediaSession(sessions, MediaSourceMode.Spotify)?.packageName)
    assertNull(chooseBestMediaSession(sessions.take(1), MediaSourceMode.Fluidify))
  }

  @Test fun simultaneousWidgetsAndRapidCommandsKeepTheirShownSession() {
    val sessions = listOf(MediaPackages.Spotify to 10, MediaPackages.Fluidify to 20)
    repeat(20) {
      assertEquals(10, resolveMediaCommandTarget(MediaSourceMode.Spotify, MediaPackages.Spotify, 10, sessions))
      assertEquals(20, resolveMediaCommandTarget(MediaSourceMode.Fluidify, MediaPackages.Fluidify, 20, sessions.reversed()))
    }
    assertEquals(20, resolveMediaCommandTarget(MediaSourceMode.Automatic, MediaPackages.Fluidify, 20, sessions))
  }

  @Test fun sourceChangedDuringReadOrSessionReplacedRejectsStaleCommands() {
    val sessions = listOf(MediaPackages.Spotify to 10, MediaPackages.Fluidify to 21)
    assertNull(resolveMediaCommandTarget(MediaSourceMode.Fluidify, MediaPackages.Spotify, 10, sessions))
    assertNull(resolveMediaCommandTarget(MediaSourceMode.Fluidify, MediaPackages.Fluidify, 20, sessions))
    assertNull(resolveMediaCommandTarget(MediaSourceMode.Spotify, MediaPackages.Spotify, 10, emptyList()))
    assertNull(resolveMediaCommandTarget(MediaSourceMode.Automatic, MediaPackages.Spotify, null, sessions))
  }
}
