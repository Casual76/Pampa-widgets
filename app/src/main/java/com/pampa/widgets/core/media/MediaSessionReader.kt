package com.pampa.widgets.core.media

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.pampa.widgets.widget.media.MediaNotificationListenerService

object MediaSessionReader {
  fun readSnapshot(
    context: Context,
    keepLastSong: Boolean = true,
  ): MediaPlaybackSnapshot {
    if (!NotificationListenerAccess.isGranted(context)) {
      return MediaPlaybackSnapshot(
        availability = MediaPlaybackAvailability.PermissionRequired,
        title = "Accesso media richiesto",
        artist = "Abilita l'accesso notifiche per controllare Spotify.",
        sourceLabel = "Permesso richiesto",
      )
    }

    val controller = activeController(context) ?: return MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.NoSession,
      title = "Nessuna riproduzione",
      artist = "Apri Spotify, Apple Music o YouTube Music.",
      sourceLabel = "In attesa",
    ).let { if (keepLastSong) MediaPlaybackCache.read(context) ?: it else it }

    return controller.toSnapshot(context, keepLastSong).also { snapshot ->
      if (!snapshot.isFromCache) MediaPlaybackCache.save(context, snapshot)
    }
  }

  fun dispatch(context: Context, action: MediaControlAction): Boolean {
    val controller = activeController(context) ?: return false
    val controls = controller.transportControls
    return runCatching {
      when (action) {
        MediaControlAction.TogglePlayPause -> {
          if (controller.playbackState?.state.isActivelyPlaying()) controls.pause() else controls.play()
        }
        MediaControlAction.Next -> controls.skipToNext()
        MediaControlAction.Previous -> controls.skipToPrevious()
      }
    }.isSuccess
  }

  fun sessionActivity(context: Context): PendingIntent? {
    if (!NotificationListenerAccess.isGranted(context)) return null
    return activeController(context)?.sessionActivity
  }

  private fun activeController(context: Context): MediaController? {
    val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
    val listenerComponent = ComponentName(context, MediaNotificationListenerService::class.java)
    val controllers = runCatching { manager.getActiveSessions(listenerComponent) }
      .getOrElse { return null }
    val candidates = controllers.map { controller ->
      controller to controller.toCandidate()
    }
    val selected = chooseBestMediaSession(candidates.map { it.second }) ?: return null
    return candidates.firstOrNull { it.second == selected }?.first
  }

  private fun MediaController.toCandidate(): MediaSessionCandidate {
    val state = playbackState
    val metadata = metadata
    val title = metadata.firstText(
      MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
      MediaMetadata.METADATA_KEY_TITLE,
    )
    val artist = metadata.firstText(
      MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
      MediaMetadata.METADATA_KEY_ARTIST,
      MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
    )
    return MediaSessionCandidate(
      packageName = packageName,
      isPlaying = state?.state.isActivelyPlaying(),
      hasMetadata = metadata != null,
      hasSongIdentity = title.isNotBlank() && artist.isNotBlank(),
      supportsTransportControls = (state?.actions ?: 0L) != 0L,
      lastPositionUpdateTime = state?.lastPositionUpdateTime ?: 0L,
    )
  }

  private fun MediaController.toSnapshot(
    context: Context,
    keepLastSong: Boolean,
  ): MediaPlaybackSnapshot {
    val state = playbackState
    val metadata = metadata
    val title = metadata.firstText(
      MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
      MediaMetadata.METADATA_KEY_TITLE,
    )
    val artist = metadata.firstText(
      MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
      MediaMetadata.METADATA_KEY_ARTIST,
      MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
      MediaMetadata.METADATA_KEY_ALBUM,
    )
    val actions = state?.actions ?: 0L
    val cached = if (keepLastSong) MediaPlaybackCache.read(context) else null
    if (title.isBlank() || artist.isBlank()) {
      return cached?.copy(
        isPlaying = state?.state.isActivelyPlaying(),
        canPlayPause = actions.hasAny(
          PlaybackState.ACTION_PLAY,
          PlaybackState.ACTION_PAUSE,
          PlaybackState.ACTION_PLAY_PAUSE,
        ),
        canSkipNext = actions.hasAny(PlaybackState.ACTION_SKIP_TO_NEXT),
        canSkipPrevious = actions.hasAny(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
        isFromCache = true,
      ) ?: MediaPlaybackSnapshot(
        availability = MediaPlaybackAvailability.NoSession,
        title = "Nessuna traccia visibile",
        artist = "Avvia una canzone da un'app musicale.",
        sourceLabel = appLabel(context, packageName),
      )
    }

    return MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.Active,
      title = title,
      artist = artist,
      sourceLabel = appLabel(context, packageName),
      packageName = packageName,
      isPlaying = state?.state.isActivelyPlaying(),
      canPlayPause = actions.hasAny(
        PlaybackState.ACTION_PLAY,
        PlaybackState.ACTION_PAUSE,
        PlaybackState.ACTION_PLAY_PAUSE,
      ),
      canSkipNext = actions.hasAny(PlaybackState.ACTION_SKIP_TO_NEXT),
      canSkipPrevious = actions.hasAny(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
      artwork = metadata.artwork()?.scaledForWidget() ?: cached?.artwork,
      positionMs = state?.currentPositionMs() ?: 0L,
      durationMs = metadata.durationMs(),
      lastPositionUpdateTimeMs = state?.lastPositionUpdateTime ?: 0L,
      playbackSpeed = state?.playbackSpeed ?: 0f,
    )
  }

  private fun Int?.isActivelyPlaying(): Boolean {
    return this == PlaybackState.STATE_PLAYING || this == PlaybackState.STATE_BUFFERING
  }

  private fun Long.hasAny(vararg expectedActions: Long): Boolean {
    return expectedActions.any { action -> this and action != 0L }
  }

  private fun PlaybackState.currentPositionMs(): Long {
    val basePosition = position.coerceAtLeast(0L)
    if (!state.isActivelyPlaying() || playbackSpeed <= 0f || lastPositionUpdateTime <= 0L) {
      return basePosition
    }
    val elapsed = (SystemClock.elapsedRealtime() - lastPositionUpdateTime).coerceAtLeast(0L)
    return (basePosition + elapsed * playbackSpeed).toLong().coerceAtLeast(0L)
  }

  private fun MediaMetadata?.firstText(vararg keys: String): String {
    if (this == null) return ""
    return keys.firstNotNullOfOrNull { key ->
      getText(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }.orEmpty()
  }

  private fun MediaMetadata?.artwork(): Bitmap? {
    if (this == null) return null
    return getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
      ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
      ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
  }

  private fun MediaMetadata?.durationMs(): Long {
    if (this == null) return 0L
    return getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
  }

  private fun Bitmap.scaledForWidget(): Bitmap {
    val maxSide = 256
    val biggestSide = maxOf(width, height)
    if (biggestSide <= maxSide) return this
    val scale = maxSide.toFloat() / biggestSide.toFloat()
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
  }

  private fun appLabel(context: Context, packageName: String): String {
    return runCatching {
      val packageManager = context.packageManager
      val info = packageManager.getApplicationInfo(packageName, 0)
      packageManager.getApplicationLabel(info).toString()
    }.getOrElse {
      when (packageName) {
        MediaPackages.Spotify -> "Spotify"
        MediaPackages.YouTubeMusic -> "YouTube Music"
        MediaPackages.AppleMusic -> "Apple Music"
        MediaPackages.SamsungMusic -> "Samsung Music"
        MediaPackages.AmazonMusic -> "Amazon Music"
        MediaPackages.Deezer -> "Deezer"
        MediaPackages.Tidal -> "Tidal"
        MediaPackages.SoundCloud -> "SoundCloud"
        MediaPackages.VLC -> "VLC"
        else -> "Media"
      }
    }
  }
}
