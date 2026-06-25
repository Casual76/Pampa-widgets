package com.pampa.widgets.widget.media

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pampa.widgets.core.design.PampaWidgetsTheme
import com.pampa.widgets.core.media.NotificationListenerAccess
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.MediaWidgetArtworkSize
import com.pampa.widgets.core.settings.MediaWidgetTheme
import com.pampa.widgets.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MediaWidgetConfigurationActivity : ComponentActivity() {
  private val appWidgetId: Int
    get() = intent?.extras?.getInt(
      AppWidgetManager.EXTRA_APPWIDGET_ID,
      AppWidgetManager.INVALID_APPWIDGET_ID,
    ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setResult(RESULT_CANCELED)
    enableEdgeToEdge()

    setContent {
      val viewModel: MainViewModel = hiltViewModel()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshMediaAccess()
      }

      PampaWidgetsTheme(settings = uiState.settings) {
        MediaWidgetConfigurationScreen(
          settings = uiState.settings,
          mediaNotificationAccessGranted = uiState.mediaNotificationAccessGranted,
          onThemeChange = viewModel::setMediaWidgetTheme,
          onArtworkSizeChange = viewModel::setMediaWidgetArtworkSize,
          onShowSourceChange = viewModel::setMediaWidgetShowSource,
          onShowArtistChange = viewModel::setMediaWidgetShowArtist,
          onKeepLastSongChange = viewModel::setMediaWidgetKeepLastSong,
          onInstantControlsChange = viewModel::setMediaWidgetInstantControls,
          onAnimatedFeedbackChange = viewModel::setMediaWidgetAnimatedFeedback,
          onOpenMediaAccessSettings = {
            runCatching { startActivity(NotificationListenerAccess.settingsIntent()) }
          },
          onCancel = { finish() },
          onDone = { finishConfiguration() },
        )
      }
    }
  }

  private fun finishConfiguration() {
    MediaWidgetUpdater.updateAll(this)
    val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    setResult(RESULT_OK, result)
    finish()
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MediaWidgetConfigurationScreen(
  settings: AppSettings,
  mediaNotificationAccessGranted: Boolean,
  onThemeChange: (MediaWidgetTheme) -> Unit,
  onArtworkSizeChange: (MediaWidgetArtworkSize) -> Unit,
  onShowSourceChange: (Boolean) -> Unit,
  onShowArtistChange: (Boolean) -> Unit,
  onKeepLastSongChange: (Boolean) -> Unit,
  onInstantControlsChange: (Boolean) -> Unit,
  onAnimatedFeedbackChange: (Boolean) -> Unit,
  onOpenMediaAccessSettings: () -> Unit,
  onCancel: () -> Unit,
  onDone: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text("Media Controls", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
          IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, contentDescription = "Chiudi")
          }
        },
        actions = {
          IconButton(onClick = onDone) {
            Icon(Icons.Rounded.Check, contentDescription = "Salva")
          }
        },
      )
    },
    bottomBar = {
      Surface(shadowElevation = 8.dp) {
        Button(
          onClick = onDone,
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        ) {
          Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Salva widget")
        }
      }
    },
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
    ) {
      item { WidgetPreview(settings = settings) }

      item {
        ConfigSurface {
          SectionHeader(
            icon = Icons.Rounded.Palette,
            title = "Aspetto",
            subtitle = "Superficie pulita, tinta dalla canzone e cover nel riquadro.",
          )
          Spacer(Modifier.height(14.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ThemeChip("Vetro", MediaWidgetTheme.SamsungGlass, settings.mediaWidgetTheme, onThemeChange)
            ThemeChip("Album", MediaWidgetTheme.AlbumColor, settings.mediaWidgetTheme, onThemeChange)
            ThemeChip("Chiaro", MediaWidgetTheme.LightGlass, settings.mediaWidgetTheme, onThemeChange)
            ThemeChip("Scuro", MediaWidgetTheme.DarkGlass, settings.mediaWidgetTheme, onThemeChange)
          }

          HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
          Text("Cover", fontWeight = FontWeight.SemiBold)
          Spacer(Modifier.height(8.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ArtworkChip("Compatta", MediaWidgetArtworkSize.Compact, settings.mediaWidgetArtworkSize, onArtworkSizeChange)
            ArtworkChip("Bilanciata", MediaWidgetArtworkSize.Balanced, settings.mediaWidgetArtworkSize, onArtworkSizeChange)
            ArtworkChip("Grande", MediaWidgetArtworkSize.Large, settings.mediaWidgetArtworkSize, onArtworkSizeChange)
          }
        }
      }

      item {
        ConfigSurface {
          SectionHeader(
            icon = Icons.Rounded.Settings,
            title = "Comportamento",
            subtitle = "Sessioni musicali, cache e risposta dei controlli.",
          )
          Spacer(Modifier.height(12.dp))
          ConfigSwitchRow("Sorgente", "Mostra Spotify, YouTube Music, Apple Music e simili.", settings.mediaWidgetShowSource, onShowSourceChange)
          ConfigSwitchRow("Artista", "Mostra la riga secondaria sotto il titolo.", settings.mediaWidgetShowArtist, onShowArtistChange)
          ConfigSwitchRow("Ultima canzone", "Mantiene titolo e cover quando Samsung nasconde la sessione.", settings.mediaWidgetKeepLastSong, onKeepLastSongChange)
          ConfigSwitchRow("Comandi immediati", "Aggiorna subito l'icona play/pausa dopo il tocco.", settings.mediaWidgetInstantControls, onInstantControlsChange)
          ConfigSwitchRow("Feedback leggero", "Micro-pressione sui pulsanti, disattivata di default.", settings.mediaWidgetAnimatedFeedback, onAnimatedFeedbackChange)
        }
      }

      item {
        ConfigSurface {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (mediaNotificationAccessGranted) Icons.Rounded.CheckCircle else Icons.Rounded.MusicNote, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
              Text(
                if (mediaNotificationAccessGranted) "Accesso media attivo" else "Accesso media richiesto",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                if (mediaNotificationAccessGranted) {
                  "Il widget puo leggere e controllare le app musicali supportate."
                } else {
                  "Serve per leggere titolo, artista, cover e inviare play/pausa."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Spacer(Modifier.height(12.dp))
          OutlinedButton(onClick = onOpenMediaAccessSettings) {
            Text(if (mediaNotificationAccessGranted) "Gestisci accesso" else "Abilita accesso")
          }
        }
      }
    }
  }
}

@Composable
private fun WidgetPreview(settings: AppSettings) {
  val accent = when (settings.mediaWidgetTheme) {
    MediaWidgetTheme.AlbumColor -> MaterialTheme.colorScheme.primary
    MediaWidgetTheme.DarkGlass -> Color(0xFF202722)
    else -> MaterialTheme.colorScheme.tertiary
  }
  val dark = settings.mediaWidgetTheme == MediaWidgetTheme.DarkGlass
  val cardColor = if (dark) {
    Color(0xE61B211E)
  } else {
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
  }
  val textColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
  val secondaryTextColor = if (dark) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    color = cardColor,
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier = Modifier
            .size(
              when (settings.mediaWidgetArtworkSize) {
                MediaWidgetArtworkSize.Compact -> 80.dp
                MediaWidgetArtworkSize.Balanced -> 92.dp
                MediaWidgetArtworkSize.Large -> 104.dp
              },
            )
            .clip(MaterialTheme.shapes.large)
            .background(accent.copy(alpha = 0.82f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
          if (settings.mediaWidgetShowSource) {
            Text("Spotify", style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
          }
          Text(
            "Titolo canzone",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (settings.mediaWidgetShowArtist) {
            Text("Artista", color = secondaryTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(4.dp)
          .clip(MaterialTheme.shapes.extraSmall)
          .background(if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(0.62f)
            .height(4.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (dark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.42f)),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PreviewControlIcon(Icons.Rounded.SkipPrevious, textColor.copy(alpha = 0.88f), dark)
        Spacer(Modifier.width(16.dp))
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(accent.copy(alpha = if (dark) 0.88f else 0.42f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = textColor, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(16.dp))
        PreviewControlIcon(Icons.Rounded.SkipNext, textColor.copy(alpha = 0.88f), dark)
      }
    }
  }
}

@Composable
private fun PreviewControlIcon(
  imageVector: androidx.compose.ui.graphics.vector.ImageVector,
  tint: Color,
  dark: Boolean,
) {
  Box(
    modifier = Modifier
      .size(44.dp)
      .clip(MaterialTheme.shapes.extraLarge)
      .background(if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.40f)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(imageVector, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
  }
}

@Composable
private fun ConfigSurface(content: @Composable ColumnScope.() -> Unit) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp),
      content = content,
    )
  }
}

@Composable
private fun SectionHeader(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Icon(icon, contentDescription = null)
    Column {
      Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun ThemeChip(
  label: String,
  value: MediaWidgetTheme,
  selected: MediaWidgetTheme,
  onThemeChange: (MediaWidgetTheme) -> Unit,
) {
  FilterChip(
    selected = selected == value,
    onClick = { onThemeChange(value) },
    label = { Text(label) },
  )
}

@Composable
private fun ArtworkChip(
  label: String,
  value: MediaWidgetArtworkSize,
  selected: MediaWidgetArtworkSize,
  onArtworkSizeChange: (MediaWidgetArtworkSize) -> Unit,
) {
  FilterChip(
    selected = selected == value,
    onClick = { onArtworkSizeChange(value) },
    label = { Text(label) },
  )
}

@Composable
private fun ConfigSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
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
