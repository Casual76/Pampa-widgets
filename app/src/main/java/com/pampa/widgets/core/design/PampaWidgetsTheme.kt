package com.pampa.widgets.core.design

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.ThemeMode

private val LightColors = lightColorScheme(
  primary = Color(0xFF146C5A),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFB6F1DA),
  onPrimaryContainer = Color(0xFF002018),
  secondary = Color(0xFF4B635B),
  tertiary = Color(0xFF3D6375),
  background = Color(0xFFF8FBF7),
  surface = Color(0xFFF8FBF7),
  surfaceContainer = Color(0xFFECEFEB),
  surfaceContainerHigh = Color(0xFFE6EAE5),
)

private val DarkColors = darkColorScheme(
  primary = Color(0xFF9AD7C2),
  onPrimary = Color(0xFF00382C),
  primaryContainer = Color(0xFF005141),
  onPrimaryContainer = Color(0xFFB6F1DA),
  secondary = Color(0xFFB9CCC4),
  tertiary = Color(0xFFA7CDDF),
  background = Color(0xFF101512),
  surface = Color(0xFF101512),
  surfaceContainer = Color(0xFF1C211E),
  surfaceContainerHigh = Color(0xFF272C29),
)

private val AppShapes = Shapes(
  extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
  small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
  medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
  large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
  extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PampaWidgetsTheme(
  settings: AppSettings,
  content: @Composable () -> Unit,
) {
  val systemDark = isSystemInDarkTheme()
  val darkTheme = when (settings.themeMode) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
  }
  val context = LocalContext.current
  val colorScheme = when {
    settings.dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColors
    else -> LightColors
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography(),
    shapes = AppShapes,
    motionScheme = MotionScheme.expressive(),
    content = content,
  )
}
