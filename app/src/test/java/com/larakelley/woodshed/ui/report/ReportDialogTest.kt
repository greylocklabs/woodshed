package com.larakelley.woodshed.ui.report

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.larakelley.woodshed.domain.ReportSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReportDialogTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun aProblemMustBeChosenThenGitHubOpensWithIt() {
    val opened = mutableListOf<String>()
    val uris = object : UriHandler {
      override fun openUri(uri: String) {
        opened += uri
      }
    }
    compose.setContent {
      CompositionLocalProvider(LocalUriHandler provides uris, LocalAppVersion provides "1.0") {
        ReportButton(ReportSubject.TUNE, item = "Solar", id = "solar", details = listOf("Key" to "Concert C minor"))
      }
    }
    compose.onNodeWithTag("report").performClick()
    compose.onNodeWithTag("open-github").assertIsNotEnabled()
    compose.onNodeWithTag("problem-Wrong key").performClick()
    compose.onNodeWithTag("report-note").performTextInput("Miles plays it in C minor; fine")
    compose.onNodeWithTag("open-github").assertIsEnabled().performClick()

    assertEquals(1, opened.size)
    assertTrue(opened.single().contains("title=Wrong%20key%20in%20%22Solar%22"))
    assertTrue(opened.single().contains("Woodshed%201.0"))
    compose.onNodeWithTag("open-github").assertDoesNotExist()
  }
}
