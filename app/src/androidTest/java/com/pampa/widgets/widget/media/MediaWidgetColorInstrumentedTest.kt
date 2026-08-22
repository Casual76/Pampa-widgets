package com.pampa.widgets.widget.media

import android.graphics.Color
import com.pampa.widgets.core.settings.MediaWidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaWidgetColorInstrumentedTest {
  private val sampleAccents = listOf(
    Color.rgb(28, 54, 108),
    Color.rgb(58, 162, 96),
    Color.rgb(204, 88, 46),
    Color.rgb(220, 210, 86),
    Color.rgb(102, 56, 150),
  )

  @Test
  fun backgroundAndPlayButtonAreOpaqueAndTonallySeparated() {
    MediaWidgetTheme.entries.forEach { theme ->
      sampleAccents.forEach { accent ->
        val background = accent.toWidgetBackgroundColor(theme)
        val darkSurface = when (theme) {
          MediaWidgetTheme.DarkGlass -> true
          MediaWidgetTheme.LightGlass -> false
          else -> background.luminance() < 0.48f
        }
        val play = background.playButtonColor(darkSurface)

        assertEquals("$theme background alpha", 255, Color.alpha(background))
        assertEquals("$theme play alpha", 255, Color.alpha(play))
        if (darkSurface) {
          assertTrue("$theme play should be lighter on dark backgrounds", play.luminance() > background.luminance())
        } else {
          assertTrue("$theme play should be darker on light backgrounds", play.luminance() < background.luminance())
        }
      }
    }
  }
}
