package com.pampa.widgets.core.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PampaManifestParserTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun parsesStableReleaseAndBuildsGithubDownloadUrl() {
    val update = json.parsePampaStableUpdate(
      """
      {
        "app": {
          "repository": {
            "repoOwner": "Casual76",
            "repoName": "Pampa-widgets"
          },
          "stable": {
            "version": "1.2.3",
            "changelog": "Nuovi widget.",
            "releaseTag": "stable-pampa-widgets-v1.2.3",
            "apkAsset": "pampa-widgets-1.2.3.apk",
            "sizeBytes": 123456
          }
        }
      }
      """.trimIndent(),
    )

    assertEquals("1.2.3", update.version)
    assertEquals("Nuovi widget.", update.changelog)
    assertEquals("pampa-widgets-1.2.3.apk", update.apkAsset)
    assertEquals(123456L, update.sizeBytes)
    assertEquals(
      "https://github.com/Casual76/Pampa-widgets/releases/download/stable-pampa-widgets-v1.2.3/pampa-widgets-1.2.3.apk",
      update.downloadUrl,
    )
  }
}
