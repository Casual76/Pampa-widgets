package com.pampa.widgets.widget.media

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.pampa.widgets.core.media.MediaSessionReader
import com.pampa.widgets.core.media.NotificationListenerAccess
import com.pampa.widgets.core.settings.MediaWidgetSettingsRepository
import kotlinx.coroutines.launch

/** Resolve saved source at tap time, including taps delivered from an old RemoteViews. */
class MediaWidgetLaunchActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleScope.launch {
      try {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val ids = AppWidgetManager.getInstance(this@MediaWidgetLaunchActivity)
          .getAppWidgetIds(ComponentName(this@MediaWidgetLaunchActivity, MediaWidgetProvider::class.java))
        if (id !in ids) return@launch
        if (!NotificationListenerAccess.isGranted(this@MediaWidgetLaunchActivity)) {
          startActivity(NotificationListenerAccess.settingsIntent())
          return@launch
        }
        val source = MediaWidgetSettingsRepository(this@MediaWidgetLaunchActivity).read(id).source
        val pkg = source.packageName ?: intent.getStringExtra("shownPackage").orEmpty()
        if (pkg.isBlank()) return@launch
        val activity = MediaSessionReader.sessionActivity(this@MediaWidgetLaunchActivity, pkg)
        val sent = activity != null && runCatching { activity.send() }.isSuccess
        if (!sent) packageManager.getLaunchIntentForPackage(pkg)?.let {
          startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
      } finally { finish() }
    }
  }
}
