package com.pampa.widgets.core.update

import kotlinx.coroutines.flow.Flow

data class AvailableAppUpdate(
  val version: String,
  val changelog: String,
  val releaseTag: String,
  val apkAsset: String,
  val downloadUrl: String,
  val sizeBytes: Long = 0L,
)

sealed interface AppUpdateInstallState {
  data object Idle : AppUpdateInstallState
  data class Downloading(
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
  ) : AppUpdateInstallState
  data class Verifying(val message: String = "Preparazione aggiornamento...") : AppUpdateInstallState
  data class AwaitingUserAction(val message: String) : AppUpdateInstallState
  data class Installing(val message: String) : AppUpdateInstallState
  data class Installed(val filePath: String) : AppUpdateInstallState
  data class Error(val message: String) : AppUpdateInstallState
}

interface AppUpdateRepository {
  suspend fun checkForStableUpdate(
    currentVersionName: String,
    ignoredVersion: String,
  ): Result<AvailableAppUpdate?>

  fun install(update: AvailableAppUpdate): Flow<AppUpdateInstallState>
}

fun AppUpdateInstallState.isBusy(): Boolean = when (this) {
  is AppUpdateInstallState.Downloading,
  is AppUpdateInstallState.Verifying,
  is AppUpdateInstallState.Installing,
  is AppUpdateInstallState.AwaitingUserAction -> true
  AppUpdateInstallState.Idle,
  is AppUpdateInstallState.Installed,
  is AppUpdateInstallState.Error -> false
}
