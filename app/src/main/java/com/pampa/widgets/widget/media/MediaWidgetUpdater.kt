package com.pampa.widgets.widget.media

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.RemoteViews
import com.pampa.widgets.R
import com.pampa.widgets.core.media.MediaPlaybackAvailability
import com.pampa.widgets.core.media.MediaPlaybackSnapshot
import com.pampa.widgets.core.media.MediaSessionReader
import com.pampa.widgets.core.media.NotificationListenerAccess

object MediaWidgetUpdater {
  fun updateAll(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val component = ComponentName(context, MediaWidgetProvider::class.java)
    val widgetIds = appWidgetManager.getAppWidgetIds(component)
    update(context, appWidgetManager, widgetIds)
  }

  fun update(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    if (appWidgetIds.isEmpty()) return
    val snapshot = MediaSessionReader.readSnapshot(context)
    val views = buildRemoteViews(context, snapshot)
    appWidgetIds.forEach { widgetId ->
      appWidgetManager.updateAppWidget(widgetId, views)
    }
  }

  private fun buildRemoteViews(
    context: Context,
    snapshot: MediaPlaybackSnapshot,
  ): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_media_controls)
    val style = MediaWidgetStyle.from(snapshot.artwork)

    views.setImageViewBitmap(R.id.media_widget_background, style.background)
    views.setTextViewText(R.id.media_widget_source, snapshot.sourceLabel)
    views.setTextViewText(R.id.media_widget_title, snapshot.title)
    views.setTextViewText(R.id.media_widget_artist, snapshot.artist)
    views.setTextColor(R.id.media_widget_source, style.primaryTextColor)
    views.setTextColor(R.id.media_widget_title, style.primaryTextColor)
    views.setTextColor(R.id.media_widget_artist, style.secondaryTextColor)
    views.setTextColor(R.id.media_widget_permission, style.secondaryTextColor)
    views.setViewVisibility(
      R.id.media_widget_permission,
      if (snapshot.availability == MediaPlaybackAvailability.PermissionRequired) View.VISIBLE else View.GONE,
    )
    views.setImageViewResource(
      R.id.media_widget_play_pause,
      if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
    )
    views.setInt(R.id.media_widget_previous, "setColorFilter", style.primaryTextColor)
    views.setInt(R.id.media_widget_next, "setColorFilter", style.primaryTextColor)
    views.setInt(R.id.media_widget_play_pause, "setColorFilter", style.playIconColor)

    if (snapshot.artwork != null) {
      views.setViewPadding(R.id.media_widget_artwork, 0, 0, 0, 0)
      views.setImageViewBitmap(R.id.media_widget_artwork, snapshot.artwork.roundedSquare())
    } else {
      val padding = context.dp(18)
      views.setViewPadding(R.id.media_widget_artwork, padding, padding, padding, padding)
      views.setImageViewResource(R.id.media_widget_artwork, R.drawable.ic_widget_music_note)
    }

    val playIntent = if (snapshot.availability == MediaPlaybackAvailability.PermissionRequired) {
      settingsPendingIntent(context)
    } else {
      broadcastPendingIntent(context, MediaWidgetProvider.ActionTogglePlayPause, 1)
    }
    views.setOnClickPendingIntent(R.id.media_widget_play_pause, playIntent)
    views.setOnClickPendingIntent(
      R.id.media_widget_permission,
      settingsPendingIntent(context),
    )
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
}

private data class MediaWidgetStyle(
  val background: Bitmap,
  val primaryTextColor: Int,
  val secondaryTextColor: Int,
  val playIconColor: Int,
) {
  companion object {
    fun from(artwork: Bitmap?): MediaWidgetStyle {
      val baseColor = artwork?.dominantColor() ?: Color.rgb(238, 244, 236)
      val darkCard = baseColor.luminance() < 0.48f
      val start = if (darkCard) baseColor.lighten(0.12f) else baseColor.lighten(0.34f)
      val end = if (darkCard) baseColor.darken(0.18f) else baseColor.lighten(0.18f)
      val primary = if (darkCard) Color.WHITE else Color.rgb(22, 27, 24)
      val secondary = if (darkCard) Color.argb(220, 255, 255, 255) else Color.rgb(74, 83, 77)
      return MediaWidgetStyle(
        background = createBackgroundBitmap(start, end),
        primaryTextColor = primary,
        secondaryTextColor = secondary,
        playIconColor = Color.rgb(14, 27, 20),
      )
    }

    private fun createBackgroundBitmap(startColor: Int, endColor: Int): Bitmap {
      val width = 640
      val height = 300
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
      val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f,
          0f,
          width.toFloat(),
          height.toFloat(),
          startColor.withAlpha(224),
          endColor.withAlpha(214),
          Shader.TileMode.CLAMP,
        )
      }
      canvas.drawRoundRect(rect, 54f, 54f, paint)

      val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f,
          0f,
          width.toFloat(),
          0f,
          Color.argb(64, 255, 255, 255),
          Color.argb(6, 255, 255, 255),
          Shader.TileMode.CLAMP,
        )
      }
      canvas.drawRoundRect(rect, 54f, 54f, shine)
      return bitmap
    }
  }
}

private fun Bitmap.roundedSquare(): Bitmap {
  val size = minOf(width, height)
  val left = (width - size) / 2
  val top = (height - size) / 2
  val output = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(output)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  val rect = Rect(0, 0, output.width, output.height)
  val source = Rect(left, top, left + size, top + size)
  val rounded = RectF(rect)
  canvas.drawRoundRect(rounded, 34f, 34f, paint.apply { color = Color.WHITE })
  paint.shader = null
  paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
  canvas.drawBitmap(this, source, rect, paint)
  paint.xfermode = null
  return output
}

private fun Bitmap.dominantColor(): Int {
  var red = 0L
  var green = 0L
  var blue = 0L
  var count = 0L
  val stepX = (width / 32).coerceAtLeast(1)
  val stepY = (height / 32).coerceAtLeast(1)
  var y = 0
  while (y < height) {
    var x = 0
    while (x < width) {
      val color = getPixel(x, y)
      val alpha = Color.alpha(color)
      if (alpha > 180) {
        red += Color.red(color)
        green += Color.green(color)
        blue += Color.blue(color)
        count++
      }
      x += stepX
    }
    y += stepY
  }
  if (count == 0L) return Color.rgb(80, 94, 86)
  return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
}

private fun Int.luminance(): Float {
  return (0.299f * Color.red(this) + 0.587f * Color.green(this) + 0.114f * Color.blue(this)) / 255f
}

private fun Int.lighten(amount: Float): Int {
  return Color.rgb(
    (Color.red(this) + (255 - Color.red(this)) * amount).toInt().coerceIn(0, 255),
    (Color.green(this) + (255 - Color.green(this)) * amount).toInt().coerceIn(0, 255),
    (Color.blue(this) + (255 - Color.blue(this)) * amount).toInt().coerceIn(0, 255),
  )
}

private fun Int.darken(amount: Float): Int {
  return Color.rgb(
    (Color.red(this) * (1f - amount)).toInt().coerceIn(0, 255),
    (Color.green(this) * (1f - amount)).toInt().coerceIn(0, 255),
    (Color.blue(this) * (1f - amount)).toInt().coerceIn(0, 255),
  )
}

private fun Int.withAlpha(alpha: Int): Int {
  return Color.argb(alpha.coerceIn(0, 255), Color.red(this), Color.green(this), Color.blue(this))
}

private fun Context.dp(value: Int): Int {
  return (value * resources.displayMetrics.density).toInt()
}
