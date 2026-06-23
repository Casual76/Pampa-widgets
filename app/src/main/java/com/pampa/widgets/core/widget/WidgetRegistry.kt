package com.pampa.widgets.core.widget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import com.pampa.widgets.widget.media.MediaWidgetProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface WidgetRegistry {
  fun observeWidgets(): Flow<List<WidgetDefinition>>
  suspend fun getWidget(id: String): WidgetDefinition?
}

@Singleton
class LocalWidgetRegistry @Inject constructor() : WidgetRegistry {
  private val widgets: List<WidgetDefinition> = listOf(
    WidgetDefinition(
      id = "media-controls",
      name = "Media Controls",
      shortDescription = "Controlli multimediali per Spotify, YouTube Music e sessioni Android attive.",
      category = WidgetCategory.Media,
      sizes = listOf(
        WidgetSize(minColumns = 4, minRows = 2, preferredColumns = 4, preferredRows = 2),
      ),
      icon = Icons.Rounded.GraphicEq,
      preview = WidgetPreview(
        title = "Traccia in riproduzione",
        subtitle = "Play, pausa, avanti e indietro direttamente dalla home.",
        accentLabel = "Spotify ready",
      ),
      capabilities = listOf(
        "Spotify",
        "YouTube Music",
        "Play/Pausa",
        "Traccia successiva",
        "Traccia precedente",
        "MediaSession",
      ),
      appWidgetProviderClassName = MediaWidgetProvider::class.java.name,
      permissionHints = listOf(
        "Richiede l'accesso notifiche per leggere e controllare le sessioni multimediali.",
      ),
    ),
  )

  override fun observeWidgets(): Flow<List<WidgetDefinition>> = flowOf(widgets)

  override suspend fun getWidget(id: String): WidgetDefinition? {
    return widgets.firstOrNull { it.id == id }
  }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetRegistryModule {
  @Binds
  abstract fun bindWidgetRegistry(implementation: LocalWidgetRegistry): WidgetRegistry
}
