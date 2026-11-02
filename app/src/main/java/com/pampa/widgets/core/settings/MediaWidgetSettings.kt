package com.pampa.widgets.core.settings

import com.pampa.widgets.core.media.MediaPackages
import kotlinx.serialization.Serializable

@Serializable
enum class MediaSourceMode(val label: String, val packageName: String?) {
  Automatic("Automatica", null),
  Spotify("Spotify", MediaPackages.Spotify),
  Fluidify("Fluidify", MediaPackages.Fluidify);

  fun accepts(packageName: String): Boolean = this.packageName == null || this.packageName == packageName
}

/** Immutable instance settings: editing a copy has no effect until the repository saves it. */
@Serializable
data class MediaWidgetSettings(
  val source: MediaSourceMode = MediaSourceMode.Automatic,
  val theme: MediaWidgetTheme = MediaWidgetTheme.SamsungGlass,
  val artworkSize: MediaWidgetArtworkSize = MediaWidgetArtworkSize.Large,
  val showSource: Boolean = false,
  val showArtist: Boolean = true,
  val keepLastSong: Boolean = true,
  val instantControls: Boolean = false,
  val animatedFeedback: Boolean = true,
) {
  fun applyTo(app: AppSettings = AppSettings()) = app.copy(
    mediaWidgetTheme = theme, mediaWidgetArtworkSize = artworkSize,
    mediaWidgetShowSource = showSource, mediaWidgetShowArtist = showArtist,
    mediaWidgetKeepLastSong = keepLastSong, mediaWidgetInstantControls = instantControls,
    mediaWidgetAnimatedFeedback = animatedFeedback,
  )

  companion object {
    fun fromLegacy(app: AppSettings) = MediaWidgetSettings(
      theme = app.mediaWidgetTheme, artworkSize = app.mediaWidgetArtworkSize,
      showSource = app.mediaWidgetShowSource, showArtist = app.mediaWidgetShowArtist,
      keepLastSong = app.mediaWidgetKeepLastSong, instantControls = app.mediaWidgetInstantControls,
      animatedFeedback = app.mediaWidgetAnimatedFeedback,
    )
  }
}
