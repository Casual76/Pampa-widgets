package com.pampa.widgets

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pampa.widgets.core.media.NotificationListenerAccess
import com.pampa.widgets.ui.MainActions
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
      val actions = remember(viewModel) {
        MainActions(
          onSearchQueryChange = viewModel::setSearchQuery,
          onAppearanceChange = viewModel::setAppearance,
          onStoreLayoutChange = viewModel::setStoreLayout,
          onWidgetSortModeChange = viewModel::setWidgetSortMode,
          onAutomaticUpdateChecksChange = viewModel::setAutomaticUpdateChecksEnabled,
          onCheckUpdate = { viewModel.checkForUpdate(manual = true) },
          onInstallUpdate = viewModel::startUpdateInstall,
          onDismissUpdate = viewModel::dismissUpdateForSession,
          onIgnoreUpdate = viewModel::ignoreUpdateVersion,
          onPinWidget = viewModel::requestPinWidget,
          onClearWidgetPinMessage = viewModel::clearWidgetPinMessage,
          onOpenMediaAccessSettings = {
            runCatching { startActivity(NotificationListenerAccess.settingsIntent()) }
          },
          onOpenUrl = { url ->
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
          },
        )
      }
      PampaWidgetsRoot(uiState = uiState, actions = actions)
    }
  }
}
