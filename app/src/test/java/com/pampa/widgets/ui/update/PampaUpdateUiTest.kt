package com.pampa.widgets.ui.update

import com.pampa.widgets.core.update.AppUpdateInstallState
import com.pampa.widgets.core.update.AvailableAppUpdate
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateRelease
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateStage
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class PampaUpdateUiTest {
  private val update = AvailableAppUpdate(
    version = "0.5.1",
    changelog = "Novita'",
    releaseTag = "stable-pampa-widgets-v0.5.1",
    apkAsset = "pampa-widgets-0.5.1.apk",
    downloadUrl = "https://example.invalid/a.apk",
    sizeBytes = 1_000L,
  )
  private val release = FluidUpdateRelease(version = "0.5.1", notes = "Novita'", sizeBytes = 1_000L)

  @Test fun nothingKnownIsIdle() {
    assertEquals(FluidUpdateUiState.Idle, pampaUpdateUiState(null, AppUpdateInstallState.Idle, false, UpdateCheckOutcome.NotChecked))
  }

  @Test fun aCheckWithoutAVersionIsChecking() {
    assertEquals(FluidUpdateUiState.Checking, pampaUpdateUiState(null, AppUpdateInstallState.Idle, true, UpdateCheckOutcome.UpToDate))
  }

  @Test fun noNewVersionIsGoodNewsAndAFailedCheckIsNotAFailedInstall() {
    assertEquals(FluidUpdateUiState.UpToDate, pampaUpdateUiState(null, AppUpdateInstallState.Idle, false, UpdateCheckOutcome.UpToDate))
    assertEquals(
      FluidUpdateUiState.CheckFailed("offline"),
      pampaUpdateUiState(null, AppUpdateInstallState.Idle, false, UpdateCheckOutcome.Failed("offline")),
    )
  }

  @Test fun aVersionInHandWinsOverARunningCheck() {
    assertEquals(
      FluidUpdateUiState.Available(release),
      pampaUpdateUiState(update, AppUpdateInstallState.Idle, true, UpdateCheckOutcome.NotChecked),
    )
  }

  @Test fun everyInstallStepMapsToItsStage() {
    fun map(state: AppUpdateInstallState) = pampaUpdateUiState(update, state, false, UpdateCheckOutcome.NotChecked)
    assertEquals(
      FluidUpdateUiState.Downloading(release, 0.5f, 500L, 1_000L),
      map(AppUpdateInstallState.Downloading(0.5f, 500L, 1_000L)),
    )
    // Senza peso dichiarato non c'e' percentuale: la barra gira senza fine.
    assertEquals(
      FluidUpdateUiState.Downloading(release, null, 500L, 0L),
      map(AppUpdateInstallState.Downloading(0.5f, 500L, 0L)),
    )
    assertEquals(FluidUpdateUiState.Working(release, FluidUpdateStage.Verifying, "v"), map(AppUpdateInstallState.Verifying("v")))
    assertEquals(FluidUpdateUiState.Working(release, FluidUpdateStage.Installing, "i"), map(AppUpdateInstallState.Installing("i")))
    assertEquals(
      FluidUpdateUiState.Working(release, FluidUpdateStage.AwaitingUser, "a"),
      map(AppUpdateInstallState.AwaitingUserAction("a")),
    )
    assertEquals(FluidUpdateUiState.Installed(release), map(AppUpdateInstallState.Installed("/x.apk")))
    assertEquals(FluidUpdateUiState.Failed(release, "boom"), map(AppUpdateInstallState.Error("boom")))
  }
}
