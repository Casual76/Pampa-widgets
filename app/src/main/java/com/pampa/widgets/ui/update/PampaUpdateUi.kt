package com.pampa.widgets.ui.update

import com.pampa.widgets.core.update.AppUpdateInstallState
import com.pampa.widgets.core.update.AvailableAppUpdate
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateRelease
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateStage
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateUiState

/** Com'e' andato l'ultimo controllo, quando non ha trovato niente da installare. */
sealed interface UpdateCheckOutcome {
  /** Non si e' ancora controllato in questa sessione. */
  data object NotChecked : UpdateCheckOutcome

  /** Controllato: nessuna versione nuova. Non e' un errore, e la sezione lo dice come una buona notizia. */
  data object UpToDate : UpdateCheckOutcome

  /** Il manifest non si e' letto. Si ritenta con un tocco, e non e' il fallimento di un'installazione. */
  data class Failed(val message: String?) : UpdateCheckOutcome
}

/**
 * L'aggiornamento dell'app nei termini della vista dell'engine.
 *
 * L'updater resta quello dell'app (il manifest del repo con il ripiego su quello del Pampa Store,
 * provato contro una release vera): i suoi tipi sono suoi, e la sezione e il foglio dell'engine
 * parlano [FluidUpdateUiState]. Questa e' l'unica traduzione, pura, e la prova un test.
 *
 * L'ordine conta: una versione in mano vince su tutto (anche su un nuovo controllo, che non deve far
 * sparire il tasto «Installa» mentre gira), poi il controllo in corso, poi l'esito dell'ultimo.
 */
fun pampaUpdateUiState(
  available: AvailableAppUpdate?,
  installState: AppUpdateInstallState,
  checking: Boolean,
  outcome: UpdateCheckOutcome,
): FluidUpdateUiState {
  if (available != null) {
    val release = available.toFluidRelease()
    return when (installState) {
      AppUpdateInstallState.Idle -> FluidUpdateUiState.Available(release)
      is AppUpdateInstallState.Downloading -> FluidUpdateUiState.Downloading(
        release = release,
        // Senza un peso dichiarato non c'e' una percentuale vera: la barra gira senza fine.
        progress = if (installState.totalBytes > 0L) installState.progress.coerceIn(0f, 1f) else null,
        downloadedBytes = installState.downloadedBytes,
        totalBytes = installState.totalBytes,
      )
      is AppUpdateInstallState.Verifying ->
        FluidUpdateUiState.Working(release, FluidUpdateStage.Verifying, installState.message)
      is AppUpdateInstallState.Installing ->
        FluidUpdateUiState.Working(release, FluidUpdateStage.Installing, installState.message)
      is AppUpdateInstallState.AwaitingUserAction ->
        FluidUpdateUiState.Working(release, FluidUpdateStage.AwaitingUser, installState.message)
      is AppUpdateInstallState.Installed -> FluidUpdateUiState.Installed(release)
      is AppUpdateInstallState.Error -> FluidUpdateUiState.Failed(release, installState.message)
    }
  }
  if (checking) return FluidUpdateUiState.Checking
  return when (outcome) {
    UpdateCheckOutcome.NotChecked -> FluidUpdateUiState.Idle
    UpdateCheckOutcome.UpToDate -> FluidUpdateUiState.UpToDate
    is UpdateCheckOutcome.Failed -> FluidUpdateUiState.CheckFailed(outcome.message)
  }
}

private fun AvailableAppUpdate.toFluidRelease(): FluidUpdateRelease =
  FluidUpdateRelease(version = version, notes = changelog, sizeBytes = sizeBytes)
