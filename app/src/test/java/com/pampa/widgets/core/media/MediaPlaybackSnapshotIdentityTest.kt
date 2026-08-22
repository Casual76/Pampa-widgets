package com.pampa.widgets.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlaybackSnapshotIdentityTest {
  @Test
  fun mediaIdIsPreferredOverMutableDisplayText() {
    val first = snapshot(mediaId = "spotify:track:42", title = "Title", artist = "Artist")
    val renamed = snapshot(mediaId = "spotify:track:42", title = "Title (Remastered)", artist = "Artist")

    assertEquals(first.trackKey, renamed.trackKey)
  }

  @Test
  fun fallbackIdentitySeparatesTracksAndNormalizesWhitespace() {
    val first = snapshot(mediaId = "", title = "  First   Song ", artist = " Artist ")
    val normalized = snapshot(mediaId = "", title = "first song", artist = "artist")
    val second = snapshot(mediaId = "", title = "Second Song", artist = "Artist")

    assertEquals(first.trackKey, normalized.trackKey)
    assertNotEquals(first.trackKey, second.trackKey)
    assertTrue(first.trackKey.startsWith("com.spotify.music|track:"))
  }

  @Test
  fun packageIsPartOfIdentity() {
    val spotify = snapshot(mediaId = "same", title = "Song", artist = "Artist")
    val other = spotify.copy(packageName = MediaPackages.YouTubeMusic)

    assertNotEquals(spotify.trackKey, other.trackKey)
  }

  private fun snapshot(mediaId: String, title: String, artist: String) =
    MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.Active,
      packageName = MediaPackages.Spotify,
      mediaId = mediaId,
      title = title,
      artist = artist,
      album = "Album",
    )
}
