package com.pampa.widgets

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
    composeRule.onNodeWithText("Media Controls").performClick()
    composeRule.onNodeWithTag("widget-detail-screen").assertIsDisplayed()
    composeRule.onNodeWithText("Aggiungi alla home").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Store").performClick()
    composeRule.onNodeWithText("Impostazioni").performClick()
    composeRule.onNodeWithTag("settings-screen").assertIsDisplayed()
  }
}
