package com.pampa.widgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pampa.widgets.core.media.NotificationListenerAccess
import com.pampa.widgets.ui.MainViewModel
import com.pampa.widgets.ui.navigation.PampaWidgetsRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: MainViewModel = hiltViewModel()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshMediaAccess()
      }
      PampaWidgetsRoot(
        uiState = uiState,
        onSearchQueryChange = viewModel::setSearchQuery,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColorEnabled,
        onStoreLayoutChange = viewModel::setStoreLayout,
        onWidgetSortModeChange = viewModel::setWidgetSortMode,
        onAutomaticUpdateChecksChange = viewModel::setAutomaticUpdateChecksEnabled,
        onCheckUpdate = { viewModel.checkForUpdate(showNoUpdateMessage = true) },
        onInstallUpdate = viewModel::startUpdateInstall,
        onDismissUpdate = viewModel::dismissUpdateForSession,
        onIgnoreUpdate = viewModel::ignoreUpdateVersion,
        onClearUpdateMessage = viewModel::clearUpdateMessage,
        onPinWidget = viewModel::requestPinWidget,
        onClearWidgetPinMessage = viewModel::clearWidgetPinMessage,
        onOpenMediaAccessSettings = {
          runCatching { startActivity(NotificationListenerAccess.settingsIntent()) }
        },
      )
    }
  }
}
