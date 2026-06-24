package com.pampa.widgets.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object MediaPlaybackCache {
  private const val PreferencesName = "media_playback_cache"
  private const val KeyTitle = "title"
  private const val KeyArtist = "artist"
  private const val KeySource = "source"
  private const val KeyPackageName = "package_name"
  private const val KeyPositionMs = "position_ms"
  private const val KeyDurationMs = "duration_ms"
  private const val KeyUpdatedAt = "updated_at"
  private const val MaxCacheAgeMillis = 6 * 60 * 60 * 1000L

  fun save(context: Context, snapshot: MediaPlaybackSnapshot) {
    if (snapshot.title.isBlank() || snapshot.artist.isBlank()) return
    context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
      .edit()
      .putString(KeyTitle, snapshot.title)
      .putString(KeyArtist, snapshot.artist)
      .putString(KeySource, snapshot.sourceLabel)
      .putString(KeyPackageName, snapshot.packageName)
      .putLong(KeyPositionMs, snapshot.positionMs)
      .putLong(KeyDurationMs, snapshot.durationMs)
      .putLong(KeyUpdatedAt, System.currentTimeMillis())
      .apply()

    val artwork = snapshot.artwork ?: return
    runCatching {
      artworkFile(context).outputStream().use { output ->
        artwork.compress(Bitmap.CompressFormat.PNG, 92, output)
      }
    }
  }

  fun read(context: Context): MediaPlaybackSnapshot? {
    val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    val updatedAt = preferences.getLong(KeyUpdatedAt, 0L)
    if (updatedAt <= 0L || System.currentTimeMillis() - updatedAt > MaxCacheAgeMillis) return null
    val title = preferences.getString(KeyTitle, "").orEmpty()
    val artist = preferences.getString(KeyArtist, "").orEmpty()
    if (title.isBlank() || artist.isBlank()) return null

    return MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.Active,
      title = title,
      artist = artist,
      sourceLabel = preferences.getString(KeySource, "Musica").orEmpty(),
      packageName = preferences.getString(KeyPackageName, "").orEmpty(),
      canPlayPause = true,
      canSkipNext = true,
      canSkipPrevious = true,
      artwork = readArtwork(context),
      positionMs = preferences.getLong(KeyPositionMs, 0L),
      durationMs = preferences.getLong(KeyDurationMs, 0L),
      isFromCache = true,
    )
  }

  private fun readArtwork(context: Context): Bitmap? {
    val file = artworkFile(context)
    if (!file.isFile) return null
    return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
  }

  private fun artworkFile(context: Context): File {
    return File(context.filesDir, "media_widget_artwork.png")
  }
}
