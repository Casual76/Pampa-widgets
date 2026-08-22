package com.pampa.widgets.widget.media

import android.app.Notification
import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pampa.widgets.core.media.MediaSessionReader
import com.pampa.widgets.core.media.isSupportedMusicPackage

class MediaNotificationListenerService : NotificationListenerService() {
  private var mediaSessionManager: MediaSessionManager? = null
  private var isSessionCallbackRegistered = false
  private var observedController: MediaController? = null

  private val controllerCallback = object : MediaController.Callback() {
    override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
      MediaWidgetUpdateCoordinator.requestUpdate(applicationContext, MediaWidgetUpdateReason.MediaSignal)
    }

    override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
      MediaWidgetUpdateCoordinator.requestUpdate(applicationContext, MediaWidgetUpdateReason.MediaSignal)
    }

    override fun onSessionDestroyed() {
      refreshObservedController()
      MediaWidgetUpdateCoordinator.requestUpdate(applicationContext, MediaWidgetUpdateReason.MediaSignal)
    }
  }

  private val activeSessionsChangedListener =
    MediaSessionManager.OnActiveSessionsChangedListener {
      refreshObservedController()
      MediaWidgetUpdateCoordinator.requestUpdate(applicationContext, MediaWidgetUpdateReason.MediaSignal)
    }

  override fun onCreate() {
    super.onCreate()
    mediaSessionManager = getSystemService(MediaSessionManager::class.java)
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    registerSessionCallback()
    refreshObservedController()
    MediaWidgetUpdateCoordinator.requestUpdate(applicationContext, MediaWidgetUpdateReason.MediaSignal)
  }

  override fun onListenerDisconnected() {
    clearObservedController()
    unregisterSessionCallback()
    MediaWidgetUpdateCoordinator.requestUpdate(applicationContext, MediaWidgetUpdateReason.MediaSignal)
    super.onListenerDisconnected()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn?.isRelevantMediaSignal() == true) {
      refreshObservedController()
      MediaWidgetUpdateCoordinator.requestUpdate(applicationContext, MediaWidgetUpdateReason.MediaSignal)
    }
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    if (sbn?.isRelevantMediaSignal() == true) {
      refreshObservedController()
      MediaWidgetUpdateCoordinator.requestUpdate(applicationContext, MediaWidgetUpdateReason.MediaSignal)
    }
  }

  override fun onDestroy() {
    clearObservedController()
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

  private fun refreshObservedController() {
    val next = MediaSessionReader.currentController(applicationContext)
    val current = observedController
    if (current?.sessionToken == next?.sessionToken) return
    current?.unregisterCallback(controllerCallback)
    observedController = next
    next?.registerCallback(controllerCallback)
  }

  private fun clearObservedController() {
    observedController?.unregisterCallback(controllerCallback)
    observedController = null
  }

  private fun StatusBarNotification.isRelevantMediaSignal(): Boolean {
    val mediaNotification = notification
    return isSupportedMusicPackage(packageName) ||
      mediaNotification.category == Notification.CATEGORY_TRANSPORT ||
      mediaNotification.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
  }
}
