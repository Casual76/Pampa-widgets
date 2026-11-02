@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pampa.widgets.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import com.pampa.widgets.widget.media.ChoiceButtons
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection

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
  // Il titolo non e' piu' una voce della lista: lo tiene FluidScreen, che lo fa ritirare nella
  // barra mentre si scorre invece di lasciarlo scorrere via. La barra e' in vetro e il bordo in
  // fondo cede: sono le tre cose che arrivano dal componente e non dal tema.
  FluidScreen(
    modifier = Modifier.testTag("settings-screen"),
    title = "Impostazioni",
    subtitle = "Aspetto, accesso media e aggiornamenti.",
  ) {

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

    // Le opere di terze parti che il Fluid Engine porta dentro l'APK — il vetro di Kyant e il font
    // Inter. Le rispettive licenze chiedono che l'avviso viaggi con la distribuzione, e un file di
    // licenza in un repository non e' la distribuzione. L'elenco vive nell'engine, cosi' non resta
    // indietro qui mentre viene aggiornato altrove.
    fluidLicensesSection()
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
        "Serve per Spotify, Fluidify e i pulsanti del widget."
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
    FluidButton(if (granted) "Gestisci accesso" else "Abilita accesso", onOpenMediaAccessSettings, style = FluidButtonStyle.Tinted)
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
      FluidButton("Controlla", onCheckUpdate, style = FluidButtonStyle.Tinted,
        enabled = !uiState.isCheckingUpdate && !uiState.updateInstallState.isBusy())
      FluidButton("Installa", onInstallUpdate,
        enabled = uiState.availableUpdate != null && !uiState.updateInstallState.isBusy())
      if (uiState.availableUpdate != null) FluidButton("Ignora versione", onIgnoreUpdate, style = FluidButtonStyle.Plain)

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
        is AppUpdateInstallState.Installed -> "Aggiornamento pronto"
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
    SectionTitle(icon = Icons.Rounded.AutoAwesome, title = "Aspetto", subtitle = "Fluid Engine e colori dinamici.")
    Spacer(Modifier.height(14.dp))
    ChoiceButtons(ThemeMode.entries, themeMode, onThemeModeChange) {
      when (it) { ThemeMode.System -> "Sistema"; ThemeMode.Light -> "Chiaro"; ThemeMode.Dark -> "Scuro" }
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
    SectionTitle(icon = Icons.Rounded.GridView, title = "Catalogo", subtitle = "Scegli come esplorare i widget.")
    Spacer(Modifier.height(14.dp))
    ChoiceButtons(StoreLayout.entries, layout, onStoreLayoutChange) { if (it == StoreLayout.Grid) "Griglia" else "Lista" }
    Spacer(Modifier.height(12.dp))
    ChoiceButtons(WidgetSortMode.entries, sortMode, onWidgetSortModeChange) { if (it == WidgetSortMode.Featured) "In evidenza" else "Nome" }
  }
}

@Composable
private fun AppInfoSection(widgetCount: Int) {
  SettingsSurface {
    SectionTitle(icon = Icons.Rounded.Download, title = "App", subtitle = "Pampa Widgets ${BuildConfig.VERSION_NAME}")
    Spacer(Modifier.height(12.dp))
    InfoRow(label = "Fluid Engine", value = "1.30.1")
    InfoRow(label = "Widget registrati", value = widgetCount.toString())
    InfoRow(label = "Canale", value = "Stable")
  }
}

@Composable
private fun SettingsSurface(content: @Composable ColumnScope.() -> Unit) {
  FluidListGroup {
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
    FluidSwitch(checked = checked, onCheckedChange = onCheckedChange)
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
