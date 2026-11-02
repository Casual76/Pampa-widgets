package com.pampa.widgets.ui.store

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pampa.widgets.core.media.MediaPlaybackAvailability
import com.pampa.widgets.core.media.MediaPlaybackSnapshot
import com.pampa.widgets.core.media.MediaSessionReader
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.MediaWidgetSettings
import com.pampa.widgets.core.settings.MediaWidgetSettingsRepository
import com.pampa.widgets.widget.media.*
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.theme.FluidCard
import kotlinx.coroutines.launch

private data class WidgetInstance(val id: Int, val settings: MediaWidgetSettings,
  val snapshot: MediaPlaybackSnapshot, val layout: MediaWidgetLayoutSpec)

@Composable
fun MyWidgetsScreen(app: AppSettings, onCatalog: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var instances by remember { mutableStateOf<List<WidgetInstance>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf(false) }
  val refresh: () -> Unit = {
    scope.launch {
      try {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, MediaWidgetProvider::class.java))
        val settings = MediaWidgetSettingsRepository(context).ensure(ids, MediaWidgetSettings.fromLegacy(app))
        val snapshots = MediaSessionReader.readSnapshots(context, settings.values)
        instances = settings.map { (id, config) -> WidgetInstance(id, config,
          snapshots.getValue(config.source to config.keepLastSong),
          MediaWidgetLayoutSpec.from(manager.getAppWidgetOptions(id), config.applyTo(app))) }
        error = false
      } catch (failure: Exception) { error = true }
      finally { loading = false }
    }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
  FluidScreen(title = "I miei", subtitle = "Le istanze presenti sulla tua home", onRefresh = refresh) {
    if (error) item { Text("Impossibile leggere i widget. Riprova."); FluidButton("Riprova", refresh) }
    else if (loading) item { Text("Caricamento…") }
    else if (instances.isEmpty()) item {
      FluidCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text("La tua home, la tua musica", style = MaterialTheme.typography.titleLarge)
          Text("Aggiungi un widget dal catalogo. Qui potrai personalizzare ogni istanza.")
          FluidButton("Esplora i widget", onCatalog)
        }
      }
    }
    items(instances, key = { it.id }) { instance ->
      FluidCard(animateContent = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Media Controls · #${instance.id}", style = MaterialTheme.typography.titleMedium)
          MediaWidgetPreview(instance.settings, instance.layout.widthDp, instance.layout.heightDp, instance.snapshot)
          Text("${instance.settings.source.label} · " + when (instance.snapshot.availability) {
            MediaPlaybackAvailability.PermissionRequired -> "Accesso richiesto"
            MediaPlaybackAvailability.AppMissing -> "App non installata"
            MediaPlaybackAvailability.NoSession -> "Sessione inattiva"
            MediaPlaybackAvailability.Active -> if (instance.snapshot.isPlaying) "In riproduzione" else "In pausa"
          }, style = MaterialTheme.typography.bodyMedium)
          FluidButton("Configura widget", {
            context.startActivity(Intent(context, MediaWidgetConfigurationActivity::class.java)
              .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, instance.id))
          }, fillWidth = true)
        }
      }
    }
  }
}
