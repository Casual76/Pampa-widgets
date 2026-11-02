package com.pampa.widgets.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.pampa.widgets.widget.media.ChoiceButtons
import com.pampa.widgets.widget.media.MediaWidgetPreview
import com.pampa.widgets.core.settings.MediaWidgetSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pampa.widgets.core.settings.StoreLayout
import com.pampa.widgets.core.settings.WidgetSortMode
import com.pampa.widgets.core.update.AppUpdateInstallState
import com.pampa.widgets.core.update.isBusy
import com.pampa.widgets.core.widget.WidgetDefinition
import com.pampa.widgets.ui.MainUiState
import androidx.compose.foundation.lazy.LazyListScope
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun StoreScreen(
  uiState: MainUiState,
  onSearchQueryChange: (String) -> Unit,
  onStoreLayoutChange: (StoreLayout) -> Unit,
  onWidgetSortModeChange: (WidgetSortMode) -> Unit,
  onWidgetClick: (String) -> Unit,
  onPinWidget: (String) -> Unit,
  onInstallUpdate: () -> Unit,
  onDismissUpdate: () -> Unit,
) {
  // Il conteggio dei widget passa da una pastiglia accanto al titolo a una faccetta della barra:
  // FluidScreen le fa scorrere una dopo l'altra quando il titolo grande si e' ritirato, quindi
  // l'informazione resta raggiungibile senza occupare spazio nella pagina.
  // Quante colonne stanno davvero nella larghezza, con la stessa regola di GridCells.Adaptive:
  // (larghezza + spazio) / (minimo + spazio), almeno una. Su un telefono in verticale fa **una**,
  // non due: fissarle a due schiacciava l'unica card a meta' schermo con il titolo troncato.
  val available = LocalConfiguration.current.screenWidthDp.dp - HorizontalPadding * 2
  val gridColumns = maxOf(
    1,
    ((available + CardSpacing) / (MinimumCardWidth + CardSpacing)).toInt(),
  )

  FluidScreen(
    modifier = Modifier.testTag("store-screen"),
    title = "Pampa Widgets",
    subtitle = "La tua musica, a portata di home.",
    titleFacets = listOf("${uiState.widgets.size} widget"),
    itemSpacing = 18.dp,
  ) {
    if (uiState.availableUpdate != null && !uiState.isUpdateDismissedForSession) {
      item(key = "update-banner") {
        UpdateBanner(
          version = uiState.availableUpdate.version,
          installState = uiState.updateInstallState,
          onInstall = onInstallUpdate,
          onDismiss = onDismissUpdate,
        )
      }
    }

    item(key = "controls") {
      StoreControls(
        query = uiState.searchQuery,
        layout = uiState.settings.storeLayout,
        sortMode = uiState.settings.widgetSortMode,
        onSearchQueryChange = onSearchQueryChange,
        onStoreLayoutChange = onStoreLayoutChange,
        onWidgetSortModeChange = onWidgetSortModeChange,
      )
    }

    if (uiState.visibleWidgets.isEmpty()) {
      item(key = "empty") {
        EmptyStoreState(
          query = uiState.searchQuery,
          totalWidgets = uiState.widgets.size,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("store-empty-state"),
        )
      }
    } else {
      widgetItems(
        widgets = uiState.visibleWidgets,
        layout = uiState.settings.storeLayout,
        columns = gridColumns,
        onWidgetClick = onWidgetClick,
        onPinWidget = onPinWidget,
      )
    }
  }
}

/**
 * I widget, emessi nella lista della schermata invece che in una lista propria.
 *
 * Una lista dentro un'altra lista non scorre: la interna prende il gesto e la pagina resta ferma,
 * quindi il titolo grande non si ritira mai e il bordo elastico non si sente. La griglia diventa
 * righe di due, che e' quello che l'adattiva faceva comunque a queste larghezze.
 */
private fun LazyListScope.widgetItems(
  widgets: List<WidgetDefinition>,
  layout: StoreLayout,
  columns: Int,
  onWidgetClick: (String) -> Unit,
  onPinWidget: (String) -> Unit,
) {
  if (layout == StoreLayout.List || columns <= 1) {
    items(widgets, key = { it.id }) { widget ->
      WidgetCard(widget = widget, onClick = { onWidgetClick(widget.id) }, onPin = { onPinWidget(widget.id) })
    }
    return
  }
  val rows = widgets.chunked(columns)
  items(rows, key = { row -> row.first().id }) { row ->
    Row(horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
      row.forEach { widget ->
        Box(modifier = Modifier.weight(1f)) {
          WidgetCard(widget = widget, onClick = { onWidgetClick(widget.id) }, onPin = { onPinWidget(widget.id) })
        }
      }
      // Tiene le card di una riga incompleta larghe quanto le altre invece di lasciarle
      // allargare fino a riempire la riga.
      repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
  }
}

private val MinimumCardWidth = 280.dp
private val CardSpacing = 12.dp
private val HorizontalPadding = 20.dp

@Composable
private fun UpdateBanner(
  version: String,
  installState: AppUpdateInstallState,
  onInstall: () -> Unit,
  onDismiss: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(Icons.Rounded.InstallMobile, contentDescription = null)
      Column(modifier = Modifier.weight(1f)) {
        Text("Aggiornamento $version disponibile", fontWeight = FontWeight.SemiBold)
        Text(
          text = if (installState.isBusy()) "Installazione in corso" else "Disponibile tramite Pampa Store.",
          style = MaterialTheme.typography.bodySmall,
        )
      }
      FluidButton("Installa", onInstall, enabled = !installState.isBusy())
      IconButton(onClick = onDismiss) {
        Icon(Icons.Rounded.Close, contentDescription = "Nascondi")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StoreControls(
  query: String,
  layout: StoreLayout,
  sortMode: WidgetSortMode,
  onSearchQueryChange: (String) -> Unit,
  onStoreLayoutChange: (StoreLayout) -> Unit,
  onWidgetSortModeChange: (WidgetSortMode) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    FluidTextField(value = query, onValueChange = onSearchQueryChange,
      modifier = Modifier.fillMaxWidth().testTag("widget-search"),
      label = "Cerca widget", placeholder = "Musica, orologio, sistema…",
      leading = { Icon(Icons.Rounded.Search, contentDescription = null) })

    ChoiceButtons(StoreLayout.entries, layout, onStoreLayoutChange) { if (it == StoreLayout.Grid) "Griglia" else "Lista" }
    ChoiceButtons(WidgetSortMode.entries, sortMode, onWidgetSortModeChange) { if (it == WidgetSortMode.Featured) "In evidenza" else "Nome" }
  }
}

@Composable
private fun EmptyStoreState(
  query: String,
  totalWidgets: Int,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier.padding(horizontal = 12.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      ) {
        Icon(
          imageVector = Icons.Rounded.Widgets,
          contentDescription = null,
          modifier = Modifier
            .padding(22.dp)
            .size(44.dp),
        )
      }
      Text(
        text = if (query.isBlank()) "Nessun widget disponibile" else "Nessun risultato",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Text(
        text = if (query.isBlank()) {
          "Lo scaffale e' pronto. I widget arriveranno con i prossimi aggiornamenti dell'app."
        } else {
          "Nessun widget corrisponde alla ricerca."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      AssistChip(
        onClick = {},
        label = {
          Text(
            if (query.isBlank()) {
              "$totalWidgets widget registrati"
            } else {
              "0 risultati su $totalWidgets widget"
            },
          )
        },
        leadingIcon = { Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(18.dp)) },
      )
    }
  }
}

@Composable
private fun WidgetCard(widget: WidgetDefinition, onClick: () -> Unit, onPin: () -> Unit) {
  FluidCard(animateContent = false) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Text(widget.name, style = MaterialTheme.typography.titleLarge)
      MediaWidgetPreview(MediaWidgetSettings())
      Text("Spotify · Fluidify · Automatica", style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary)
      Text("Da 2 × 2 a tutta pagina. Sorgente, colori e controlli indipendenti per ogni widget.",
        style = MaterialTheme.typography.bodyMedium)
      FluidButton("Aggiungi alla home", onPin, fillWidth = true)
      FluidButton("Scopri il widget", onClick, style = FluidButtonStyle.Plain, fillWidth = true)
    }
  }
}
