package com.pampa.widgets.core.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
  fun observeSettings(): Flow<AppSettings>

  suspend fun setThemeMode(mode: ThemeMode)
  suspend fun setDynamicColorEnabled(enabled: Boolean)
  suspend fun setStoreLayout(layout: StoreLayout)
  suspend fun setWidgetSortMode(mode: WidgetSortMode)
  suspend fun setIgnoredStableUpdateVersion(version: String)
  suspend fun setAutomaticUpdateChecksEnabled(enabled: Boolean)
}
