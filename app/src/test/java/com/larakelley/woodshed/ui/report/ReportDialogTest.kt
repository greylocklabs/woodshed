package com.larakelley.woodshed.ui.report

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.larakelley.woodshed.data.FiledReport
import com.larakelley.woodshed.data.ReportSender
import com.larakelley.woodshed.domain.Report
import com.larakelley.woodshed.domain.ReportSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

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
    compose.onNodeWithTag("open-github").performScrollTo().assertIsEnabled().performClick()

    assertEquals(1, opened.size)
    assertTrue(opened.single().contains("title=Wrong%20key%20in%20%22Solar%22"))
    assertTrue(opened.single().contains("Woodshed%201.0"))
    compose.onNodeWithTag("open-github").assertDoesNotExist()
  }

  @Test
  fun anAppReportNeedsADescription() {
    compose.setContent {
      CompositionLocalProvider(LocalAppVersion provides "1.0") { ReportButton(ReportSubject.APP) }
    }
    compose.onNodeWithTag("report").performClick()
    compose.onNodeWithTag("problem-Something is broken").performClick()
    compose.onNodeWithTag("send-report").assertIsNotEnabled()
    compose.onNodeWithTag("report-note").performTextInput("   ")
    compose.onNodeWithTag("send-report").assertIsNotEnabled()
    compose.onNodeWithTag("report-note").performTextInput("The tuner freezes")
    compose.onNodeWithTag("send-report").assertIsEnabled()
  }

  @Test
  fun sendingFilesTheReportAndSaysWhere() {
    val sent = mutableListOf<Report>()
    val sender = ReportSender { report, appVersion, _ ->
      sent += report
      assertEquals("1.0", appVersion)
      Result.success(FiledReport(42, "https://github.com/lararosekelley/woodshed/issues/42"))
    }
    compose.setContent {
      CompositionLocalProvider(LocalReportSender provides sender, LocalAppVersion provides "1.0") {
        ReportButton(ReportSubject.TUNE, item = "Solar", id = "solar")
      }
    }
    compose.onNodeWithTag("report").performClick()
    compose.onNodeWithTag("send-report").assertIsNotEnabled()
    compose.onNodeWithTag("problem-Wrong key").performClick()
    compose.onNodeWithTag("send-report").performClick()

    compose.onNodeWithTag("report-filed").assertTextContains("issue #42", substring = true)
    assertEquals(listOf("Wrong key"), sent.map { it.problem })
    compose.onNodeWithTag("report-done").performClick()
    compose.onNodeWithTag("report-filed").assertDoesNotExist()
  }

  @Test
  fun aFailedSendSaysWhyAndCanBeTriedAgain() {
    var attempts = 0
    val sender = ReportSender { _, _, _ ->
      attempts++
      if (attempts == 1) Result.failure(IOException("Couldn't reach the report service.")) else Result.success(FiledReport(7, "https://example.org/7"))
    }
    compose.setContent {
      CompositionLocalProvider(LocalReportSender provides sender) { ReportButton(ReportSubject.APP) }
    }
    compose.onNodeWithTag("report").performClick()
    compose.onNodeWithTag("problem-An idea").performClick()
    compose.onNodeWithTag("report-note").performTextInput("More drones")
    compose.onNodeWithTag("send-report").performClick()
    compose.onNodeWithTag("report-failed").assertTextContains("Couldn't reach", substring = true)
    compose.onNodeWithTag("send-report").assertTextEquals("Try again").performClick()
    compose.onNodeWithTag("report-filed").assertExists()
    assertEquals(2, attempts)
  }

  @Test
  fun theThanksSurvivesRotationInsteadOfReopeningTheForm() {
    val restore = StateRestorationTester(compose)
    var sends = 0
    val sender = ReportSender { _, _, _ ->
      sends++
      Result.success(FiledReport(42, "https://example.org/42"))
    }
    restore.setContent {
      CompositionLocalProvider(LocalReportSender provides sender) { ReportButton(ReportSubject.APP) }
    }
    compose.onNodeWithTag("report").performClick()
    compose.onNodeWithTag("problem-An idea").performClick()
    compose.onNodeWithTag("report-note").performTextInput("More drones")
    compose.onNodeWithTag("send-report").performClick()
    compose.onNodeWithTag("report-filed").assertExists()

    restore.emulateSavedInstanceStateRestore()
    compose.onNodeWithTag("report-filed").assertExists()
    compose.onNodeWithTag("send-report").assertDoesNotExist()
    assertEquals(1, sends)
  }
}
