package com.pampa.widgets.core.media

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPlaybackCacheInstrumentedTest {
  @Test
  fun cacheNeverPairsNewTrackWithPreviousArtwork() {
    val context = IsolatedCacheContext(InstrumentationRegistry.getInstrumentation().targetContext)
    context.clear()
    try {
      val first = snapshot("first", "First song", coloredArtwork(Color.RED))
      MediaPlaybackCache.save(context, first)
      assertEquals(Color.RED, MediaPlaybackCache.read(context)?.artwork?.getPixel(10, 10))

      val secondWithoutArtwork = snapshot("second", "Second song", artwork = null)
      MediaPlaybackCache.save(context, secondWithoutArtwork)
      val missingArtworkRead = MediaPlaybackCache.read(context)
      assertEquals(secondWithoutArtwork.trackKey, missingArtworkRead?.trackKey)
      assertNull(missingArtworkRead?.artwork)

      val secondWithArtwork = secondWithoutArtwork.copy(
        artwork = coloredArtwork(Color.BLUE),
        artworkKey = "blue",
      )
      MediaPlaybackCache.save(context, secondWithArtwork)
      assertEquals(Color.BLUE, MediaPlaybackCache.read(context)?.artwork?.getPixel(10, 10))
    } finally {
      context.clear()
    }
  }

  @Test
  fun rememberedTargetIsAvailableWithoutDecodingOrShowingTheLastSong() {
    val context = IsolatedCacheContext(InstrumentationRegistry.getInstrumentation().targetContext)
    context.clear()
    try {
      MediaPlaybackCache.save(context, snapshot("spotify-track", "Song", coloredArtwork(Color.CYAN)))

      val target = MediaPlaybackCache.readRememberedTarget(context)

      assertEquals(MediaPackages.Spotify, target?.packageName)
      assertEquals("Spotify", target?.sourceLabel)
    } finally {
      context.clear()
    }
  }

  @Test
  fun identicalPlaybackCheckpointDoesNotRewriteMetadataOrArtwork() {
    val context = IsolatedCacheContext(InstrumentationRegistry.getInstrumentation().targetContext)
    context.clear()
    try {
      val first = snapshot("stable-track", "Stable song", coloredArtwork(Color.YELLOW))
        .copy(positionMs = 20_000L, durationMs = 180_000L, isPlaying = true)
      MediaPlaybackCache.save(context, first)
      val preferences = context.getSharedPreferences("media_playback_cache_${MediaPackages.Spotify}", Context.MODE_PRIVATE)
      val firstCheckpoint = preferences.getLong("updated_at", 0L)
      val artworkFile = File(context.filesDir, preferences.getString("artwork_file", "").orEmpty())
      val firstArtworkTimestamp = artworkFile.lastModified()
      Thread.sleep(25L)

      MediaPlaybackCache.save(context, first.copy(positionMs = 20_500L))

      assertEquals(firstCheckpoint, preferences.getLong("updated_at", 0L))
      assertEquals(firstArtworkTimestamp, artworkFile.lastModified())
    } finally {
      context.clear()
    }
  }

  @Test
  fun legacyMetadataMigratesButUnverifiedArtworkIsDiscarded() {
    val context = IsolatedCacheContext(InstrumentationRegistry.getInstrumentation().targetContext)
    context.clear()
    try {
      context.getSharedPreferences("media_playback_cache", Context.MODE_PRIVATE).edit()
        .putString("title", "Legacy song")
        .putString("artist", "Legacy artist")
        .putString("package_name", MediaPackages.Spotify)
        .putString("source", "Spotify")
        .putLong("updated_at", System.currentTimeMillis())
        .commit()
      val legacyArtwork = File(context.filesDir, "media_widget_artwork.png")
      legacyArtwork.outputStream().use { coloredArtwork(Color.MAGENTA).compress(Bitmap.CompressFormat.PNG, 100, it) }

      val migrated = MediaPlaybackCache.read(context)

      assertEquals("Legacy song", migrated?.title)
      assertEquals("Legacy song", MediaPlaybackCache.read(context)?.title)
      assertNull(migrated?.artwork)
      assertFalse(legacyArtwork.exists())
    } finally {
      context.clear()
    }
  }

  @Test
  fun fileArtworkUriIsDecodedAndDownsampled() {
    val context = IsolatedCacheContext(InstrumentationRegistry.getInstrumentation().targetContext)
    context.clear()
    try {
      val artworkFile = File(context.filesDir, "uri-artwork.png")
      artworkFile.outputStream().use { coloredArtwork(Color.GREEN, 900).compress(Bitmap.CompressFormat.PNG, 100, it) }
      val metadata = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, Uri.fromFile(artworkFile).toString())
        .build()

      val decoded = MediaSessionReader.resolveArtworkForTest(context, metadata)

      assertNotNull(decoded)
      assertEquals(Color.GREEN, decoded?.getPixel(10, 10))
      assertEquals(384, maxOf(decoded?.width ?: 0, decoded?.height ?: 0))
    } finally {
      context.clear()
    }
  }

  @Test
  fun twoPackagesKeepIndependentArtworkAndRememberedTargets() {
    val context = IsolatedCacheContext(InstrumentationRegistry.getInstrumentation().targetContext)
    context.clear()
    try {
      val spotify = snapshot("shared-id", "Spotify song", coloredArtwork(Color.RED))
      val fluidify = snapshot("shared-id", "Fluidify song", coloredArtwork(Color.BLUE))
        .copy(packageName = MediaPackages.Fluidify, sourceLabel = "Fluidify")
      MediaPlaybackCache.save(context, spotify)
      MediaPlaybackCache.save(context, fluidify)
      assertEquals("Spotify song", MediaPlaybackCache.read(context, MediaPackages.Spotify)?.title)
      assertEquals(Color.RED, MediaPlaybackCache.read(context, MediaPackages.Spotify)?.artwork?.getPixel(10, 10))
      assertEquals("Fluidify song", MediaPlaybackCache.read(context, MediaPackages.Fluidify)?.title)
      assertEquals(Color.BLUE, MediaPlaybackCache.read(context, MediaPackages.Fluidify)?.artwork?.getPixel(10, 10))
      assertEquals(MediaPackages.Spotify, MediaPlaybackCache.readRememberedTarget(context, MediaPackages.Spotify)?.packageName)
      MediaPlaybackCache.save(context, fluidify.copy(mediaId = "next", artwork = null, artworkKey = ""))
      assertEquals(Color.RED, MediaPlaybackCache.read(context, MediaPackages.Spotify)?.artwork?.getPixel(10, 10))
      assertNull(MediaPlaybackCache.read(context, MediaPackages.Fluidify)?.artwork)
    } finally { context.clear() }
  }

  private fun snapshot(mediaId: String, title: String, artwork: Bitmap?) =
    MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.Active,
      title = title,
      artist = "Artist",
      album = "Album",
      mediaId = mediaId,
      sourceLabel = "Spotify",
      packageName = MediaPackages.Spotify,
      artwork = artwork,
      artworkKey = artwork?.let { mediaId }.orEmpty(),
      canPlayPause = true,
    )

  private fun coloredArtwork(color: Int, size: Int = 64): Bitmap =
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

  private class IsolatedCacheContext(base: Context) : ContextWrapper(base) {
    private val root = File(base.cacheDir, "media-widget-cache-test").apply { mkdirs() }
    private val preferencesName = "media_playback_cache_instrumented_test"

    override fun getFilesDir(): File = root

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
      baseContext.getSharedPreferences("${preferencesName}_$name", mode)

    fun clear() {
      listOf("media_playback_cache", "media_last_target", "media_playback_cache_${MediaPackages.Spotify}",
        "media_playback_cache_${MediaPackages.Fluidify}").forEach { name ->
        getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
      }
      root.listFiles()?.forEach(File::delete)
    }
  }
}
