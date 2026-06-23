package com.pampa.widgets.core.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

sealed interface WidgetPinResult {
  data object Requested : WidgetPinResult
  data object NotFound : WidgetPinResult
  data object NoProvider : WidgetPinResult
  data object Unsupported : WidgetPinResult
}

interface WidgetPinService {
  suspend fun requestPin(widgetId: String): WidgetPinResult
}

@Singleton
class AndroidWidgetPinService @Inject constructor(
  @ApplicationContext private val context: Context,
  private val registry: WidgetRegistry,
) : WidgetPinService {
  override suspend fun requestPin(widgetId: String): WidgetPinResult {
    val widget = registry.getWidget(widgetId) ?: return WidgetPinResult.NotFound
    val providerClassName = widget.appWidgetProviderClassName
      ?: return if (widget.glanceReceiverClassName.isNullOrBlank()) {
        WidgetPinResult.NoProvider
      } else {
        WidgetPinResult.Unsupported
      }

    val appWidgetManager = AppWidgetManager.getInstance(context)
    if (!appWidgetManager.isRequestPinAppWidgetSupported) return WidgetPinResult.Unsupported

    val provider = ComponentName(context.packageName, providerClassName)
    return if (appWidgetManager.requestPinAppWidget(provider, null, null)) {
      WidgetPinResult.Requested
    } else {
      WidgetPinResult.Unsupported
    }
  }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetPinModule {
  @Binds
  abstract fun bindWidgetPinService(implementation: AndroidWidgetPinService): WidgetPinService
}
