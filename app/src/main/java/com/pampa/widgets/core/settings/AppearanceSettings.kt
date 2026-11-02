package com.pampa.widgets.core.settings

import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode as EngineThemeMode

/**
 * L'aspetto dell'app nei termini dell'engine.
 *
 * Le impostazioni restano quelle dell'app (le chiavi di DataStore non cambiano nome, e un'app che
 * aggiorna le ritrova): qui c'e' solo la traduzione, in un posto e senza Compose, perche' la usano in
 * tre — il tema, la sezione «Aspetto» e il widget quando segue l'app.
 */
fun AppSettings.toEngineSettings(): EngineSettings = EngineSettings(
  themeMode = when (themeMode) {
    ThemeMode.System -> EngineThemeMode.SYSTEM
    ThemeMode.Light -> EngineThemeMode.LIGHT
    ThemeMode.Dark -> EngineThemeMode.DARK
  },
  accentMode = accentMode,
  customAccentName = customAccentName,
  dynamicColorEnabled = dynamicColorEnabled,
  amoledEnabled = pureBlackEnabled,
  hapticsEnabled = hapticsEnabled,
)

/**
 * Il contrario: quello che la sezione «Aspetto» ha scelto, nei campi dell'app.
 *
 * `AMOLED` non e' un quarto tema ma «scuro col nero vero»: diventa [ThemeMode.Dark] piu'
 * [AppSettings.pureBlackEnabled], la stessa tavolozza.
 */
fun AppSettings.withEngineSettings(engine: EngineSettings): AppSettings = copy(
  themeMode = when (engine.themeMode) {
    EngineThemeMode.SYSTEM -> ThemeMode.System
    EngineThemeMode.LIGHT -> ThemeMode.Light
    EngineThemeMode.DARK, EngineThemeMode.AMOLED -> ThemeMode.Dark
  },
  pureBlackEnabled = engine.amoledEnabled || engine.themeMode == EngineThemeMode.AMOLED,
  accentMode = engine.accentMode,
  customAccentName = engine.customAccentName,
  dynamicColorEnabled = engine.dynamicColorEnabled,
  hapticsEnabled = engine.hapticsEnabled,
)

/**
 * L'accento di chi arriva da una versione che non lo salvava.
 *
 * Fino alla 0.4.x c'era solo l'interruttore «Colori dinamici»: acceso, il tema prendeva i colori di
 * sistema; spento, il verde dell'app. Un accento salvato (dalla 0.5.0) vince sempre.
 */
internal fun resolveAccentMode(storedName: String?, dynamicColorEnabled: Boolean): AccentMode =
  AccentMode.entries.firstOrNull { it.name == storedName }
    ?: if (dynamicColorEnabled) AccentMode.DYNAMIC else AccentMode.BRAND
