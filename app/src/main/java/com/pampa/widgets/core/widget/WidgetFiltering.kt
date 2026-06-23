package com.pampa.widgets.core.widget

import com.pampa.widgets.core.settings.WidgetSortMode

fun filterAndSortWidgets(
  widgets: List<WidgetDefinition>,
  query: String,
  sortMode: WidgetSortMode,
): List<WidgetDefinition> {
  val normalizedQuery = query.trim().lowercase()
  val filtered = if (normalizedQuery.isBlank()) {
    widgets
  } else {
    widgets.filter { widget ->
      widget.name.lowercase().contains(normalizedQuery) ||
        widget.shortDescription.lowercase().contains(normalizedQuery) ||
        widget.category.name.lowercase().contains(normalizedQuery) ||
        widget.capabilities.any { it.lowercase().contains(normalizedQuery) }
    }
  }

  return when (sortMode) {
    WidgetSortMode.Featured -> filtered.sortedWith(
      compareBy<WidgetDefinition> { it.availability.ordinal }
        .thenBy { it.category.name }
        .thenBy { it.name },
    )
    WidgetSortMode.Name -> filtered.sortedBy { it.name.lowercase() }
  }
}
