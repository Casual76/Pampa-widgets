package com.pampa.widgets.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode as EngineThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Chi aggiorna dalla 0.4.x ha solo `theme_mode` e `dynamic_color`: deve ritrovare lo stesso aspetto,
 * e le scelte nuove (accento, nero, aptica) devono sopravvivere a un riavvio.
 */
class AppearanceSettingsMigrationTest {
  @get:Rule val folder = TemporaryFolder()

  @Test fun legacyDynamicColorOnBecomesSystemColours() = runTest {
    val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(folder.root, "a.preferences_pb") })
    store.edit {
      it[PreferencesSettingsRepository.Keys.ThemeMode] = "Dark"
      it[PreferencesSettingsRepository.Keys.DynamicColor] = true
    }
    val settings = PreferencesSettingsRepository(store).observeSettings().first()
    val engine = settings.toEngineSettings()
    assertEquals(EngineThemeMode.DARK, engine.themeMode)
    assertEquals(AccentMode.DYNAMIC, engine.accentMode)
    assertTrue(engine.dynamicColorEnabled)
    assertFalse(engine.amoledEnabled)
    assertTrue(engine.hapticsEnabled)
  }

  @Test fun legacyDynamicColorOffBecomesTheBrandGreen() = runTest {
    val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(folder.root, "b.preferences_pb") })
    store.edit {
      it[PreferencesSettingsRepository.Keys.ThemeMode] = "Light"
      it[PreferencesSettingsRepository.Keys.DynamicColor] = false
    }
    val engine = PreferencesSettingsRepository(store).observeSettings().first().toEngineSettings()
    assertEquals(EngineThemeMode.LIGHT, engine.themeMode)
    assertEquals(AccentMode.BRAND, engine.accentMode)
    assertFalse(engine.dynamicColorEnabled)
  }

  @Test fun anEmptyStoreIsTheDefaultOfEveryVersion() = runTest {
    val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(folder.root, "c.preferences_pb") })
    val settings = PreferencesSettingsRepository(store).observeSettings().first()
    assertEquals(AppSettings(), settings)
    assertEquals(AccentMode.DYNAMIC, settings.accentMode)
    assertEquals(EngineThemeMode.SYSTEM, settings.toEngineSettings().themeMode)
  }

  @Test fun theAppearanceSectionWritesEverythingInOneGo() = runTest {
    val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(folder.root, "d.preferences_pb") })
    val repository = PreferencesSettingsRepository(store)
    val chosen = EngineSettings(
      themeMode = EngineThemeMode.AMOLED,
      accentMode = AccentMode.CUSTOM_PRESET,
      customAccentName = "ember",
      dynamicColorEnabled = false,
      hapticsEnabled = false,
    )
    repository.setAppearance(AppSettings().withEngineSettings(chosen))
    // Un'altra istanza sullo stesso file: quello che si rilegge dopo un riavvio.
    val reread = PreferencesSettingsRepository(store).observeSettings().first()
    assertEquals(ThemeMode.Dark, reread.themeMode)
    assertTrue(reread.pureBlackEnabled)
    assertEquals(AccentMode.CUSTOM_PRESET, reread.accentMode)
    assertEquals("ember", reread.customAccentName)
    assertFalse(reread.dynamicColorEnabled)
    assertFalse(reread.hapticsEnabled)
    // AMOLED si legge come «Scuro» col nero: la stessa tavolozza.
    val engine = reread.toEngineSettings()
    assertEquals(EngineThemeMode.DARK, engine.themeMode)
    assertTrue(engine.amoledEnabled)
  }

  @Test fun anAccentChosenOnceWinsOverTheOldSwitch() = runTest {
    val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(folder.root, "e.preferences_pb") })
    val repository = PreferencesSettingsRepository(store)
    repository.setAppearance(AppSettings(accentMode = AccentMode.BRAND, dynamicColorEnabled = true))
    assertEquals(AccentMode.BRAND, repository.observeSettings().first().accentMode)
  }

  @Test fun theOtherSettingsAreUntouchedByAnAppearanceChange() = runTest {
    val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(folder.root, "f.preferences_pb") })
    val repository = PreferencesSettingsRepository(store)
    repository.setStoreLayout(StoreLayout.List)
    repository.setMediaWidgetTheme(MediaWidgetTheme.AlbumColor)
    repository.setIgnoredStableUpdateVersion("0.4.9")
    val before = repository.observeSettings().first()
    repository.setAppearance(before.withEngineSettings(before.toEngineSettings().copy(themeMode = EngineThemeMode.LIGHT)))
    val after = repository.observeSettings().first()
    assertEquals(StoreLayout.List, after.storeLayout)
    assertEquals(MediaWidgetTheme.AlbumColor, after.mediaWidgetTheme)
    assertEquals("0.4.9", after.ignoredStableUpdateVersion)
    assertEquals(ThemeMode.Light, after.themeMode)
  }

  @Test fun roundTripThroughTheEngineIsLossless() {
    val modes = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
    modes.forEach { mode ->
      listOf(true, false).forEach { black ->
        AccentMode.entries.forEach { accent ->
          val app = AppSettings(themeMode = mode, pureBlackEnabled = black, accentMode = accent, customAccentName = "ocean")
          assertEquals(app, AppSettings().withEngineSettings(app.toEngineSettings()))
        }
      }
    }
  }

  @Test fun anUnknownStoredAccentFallsBackToTheOldRule() {
    assertEquals(AccentMode.DYNAMIC, resolveAccentMode("NOT_A_MODE", dynamicColorEnabled = true))
    assertEquals(AccentMode.BRAND, resolveAccentMode(null, dynamicColorEnabled = false))
    assertEquals(AccentMode.CUSTOM_PRESET, resolveAccentMode("CUSTOM_PRESET", dynamicColorEnabled = true))
  }
}
