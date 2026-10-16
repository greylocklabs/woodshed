package com.larakelley.woodshed.ui.help

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.LinkAnnotation
import com.larakelley.woodshed.ui.inlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HelpScreenTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun aTopicOpensToItsAnswerWithTheMarkdownRendered() {
    compose.setContent { HelpScreen() }
    compose.onNodeWithText("Getting started").assertExists()
    compose.onNodeWithText("Then plan a session from the 🎷 Practice tab.").assertDoesNotExist()
    compose.onNodeWithTag("help-Getting started").performClick()
    compose.onNodeWithText("Then plan a session from the 🎷 Practice tab.").assertExists()
    compose.onNodeWithTag("help-Getting started").performClick()
    compose.onNodeWithText("Then plan a session from the 🎷 Practice tab.").assertDoesNotExist()
  }

  @Test
  fun everyTopicHasAnAnswerAndNoStrayMarkdown() {
    HELP_TOPICS.forEach { t ->
      assertTrue(t.title, t.paragraphs.isNotEmpty())
      // Bold must pair up, or the dialog shows the asterisks.
      t.paragraphs.forEach { p -> assertTrue("$t: $p", p.split("**").size % 2 == 1) }
    }
  }

  @Test
  fun theContactAddressIsAnEmailLink() {
    val text = HELP_TOPICS.single { it.title == "Problems and ideas" }.paragraphs.joinToString(" ").let { inlineMarkdown(it, Color.Red) }
    val link = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Url
    assertEquals("mailto:hello@woodshedapp.org", link.url)
    assertTrue(text.text.contains("hello@woodshedapp.org"))
  }
}
