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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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
      .map { preferences ->
        AppSettings(
          themeMode = preferences[Keys.ThemeMode].toEnumOrDefault(ThemeMode.System),
          dynamicColorEnabled = preferences[Keys.DynamicColor] ?: true,
          storeLayout = preferences[Keys.StoreLayout].toEnumOrDefault(StoreLayout.Grid),
          widgetSortMode = preferences[Keys.WidgetSortMode].toEnumOrDefault(WidgetSortMode.Featured),
          ignoredStableUpdateVersion = preferences[Keys.IgnoredStableUpdateVersion].orEmpty(),
          automaticUpdateChecksEnabled = preferences[Keys.AutomaticUpdateChecks] ?: true,
        )
      }
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

  private object Keys {
    val ThemeMode = stringPreferencesKey("theme_mode")
    val DynamicColor = booleanPreferencesKey("dynamic_color")
    val StoreLayout = stringPreferencesKey("store_layout")
    val WidgetSortMode = stringPreferencesKey("widget_sort_mode")
    val IgnoredStableUpdateVersion = stringPreferencesKey("ignored_stable_update_version")
    val AutomaticUpdateChecks = booleanPreferencesKey("automatic_update_checks")
  }
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
