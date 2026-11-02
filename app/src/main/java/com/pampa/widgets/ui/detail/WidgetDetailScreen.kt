package com.pampa.widgets.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pampa.widgets.core.widget.WidgetDefinition
import com.pampa.widgets.core.settings.MediaWidgetSettings
import com.pampa.widgets.widget.media.MediaWidgetPreview
import com.pampa.widgets.widget.media.ConfigGroup
import dev.antigravity.fluidengine.ui.fluid.*

@Composable
fun WidgetDetailScreen(widget: WidgetDefinition?, onBack: () -> Unit,
  mediaNotificationAccessGranted: Boolean, onPinWidget: (String) -> Unit, onOpenMediaAccessSettings: () -> Unit) {
  FluidScreen(title = widget?.name ?: "Widget", onBack = onBack,
    modifier = Modifier.testTag("widget-detail-screen"), subtitle = "La musica segue il tuo spazio") {
    if (widget == null) item { Text("Widget non disponibile"); FluidButton("Torna al catalogo", onBack) }
    else {
      item { MediaWidgetPreview(MediaWidgetSettings()) }
      item {
        ConfigGroup("Fatto per la tua home", "Da 2 × 2 a tutta pagina, con dimensioni adattive.") {
          Text("Scegli Automatica per seguire la sessione attiva, oppure dedica un widget a Spotify e uno a Fluidify.")
          Text("Cinque temi, tre dimensioni della copertina e dettagli personalizzabili per ogni istanza.")
          FluidButton("Aggiungi alla home", { onPinWidget(widget.id) }, fillWidth = true)
        }
      }
      item {
        ConfigGroup(if (mediaNotificationAccessGranted) "Accesso media attivo" else "Accesso media richiesto",
          "L'accesso notifiche permette di leggere il brano e controllare la sessione Android.") {
          FluidButton(if (mediaNotificationAccessGranted) "Gestisci accesso" else "Abilita accesso",
            onOpenMediaAccessSettings, style = FluidButtonStyle.Tinted)
        }
      }
    }
  }
}
