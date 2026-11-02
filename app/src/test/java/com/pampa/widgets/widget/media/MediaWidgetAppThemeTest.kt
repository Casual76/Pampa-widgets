package com.pampa.widgets.widget.media

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.pampa.widgets.core.design.PampaBrand
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.ThemeMode
import com.pampa.widgets.core.settings.toEngineSettings
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode as EngineThemeMode
import dev.antigravity.fluidengine.ui.theme.fluidAccentPresets
import dev.antigravity.fluidengine.ui.theme.fluidColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** Il widget «Come l'app»: la tavolozza dell'app, chiara o scura come lei, e leggibile. */
class MediaWidgetAppThemeTest {
  private fun contrast(a: Int, b: Int): Float {
    val la = Color(a).luminance()
    val lb = Color(b).luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
  }

  private fun tokens(engine: EngineSettings, dark: Boolean) =
    mediaWidgetAppTokens(fluidColorScheme(engine, dark, PampaBrand), dark)

  @Test fun everyAccentAndSideIsOpaqueAndReadable() {
    val accents = listOf(EngineSettings(accentMode = AccentMode.BRAND)) +
      fluidAccentPresets.map { EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = it.name) }
    accents.forEach { base ->
      listOf(false, true).forEach { dark ->
        listOf(false, true).forEach { black ->
          val t = tokens(base.copy(amoledEnabled = black), dark)
          val label = "${base.accentMode}/${base.customAccentName} dark=$dark black=$black"
          listOf(t.backgroundColor, t.playSurfaceColor, t.controlSurfaceColor, t.primaryTextColor).forEach {
            assertEquals("$label opaque", 0xFF, (it ushr 24) and 0xFF)
          }
          assertEquals(dark, t.darkSurface)
          assertTrue("$label title ${contrast(t.primaryTextColor, t.backgroundColor)}", contrast(t.primaryTextColor, t.backgroundColor) >= 4.5f)
          assertTrue("$label play glyph", contrast(t.playIconColor, t.playSurfaceColor) >= 3f)
          assertTrue("$label control glyph", contrast(t.controlIconColor, t.controlSurfaceColor) >= 3f)
        }
      }
    }
  }

  @Test fun lightAndDarkAreDifferentWidgets() {
    val engine = EngineSettings(accentMode = AccentMode.BRAND)
    val light = tokens(engine, dark = false)
    val dark = tokens(engine, dark = true)
    assertTrue(Color(light.backgroundColor).luminance() > Color(dark.backgroundColor).luminance())
    assertNotEquals(light.appearanceSignature(), dark.appearanceSignature())
  }

  @Test fun theAccentReachesThePlayButtonAndTheSignature() {
    val green = tokens(EngineSettings(accentMode = AccentMode.BRAND), dark = false)
    val orange = tokens(EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "ember"), dark = false)
    assertNotEquals(green.playSurfaceColor, orange.playSurfaceColor)
    assertNotEquals(green.appearanceSignature(), orange.appearanceSignature())
  }

  @Test fun aThemeFixedInTheAppWinsOverThePhone() {
    assertTrue(widgetIsDark(EngineSettings(themeMode = EngineThemeMode.SYSTEM), systemDark = true))
    assertFalse(widgetIsDark(EngineSettings(themeMode = EngineThemeMode.SYSTEM), systemDark = false))
    assertFalse(widgetIsDark(EngineSettings(themeMode = EngineThemeMode.LIGHT), systemDark = true))
    assertTrue(widgetIsDark(EngineSettings(themeMode = EngineThemeMode.DARK), systemDark = false))
    assertTrue(widgetIsDark(EngineSettings(themeMode = EngineThemeMode.AMOLED), systemDark = false))
    assertTrue(widgetIsDark(AppSettings(themeMode = ThemeMode.Dark).toEngineSettings(), systemDark = false))
  }
}
