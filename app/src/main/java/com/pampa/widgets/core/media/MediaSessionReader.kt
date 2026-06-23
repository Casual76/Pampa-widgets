package com.pampa.widgets.core.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.pampa.widgets.widget.media.MediaNotificationListenerService

object MediaSessionReader {
  fun readSnapshot(context: Context): MediaPlaybackSnapshot {
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
      artist = "Apri Spotify o YouTube Music e premi play.",
      sourceLabel = "In attesa",
    )

    return controller.toSnapshot(context)
  }

  fun dispatch(context: Context, action: MediaControlAction): Boolean {
    val controls = activeController(context)?.transportControls ?: return false
    return runCatching {
      when (action) {
        MediaControlAction.TogglePlayPause -> {
          val snapshot = readSnapshot(context)
          if (snapshot.isPlaying) controls.pause() else controls.play()
        }
        MediaControlAction.Next -> controls.skipToNext()
        MediaControlAction.Previous -> controls.skipToPrevious()
      }
    }.isSuccess
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
    return MediaSessionCandidate(
      packageName = packageName,
      isPlaying = state?.state.isActivelyPlaying(),
      hasMetadata = metadata != null,
      supportsTransportControls = (state?.actions ?: 0L) != 0L,
      lastPositionUpdateTime = state?.lastPositionUpdateTime ?: 0L,
    )
  }

  private fun MediaController.toSnapshot(context: Context): MediaPlaybackSnapshot {
    val state = playbackState
    val metadata = metadata
    val title = metadata.firstText(
      MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
      MediaMetadata.METADATA_KEY_TITLE,
    ).ifBlank { appLabel(context, packageName) }
    val artist = metadata.firstText(
      MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
      MediaMetadata.METADATA_KEY_ARTIST,
      MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
      MediaMetadata.METADATA_KEY_ALBUM,
    ).ifBlank { "Sorgente multimediale attiva" }
    val actions = state?.actions ?: 0L
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
      artwork = metadata.artwork()?.scaledForWidget(),
    )
  }

  private fun Int?.isActivelyPlaying(): Boolean {
    return this == PlaybackState.STATE_PLAYING || this == PlaybackState.STATE_BUFFERING
  }

  private fun Long.hasAny(vararg expectedActions: Long): Boolean {
    return expectedActions.any { action -> this and action != 0L }
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
        else -> "Media"
      }
    }
  }
}
