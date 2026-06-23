package com.pampa.widgets.core.widget

import com.pampa.widgets.core.settings.WidgetSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFilteringTest {
  @Test
  fun emptyCatalogCanBeSearchedAndSorted() {
    val result = filterAndSortWidgets(
      widgets = emptyList(),
      query = "orologio",
      sortMode = WidgetSortMode.Name,
    )

    assertTrue(result.isEmpty())
  }

  @Test
  fun filtersByNameDescriptionCategoryAndCapability() {
    val media = WidgetDefinition(
      id = "media",
      name = "Media controls",
      shortDescription = "Controlli rapidi",
      category = WidgetCategory.Media,
      capabilities = listOf("playback"),
    )
    val clock = WidgetDefinition(
      id = "clock",
      name = "Clock",
      shortDescription = "Ora locale",
      category = WidgetCategory.Time,
    )

    assertEquals(listOf(media), filterAndSortWidgets(listOf(media, clock), "play", WidgetSortMode.Featured))
    assertEquals(listOf(clock), filterAndSortWidgets(listOf(media, clock), "time", WidgetSortMode.Featured))
  }

  @Test
  fun nameSortIsAlphabetical() {
    val second = WidgetDefinition(
      id = "z",
      name = "Zen",
      shortDescription = "Secondo",
      category = WidgetCategory.Utility,
    )
    val first = WidgetDefinition(
      id = "a",
      name = "Alpha",
      shortDescription = "Primo",
      category = WidgetCategory.System,
    )

    assertEquals(
      listOf("a", "z"),
      filterAndSortWidgets(listOf(second, first), "", WidgetSortMode.Name).map { it.id },
    )
  }
}
