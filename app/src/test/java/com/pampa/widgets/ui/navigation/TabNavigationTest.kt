package com.pampa.widgets.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabNavigationTest {
  @Test fun tabsArePeersInBarOrder() {
    assertEquals(1, tabPeerDirection(AppRoutes.Store, AppRoutes.MyWidgets))
    assertEquals(1, tabPeerDirection(AppRoutes.Store, AppRoutes.Settings))
    assertEquals(-1, tabPeerDirection(AppRoutes.Settings, AppRoutes.MyWidgets))
  }

  @Test fun theDetailIsAPageNotAPeer() {
    // Zero vuol dire "pagina": entra da destra e torna indietro sotto il dito.
    assertEquals(0, tabPeerDirection(AppRoutes.Store, AppRoutes.WidgetDetail))
    assertEquals(0, tabPeerDirection(AppRoutes.WidgetDetail, AppRoutes.Store))
  }

  @Test fun theDetailKeepsTheCatalogueTabLit() {
    assertEquals(AppRoutes.Store, selectedTabFor(AppRoutes.WidgetDetail))
    assertEquals(AppRoutes.Settings, selectedTabFor(AppRoutes.Settings))
    assertNull(selectedTabFor(null))
  }
}
