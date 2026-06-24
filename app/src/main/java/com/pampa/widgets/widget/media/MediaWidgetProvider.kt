package com.pampa.widgets.widget.media

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.pampa.widgets.core.media.MediaControlAction
import com.pampa.widgets.core.media.MediaSessionReader

class MediaWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    MediaWidgetUpdater.update(context, appWidgetManager, appWidgetIds)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    when (intent.action) {
      ActionTogglePlayPause -> {
        val feedbackSnapshot = MediaWidgetUpdater.feedbackSnapshot(context, MediaControlAction.TogglePlayPause)
        MediaSessionReader.dispatch(context, MediaControlAction.TogglePlayPause)
        MediaWidgetUpdater.afterMediaControl(context, MediaControlAction.TogglePlayPause, feedbackSnapshot)
      }
      ActionNext -> {
        val feedbackSnapshot = MediaWidgetUpdater.feedbackSnapshot(context, MediaControlAction.Next)
        MediaSessionReader.dispatch(context, MediaControlAction.Next)
        MediaWidgetUpdater.afterMediaControl(context, MediaControlAction.Next, feedbackSnapshot)
      }
      ActionPrevious -> {
        val feedbackSnapshot = MediaWidgetUpdater.feedbackSnapshot(context, MediaControlAction.Previous)
        MediaSessionReader.dispatch(context, MediaControlAction.Previous)
        MediaWidgetUpdater.afterMediaControl(context, MediaControlAction.Previous, feedbackSnapshot)
      }
      ActionRefresh -> MediaWidgetUpdater.updateAll(context)
    }
  }

  companion object {
    const val ActionTogglePlayPause = "com.pampa.widgets.widget.media.action.TOGGLE_PLAY_PAUSE"
    const val ActionNext = "com.pampa.widgets.widget.media.action.NEXT"
    const val ActionPrevious = "com.pampa.widgets.widget.media.action.PREVIOUS"
    const val ActionRefresh = "com.pampa.widgets.widget.media.action.REFRESH"
  }
}
