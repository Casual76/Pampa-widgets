package com.pampa.widgets.ui.settings

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.pampa.widgets.BuildConfig
import com.pampa.widgets.core.design.PampaBrand
import com.pampa.widgets.core.settings.StoreLayout
import com.pampa.widgets.core.settings.WidgetSortMode
import com.pampa.widgets.core.settings.toEngineSettings
import com.pampa.widgets.ui.MainActions
import com.pampa.widgets.ui.MainUiState
import dev.antigravity.fluidengine.foundation.EngineBuild
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidChoiceRow
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.fluidAppearanceSection
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.fluid.fluidUpdateSection
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTone

/** Dove stanno i sorgenti: la GPL-3.0 chiede che chi ha l'APK sappia dove trovarli. */
internal const val SourceRepositoryUrl = "https://github.com/Casual76/Pampa-widgets"
internal const val LicenseUrl = "$SourceRepositoryUrl/blob/main/LICENSE"

/**
 * Le impostazioni, nella grammatica delle impostazioni dell'engine: intestazioni quiete, gruppi di
 * vetro sul fondale, righe che sono il controllo (la riga **e'** l'interruttore, la scelta ha la spunta
 * a destra). Aspetto e aggiornamenti sono le sezioni dell'engine, le stesse delle altre app: qui c'e'
 * solo quello che e' di Pampa Widgets — l'accesso alle sessioni media, il catalogo, le informazioni.
 */
@Composable
fun SettingsScreen(
  uiState: MainUiState,
  actions: MainActions,
) {
  val settings = uiState.settings
  FluidScreen(
    modifier = Modifier.testTag("settings-screen"),
    title = "Impostazioni",
    subtitle = "Aspetto, accesso media e aggiornamenti.",
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Tertiary, motif = FluidHeroMotif.Ticks) },
  ) {
    item(key = "media-header") { FluidSectionHeader(title = "Controlli media") }
    item(key = "media") {
      val granted = uiState.mediaNotificationAccessGranted
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Accesso alle notifiche",
          subtitle = if (granted) {
            "Il widget legge brano e stato della riproduzione e invia i comandi all'app che suona."
          } else {
            "Serve al widget per Spotify, Fluidify e i suoi pulsanti. Tocca per abilitarlo."
          },
          tone = if (granted) FluidTone.Success else FluidTone.Warning,
          leading = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
          badge = {
            FluidStatusBadge(
              label = if (granted) "Attivo" else "Da abilitare",
              tone = if (granted) FluidTone.Success else FluidTone.Warning,
            )
          },
          onClick = actions.onOpenMediaAccessSettings,
          modifier = Modifier.testTag("settings-media-access"),
        )
      }
    }

    fluidAppearanceSection(
      settings = settings.toEngineSettings(),
      onSettingsChange = actions.onAppearanceChange,
      brand = PampaBrand,
      glass = true,
      footnote = "Un widget con i colori «Come l'app» segue tema e accento scelti qui.",
    )

    item(key = "catalog-header") { FluidSectionHeader(title = "Catalogo") }
    item(key = "catalog-layout") {
      FluidListGroup(modifier = Modifier.selectableGroup(), glass = true) {
        StoreLayout.entries.forEachIndexed { index, option ->
          if (index > 0) FluidListDivider()
          FluidChoiceRow(
            title = if (option == StoreLayout.Grid) "Griglia" else "Lista",
            subtitle = if (option == StoreLayout.Grid) {
              "Card affiancate quando lo schermo e' largo."
            } else {
              "Una card per riga, sempre."
            },
            selected = settings.storeLayout == option,
            onSelect = { if (settings.storeLayout != option) actions.onStoreLayoutChange(option) },
            leading = {
              Icon(
                if (option == StoreLayout.Grid) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                contentDescription = null,
              )
            },
          )
        }
      }
    }
    item(key = "catalog-sort") {
      FluidListGroup(modifier = Modifier.selectableGroup(), glass = true) {
        WidgetSortMode.entries.forEachIndexed { index, option ->
          if (index > 0) FluidListDivider()
          FluidChoiceRow(
            title = if (option == WidgetSortMode.Featured) "In evidenza" else "Per nome",
            subtitle = if (option == WidgetSortMode.Featured) {
              "Prima quelli disponibili, poi per categoria."
            } else {
              "In ordine alfabetico."
            },
            selected = settings.widgetSortMode == option,
            onSelect = { if (settings.widgetSortMode != option) actions.onWidgetSortModeChange(option) },
            leading = {
              Icon(
                if (option == WidgetSortMode.Featured) Icons.Rounded.Star else Icons.Rounded.SortByAlpha,
                contentDescription = null,
              )
            },
          )
        }
      }
    }

    val update = uiState.update
    fluidUpdateSection(
      state = update.ui,
      onCheck = actions.onCheckUpdate,
      onInstall = actions.onInstallUpdate,
      currentVersion = BuildConfig.VERSION_NAME,
      lastCheckedAtMillis = update.lastCheckedAtMillis,
      onSkip = actions.onIgnoreUpdate,
      glass = true,
    )
    item(key = "update-auto") {
      FluidListGroup(glass = true) {
        FluidToggleRow(
          title = "Controllo automatico",
          subtitle = "Cerca una versione nuova all'avvio, senza interrompere.",
          checked = settings.automaticUpdateChecksEnabled,
          onCheckedChange = actions.onAutomaticUpdateChecksChange,
        )
      }
    }
    item(key = "update-footnote") {
      FluidSectionFootnote(text = "Le versioni arrivano dal Pampa Store, tramite le release su GitHub.")
    }

    item(key = "about-header") { FluidSectionHeader(title = "Informazioni") }
    item(key = "about") {
      FluidListGroup(glass = true) {
        FluidListRow(
          title = "Pampa Widgets",
          subtitle = "Versione ${BuildConfig.VERSION_NAME} · canale stabile",
          disclosure = false,
        )
        FluidListDivider()
        FluidListRow(title = "Fluid Engine", subtitle = EngineBuild.VERSION, disclosure = false)
        FluidListDivider()
        FluidListRow(
          title = "Widget nel catalogo",
          subtitle = uiState.widgets.size.toString(),
          disclosure = false,
        )
        FluidListDivider()
        FluidListRow(
          title = "Licenza",
          subtitle = "GNU GPL 3.0: software libero, con i sorgenti per chi ha l'app.",
          leading = { Icon(Icons.Rounded.Gavel, contentDescription = null) },
          onClick = { actions.onOpenUrl(LicenseUrl) },
        )
        FluidListDivider()
        FluidListRow(
          title = "Codice sorgente",
          subtitle = "github.com/Casual76/Pampa-widgets",
          leading = { Icon(Icons.Rounded.Code, contentDescription = null) },
          onClick = { actions.onOpenUrl(SourceRepositoryUrl) },
        )
      }
    }

    // Le opere di terze parti che il Fluid Engine porta dentro l'APK — il vetro di Kyant, il font
    // Inter e, dalla 3.0, i pulsanti e le pagine di LibrePods (GPL-3.0). L'avviso deve viaggiare con
    // la distribuzione: un file in un repository non e' la distribuzione.
    fluidLicensesSection()
  }
}
