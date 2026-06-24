package com.pampa.widgets.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun persistsUserPreferencesInDataStore() = runTest {
    val repository = PreferencesSettingsRepository(
      dataStore = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
      ),
    )

    repository.setThemeMode(ThemeMode.Dark)
    repository.setDynamicColorEnabled(false)
    repository.setStoreLayout(StoreLayout.List)
    repository.setWidgetSortMode(WidgetSortMode.Name)
    repository.setIgnoredStableUpdateVersion("1.2.3")
    repository.setAutomaticUpdateChecksEnabled(false)
    repository.setMediaWidgetTheme(MediaWidgetTheme.AlbumColor)
    repository.setMediaWidgetArtworkSize(MediaWidgetArtworkSize.Compact)
    repository.setMediaWidgetBlurBackground(false)
    repository.setMediaWidgetShowSource(false)
    repository.setMediaWidgetShowArtist(false)
    repository.setMediaWidgetKeepLastSong(false)
    repository.setMediaWidgetInstantControls(false)
    repository.setMediaWidgetAnimatedFeedback(false)

    val settings = repository.observeSettings().first()

    assertEquals(ThemeMode.Dark, settings.themeMode)
    assertFalse(settings.dynamicColorEnabled)
    assertEquals(StoreLayout.List, settings.storeLayout)
    assertEquals(WidgetSortMode.Name, settings.widgetSortMode)
    assertEquals("1.2.3", settings.ignoredStableUpdateVersion)
    assertFalse(settings.automaticUpdateChecksEnabled)
    assertEquals(MediaWidgetTheme.AlbumColor, settings.mediaWidgetTheme)
    assertEquals(MediaWidgetArtworkSize.Compact, settings.mediaWidgetArtworkSize)
    assertFalse(settings.mediaWidgetBlurBackground)
    assertFalse(settings.mediaWidgetShowSource)
    assertFalse(settings.mediaWidgetShowArtist)
    assertFalse(settings.mediaWidgetKeepLastSong)
    assertFalse(settings.mediaWidgetInstantControls)
    assertFalse(settings.mediaWidgetAnimatedFeedback)
  }
}
