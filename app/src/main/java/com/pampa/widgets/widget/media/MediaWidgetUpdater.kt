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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
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

/** Background render bounds used for launcher-size-specific bitmaps. */
private const val BackgroundMinPx = 420
private const val BackgroundMaxPx = 1400
private const val CommandSettleDelayMs = 110L
private const val CommandTimeoutMs = 1_600L
private val CommandResyncDelaysMs = longArrayOf(220L, 600L, 1_100L)

/** A command stays visually stable until the media session confirms its result. */
private data class PendingMediaCommand(
  val id: Long,
  val action: MediaControlAction,
  val baselineSnapshot: MediaPlaybackSnapshot,
  val expectedPlaying: Boolean?,
  val startedAtElapsedMs: Long,
)

/** The ephemeral state rendered while a command is in flight. */
private data class MediaWidgetInteraction(
  val action: MediaControlAction,
  val showFeedback: Boolean,
  val expectedPlaying: Boolean?,
)

/** State owned by this process so a RemoteViews [android.widget.ViewFlipper] can alternate layers. */
private data class MediaWidgetVisualState(
  val backgroundSignature: String,
  val backgroundSlot: Int,
  val artworkSignature: String,
  val artworkSlot: Int,
  val isPlaying: Boolean,
)

private data class MediaWidgetVisualPlan(
  val initialize: Boolean,
  val backgroundChanged: Boolean,
  val targetBackgroundSlot: Int,
  val artworkChanged: Boolean,
  val targetArtworkSlot: Int,
  val playbackChanged: Boolean,
) {
  val hasTransition: Boolean
    get() = backgroundChanged || artworkChanged || playbackChanged
}

/**
 * Builds the RemoteViews for the Media Controls widget and drives the press feedback
 * animations.
 *
 * Visual language: Samsung-like album-tinted glass. Artwork is shown only in the square
 * cover frame; its palette is used as a flat tint, never as the card background image.
 */
object MediaWidgetUpdater {
  private val mainHandler = Handler(Looper.getMainLooper())
  private val stateLock = Any()
  private var nextCommandId = 0L
  private var pendingCommand: PendingMediaCommand? = null
  private val visualStates = mutableMapOf<Int, MediaWidgetVisualState>()

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
    if (shouldHoldCurrentVisualState(snapshot)) return
    updateWithSnapshot(context, appWidgetManager, appWidgetIds, snapshot, settings)
  }

  fun feedbackSnapshot(context: Context, action: MediaControlAction): MediaPlaybackSnapshot? {
    val settings = AppSettingsSnapshotReader.readBlocking(context.applicationContext)
    val snapshot = MediaSessionReader.readSnapshot(
      context = context.applicationContext,
      keepLastSong = settings.mediaWidgetKeepLastSong,
    )
    return snapshot.takeIf { it.canHandle(action) }
  }

  fun afterMediaControl(
    context: Context,
    action: MediaControlAction,
    feedbackSnapshot: MediaPlaybackSnapshot? = null,
    commandDispatched: Boolean,
  ) {
    val appContext = context.applicationContext
    val settings = AppSettingsSnapshotReader.readBlocking(appContext)
    val baseline = feedbackSnapshot ?: MediaSessionReader.readSnapshot(
      context = appContext,
      keepLastSong = settings.mediaWidgetKeepLastSong,
    )
    if (!commandDispatched || !baseline.canHandle(action)) {
      updateAll(appContext)
      return
    }

    val command = synchronized(stateLock) {
      PendingMediaCommand(
        id = ++nextCommandId,
        action = action,
        baselineSnapshot = baseline,
        expectedPlaying = if (action == MediaControlAction.TogglePlayPause) !baseline.isPlaying else null,
        startedAtElapsedMs = SystemClock.elapsedRealtime(),
      ).also { pendingCommand = it }
    }
    val showFeedback = settings.mediaWidgetAnimatedFeedback || settings.mediaWidgetInstantControls
    updateAllWithSnapshot(
      context = appContext,
      snapshot = baseline,
      settings = settings,
      interaction = MediaWidgetInteraction(
        action = action,
        showFeedback = showFeedback,
        expectedPlaying = command.expectedPlaying,
      ),
    )
    scheduleCommandResolution(appContext, command)
  }

  fun onDeleted(appWidgetIds: IntArray) {
    synchronized(stateLock) {
      appWidgetIds.forEach(visualStates::remove)
    }
  }

  private fun shouldHoldCurrentVisualState(snapshot: MediaPlaybackSnapshot): Boolean {
    val command = synchronized(stateLock) { pendingCommand } ?: return false
    val elapsed = SystemClock.elapsedRealtime() - command.startedAtElapsedMs
    val confirmed = snapshot.confirms(command)
    return when {
      confirmed || snapshot.availability == MediaPlaybackAvailability.PermissionRequired || elapsed >= CommandTimeoutMs -> {
        clearPendingCommand(command.id)
        false
      }
      else -> true
    }
  }

  private fun scheduleCommandResolution(context: Context, command: PendingMediaCommand) {
    mainHandler.postDelayed({ settlePressVisual(context, command.id) }, CommandSettleDelayMs)
    CommandResyncDelaysMs.forEach { delayMs ->
      mainHandler.postDelayed({
        if (isCommandPending(command.id)) updateAll(context)
      }, delayMs)
    }
    mainHandler.postDelayed({
      if (clearPendingCommand(command.id)) updateAll(context)
    }, CommandTimeoutMs)
  }

  private fun settlePressVisual(context: Context, commandId: Long) {
    val command = synchronized(stateLock) { pendingCommand }?.takeIf { it.id == commandId } ?: return
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, MediaWidgetProvider::class.java)
    val widgetIds = appWidgetManager.getAppWidgetIds(component)
    widgetIds.forEach { widgetId ->
      val partialViews = RemoteViews(context.packageName, R.layout.widget_media_controls)
      when (command.action) {
        MediaControlAction.TogglePlayPause -> partialViews.setScale(R.id.media_widget_play_pause_container, 1f)
        MediaControlAction.Next -> partialViews.setScale(R.id.media_widget_next, 1f)
        MediaControlAction.Previous -> partialViews.setScale(R.id.media_widget_previous, 1f)
      }
      appWidgetManager.partiallyUpdateAppWidget(widgetId, partialViews)
    }
  }

  private fun isCommandPending(commandId: Long): Boolean =
    synchronized(stateLock) { pendingCommand?.id == commandId }

  private fun clearPendingCommand(commandId: Long): Boolean = synchronized(stateLock) {
    if (pendingCommand?.id != commandId) return@synchronized false
    pendingCommand = null
    true
  }

  internal fun buildRemoteViewsForTest(
    context: Context,
    snapshot: MediaPlaybackSnapshot,
    settings: AppSettings,
    widthDp: Int,
    heightDp: Int,
  ): RemoteViews {
    val layout = MediaWidgetLayoutSpec.fromDimensions(
      widthDp = widthDp,
      heightDp = heightDp,
      settings = settings,
    )
    val style = MediaWidgetStyle.from(context, snapshot.artwork, settings, layout)
    return buildRemoteViews(
      context = context,
      snapshot = snapshot,
      settings = settings,
      style = style,
      layout = layout,
      visualPlan = MediaWidgetVisualPlan(
        initialize = true,
        backgroundChanged = false,
        targetBackgroundSlot = 0,
        artworkChanged = false,
        targetArtworkSlot = 0,
        playbackChanged = false,
      ),
      interaction = null,
    )
  }

  private fun updateAllWithSnapshot(
    context: Context,
    snapshot: MediaPlaybackSnapshot,
    settings: AppSettings,
    interaction: MediaWidgetInteraction? = null,
  ) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, MediaWidgetProvider::class.java)
    val widgetIds = appWidgetManager.getAppWidgetIds(component)
    updateWithSnapshot(context, appWidgetManager, widgetIds, snapshot, settings, interaction)
  }

  private fun updateWithSnapshot(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
    snapshot: MediaPlaybackSnapshot,
    settings: AppSettings,
    interaction: MediaWidgetInteraction? = null,
  ) {
    if (appWidgetIds.isEmpty()) return
    appWidgetIds.forEach { widgetId ->
      val layout = MediaWidgetLayoutSpec.from(appWidgetManager.getAppWidgetOptions(widgetId), settings)
      val style = MediaWidgetStyle.from(context, snapshot.artwork, settings, layout)
      val visualPlan = visualPlanFor(widgetId, snapshot, style)
      val views = buildRemoteViews(context, snapshot, settings, style, layout, visualPlan, interaction)
      appWidgetManager.updateAppWidget(widgetId, views)
      if (visualPlan.hasTransition) {
        applyVisualTransition(context, appWidgetManager, widgetId, snapshot, style, layout, visualPlan)
      }
      commitVisualState(widgetId, snapshot, style, visualPlan)
    }
  }

  private fun visualPlanFor(
    widgetId: Int,
    snapshot: MediaPlaybackSnapshot,
    style: MediaWidgetStyle,
  ): MediaWidgetVisualPlan {
    val previous = synchronized(stateLock) { visualStates[widgetId] }
    if (previous == null) {
      return MediaWidgetVisualPlan(
        initialize = true,
        backgroundChanged = false,
        targetBackgroundSlot = 0,
        artworkChanged = false,
        targetArtworkSlot = 0,
        playbackChanged = false,
      )
    }
    val backgroundChanged = previous.backgroundSignature != style.backgroundSignature
    val artworkChanged = previous.artworkSignature != snapshot.artworkSignature()
    return MediaWidgetVisualPlan(
      initialize = false,
      backgroundChanged = backgroundChanged,
      targetBackgroundSlot = if (backgroundChanged) 1 - previous.backgroundSlot else previous.backgroundSlot,
      artworkChanged = artworkChanged,
      targetArtworkSlot = if (artworkChanged) 1 - previous.artworkSlot else previous.artworkSlot,
      playbackChanged = previous.isPlaying != snapshot.isPlaying,
    )
  }

  private fun applyVisualTransition(
    context: Context,
    appWidgetManager: AppWidgetManager,
    widgetId: Int,
    snapshot: MediaPlaybackSnapshot,
    style: MediaWidgetStyle,
    layout: MediaWidgetLayoutSpec,
    visualPlan: MediaWidgetVisualPlan,
  ) {
    val views = RemoteViews(context.packageName, R.layout.widget_media_controls)
    if (visualPlan.backgroundChanged) {
      views.setImageViewBitmap(
        if (visualPlan.targetBackgroundSlot == 0) R.id.media_widget_background else R.id.media_widget_background_next,
        style.background,
      )
      views.setDisplayedChild(R.id.media_widget_background_flipper, visualPlan.targetBackgroundSlot)
    }
    if (visualPlan.artworkChanged) {
      applyArtworkImage(
        context = context,
        views = views,
        imageViewId = if (visualPlan.targetArtworkSlot == 0) {
          R.id.media_widget_artwork
        } else {
          R.id.media_widget_artwork_next
        },
        snapshot = snapshot,
        layout = layout,
      )
      views.setDisplayedChild(R.id.media_widget_artwork_flipper, visualPlan.targetArtworkSlot)
    }
    if (visualPlan.playbackChanged) {
      views.setDisplayedChild(
        R.id.media_widget_play_pause_glyph_flipper,
        if (snapshot.isPlaying) 1 else 0,
      )
    }
    appWidgetManager.partiallyUpdateAppWidget(widgetId, views)
  }

  private fun commitVisualState(
    widgetId: Int,
    snapshot: MediaPlaybackSnapshot,
    style: MediaWidgetStyle,
    visualPlan: MediaWidgetVisualPlan,
  ) {
    synchronized(stateLock) {
      visualStates[widgetId] = MediaWidgetVisualState(
        backgroundSignature = style.backgroundSignature,
        backgroundSlot = visualPlan.targetBackgroundSlot,
        artworkSignature = snapshot.artworkSignature(),
        artworkSlot = visualPlan.targetArtworkSlot,
        isPlaying = snapshot.isPlaying,
      )
    }
  }

  private fun buildRemoteViews(
    context: Context,
    snapshot: MediaPlaybackSnapshot,
    settings: AppSettings,
    style: MediaWidgetStyle,
    layout: MediaWidgetLayoutSpec,
    visualPlan: MediaWidgetVisualPlan,
    interaction: MediaWidgetInteraction?,
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_media_controls)

    applyBackgroundBaseline(views, style, visualPlan)
    applyLayout(context, views, style, layout)
    views.setTextViewText(R.id.media_widget_source, snapshot.sourceLabel)
    views.setTextViewText(R.id.media_widget_title, snapshot.title)
    views.setTextViewText(R.id.media_widget_artist, snapshot.artist)
    views.setTextViewText(R.id.media_widget_status, interaction?.statusLabel() ?: snapshot.statusLabel())
    views.setTextViewText(R.id.media_widget_time, snapshot.timeLabel())
    views.setTextColor(R.id.media_widget_source, style.primaryTextColor)
    views.setTextColor(R.id.media_widget_title, style.primaryTextColor)
    views.setTextColor(R.id.media_widget_artist, style.secondaryTextColor)
    views.setTextColor(R.id.media_widget_permission, style.secondaryTextColor)
    views.setTextColor(R.id.media_widget_status, style.secondaryTextColor)
    views.setTextColor(R.id.media_widget_time, style.secondaryTextColor)
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
      R.id.media_widget_text_stack,
      if (layout.showText) View.VISIBLE else View.GONE,
    )
    views.setViewVisibility(
      R.id.media_widget_source,
      if (layout.showText && layout.showSource && settings.mediaWidgetShowSource) View.VISIBLE else View.GONE,
    )
    views.setViewVisibility(
      R.id.media_widget_artist,
      if (layout.showText && layout.showArtist && settings.mediaWidgetShowArtist) View.VISIBLE else View.GONE,
    )
    views.setViewVisibility(
      R.id.media_widget_permission,
      if (layout.showText && snapshot.availability == MediaPlaybackAvailability.PermissionRequired) {
        View.VISIBLE
      } else {
        View.GONE
      },
    )

    applyArtwork(context, views, snapshot, layout, visualPlan)
    applyControls(context, views, snapshot, style, layout, visualPlan, interaction)
    applyProgress(views, snapshot, style, layout)

    val playIntent = if (snapshot.availability == MediaPlaybackAvailability.PermissionRequired) {
      settingsPendingIntent(context)
    } else {
      broadcastPendingIntent(context, MediaWidgetProvider.ActionTogglePlayPause, 1)
    }
    views.setOnClickPendingIntent(R.id.media_widget_play_pause, playIntent)
    views.setOnClickPendingIntent(R.id.media_widget_play_pause_container, playIntent)
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
    views.setOnClickPendingIntent(
      R.id.media_widget_refresh,
      broadcastPendingIntent(context, MediaWidgetProvider.ActionRefresh, 4),
    )
    views.setOnClickPendingIntent(R.id.media_widget_open_media, openMediaIntent)
    views.setOnClickPendingIntent(R.id.media_widget_open_pampa, mainActivityPendingIntent(context))

    return views
  }

  private fun applyBackgroundBaseline(
    views: RemoteViews,
    style: MediaWidgetStyle,
    visualPlan: MediaWidgetVisualPlan,
  ) {
    if (!visualPlan.initialize) return
    views.setImageViewBitmap(R.id.media_widget_background, style.background)
    views.setImageViewBitmap(R.id.media_widget_background_next, style.background)
    views.setDisplayedChild(R.id.media_widget_background_flipper, 0)
  }

  private fun applyLayout(
    context: Context,
    views: RemoteViews,
    style: MediaWidgetStyle,
    layout: MediaWidgetLayoutSpec,
  ) {
    views.setViewPadding(
      R.id.media_widget_content,
      context.dp(layout.contentPaddingHorizontalDp),
      context.dp(layout.contentPaddingVerticalDp),
      context.dp(layout.contentPaddingHorizontalDp),
      context.dp(layout.contentPaddingVerticalDp),
    )
    views.setInt(R.id.media_widget_track_row, "setGravity", layout.trackGravity)
    views.setViewLayoutHeight(
      R.id.media_widget_controls,
      layout.controlsRowHeightDp.toFloat(),
      TypedValue.COMPLEX_UNIT_DIP,
    )
    views.setViewLayoutHeight(
      R.id.media_widget_extra_actions,
      layout.extraRowHeightDp.toFloat(),
      TypedValue.COMPLEX_UNIT_DIP,
    )
    views.setViewLayoutHeight(
      R.id.media_widget_source,
      layout.sourcePillHeightDp,
      TypedValue.COMPLEX_UNIT_DIP,
    )
    views.setTextViewTextSize(R.id.media_widget_source, TypedValue.COMPLEX_UNIT_SP, layout.sourceSp)
    views.setTextViewTextSize(R.id.media_widget_title, TypedValue.COMPLEX_UNIT_SP, layout.titleSp)
    views.setTextViewTextSize(R.id.media_widget_artist, TypedValue.COMPLEX_UNIT_SP, layout.artistSp)
    views.setTextViewTextSize(R.id.media_widget_permission, TypedValue.COMPLEX_UNIT_SP, layout.permissionSp)
    views.setTextViewTextSize(R.id.media_widget_status, TypedValue.COMPLEX_UNIT_SP, layout.metaSp)
    views.setTextViewTextSize(R.id.media_widget_time, TypedValue.COMPLEX_UNIT_SP, layout.metaSp)
    views.setInt(R.id.media_widget_title, "setMaxLines", layout.titleMaxLines)
    views.setViewVisibility(R.id.media_widget_meta_row, if (layout.showMeta) View.VISIBLE else View.GONE)
    views.setViewVisibility(
      R.id.media_widget_extra_actions,
      if (layout.showExtraActions) View.VISIBLE else View.GONE,
    )
    views.setColorStateList(
      R.id.media_widget_refresh,
      "setBackgroundTintList",
      ColorStateList.valueOf(style.controlSurfaceColor),
    )
    views.setColorStateList(
      R.id.media_widget_open_media,
      "setBackgroundTintList",
      ColorStateList.valueOf(style.controlSurfaceColor),
    )
    views.setColorStateList(
      R.id.media_widget_open_pampa,
      "setBackgroundTintList",
      ColorStateList.valueOf(style.controlSurfaceColor),
    )
  }

  private fun applyArtwork(
    context: Context,
    views: RemoteViews,
    snapshot: MediaPlaybackSnapshot,
    layout: MediaWidgetLayoutSpec,
    visualPlan: MediaWidgetVisualPlan,
  ) {
    val artworkDp = layout.artworkDp
    views.setViewLayoutWidth(R.id.media_widget_artwork_frame, artworkDp, TypedValue.COMPLEX_UNIT_DIP)
    views.setViewLayoutHeight(R.id.media_widget_artwork_frame, artworkDp, TypedValue.COMPLEX_UNIT_DIP)
    // Load the current artwork into the slot that will be displayed after this update.
    // The other slot is left with its previous content so the flipper crossfade looks correct.
    // On initialize, both slots are primed to avoid showing an empty view briefly.
    val primarySlot = if (visualPlan.initialize) R.id.media_widget_artwork else {
      if (visualPlan.targetArtworkSlot == 0) R.id.media_widget_artwork else R.id.media_widget_artwork_next
    }
    applyArtworkImage(context, views, primarySlot, snapshot, layout)
    if (visualPlan.initialize) {
      applyArtworkImage(context, views, R.id.media_widget_artwork_next, snapshot, layout)
      views.setDisplayedChild(R.id.media_widget_artwork_flipper, 0)
    }
  }

  private fun applyArtworkImage(
    context: Context,
    views: RemoteViews,
    imageViewId: Int,
    snapshot: MediaPlaybackSnapshot,
    layout: MediaWidgetLayoutSpec,
  ) {
    if (snapshot.artwork != null) {
      views.setViewPadding(imageViewId, 0, 0, 0, 0)
      views.setImageViewBitmap(imageViewId, snapshot.artwork.roundedSquare())
    } else {
      val padding = context.dp((layout.artworkDp * 0.18f).toInt().coerceAtLeast(10))
      views.setViewPadding(imageViewId, padding, padding, padding, padding)
      views.setImageViewResource(imageViewId, R.drawable.ic_widget_music_note)
    }
  }

  private fun applyControls(
    context: Context,
    views: RemoteViews,
    snapshot: MediaPlaybackSnapshot,
    style: MediaWidgetStyle,
    layout: MediaWidgetLayoutSpec,
    visualPlan: MediaWidgetVisualPlan,
    interaction: MediaWidgetInteraction?,
  ) {
    val playEnabled = snapshot.canPlayPause || snapshot.availability == MediaPlaybackAvailability.PermissionRequired
    val commandAction = interaction?.action
    val showFeedback = interaction?.showFeedback == true
    val controlsLocked = interaction != null
    val sidePadding = context.dp(((layout.sideButtonDp - layout.sideIconDp) / 2).coerceAtLeast(4))
    val playPadding = context.dp(((layout.playButtonDp - layout.playIconDp) / 2).coerceAtLeast(4))
    val extraPadding = context.dp(((layout.extraButtonDp - layout.extraIconDp) / 2).coerceAtLeast(4))

    views.setViewPadding(R.id.media_widget_play_pause, 0, 0, 0, 0)
    views.setViewPadding(R.id.media_widget_previous, sidePadding, sidePadding, sidePadding, sidePadding)
    views.setViewPadding(R.id.media_widget_next, sidePadding, sidePadding, sidePadding, sidePadding)
    views.setViewPadding(R.id.media_widget_refresh, extraPadding, extraPadding, extraPadding, extraPadding)
    views.setViewPadding(R.id.media_widget_open_media, extraPadding, extraPadding, extraPadding, extraPadding)
    views.setViewPadding(R.id.media_widget_open_pampa, extraPadding, extraPadding, extraPadding, extraPadding)
    views.setViewPadding(R.id.media_widget_play_glyph, playPadding, playPadding, playPadding, playPadding)
    views.setViewPadding(R.id.media_widget_pause_glyph, playPadding, playPadding, playPadding, playPadding)

    views.setSquareDp(R.id.media_widget_previous, layout.sideButtonDp)
    views.setSquareDp(R.id.media_widget_next, layout.sideButtonDp)
    views.setSquareDp(R.id.media_widget_play_pause_container, layout.playButtonDp)
    views.setSquareDp(R.id.media_widget_play_pause, layout.playButtonDp)
    views.setSquareDp(R.id.media_widget_play_pause_glyph_flipper, layout.playButtonDp)
    views.setSquareDp(R.id.media_widget_command_pending, minOf(30, layout.playIconDp + 6))
    views.setSquareDp(R.id.media_widget_refresh, layout.extraButtonDp)
    views.setSquareDp(R.id.media_widget_open_media, layout.extraButtonDp)
    views.setSquareDp(R.id.media_widget_open_pampa, layout.extraButtonDp)
    views.setViewVisibility(R.id.media_widget_previous, if (layout.showSideControls) View.VISIBLE else View.GONE)
    views.setViewVisibility(R.id.media_widget_next, if (layout.showSideControls) View.VISIBLE else View.GONE)

    views.setColorStateList(
      R.id.media_widget_previous,
      "setBackgroundTintList",
      ColorStateList.valueOf(
        if (snapshot.canSkipPrevious) style.controlSurfaceColor else style.disabledControlSurfaceColor,
      ),
    )
    views.setColorStateList(
      R.id.media_widget_next,
      "setBackgroundTintList",
      ColorStateList.valueOf(
        if (snapshot.canSkipNext) style.controlSurfaceColor else style.disabledControlSurfaceColor,
      ),
    )
    views.setColorStateList(
      R.id.media_widget_play_pause,
      "setBackgroundTintList",
      ColorStateList.valueOf(if (playEnabled) style.playSurfaceColor else style.disabledControlSurfaceColor),
    )
    views.setImageViewResource(R.id.media_widget_previous, R.drawable.ic_widget_previous)
    views.setImageViewResource(R.id.media_widget_next, R.drawable.ic_widget_next)
    views.setImageViewResource(R.id.media_widget_refresh, R.drawable.ic_widget_refresh)
    views.setImageViewResource(R.id.media_widget_open_media, R.drawable.ic_widget_open_app)
    views.setImageViewResource(R.id.media_widget_open_pampa, R.drawable.ic_widget_settings)
    views.setColorStateList(
      R.id.media_widget_previous,
      "setImageTintList",
      ColorStateList.valueOf(if (snapshot.canSkipPrevious) style.controlIconColor else style.disabledControlIconColor),
    )
    views.setColorStateList(
      R.id.media_widget_next,
      "setImageTintList",
      ColorStateList.valueOf(if (snapshot.canSkipNext) style.controlIconColor else style.disabledControlIconColor),
    )
    listOf(R.id.media_widget_play_glyph, R.id.media_widget_pause_glyph).forEach { glyphId ->
      views.setColorStateList(
        glyphId,
        "setImageTintList",
        ColorStateList.valueOf(if (playEnabled) style.playIconColor else style.disabledControlIconColor),
      )
    }
    listOf(R.id.media_widget_refresh, R.id.media_widget_open_media, R.id.media_widget_open_pampa).forEach { iconId ->
      views.setColorStateList(iconId, "setImageTintList", ColorStateList.valueOf(style.controlIconColor))
    }
    views.setColorStateList(
      R.id.media_widget_command_pending,
      "setIndeterminateTintList",
      ColorStateList.valueOf(style.playIconColor),
    )
    if (visualPlan.initialize) {
      views.setDisplayedChild(R.id.media_widget_play_pause_glyph_flipper, if (snapshot.isPlaying) 1 else 0)
    }

    val isTogglePending = commandAction == MediaControlAction.TogglePlayPause && showFeedback
    val isNextPending = commandAction == MediaControlAction.Next && showFeedback
    val isPreviousPending = commandAction == MediaControlAction.Previous && showFeedback
    views.setViewVisibility(
      R.id.media_widget_command_pending,
      if (isTogglePending) View.VISIBLE else View.GONE,
    )
    views.setFloat(R.id.media_widget_play_pause_glyph_flipper, "setAlpha", if (isTogglePending) 0.16f else 1f)
    views.setScale(R.id.media_widget_play_pause_container, if (isTogglePending) 0.93f else 1f)
    views.setScale(R.id.media_widget_next, if (isNextPending) 0.91f else 1f)
    views.setScale(R.id.media_widget_previous, if (isPreviousPending) 0.91f else 1f)

    views.setBoolean(R.id.media_widget_previous, "setEnabled", snapshot.canSkipPrevious && !controlsLocked)
    views.setBoolean(R.id.media_widget_next, "setEnabled", snapshot.canSkipNext && !controlsLocked)
    views.setBoolean(R.id.media_widget_play_pause, "setEnabled", playEnabled && !controlsLocked)
  }

  private fun applyProgress(
    views: RemoteViews,
    snapshot: MediaPlaybackSnapshot,
    style: MediaWidgetStyle,
    layout: MediaWidgetLayoutSpec,
  ) {
    val progress = snapshot.progressPermille()
    views.setViewLayoutHeight(
      R.id.media_widget_progress,
      layout.progressHeightDp,
      TypedValue.COMPLEX_UNIT_DIP,
    )
    views.setViewVisibility(
      R.id.media_widget_progress,
      when {
        !layout.showProgress -> View.GONE
        progress > 0 -> View.VISIBLE
        else -> View.INVISIBLE
      },
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

  private fun mainActivityPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return PendingIntent.getActivity(
      context,
      21,
      intent,
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

internal enum class MediaWidgetSizeClass {
  Mini,
  Compact,
  Standard,
  Expanded,
  Full,
}

internal data class MediaWidgetLayoutSpec(
  val widthDp: Int,
  val heightDp: Int,
  val sizeClass: MediaWidgetSizeClass,
  val contentPaddingHorizontalDp: Int,
  val contentPaddingVerticalDp: Int,
  val trackGravity: Int,
  val artworkDp: Float,
  val showText: Boolean,
  val showSource: Boolean,
  val showArtist: Boolean,
  val showProgress: Boolean,
  val showMeta: Boolean,
  val showExtraActions: Boolean,
  val showSideControls: Boolean,
  val titleMaxLines: Int,
  val sourcePillHeightDp: Float,
  val sourceSp: Float,
  val titleSp: Float,
  val artistSp: Float,
  val permissionSp: Float,
  val metaSp: Float,
  val controlsRowHeightDp: Int,
  val playButtonDp: Int,
  val sideButtonDp: Int,
  val playIconDp: Int,
  val sideIconDp: Int,
  val extraRowHeightDp: Int,
  val extraButtonDp: Int,
  val extraIconDp: Int,
  val progressHeightDp: Float,
) {
  companion object {
    fun from(options: Bundle?, settings: AppSettings): MediaWidgetLayoutSpec {
      val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
      val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
      val maxWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
      val maxHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
      val widthDp = (if (minWidth > 0) minWidth else maxWidth.takeIf { it > 0 } ?: 250)
        .coerceAtLeast(96)
      val heightDp = (if (minHeight > 0) minHeight else maxHeight.takeIf { it > 0 } ?: 170)
        .coerceAtLeast(96)
      return fromDimensions(widthDp = widthDp, heightDp = heightDp, settings = settings)
    }

    fun fromDimensions(
      widthDp: Int,
      heightDp: Int,
      settings: AppSettings,
    ): MediaWidgetLayoutSpec {
      val sizeClass = when {
        widthDp < 185 || heightDp < 150 -> MediaWidgetSizeClass.Mini
        widthDp < 250 || heightDp < 185 -> MediaWidgetSizeClass.Compact
        heightDp >= 420 || (widthDp >= 430 && heightDp >= 320) -> MediaWidgetSizeClass.Full
        widthDp >= 340 || heightDp >= 260 -> MediaWidgetSizeClass.Expanded
        else -> MediaWidgetSizeClass.Standard
      }

      val horizontalPadding = when (sizeClass) {
        MediaWidgetSizeClass.Mini -> 10
        MediaWidgetSizeClass.Compact -> 12
        MediaWidgetSizeClass.Standard -> 16
        MediaWidgetSizeClass.Expanded -> 20
        MediaWidgetSizeClass.Full -> 24
      }
      val verticalPadding = when (sizeClass) {
        MediaWidgetSizeClass.Mini -> 9
        MediaWidgetSizeClass.Compact -> 12
        MediaWidgetSizeClass.Standard -> 15
        MediaWidgetSizeClass.Expanded -> 20
        MediaWidgetSizeClass.Full -> 24
      }
      val showText = widthDp >= 170 && heightDp >= 135
      val showProgress = sizeClass != MediaWidgetSizeClass.Mini && heightDp >= 164
      val showMeta = sizeClass in setOf(MediaWidgetSizeClass.Expanded, MediaWidgetSizeClass.Full) && heightDp >= 280
      val showExtraActions = sizeClass in setOf(MediaWidgetSizeClass.Expanded, MediaWidgetSizeClass.Full) &&
        (heightDp >= 320 || (widthDp >= 460 && heightDp >= 260))
      val showSideControls = widthDp >= 176 && heightDp >= 118
      val playButton = when (sizeClass) {
        MediaWidgetSizeClass.Mini -> 44
        MediaWidgetSizeClass.Compact -> 50
        MediaWidgetSizeClass.Standard -> 54
        MediaWidgetSizeClass.Expanded -> 60
        MediaWidgetSizeClass.Full -> 68
      }
      val sideButton = when (sizeClass) {
        MediaWidgetSizeClass.Mini -> 36
        MediaWidgetSizeClass.Compact -> 40
        MediaWidgetSizeClass.Standard -> 42
        MediaWidgetSizeClass.Expanded -> 46
        MediaWidgetSizeClass.Full -> 50
      }
      val controlsRowHeight = max(playButton, sideButton)
      val extraRowHeight = if (showExtraActions) {
        when (sizeClass) {
          MediaWidgetSizeClass.Full -> 52
          else -> 46
        }
      } else {
        1
      }
      val progressBlock = if (showProgress) 22 else 0
      val metaBlock = if (showMeta) 30 else 0
      val extraBlock = if (showExtraActions) extraRowHeight + 10 else 0
      val availableTrackHeight = (
        heightDp - verticalPadding * 2 - controlsRowHeight - progressBlock - metaBlock - extraBlock
        ).coerceAtLeast(48)
      val desiredArtwork = when (settings.mediaWidgetArtworkSize) {
        MediaWidgetArtworkSize.Compact -> 82
        MediaWidgetArtworkSize.Balanced -> 98
        MediaWidgetArtworkSize.Large -> 112
      } + when (sizeClass) {
        MediaWidgetSizeClass.Mini -> -24
        MediaWidgetSizeClass.Compact -> -12
        MediaWidgetSizeClass.Standard -> 0
        MediaWidgetSizeClass.Expanded -> 30
        MediaWidgetSizeClass.Full -> 128
      }
      val widthArtworkLimit = if (showText) {
        (widthDp * if (sizeClass == MediaWidgetSizeClass.Full) 0.46f else 0.42f).toInt()
      } else {
        widthDp - horizontalPadding * 2
      }.coerceAtLeast(48)
      val artwork = minOf(desiredArtwork, availableTrackHeight, widthArtworkLimit)
        .coerceAtLeast(if (sizeClass == MediaWidgetSizeClass.Mini) 48 else 58)
        .toFloat()

      return MediaWidgetLayoutSpec(
        widthDp = widthDp,
        heightDp = heightDp,
        sizeClass = sizeClass,
        contentPaddingHorizontalDp = horizontalPadding,
        contentPaddingVerticalDp = verticalPadding,
        trackGravity = if (showText) Gravity.CENTER_VERTICAL or Gravity.START else Gravity.CENTER,
        artworkDp = artwork,
        showText = showText,
        showSource = sizeClass != MediaWidgetSizeClass.Mini && heightDp >= 160,
        showArtist = sizeClass !in setOf(MediaWidgetSizeClass.Mini, MediaWidgetSizeClass.Compact) || heightDp >= 176,
        showProgress = showProgress,
        showMeta = showMeta,
        showExtraActions = showExtraActions,
        showSideControls = showSideControls,
        titleMaxLines = when (sizeClass) {
          MediaWidgetSizeClass.Full -> 3
          MediaWidgetSizeClass.Expanded -> 2
          MediaWidgetSizeClass.Standard -> 2
          else -> 1
        },
        sourcePillHeightDp = if (sizeClass == MediaWidgetSizeClass.Full) 24f else 20f,
        sourceSp = if (sizeClass == MediaWidgetSizeClass.Full) 11.5f else 10f,
        titleSp = when (sizeClass) {
          MediaWidgetSizeClass.Mini -> 13f
          MediaWidgetSizeClass.Compact -> 15f
          MediaWidgetSizeClass.Standard -> 18f
          MediaWidgetSizeClass.Expanded -> 21f
          MediaWidgetSizeClass.Full -> 25f
        },
        artistSp = when (sizeClass) {
          MediaWidgetSizeClass.Full -> 16f
          MediaWidgetSizeClass.Expanded -> 15f
          else -> 14f
        },
        permissionSp = if (sizeClass == MediaWidgetSizeClass.Full) 12f else 11f,
        metaSp = if (sizeClass == MediaWidgetSizeClass.Full) 13f else 12f,
        controlsRowHeightDp = controlsRowHeight,
        playButtonDp = playButton,
        sideButtonDp = sideButton,
        playIconDp = when (sizeClass) {
          MediaWidgetSizeClass.Mini -> 24
          MediaWidgetSizeClass.Compact -> 26
          MediaWidgetSizeClass.Standard -> 28
          MediaWidgetSizeClass.Expanded -> 31
          MediaWidgetSizeClass.Full -> 35
        },
        sideIconDp = when (sizeClass) {
          MediaWidgetSizeClass.Full -> 27
          MediaWidgetSizeClass.Expanded -> 25
          else -> 23
        },
        extraRowHeightDp = extraRowHeight,
        extraButtonDp = if (sizeClass == MediaWidgetSizeClass.Full) 48 else 42,
        extraIconDp = if (sizeClass == MediaWidgetSizeClass.Full) 25 else 22,
        progressHeightDp = if (sizeClass == MediaWidgetSizeClass.Full) 5f else 4f,
      )
    }
  }
}

private data class MediaWidgetStyle(
  val background: Bitmap,
  val backgroundSignature: String,
  val primaryTextColor: Int,
  val secondaryTextColor: Int,
  val controlIconColor: Int,
  val disabledControlIconColor: Int,
  val playIconColor: Int,
  val controlSurfaceColor: Int,
  val disabledControlSurfaceColor: Int,
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
      layout: MediaWidgetLayoutSpec,
    ): MediaWidgetStyle {
      val palette = extractPalette(artwork)
      val accent = palette.vibrant.enrich(0.08f)
      val backgroundColor = accent.toWidgetBackgroundColor(settings.mediaWidgetTheme)
      val darkSurface = when (settings.mediaWidgetTheme) {
        MediaWidgetTheme.DarkGlass -> true
        MediaWidgetTheme.LightGlass -> false
        else -> backgroundColor.luminance() < 0.48f
      }
      val density = context.resources.displayMetrics.density
      val backgroundWidth = (layout.widthDp * density).toInt().coerceIn(BackgroundMinPx, BackgroundMaxPx)
      val backgroundHeight = (layout.heightDp * density).toInt().coerceIn(BackgroundMinPx, BackgroundMaxPx)
      val background = createBackgroundBitmap(
        backgroundColor = backgroundColor,
        width = backgroundWidth,
        height = backgroundHeight,
        darkSurface = darkSurface,
      )
      val primary = backgroundColor.bestContentColor()
      val secondary = backgroundColor.contentVariantColor(darkSurface)
      val controlSurface = backgroundColor.secondaryControlColor(darkSurface)
      val disabledControlSurface = backgroundColor.disabledControlColor(darkSurface)
      val playSurface = backgroundColor.playButtonColor(darkSurface)
      val sourcePill = backgroundColor.sourcePillColor(darkSurface)
      val artworkFrame = backgroundColor.artworkFrameColor(darkSurface)
      return MediaWidgetStyle(
        background = background,
        backgroundSignature = "$backgroundColor:${layout.widthDp}x${layout.heightDp}",
        primaryTextColor = primary,
        secondaryTextColor = secondary,
        controlIconColor = controlSurface.bestContentColor(),
        disabledControlIconColor = disabledControlSurface.bestContentColor().blendWith(disabledControlSurface, 0.24f),
        playIconColor = playSurface.bestContentColor(),
        controlSurfaceColor = controlSurface,
        disabledControlSurfaceColor = disabledControlSurface,
        playSurfaceColor = playSurface,
        sourcePillColor = sourcePill,
        artworkFrameColor = artworkFrame,
        progressColor = playSurface,
        progressTrackColor = controlSurface,
      )
    }

    private fun createBackgroundBitmap(
      backgroundColor: Int,
      width: Int,
      height: Int,
      darkSurface: Boolean,
    ): Bitmap {
      val radius = (min(width, height) * 0.10f).coerceIn(42f, 74f)
      val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())

      val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val layer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val layerCanvas = Canvas(layer)

      val topTone = backgroundColor.lighten(0.10f)
      val middleTone = backgroundColor.lighten(0.025f)
      val lowerTone = backgroundColor.darken(0.045f)
      val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f,
          0f,
          width.toFloat(),
          height.toFloat(),
          intArrayOf(topTone, middleTone, lowerTone),
          floatArrayOf(0f, 0.54f, 1f),
          Shader.TileMode.CLAMP,
        )
      }
      layerCanvas.drawRoundRect(bounds, radius, radius, gradientPaint)

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
        color = if (darkSurface) backgroundColor.lighten(0.18f) else backgroundColor.darken(0.10f)
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

// ---------- bitmap helpers ----------

private fun MediaPlaybackSnapshot.progressPermille(): Int {
  if (durationMs <= 0L) return 0
  return ((positionMs.coerceIn(0L, durationMs) * 1000L) / durationMs)
    .toInt()
    .coerceIn(0, 1000)
}

private fun MediaPlaybackSnapshot.statusLabel(): String =
  when (availability) {
    MediaPlaybackAvailability.PermissionRequired -> "Accesso richiesto"
    MediaPlaybackAvailability.NoSession -> if (isFromCache) "Ultima sessione" else "Nessuna sessione"
    MediaPlaybackAvailability.Active -> if (isPlaying) "In riproduzione" else "In pausa"
  }

private fun MediaWidgetInteraction.statusLabel(): String =
  when (action) {
    MediaControlAction.TogglePlayPause -> if (expectedPlaying == true) {
      "Avvio riproduzione…"
    } else {
      "Metto in pausa…"
    }
    MediaControlAction.Next -> "Passo al brano successivo…"
    MediaControlAction.Previous -> "Torno al brano precedente…"
  }

private fun MediaPlaybackSnapshot.canHandle(action: MediaControlAction): Boolean =
  when (action) {
    MediaControlAction.TogglePlayPause -> canPlayPause
    MediaControlAction.Next -> canSkipNext
    MediaControlAction.Previous -> canSkipPrevious
  }

private fun MediaPlaybackSnapshot.confirms(command: PendingMediaCommand): Boolean {
  if (availability != MediaPlaybackAvailability.Active) return false
  return when (command.action) {
    MediaControlAction.TogglePlayPause -> isPlaying == command.expectedPlaying
    MediaControlAction.Next,
    MediaControlAction.Previous -> {
      val currentTrack = trackSignature()
      currentTrack.isNotBlank() && currentTrack != command.baselineSnapshot.trackSignature()
    }
  }
}

private fun MediaPlaybackSnapshot.trackSignature(): String =
  if (packageName.isBlank() && title.isBlank() && artist.isBlank()) {
    ""
  } else {
    listOf(packageName, title, artist).joinToString("|")
  }

private fun MediaPlaybackSnapshot.artworkSignature(): String =
  "${trackSignature()}|${artwork?.width ?: 0}x${artwork?.height ?: 0}"

private fun MediaPlaybackSnapshot.timeLabel(): String {
  if (durationMs <= 0L) return ""
  val position = positionMs.coerceIn(0L, durationMs)
  return "${position.clockLabel()} / ${durationMs.clockLabel()}"
}

private fun Long.clockLabel(): String {
  val totalSeconds = (this / 1000L).coerceAtLeast(0L)
  val seconds = (totalSeconds % 60).toInt()
  val minutes = ((totalSeconds / 60) % 60).toInt()
  val hours = (totalSeconds / 3600).toInt()
  fun two(value: Int) = if (value < 10) "0$value" else value.toString()
  return if (hours > 0) {
    "$hours:${two(minutes)}:${two(seconds)}"
  } else {
    "$minutes:${two(seconds)}"
  }
}

private fun RemoteViews.setSquareDp(viewId: Int, sizeDp: Int) {
  setViewLayoutWidth(viewId, sizeDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
  setViewLayoutHeight(viewId, sizeDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
}

private fun RemoteViews.setScale(viewId: Int, scale: Float) {
  setFloat(viewId, "setScaleX", scale)
  setFloat(viewId, "setScaleY", scale)
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

internal fun Int.luminance(): Float =
  (0.299f * Color.red(this) + 0.587f * Color.green(this) + 0.114f * Color.blue(this)) / 255f

internal fun Int.toWidgetBackgroundColor(theme: MediaWidgetTheme): Int {
  val source = enrich(0.10f)
  val color = when (theme) {
    MediaWidgetTheme.SamsungGlass -> source.lighten(0.42f)
    MediaWidgetTheme.AdaptiveGlass -> source.lighten(if (source.luminance() < 0.45f) 0.46f else 0.34f)
    MediaWidgetTheme.LightGlass -> source.lighten(0.68f).blendWith(Color.rgb(250, 251, 246), 0.24f)
    MediaWidgetTheme.DarkGlass -> source.darken(0.48f).blendWith(Color.rgb(18, 22, 20), 0.30f)
    MediaWidgetTheme.AlbumColor -> source
  }
  return when {
    theme == MediaWidgetTheme.AlbumColor && color.luminance() > 0.82f -> color.darken(0.08f)
    theme == MediaWidgetTheme.AlbumColor && color.luminance() < 0.12f -> color.lighten(0.10f)
    else -> color
  }
}

private fun Int.bestContentColor(): Int =
  if (luminance() < 0.50f) Color.rgb(250, 255, 251) else Color.rgb(15, 20, 18)

private fun Int.contentVariantColor(darkSurface: Boolean): Int =
  if (darkSurface) Color.rgb(220, 231, 224) else Color.rgb(45, 53, 49)

internal fun Int.playButtonColor(darkSurface: Boolean): Int =
  if (darkSurface) lighten(0.24f) else darken(0.14f)

private fun Int.secondaryControlColor(darkSurface: Boolean): Int =
  if (darkSurface) lighten(0.12f) else darken(0.055f)

private fun Int.disabledControlColor(darkSurface: Boolean): Int =
  if (darkSurface) lighten(0.06f) else darken(0.025f)

private fun Int.sourcePillColor(darkSurface: Boolean): Int =
  if (darkSurface) lighten(0.13f) else lighten(0.08f)

private fun Int.artworkFrameColor(darkSurface: Boolean): Int =
  if (darkSurface) lighten(0.09f) else lighten(0.12f)

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

private fun RectF.insetBy(amount: Float): RectF =
  RectF(left + amount, top + amount, right - amount, bottom - amount)

private fun Context.dp(value: Int): Int =
  (value * resources.displayMetrics.density).toInt()
