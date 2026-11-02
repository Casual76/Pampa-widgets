package com.pampa.widgets

import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class PampaWidgetsSmokeTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun mediaWidgetIsVisibleAndSettingsAreReachable() {
    composeRule.onNodeWithText("Media Controls").assertIsDisplayed()
    composeRule.onNodeWithText("Scopri il widget").performScrollTo().performClick()
    composeRule.waitUntil(5_000) { composeRule.onNodeWithTag("widget-detail-screen").isDisplayed() }
    composeRule.onNodeWithTag("widget-detail-screen").assertIsDisplayed()
    composeRule.onNodeWithText("Aggiungi alla home").performScrollTo().assertIsDisplayed()
    composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    composeRule.onNodeWithText("I miei").performClick()
    composeRule.onNodeWithText("Le istanze presenti sulla tua home").assertIsDisplayed()
    composeRule.onNodeWithText("Impostazioni").performClick()
    composeRule.onNodeWithTag("settings-screen").assertIsDisplayed()
  }
}
