package com.pampa.widgets.core.widget

import androidx.compose.ui.graphics.vector.ImageVector

enum class WidgetCategory {
  Media,
  Time,
  System,
  Utility,
}

enum class WidgetAvailability {
  Available,
  ComingSoon,
  Hidden,
}

data class WidgetSize(
  val minColumns: Int,
  val minRows: Int,
  val preferredColumns: Int = minColumns,
  val preferredRows: Int = minRows,
)

data class WidgetPreview(
  val title: String,
  val subtitle: String = "",
  val accentLabel: String = "",
)

data class WidgetDefinition(
  val id: String,
  val name: String,
  val shortDescription: String,
  val category: WidgetCategory,
  val sizes: List<WidgetSize> = emptyList(),
  val availability: WidgetAvailability = WidgetAvailability.Available,
  val icon: ImageVector? = null,
  val preview: WidgetPreview? = null,
  val capabilities: List<String> = emptyList(),
  val appWidgetProviderClassName: String? = null,
  val glanceReceiverClassName: String? = null,
  val requiresConfiguration: Boolean = false,
  val permissionHints: List<String> = emptyList(),
)
