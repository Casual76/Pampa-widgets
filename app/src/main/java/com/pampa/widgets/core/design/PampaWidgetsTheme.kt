package com.pampa.widgets.core.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.ThemeMode
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.foundation.ThemeMode as EngineThemeMode

/**
 * Il verde di Pampa Widgets, nelle due versioni che gli servono.
 *
 * Non e' lo stesso colore usato due volte: su fondo chiaro e su fondo scuro lo stesso RGB non
 * mantiene ne' il carattere ne' il contrasto. Da questa coppia l'engine deriva l'intera scala di
 * superfici, quindi cambiare qui cambia tutta l'app in modo coerente.
 */
private val PampaBrand = AccentPreset(
  name = "pampa",
  label = "Pampa",
  light = Color(0xFF146C5A),
  dark = Color(0xFF9AD7C2),
)

/**
 * Il tema dell'app, ora costruito sul Fluid Engine.
 *
 * La firma resta identica di proposito: i punti di chiamata non sanno che sotto e' cambiato tutto, e
 * tornare indietro e' un `git checkout` di questo solo file. Quello che arriva in cambio della
 * palette scritta a mano che c'era prima: angoli continui, la scala tipografica iOS, il motion
 * scheme condiviso, AMOLED e la stessa identita' visiva delle altre app costruite sull'engine.
 */
@Composable
fun PampaWidgetsTheme(
  settings: AppSettings,
  content: @Composable () -> Unit,
) {
  FluidTheme(
    settings = EngineSettings(
      themeMode = settings.themeMode.toEngine(),
      // L'engine tiene separati "quale sorgente per l'accento" e "il colore dinamico e' permesso":
      // con il dinamico spento si torna al verde dell'app invece che a un preset qualsiasi.
      accentMode = if (settings.dynamicColorEnabled) AccentMode.DYNAMIC else AccentMode.BRAND,
      dynamicColorEnabled = settings.dynamicColorEnabled,
    ),
    brand = PampaBrand,
    content = content,
  )
}

private fun ThemeMode.toEngine(): EngineThemeMode = when (this) {
  ThemeMode.System -> EngineThemeMode.SYSTEM
  ThemeMode.Light -> EngineThemeMode.LIGHT
  ThemeMode.Dark -> EngineThemeMode.DARK
}
