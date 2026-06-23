package com.pampa.widgets.widget.media

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

    views.setTextViewText(R.id.media_widget_source, snapshot.sourceLabel)
    views.setTextViewText(R.id.media_widget_title, snapshot.title)
    views.setTextViewText(R.id.media_widget_artist, snapshot.artist)
    views.setViewVisibility(
      R.id.media_widget_permission,
      if (snapshot.availability == MediaPlaybackAvailability.PermissionRequired) View.VISIBLE else View.GONE,
    )
    views.setImageViewResource(
      R.id.media_widget_play_pause,
      if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
    )

    if (snapshot.artwork != null) {
      views.setImageViewBitmap(R.id.media_widget_artwork, snapshot.artwork)
    } else {
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
    views.setOnClickPendingIntent(
      R.id.media_widget_refresh,
      broadcastPendingIntent(context, MediaWidgetProvider.ActionRefresh, 4),
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
