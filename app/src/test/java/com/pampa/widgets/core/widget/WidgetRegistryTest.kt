package com.pampa.widgets.core.widget

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetRegistryTest {
  @Test
  fun localRegistryContainsMediaWidget() = runTest {
    val registry = LocalWidgetRegistry()
    val widgets = registry.observeWidgets().first()

    assertEquals(1, widgets.size)
    assertEquals("media-controls", widgets.first().id)
    assertEquals(WidgetCategory.Media, widgets.first().category)
    assertNotNull(widgets.first().appWidgetProviderClassName)
    assertNull(registry.getWidget("missing"))
  }
}
