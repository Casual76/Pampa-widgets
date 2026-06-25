package com.pampa.widgets.widget.media

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.pampa.widgets.MainActivity
import com.pampa.widgets.R
import com.pampa.widgets.core.media.MediaControlAction
import com.pampa.widgets.core.media.MediaPackages
import com.pampa.widgets.core.media.MediaPlaybackAvailability
import com.pampa.widgets.core.media.MediaPlaybackSnapshot
import com.pampa.widgets.core.media.MediaSessionReader
import com.pampa.widgets.core.media.NotificationListenerAccess
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.AppSettingsSnapshotReader
import com.pampa.widgets.core.settings.MediaWidgetArtworkSize
import com.pampa.widgets.core.settings.MediaWidgetTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Background render size (px) and corner radius for the widget card. */
private const val CompanionBG_W = 640
private const val CompanionBG_H = 470
private const val CompanionBG_RADIUS = 56f

/**
 * Builds the RemoteViews for the Media Controls widget and drives the press feedback
 * animations.
 *
 * Visual language: Samsung-like album-tinted glass. Artwork is shown only in the square
 * cover frame; its palette is used as a flat tint, never as the card background image.
 */
object MediaWidgetUpdater {
  private val mainHandler = Handler(Looper.getMainLooper())

  private const val ANIM_FRAME_MS = 48L
  private const val PLAY_PAUSE_DURATION_MS = 180L
  private const val SKIP_DURATION_MS = 150L

  private var activeAnimAction: MediaControlAction? = null
  private var animSnapshot: MediaPlaybackSnapshot? = null
  private var animStartElapsed: Long = 0L
  // Pre-computed once per run so feedback frames stay cheap.
  private var animCachedStyle: MediaWidgetStyle? = null
  private var animDonePosted = false

  private fun runAnimationStep(context: Context) {
    val action = activeAnimAction ?: return
    val snapshot = animSnapshot ?: return
    val settings = AppSettingsSnapshotReader.readBlocking(context)
    val style = animCachedStyle ?: MediaWidgetStyle.from(context, snapshot.artwork, settings).also {
      animCachedStyle = it
    }

    val now = SystemClock.elapsedRealtime()
    val elapsed = (now - animStartElapsed).coerceAtLeast(0L)
    val duration = if (action == MediaControlAction.TogglePlayPause) PLAY_PAUSE_DURATION_MS else SKIP_DURATION_MS
    val progress = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    val appWidgetManager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, MediaWidgetProvider::class.java)
    val widgetIds = appWidgetManager.getAppWidgetIds(component)

    if (widgetIds.isNotEmpty()) {
      val views = buildRemoteViews(context, snapshot, settings, style, action, progress)
      widgetIds.forEach { widgetId ->
        appWidgetManager.updateAppWidget(widgetId, views)
      }
    }

    if (progress >= 1f) {
      activeAnimAction = null
      animSnapshot = null
      animCachedStyle = null
      animDonePosted = false
      updateAll(context)
    } else {
      mainHandler.postDelayed({ runAnimationStep(context) }, ANIM_FRAME_MS)
    }
  }

  fun updateAll(context: Context) {
    val appContext = context.applicationContext
    val appWidgetManager = AppWidgetManager.getInstance(appContext)
    val component = ComponentName(appContext, MediaWidgetProvider::class.java)
    val widgetIds = appWidgetManager.getAppWidgetIds(component)
    update(appContext, appWidgetManager, widgetIds)
  }

  fun update(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    if (appWidgetIds.isEmpty()) return
    val settings = AppSettingsSnapshotReader.readBlocking(context)
    val snapshot = MediaSessionReader.readSnapshot(
      context = context,
      keepLastSong = settings.mediaWidgetKeepLastSong,
    )
    updateWithSnapshot(context, appWidgetManager, appWidgetIds, snapshot, settings)
  }

  fun feedbackSnapshot(context: Context, action: MediaControlAction): MediaPlaybackSnapshot? {
    val settings = AppSettingsSnapshotReader.readBlocking(context.applicationContext)
    if (!settings.mediaWidgetInstantControls && !settings.mediaWidgetAnimatedFeedback) return null
    val snapshot = MediaSessionReader.readSnapshot(
      context = context.applicationContext,
      keepLastSong = settings.mediaWidgetKeepLastSong,
    )
    if (snapshot.availability != MediaPlaybackAvailability.Active) return null
    return if (settings.mediaWidgetInstantControls && action == MediaControlAction.TogglePlayPause) {
      snapshot.copy(isPlaying = !snapshot.isPlaying)
    } else {
      snapshot
    }
  }

  fun afterMediaControl(
    context: Context,
    action: MediaControlAction,
    feedbackSnapshot: MediaPlaybackSnapshot? = null,
  ) {
    val appContext = context.applicationContext
    val settings = AppSettingsSnapshotReader.readBlocking(appContext)

    if (settings.mediaWidgetAnimatedFeedback) {
      animSnapshot = feedbackSnapshot ?: MediaSessionReader.readSnapshot(
        context = appContext,
        keepLastSong = settings.mediaWidgetKeepLastSong,
      )
      activeAnimAction = action
      animStartElapsed = SystemClock.elapsedRealtime()
      animCachedStyle = null
      animDonePosted = false
      runAnimationStep(appContext)
    } else {
      if (feedbackSnapshot != null && settings.mediaWidgetInstantControls) {
        updateAllWithSnapshot(
          context = appContext,
          snapshot = feedbackSnapshot,
          settings = settings,
        )
      }
    }

    // Re-sync with the real session state after the media app has had time to react.
    val delays = when (action) {
      MediaControlAction.TogglePlayPause -> longArrayOf(360L, 900L)
      MediaControlAction.Next,
      MediaControlAction.Previous -> longArrayOf(420L, 900L, 1500L)
    }
    delays.forEach { delayMs ->
      mainHandler.postDelayed({ updateAll(appContext) }, delayMs)
    }
  }

  private fun updateAllWithSnapshot(
    context: Context,
    snapshot: MediaPlaybackSnapshot,
    settings: AppSettings,
    feedbackAction: MediaControlAction? = null,
  ) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, MediaWidgetProvider::class.java)
    val widgetIds = appWidgetManager.getAppWidgetIds(component)
    updateWithSnapshot(context, appWidgetManager, widgetIds, snapshot, settings, feedbackAction)
  }

  private fun updateWithSnapshot(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
    snapshot: MediaPlaybackSnapshot,
    settings: AppSettings,
    feedbackAction: MediaControlAction? = null,
  ) {
    if (appWidgetIds.isEmpty()) return
    val style = MediaWidgetStyle.from(context, snapshot.artwork, settings)
    val views = buildRemoteViews(context, snapshot, settings, style, feedbackAction, 0f)
    appWidgetIds.forEach { widgetId ->
      appWidgetManager.updateAppWidget(widgetId, views)
    }
  }

  private fun buildRemoteViews(
    context: Context,
    snapshot: MediaPlaybackSnapshot,
    settings: AppSettings,
    style: MediaWidgetStyle,
    feedbackAction: MediaControlAction?,
    animProgress: Float,
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_media_controls)

    views.setImageViewBitmap(R.id.media_widget_background, style.background)
    views.setTextViewText(R.id.media_widget_source, snapshot.sourceLabel)
    views.setTextViewText(R.id.media_widget_title, snapshot.title)
    views.setTextViewText(R.id.media_widget_artist, snapshot.artist)
    views.setTextColor(R.id.media_widget_source, style.primaryTextColor)
    views.setTextColor(R.id.media_widget_title, style.primaryTextColor)
    views.setTextColor(R.id.media_widget_artist, style.secondaryTextColor)
    views.setTextColor(R.id.media_widget_permission, style.secondaryTextColor)
    views.setColorStateList(
      R.id.media_widget_source,
      "setBackgroundTintList",
      ColorStateList.valueOf(style.sourcePillColor),
    )
    views.setColorStateList(
      R.id.media_widget_artwork_frame,
      "setBackgroundTintList",
      ColorStateList.valueOf(style.artworkFrameColor),
    )
    views.setViewVisibility(
      R.id.media_widget_source,
      if (settings.mediaWidgetShowSource) View.VISIBLE else View.GONE,
    )
    views.setViewVisibility(
      R.id.media_widget_artist,
      if (settings.mediaWidgetShowArtist) View.VISIBLE else View.GONE,
    )
    views.setViewVisibility(
      R.id.media_widget_permission,
      if (snapshot.availability == MediaPlaybackAvailability.PermissionRequired) View.VISIBLE else View.GONE,
    )

    applyArtwork(context, views, snapshot, settings)
    applyControls(context, views, snapshot, style, settings, feedbackAction, animProgress)
    applyProgress(views, snapshot, style)

    val playIntent = if (snapshot.availability == MediaPlaybackAvailability.PermissionRequired) {
      settingsPendingIntent(context)
    } else {
      broadcastPendingIntent(context, MediaWidgetProvider.ActionTogglePlayPause, 1)
    }
    views.setOnClickPendingIntent(R.id.media_widget_play_pause, playIntent)
    views.setOnClickPendingIntent(R.id.media_widget_permission, settingsPendingIntent(context))
    val openMediaIntent = openMediaAppPendingIntent(context, snapshot)
    views.setOnClickPendingIntent(R.id.media_widget_root, openMediaIntent)
    views.setOnClickPendingIntent(R.id.media_widget_track_row, openMediaIntent)
    views.setOnClickPendingIntent(R.id.media_widget_artwork_frame, openMediaIntent)
    views.setOnClickPendingIntent(R.id.media_widget_text_stack, openMediaIntent)
    views.setOnClickPendingIntent(
      R.id.media_widget_previous,
      broadcastPendingIntent(context, MediaWidgetProvider.ActionPrevious, 2),
    )
    views.setOnClickPendingIntent(
      R.id.media_widget_next,
      broadcastPendingIntent(context, MediaWidgetProvider.ActionNext, 3),
    )

    return views
  }

  private fun applyArtwork(
    context: Context,
    views: RemoteViews,
    snapshot: MediaPlaybackSnapshot,
    settings: AppSettings,
  ) {
    val artworkDp = when (settings.mediaWidgetArtworkSize) {
      MediaWidgetArtworkSize.Compact -> 88f
      MediaWidgetArtworkSize.Balanced -> 100f
      MediaWidgetArtworkSize.Large -> 112f
    }
    views.setViewLayoutWidth(R.id.media_widget_artwork_frame, artworkDp, TypedValue.COMPLEX_UNIT_DIP)
    views.setViewLayoutHeight(R.id.media_widget_artwork_frame, artworkDp, TypedValue.COMPLEX_UNIT_DIP)

    if (snapshot.artwork != null) {
      views.setViewPadding(R.id.media_widget_artwork, 0, 0, 0, 0)
      views.setImageViewBitmap(R.id.media_widget_artwork, snapshot.artwork.roundedSquare())
    } else {
      val padding = context.dp(18)
      views.setViewPadding(R.id.media_widget_artwork, padding, padding, padding, padding)
      views.setImageViewResource(R.id.media_widget_artwork, R.drawable.ic_widget_music_note)
    }
  }

  private fun applyControls(
    context: Context,
    views: RemoteViews,
    snapshot: MediaPlaybackSnapshot,
    style: MediaWidgetStyle,
    settings: AppSettings,
    action: MediaControlAction?,
    progress: Float,
  ) {
    val animatingPlayPause = action == MediaControlAction.TogglePlayPause
    val animatingNext = action == MediaControlAction.Next
    val animatingPrev = action == MediaControlAction.Previous

    // When instant controls are on we already flipped isPlaying in the feedback snapshot,
    // so "target" is what the glyph should settle on.
    val targetPlaying = if (settings.mediaWidgetInstantControls) snapshot.isPlaying else snapshot.isPlaying

    views.setViewPadding(R.id.media_widget_play_pause, 0, 0, 0, 0)
    views.setViewPadding(R.id.media_widget_previous, 0, 0, 0, 0)
    views.setViewPadding(R.id.media_widget_next, 0, 0, 0, 0)

    views.setColorStateList(
      R.id.media_widget_previous,
      "setBackgroundTintList",
      ColorStateList.valueOf(style.controlSurfaceColor),
    )
    views.setColorStateList(
      R.id.media_widget_next,
      "setBackgroundTintList",
      ColorStateList.valueOf(style.controlSurfaceColor),
    )
    views.setColorStateList(
      R.id.media_widget_play_pause,
      "setBackgroundTintList",
      ColorStateList.valueOf(style.playSurfaceColor),
    )

    val playPauseBitmap = drawGlyph(
      context = context,
      drawableId = if (targetPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
      canvasSizeDp = 54,
      iconSizeDp = 28,
      color = style.playIconColor,
      scale = if (animatingPlayPause) gentlePressScale(progress) else 1f,
    )
    views.setImageViewBitmap(R.id.media_widget_play_pause, playPauseBitmap)

    val nextBitmap = drawGlyph(
      context = context,
      drawableId = R.drawable.ic_widget_next,
      canvasSizeDp = 42,
      iconSizeDp = 23,
      color = style.controlIconColor,
      scale = if (animatingNext) gentlePressScale(progress) else 1f,
    )
    views.setImageViewBitmap(R.id.media_widget_next, nextBitmap)

    val prevBitmap = drawGlyph(
      context = context,
      drawableId = R.drawable.ic_widget_previous,
      canvasSizeDp = 42,
      iconSizeDp = 23,
      color = style.controlIconColor,
      scale = if (animatingPrev) gentlePressScale(progress) else 1f,
    )
    views.setImageViewBitmap(R.id.media_widget_previous, prevBitmap)

    views.setBoolean(R.id.media_widget_previous, "setEnabled", snapshot.canSkipPrevious)
    views.setBoolean(R.id.media_widget_next, "setEnabled", snapshot.canSkipNext)
    views.setBoolean(R.id.media_widget_play_pause, "setEnabled", snapshot.canPlayPause)
    views.setFloat(R.id.media_widget_previous, "setAlpha", if (snapshot.canSkipPrevious) 1f else 0.35f)
    views.setFloat(R.id.media_widget_next, "setAlpha", if (snapshot.canSkipNext) 1f else 0.35f)
    views.setFloat(R.id.media_widget_play_pause, "setAlpha", if (snapshot.canPlayPause) 1f else 0.55f)
  }

  private fun applyProgress(
    views: RemoteViews,
    snapshot: MediaPlaybackSnapshot,
    style: MediaWidgetStyle,
  ) {
    val progress = snapshot.progressPermille()
    views.setViewVisibility(
      R.id.media_widget_progress,
      if (progress > 0) View.VISIBLE else View.INVISIBLE,
    )
    views.setColorStateList(
      R.id.media_widget_progress,
      "setProgressTintList",
      ColorStateList.valueOf(style.progressColor),
    )
    views.setColorStateList(
      R.id.media_widget_progress,
      "setProgressBackgroundTintList",
      ColorStateList.valueOf(style.progressTrackColor),
    )
    views.setProgressBar(R.id.media_widget_progress, 1000, progress, false)
  }

  private fun broadcastPendingIntent(
    context: Context,
    action: String,
    requestCode: Int,
  ): PendingIntent {
    val intent = Intent(context, MediaWidgetProvider::class.java).setAction(action)
    return PendingIntent.getBroadcast(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun settingsPendingIntent(context: Context): PendingIntent {
    return PendingIntent.getActivity(
      context,
      20,
      NotificationListenerAccess.settingsIntent(),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private fun openMediaAppPendingIntent(
    context: Context,
    snapshot: MediaPlaybackSnapshot,
  ): PendingIntent {
    MediaSessionReader.sessionActivity(context)?.let { return it }
    val launchIntent = snapshot.launchPackageName()
      .takeIf { it.isNotBlank() }
      ?.let { packageName -> context.packageManager.getLaunchIntentForPackage(packageName) }
      ?: Intent(context, MainActivity::class.java)
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return PendingIntent.getActivity(
      context,
      30,
      launchIntent,
      PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}

private fun MediaPlaybackSnapshot.launchPackageName(): String {
  if (packageName.isNotBlank()) return packageName
  return when (sourceLabel.trim().lowercase()) {
    "spotify" -> MediaPackages.Spotify
    "youtube music", "yt music" -> MediaPackages.YouTubeMusic
    "apple music" -> MediaPackages.AppleMusic
    "samsung music" -> MediaPackages.SamsungMusic
    "amazon music" -> MediaPackages.AmazonMusic
    "deezer" -> MediaPackages.Deezer
    "tidal" -> MediaPackages.Tidal
    "soundcloud" -> MediaPackages.SoundCloud
    "vlc" -> MediaPackages.VLC
    else -> ""
  }
}

private data class MediaWidgetStyle(
  val background: Bitmap,
  val primaryTextColor: Int,
  val secondaryTextColor: Int,
  val controlIconColor: Int,
  val playIconColor: Int,
  val controlSurfaceColor: Int,
  val playSurfaceColor: Int,
  val sourcePillColor: Int,
  val artworkFrameColor: Int,
  val progressColor: Int,
  val progressTrackColor: Int,
) {
  companion object {
    fun from(
      context: Context,
      artwork: Bitmap?,
      settings: AppSettings,
    ): MediaWidgetStyle {
      val palette = extractPalette(artwork)
      val accent = palette.vibrant
      val darkSurface = when (settings.mediaWidgetTheme) {
        MediaWidgetTheme.DarkGlass -> true
        MediaWidgetTheme.AlbumColor -> accent.luminance() < 0.46f
        MediaWidgetTheme.SamsungGlass,
        MediaWidgetTheme.AdaptiveGlass,
        MediaWidgetTheme.LightGlass -> false
      }

      val background = createBackgroundBitmap(
        accent = accent,
        dominant = palette.dominant,
        settings = settings,
        darkSurface = darkSurface,
      )

      // Always-white text family — the Samsung / Spotify frosted-colour look.
      val primary = if (darkSurface) Color.WHITE else Color.rgb(22, 26, 24)
      val secondary = if (darkSurface) {
        Color.argb(220, 255, 255, 255)
      } else {
        Color.argb(205, 52, 58, 55)
      }
      val controlIcon = if (darkSurface) Color.argb(238, 255, 255, 255) else Color.rgb(34, 38, 36)
      val controlSurface = if (darkSurface) Color.argb(40, 255, 255, 255) else Color.argb(88, 255, 255, 255)
      val playSurface = if (darkSurface) Color.argb(238, 246, 250, 246) else accent.lighten(0.42f).withAlpha(238)
      val playIcon = if (darkSurface) Color.rgb(15, 20, 18) else Color.rgb(17, 24, 21)
      val sourcePill = if (darkSurface) accent.lighten(0.18f).withAlpha(72) else accent.lighten(0.55f).withAlpha(128)
      val artworkFrame = if (darkSurface) Color.argb(36, 255, 255, 255) else Color.argb(82, 255, 255, 255)
      return MediaWidgetStyle(
        background = background,
        primaryTextColor = primary,
        secondaryTextColor = secondary,
        controlIconColor = controlIcon,
        playIconColor = playIcon,
        controlSurfaceColor = controlSurface,
        playSurfaceColor = playSurface,
        sourcePillColor = sourcePill,
        artworkFrameColor = artworkFrame,
        progressColor = if (darkSurface) Color.argb(206, 255, 255, 255) else Color.argb(150, 26, 30, 28),
        progressTrackColor = if (darkSurface) Color.argb(44, 255, 255, 255) else Color.argb(38, 26, 30, 28),
      )
    }

    private fun createBackgroundBitmap(
      accent: Int,
      dominant: Int,
      settings: AppSettings,
      darkSurface: Boolean,
    ): Bitmap {
      val width = CompanionBG_W
      val height = CompanionBG_H
      val radius = CompanionBG_RADIUS
      val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())

      val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val layer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val layerCanvas = Canvas(layer)

      val baseColor = when (settings.mediaWidgetTheme) {
        MediaWidgetTheme.DarkGlass -> dominant
          .darken(0.48f)
          .blendWith(Color.rgb(18, 22, 20), 0.58f)
          .withAlpha(238)
        MediaWidgetTheme.AlbumColor -> if (darkSurface) {
          dominant.darken(0.34f).withAlpha(238)
        } else {
          dominant.lighten(0.64f).withAlpha(242)
        }
        MediaWidgetTheme.AdaptiveGlass -> dominant
          .lighten(0.82f)
          .blendWith(Color.rgb(245, 247, 243), 0.68f)
          .withAlpha(242)
        MediaWidgetTheme.LightGlass -> Color.argb(244, 255, 255, 250)
        MediaWidgetTheme.SamsungGlass -> dominant
          .lighten(0.76f)
          .blendWith(Color.rgb(246, 243, 236), 0.58f)
          .withAlpha(242)
      }
      layerCanvas.drawRoundRect(bounds, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor })

      //    vagamente" — dreamy, vaguely-coloured, like Samsung system widgets.
      val glassColor = when (settings.mediaWidgetTheme) {
        MediaWidgetTheme.DarkGlass -> Color.argb(44, 0, 0, 0)
        MediaWidgetTheme.AlbumColor -> if (darkSurface) {
          accent.darken(0.28f).withAlpha(82)
        } else {
          accent.lighten(0.58f).withAlpha(72)
        }
        MediaWidgetTheme.AdaptiveGlass -> Color.argb(52, 255, 255, 255)
        MediaWidgetTheme.LightGlass -> Color.argb(58, 255, 255, 255)
        MediaWidgetTheme.SamsungGlass -> Color.argb(46, 255, 255, 255)
      }
      layerCanvas.drawRoundRect(bounds, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = glassColor })

      val tintAlpha = when (settings.mediaWidgetTheme) {
        MediaWidgetTheme.AlbumColor -> 62
        MediaWidgetTheme.DarkGlass -> 38
        MediaWidgetTheme.AdaptiveGlass -> 34
        MediaWidgetTheme.LightGlass -> 28
        MediaWidgetTheme.SamsungGlass -> 44
      }
      layerCanvas.drawRoundRect(bounds, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent.withAlpha(tintAlpha) })

      val softVeil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (darkSurface) Color.argb(26, 0, 0, 0) else Color.argb(34, 255, 255, 255)
      }
      layerCanvas.drawRoundRect(bounds, radius, radius, softVeil)

      // Mask everything into the rounded card.
      val canvas = Canvas(output)
      val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
      canvas.drawRoundRect(bounds, radius, radius, maskPaint)
      val contentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
      }
      canvas.drawBitmap(layer, 0f, 0f, contentPaint)
      contentPaint.xfermode = null

      // 1px inner highlight stroke for the premium glass rim.
      val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        color = Color.argb(70, 255, 255, 255)
      }
      canvas.drawRoundRect(bounds.insetBy(0.7f), radius, radius, rimPaint)
      return output
    }
  }
}

// ---------- palette ----------

private data class CoverPalette(val dominant: Int, val vibrant: Int)

/**
 * Extracts a [dominant] average and a [vibrant] colour (high saturation × brightness) from
 * the artwork. The vibrant pick is what makes the background read as the cover's colour
 * rather than a muddy grey.
 */
private fun extractPalette(artwork: Bitmap?): CoverPalette {
  if (artwork == null) {
    val base = Color.rgb(58, 84, 116)
    return CoverPalette(dominant = base, vibrant = base)
  }
  val src = if (artwork.width > 96 || artwork.height > 96) {
    Bitmap.createScaledBitmap(artwork, 96, 96, true)
  } else {
    artwork
  }
  val w = src.width
  val h = src.height
  var rSum = 0L
  var gSum = 0L
  var bSum = 0L
  var count = 0L

  var accentRSum = 0.0
  var accentGSum = 0.0
  var accentBSum = 0.0
  var accentWeightSum = 0.0

  var bestScore = -1f
  var bestR = 0
  var bestG = 0
  var bestB = 0
  var bestSat = 0f
  var bestBrightness = 0f

  var y = 0
  while (y < h) {
    var x = 0
    while (x < w) {
      val c = src.getPixel(x, y)
      if (Color.alpha(c) < 160) { x += 2; continue }
      val r = Color.red(c)
      val g = Color.green(c)
      val b = Color.blue(c)
      rSum += r; gSum += g; bSum += b; count++
      val maxC = max(r, max(g, b))
      val minC = min(r, min(g, b))
      val brightness = maxC / 255f
      val sat = if (maxC == 0) 0f else (maxC - minC) / maxC.toFloat()
      val balance = 1f - (abs(brightness - 0.62f) / 0.62f).coerceIn(0f, 1f)
      val score = sat * (0.45f + 0.55f * brightness) * (0.65f + 0.35f * balance)
      if (score > bestScore) {
        bestScore = score
        bestR = r; bestG = g; bestB = b
        bestSat = sat
        bestBrightness = brightness
      }
      if (sat >= 0.16f && brightness in 0.14f..0.94f) {
        val weight = (sat * sat) * (0.50f + 0.50f * brightness) * (0.70f + 0.30f * balance)
        accentRSum += r * weight
        accentGSum += g * weight
        accentBSum += b * weight
        accentWeightSum += weight
      }
      x += 2
    }
    y += 2
  }

  val dominant = if (count == 0L) {
    Color.rgb(58, 84, 116)
  } else {
    Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
  }

  val vibrant = if (accentWeightSum >= 1.0) {
    Color.rgb(
      (accentRSum / accentWeightSum).toInt().coerceIn(0, 255),
      (accentGSum / accentWeightSum).toInt().coerceIn(0, 255),
      (accentBSum / accentWeightSum).toInt().coerceIn(0, 255),
    ).enrich(0.16f)
  } else if (bestSat >= 0.14f && bestBrightness >= 0.12f) {
    Color.rgb(bestR, bestG, bestB).enrich(0.12f)
  } else {
    dominant.enrich(0.45f)
  }
  return CoverPalette(dominant = dominant, vibrant = vibrant)
}

private fun gentlePressScale(progress: Float): Float =
  1f - 0.10f * sin((Math.PI * progress.coerceIn(0f, 1f)).toFloat())

// ---------- icon drawing ----------

private fun drawGlyph(
  context: Context,
  drawableId: Int,
  canvasSizeDp: Int,
  iconSizeDp: Int,
  color: Int,
  scale: Float = 1.0f,
  alpha: Float = 1.0f,
): Bitmap {
  val canvasPx = context.dp(canvasSizeDp)
  val iconPx = context.dp(iconSizeDp)
  val bitmap = Bitmap.createBitmap(canvasPx, canvasPx, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  drawGlyphOnCanvas(
    context = context,
    canvas = canvas,
    drawableId = drawableId,
    center = canvasPx / 2f,
    iconPx = iconPx,
    color = color,
    scale = scale,
    alpha = alpha,
  )
  return bitmap
}

private fun drawGlyphOnCanvas(
  context: Context,
  canvas: Canvas,
  drawableId: Int,
  center: Float,
  iconPx: Int,
  color: Int,
  scale: Float,
  alpha: Float,
) {
  val drawable = context.getDrawable(drawableId) ?: return
  drawable.mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN)
  drawable.alpha = (alpha * 255).toInt().coerceIn(0, 255)
  canvas.save()
  canvas.translate(center, center)
  canvas.scale(scale, scale)
  val halfIcon = iconPx / 2
  drawable.setBounds(-halfIcon, -halfIcon, halfIcon, halfIcon)
  drawable.draw(canvas)
  canvas.restore()
}

// ---------- bitmap helpers ----------

private fun MediaPlaybackSnapshot.progressPermille(): Int {
  if (durationMs <= 0L) return 0
  return ((positionMs.coerceIn(0L, durationMs) * 1000L) / durationMs)
    .toInt()
    .coerceIn(0, 1000)
}

private fun Bitmap.roundedSquare(): Bitmap {
  val size = minOf(width, height)
  val left = (width - size) / 2
  val top = (height - size) / 2
  val output = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(output)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  val rect = Rect(0, 0, output.width, output.height)
  val source = Rect(left, top, left + size, top + size)
  val rounded = RectF(rect)
  canvas.drawRoundRect(rounded, 42f, 42f, paint.apply { color = Color.WHITE })
  paint.shader = null
  paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
  canvas.drawBitmap(this, source, rect, paint)
  paint.xfermode = null
  return output
}

private fun Int.luminance(): Float =
  (0.299f * Color.red(this) + 0.587f * Color.green(this) + 0.114f * Color.blue(this)) / 255f

private fun Int.lighten(amount: Float): Int = Color.rgb(
  (Color.red(this) + (255 - Color.red(this)) * amount).toInt().coerceIn(0, 255),
  (Color.green(this) + (255 - Color.green(this)) * amount).toInt().coerceIn(0, 255),
  (Color.blue(this) + (255 - Color.blue(this)) * amount).toInt().coerceIn(0, 255),
)

private fun Int.darken(amount: Float): Int = Color.rgb(
  (Color.red(this) * (1f - amount)).toInt().coerceIn(0, 255),
  (Color.green(this) * (1f - amount)).toInt().coerceIn(0, 255),
  (Color.blue(this) * (1f - amount)).toInt().coerceIn(0, 255),
)

private fun Int.blendWith(other: Int, amount: Float): Int {
  val t = amount.coerceIn(0f, 1f)
  fun channel(a: Int, b: Int) = (a * (1f - t) + b * t).toInt().coerceIn(0, 255)
  return Color.rgb(
    channel(Color.red(this), Color.red(other)),
    channel(Color.green(this), Color.green(other)),
    channel(Color.blue(this), Color.blue(other)),
  )
}

/** Pushes a greyish colour toward a richer version of itself by stretching RGB away from grey. */
private fun Int.enrich(amount: Float): Int {
  val r = Color.red(this)
  val g = Color.green(this)
  val b = Color.blue(this)
  val grey = (r + g + b) / 3
  fun channel(c: Int) = (grey + (c - grey) * (1f + amount)).toInt().coerceIn(0, 255)
  return Color.rgb(channel(r), channel(g), channel(b))
}

private fun Int.withAlpha(alpha: Int): Int =
  Color.argb(alpha.coerceIn(0, 255), Color.red(this), Color.green(this), Color.blue(this))

private fun RectF.insetBy(amount: Float): RectF =
  RectF(left + amount, top + amount, right - amount, bottom - amount)

private fun Context.dp(value: Int): Int =
  (value * resources.displayMetrics.density).toInt()
