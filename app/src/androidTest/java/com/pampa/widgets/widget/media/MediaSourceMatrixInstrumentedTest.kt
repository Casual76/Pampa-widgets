package com.pampa.widgets.widget.media

import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.pampa.widgets.R
import com.pampa.widgets.core.media.MediaPlaybackAvailability
import com.pampa.widgets.core.media.MediaPlaybackSnapshot
import com.pampa.widgets.core.settings.MediaSourceMode
import com.pampa.widgets.core.settings.MediaWidgetSettings
import org.junit.Assert.*
import org.junit.Test

class MediaSourceMatrixInstrumentedTest {
  @Test fun allSourcesAndEmptyStatesApplyAtEveryLauncherSize() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    var count = 0
    instrumentation.runOnMainSync {
      for (mode in MediaSourceMode.entries) for (state in MediaPlaybackAvailability.entries) {
        for ((width, height) in listOf(110 to 110, 220 to 170, 300 to 210, 430 to 220, 320 to 320, 520 to 620)) {
          val snapshot = MediaPlaybackSnapshot(state, title = "${mode.label} $state", artist = "Stato di prova",
            sourceLabel = mode.label, packageName = mode.packageName.orEmpty(),
            canPlayPause = state == MediaPlaybackAvailability.Active)
          val root = MediaWidgetUpdater.buildRemoteViewsForTest(context, snapshot,
            MediaWidgetSettings(source = mode, showSource = true).applyTo(), width, height).apply(context, FrameLayout(context))
          val density = context.resources.displayMetrics.density
          root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
          root.layout(0, 0, root.measuredWidth, root.measuredHeight)
          assertEquals(snapshot.title, root.findViewById<TextView>(R.id.media_widget_title).text.toString())
          assertEquals(mode.label, root.findViewById<TextView>(R.id.media_widget_source).text.toString())
          assertEquals(state == MediaPlaybackAvailability.Active || state == MediaPlaybackAvailability.PermissionRequired,
            root.findViewById<View>(R.id.media_widget_play_pause).isEnabled)
          count++
        }
      }
    }
    assertEquals(72, count)
  }
}
