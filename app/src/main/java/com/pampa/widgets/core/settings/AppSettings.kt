package com.pampa.widgets.core.settings

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

enum class MediaWidgetTheme {
  SamsungGlass,
  AdaptiveGlass,
  LightGlass,
  DarkGlass,
  AlbumColor,
}

enum class MediaWidgetArtworkSize {
  Compact,
  Balanced,
  Large,
}

data class AppSettings(
  val themeMode: ThemeMode = ThemeMode.System,
  val dynamicColorEnabled: Boolean = true,
  val storeLayout: StoreLayout = StoreLayout.Grid,
  val widgetSortMode: WidgetSortMode = WidgetSortMode.Featured,
  val ignoredStableUpdateVersion: String = "",
  val automaticUpdateChecksEnabled: Boolean = true,
  val mediaWidgetTheme: MediaWidgetTheme = MediaWidgetTheme.SamsungGlass,
  val mediaWidgetArtworkSize: MediaWidgetArtworkSize = MediaWidgetArtworkSize.Balanced,
  val mediaWidgetBlurBackground: Boolean = true,
  val mediaWidgetShowSource: Boolean = true,
  val mediaWidgetShowArtist: Boolean = true,
  val mediaWidgetKeepLastSong: Boolean = true,
  val mediaWidgetInstantControls: Boolean = true,
  val mediaWidgetAnimatedFeedback: Boolean = false,
)
