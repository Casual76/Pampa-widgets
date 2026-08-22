package com.pampa.widgets.widget.media

import com.pampa.widgets.core.settings.AppSettings
import com.pampa.widgets.core.settings.MediaWidgetArtworkSize
import com.pampa.widgets.core.settings.MediaWidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaWidgetLayoutSpecTest {
  private data class SizeCase(
    val label: String,
    val widthDp: Int,
    val heightDp: Int,
    val expectedClass: MediaWidgetSizeClass,
  )

  private val sizeCases = listOf(
    SizeCase("S0 2x2 mini", 110, 110, MediaWidgetSizeClass.Mini),
    SizeCase("S1 compact", 220, 170, MediaWidgetSizeClass.Compact),
    SizeCase("S2 default", 300, 210, MediaWidgetSizeClass.Standard),
    SizeCase("S3 wide", 430, 220, MediaWidgetSizeClass.Expanded),
    SizeCase("S4 tall", 320, 320, MediaWidgetSizeClass.Expanded),
    SizeCase("S5 full page", 520, 620, MediaWidgetSizeClass.Full),
  )

  @Test
  fun allSettingsCombinationsProduceValidLayoutSpecs() {
    var checked = 0

    MediaWidgetTheme.entries.forEach { theme ->
      MediaWidgetArtworkSize.entries.forEach { artworkSize ->
        booleanMasks().forEach { mask ->
          val settings = AppSettings(
            mediaWidgetTheme = theme,
            mediaWidgetArtworkSize = artworkSize,
            mediaWidgetShowSource = mask[0],
            mediaWidgetShowArtist = mask[1],
            mediaWidgetKeepLastSong = mask[2],
            mediaWidgetInstantControls = mask[3],
            mediaWidgetAnimatedFeedback = mask[4],
          )

          sizeCases.forEach { size ->
            val spec = MediaWidgetLayoutSpec.fromDimensions(
              widthDp = size.widthDp,
              heightDp = size.heightDp,
              settings = settings,
            )

            assertEquals("${size.label} class", size.expectedClass, spec.sizeClass)
            assertEquals(size.widthDp, spec.widthDp)
            assertEquals(size.heightDp, spec.heightDp)
            assertTrue("${size.label} artwork positive", spec.artworkDp >= 48f)
            assertTrue("${size.label} artwork fits height", spec.artworkDp <= size.heightDp)
            assertTrue("${size.label} artwork fits width", spec.artworkDp <= size.widthDp)
            assertTrue("${size.label} play is tappable", spec.playButtonDp >= 44)
            assertTrue("${size.label} controls row fits", spec.controlsRowHeightDp <= size.heightDp)
            assertTrue("${size.label} title readable", spec.titleSp >= 13f)
            assertTrue("${size.label} title lines valid", spec.titleMaxLines in 1..3)
            assertTrue("${size.label} progress height valid", spec.progressHeightDp >= 4f)

            if (spec.showSideControls) {
              assertTrue("${size.label} side buttons tappable", spec.sideButtonDp >= 36)
            }
            if (spec.showExtraActions) {
              assertTrue("${size.label} extra row visible", spec.extraRowHeightDp >= spec.extraButtonDp)
              assertTrue("${size.label} extra buttons tappable", spec.extraButtonDp >= 42)
            } else {
              assertEquals("${size.label} collapsed extra row", 1, spec.extraRowHeightDp)
            }
            if (spec.sizeClass == MediaWidgetSizeClass.Full) {
              assertTrue("${size.label} full uses meta or extra actions", spec.showMeta || spec.showExtraActions)
              assertTrue("${size.label} full play is emphasized", spec.playButtonDp >= 68)
              assertTrue("${size.label} full title is polished", spec.titleSp >= 25f)
            }
            if (spec.sizeClass == MediaWidgetSizeClass.Mini) {
              assertFalse("${size.label} mini hides progress", spec.showProgress)
              assertFalse("${size.label} mini hides meta", spec.showMeta)
              assertFalse("${size.label} mini hides extra actions", spec.showExtraActions)
            }
            checked++
          }
        }
      }
    }

    assertEquals(5 * 3 * 32 * 6, checked)
  }

  @Test
  fun artworkSizeSettingActuallyChangesUsableArtworkOutsideMini() {
    val compact = MediaWidgetLayoutSpec.fromDimensions(
      widthDp = 320,
      heightDp = 260,
      settings = AppSettings(mediaWidgetArtworkSize = MediaWidgetArtworkSize.Compact),
    )
    val balanced = MediaWidgetLayoutSpec.fromDimensions(
      widthDp = 320,
      heightDp = 260,
      settings = AppSettings(mediaWidgetArtworkSize = MediaWidgetArtworkSize.Balanced),
    )
    val large = MediaWidgetLayoutSpec.fromDimensions(
      widthDp = 320,
      heightDp = 260,
      settings = AppSettings(mediaWidgetArtworkSize = MediaWidgetArtworkSize.Large),
    )

    assertTrue(compact.artworkDp < balanced.artworkDp)
    assertTrue(balanced.artworkDp < large.artworkDp)
  }

  private fun booleanMasks(): List<BooleanArray> {
    return (0 until 32).map { value ->
      BooleanArray(5) { bit -> value and (1 shl (4 - bit)) != 0 }
    }
  }
}
