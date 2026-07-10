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
  private const val KeyIsPlaying = "is_playing"
  private const val KeyCanPlayPause = "can_play_pause"
  private const val KeyCanSkipNext = "can_skip_next"
  private const val KeyCanSkipPrevious = "can_skip_previous"
  private const val KeyUpdatedAt = "updated_at"
  private const val MaxCacheAgeMillis = 7 * 24 * 60 * 60 * 1000L

  fun save(context: Context, snapshot: MediaPlaybackSnapshot) {
    if (snapshot.title.isBlank() || snapshot.artist.isBlank()) return
    val artwork = snapshot.artwork
    if (artwork != null) {
      writeArtworkAtomically(context, artwork)
    } else {
      // Never pair a new track title with a previous track's cover while the media app is still
      // loading artwork.
      artworkFile(context).delete()
    }
    context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
      .edit()
      .putString(KeyTitle, snapshot.title)
      .putString(KeyArtist, snapshot.artist)
      .putString(KeySource, snapshot.sourceLabel)
      .putString(KeyPackageName, snapshot.packageName)
      .putLong(KeyPositionMs, snapshot.positionMs)
      .putLong(KeyDurationMs, snapshot.durationMs)
      .putBoolean(KeyIsPlaying, snapshot.isPlaying)
      .putBoolean(KeyCanPlayPause, snapshot.canPlayPause)
      .putBoolean(KeyCanSkipNext, snapshot.canSkipNext)
      .putBoolean(KeyCanSkipPrevious, snapshot.canSkipPrevious)
      .putLong(KeyUpdatedAt, System.currentTimeMillis())
      .apply()
  }

  fun read(context: Context): MediaPlaybackSnapshot? {
    val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    val updatedAt = preferences.getLong(KeyUpdatedAt, 0L)
    if (updatedAt <= 0L || System.currentTimeMillis() - updatedAt > MaxCacheAgeMillis) return null
    val title = preferences.getString(KeyTitle, "").orEmpty()
    val artist = preferences.getString(KeyArtist, "").orEmpty()
    if (title.isBlank() || artist.isBlank()) return null

    val packageName = preferences.getString(KeyPackageName, "").orEmpty()
    // Old cache entries did not include capability flags. Keep them actionable if they identify
    // a media app: MediaSessionReader can send a platform media-key fallback after idle.
    val hasRememberedTarget = packageName.isNotBlank()
    return MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.NoSession,
      title = title,
      artist = artist,
      sourceLabel = preferences.getString(KeySource, "Musica").orEmpty(),
      packageName = packageName,
      isPlaying = preferences.getBoolean(KeyIsPlaying, false),
      canPlayPause = preferences.getBoolean(KeyCanPlayPause, hasRememberedTarget),
      canSkipNext = preferences.getBoolean(KeyCanSkipNext, hasRememberedTarget),
      canSkipPrevious = preferences.getBoolean(KeyCanSkipPrevious, hasRememberedTarget),
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

  private fun writeArtworkAtomically(context: Context, artwork: Bitmap) {
    val target = artworkFile(context)
    val temporary = File(target.parentFile, "${target.name}.tmp")
    val written = runCatching {
      temporary.outputStream().use { output ->
        artwork.compress(Bitmap.CompressFormat.PNG, 92, output)
      }
      if (!temporary.renameTo(target)) {
        temporary.copyTo(target, overwrite = true)
        temporary.delete()
      }
    }.isSuccess
    if (!written) {
      temporary.delete()
      target.delete()
    }
  }

  private fun artworkFile(context: Context): File {
    return File(context.filesDir, "media_widget_artwork.png")
  }
}
