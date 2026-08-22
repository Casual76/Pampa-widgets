package com.pampa.widgets.widget.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.test.platform.app.InstrumentationRegistry
import com.pampa.widgets.R
import com.pampa.widgets.core.media.MediaPackages
import com.pampa.widgets.core.media.MediaPlaybackAvailability
import com.pampa.widgets.core.media.MediaPlaybackSnapshot
import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.MediaWidgetArtworkSize
import com.pampa.widgets.core.settings.MediaWidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import kotlin.math.roundToInt

class MediaWidgetRemoteViewsInstrumentedTest {
  private data class SizeCase(
    val label: String,
    val widthDp: Int,
    val heightDp: Int,
  )

  private data class VisualCase(
    val size: SizeCase,
    val settings: AppSettings,
    val snapshot: MediaPlaybackSnapshot,
    val expectText: Boolean,
    val expectProgress: Boolean,
    val expectMeta: Boolean,
    val expectExtraActions: Boolean,
  )

  private val sizes = listOf(
    SizeCase("S0 mini", 110, 110),
    SizeCase("S1 compact", 220, 170),
    SizeCase("S2 default", 300, 210),
    SizeCase("S3 wide", 430, 220),
    SizeCase("S4 tall", 320, 320),
    SizeCase("S5 full", 520, 620),
  )

  @Test
  fun remoteViewsApplyAcrossRepresentativeSizesSettingsAndStates() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val snapshots = listOf(activeSnapshot(artwork = false), activeSnapshot(artwork = true), permissionSnapshot())
    val settingsCases = MediaWidgetTheme.entries.flatMap { theme ->
      MediaWidgetArtworkSize.entries.map { artworkSize ->
        AppSettings(
          mediaWidgetTheme = theme,
          mediaWidgetArtworkSize = artworkSize,
          mediaWidgetShowSource = true,
          mediaWidgetShowArtist = true,
          mediaWidgetKeepLastSong = true,
          mediaWidgetInstantControls = true,
          mediaWidgetAnimatedFeedback = true,
        )
      }
    }

    instrumentation.runOnMainSync {
      sizes.forEach { size ->
        settingsCases.forEach { settings ->
          snapshots.forEach { snapshot ->
            val root = MediaWidgetUpdater.buildRemoteViewsForTest(
              context = context,
              snapshot = snapshot,
              settings = settings,
              widthDp = size.widthDp,
              heightDp = size.heightDp,
            ).apply(context, FrameLayout(context))

            assertNotNull("${size.label} root", root.findViewById(R.id.media_widget_root))
            assertNotNull("${size.label} play", root.findViewById(R.id.media_widget_play_pause))
            assertNotNull("${size.label} artwork", root.findViewById(R.id.media_widget_artwork_frame))
            assertNotNull("${size.label} progress", root.findViewById(R.id.media_widget_progress))
            assertNotNull("${size.label} background flipper", root.findViewById(R.id.media_widget_background_flipper))
            assertNotNull("${size.label} artwork flipper", root.findViewById(R.id.media_widget_artwork_flipper))
            assertNotNull("${size.label} glyph flipper", root.findViewById(R.id.media_widget_play_pause_glyph_flipper))

            val extraActions = root.findViewById<View>(R.id.media_widget_extra_actions)
            if (size.label == "S5 full") {
              assertEquals("${size.label} extra actions", View.VISIBLE, extraActions.visibility)
            }
            if (size.label == "S0 mini") {
              assertEquals("${size.label} extra actions", View.GONE, extraActions.visibility)
              assertEquals(
                "${size.label} text collapses when needed",
                View.GONE,
                root.findViewById<View>(R.id.media_widget_text_stack).visibility,
              )
            }
          }
        }
      }
    }
  }

  @Test
  fun remoteViewsMeasureDrawAndUseAvailableSpaceAcrossResizeProfiles() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val density = context.resources.displayMetrics.density
    val cases = listOf(
      VisualCase(
        size = sizes[0],
        settings = AppSettings(
          mediaWidgetTheme = MediaWidgetTheme.AlbumColor,
          mediaWidgetArtworkSize = MediaWidgetArtworkSize.Large,
        ),
        snapshot = activeSnapshot(artwork = true),
        expectText = false,
        expectProgress = false,
        expectMeta = false,
        expectExtraActions = false,
      ),
      VisualCase(
        size = sizes[1],
        settings = AppSettings(
          mediaWidgetTheme = MediaWidgetTheme.AdaptiveGlass,
          mediaWidgetArtworkSize = MediaWidgetArtworkSize.Compact,
        ),
        snapshot = permissionSnapshot(),
        expectText = true,
        expectProgress = true,
        expectMeta = false,
        expectExtraActions = false,
      ),
      VisualCase(
        size = sizes[2],
        settings = AppSettings(
          mediaWidgetTheme = MediaWidgetTheme.SamsungGlass,
          mediaWidgetArtworkSize = MediaWidgetArtworkSize.Balanced,
        ),
        snapshot = activeSnapshot(artwork = true),
        expectText = true,
        expectProgress = true,
        expectMeta = false,
        expectExtraActions = false,
      ),
      VisualCase(
        size = sizes[3],
        settings = AppSettings(
          mediaWidgetTheme = MediaWidgetTheme.LightGlass,
          mediaWidgetArtworkSize = MediaWidgetArtworkSize.Large,
        ),
        snapshot = activeSnapshot(artwork = false),
        expectText = true,
        expectProgress = true,
        expectMeta = false,
        expectExtraActions = false,
      ),
      VisualCase(
        size = sizes[4],
        settings = AppSettings(
          mediaWidgetTheme = MediaWidgetTheme.DarkGlass,
          mediaWidgetArtworkSize = MediaWidgetArtworkSize.Balanced,
        ),
        snapshot = activeSnapshot(artwork = true),
        expectText = true,
        expectProgress = true,
        expectMeta = true,
        expectExtraActions = true,
      ),
      VisualCase(
        size = sizes[5],
        settings = AppSettings(
          mediaWidgetTheme = MediaWidgetTheme.AlbumColor,
          mediaWidgetArtworkSize = MediaWidgetArtworkSize.Large,
        ),
        snapshot = activeSnapshot(artwork = true),
        expectText = true,
        expectProgress = true,
        expectMeta = true,
        expectExtraActions = true,
      ),
    )

    instrumentation.runOnMainSync {
      cases.forEach { testCase ->
        val widthPx = (testCase.size.widthDp * density).roundToInt()
        val heightPx = (testCase.size.heightDp * density).roundToInt()
        val parent = FrameLayout(context).apply {
          layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
        }
        val root = MediaWidgetUpdater.buildRemoteViewsForTest(
          context = context,
          snapshot = testCase.snapshot,
          settings = testCase.settings,
          widthDp = testCase.size.widthDp,
          heightDp = testCase.size.heightDp,
        ).apply(context, parent)
        parent.addView(root, FrameLayout.LayoutParams(widthPx, heightPx, Gravity.CENTER))
        parent.measure(
          View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, widthPx, heightPx)

        val label = testCase.size.label
        assertEquals("$label measured width", widthPx, parent.measuredWidth)
        assertEquals("$label measured height", heightPx, parent.measuredHeight)
        assertFitsInside("$label background", root.findViewById(R.id.media_widget_background), widthPx, heightPx)
        assertFitsInside("$label artwork", root.findViewById(R.id.media_widget_artwork_frame), widthPx, heightPx)
        assertFitsInside("$label controls", root.findViewById(R.id.media_widget_controls), widthPx, heightPx)
        assertFitsInside("$label play", root.findViewById(R.id.media_widget_play_pause), widthPx, heightPx)

        assertVisibility("$label text stack", root.findViewById(R.id.media_widget_text_stack), testCase.expectText)
        assertReservedSpace("$label progress", root.findViewById(R.id.media_widget_progress), testCase.expectProgress)
        assertVisibility("$label meta row", root.findViewById(R.id.media_widget_meta_row), testCase.expectMeta)
        assertVisibility(
          "$label extra actions",
          root.findViewById(R.id.media_widget_extra_actions),
          testCase.expectExtraActions,
        )

        if (testCase.expectText) {
          assertFitsInside("$label title", root.findViewById(R.id.media_widget_title), widthPx, heightPx)
        }
        if (testCase.expectMeta) {
          assertFitsInside("$label status", root.findViewById(R.id.media_widget_status), widthPx, heightPx)
          assertFitsInside("$label time", root.findViewById(R.id.media_widget_time), widthPx, heightPx)
        }
        if (testCase.expectExtraActions) {
          assertFitsInside("$label refresh", root.findViewById(R.id.media_widget_refresh), widthPx, heightPx)
          assertFitsInside("$label open media", root.findViewById(R.id.media_widget_open_media), widthPx, heightPx)
          assertFitsInside("$label open pampa", root.findViewById(R.id.media_widget_open_pampa), widthPx, heightPx)
        }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        parent.draw(Canvas(bitmap))
        assertRenderedBitmapLooksAlive(label, bitmap)
      }
    }
  }

  @Test
  fun playPauseGlyphUsesTheConfirmedPlaybackState() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val settings = AppSettings(mediaWidgetAnimatedFeedback = true)

    instrumentation.runOnMainSync {
      listOf(false, true).forEach { isPlaying ->
        val root = MediaWidgetUpdater.buildRemoteViewsForTest(
          context = context,
          snapshot = activeSnapshot(artwork = true).copy(isPlaying = isPlaying),
          settings = settings,
          widthDp = 300,
          heightDp = 210,
        ).apply(context, FrameLayout(context))
        val glyphFlipper = root.findViewById<ViewFlipper>(R.id.media_widget_play_pause_glyph_flipper)
        val expectedGlyph = context.getDrawable(
          if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        assertEquals(
          "fresh RemoteViews keeps the stable default child for confirmed state $isPlaying",
          0,
          glyphFlipper.displayedChild,
        )
        assertEquals(
          "visible glyph reflects confirmed state $isPlaying",
          expectedGlyph?.let(::drawableAlphaMaskHash),
          drawableAlphaMaskHash((glyphFlipper.currentView as ImageView).drawable),
        )
      }
    }
  }

  @Test
  fun disabledFeedbackKeepsThePlayPauseGlyphOnAStaticLayer() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val settings = AppSettings(mediaWidgetAnimatedFeedback = false)

    instrumentation.runOnMainSync {
      listOf(false, true).forEach { isPlaying ->
        val root = MediaWidgetUpdater.buildRemoteViewsForTest(
          context = context,
          snapshot = activeSnapshot(artwork = true).copy(isPlaying = isPlaying),
          settings = settings,
          widthDp = 300,
          heightDp = 210,
        ).apply(context, FrameLayout(context))
        val glyphFlipper = root.findViewById<ViewFlipper>(R.id.media_widget_play_pause_glyph_flipper)
        assertEquals("disabled feedback uses a non-animated child", 0, glyphFlipper.displayedChild)
      }
    }
  }

  @Test
  fun rootLaunchResolverUsesRememberedSpotifyAndNeverPampaSettings() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    assumeNotNull(context.packageManager.getLaunchIntentForPackage(MediaPackages.Spotify))

    val rememberedSpotify = mediaAppLaunchIntent(
      context = context,
      snapshot = MediaPlaybackSnapshot(
        availability = MediaPlaybackAvailability.NoSession,
        title = "Last song",
        artist = "Artist",
        sourceLabel = "Spotify",
        packageName = MediaPackages.Spotify,
        isFromCache = true,
      ),
    )
    assertNotNull(rememberedSpotify)
    val targetPackage = rememberedSpotify?.component?.packageName ?: rememberedSpotify?.`package`
    assertEquals(MediaPackages.Spotify, targetPackage)

    val noTarget = mediaAppLaunchIntent(
      context = context,
      snapshot = MediaPlaybackSnapshot(
        availability = MediaPlaybackAvailability.NoSession,
        title = "Nessuna riproduzione",
        artist = "Apri un'app musicale",
        sourceLabel = "In attesa",
      ),
    )
    assertNull("unknown media target never resolves to Pampa/settings", noTarget)
  }

  @Test
  fun sequentialAtomicUpdatesKeepTextArtworkPaletteAndCachedReinflateCoherent() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val widgetId = -9_041
    val settings = AppSettings(
      mediaWidgetTheme = MediaWidgetTheme.AlbumColor,
      mediaWidgetAnimatedFeedback = true,
    )

    instrumentation.runOnMainSync {
      MediaWidgetUpdater.resetVisualStateForTest(widgetId)
      try {
        val red = activeSnapshot(artwork = true).copy(
          title = "Red song",
          mediaId = "red",
          artwork = coloredArtwork(Color.RED),
          artworkKey = "red-art",
        )
        val firstViews = MediaWidgetUpdater.buildRemoteViewsSequenceForTest(
          context, widgetId, red, settings, widthDp = 300, heightDp = 320,
        )
        val root = firstViews.apply(context, FrameLayout(context))
        assertCoherentFrame(root, "Red song", expectedArtworkColor = Color.RED)

        val blueLoading = red.copy(
          title = "Blue song",
          mediaId = "blue",
          artwork = null,
          artworkKey = "",
        )
        val loadingViews = MediaWidgetUpdater.buildRemoteViewsSequenceForTest(
          context, widgetId, blueLoading, settings, widthDp = 300, heightDp = 320,
        )
        loadingViews.reapply(context, root)
        assertEquals("Blue song", root.findViewById<TextView>(R.id.media_widget_title).text.toString())
        assertBothSlotsPopulated(root, R.id.media_widget_background_flipper)
        assertBothSlotsPopulated(root, R.id.media_widget_artwork_flipper)
        assertHeavyLayersStayOnTheStableChild(root)

        val blueReady = blueLoading.copy(
          artwork = coloredArtwork(Color.BLUE),
          artworkKey = "blue-art",
        )
        val readyViews = MediaWidgetUpdater.buildRemoteViewsSequenceForTest(
          context, widgetId, blueReady, settings, widthDp = 300, heightDp = 320,
        )
        readyViews.reapply(context, root)
        assertCoherentFrame(root, "Blue song", expectedArtworkColor = Color.BLUE)
        assertHeavyLayersStayOnTheStableChild(root)

        // Simulates launcher rotation/process restoration from AppWidgetService's cached full update.
        val reinflated = readyViews.apply(context, FrameLayout(context))
        assertCoherentFrame(reinflated, "Blue song", expectedArtworkColor = Color.BLUE)
      } finally {
        MediaWidgetUpdater.resetVisualStateForTest(widgetId)
      }
    }
  }

  private fun activeSnapshot(artwork: Boolean): MediaPlaybackSnapshot =
    MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.Active,
      title = "Long Song Title For Layout",
      artist = "Artist Name",
      sourceLabel = "Spotify",
      packageName = "com.spotify.music",
      isPlaying = true,
      canPlayPause = true,
      canSkipNext = true,
      canSkipPrevious = false,
      artwork = if (artwork) sampleArtwork() else null,
      positionMs = 64_000L,
      durationMs = 188_000L,
    )

  private fun permissionSnapshot(): MediaPlaybackSnapshot =
    MediaPlaybackSnapshot(
      availability = MediaPlaybackAvailability.PermissionRequired,
      title = "Nessuna riproduzione",
      artist = "Apri Spotify o YouTube Music",
      sourceLabel = "Media",
      canPlayPause = false,
      canSkipNext = false,
      canSkipPrevious = false,
    )

  private fun sampleArtwork(): Bitmap {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(38, 126, 210))
    return bitmap
  }

  private fun coloredArtwork(color: Int): Bitmap =
    Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

  private fun drawableAlphaMaskHash(drawable: Drawable): Int {
    val rendered = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val copy = drawable.constantState?.newDrawable()?.mutate() ?: drawable
    copy.setBounds(0, 0, rendered.width, rendered.height)
    copy.draw(Canvas(rendered))
    val alphaMask = IntArray(rendered.width * rendered.height)
    rendered.getPixels(alphaMask, 0, rendered.width, 0, 0, rendered.width, rendered.height)
    for (index in alphaMask.indices) {
      alphaMask[index] = if (Color.alpha(alphaMask[index]) > 32) 1 else 0
    }
    return alphaMask.contentHashCode()
  }

  private fun assertCoherentFrame(root: View, title: String, expectedArtworkColor: Int) {
    assertEquals(title, root.findViewById<TextView>(R.id.media_widget_title).text.toString())
    assertBothSlotsPopulated(root, R.id.media_widget_background_flipper)
    assertBothSlotsPopulated(root, R.id.media_widget_artwork_flipper)
    val artworkFlipper = root.findViewById<ViewFlipper>(R.id.media_widget_artwork_flipper)
    val artworkView = artworkFlipper.getChildAt(artworkFlipper.displayedChild) as ImageView
    val bitmap = (artworkView.drawable as BitmapDrawable).bitmap
    assertEquals(expectedArtworkColor, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    assertEquals(View.VISIBLE, root.findViewById<Chronometer>(R.id.media_widget_time).visibility)
  }

  private fun assertBothSlotsPopulated(root: View, flipperId: Int) {
    val flipper = root.findViewById<ViewFlipper>(flipperId)
    assertEquals(2, flipper.childCount)
    repeat(flipper.childCount) { index ->
      assertNotNull((flipper.getChildAt(index) as ImageView).drawable)
    }
  }

  private fun assertHeavyLayersStayOnTheStableChild(root: View) {
    assertEquals(0, root.findViewById<ViewFlipper>(R.id.media_widget_background_flipper).displayedChild)
    assertEquals(0, root.findViewById<ViewFlipper>(R.id.media_widget_artwork_flipper).displayedChild)
  }

  private fun assertVisibility(label: String, view: View, expectedVisible: Boolean) {
    if (expectedVisible) {
      assertEquals(label, View.VISIBLE, view.visibility)
      assertTrue("$label has width", view.width > 0)
      assertTrue("$label has height", view.height > 0)
    } else {
      assertFalse("$label hidden", view.visibility == View.VISIBLE)
    }
  }

  private fun assertReservedSpace(label: String, view: View, expectedPresent: Boolean) {
    if (expectedPresent) {
      assertFalse("$label is not gone", view.visibility == View.GONE)
      assertTrue("$label has width", view.width > 0)
      assertTrue("$label has height", view.height > 0)
    } else {
      assertEquals(label, View.GONE, view.visibility)
    }
  }

  private fun assertFitsInside(label: String, view: View, parentWidth: Int, parentHeight: Int) {
    assertEquals("$label visible", View.VISIBLE, view.visibility)
    assertTrue("$label laid out width", view.width > 0)
    assertTrue("$label laid out height", view.height > 0)
    assertTrue("$label left inside", view.left >= 0)
    assertTrue("$label top inside", view.top >= 0)
    assertTrue("$label right inside", view.right <= parentWidth)
    assertTrue("$label bottom inside", view.bottom <= parentHeight)
  }

  private fun assertRenderedBitmapLooksAlive(label: String, bitmap: Bitmap) {
    var sampled = 0
    var opaquePixels = 0
    val colors = LinkedHashSet<Int>()
    val stepX = (bitmap.width / 24).coerceAtLeast(1)
    val stepY = (bitmap.height / 24).coerceAtLeast(1)
    var y = 0
    while (y < bitmap.height) {
      var x = 0
      while (x < bitmap.width) {
        val color = bitmap.getPixel(x, y)
        sampled++
        if (Color.alpha(color) > 16) {
          opaquePixels++
          colors += color and 0x00FFFFFF
        }
        x += stepX
      }
      y += stepY
    }

    assertTrue("$label rendered opaque content", opaquePixels > sampled * 0.70f)
    assertTrue("$label rendered varied content", colors.size >= 8)
  }
}
