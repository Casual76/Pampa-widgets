package com.pampa.widgets.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.widgetSettingsStore by preferencesDataStore(name = "media_widget_instances")

/** Each edit (including migration and restore) is one atomic DataStore transaction. */
class MediaWidgetSettingsRepository internal constructor(private val store: DataStore<Preferences>) {
  constructor(context: Context) : this(context.applicationContext.widgetSettingsStore)

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private fun key(id: Int) = stringPreferencesKey("widget_$id")
  private fun decode(value: String?) = value?.let {
    runCatching { json.decodeFromString<MediaWidgetSettings>(it) }.getOrNull()
  }

  suspend fun read(id: Int, defaults: MediaWidgetSettings = MediaWidgetSettings()): MediaWidgetSettings =
    decode(store.data.first()[key(id)]) ?: defaults

  suspend fun ensure(ids: IntArray, defaults: MediaWidgetSettings): Map<Int, MediaWidgetSettings> {
    val result = mutableMapOf<Int, MediaWidgetSettings>()
    store.edit { prefs ->
      ids.filter { it > 0 }.forEach { id ->
        val value = decode(prefs[key(id)]) ?: defaults.also { prefs[key(id)] = json.encodeToString(it) }
        result[id] = value
      }
    }
    return result
  }

  suspend fun save(id: Int, settings: MediaWidgetSettings) {
    require(id > 0)
    store.edit { it[key(id)] = json.encodeToString(settings) }
  }

  suspend fun delete(ids: IntArray) {
    store.edit { prefs -> ids.forEach { prefs.remove(key(it)) } }
  }

  suspend fun restore(oldIds: IntArray, newIds: IntArray, defaults: MediaWidgetSettings) {
    require(oldIds.size == newIds.size && newIds.all { it > 0 })
    store.edit { prefs ->
      val originals = oldIds.map { decode(prefs[key(it)]) ?: defaults }
      oldIds.forEach { prefs.remove(key(it)) }
      newIds.forEachIndexed { index, id -> prefs[key(id)] = json.encodeToString(originals[index]) }
    }
  }
}
