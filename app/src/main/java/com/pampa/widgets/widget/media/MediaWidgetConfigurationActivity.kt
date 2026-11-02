package com.pampa.widgets.widget.media

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pampa.widgets.core.design.PampaWidgetsTheme
import com.pampa.widgets.core.media.NotificationListenerAccess
import com.pampa.widgets.core.settings.*
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MediaWidgetConfigurationActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setResult(RESULT_CANCELED)
    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    if (id <= 0) { finish(); return }
    enableEdgeToEdge()
    setContent {
      var app by remember { mutableStateOf(AppSettings()) }
      var draftJson by rememberSaveable(id) { mutableStateOf<String?>(null) }
      var saving by remember { mutableStateOf(false) }
      var error by remember { mutableStateOf<String?>(null) }
      var granted by remember { mutableStateOf(false) }
      val scope = rememberCoroutineScope()
      val repository = remember { MediaWidgetSettingsRepository(this) }
      LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = NotificationListenerAccess.isGranted(this) }
      LaunchedEffect(id) {
        app = withContext(Dispatchers.IO) { AppSettingsSnapshotReader.readBlocking(this@MediaWidgetConfigurationActivity) }
        if (draftJson == null) draftJson = Json.encodeToString(repository.read(id, MediaWidgetSettings.fromLegacy(app)))
      }
      val draft = draftJson?.let { Json.decodeFromString<MediaWidgetSettings>(it) }
      PampaWidgetsTheme(app) {
        if (draft == null) FluidScreen(title = "Configurazione") { item { Text("Caricamento…") } }
        else MediaWidgetConfigurationScreen(id, draft, granted, saving, error,
          onChange = { draftJson = Json.encodeToString(it) },
          onAccess = { startActivity(NotificationListenerAccess.settingsIntent()) },
          onCancel = { if (!saving) finish() },
          onSave = {
            if (!saving) scope.launch {
              saving = true
              try {
                MediaWidgetUpdateCoordinator.saveSettings(this@MediaWidgetConfigurationActivity, id, draft)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                finish()
              } catch (failure: Exception) { error = "Salvataggio non riuscito. Riprova." }
              finally { saving = false }
            }
          })
      }
    }
  }
}

@Composable
internal fun MediaWidgetConfigurationScreen(id: Int, draft: MediaWidgetSettings, granted: Boolean,
  saving: Boolean, error: String?, onChange: (MediaWidgetSettings) -> Unit,
  onAccess: () -> Unit, onCancel: () -> Unit, onSave: () -> Unit) {
  val context = LocalContext.current
  val options = remember(id) { AppWidgetManager.getInstance(context).getAppWidgetOptions(id) }
  val layout = MediaWidgetLayoutSpec.from(options, draft.applyTo())
  FluidScreen(title = "Il tuo widget", subtitle = "Widget #$id · Le modifiche si applicano con Salva",
    onBack = onCancel, modifier = Modifier.imePadding(),
    actions = { FluidButton("Salva", onSave, enabled = !saving, loading = saving) }) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MediaWidgetPreview(draft, widthDp = layout.widthDp, heightDp = layout.heightDp)
        Text("${layout.widthDp} × ${layout.heightDp} dp · ${draft.source.label}", style = MaterialTheme.typography.labelMedium)
      }
    }
    item {
      ConfigGroup("Sorgente", "Ogni widget controlla la propria app.") {
        ChoiceButtons(MediaSourceMode.entries, draft.source, { onChange(draft.copy(source = it)) }) { it.label }
        Text(when (draft.source) {
          MediaSourceMode.Automatic -> "Segue la sessione musicale attiva, con priorità a Spotify e poi Fluidify."
          else -> "Controlla esclusivamente ${draft.source.label}."
        }, style = MaterialTheme.typography.bodyMedium)
        if (!granted) FluidButton("Abilita accesso notifiche", onAccess, style = FluidButtonStyle.Tinted)
        else if (draft.source.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) == null } == true)
          Text("${draft.source.label} non è installata su questo dispositivo.", color = MaterialTheme.colorScheme.error)
      }
    }
    item {
      ConfigGroup("Aspetto", "Colori opachi, cover e dettagli nelle dimensioni della home.") {
        ChoiceButtons(MediaWidgetTheme.entries, draft.theme, { onChange(draft.copy(theme = it)) }) { it.displayName() }
        Text("Copertina", style = MaterialTheme.typography.titleMedium)
        ChoiceButtons(MediaWidgetArtworkSize.entries, draft.artworkSize, { onChange(draft.copy(artworkSize = it)) }) {
          when (it) { MediaWidgetArtworkSize.Compact -> "Compatta"; MediaWidgetArtworkSize.Balanced -> "Bilanciata"; else -> "Grande" }
        }
        ConfigToggle("Mostra sorgente", draft.showSource) { onChange(draft.copy(showSource = it)) }
        ConfigToggle("Mostra artista", draft.showArtist) { onChange(draft.copy(showArtist = it)) }
      }
    }
    item {
      ConfigGroup("Comportamento", "La memoria del brano resta separata per app.") {
        ConfigToggle("Mantieni ultimo brano", draft.keepLastSong) { onChange(draft.copy(keepLastSong = it)) }
        ConfigToggle("Risposta immediata", draft.instantControls) { onChange(draft.copy(instantControls = it)) }
        ConfigToggle("Feedback animato", draft.animatedFeedback) { onChange(draft.copy(animatedFeedback = it)) }
        Text("I controlli aspettano la conferma della sessione. Le animazioni rispettano le impostazioni di sistema.",
          style = MaterialTheme.typography.bodyMedium)
      }
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        FluidButton("Salva widget", onSave, fillWidth = true, enabled = !saving, loading = saving)
        FluidButton("Annulla", onCancel, fillWidth = true, enabled = !saving, style = FluidButtonStyle.Plain)
      }
    }
  }
}

@Composable
internal fun ConfigGroup(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
  FluidListGroup {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      content()
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceButtons(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEach { option -> FluidButton(label(option), { onSelect(option) },
      style = if (selected == option) FluidButtonStyle.Filled else FluidButtonStyle.Tinted) }
  }
}

@Composable
private fun ConfigToggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    FluidSwitch(checked, onChange, modifier = Modifier.semantics { contentDescription = text })
  }
}

internal fun MediaWidgetTheme.displayName() = when (this) {
  MediaWidgetTheme.SamsungGlass -> "Vetro"
  MediaWidgetTheme.AdaptiveGlass -> "Adattivo"
  MediaWidgetTheme.LightGlass -> "Chiaro"
  MediaWidgetTheme.DarkGlass -> "Scuro"
  MediaWidgetTheme.AlbumColor -> "Album"
}
