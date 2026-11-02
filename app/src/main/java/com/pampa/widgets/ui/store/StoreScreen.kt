package com.pampa.widgets.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pampa.widgets.core.settings.MediaWidgetSettings
import com.pampa.widgets.core.settings.StoreLayout
import com.pampa.widgets.core.settings.WidgetSortMode
import com.pampa.widgets.core.update.isBusy
import com.pampa.widgets.core.widget.WidgetDefinition
import com.pampa.widgets.ui.MainActions
import com.pampa.widgets.ui.MainUiState
import com.pampa.widgets.widget.media.MediaWidgetPreview
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidColumnsDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import dev.antigravity.fluidengine.ui.fluid.FluidPressGrip
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchField
import dev.antigravity.fluidengine.ui.fluid.FluidSearchLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidGridItems
import dev.antigravity.fluidengine.ui.fluid.rememberFluidScreenMetrics
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.ui.theme.FluidToneTile

/**
 * Il catalogo.
 *
 * Una pagina sola che scorre, col fondale del catalogo dietro: le card dei widget sono vetro su quel
 * fondale, la ricerca e' una lastra di vetro in pagina, e la vista e l'ordine — che prima erano due
 * file di pulsanti in cima, cioe' una pagina da cui non si poteva scorrere partendo dai pulsanti —
 * stanno nel menu del tasto della barra, che si apre al tocco come i tre puntini di iOS.
 */
@Composable
fun StoreScreen(
  uiState: MainUiState,
  actions: MainActions,
  onWidgetClick: (String) -> Unit,
  onOpenUpdate: () -> Unit,
) {
  val metrics = rememberFluidScreenMetrics()
  val layout = uiState.settings.storeLayout
  val sortMode = uiState.settings.widgetSortMode

  FluidScreen(
    modifier = Modifier.testTag("store-screen"),
    title = "Pampa Widgets",
    subtitle = "La tua musica, a portata di home.",
    // Il conteggio sta nella barra: compare quando il titolo grande si e' ritirato.
    titleFacets = listOf("${uiState.widgets.size} widget"),
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Cards) },
    metrics = metrics,
    actions = {
      FluidBarAction(
        icon = Icons.Rounded.Tune,
        contentDescription = "Vista e ordine",
        onClick = {},
        opensOn = FluidMenuOpensOn.Tap,
        actions = {
          storeMenu(
            layout = layout,
            sortMode = sortMode,
            onLayout = actions.onStoreLayoutChange,
            onSort = actions.onWidgetSortModeChange,
          )
        },
      )
    },
  ) {
    val update = uiState.update
    val available = update.available
    if (available != null && !update.dismissedForSession) {
      item(key = "update") {
        FluidInlineMessage(
          title = "Aggiornamento ${available.version}",
          message = if (update.installState.isBusy()) {
            "Installazione in corso."
          } else {
            "Disponibile tramite Pampa Store."
          },
          tone = FluidTone.Info,
          leading = { Icon(Icons.Rounded.SystemUpdateAlt, contentDescription = null) },
          action = "Vedi le novità",
          onClick = onOpenUpdate,
          onDismiss = actions.onDismissUpdate,
          modifier = Modifier.testTag("store-update"),
        )
      }
    }

    item(key = "search") {
      FluidSearchField(
        query = uiState.searchQuery,
        onQueryChange = actions.onSearchQueryChange,
        modifier = Modifier.fillMaxWidth().testTag("widget-search"),
        // Il vetro in pagina rifrange il fondale, mai la registrazione della pagina che lo contiene.
        backdrop = LocalFluidCanvasBackdrop.current ?: rememberGlassBackdrop(),
        placeholder = "Cerca widget",
        labels = FluidSearchLabels(search = "Cerca widget"),
        // Sempre in pagina: una tastiera che salisse da sola all'apertura coprirebbe il catalogo.
        autoFocus = false,
      )
    }

    if (uiState.visibleWidgets.isEmpty()) {
      item(key = "empty") {
        val searching = uiState.searchQuery.isNotBlank()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          FluidEmptyState(
            title = if (searching) "Nessun risultato" else "Nessun widget disponibile",
            detail = if (searching) {
              "Nessuno dei ${uiState.widgets.size} widget corrisponde alla ricerca."
            } else {
              "Lo scaffale è pronto: i widget arrivano con i prossimi aggiornamenti dell'app."
            },
            illustration = {
              Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(64.dp))
            },
            modifier = Modifier.testTag("store-empty-state"),
          )
          if (searching) {
            FluidButton(
              text = "Svuota la ricerca",
              onClick = { actions.onSearchQueryChange("") },
              style = FluidButtonStyle.Tinted,
            )
          }
        }
      }
    } else {
      // Le colonne si leggono qui, dentro la lista: la schermata scrive la sua larghezza nello stesso
      // passaggio, e il primo fotogramma ha gia' le colonne giuste.
      fluidGridItems(
        items = uiState.visibleWidgets,
        columns = if (layout == StoreLayout.List) 1 else metrics.columns(minColumn = FluidColumnsDefaults.MinCard),
        key = { it.id },
      ) { widget ->
        WidgetCard(
          widget = widget,
          onClick = { onWidgetClick(widget.id) },
          onPin = { actions.onPinWidget(widget.id) },
        )
      }
    }
  }
}

/**
 * Le voci del menu «Vista e ordine»: due sezioni, la scelta corrente con la spunta a destra come
 * nei menu di iOS. Scegliere la voce gia' scelta non scrive niente.
 */
private fun storeMenu(
  layout: StoreLayout,
  sortMode: WidgetSortMode,
  onLayout: (StoreLayout) -> Unit,
  onSort: (WidgetSortMode) -> Unit,
): List<FluidContextAction> = buildList {
  StoreLayout.entries.forEach { option ->
    add(
      FluidContextAction(
        label = if (option == StoreLayout.Grid) "Griglia" else "Lista",
        icon = Icons.Rounded.Check.takeIf { option == layout },
      ) { if (option != layout) onLayout(option) },
    )
  }
  WidgetSortMode.entries.forEachIndexed { index, option ->
    add(
      FluidContextAction(
        label = if (option == WidgetSortMode.Featured) "In evidenza" else "Per nome",
        icon = Icons.Rounded.Check.takeIf { option == sortMode },
        startsSection = index == 0,
      ) { if (option != sortMode) onSort(option) },
    )
  }
}

/**
 * Un widget del catalogo: una card di vetro che si tocca per i dettagli, con l'anteprima vera (lo
 * stesso `RemoteViews` della home) e i due tasti.
 *
 * I tasti stanno in una lista che scorre e si ripetono per ogni widget: cedono il dito alla pagina
 * (`YieldsToScroll`), altrimenti un pollice posato su un tasto non farebbe scorrere il catalogo.
 */
@Composable
private fun WidgetCard(widget: WidgetDefinition, onClick: () -> Unit, onPin: () -> Unit) {
  FluidCard(glass = true, onClick = onClick, animateContent = false) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FluidToneTile(tone = FluidTone.Primary) {
          Icon(widget.icon ?: Icons.Rounded.GraphicEq, contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(widget.name, style = MaterialTheme.typography.titleLarge)
          Text(
            text = "Spotify · Fluidify · Automatica",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
      MediaWidgetPreview(MediaWidgetSettings())
      Text(
        text = "Da 2 × 2 a tutta pagina. Sorgente, colori e controlli indipendenti per ogni widget.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FluidButton(
        text = "Aggiungi alla home",
        onClick = onPin,
        fillWidth = true,
        grip = FluidPressGrip.YieldsToScroll,
      )
      FluidButton(
        text = "Scopri il widget",
        onClick = onClick,
        style = FluidButtonStyle.Plain,
        fillWidth = true,
        grip = FluidPressGrip.YieldsToScroll,
      )
    }
  }
}
