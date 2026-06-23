package com.pampa.widgets.ui.store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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

@Composable
fun StoreScreen(
  uiState: MainUiState,
  onSearchQueryChange: (String) -> Unit,
  onStoreLayoutChange: (StoreLayout) -> Unit,
  onWidgetSortModeChange: (WidgetSortMode) -> Unit,
  onWidgetClick: (String) -> Unit,
  onInstallUpdate: () -> Unit,
  onDismissUpdate: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Header(totalWidgets = uiState.widgets.size)

    if (uiState.availableUpdate != null && !uiState.isUpdateDismissedForSession) {
      UpdateBanner(
        version = uiState.availableUpdate.version,
        installState = uiState.updateInstallState,
        onInstall = onInstallUpdate,
        onDismiss = onDismissUpdate,
      )
    }

    StoreControls(
      query = uiState.searchQuery,
      layout = uiState.settings.storeLayout,
      sortMode = uiState.settings.widgetSortMode,
      onSearchQueryChange = onSearchQueryChange,
      onStoreLayoutChange = onStoreLayoutChange,
      onWidgetSortModeChange = onWidgetSortModeChange,
    )

    if (uiState.visibleWidgets.isEmpty()) {
      EmptyStoreState(
        query = uiState.searchQuery,
        totalWidgets = uiState.widgets.size,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .testTag("store-empty-state"),
      )
    } else {
      WidgetList(
        widgets = uiState.visibleWidgets,
        layout = uiState.settings.storeLayout,
        onWidgetClick = onWidgetClick,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun Header(totalWidgets: Int) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "Pampa Widgets",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Store locale per widget di sistema creati da te.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
      Text(
        text = totalWidgets.toString(),
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

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
      Button(
        onClick = onInstall,
        enabled = !installState.isBusy(),
      ) {
        Text("Installa")
      }
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
    OutlinedTextField(
      value = query,
      onValueChange = onSearchQueryChange,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("widget-search"),
      singleLine = true,
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      trailingIcon = {
        if (query.isNotBlank()) {
          IconButton(onClick = { onSearchQueryChange("") }) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancella ricerca")
          }
        }
      },
      label = { Text("Cerca widget") },
      placeholder = { Text("Multimedia, orologio, sistema...") },
      shape = MaterialTheme.shapes.large,
    )

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
        leadingIcon = { Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(18.dp)) },
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
private fun WidgetList(
  widgets: List<WidgetDefinition>,
  layout: StoreLayout,
  onWidgetClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  when (layout) {
    StoreLayout.Grid -> LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 280.dp),
      modifier = modifier.fillMaxWidth(),
      contentPadding = PaddingValues(bottom = 24.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(widgets, key = { it.id }) { widget ->
        WidgetCard(widget = widget, onClick = { onWidgetClick(widget.id) })
      }
    }
    StoreLayout.List -> LazyColumn(
      modifier = modifier.fillMaxWidth(),
      contentPadding = PaddingValues(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(widgets, key = { it.id }) { widget ->
        WidgetCard(widget = widget, onClick = { onWidgetClick(widget.id) })
      }
    }
  }
}

@Composable
private fun WidgetCard(
  widget: WidgetDefinition,
  onClick: () -> Unit,
) {
  ElevatedCard(
    onClick = onClick,
    colors = CardDefaults.elevatedCardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
      ) {
        Icon(
          imageVector = widget.icon ?: Icons.Rounded.Widgets,
          contentDescription = null,
          modifier = Modifier
            .padding(12.dp)
            .size(28.dp),
        )
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(widget.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
          widget.shortDescription,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
