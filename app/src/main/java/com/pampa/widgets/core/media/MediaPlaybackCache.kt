package com.pampa.widgets.core.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

/**
 * A track-keyed, crash-safe last-playback cache.
 *
 * Artwork is written before the metadata commit and is only read when its stored track key matches
 * the metadata track key. A crash can therefore leave an unused file, but can never pair the image
 * from one song with the title of another.
 */
object MediaPlaybackCache {
  private const val PreferencesName = "media_playback_cache"
  private const val CacheVersion = 2
  private const val KeyCacheVersion = "cache_version"
  private const val KeyTrackKey = "track_key"
  private const val KeyTitle = "title"
  private const val KeyArtist = "artist"
  private const val KeyAlbum = "album"
  private const val KeyMediaId = "media_id"
  private const val KeySource = "source"
  private const val KeyPackageName = "package_name"
  private const val KeyArtworkTrackKey = "artwork_track_key"
  private const val KeyArtworkFile = "artwork_file"
  private const val KeyArtworkKey = "artwork_key"
  private const val KeyArtworkUri = "artwork_uri"
  private const val KeyPositionMs = "position_ms"
  private const val KeyDurationMs = "duration_ms"
  private const val KeyIsPlaying = "is_playing"
  private const val KeyCanPlayPause = "can_play_pause"
  private const val KeyCanSkipNext = "can_skip_next"
  private const val KeyCanSkipPrevious = "can_skip_previous"
  private const val KeyUpdatedAt = "updated_at"
  private const val LegacyArtworkFileName = "media_widget_artwork.png"
  private const val MaxCacheAgeMillis = 7 * 24 * 60 * 60 * 1000L
  private const val PositionCheckpointIntervalMillis = 15_000L

  data class RememberedMediaTarget(
    val packageName: String,
    val sourceLabel: String,
  )

  @Synchronized
  fun save(context: Context, snapshot: MediaPlaybackSnapshot) {
    val trackKey = snapshot.trackKey
    if (snapshot.title.isBlank() || snapshot.artist.isBlank() || trackKey.isBlank()) return

    val preferences = preferences(context)
    val previousTrackKey = preferences.getString(KeyTrackKey, "").orEmpty()
    val previousArtworkFile = preferences.getString(KeyArtworkFile, "").orEmpty()
    val canReuseArtwork = snapshot.artwork != null &&
      snapshot.artworkKey.isNotBlank() &&
      previousTrackKey == trackKey &&
      preferences.getString(KeyArtworkTrackKey, "").orEmpty() == trackKey &&
      preferences.getString(KeyArtworkKey, "").orEmpty() == snapshot.artworkKey &&
      previousArtworkFile.isNotBlank() &&
      File(context.filesDir, previousArtworkFile).isFile
    val unchangedMissingArtwork = snapshot.artwork == null &&
      snapshot.artworkKey.isBlank() &&
      previousTrackKey == trackKey &&
      previousArtworkFile.isBlank()

    if ((canReuseArtwork || unchangedMissingArtwork) &&
      preferences.matchesStableSnapshot(snapshot, trackKey)
    ) {
      return
    }

    val newArtworkFile = snapshot.artwork?.let { artwork ->
      if (canReuseArtwork) {
        previousArtworkFile
      } else {
        val target = artworkFile(context, trackKey)
        if (!writeArtworkAtomically(target, artwork)) return
        target.name
      }
    }.orEmpty()

    val committed = writeSnapshot(
      editor = preferences.edit(),
      snapshot = snapshot,
      trackKey = trackKey,
      artworkFileName = newArtworkFile,
      artworkTrackKey = if (newArtworkFile.isNotBlank()) trackKey else "",
    ).commit()

    if (committed) {
      if (previousArtworkFile.isNotBlank() && previousArtworkFile != newArtworkFile) {
        File(context.filesDir, previousArtworkFile).delete()
      }
      legacyArtworkFile(context).delete()
    } else if (newArtworkFile.isNotBlank() && newArtworkFile != previousArtworkFile) {
      File(context.filesDir, newArtworkFile).delete()
    }
  }

  @Synchronized
  fun read(context: Context): MediaPlaybackSnapshot? {
    val preferences = preferences(context)
    if (!isFresh(preferences)) return null
    if (preferences.getInt(KeyCacheVersion, 0) != CacheVersion) {
      return migrateLegacyEntry(context, preferences)
    }

    val title = preferences.getString(KeyTitle, "").orEmpty()
    val artist = preferences.getString(KeyArtist, "").orEmpty()
    if (title.isBlank() || artist.isBlank()) return null

    val packageName = preferences.getString(KeyPackageName, "").orEmpty()
    val hasRememberedTarget = packageName.isNotBlank()
    val snapshot = MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.NoSession,
      title = title,
      artist = artist,
      album = preferences.getString(KeyAlbum, "").orEmpty(),
      mediaId = preferences.getString(KeyMediaId, "").orEmpty(),
      sourceLabel = preferences.getString(KeySource, "Musica").orEmpty(),
      packageName = packageName,
      isPlaying = preferences.getBoolean(KeyIsPlaying, false),
      canPlayPause = preferences.getBoolean(KeyCanPlayPause, hasRememberedTarget),
      canSkipNext = preferences.getBoolean(KeyCanSkipNext, hasRememberedTarget),
      canSkipPrevious = preferences.getBoolean(KeyCanSkipPrevious, hasRememberedTarget),
      artworkUri = preferences.getString(KeyArtworkUri, "").orEmpty(),
      artworkKey = preferences.getString(KeyArtworkKey, "").orEmpty(),
      positionMs = preferences.getLong(KeyPositionMs, 0L),
      durationMs = preferences.getLong(KeyDurationMs, 0L),
      isFromCache = true,
    )
    val storedTrackKey = preferences.getString(KeyTrackKey, "").orEmpty()
    val artworkTrackKey = preferences.getString(KeyArtworkTrackKey, "").orEmpty()
    val artworkFileName = preferences.getString(KeyArtworkFile, "").orEmpty()
    val artwork = if (
      storedTrackKey == snapshot.trackKey &&
      artworkTrackKey == snapshot.trackKey &&
      artworkFileName.isNotBlank()
    ) {
      readArtwork(File(context.filesDir, artworkFileName))
    } else {
      null
    }
    return snapshot.copy(artwork = artwork)
  }

  /**
   * Reads only the last actionable media-app target without decoding cached artwork.
   * This remains available when the user hides the last song from the widget.
   */
  @Synchronized
  fun readRememberedTarget(context: Context): RememberedMediaTarget? {
    val preferences = preferences(context)
    if (!isFresh(preferences)) return null
    val packageName = preferences.getString(KeyPackageName, "").orEmpty()
    if (packageName.isBlank()) return null
    return RememberedMediaTarget(
      packageName = packageName,
      sourceLabel = preferences.getString(KeySource, "Musica").orEmpty(),
    )
  }

  @SuppressLint("ApplySharedPref")
  private fun migrateLegacyEntry(
    context: Context,
    preferences: SharedPreferences,
  ): MediaPlaybackSnapshot? {
    val title = preferences.getString(KeyTitle, "").orEmpty()
    val artist = preferences.getString(KeyArtist, "").orEmpty()
    if (title.isBlank() || artist.isBlank()) {
      preferences.edit().clear().putInt(KeyCacheVersion, CacheVersion).commit()
      legacyArtworkFile(context).delete()
      return null
    }
    val packageName = preferences.getString(KeyPackageName, "").orEmpty()
    val hasRememberedTarget = packageName.isNotBlank()
    val snapshot = MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.NoSession,
      title = title,
      artist = artist,
      sourceLabel = preferences.getString(KeySource, "Musica").orEmpty(),
      packageName = packageName,
      isPlaying = preferences.getBoolean(KeyIsPlaying, false),
      canPlayPause = preferences.getBoolean(KeyCanPlayPause, hasRememberedTarget),
      canSkipNext = preferences.getBoolean(KeyCanSkipNext, hasRememberedTarget),
      canSkipPrevious = preferences.getBoolean(KeyCanSkipPrevious, hasRememberedTarget),
      positionMs = preferences.getLong(KeyPositionMs, 0L),
      durationMs = preferences.getLong(KeyDurationMs, 0L),
      isFromCache = true,
    )
    writeSnapshot(
      editor = preferences.edit().clear(),
      snapshot = snapshot,
      trackKey = snapshot.trackKey,
      artworkFileName = "",
      artworkTrackKey = "",
    ).commit()
    legacyArtworkFile(context).delete()
    return snapshot
  }

  private fun writeSnapshot(
    editor: SharedPreferences.Editor,
    snapshot: MediaPlaybackSnapshot,
    trackKey: String,
    artworkFileName: String,
    artworkTrackKey: String,
  ): SharedPreferences.Editor = editor
    .putInt(KeyCacheVersion, CacheVersion)
    .putString(KeyTrackKey, trackKey)
    .putString(KeyTitle, snapshot.title)
    .putString(KeyArtist, snapshot.artist)
    .putString(KeyAlbum, snapshot.album)
    .putString(KeyMediaId, snapshot.mediaId)
    .putString(KeySource, snapshot.sourceLabel)
    .putString(KeyPackageName, snapshot.packageName)
    .putString(KeyArtworkTrackKey, artworkTrackKey)
    .putString(KeyArtworkFile, artworkFileName)
    .putString(KeyArtworkKey, if (artworkFileName.isNotBlank()) snapshot.artworkKey else "")
    .putString(KeyArtworkUri, snapshot.artworkUri)
    .putLong(KeyPositionMs, snapshot.positionMs)
    .putLong(KeyDurationMs, snapshot.durationMs)
    .putBoolean(KeyIsPlaying, snapshot.isPlaying)
    .putBoolean(KeyCanPlayPause, snapshot.canPlayPause)
    .putBoolean(KeyCanSkipNext, snapshot.canSkipNext)
    .putBoolean(KeyCanSkipPrevious, snapshot.canSkipPrevious)
    .putLong(KeyUpdatedAt, System.currentTimeMillis())

  private fun isFresh(preferences: SharedPreferences): Boolean {
    val updatedAt = preferences.getLong(KeyUpdatedAt, 0L)
    return updatedAt > 0L && System.currentTimeMillis() - updatedAt <= MaxCacheAgeMillis
  }

  private fun SharedPreferences.matchesStableSnapshot(
    snapshot: MediaPlaybackSnapshot,
    trackKey: String,
  ): Boolean {
    val checkpointAge = System.currentTimeMillis() - getLong(KeyUpdatedAt, 0L)
    if (checkpointAge !in 0 until PositionCheckpointIntervalMillis) return false
    return getInt(KeyCacheVersion, 0) == CacheVersion &&
      getString(KeyTrackKey, "").orEmpty() == trackKey &&
      getString(KeyTitle, "").orEmpty() == snapshot.title &&
      getString(KeyArtist, "").orEmpty() == snapshot.artist &&
      getString(KeyAlbum, "").orEmpty() == snapshot.album &&
      getString(KeyMediaId, "").orEmpty() == snapshot.mediaId &&
      getString(KeySource, "").orEmpty() == snapshot.sourceLabel &&
      getString(KeyPackageName, "").orEmpty() == snapshot.packageName &&
      getString(KeyArtworkKey, "").orEmpty() == snapshot.artworkKey &&
      getString(KeyArtworkUri, "").orEmpty() == snapshot.artworkUri &&
      getLong(KeyDurationMs, 0L) == snapshot.durationMs &&
      getBoolean(KeyIsPlaying, false) == snapshot.isPlaying &&
      getBoolean(KeyCanPlayPause, false) == snapshot.canPlayPause &&
      getBoolean(KeyCanSkipNext, false) == snapshot.canSkipNext &&
      getBoolean(KeyCanSkipPrevious, false) == snapshot.canSkipPrevious &&
      abs(getLong(KeyPositionMs, 0L) - snapshot.positionMs) < PositionCheckpointIntervalMillis
  }

  private fun readArtwork(file: File): Bitmap? {
    if (!file.isFile) return null
    return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
  }

  private fun writeArtworkAtomically(target: File, artwork: Bitmap): Boolean {
    val temporary = File(target.parentFile, "${target.name}.tmp")
    val written = runCatching {
      temporary.outputStream().buffered().use { output ->
        check(artwork.compress(Bitmap.CompressFormat.PNG, 100, output))
      }
      if (!temporary.renameTo(target)) {
        temporary.copyTo(target, overwrite = true)
        temporary.delete()
      }
    }.isSuccess
    if (!written) temporary.delete()
    return written
  }

  private fun artworkFile(context: Context, trackKey: String): File {
    val digest = MessageDigest.getInstance("SHA-256")
      .digest(trackKey.toByteArray(Charsets.UTF_8))
      .joinToString("") { byte -> "%02x".format(byte) }
      .take(24)
    return File(context.filesDir, "media_widget_artwork_v2_$digest.png")
  }

  private fun legacyArtworkFile(context: Context): File =
    File(context.filesDir, LegacyArtworkFileName)

  private fun preferences(context: Context): SharedPreferences =
    context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
