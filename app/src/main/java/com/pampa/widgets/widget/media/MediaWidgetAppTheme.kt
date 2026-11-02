package com.pampa.widgets.widget.media

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.pampa.widgets.core.design.PampaBrand
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.toEngineSettings
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.theme.fluidColorScheme

/**
 * I colori del widget quando segue l'app (`MediaWidgetTheme.AppTheme`).
 *
 * La tavolozza e' quella che l'engine da' all'app — `fluidColorScheme` con le stesse impostazioni e lo
 * stesso marchio del tema — quindi un accento cambiato in Aspetto cambia anche il widget, e il chiaro
 * e lo scuro sono quelli dell'app: fissati se l'app li fissa, del telefono se l'app segue il sistema.
 * Un widget non ha una composizione da cui chiederli: `RemoteViews` si consegna a colori gia' decisi,
 * e il cambio di tema del telefono lo ridisegna il servizio delle notifiche (vedi
 * `MediaNotificationListenerService.onConfigurationChanged`).
 */
internal fun mediaWidgetAppTokens(context: Context, settings: AppSettings): MediaWidgetColorTokens {
  val engine = settings.toEngineSettings()
  val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
    Configuration.UI_MODE_NIGHT_YES
  val isDark = widgetIsDark(engine, systemDark)
  val dynamic = if (
    engine.dynamicColorEnabled &&
    engine.accentMode == AccentMode.DYNAMIC &&
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  ) {
    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
  } else {
    null
  }
  val scheme = fluidColorScheme(settings = engine, isDark = isDark, brand = PampaBrand, dynamicScheme = dynamic)
  return mediaWidgetAppTokens(scheme, isDark)
}

/** Chiaro o scuro, con la stessa regola di `FluidTheme`: un tema fissato nell'app vince sul telefono. */
internal fun widgetIsDark(settings: EngineSettings, systemDark: Boolean): Boolean = when (settings.themeMode) {
  ThemeMode.SYSTEM -> systemDark
  ThemeMode.LIGHT -> false
  ThemeMode.DARK, ThemeMode.AMOLED -> true
}

/**
 * I ruoli della tavolozza sui pezzi del widget. Le coppie restano coppie (`primary`/`onPrimary` per
 * il tasto play, `onSurface` sulle superfici), come in una schermata dell'app: il widget sembra un
 * pezzo dell'app perche' ne usa i ruoli, non perche' ne copia un colore.
 */
internal fun mediaWidgetAppTokens(scheme: ColorScheme, isDark: Boolean): MediaWidgetColorTokens {
  val disabledSurface = scheme.surfaceContainerHigh
  return MediaWidgetColorTokens(
    backgroundColor = scheme.surfaceContainer.toArgb(),
    darkSurface = isDark,
    primaryTextColor = scheme.onSurface.toArgb(),
    secondaryTextColor = scheme.onSurfaceVariant.toArgb(),
    controlIconColor = scheme.onSurface.toArgb(),
    disabledControlIconColor = scheme.onSurface.copy(alpha = 0.38f).compositeOver(disabledSurface).toArgb(),
    playIconColor = scheme.onPrimary.toArgb(),
    controlSurfaceColor = scheme.surfaceContainerHighest.toArgb(),
    disabledControlSurfaceColor = disabledSurface.toArgb(),
    playSurfaceColor = scheme.primary.toArgb(),
    sourcePillColor = scheme.secondaryContainer.toArgb(),
    artworkFrameColor = scheme.surfaceContainerHigh.toArgb(),
    progressColor = scheme.primary.toArgb(),
    progressTrackColor = scheme.surfaceContainerHighest.toArgb(),
  )
}

/**
 * Cosa del tema entra nella firma di un fotogramma del widget: se cambia, il widget si ridisegna
 * anche con lo stesso brano. Senza, un accento o un tema cambiati nell'app verrebbero scartati come
 * "gia' disegnato" fino al brano dopo.
 */
internal fun MediaWidgetColorTokens.appearanceSignature(): String =
  "$darkSurface:$backgroundColor:$playSurfaceColor:$primaryTextColor:$sourcePillColor"
