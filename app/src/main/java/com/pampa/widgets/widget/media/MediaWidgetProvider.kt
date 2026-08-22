package com.pampa.widgets.widget.media

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.pampa.widgets.core.media.MediaControlAction

class MediaWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    MediaWidgetUpdateCoordinator.requestUpdate(context, MediaWidgetUpdateReason.WidgetLifecycle)
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle,
  ) {
    super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    MediaWidgetUpdateCoordinator.requestUpdate(context, MediaWidgetUpdateReason.Resize)
  }

  override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    MediaWidgetUpdateCoordinator.onWidgetsDeleted(appWidgetIds)
    super.onDeleted(context, appWidgetIds)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    when (intent.action) {
      ActionTogglePlayPause -> {
        val pendingResult = goAsync()
        MediaWidgetUpdateCoordinator.dispatchControl(context, MediaControlAction.TogglePlayPause) {
          pendingResult.finish()
        }
      }
      ActionNext -> {
        val pendingResult = goAsync()
        MediaWidgetUpdateCoordinator.dispatchControl(context, MediaControlAction.Next) {
          pendingResult.finish()
        }
      }
      ActionPrevious -> {
        val pendingResult = goAsync()
        MediaWidgetUpdateCoordinator.dispatchControl(context, MediaControlAction.Previous) {
          pendingResult.finish()
        }
      }
      ActionRefresh -> MediaWidgetUpdateCoordinator.requestUpdate(context, MediaWidgetUpdateReason.ManualRefresh)
      ActionNoOp -> Unit
    }
  }

  companion object {
    const val ActionTogglePlayPause = "com.pampa.widgets.widget.media.action.TOGGLE_PLAY_PAUSE"
    const val ActionNext = "com.pampa.widgets.widget.media.action.NEXT"
    const val ActionPrevious = "com.pampa.widgets.widget.media.action.PREVIOUS"
    const val ActionRefresh = "com.pampa.widgets.widget.media.action.REFRESH"
    const val ActionNoOp = "com.pampa.widgets.widget.media.action.NO_OP"
  }
}
