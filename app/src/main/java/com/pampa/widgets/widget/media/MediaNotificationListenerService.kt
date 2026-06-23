package com.pampa.widgets.widget.media

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaNotificationListenerService : NotificationListenerService() {
  private var mediaSessionManager: MediaSessionManager? = null
  private var isSessionCallbackRegistered = false

  private val activeSessionsChangedListener =
    MediaSessionManager.OnActiveSessionsChangedListener {
      MediaWidgetUpdater.updateAll(applicationContext)
    }

  override fun onCreate() {
    super.onCreate()
    mediaSessionManager = getSystemService(MediaSessionManager::class.java)
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    registerSessionCallback()
    MediaWidgetUpdater.updateAll(applicationContext)
  }

  override fun onListenerDisconnected() {
    unregisterSessionCallback()
    MediaWidgetUpdater.updateAll(applicationContext)
    super.onListenerDisconnected()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    MediaWidgetUpdater.updateAll(applicationContext)
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    MediaWidgetUpdater.updateAll(applicationContext)
  }

  override fun onDestroy() {
    unregisterSessionCallback()
    super.onDestroy()
  }

  private fun registerSessionCallback() {
    if (isSessionCallbackRegistered) return
    val listenerComponent = ComponentName(this, MediaNotificationListenerService::class.java)
    runCatching {
      mediaSessionManager?.addOnActiveSessionsChangedListener(
        activeSessionsChangedListener,
        listenerComponent,
      )
      isSessionCallbackRegistered = true
    }
  }

  private fun unregisterSessionCallback() {
    if (!isSessionCallbackRegistered) return
    runCatching {
      mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
    }
    isSessionCallbackRegistered = false
  }
}
