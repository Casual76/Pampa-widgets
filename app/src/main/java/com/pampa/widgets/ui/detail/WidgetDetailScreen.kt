package com.pampa.widgets.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pampa.widgets.core.widget.WidgetDefinition

@Composable
fun WidgetDetailScreen(
  widget: WidgetDefinition?,
  onBack: () -> Unit,
  mediaNotificationAccessGranted: Boolean,
  onPinWidget: (String) -> Unit,
  onOpenMediaAccessSettings: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(20.dp)
      .testTag("widget-detail-screen"),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.Rounded.ArrowBack, contentDescription = "Indietro")
      }
      Text(
        text = widget?.name ?: "Widget",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
    }

    if (widget == null) {
      NotFoundState(onBack = onBack)
    } else {
      WidgetDetailContent(
        widget = widget,
        mediaNotificationAccessGranted = mediaNotificationAccessGranted,
        onPinWidget = onPinWidget,
        onOpenMediaAccessSettings = onOpenMediaAccessSettings,
      )
    }
  }
}

@Composable
private fun NotFoundState(onBack: () -> Unit) {
  Surface(
    modifier = Modifier.fillMaxSize(),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        Icons.Rounded.Widgets,
        contentDescription = null,
        modifier = Modifier.size(56.dp),
        tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.height(18.dp))
      Text(
        text = "Widget non disponibile",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = "Il catalogo e' pronto, ma questo widget non e' ancora registrato nell'app.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(18.dp))
      Button(onClick = onBack) {
        Text("Torna allo store")
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetDetailContent(
  widget: WidgetDefinition,
  mediaNotificationAccessGranted: Boolean,
  onPinWidget: (String) -> Unit,
  onOpenMediaAccessSettings: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
      Row(
        modifier = Modifier.padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
          Icon(
            imageVector = widget.icon ?: Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier
              .padding(16.dp)
              .size(34.dp),
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(widget.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Text(widget.shortDescription)
          widget.preview?.accentLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }

    MediaPermissionSurface(
      granted = mediaNotificationAccessGranted,
      onOpenMediaAccessSettings = onOpenMediaAccessSettings,
    )

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("Dettagli widget", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          text = widget.preview?.subtitle ?: widget.shortDescription,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          widget.sizes.forEach { size ->
            AssistChip(
              onClick = {},
              label = { Text("${size.preferredColumns}x${size.preferredRows}") },
              leadingIcon = { Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
          }
          widget.capabilities.forEach { capability ->
            AssistChip(onClick = {}, label = { Text(capability) })
          }
        }
      }
    }

    Button(
      modifier = Modifier.fillMaxWidth(),
      onClick = { onPinWidget(widget.id) },
      enabled = widget.appWidgetProviderClassName != null,
    ) {
      Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.size(8.dp))
      Text("Aggiungi alla home")
    }
  }
}

@Composable
private fun MediaPermissionSurface(
  granted: Boolean,
  onOpenMediaAccessSettings: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    color = if (granted) {
      MaterialTheme.colorScheme.tertiaryContainer
    } else {
      MaterialTheme.colorScheme.secondaryContainer
    },
    contentColor = if (granted) {
      MaterialTheme.colorScheme.onTertiaryContainer
    } else {
      MaterialTheme.colorScheme.onSecondaryContainer
    },
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.MusicNote, contentDescription = null)
        Text(
          text = if (granted) "Accesso media attivo" else "Accesso media richiesto",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = if (granted) {
          "Spotify e YouTube Music possono essere letti e controllati quando espongono una sessione multimediale attiva."
        } else {
          "Abilita Pampa Widgets in Accesso notifiche per mostrare titolo, artista e controlli reali nel widget."
        },
      )
      Button(onClick = onOpenMediaAccessSettings) {
        Text(if (granted) "Gestisci accesso" else "Abilita accesso")
      }
    }
  }
}
