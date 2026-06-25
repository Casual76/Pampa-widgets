package com.pampa.widgets.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "pampa_widgets_settings",
)

@Singleton
class DataStoreSettingsRepository @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : SettingsRepository by PreferencesSettingsRepository(context.appSettingsDataStore)

internal class PreferencesSettingsRepository(
  private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
  override fun observeSettings(): Flow<AppSettings> {
    return dataStore.data
      .catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
      }
      .map { preferences -> preferences.toAppSettings() }
  }

  override suspend fun setThemeMode(mode: ThemeMode) {
    dataStore.edit { it[Keys.ThemeMode] = mode.name }
  }

  override suspend fun setDynamicColorEnabled(enabled: Boolean) {
    dataStore.edit { it[Keys.DynamicColor] = enabled }
  }

  override suspend fun setStoreLayout(layout: StoreLayout) {
    dataStore.edit { it[Keys.StoreLayout] = layout.name }
  }

  override suspend fun setWidgetSortMode(mode: WidgetSortMode) {
    dataStore.edit { it[Keys.WidgetSortMode] = mode.name }
  }

  override suspend fun setIgnoredStableUpdateVersion(version: String) {
    dataStore.edit { it[Keys.IgnoredStableUpdateVersion] = version }
  }

  override suspend fun setAutomaticUpdateChecksEnabled(enabled: Boolean) {
    dataStore.edit { it[Keys.AutomaticUpdateChecks] = enabled }
  }

  override suspend fun setMediaWidgetTheme(theme: MediaWidgetTheme) {
    dataStore.edit { it[Keys.MediaWidgetTheme] = theme.name }
  }

  override suspend fun setMediaWidgetArtworkSize(size: MediaWidgetArtworkSize) {
    dataStore.edit { it[Keys.MediaWidgetArtworkSize] = size.name }
  }

  override suspend fun setMediaWidgetShowSource(enabled: Boolean) {
    dataStore.edit { it[Keys.MediaWidgetShowSource] = enabled }
  }

  override suspend fun setMediaWidgetShowArtist(enabled: Boolean) {
    dataStore.edit { it[Keys.MediaWidgetShowArtist] = enabled }
  }

  override suspend fun setMediaWidgetKeepLastSong(enabled: Boolean) {
    dataStore.edit { it[Keys.MediaWidgetKeepLastSong] = enabled }
  }

  override suspend fun setMediaWidgetInstantControls(enabled: Boolean) {
    dataStore.edit { it[Keys.MediaWidgetInstantControls] = enabled }
  }

  override suspend fun setMediaWidgetAnimatedFeedback(enabled: Boolean) {
    dataStore.edit { it[Keys.MediaWidgetAnimatedFeedback] = enabled }
  }

  internal object Keys {
    val ThemeMode = stringPreferencesKey("theme_mode")
    val DynamicColor = booleanPreferencesKey("dynamic_color")
    val StoreLayout = stringPreferencesKey("store_layout")
    val WidgetSortMode = stringPreferencesKey("widget_sort_mode")
    val IgnoredStableUpdateVersion = stringPreferencesKey("ignored_stable_update_version")
    val AutomaticUpdateChecks = booleanPreferencesKey("automatic_update_checks")
    val MediaWidgetTheme = stringPreferencesKey("media_widget_theme")
    val MediaWidgetArtworkSize = stringPreferencesKey("media_widget_artwork_size")
    val MediaWidgetShowSource = booleanPreferencesKey("media_widget_show_source")
    val MediaWidgetShowArtist = booleanPreferencesKey("media_widget_show_artist")
    val MediaWidgetKeepLastSong = booleanPreferencesKey("media_widget_keep_last_song")
    val MediaWidgetInstantControls = booleanPreferencesKey("media_widget_instant_controls")
    val MediaWidgetAnimatedFeedback = booleanPreferencesKey("media_widget_animated_feedback")
  }
}

object AppSettingsSnapshotReader {
  fun readBlocking(context: Context): AppSettings {
    return runCatching {
      runBlocking(Dispatchers.IO) {
        context.appSettingsDataStore.data.first().toAppSettings()
      }
    }.getOrDefault(AppSettings())
  }
}

private fun Preferences.toAppSettings(): AppSettings {
  return AppSettings(
    themeMode = this[PreferencesSettingsRepository.Keys.ThemeMode].toEnumOrDefault(ThemeMode.System),
    dynamicColorEnabled = this[PreferencesSettingsRepository.Keys.DynamicColor] ?: true,
    storeLayout = this[PreferencesSettingsRepository.Keys.StoreLayout].toEnumOrDefault(StoreLayout.Grid),
    widgetSortMode = this[PreferencesSettingsRepository.Keys.WidgetSortMode].toEnumOrDefault(WidgetSortMode.Featured),
    ignoredStableUpdateVersion = this[PreferencesSettingsRepository.Keys.IgnoredStableUpdateVersion].orEmpty(),
    automaticUpdateChecksEnabled = this[PreferencesSettingsRepository.Keys.AutomaticUpdateChecks] ?: true,
    mediaWidgetTheme = this[PreferencesSettingsRepository.Keys.MediaWidgetTheme].toEnumOrDefault(
      MediaWidgetTheme.SamsungGlass,
    ),
    mediaWidgetArtworkSize = this[PreferencesSettingsRepository.Keys.MediaWidgetArtworkSize].toEnumOrDefault(
      MediaWidgetArtworkSize.Large,
    ),
    mediaWidgetShowSource = this[PreferencesSettingsRepository.Keys.MediaWidgetShowSource] ?: false,
    mediaWidgetShowArtist = this[PreferencesSettingsRepository.Keys.MediaWidgetShowArtist] ?: true,
    mediaWidgetKeepLastSong = this[PreferencesSettingsRepository.Keys.MediaWidgetKeepLastSong] ?: true,
    mediaWidgetInstantControls = this[PreferencesSettingsRepository.Keys.MediaWidgetInstantControls] ?: false,
    mediaWidgetAnimatedFeedback = this[PreferencesSettingsRepository.Keys.MediaWidgetAnimatedFeedback] ?: false,
  )
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T {
  if (isNullOrBlank()) return default
  return enumValues<T>().firstOrNull { it.name == this } ?: default
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
  @Binds
  abstract fun bindSettingsRepository(
    implementation: DataStoreSettingsRepository,
  ): SettingsRepository
}
