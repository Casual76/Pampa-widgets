package com.pampa.widgets.ui.navigation

object AppRoutes {
  const val Store = "store"
  const val Settings = "settings"
  const val WidgetDetail = "widget/{widgetId}"

  fun widgetDetail(widgetId: String): String = "widget/$widgetId"
}
