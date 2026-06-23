package com.pampa.widgets.core.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.pampa.widgets.widget.media.MediaNotificationListenerService

object NotificationListenerAccess {
  fun isGranted(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
      context.contentResolver,
      "enabled_notification_listeners",
    ).orEmpty()
    if (enabledListeners.isBlank()) return false

    val expectedComponent = ComponentName(
      context,
      MediaNotificationListenerService::class.java,
    ).flattenToString()
    return enabledListeners
      .split(':')
      .any { entry ->
        entry.equals(expectedComponent, ignoreCase = true) ||
          entry.startsWith("${context.packageName}/", ignoreCase = true)
      }
  }

  fun settingsIntent(): Intent {
    return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}
