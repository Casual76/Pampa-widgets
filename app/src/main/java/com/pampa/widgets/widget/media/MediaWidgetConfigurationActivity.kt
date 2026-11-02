package com.pampa.widgets.widget.media

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pampa.widgets.core.design.PampaWidgetsTheme
import com.pampa.widgets.core.media.NotificationListenerAccess
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.AppSettingsSnapshotReader
import com.pampa.widgets.core.settings.MediaSourceMode
import com.pampa.widgets.core.settings.MediaWidgetArtworkSize
import com.pampa.widgets.core.settings.MediaWidgetSettings
import com.pampa.widgets.core.settings.MediaWidgetSettingsRepository
import com.pampa.widgets.core.settings.MediaWidgetTheme
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidChoiceRow
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonTile
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * La configurazione di una istanza del widget: la apre il launcher quando il widget si aggiunge alla
 * home (e quando si riconfigura), e la app da «I miei».
 *
 * E' una finestra a se', fuori dall'host delle pagine: la barra in cima la disegna la schermata, e il
 * back chiude. Le modifiche restano una bozza finche' non si salva; con una bozza da perdere il back
 * (di sistema, a dito o il tasto) chiede prima con un avviso di vetro.
 */
class MediaWidgetConfigurationActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setResult(RESULT_CANCELED)
    val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    if (id <= 0) {
      finish()
      return
    }
    enableEdgeToEdge()
    setContent {
      var app by remember { mutableStateOf<AppSettings?>(null) }
      var savedJson by rememberSaveable(id) { mutableStateOf<String?>(null) }
      var draftJson by rememberSaveable(id) { mutableStateOf<String?>(null) }
      var saving by remember { mutableStateOf(false) }
      var error by remember { mutableStateOf<String?>(null) }
      var granted by remember { mutableStateOf(NotificationListenerAccess.isGranted(this)) }
      var asking by remember { mutableStateOf(false) }
      val scope = rememberCoroutineScope()
      val repository = remember { MediaWidgetSettingsRepository(this) }
      LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = NotificationListenerAccess.isGranted(this) }
      LaunchedEffect(id) {
        val settings = withContext(Dispatchers.IO) {
          AppSettingsSnapshotReader.readBlocking(this@MediaWidgetConfigurationActivity)
        }
        app = settings
        if (savedJson == null) {
          val stored = Json.encodeToString(repository.read(id, MediaWidgetSettings.fromLegacy(settings)))
          savedJson = stored
          draftJson = stored
        }
      }
      val draft = draftJson?.let { Json.decodeFromString<MediaWidgetSettings>(it) }
      val dirty = draftJson != null && draftJson != savedJson

      val save: () -> Unit = save@{
        val current = draft ?: return@save
        if (saving) return@save
        scope.launch {
          saving = true
          error = null
          try {
            MediaWidgetUpdateCoordinator.saveSettings(this@MediaWidgetConfigurationActivity, id, current)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
          } catch (failure: Exception) {
            error = "Salvataggio non riuscito. Riprova."
          } finally {
            saving = false
          }
        }
      }
      val leave: () -> Unit = { if (!saving) { if (dirty) asking = true else finish() } }

      // Il back a dito e quello di sistema passano di qui solo con una bozza da perdere.
      BackHandler(enabled = dirty && !saving) { asking = true }

      PampaWidgetsTheme(app ?: AppSettings()) {
        ConfigurationShell {
          if (draft == null || app == null) {
            ConfigurationLoading(onBack = { finish() })
          } else {
            MediaWidgetConfigurationScreen(
              id = id,
              draft = draft,
              app = app!!,
              granted = granted,
              saving = saving,
              error = error,
              onChange = { draftJson = Json.encodeToString(it) },
              onAccess = { runCatching { startActivity(NotificationListenerAccess.settingsIntent()) } },
              onBack = leave,
              onSave = save,
            )
          }
          if (asking) {
            FluidAlert(
              onDismissRequest = { asking = false },
              title = "Uscire senza salvare?",
              message = "Le modifiche a questo widget andranno perse.",
              actions = listOf(
                FluidAlertAction("Resta", onClick = { asking = false }, emphasis = FluidAlertAction.Emphasis.Preferred),
                FluidAlertAction(
                  "Esci",
                  onClick = {
                    asking = false
                    finish()
                  },
                  emphasis = FluidAlertAction.Emphasis.Destructive,
                ),
              ),
            )
          }
        }
      }
    }
  }
}

/**
 * La radice di questa finestra: l'host dei modali (l'avviso sta li', vetro vero sulla pagina) e il
 * registro della barra, perche' l'avviso rifranga la pagina che copre.
 */
@Composable
private fun ConfigurationShell(content: @Composable () -> Unit) {
  val modalHost = rememberFluidGlassModalHostState()
  val chromeController = rememberFluidChromeController()
  val fallbackBackdrop = rememberGlassBackdrop()
  val scrollToTop = remember { FluidScrollToTopBus() }
  CompositionLocalProvider(LocalFluidGlassModalHostState provides modalHost) {
    Box(modifier = Modifier.fillMaxSize()) {
      ProvideFluidChrome(controller = chromeController, bottomInset = 0.dp, scrollToTop = scrollToTop) {
        content()
      }
      FluidGlassModalHost(state = modalHost, backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop)
    }
  }
}

@Composable
private fun ConfigurationLoading(onBack: () -> Unit) {
  FluidScreen(
    title = "Il tuo widget",
    onBack = onBack,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Ripples) },
  ) {
    item(key = "loading") {
      FluidCard(glass = true, animateContent = false) {
        FluidSkeletonTile(modifier = Modifier.fillMaxWidth(), aspectRatio = 320f / 230f)
      }
    }
  }
}

@Composable
internal fun MediaWidgetConfigurationScreen(
  id: Int,
  draft: MediaWidgetSettings,
  app: AppSettings,
  granted: Boolean,
  saving: Boolean,
  error: String?,
  onChange: (MediaWidgetSettings) -> Unit,
  onAccess: () -> Unit,
  onBack: () -> Unit,
  onSave: () -> Unit,
) {
  val context = LocalContext.current
  val options = remember(id) { AppWidgetManager.getInstance(context).getAppWidgetOptions(id) }
  val layout = MediaWidgetLayoutSpec.from(options, draft.applyTo(app))
  val sourceMissing = remember(draft.source) {
    draft.source.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) == null } == true
  }

  FluidScreen(
    title = "Il tuo widget",
    subtitle = "Widget #$id · le modifiche si applicano con Salva",
    onBack = onBack,
    modifier = Modifier.imePadding().testTag("widget-configuration"),
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Ripples) },
    actions = {
      // Sempre attivo: per un widget appena messo sulla home salvare e' anche confermare i valori di
      // serie, e senza conferma il launcher lo toglie.
      FluidGlassButton(text = "Salva", onClick = onSave, enabled = !saving)
    },
  ) {
    item(key = "preview") {
      FluidCard(glass = true, animateContent = false) {
        MediaWidgetPreview(draft, widthDp = layout.widthDp, heightDp = layout.heightDp, appSettings = app)
        Text(
          text = "${layout.widthDp} × ${layout.heightDp} dp · ${draft.source.label} · ${draft.theme.displayName()}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item(key = "source-header") { FluidSectionHeader(title = "Sorgente") }
    item(key = "source") {
      FluidSegmentedControl(
        options = MediaSourceMode.entries,
        selected = draft.source,
        onSelect = { if (it != draft.source) onChange(draft.copy(source = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { it.label },
      )
    }
    item(key = "source-footnote") {
      FluidSectionFootnote(
        text = when (draft.source) {
          MediaSourceMode.Automatic -> "Segue la sessione musicale attiva, con priorità a Spotify e poi Fluidify."
          else -> "Controlla solo ${draft.source.label}: le altre app non lo cambiano."
        },
      )
    }
    if (!granted) {
      item(key = "access") {
        FluidInlineMessage(
          title = "Serve l'accesso alle notifiche",
          message = "Senza, il widget non vede cosa suona e i suoi tasti non arrivano all'app.",
          tone = FluidTone.Warning,
          leading = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
          action = "Abilita accesso",
          onClick = onAccess,
        )
      }
    } else if (sourceMissing) {
      item(key = "missing") {
        FluidInlineMessage(
          title = "${draft.source.label} non c'è",
          message = "Non è installata su questo dispositivo: il widget resterà vuoto finché non la installi.",
          tone = FluidTone.Warning,
          leading = { Icon(Icons.Rounded.Warning, contentDescription = null) },
        )
      }
    }

    item(key = "theme-header") { FluidSectionHeader(title = "Colori") }
    item(key = "theme") {
      FluidListGroup(modifier = Modifier.selectableGroup(), glass = true) {
        MediaWidgetThemeOrder.forEachIndexed { index, theme ->
          if (index > 0) FluidListDivider()
          FluidChoiceRow(
            title = theme.displayName(),
            subtitle = theme.description(),
            selected = draft.theme == theme,
            onSelect = { if (draft.theme != theme) onChange(draft.copy(theme = theme)) },
          )
        }
      }
    }

    item(key = "artwork-header") { FluidSectionHeader(title = "Copertina e dettagli") }
    item(key = "artwork") {
      FluidSegmentedControl(
        options = MediaWidgetArtworkSize.entries,
        selected = draft.artworkSize,
        onSelect = { if (it != draft.artworkSize) onChange(draft.copy(artworkSize = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { it.displayName() },
      )
    }
    item(key = "details") {
      FluidListGroup(glass = true) {
        FluidToggleRow(
          title = "Mostra sorgente",
          subtitle = "Il nome dell'app che suona, in una pillola sopra il titolo.",
          checked = draft.showSource,
          onCheckedChange = { onChange(draft.copy(showSource = it)) },
        )
        FluidListDivider()
        FluidToggleRow(
          title = "Mostra artista",
          subtitle = "Sotto il titolo, quando c'è spazio.",
          checked = draft.showArtist,
          onCheckedChange = { onChange(draft.copy(showArtist = it)) },
        )
      }
    }

    item(key = "behaviour-header") { FluidSectionHeader(title = "Comportamento") }
    item(key = "behaviour") {
      FluidListGroup(glass = true) {
        FluidToggleRow(
          title = "Mantieni ultimo brano",
          subtitle = "Quando l'app si chiude resta l'ultimo brano, ricordato per ogni app.",
          checked = draft.keepLastSong,
          onCheckedChange = { onChange(draft.copy(keepLastSong = it)) },
        )
        FluidListDivider()
        FluidToggleRow(
          title = "Risposta immediata",
          subtitle = "Il tasto mostra subito il comando in corso, prima che l'app lo confermi.",
          checked = draft.instantControls,
          onCheckedChange = { onChange(draft.copy(instantControls = it)) },
        )
        FluidListDivider()
        FluidToggleRow(
          title = "Feedback animato",
          subtitle = "Il tasto play si trasforma quando cambia stato.",
          checked = draft.animatedFeedback,
          onCheckedChange = { onChange(draft.copy(animatedFeedback = it)) },
        )
      }
    }
    item(key = "behaviour-footnote") {
      FluidSectionFootnote(
        text = "Copertina, colori e testo cambiano solo quando l'app conferma il comando. " +
          "Le animazioni rispettano quelle di sistema.",
      )
    }

    if (error != null) {
      item(key = "error") {
        FluidInlineMessage(
          title = "Non salvato",
          message = error,
          tone = FluidTone.Danger,
          leading = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
          action = "Riprova",
          onClick = onSave,
        )
      }
    }
    item(key = "save") {
      FluidButton(
        text = "Salva widget",
        onClick = onSave,
        fillWidth = true,
        enabled = !saving,
        loading = saving,
      )
    }
  }
}

/** L'ordine in cui si offrono i colori: quelli dalla copertina, poi quelli dell'app. */
internal val MediaWidgetThemeOrder = listOf(
  MediaWidgetTheme.SamsungGlass,
  MediaWidgetTheme.AdaptiveGlass,
  MediaWidgetTheme.LightGlass,
  MediaWidgetTheme.DarkGlass,
  MediaWidgetTheme.AlbumColor,
  MediaWidgetTheme.AppTheme,
)

internal fun MediaWidgetTheme.displayName() = when (this) {
  MediaWidgetTheme.SamsungGlass -> "Vetro"
  MediaWidgetTheme.AdaptiveGlass -> "Adattivo"
  MediaWidgetTheme.LightGlass -> "Chiaro"
  MediaWidgetTheme.DarkGlass -> "Scuro"
  MediaWidgetTheme.AlbumColor -> "Album"
  MediaWidgetTheme.AppTheme -> "Come l'app"
}

internal fun MediaWidgetTheme.description() = when (this) {
  MediaWidgetTheme.SamsungGlass -> "La tinta della copertina, schiarita."
  MediaWidgetTheme.AdaptiveGlass -> "La tinta della copertina, chiara o scura secondo il brano."
  MediaWidgetTheme.LightGlass -> "Quasi bianco, con un velo della copertina."
  MediaWidgetTheme.DarkGlass -> "Quasi nero, con un velo della copertina."
  MediaWidgetTheme.AlbumColor -> "Il colore pieno della copertina."
  MediaWidgetTheme.AppTheme -> "Chiaro o scuro come l'app, con il suo accento."
}

internal fun MediaWidgetArtworkSize.displayName() = when (this) {
  MediaWidgetArtworkSize.Compact -> "Compatta"
  MediaWidgetArtworkSize.Balanced -> "Bilanciata"
  MediaWidgetArtworkSize.Large -> "Grande"
}
