package com.pampa.widgets.widget.media

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pampa.widgets.core.media.MediaPlaybackAvailability
import com.pampa.widgets.core.media.MediaPlaybackSnapshot
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.MediaWidgetSettings

/**
 * L'anteprima del widget con lo stesso renderer `RemoteViews` della home, alla misura del launcher.
 *
 * [appSettings] serve al tema «Come l'app»: i suoi colori vengono dall'aspetto dell'app, e senza
 * l'anteprima mostrerebbe quelli di serie.
 */
@Composable
internal fun MediaWidgetPreview(settings: MediaWidgetSettings, widthDp: Int = 320, heightDp: Int = 230,
  snapshot: MediaPlaybackSnapshot? = null, appSettings: AppSettings = AppSettings()) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val width = widthDp.coerceAtLeast(100)
    val height = heightDp.coerceAtLeast(100)
    val scale = (maxWidth.value / width).coerceAtMost(1f)
    AndroidView(factory = { PreviewHost(it) }, modifier = Modifier.fillMaxWidth().height((height * scale).dp),
      update = { host ->
        val sample = snapshot ?: MediaPlaybackSnapshot(MediaPlaybackAvailability.Active,
          title = "La tua musica", artist = "Il prossimo brano preferito", sourceLabel = settings.source.label,
          packageName = settings.source.packageName.orEmpty(), isPlaying = true,
          canPlayPause = true, canSkipNext = true, canSkipPrevious = true, durationMs = 222000, positionMs = 74000)
        val rendered = settings.applyTo(appSettings)
        val key = listOf(rendered, width, height, sample, host.resources.configuration.uiMode)
        if (host.tag != key) {
          host.tag = key
          host.removeAllViews()
          host.targetWidth = (width * host.resources.displayMetrics.density).toInt()
          host.targetHeight = (height * host.resources.displayMetrics.density).toInt()
          host.addView(MediaWidgetUpdater.buildRemoteViewsForTest(host.context, sample, rendered, width, height).apply(host.context, host))
          host.requestLayout()
        }
      })
  }
}

private class PreviewHost(context: Context) : FrameLayout(context) {
  var targetWidth = 1
  var targetHeight = 1
  init { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
  override fun onInterceptTouchEvent(event: android.view.MotionEvent?) = true
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    getChildAt(0)?.measure(MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY))
  }
  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    getChildAt(0)?.let { child ->
      val scale = minOf(width.toFloat() / targetWidth, height.toFloat() / targetHeight, 1f)
      child.pivotX = 0f; child.pivotY = 0f; child.scaleX = scale; child.scaleY = scale
      val inset = ((width - targetWidth * scale) / 2).toInt()
      child.layout(inset, 0, inset + targetWidth, targetHeight)
    }
  }
}
