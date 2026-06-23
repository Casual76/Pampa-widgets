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

data class AppSettings(
  val themeMode: ThemeMode = ThemeMode.System,
  val dynamicColorEnabled: Boolean = true,
  val storeLayout: StoreLayout = StoreLayout.Grid,
  val widgetSortMode: WidgetSortMode = WidgetSortMode.Featured,
  val ignoredStableUpdateVersion: String = "",
  val automaticUpdateChecksEnabled: Boolean = true,
)
