@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pampa.widgets.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pampa.widgets.BuildConfig
import com.pampa.widgets.core.settings.StoreLayout
import com.pampa.widgets.core.settings.ThemeMode
import com.pampa.widgets.core.settings.WidgetSortMode
import com.pampa.widgets.core.update.AppUpdateInstallState
import com.pampa.widgets.core.update.isBusy
import com.pampa.widgets.ui.MainUiState

@Composable
fun SettingsScreen(
  uiState: MainUiState,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
  onStoreLayoutChange: (StoreLayout) -> Unit,
  onWidgetSortModeChange: (WidgetSortMode) -> Unit,
  onAutomaticUpdateChecksChange: (Boolean) -> Unit,
  onCheckUpdate: () -> Unit,
  onInstallUpdate: () -> Unit,
  onIgnoreUpdate: () -> Unit,
  mediaNotificationAccessGranted: Boolean,
  onOpenMediaAccessSettings: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .testTag("settings-screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "Impostazioni",
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Infrastruttura pronta per catalogo, aggiornamenti e widget futuri.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      UpdateSection(
        uiState = uiState,
        onCheckUpdate = onCheckUpdate,
        onInstallUpdate = onInstallUpdate,
        onIgnoreUpdate = onIgnoreUpdate,
        onAutomaticUpdateChecksChange = onAutomaticUpdateChecksChange,
      )
    }

    item {
      MediaAccessSection(
        granted = mediaNotificationAccessGranted,
        onOpenMediaAccessSettings = onOpenMediaAccessSettings,
      )
    }

    item {
      AppearanceSection(
        themeMode = uiState.settings.themeMode,
        dynamicColorEnabled = uiState.settings.dynamicColorEnabled,
        onThemeModeChange = onThemeModeChange,
        onDynamicColorChange = onDynamicColorChange,
      )
    }

    item {
      StoreSection(
        layout = uiState.settings.storeLayout,
        sortMode = uiState.settings.widgetSortMode,
        onStoreLayoutChange = onStoreLayoutChange,
        onWidgetSortModeChange = onWidgetSortModeChange,
      )
    }

    item {
      AppInfoSection(widgetCount = uiState.widgets.size)
    }
  }
}

@Composable
private fun MediaAccessSection(
  granted: Boolean,
  onOpenMediaAccessSettings: () -> Unit,
) {
  SettingsSurface {
    SectionTitle(
      icon = Icons.Rounded.MusicNote,
      title = "Controlli media",
      subtitle = if (granted) {
        "Accesso alle sessioni attive abilitato."
      } else {
        "Serve per Spotify, YouTube Music e i pulsanti del widget."
      },
    )
    Spacer(Modifier.height(14.dp))
    Text(
      text = if (granted) {
        "Il widget puo leggere titolo, artista, stato play/pausa e inviare i comandi alla sessione multimediale attiva."
      } else {
        "Abilita Pampa Widgets in Accesso notifiche. Android usa questo canale per esporre le sessioni multimediali delle app come Spotify."
      },
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))
    OutlinedButton(onClick = onOpenMediaAccessSettings) {
      Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.size(8.dp))
      Text(if (granted) "Gestisci accesso" else "Abilita accesso")
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateSection(
  uiState: MainUiState,
  onCheckUpdate: () -> Unit,
  onInstallUpdate: () -> Unit,
  onIgnoreUpdate: () -> Unit,
  onAutomaticUpdateChecksChange: (Boolean) -> Unit,
) {
  SettingsSurface {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(Icons.Rounded.SystemUpdateAlt, contentDescription = null)
      Column(modifier = Modifier.weight(1f)) {
        Text("Aggiornamenti", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          "Da Pampa Store tramite release GitHub.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (uiState.isCheckingUpdate) {
        LoadingIndicator(modifier = Modifier.size(36.dp))
      }
    }

    Spacer(Modifier.height(16.dp))
    SettingSwitchRow(
      title = "Controllo automatico",
      subtitle = "Cerca update all'avvio senza interrompere l'uso.",
      checked = uiState.settings.automaticUpdateChecksEnabled,
      onCheckedChange = onAutomaticUpdateChecksChange,
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

    UpdateStateContent(
      state = uiState.updateInstallState,
      availableVersion = uiState.availableUpdate?.version,
      changelog = uiState.availableUpdate?.changelog.orEmpty(),
    )

    Spacer(Modifier.height(14.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      OutlinedButton(
        onClick = onCheckUpdate,
        enabled = !uiState.isCheckingUpdate && !uiState.updateInstallState.isBusy(),
      ) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Controlla")
      }
      Button(
        onClick = onInstallUpdate,
        enabled = uiState.availableUpdate != null && !uiState.updateInstallState.isBusy(),
      ) {
        Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Installa")
      }
      OutlinedButton(
        onClick = onIgnoreUpdate,
        enabled = uiState.availableUpdate != null && !uiState.updateInstallState.isBusy(),
      ) {
        Text("Ignora versione")
      }
    }
  }
}

@Composable
private fun UpdateStateContent(
  state: AppUpdateInstallState,
  availableVersion: String?,
  changelog: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    val title = when (state) {
      AppUpdateInstallState.Idle -> availableVersion?.let { "Versione $it disponibile" } ?: "App aggiornata"
      is AppUpdateInstallState.Downloading -> "Download ${(state.progress * 100).toInt()}%"
      is AppUpdateInstallState.Verifying -> state.message
      is AppUpdateInstallState.AwaitingUserAction -> state.message
      is AppUpdateInstallState.Installing -> state.message
      is AppUpdateInstallState.Installed -> "Aggiornamento installato"
      is AppUpdateInstallState.Error -> "Aggiornamento non completato"
    }
    Text(title, fontWeight = FontWeight.SemiBold)
    Text(
      text = when (state) {
        is AppUpdateInstallState.Downloading ->
          "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}"
        is AppUpdateInstallState.Error -> state.message
        is AppUpdateInstallState.Installed -> "APK pronto: ${state.filePath}"
        else -> changelog.ifBlank { "Versione installata: ${BuildConfig.VERSION_NAME}" }
      },
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
private fun AppearanceSection(
  themeMode: ThemeMode,
  dynamicColorEnabled: Boolean,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
) {
  SettingsSurface {
    SectionTitle(icon = Icons.Rounded.AutoAwesome, title = "Aspetto", subtitle = "Material 3 Expressive e colori dinamici.")
    Spacer(Modifier.height(14.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      FilterChip(
        selected = themeMode == ThemeMode.System,
        onClick = { onThemeModeChange(ThemeMode.System) },
        label = { Text("Sistema") },
        leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp)) },
      )
      FilterChip(
        selected = themeMode == ThemeMode.Light,
        onClick = { onThemeModeChange(ThemeMode.Light) },
        label = { Text("Chiaro") },
        leadingIcon = { Icon(Icons.Rounded.LightMode, contentDescription = null, modifier = Modifier.size(18.dp)) },
      )
      FilterChip(
        selected = themeMode == ThemeMode.Dark,
        onClick = { onThemeModeChange(ThemeMode.Dark) },
        label = { Text("Scuro") },
        leadingIcon = { Icon(Icons.Rounded.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp)) },
      )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
    SettingSwitchRow(
      title = "Colori dinamici",
      subtitle = "Usa la palette del dispositivo quando disponibile.",
      checked = dynamicColorEnabled,
      onCheckedChange = onDynamicColorChange,
    )
  }
}

@Composable
private fun StoreSection(
  layout: StoreLayout,
  sortMode: WidgetSortMode,
  onStoreLayoutChange: (StoreLayout) -> Unit,
  onWidgetSortModeChange: (WidgetSortMode) -> Unit,
) {
  SettingsSurface {
    SectionTitle(icon = Icons.Rounded.GridView, title = "Store", subtitle = "Preferenze gia attive sul catalogo vuoto.")
    Spacer(Modifier.height(14.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      FilterChip(
        selected = layout == StoreLayout.Grid,
        onClick = { onStoreLayoutChange(StoreLayout.Grid) },
        label = { Text("Griglia") },
        leadingIcon = { Icon(Icons.Rounded.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) },
      )
      FilterChip(
        selected = layout == StoreLayout.List,
        onClick = { onStoreLayoutChange(StoreLayout.List) },
        label = { Text("Lista") },
        leadingIcon = { Icon(Icons.Rounded.ViewAgenda, contentDescription = null, modifier = Modifier.size(18.dp)) },
      )
      FilterChip(
        selected = sortMode == WidgetSortMode.Featured,
        onClick = { onWidgetSortModeChange(WidgetSortMode.Featured) },
        label = { Text("In evidenza") },
      )
      FilterChip(
        selected = sortMode == WidgetSortMode.Name,
        onClick = { onWidgetSortModeChange(WidgetSortMode.Name) },
        label = { Text("Nome") },
        leadingIcon = { Icon(Icons.Rounded.SortByAlpha, contentDescription = null, modifier = Modifier.size(18.dp)) },
      )
    }
  }
}

@Composable
private fun AppInfoSection(widgetCount: Int) {
  SettingsSurface {
    SectionTitle(icon = Icons.Rounded.Download, title = "App", subtitle = "Pampa Widgets ${BuildConfig.VERSION_NAME}")
    Spacer(Modifier.height(12.dp))
    InfoRow(label = "Package", value = BuildConfig.APPLICATION_ID)
    InfoRow(label = "Widget registrati", value = widgetCount.toString())
    InfoRow(label = "Canale", value = "Stable")
  }
}

@Composable
private fun SettingsSurface(content: @Composable ColumnScope.() -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      content = content,
    )
  }
}

@Composable
private fun SectionTitle(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(icon, contentDescription = null)
    Column {
      Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun SettingSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontWeight = FontWeight.SemiBold)
      Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun InfoRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.SemiBold)
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes <= 0L) return "-"
  val mib = bytes / (1024f * 1024f)
  return if (mib >= 1f) {
    "%.1f MB".format(mib)
  } else {
    "${bytes / 1024L} KB"
  }
}
