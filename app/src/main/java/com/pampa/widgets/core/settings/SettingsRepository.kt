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
  suspend fun setMediaWidgetTheme(theme: MediaWidgetTheme)
  suspend fun setMediaWidgetArtworkSize(size: MediaWidgetArtworkSize)
  suspend fun setMediaWidgetShowSource(enabled: Boolean)
  suspend fun setMediaWidgetShowArtist(enabled: Boolean)
  suspend fun setMediaWidgetKeepLastSong(enabled: Boolean)
  suspend fun setMediaWidgetInstantControls(enabled: Boolean)
  suspend fun setMediaWidgetAnimatedFeedback(enabled: Boolean)
}
