package com.pampa.widgets.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pampa.widgets.BuildConfig
import com.pampa.widgets.core.media.NotificationListenerAccess
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.MediaWidgetArtworkSize
import com.pampa.widgets.core.settings.MediaWidgetTheme
import com.pampa.widgets.core.settings.SettingsRepository
import com.pampa.widgets.core.settings.StoreLayout
import com.pampa.widgets.core.settings.ThemeMode
import com.pampa.widgets.core.settings.WidgetSortMode
import com.pampa.widgets.core.update.AppUpdateInstallState
import com.pampa.widgets.core.update.AppUpdateRepository
import com.pampa.widgets.core.update.AvailableAppUpdate
import com.pampa.widgets.core.update.isBusy
import com.pampa.widgets.core.widget.WidgetDefinition
import com.pampa.widgets.core.widget.WidgetPinResult
import com.pampa.widgets.core.widget.WidgetPinService
import com.pampa.widgets.core.widget.WidgetRegistry
import com.pampa.widgets.core.widget.filterAndSortWidgets
import com.pampa.widgets.widget.media.MediaWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
  val settings: AppSettings = AppSettings(),
  val widgets: List<WidgetDefinition> = emptyList(),
  val visibleWidgets: List<WidgetDefinition> = emptyList(),
  val searchQuery: String = "",
  val availableUpdate: AvailableAppUpdate? = null,
  val updateInstallState: AppUpdateInstallState = AppUpdateInstallState.Idle,
  val isCheckingUpdate: Boolean = false,
  val updateMessage: String? = null,
  val isUpdateDismissedForSession: Boolean = false,
  val mediaNotificationAccessGranted: Boolean = false,
  val widgetPinMessage: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  private val settingsRepository: SettingsRepository,
  private val widgetRegistry: WidgetRegistry,
  private val widgetPinService: WidgetPinService,
  private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {
  private val searchQuery = MutableStateFlow("")
  private val availableUpdate = MutableStateFlow<AvailableAppUpdate?>(null)
  private val updateInstallState = MutableStateFlow<AppUpdateInstallState>(AppUpdateInstallState.Idle)
  private val isCheckingUpdate = MutableStateFlow(false)
  private val updateMessage = MutableStateFlow<String?>(null)
  private val isUpdateDismissedForSession = MutableStateFlow(false)
  private val mediaNotificationAccessGranted = MutableStateFlow(NotificationListenerAccess.isGranted(context))
  private val widgetPinMessage = MutableStateFlow<String?>(null)

  val uiState = combine(
    settingsRepository.observeSettings(),
    widgetRegistry.observeWidgets(),
    searchQuery,
    availableUpdate,
    updateInstallState,
    isCheckingUpdate,
    updateMessage,
    isUpdateDismissedForSession,
    mediaNotificationAccessGranted,
    widgetPinMessage,
  ) { values ->
    val settings = values[0] as AppSettings
    val widgets = values[1] as List<WidgetDefinition>
    val query = values[2] as String
    val update = values[3] as AvailableAppUpdate?
    val installState = values[4] as AppUpdateInstallState
    val checking = values[5] as Boolean
    val message = values[6] as String?
    val dismissed = values[7] as Boolean
    val mediaAccessGranted = values[8] as Boolean
    val pinMessage = values[9] as String?
    MainUiState(
      settings = settings,
      widgets = widgets,
      visibleWidgets = filterAndSortWidgets(widgets, query, settings.widgetSortMode),
      searchQuery = query,
      availableUpdate = update,
      updateInstallState = installState,
      isCheckingUpdate = checking,
      updateMessage = message,
      isUpdateDismissedForSession = dismissed,
      mediaNotificationAccessGranted = mediaAccessGranted,
      widgetPinMessage = pinMessage,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

  init {
    viewModelScope.launch {
      val settings = settingsRepository.observeSettings().first()
      if (settings.automaticUpdateChecksEnabled) {
        checkForUpdate(showNoUpdateMessage = false)
      }
    }
  }

  fun setSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settingsRepository.setThemeMode(mode) }
  }

  fun setDynamicColorEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setDynamicColorEnabled(enabled) }
  }

  fun setStoreLayout(layout: StoreLayout) {
    viewModelScope.launch { settingsRepository.setStoreLayout(layout) }
  }

  fun setWidgetSortMode(mode: WidgetSortMode) {
    viewModelScope.launch { settingsRepository.setWidgetSortMode(mode) }
  }

  fun setAutomaticUpdateChecksEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setAutomaticUpdateChecksEnabled(enabled) }
  }

  fun setMediaWidgetTheme(theme: MediaWidgetTheme) {
    viewModelScope.launch {
      settingsRepository.setMediaWidgetTheme(theme)
      MediaWidgetUpdater.updateAll(context)
    }
  }

  fun setMediaWidgetArtworkSize(size: MediaWidgetArtworkSize) {
    viewModelScope.launch {
      settingsRepository.setMediaWidgetArtworkSize(size)
      MediaWidgetUpdater.updateAll(context)
    }
  }

  fun setMediaWidgetBlurBackground(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepository.setMediaWidgetBlurBackground(enabled)
      MediaWidgetUpdater.updateAll(context)
    }
  }

  fun setMediaWidgetShowSource(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepository.setMediaWidgetShowSource(enabled)
      MediaWidgetUpdater.updateAll(context)
    }
  }

  fun setMediaWidgetShowArtist(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepository.setMediaWidgetShowArtist(enabled)
      MediaWidgetUpdater.updateAll(context)
    }
  }

  fun setMediaWidgetKeepLastSong(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepository.setMediaWidgetKeepLastSong(enabled)
      MediaWidgetUpdater.updateAll(context)
    }
  }

  fun setMediaWidgetInstantControls(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepository.setMediaWidgetInstantControls(enabled)
      MediaWidgetUpdater.updateAll(context)
    }
  }

  fun setMediaWidgetAnimatedFeedback(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepository.setMediaWidgetAnimatedFeedback(enabled)
      MediaWidgetUpdater.updateAll(context)
    }
  }

  fun checkForUpdate(showNoUpdateMessage: Boolean = true) {
    if (isCheckingUpdate.value) return
    viewModelScope.launch {
      isCheckingUpdate.value = true
      if (showNoUpdateMessage) updateMessage.value = null
      try {
        val ignoredVersion = if (showNoUpdateMessage) {
          ""
        } else {
          settingsRepository.observeSettings().first().ignoredStableUpdateVersion
        }
        appUpdateRepository.checkForStableUpdate(
          currentVersionName = BuildConfig.VERSION_NAME,
          ignoredVersion = ignoredVersion,
        ).onSuccess { update ->
          availableUpdate.value = update
          updateInstallState.value = AppUpdateInstallState.Idle
          if (update != null) {
            isUpdateDismissedForSession.value = false
            if (showNoUpdateMessage) {
              updateMessage.value = "Aggiornamento ${update.version} disponibile."
            }
          } else if (showNoUpdateMessage) {
            updateMessage.value = "Nessun aggiornamento disponibile."
          }
        }.onFailure { error ->
          if (showNoUpdateMessage) {
            updateMessage.value = error.message ?: "Controllo aggiornamenti non riuscito."
          }
        }
      } finally {
        isCheckingUpdate.value = false
      }
    }
  }

  fun startUpdateInstall() {
    val update = availableUpdate.value ?: return
    if (updateInstallState.value.isBusy()) return
    viewModelScope.launch {
      appUpdateRepository.install(update).collect { state ->
        updateInstallState.value = state
      }
    }
  }

  fun dismissUpdateForSession() {
    isUpdateDismissedForSession.value = true
  }

  fun ignoreUpdateVersion() {
    val update = availableUpdate.value ?: return
    viewModelScope.launch {
      settingsRepository.setIgnoredStableUpdateVersion(update.version)
      availableUpdate.value = null
      updateInstallState.value = AppUpdateInstallState.Idle
      isUpdateDismissedForSession.value = true
    }
  }

  fun clearUpdateMessage() {
    updateMessage.value = null
  }

  fun refreshMediaAccess() {
    mediaNotificationAccessGranted.value = NotificationListenerAccess.isGranted(context)
  }

  fun requestPinWidget(widgetId: String) {
    viewModelScope.launch {
      widgetPinMessage.value = when (widgetPinService.requestPin(widgetId)) {
        WidgetPinResult.Requested -> "Richiesta inviata al launcher."
        WidgetPinResult.NotFound -> "Widget non trovato."
        WidgetPinResult.NoProvider -> "Questo widget non ha ancora un provider installabile."
        WidgetPinResult.Unsupported -> "Il launcher non supporta l'aggiunta diretta."
      }
    }
  }

  fun clearWidgetPinMessage() {
    widgetPinMessage.value = null
  }
}
