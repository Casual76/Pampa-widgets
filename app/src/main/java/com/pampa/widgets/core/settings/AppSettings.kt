package com.pampa.widgets.core.settings

import dev.antigravity.fluidengine.foundation.AccentMode

enum class ThemeMode {
  System,
  Light,
  Dark,
}

enum class StoreLayout {
  Grid,
  List,
}

enum class WidgetSortMode {
  Featured,
  Name,
}

@kotlinx.serialization.Serializable
enum class MediaWidgetTheme {
  SamsungGlass,
  AdaptiveGlass,
  LightGlass,
  DarkGlass,
  AlbumColor,

  /**
   * I colori dell'app: chiaro o scuro come l'app (o come il telefono, se l'app segue il sistema) e
   * l'accento scelto in Aspetto. In coda all'enum perche' i valori si salvano per nome e un'istanza
   * salvata prima della 0.5.0 deve leggersi com'era.
   */
  AppTheme,
}

@kotlinx.serialization.Serializable
enum class MediaWidgetArtworkSize {
  Compact,
  Balanced,
  Large,
}

data class AppSettings(
  val themeMode: ThemeMode = ThemeMode.System,
  val dynamicColorEnabled: Boolean = true,
  /**
   * Da dove viene l'accento. Fino alla 0.4.x non si salvava: lo decideva [dynamicColorEnabled]
   * (acceso = colori di sistema, spento = il verde dell'app), e chi aggiorna lo ritrova cosi'.
   */
  val accentMode: AccentMode = AccentMode.DYNAMIC,
  val customAccentName: String = DefaultAccentPresetName,
  val pureBlackEnabled: Boolean = false,
  val hapticsEnabled: Boolean = true,
  val storeLayout: StoreLayout = StoreLayout.Grid,
  val widgetSortMode: WidgetSortMode = WidgetSortMode.Featured,
  val ignoredStableUpdateVersion: String = "",
  val automaticUpdateChecksEnabled: Boolean = true,
  val mediaWidgetTheme: MediaWidgetTheme = MediaWidgetTheme.SamsungGlass,
  val mediaWidgetArtworkSize: MediaWidgetArtworkSize = MediaWidgetArtworkSize.Large,
  val mediaWidgetShowSource: Boolean = false,
  val mediaWidgetShowArtist: Boolean = true,
  val mediaWidgetKeepLastSong: Boolean = true,
  val mediaWidgetInstantControls: Boolean = false,
  val mediaWidgetAnimatedFeedback: Boolean = true,
)

/** Il preset che l'engine sceglie quando un nome non c'e': quello di [EngineSettings] di serie. */
internal const val DefaultAccentPresetName = "fluid"
