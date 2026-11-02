package com.pampa.widgets.ui.store

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pampa.widgets.core.media.MediaPlaybackAvailability
import com.pampa.widgets.core.media.MediaPlaybackSnapshot
import com.pampa.widgets.core.media.MediaSessionReader
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.MediaWidgetSettings
import com.pampa.widgets.core.settings.MediaWidgetSettingsRepository
import com.pampa.widgets.widget.media.MediaWidgetConfigurationActivity
import com.pampa.widgets.widget.media.MediaWidgetLayoutSpec
import com.pampa.widgets.widget.media.MediaWidgetPreview
import com.pampa.widgets.widget.media.MediaWidgetProvider
import com.pampa.widgets.widget.media.displayName
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidPressGrip
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonTile
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.ui.theme.FluidToneTile
import kotlinx.coroutines.launch

/** Un widget sulla home: le sue impostazioni, cosa sta mostrando adesso e quanto e' grande. */
internal data class WidgetInstance(
  val id: Int,
  val settings: MediaWidgetSettings,
  val snapshot: MediaPlaybackSnapshot,
  val layout: MediaWidgetLayoutSpec,
)

/** Dove sta la lettura delle istanze: in corso, fallita, o finita (anche vuota). */
internal sealed interface MyWidgetsState {
  data object Loading : MyWidgetsState
  data object Failed : MyWidgetsState
  data class Loaded(val instances: List<WidgetInstance>) : MyWidgetsState
}

/** L'ultima lettura delle istanze, tenuta dalla radice: tornando sulla scheda si vede subito. */
@Composable
internal fun rememberMyWidgetsState(): MutableState<MyWidgetsState> =
  remember { mutableStateOf(MyWidgetsState.Loading) }

/**
 * Le istanze del widget presenti sulla home, ognuna con la sua anteprima vera.
 *
 * Si rilegge a ogni ritorno sulla pagina (un widget aggiunto o tolto dalla home nel frattempo), in
 * silenzio sopra l'ultima lettura, e col gesto di aggiornamento, che mostra il suo indicatore. Lo
 * scheletro di una card, che respira come quelli delle altre app, si vede solo la prima volta; se la
 * lettura non riesce lo dice un messaggio che si tocca per riprovare.
 */
@Composable
internal fun MyWidgetsScreen(
  app: AppSettings,
  onCatalog: () -> Unit,
  stateHolder: MutableState<MyWidgetsState> = rememberMyWidgetsState(),
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var state by stateHolder
  var refreshing by remember { mutableStateOf(false) }
  val read: (Boolean) -> Unit = { byHand ->
    scope.launch {
      refreshing = byHand
      state = try {
        MyWidgetsState.Loaded(readInstances(context, app))
      } catch (failure: Exception) {
        MyWidgetsState.Failed
      }
      refreshing = false
    }
  }
  val refresh: () -> Unit = { read(true) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { read(false) }

  FluidScreen(
    title = "I miei",
    subtitle = "Le istanze presenti sulla tua home",
    isRefreshing = refreshing,
    onRefresh = refresh,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.SecondaryToTertiary, motif = FluidHeroMotif.Ripples) },
    modifier = Modifier.testTag("my-widgets-screen"),
  ) {
    when (val current = state) {
      MyWidgetsState.Loading -> item(key = "loading") {
        FluidCard(glass = true, animateContent = false) {
          // La proporzione dell'anteprima di serie (320 × 230): quando arriva, la card non salta.
          FluidSkeletonTile(modifier = Modifier.fillMaxWidth(), aspectRatio = 320f / 230f)
        }
      }
      MyWidgetsState.Failed -> item(key = "error") {
        FluidInlineMessage(
          title = "Non riesco a leggere i widget",
          message = "Il launcher non ha risposto.",
          tone = FluidTone.Danger,
          leading = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
          action = "Riprova",
          onClick = refresh,
        )
      }
      is MyWidgetsState.Loaded -> if (current.instances.isEmpty()) {
        item(key = "empty") {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FluidEmptyState(
              title = "La tua home, la tua musica",
              detail = "Aggiungi un widget dal catalogo: qui potrai personalizzare ogni istanza.",
              illustration = {
                Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(64.dp))
              },
            )
            FluidButton(text = "Esplora i widget", onClick = onCatalog, style = FluidButtonStyle.Tinted)
          }
        }
      } else {
        items(current.instances, key = { it.id }) { instance ->
          WidgetInstanceCard(
            instance = instance,
            app = app,
            onConfigure = {
              context.startActivity(
                Intent(context, MediaWidgetConfigurationActivity::class.java)
                  .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, instance.id),
              )
            },
          )
        }
      }
    }
  }
}

/**
 * Una istanza: la card si tocca per configurarla, e il tasto dice la stessa cosa a chi non lo sa.
 * Il tasto si ripete per ogni istanza in una lista che scorre, quindi cede il dito alla pagina.
 */
@Composable
private fun WidgetInstanceCard(instance: WidgetInstance, app: AppSettings, onConfigure: () -> Unit) {
  FluidCard(glass = true, onClick = onConfigure, animateContent = false) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FluidToneTile(tone = FluidTone.Primary) {
          Icon(Icons.Rounded.GraphicEq, contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f)) {
          Text("Media Controls", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "#${instance.id} · ${instance.settings.source.label} · ${instance.settings.theme.displayName()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        val (label, tone) = instance.snapshot.statusBadge()
        FluidStatusBadge(label = label, tone = tone)
      }
      MediaWidgetPreview(
        settings = instance.settings,
        widthDp = instance.layout.widthDp,
        heightDp = instance.layout.heightDp,
        snapshot = instance.snapshot,
        // L'aspetto di adesso, non quello dell'ultima lettura: un widget «Come l'app» cambia con lui.
        appSettings = app,
      )
      Text(
        text = "${instance.layout.widthDp} × ${instance.layout.heightDp} dp sulla home",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FluidButton(
        text = "Configura widget",
        onClick = onConfigure,
        fillWidth = true,
        style = FluidButtonStyle.Tinted,
        grip = FluidPressGrip.YieldsToScroll,
      )
    }
  }
}

private fun MediaPlaybackSnapshot.statusBadge(): Pair<String, FluidTone> = when (availability) {
  MediaPlaybackAvailability.PermissionRequired -> "Accesso richiesto" to FluidTone.Warning
  MediaPlaybackAvailability.AppMissing -> "App non installata" to FluidTone.Warning
  MediaPlaybackAvailability.NoSession -> "Inattiva" to FluidTone.Neutral
  MediaPlaybackAvailability.Active -> if (isPlaying) "In riproduzione" to FluidTone.Success else "In pausa" to FluidTone.Info
}

private suspend fun readInstances(context: Context, app: AppSettings): List<WidgetInstance> {
  val manager = AppWidgetManager.getInstance(context)
  val ids = manager.getAppWidgetIds(ComponentName(context, MediaWidgetProvider::class.java))
  val settings = MediaWidgetSettingsRepository(context).ensure(ids, MediaWidgetSettings.fromLegacy(app))
  val snapshots = MediaSessionReader.readSnapshots(context, settings.values)
  return settings.map { (id, config) ->
    WidgetInstance(
      id = id,
      settings = config,
      snapshot = snapshots.getValue(config.source to config.keepLastSong),
      layout = MediaWidgetLayoutSpec.from(manager.getAppWidgetOptions(id), config.applyTo(app)),
    )
  }
}
