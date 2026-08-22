package com.pampa.widgets.core.media

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import com.pampa.widgets.widget.media.MediaNotificationListenerService
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val ArtworkMaxSidePx = 384
private const val ArtworkDownloadLimitBytes = 6 * 1024 * 1024
private const val ArtworkConnectTimeoutMs = 2_000
private const val ArtworkReadTimeoutMs = 3_000

object MediaSessionReader {
  /** Reads media and artwork away from the main thread. */
  suspend fun readSnapshot(
    context: Context,
    keepLastSong: Boolean = true,
  ): MediaPlaybackSnapshot = withContext(Dispatchers.IO) {
    readSnapshotInternal(context.applicationContext, keepLastSong)
  }

  /** Only for synchronous platform callbacks that cannot be made suspending. */
  fun readSnapshotBlocking(
    context: Context,
    keepLastSong: Boolean = true,
  ): MediaPlaybackSnapshot = runBlocking(Dispatchers.IO) {
    readSnapshotInternal(context.applicationContext, keepLastSong)
  }

  private fun readSnapshotInternal(
    context: Context,
    keepLastSong: Boolean,
  ): MediaPlaybackSnapshot {
    if (!NotificationListenerAccess.isGranted(context)) {
      return MediaPlaybackSnapshot(
        availability = MediaPlaybackAvailability.PermissionRequired,
        title = "Accesso media richiesto",
        artist = "Abilita l'accesso notifiche per controllare Spotify.",
        sourceLabel = "Permesso richiesto",
      )
    }

    val controller = activeController(context) ?: run {
      val emptySnapshot = MediaPlaybackSnapshot(
        availability = MediaPlaybackAvailability.NoSession,
        title = "Nessuna riproduzione",
        artist = "Apri Spotify, Apple Music o YouTube Music.",
        sourceLabel = "In attesa",
      )
      val cachedSnapshot = if (keepLastSong) MediaPlaybackCache.read(context) else null
      if (cachedSnapshot != null) return cachedSnapshot

      // Keep the root tap actionable without leaking the hidden song metadata back into the UI.
      val rememberedTarget = MediaPlaybackCache.readRememberedTarget(context)
      return emptySnapshot.copy(packageName = rememberedTarget?.packageName.orEmpty())
    }

    return controller.toSnapshot(context, keepLastSong)
  }

  fun dispatch(context: Context, action: MediaControlAction): Boolean {
    if (!NotificationListenerAccess.isGranted(context)) return false
    val controller = activeController(context)
    val dispatchedToSession = controller?.let { dispatchToController(it, action) } ?: false
    return dispatchedToSession || dispatchMediaKeyFallback(context, action)
  }

  /** Lets the notification listener observe the same controller used by the widget. */
  internal fun currentController(context: Context): MediaController? = activeController(context)

  private fun dispatchToController(controller: MediaController, action: MediaControlAction): Boolean {
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

  private fun dispatchMediaKeyFallback(context: Context, action: MediaControlAction): Boolean {
    val snapshot = readSnapshotBlocking(context, keepLastSong = true)
    if (!snapshot.canDispatch(action)) return false
    val keyCode = when (action) {
      MediaControlAction.TogglePlayPause -> if (snapshot.isPlaying) {
        KeyEvent.KEYCODE_MEDIA_PAUSE
      } else {
        KeyEvent.KEYCODE_MEDIA_PLAY
      }
      MediaControlAction.Next -> KeyEvent.KEYCODE_MEDIA_NEXT
      MediaControlAction.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
    }
    val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
    return runCatching {
      val now = SystemClock.uptimeMillis()
      audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
      audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
      true
    }.getOrDefault(false)
  }

  fun sessionActivity(context: Context): PendingIntent? {
    if (!NotificationListenerAccess.isGranted(context)) return null
    return activeController(context)?.sessionActivity
  }

  internal fun resolveArtworkForTest(context: Context, metadata: MediaMetadata): Bitmap? =
    metadata.resolveArtwork(context.applicationContext, matchingCache = null).bitmap

  private fun activeController(context: Context): MediaController? {
    val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
    val listenerComponent = ComponentName(context, MediaNotificationListenerService::class.java)
    val controllers = runCatching { manager.getActiveSessions(listenerComponent) }
      .getOrElse { return null }
    val candidates = controllers.map { controller -> controller to controller.toCandidate() }
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
      supportsTransportControls = state != null || metadata != null,
      lastPositionUpdateTime = state?.lastPositionUpdateTime ?: 0L,
    )
  }

  private fun MediaController.toSnapshot(
    context: Context,
    keepLastSong: Boolean,
  ): MediaPlaybackSnapshot {
    val state = playbackState
    val metadata = metadata
    val rawTitle = metadata.firstText(
      MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
      MediaMetadata.METADATA_KEY_TITLE,
    )
    val rawArtist = metadata.firstText(
      MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
      MediaMetadata.METADATA_KEY_ARTIST,
      MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
    )
    val album = metadata.firstText(MediaMetadata.METADATA_KEY_ALBUM)
    val mediaId = metadata.firstText(MediaMetadata.METADATA_KEY_MEDIA_ID)
    val source = appLabel(context, packageName)
    val hasCompleteIdentity = rawTitle.isNotBlank() && rawArtist.isNotBlank()
    val title = rawTitle.ifBlank { source }
    val artist = rawArtist.ifBlank { "Caricamento metadati…" }
    val actions = state?.actions ?: 0L
    val allowsFallbackTransport = isSupportedMusicPackage(packageName) &&
      (state != null || metadata != null) && actions == 0L
    val canPlayPause = actions.hasAny(
      PlaybackState.ACTION_PLAY,
      PlaybackState.ACTION_PAUSE,
      PlaybackState.ACTION_PLAY_PAUSE,
    ) || allowsFallbackTransport
    val canSkipNext = actions.hasAny(PlaybackState.ACTION_SKIP_TO_NEXT) || allowsFallbackTransport
    val canSkipPrevious = actions.hasAny(PlaybackState.ACTION_SKIP_TO_PREVIOUS) || allowsFallbackTransport
    val cached = if (keepLastSong) MediaPlaybackCache.read(context) else null
    val isPlaying = state?.let { it.state.isActivelyPlaying() } ?: cached?.isPlaying ?: false
    val identityProbe = MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.Active,
      title = title,
      artist = artist,
      album = album,
      mediaId = mediaId,
      sourceLabel = source,
      packageName = packageName,
    )
    val matchingCache = cached?.takeIf { hasCompleteIdentity && it.trackKey == identityProbe.trackKey }
    val resolvedArtwork = metadata.resolveArtwork(context, matchingCache)

    val baseSnapshot = MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.Active,
      title = title,
      artist = artist,
      album = album,
      mediaId = mediaId,
      sourceLabel = source,
      packageName = packageName,
      isPlaying = isPlaying,
      canPlayPause = canPlayPause,
      canSkipNext = canSkipNext,
      canSkipPrevious = canSkipPrevious,
      artwork = resolvedArtwork.bitmap,
      artworkUri = resolvedArtwork.uri,
      artworkKey = resolvedArtwork.key,
      positionMs = state?.currentPositionMs() ?: 0L,
      durationMs = metadata.durationMs(),
      lastPositionUpdateTimeMs = state?.lastPositionUpdateTime ?: 0L,
      playbackSpeed = state?.playbackSpeed ?: 0f,
    )
    val snapshot = if (baseSnapshot.artwork == null && matchingCache?.artwork != null) {
      baseSnapshot.copy(
        artwork = matchingCache.artwork,
        artworkKey = matchingCache.artworkKey,
      )
    } else {
      baseSnapshot
    }
    if (hasCompleteIdentity) MediaPlaybackCache.save(context, snapshot)
    return snapshot
  }

  private fun Int?.isActivelyPlaying(): Boolean =
    this == PlaybackState.STATE_PLAYING || this == PlaybackState.STATE_BUFFERING

  private fun Long.hasAny(vararg expectedActions: Long): Boolean =
    expectedActions.any { action -> this and action != 0L }

  private fun MediaPlaybackSnapshot.canDispatch(action: MediaControlAction): Boolean =
    when (action) {
      MediaControlAction.TogglePlayPause -> canPlayPause
      MediaControlAction.Next -> canSkipNext
      MediaControlAction.Previous -> canSkipPrevious
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

  private data class ResolvedArtwork(
    val bitmap: Bitmap?,
    val uri: String,
    val key: String,
  )

  private fun MediaMetadata?.resolveArtwork(
    context: Context,
    matchingCache: MediaPlaybackSnapshot?,
  ): ResolvedArtwork {
    if (this == null) return ResolvedArtwork(null, "", "")
    val uri = firstText(
      MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
      MediaMetadata.METADATA_KEY_ART_URI,
      MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
    )
    val direct = (
      getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
      )?.scaledForWidget()
    if (direct != null) {
      return ResolvedArtwork(direct, uri, "bitmap:${direct.contentSignature()}")
    }
    if (matchingCache?.artwork != null) {
      return ResolvedArtwork(
        bitmap = matchingCache.artwork,
        uri = uri.ifBlank { matchingCache.artworkUri },
        key = matchingCache.artworkKey,
      )
    }
    val loaded = uri.takeIf { it.isNotBlank() }?.let { loadArtworkUri(context, it) }
    return ResolvedArtwork(
      bitmap = loaded,
      uri = uri,
      key = loaded?.let { "uri:$uri:${it.contentSignature()}" }.orEmpty(),
    )
  }

  private fun loadArtworkUri(context: Context, uriText: String): Bitmap? = runCatching {
    val uri = Uri.parse(uriText)
    val bytes = when (uri.scheme?.lowercase()) {
      "content", "file", "android.resource" -> {
        context.contentResolver.openInputStream(uri)?.use(::readLimitedBytes)
      }
      "https" -> {
        val connection = URL(uriText).openConnection() as HttpURLConnection
        try {
          connection.connectTimeout = ArtworkConnectTimeoutMs
          connection.readTimeout = ArtworkReadTimeoutMs
          connection.instanceFollowRedirects = true
          connection.useCaches = true
          connection.inputStream.use(::readLimitedBytes)
        } finally {
          connection.disconnect()
        }
      }
      else -> null
    } ?: return@runCatching null
    decodeSampled(bytes)?.scaledForWidget()
  }.getOrNull()

  private fun readLimitedBytes(input: InputStream): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      total += read
      if (total > ArtworkDownloadLimitBytes) return null
      output.write(buffer, 0, read)
    }
    return output.toByteArray()
  }

  private fun decodeSampled(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > ArtworkMaxSidePx * 2 ||
      bounds.outHeight / sampleSize > ArtworkMaxSidePx * 2
    ) {
      sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
      inSampleSize = sampleSize
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
  }

  private fun Bitmap.scaledForWidget(): Bitmap {
    val biggestSide = maxOf(width, height)
    if (biggestSide <= ArtworkMaxSidePx) return this
    val scale = ArtworkMaxSidePx.toFloat() / biggestSide.toFloat()
    return Bitmap.createScaledBitmap(
      this,
      (width * scale).toInt().coerceAtLeast(1),
      (height * scale).toInt().coerceAtLeast(1),
      true,
    )
  }

  private fun Bitmap.contentSignature(): String {
    var hash = -0x340d631b7bdddcdbL
    val samples = 8
    repeat(samples) { yIndex ->
      repeat(samples) { xIndex ->
        val x = ((width - 1) * xIndex / (samples - 1)).coerceAtLeast(0)
        val y = ((height - 1) * yIndex / (samples - 1)).coerceAtLeast(0)
        hash = (hash xor getPixel(x, y).toLong()) * 0x100000001b3L
      }
    }
    return "${width}x$height:${hash.toULong().toString(16)}"
  }

  private fun MediaMetadata?.durationMs(): Long =
    this?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L

  private fun appLabel(context: Context, packageName: String): String = runCatching {
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
