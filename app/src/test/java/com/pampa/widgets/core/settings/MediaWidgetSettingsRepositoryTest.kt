package com.pampa.widgets.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaWidgetSettingsRepositoryTest {
  @get:Rule val folder = TemporaryFolder()

  @Test fun migrationCopiesLegacyOnlyOnceAndNewInstancesInheritDefaults() = runTest {
    val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(folder.root, "migration.preferences_pb") })
    val repo = MediaWidgetSettingsRepository(store)
    val legacy = MediaWidgetSettings.fromLegacy(AppSettings(mediaWidgetTheme = MediaWidgetTheme.AlbumColor,
      mediaWidgetArtworkSize = MediaWidgetArtworkSize.Compact, mediaWidgetShowSource = true,
      mediaWidgetShowArtist = false, mediaWidgetKeepLastSong = false, mediaWidgetInstantControls = true,
      mediaWidgetAnimatedFeedback = false))
    val migrated = repo.ensure(intArrayOf(10, 20), legacy)
    assertEquals(legacy, migrated[10]); assertEquals(MediaSourceMode.Automatic, migrated[20]?.source)
    val changed = legacy.copy(source = MediaSourceMode.Fluidify, theme = MediaWidgetTheme.DarkGlass)
    repo.save(10, changed)
    repo.ensure(intArrayOf(10, 20, 30), legacy.copy(showArtist = true))
    assertEquals(changed, repo.read(10)); assertEquals(legacy, repo.read(20))
    assertTrue(repo.read(30).showArtist)
    assertEquals(changed, MediaWidgetSettingsRepository(store).read(10))
  }

  @Test fun cancelHasNoWriteAndSaveOnlyChangesTheChosenInstance() = runTest {
    val repo = MediaWidgetSettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope,
      produceFile = { File(folder.root, "draft.preferences_pb") }))
    val original = MediaWidgetSettings()
    repo.ensure(intArrayOf(1, 2), original)
    val draft = repo.read(1).copy(source = MediaSourceMode.Spotify, showArtist = false)
    assertEquals(original, repo.read(1)) // dismissing this draft performs no write
    repo.save(1, draft)
    assertEquals(draft, repo.read(1)); assertEquals(original, repo.read(2))
  }

  @Test fun deletionAndOverlappingRestoreKeepEachOriginalValue() = runTest {
    val repo = MediaWidgetSettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope,
      produceFile = { File(folder.root, "restore.preferences_pb") }))
    val spotify = MediaWidgetSettings(source = MediaSourceMode.Spotify)
    val fluidify = MediaWidgetSettings(source = MediaSourceMode.Fluidify)
    repo.save(1, spotify); repo.save(2, fluidify)
    repo.restore(intArrayOf(1, 2, 99), intArrayOf(2, 1, 3), MediaWidgetSettings())
    assertEquals(fluidify, repo.read(1)); assertEquals(spotify, repo.read(2))
    assertEquals(MediaWidgetSettings(), repo.read(3))
    repo.delete(intArrayOf(1, 2, 3))
    assertEquals(emptyMap<Int, MediaWidgetSettings>(), repo.ensure(intArrayOf(), spotify))
    assertEquals(spotify, repo.ensure(intArrayOf(1), spotify)[1])
  }
}
