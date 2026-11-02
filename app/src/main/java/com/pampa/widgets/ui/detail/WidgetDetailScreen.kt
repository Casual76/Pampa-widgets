package com.pampa.widgets.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pampa.widgets.core.settings.MediaWidgetSettings
import com.pampa.widgets.core.widget.WidgetDefinition
import com.pampa.widgets.widget.media.MediaWidgetPreview
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

/**
 * Il dettaglio di un widget del catalogo: l'unica pagina che si spinge sopra una scheda.
 *
 * Dentro l'host delle pagine: entra da destra, torna indietro sotto il dito (da qualunque punto
 * fuori dai tasti), e la barra in alto col tasto indietro la disegna l'host, ferma.
 */
@Composable
fun WidgetDetailScreen(
  widget: WidgetDefinition?,
  onBack: () -> Unit,
  mediaNotificationAccessGranted: Boolean,
  onPinWidget: (String) -> Unit,
  onOpenMediaAccessSettings: () -> Unit,
) {
  FluidScreen(
    title = widget?.name ?: "Widget",
    subtitle = "La musica segue il tuo spazio",
    onBack = onBack,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.PrimaryToSecondary, motif = FluidHeroMotif.Bars) },
    modifier = Modifier.testTag("widget-detail-screen"),
  ) {
    if (widget == null) {
      item(key = "missing") {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          FluidEmptyState(
            title = "Widget non disponibile",
            detail = "Non e' piu' nel catalogo di questa versione dell'app.",
            illustration = {
              Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(64.dp))
            },
          )
          FluidButton(text = "Torna al catalogo", onClick = onBack, style = FluidButtonStyle.Tinted)
        }
      }
      return@FluidScreen
    }

    item(key = "preview") {
      FluidCard(glass = true, animateContent = false) {
        MediaWidgetPreview(MediaWidgetSettings())
      }
    }
    item(key = "pin") {
      FluidButton(text = "Aggiungi alla home", onClick = { onPinWidget(widget.id) }, fillWidth = true)
    }

    item(key = "features-header") { FluidSectionHeader(title = "Fatto per la tua home") }
    item(key = "features") {
      FluidListGroup(glass = true) {
        FeatureRow(Icons.Rounded.AspectRatio, "Da 2 × 2 a tutta pagina",
          "Si adatta alla misura del launcher: piu' spazio vuol dire copertina piu' grande e piu' comandi.")
        FluidListDivider()
        FeatureRow(Icons.Rounded.Speaker, "Una sorgente per widget",
          "Automatica segue la sessione attiva; oppure dedica un widget a Spotify e uno a Fluidify.")
        FluidListDivider()
        FeatureRow(Icons.Rounded.Palette, "Sei stili di colore",
          "Cinque presi dalla copertina, e uno con i colori dell'app, chiaro o scuro come lei.")
        FluidListDivider()
        FeatureRow(Icons.Rounded.Image, "Tre misure di copertina",
          "Compatta, bilanciata o grande, con sorgente e artista da mostrare o nascondere.")
      }
    }

    item(key = "access-header") { FluidSectionHeader(title = "Accesso media") }
    item(key = "access") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = if (mediaNotificationAccessGranted) "Accesso attivo" else "Accesso richiesto",
          subtitle = "L'accesso alle notifiche permette di leggere il brano e di controllare la sessione Android.",
          tone = if (mediaNotificationAccessGranted) FluidTone.Success else FluidTone.Warning,
          leading = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
          badge = {
            FluidStatusBadge(
              label = if (mediaNotificationAccessGranted) "Attivo" else "Da abilitare",
              tone = if (mediaNotificationAccessGranted) FluidTone.Success else FluidTone.Warning,
            )
          },
          onClick = onOpenMediaAccessSettings,
        )
      }
    }
  }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
  FluidListRow(
    title = title,
    subtitle = subtitle,
    tone = FluidTone.Primary,
    leading = { Icon(icon, contentDescription = null) },
    disclosure = false,
  )
}
