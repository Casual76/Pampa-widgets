package com.pampa.widgets.widget.media

import com.pampa.widgets.core.media.MediaPackages
import com.pampa.widgets.core.media.MediaPlaybackAvailability
import com.pampa.widgets.core.media.MediaPlaybackSnapshot
import com.pampa.widgets.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaWidgetUpdateOrderingTest {
  @Test
  fun onlyNewestIssuedGenerationCanCommit() {
    val generations = LatestUpdateGeneration()
    val slowArtworkRead = generations.issue()
    val newerTrackRead = generations.issue()

    assertFalse(generations.isLatest(slowArtworkRead))
    assertTrue(generations.isLatest(newerTrackRead))
  }

  @Test
  fun worstCaseRemoteViewsBitmapPayloadStaysBounded() {
    val fullPage = widgetBackgroundBitmapSize(widthDp = 520, heightDp = 620, density = 4f)
    val mini = widgetBackgroundBitmapSize(widthDp = 110, heightDp = 110, density = 1f)

    assertTrue(fullPage.width <= 720)
    assertTrue(fullPage.height <= 720)
    assertTrue(mini.width >= 220)
    assertTrue(mini.height >= 220)
    assertTrue(estimatedRemoteViewsBitmapBytes(fullPage) < 6L * 1024L * 1024L)
  }

  @Test
  fun repeatedPlayingSnapshotsShareAStableRenderSignature() {
    val settings = AppSettings()
    val layout = MediaWidgetLayoutSpec.fromDimensions(300, 210, settings)
    val first = playingSnapshot(positionMs = 42_000L)
    val second = playingSnapshot(positionMs = 42_500L)

    val firstSignature = mediaWidgetRenderSignature(first, settings, layout, null, 100_000L)
    val secondSignature = mediaWidgetRenderSignature(second, settings, layout, null, 100_500L)

    assertEquals(firstSignature, secondSignature)
  }

  @Test
  fun aSeekOrVisibleStateChangeInvalidatesTheRenderSignature() {
    val settings = AppSettings()
    val layout = MediaWidgetLayoutSpec.fromDimensions(300, 210, settings)
    val baseline = mediaWidgetRenderSignature(
      playingSnapshot(positionMs = 42_000L), settings, layout, null, 100_000L,
    )
    val seeked = mediaWidgetRenderSignature(
      playingSnapshot(positionMs = 82_000L), settings, layout, null, 100_500L,
    )
    val paused = mediaWidgetRenderSignature(
      playingSnapshot(positionMs = 42_000L).copy(isPlaying = false, playbackSpeed = 0f),
      settings,
      layout,
      null,
      100_000L,
    )

    assertNotEquals(baseline, seeked)
    assertNotEquals(baseline, paused)
  }

  @Test
  fun mediaLaunchTargetNeverFallsBackToPampaSettings() {
    val rememberedSpotify = MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.NoSession,
      packageName = MediaPackages.Spotify,
    )
    val unknownTarget = rememberedSpotify.copy(packageName = "", sourceLabel = "In attesa")

    assertEquals(MediaPackages.Spotify, rememberedSpotify.launchPackageName())
    assertEquals("", unknownTarget.launchPackageName())
  }

  private fun playingSnapshot(positionMs: Long) = MediaPlaybackSnapshot(
    availability = MediaPlaybackAvailability.Active,
    title = "Song",
    artist = "Artist",
    mediaId = "track-1",
    sourceLabel = "Spotify",
    packageName = MediaPackages.Spotify,
    isPlaying = true,
    canPlayPause = true,
    canSkipNext = true,
    artworkKey = "art-1",
    positionMs = positionMs,
    durationMs = 180_000L,
    playbackSpeed = 1f,
  )
}
