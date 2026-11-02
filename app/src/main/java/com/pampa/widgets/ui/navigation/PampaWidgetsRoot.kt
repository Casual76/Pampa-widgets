package com.pampa.widgets.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pampa.widgets.core.design.PampaWidgetsTheme
import com.pampa.widgets.core.widget.WidgetPinResult
import com.pampa.widgets.ui.MainActions
import com.pampa.widgets.ui.MainUiState
import com.pampa.widgets.ui.detail.WidgetDetailScreen
import com.pampa.widgets.ui.settings.SettingsScreen
import com.pampa.widgets.ui.store.MyWidgetsScreen
import com.pampa.widgets.ui.store.rememberMyWidgetsState
import com.pampa.widgets.ui.store.StoreScreen
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationTone
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidUpdatePromptSheet
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPeerMove
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import kotlinx.coroutines.launch

/**
 * Le tre schede, nell'ordine della barra.
 *
 * L'ordine e' anche quello del passo fra schede: [tabPeer] lo legge da qui, cosi' la scheda nuova
 * entra dal lato in cui sta nella barra invece che sempre da destra come una pagina spinta.
 */
internal val tabItems = listOf(
  FluidTabItem(route = AppRoutes.Store, label = "Widget", icon = Icons.Rounded.Widgets),
  FluidTabItem(route = AppRoutes.MyWidgets, label = "I miei", icon = Icons.Rounded.Home),
  FluidTabItem(route = AppRoutes.Settings, label = "Impostazioni", icon = Icons.Rounded.Settings),
)

/** La posizione di una rotta fra le schede, o -1 se non e' una scheda (il dettaglio di un widget). */
internal fun tabIndexOf(route: String?): Int = tabItems.indexOfFirst { it.route == route }

/**
 * Il passo fra due schede: positivo se [to] sta dopo [from] nella barra, zero se una delle due non e'
 * una scheda. Zero vuol dire "pagina": il dettaglio entra da destra e torna indietro sotto il dito.
 */
internal fun tabPeerDirection(from: String?, to: String?): Int {
  val a = tabIndexOf(from)
  val b = tabIndexOf(to)
  return if (a < 0 || b < 0) 0 else b.compareTo(a)
}

private val tabPeer = FluidPeerMove<NavBackStackEntry> { from, to ->
  tabPeerDirection(from.destination.route, to.destination.route)
}

/**
 * La scheda accesa per la rotta in cima. Il dettaglio di un widget si apre solo dal catalogo, e la
 * barra resta sulla sua scheda: e' la pagina dentro "Widget", non un posto a se'.
 */
internal fun selectedTabFor(route: String?): String? = when {
  tabIndexOf(route) >= 0 -> route
  route == AppRoutes.WidgetDetail -> AppRoutes.Store
  else -> null
}

@Composable
fun PampaWidgetsRoot(
  uiState: MainUiState,
  actions: MainActions,
) {
  PampaWidgetsTheme(settings = uiState.settings) {
    val navController = rememberFluidNavController()
    val pageState = rememberFluidPageHostState()
    val chromeController = rememberFluidChromeController()
    val modalHost = rememberFluidGlassModalHostState()
    val notificationHost = rememberFluidNotificationHostState()
    val fallbackBackdrop = rememberGlassBackdrop()
    val backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop
    val scrollToTop = remember { FluidScrollToTopBus() }
    val tabBarScroll = rememberFluidFloatingTabBarScrollConnection()
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedRoute = selectedTabFor(currentRoute)

    // A ogni cambio di pagina la barra torna aperta e smette di tenere il vetro fermo: una lista
    // tolta a meta' gesto non consegna mai il suo lancio, e la barra restava ripiegata (o col vetro
    // congelato) sulla pagina nuova, che magari non scorre nemmeno.
    LaunchedEffect(currentRoute) {
      tabBarScroll.settle()
      tabBarScroll.expand()
    }

    val myWidgets = rememberMyWidgetsState()

    // Il foglio dell'aggiornamento: lo apre l'avviso del catalogo, si chiude con «Più tardi».
    var updateSheetOpen by rememberSaveable { mutableStateOf(false) }

    // L'esito della richiesta al launcher, come notifica di vetro in cima: la vecchia Snackbar di
    // Material stava in fondo, sotto la barra, e non era vetro.
    var notificationCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(uiState.widgetPinMessage) {
      val result = uiState.widgetPinMessage ?: return@LaunchedEffect
      actions.onClearWidgetPinMessage()
      val id = "pin-${++notificationCount}"
      scope.launch { notificationHost.show(result.toNotification(id)) }
    }

    CompositionLocalProvider(
      LocalFluidGlassModalHostState provides modalHost,
      LocalFluidNotificationHostState provides notificationHost,
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        // La barra in vetro galleggia sopra le pagine e lo spazio che occuperebbe torna al contenuto
        // come margine in fondo: le liste finiscono sopra la barra, non dietro.
        ProvideFluidChrome(
          controller = chromeController,
          bottomInset = FluidFloatingTabBarDefaults.ContentInset,
          scrollToTop = scrollToTop,
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .nestedScroll(tabBarScroll)
              .fluidGlassModalObscured(),
          ) {
            FluidNavHost(
              navController = navController,
              startDestination = AppRoutes.Store,
              modifier = Modifier.fillMaxSize(),
              peer = tabPeer,
              state = pageState,
            ) {
              composable(AppRoutes.Store) {
                StoreScreen(
                  uiState = uiState,
                  actions = actions,
                  onWidgetClick = { widgetId -> navController.navigate(AppRoutes.widgetDetail(widgetId)) },
                  onOpenUpdate = { updateSheetOpen = true },
                )
              }
              composable(AppRoutes.MyWidgets) {
                MyWidgetsScreen(
                  app = uiState.settings,
                  onCatalog = { navigateToTab(navController, AppRoutes.Store) },
                  stateHolder = myWidgets,
                )
              }
              composable(AppRoutes.Settings) {
                SettingsScreen(uiState = uiState, actions = actions)
              }
              composable(AppRoutes.WidgetDetail) { entry ->
                val widgetId = entry.arguments?.getString("widgetId").orEmpty()
                WidgetDetailScreen(
                  widget = uiState.widgets.firstOrNull { it.id == widgetId },
                  onBack = { navController.popBackStack() },
                  mediaNotificationAccessGranted = uiState.mediaNotificationAccessGranted,
                  onPinWidget = actions.onPinWidget,
                  onOpenMediaAccessSettings = actions.onOpenMediaAccessSettings,
                )
              }
            }
          }
        }

        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
              horizontal = FluidFloatingTabBarDefaults.HorizontalMargin,
              vertical = FluidFloatingTabBarDefaults.BottomMargin,
            ),
        ) {
          FluidFloatingTabBar(
            items = tabItems,
            selectedRoute = selectedRoute,
            onSelect = { item -> navigateToTab(navController, item.route) },
            // Un secondo tocco sulla scheda gia' scelta: sopra una pagina torna alla scheda, sulla
            // scheda torna in cima.
            onReselect = { item ->
              if (currentRoute != item.route) {
                navController.popBackStack(item.route, inclusive = false)
              } else {
                scrollToTop.request()
              }
            },
            scrollConnection = tabBarScroll,
            backdrop = backdrop,
          )
        }

        FluidUpdatePromptSheet(
          state = if (updateSheetOpen) uiState.update.ui else null,
          onInstall = actions.onInstallUpdate,
          onLater = {
            updateSheetOpen = false
            actions.onDismissUpdate()
          },
          onSkip = {
            updateSheetOpen = false
            actions.onIgnoreUpdate()
          },
        )

        // Sopra la barra: un pop-up sotto cui la capsula di navigazione puo' passare non e' un modale.
        FluidGlassModalHost(state = modalHost, backdrop = backdrop)
        FluidNotificationHost(
          state = notificationHost,
          backdrop = backdrop,
          modifier = Modifier.align(Alignment.TopCenter),
        )
      }
    }
  }
}

/** Le schede si scambiano tenendo lo stato di ciascuna, e la radice resta il catalogo. */
private fun navigateToTab(navController: androidx.navigation.NavHostController, route: String) {
  navController.navigate(route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
}

private fun WidgetPinResult.toNotification(id: String): FluidNotification = when (this) {
  WidgetPinResult.Requested -> FluidNotification(
    id = id,
    title = "Aggiungi alla home",
    message = "Richiesta inviata al launcher: conferma dove metterlo.",
    tone = FluidNotificationTone.Success,
    durationMillis = 4_000L,
  )
  WidgetPinResult.NotFound -> FluidNotification(
    id = id,
    title = "Widget non trovato",
    message = "Questo widget non è più nel catalogo.",
    tone = FluidNotificationTone.Error,
  )
  WidgetPinResult.NoProvider -> FluidNotification(
    id = id,
    title = "Non ancora installabile",
    message = "Questo widget non ha ancora un provider da aggiungere alla home.",
    tone = FluidNotificationTone.Warning,
  )
  WidgetPinResult.Unsupported -> FluidNotification(
    id = id,
    title = "Il launcher non lo permette",
    message = "Tieni premuta la home, apri «Widget» e cerca Pampa Widgets.",
    tone = FluidNotificationTone.Warning,
  )
}
