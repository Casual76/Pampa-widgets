package com.pampa.widgets.core.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.toEngineSettings
import dev.antigravity.fluidengine.ui.theme.AccentPreset
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * Il verde di Pampa Widgets, nelle due versioni che gli servono.
 *
 * Non e' lo stesso colore usato due volte: su fondo chiaro e su fondo scuro lo stesso RGB non
 * mantiene ne' il carattere ne' il contrasto. Da questa coppia l'engine deriva l'intera scala di
 * superfici, quindi cambiare qui cambia tutta l'app in modo coerente — e il widget, quando segue
 * l'app (`MediaWidgetTheme.AppTheme`), perche' prende la tavolozza dallo stesso marchio.
 */
val PampaBrand = AccentPreset(
  name = "pampa",
  label = "Pampa",
  light = Color(0xFF146C5A),
  dark = Color(0xFF9AD7C2),
)

/**
 * Il tema dell'app, costruito sul Fluid Engine.
 *
 * La firma resta quella di sempre: i punti di chiamata passano le impostazioni dell'app, e la
 * traduzione in [dev.antigravity.fluidengine.foundation.EngineSettings] (tema, nero, accento,
 * colore dinamico, aptica) sta in `toEngineSettings`, la stessa che usano la sezione «Aspetto» e il
 * widget.
 */
@Composable
fun PampaWidgetsTheme(
  settings: AppSettings,
  content: @Composable () -> Unit,
) {
  val engineSettings = remember(settings) { settings.toEngineSettings() }
  FluidTheme(
    settings = engineSettings,
    brand = PampaBrand,
    content = content,
  )
}
