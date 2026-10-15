package com.larakelley.woodshed.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import com.larakelley.woodshed.domain.parseChangelog
import com.larakelley.woodshed.theme.WoodshedTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WhatsNewDialogTest {
  @get:Rule val compose = createComposeRule()

  private val releases = parseChangelog(
    """
    ## 0.3.0

    ### Added

    - **tunes:** something new, see [the site](https://woodshedapp.org).
    """.trimIndent(),
  )

  @Test
  fun showsTheReleaseUntilDismissed() {
    var dismissed = false
    compose.setContent { WoodshedTheme { WhatsNewDialog(releases) { dismissed = true } } }
    compose.onNodeWithText("What's new in 0.3.0").assertExists()
    compose.onNodeWithText("Added").assertExists()
    compose.onNodeWithText("tunes: something new, see the site.").assertExists()
    compose.onNodeWithTag("whats-new-done").performClick()
    assertEquals(true, dismissed)
  }

  @Test
  fun rendersBoldAndLinks() {
    val text = inlineMarkdown("**tunes:** see [the site](https://woodshedapp.org).", Color.Red)
    assertEquals("tunes: see the site.", text.text)
    assertEquals(FontWeight.Bold, text.spanStyles.single { text.text.substring(it.start, it.end) == "tunes:" }.item.fontWeight)
    val link = text.getLinkAnnotations(0, text.length).single()
    assertEquals("the site", text.text.substring(link.start, link.end))
    assertEquals("https://woodshedapp.org", (link.item as LinkAnnotation.Url).url)
  }
}
