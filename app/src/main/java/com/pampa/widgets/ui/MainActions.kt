package com.pampa.widgets.ui

import androidx.compose.runtime.Immutable
import com.pampa.widgets.core.settings.StoreLayout
import com.pampa.widgets.core.settings.WidgetSortMode
import dev.antigravity.fluidengine.foundation.EngineSettings

/**
 * Quello che le schermate dell'app possono chiedere.
 *
 * Un oggetto solo invece di quindici parametri in fila: la radice lo passa giu' com'e', e una
 * schermata nuova non costringe a riscrivere tre firme per arrivarci.
 */
@Immutable
data class MainActions(
  val onSearchQueryChange: (String) -> Unit = {},
  val onAppearanceChange: (EngineSettings) -> Unit = {},
  val onStoreLayoutChange: (StoreLayout) -> Unit = {},
  val onWidgetSortModeChange: (WidgetSortMode) -> Unit = {},
  val onAutomaticUpdateChecksChange: (Boolean) -> Unit = {},
  val onCheckUpdate: () -> Unit = {},
  val onInstallUpdate: () -> Unit = {},
  val onDismissUpdate: () -> Unit = {},
  val onIgnoreUpdate: () -> Unit = {},
  val onPinWidget: (String) -> Unit = {},
  val onClearWidgetPinMessage: () -> Unit = {},
  val onOpenMediaAccessSettings: () -> Unit = {},
  val onOpenUrl: (String) -> Unit = {},
)
