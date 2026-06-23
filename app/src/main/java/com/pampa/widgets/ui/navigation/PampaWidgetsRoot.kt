package com.pampa.widgets.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

private data class BottomDestination(
  val route: String,
  val label: String,
  val icon: @Composable () -> Unit,
)

private val bottomDestinations = listOf(
  BottomDestination(
    route = AppRoutes.Store,
    label = "Store",
    icon = { Icon(Icons.Rounded.Widgets, contentDescription = null) },
  ),
  BottomDestination(
    route = AppRoutes.Settings,
    label = "Impostazioni",
    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
  ),
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

    Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination
        NavigationBar {
          bottomDestinations.forEach { destination ->
            NavigationBarItem(
              selected = currentDestination.isInHierarchy(destination.route),
              onClick = {
                navController.navigate(destination.route) {
                  popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                  }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = destination.icon,
              label = { Text(destination.label) },
            )
          }
        }
      },
    ) { paddingValues ->
      NavHost(
        navController = navController,
        startDestination = AppRoutes.Store,
        modifier = Modifier.padding(paddingValues),
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
  }
}

private fun NavDestination?.isInHierarchy(route: String): Boolean {
  return this?.hierarchy?.any { it.route == route } == true
}
