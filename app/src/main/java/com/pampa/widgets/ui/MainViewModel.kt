package com.pampa.widgets.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pampa.widgets.BuildConfig
import com.pampa.widgets.core.media.NotificationListenerAccess
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.SettingsRepository
import com.pampa.widgets.core.settings.StoreLayout
import com.pampa.widgets.core.settings.WidgetSortMode
import com.pampa.widgets.core.settings.withEngineSettings
import com.pampa.widgets.core.update.AppUpdateInstallState
import com.pampa.widgets.core.update.AppUpdateRepository
import com.pampa.widgets.core.update.AvailableAppUpdate
import com.pampa.widgets.core.update.isBusy
import com.pampa.widgets.core.widget.WidgetDefinition
import com.pampa.widgets.core.widget.WidgetPinResult
import com.pampa.widgets.core.widget.WidgetPinService
import com.pampa.widgets.core.widget.WidgetRegistry
import com.pampa.widgets.core.widget.filterAndSortWidgets
import com.pampa.widgets.ui.update.UpdateCheckOutcome
import com.pampa.widgets.ui.update.pampaUpdateUiState
import com.pampa.widgets.widget.media.MediaWidgetUpdateCoordinator
import com.pampa.widgets.widget.media.MediaWidgetUpdateReason
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateUiState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Tutto quello che l'aggiornamento in-app sa in questa sessione. */
data class UpdateSession(
  val available: AvailableAppUpdate? = null,
  val installState: AppUpdateInstallState = AppUpdateInstallState.Idle,
  val checking: Boolean = false,
  val outcome: UpdateCheckOutcome = UpdateCheckOutcome.NotChecked,
  val lastCheckedAtMillis: Long? = null,
  /** «Più tardi» sull'avviso del catalogo: torna alla prossima apertura dell'app. */
  val dismissedForSession: Boolean = false,
) {
  /** Lo stesso stato nei termini della sezione e del foglio dell'engine. */
  val ui: FluidUpdateUiState
    get() = pampaUpdateUiState(available, installState, checking, outcome)
}

data class MainUiState(
  val settings: AppSettings = AppSettings(),
  val widgets: List<WidgetDefinition> = emptyList(),
  val visibleWidgets: List<WidgetDefinition> = emptyList(),
  val searchQuery: String = "",
  val update: UpdateSession = UpdateSession(),
  val mediaNotificationAccessGranted: Boolean = false,
  val widgetPinMessage: WidgetPinResult? = null,
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
  private val update = MutableStateFlow(UpdateSession())
  private val mediaNotificationAccessGranted = MutableStateFlow(NotificationListenerAccess.isGranted(context))
  private val widgetPinMessage = MutableStateFlow<WidgetPinResult?>(null)

  val uiState = combine(
    settingsRepository.observeSettings(),
    widgetRegistry.observeWidgets(),
    searchQuery,
    update,
    mediaNotificationAccessGranted,
    widgetPinMessage,
  ) { values ->
    val settings = values[0] as AppSettings
    @Suppress("UNCHECKED_CAST")
    val widgets = values[1] as List<WidgetDefinition>
    val query = values[2] as String
    MainUiState(
      settings = settings,
      widgets = widgets,
      visibleWidgets = filterAndSortWidgets(widgets, query, settings.widgetSortMode),
      searchQuery = query,
      update = values[3] as UpdateSession,
      mediaNotificationAccessGranted = values[4] as Boolean,
      widgetPinMessage = values[5] as WidgetPinResult?,
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

  init {
    viewModelScope.launch {
      val settings = settingsRepository.observeSettings().first()
      if (settings.automaticUpdateChecksEnabled) {
        checkForUpdate(manual = false)
      }
    }
  }

  fun setSearchQuery(query: String) {
    searchQuery.value = query
  }

  /**
   * L'aspetto scelto nella sezione dell'engine. Anche il widget lo legge quando segue l'app, quindi
   * dopo la scrittura si ridisegna: un accento cambiato che sulla home non si vede sembra
   * un'impostazione che non ha funzionato.
   */
  fun setAppearance(appearance: EngineSettings) {
    viewModelScope.launch {
      val current = settingsRepository.observeSettings().first()
      settingsRepository.setAppearance(current.withEngineSettings(appearance))
      MediaWidgetUpdateCoordinator.requestUpdate(context, MediaWidgetUpdateReason.Settings)
    }
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

  /**
   * Cerca una versione nuova.
   *
   * [manual] e' il tocco su «Controlla ora»: vede anche la versione che si era scelto di saltare
   * (chi controlla a mano vuole sapere cosa c'e'), e il suo esito resta scritto nella sezione. Il
   * controllo all'avvio invece rispetta la versione saltata e, se non trova niente, non dice niente.
   */
  fun checkForUpdate(manual: Boolean = true) {
    if (update.value.checking) return
    viewModelScope.launch {
      update.update { it.copy(checking = true) }
      try {
        val ignoredVersion = if (manual) {
          ""
        } else {
          settingsRepository.observeSettings().first().ignoredStableUpdateVersion
        }
        appUpdateRepository.checkForStableUpdate(
          currentVersionName = BuildConfig.VERSION_NAME,
          ignoredVersion = ignoredVersion,
        ).onSuccess { found ->
          update.update {
            it.copy(
              available = found,
              installState = AppUpdateInstallState.Idle,
              outcome = if (found == null) UpdateCheckOutcome.UpToDate else UpdateCheckOutcome.NotChecked,
              lastCheckedAtMillis = System.currentTimeMillis(),
              dismissedForSession = if (found != null) false else it.dismissedForSession,
            )
          }
        }.onFailure { error ->
          update.update {
            it.copy(
              // Un controllo all'avvio che fallisce (offline) non ha niente da dire a nessuno.
              outcome = if (manual) UpdateCheckOutcome.Failed(error.message) else it.outcome,
              lastCheckedAtMillis = if (manual) System.currentTimeMillis() else it.lastCheckedAtMillis,
            )
          }
        }
      } finally {
        update.update { it.copy(checking = false) }
      }
    }
  }

  fun startUpdateInstall() {
    val available = update.value.available ?: return
    if (update.value.installState.isBusy()) return
    viewModelScope.launch {
      appUpdateRepository.install(available).collect { state ->
        update.update { it.copy(installState = state) }
      }
    }
  }

  fun dismissUpdateForSession() {
    update.update { it.copy(dismissedForSession = true) }
  }

  fun ignoreUpdateVersion() {
    val available = update.value.available ?: return
    viewModelScope.launch {
      settingsRepository.setIgnoredStableUpdateVersion(available.version)
      update.update {
        it.copy(
          available = null,
          installState = AppUpdateInstallState.Idle,
          dismissedForSession = true,
        )
      }
    }
  }

  fun refreshMediaAccess() {
    mediaNotificationAccessGranted.value = NotificationListenerAccess.isGranted(context)
  }

  fun requestPinWidget(widgetId: String) {
    viewModelScope.launch {
      widgetPinMessage.value = widgetPinService.requestPin(widgetId)
    }
  }

  fun clearWidgetPinMessage() {
    widgetPinMessage.value = null
  }
}
