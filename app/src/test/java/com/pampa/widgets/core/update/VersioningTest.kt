package com.pampa.widgets.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersioningTest {
  @Test
  fun stableVersionComparisonHandlesSemverSegments() {
    assertTrue(isStableVersionNewer("1.10.0", "1.9.9"))
    assertTrue(isStableVersionNewer("2.0", "1.9.9"))
    assertFalse(isStableVersionNewer("1.0.0", "1.0.0"))
    assertFalse(isStableVersionNewer("1.0.0-beta01", "0.9.0"))
  }
}
