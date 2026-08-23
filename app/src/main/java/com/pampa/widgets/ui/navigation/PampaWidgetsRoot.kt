package com.pampa.widgets.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pampa.widgets.core.design.PampaWidgetsTheme
import com.pampa.widgets.core.settings.StoreLayout
import com.pampa.widgets.core.settings.ThemeMode
import com.pampa.widgets.core.settings.WidgetSortMode
import com.pampa.widgets.ui.MainUiState
import com.pampa.widgets.ui.detail.WidgetDetailScreen
import com.pampa.widgets.ui.settings.SettingsScreen
import com.pampa.widgets.ui.store.StoreScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import androidx.compose.runtime.getValue

/**
 * Le due destinazioni della barra in basso, nella forma che l'engine legge.
 *
 * FluidTabItem porta l'icona come ImageVector e non come composable: la barra ha bisogno di
 * disegnarla lei, a due opacita' diverse, per far scorrere la pillola sotto quella selezionata.
 */
private val tabItems = listOf(
  FluidTabItem(route = AppRoutes.Store, label = "Store", icon = Icons.Rounded.Widgets),
  FluidTabItem(route = AppRoutes.Settings, label = "Impostazioni", icon = Icons.Rounded.Settings),
)

@Composable
fun PampaWidgetsRoot(
  uiState: MainUiState,
  onSearchQueryChange: (String) -> Unit,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
  onStoreLayoutChange: (StoreLayout) -> Unit,
  onWidgetSortModeChange: (WidgetSortMode) -> Unit,
  onAutomaticUpdateChecksChange: (Boolean) -> Unit,
  onCheckUpdate: () -> Unit,
  onInstallUpdate: () -> Unit,
  onDismissUpdate: () -> Unit,
  onIgnoreUpdate: () -> Unit,
  onClearUpdateMessage: () -> Unit,
  onPinWidget: (String) -> Unit,
  onClearWidgetPinMessage: () -> Unit,
  onOpenMediaAccessSettings: () -> Unit,
) {
  PampaWidgetsTheme(settings = uiState.settings) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.updateMessage) {
      val message = uiState.updateMessage
      if (!message.isNullOrBlank()) {
        snackbarHostState.showSnackbar(message)
        onClearUpdateMessage()
      }
    }
    LaunchedEffect(uiState.widgetPinMessage) {
      val message = uiState.widgetPinMessage
      if (!message.isNullOrBlank()) {
        snackbarHostState.showSnackbar(message)
        onClearWidgetPinMessage()
      }
    }

    // La barra in vetro fluttua *sopra* il contenuto invece di occupare una fascia in fondo, e per
    // questo il contenuto le scorre sotto e si vede sfocato attraverso. E' anche il motivo per cui
    // non c'e' piu' uno Scaffold: la sua bottomBar toglie spazio, e non e' quello che vogliamo.
    val chromeController = rememberFluidChromeController()
    val fallbackBackdrop = rememberGlassBackdrop()
    val scrollToTop = remember { FluidScrollToTopBus() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val selectedRoute = tabItems.firstOrNull { currentDestination.isInHierarchy(it.route) }?.route

    Box(modifier = Modifier.fillMaxSize()) {
      ProvideFluidChrome(
        controller = chromeController,
        // Lo spazio che la barra occuperebbe viene restituito al contenuto come spaziatura in
        // fondo: le liste finiscono sopra la barra invece che dietro.
        bottomInset = FluidTabBarDefaults.ContentInset,
        scrollToTop = scrollToTop,
      ) {
      NavHost(
        navController = navController,
        startDestination = AppRoutes.Store,
      ) {
        composable(AppRoutes.Store) {
          StoreScreen(
            uiState = uiState,
            onSearchQueryChange = onSearchQueryChange,
            onStoreLayoutChange = onStoreLayoutChange,
            onWidgetSortModeChange = onWidgetSortModeChange,
            onWidgetClick = { widgetId -> navController.navigate(AppRoutes.widgetDetail(widgetId)) },
            onInstallUpdate = onInstallUpdate,
            onDismissUpdate = onDismissUpdate,
          )
        }
        composable(AppRoutes.Settings) {
          SettingsScreen(
            uiState = uiState,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange,
            onStoreLayoutChange = onStoreLayoutChange,
            onWidgetSortModeChange = onWidgetSortModeChange,
            onAutomaticUpdateChecksChange = onAutomaticUpdateChecksChange,
            onCheckUpdate = onCheckUpdate,
            onInstallUpdate = onInstallUpdate,
            onIgnoreUpdate = onIgnoreUpdate,
            mediaNotificationAccessGranted = uiState.mediaNotificationAccessGranted,
            onOpenMediaAccessSettings = onOpenMediaAccessSettings,
          )
        }
        composable(AppRoutes.WidgetDetail) { entry ->
          val widgetId = entry.arguments?.getString("widgetId").orEmpty()
          WidgetDetailScreen(
            widget = uiState.widgets.firstOrNull { it.id == widgetId },
            onBack = { navController.popBackStack() },
            mediaNotificationAccessGranted = uiState.mediaNotificationAccessGranted,
            onPinWidget = onPinWidget,
            onOpenMediaAccessSettings = onOpenMediaAccessSettings,
          )
        }
      }
      }

      FluidTabBar(
        items = tabItems,
        selectedRoute = selectedRoute,
        onSelect = { item ->
          navController.navigate(item.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
          }
        },
        onReselect = { scrollToTop.request() },
        backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop,
        modifier = Modifier.align(Alignment.BottomCenter),
      )

      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = FluidTabBarDefaults.ContentInset),
      )
    }
  }
}

private fun NavDestination?.isInHierarchy(route: String): Boolean {
  return this?.hierarchy?.any { it.route == route } == true
}
