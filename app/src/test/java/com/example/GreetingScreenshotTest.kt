package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.RagSettings
import com.example.ui.screens.ChatScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun chat_screen_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
          ChatScreen(
            messages = emptyList(),
            isGenerating = false,
            settings = RagSettings(),
            hasApiKey = false,
            onSendMessage = {},
            onClearChat = {},
            onOpenAddDocument = {},
            onOpenSettings = {},
            parseCitations = { emptyList() }
          )
        }
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/chat_screen.png")
  }
}
