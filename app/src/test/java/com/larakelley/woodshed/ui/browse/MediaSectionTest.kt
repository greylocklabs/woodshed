package com.larakelley.woodshed.ui.browse

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.larakelley.woodshed.data.db.TuneMediaEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaSectionTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun videosOfferPlayAndOtherLinksOpenOutside() {
    val opened = mutableListOf<String>()
    val uris = object : UriHandler {
      override fun openUri(uri: String) {
        opened += uri
      }
    }
    val media = listOf(
      TuneMediaEntity(1, 7, "https://youtu.be/jNQXAC9IVRw", "Miles"),
      TuneMediaEntity(2, 7, "https://open.spotify.com/track/abc", null),
    )
    compose.setContent { CompositionLocalProvider(LocalUriHandler provides uris) { Column { MediaSection(7, media, MediaActions()) } } }

    compose.onNodeWithTag("play-1").assertExists()
    compose.onNodeWithTag("open-1").assertDoesNotExist()
    compose.onNodeWithText("Spotify").assertExists()
    compose.onNodeWithTag("open-2").performClick()
    assertEquals(listOf("https://open.spotify.com/track/abc"), opened)
  }

  @Test
  fun addingReportsABadLinkAndSavesAGoodOne() {
    val added = mutableListOf<String>()
    val actions = MediaActions(add = { _, url, _, done ->
      val ok = url.startsWith("https://")
      if (ok) added += url
      done(ok)
    })
    compose.setContent { Column { MediaSection(7, emptyList(), actions) } }
    compose.onNodeWithTag("add-media").performClick()
    compose.onNodeWithTag("media-url").performTextInput("nope")
    compose.onNodeWithTag("save-media").performClick()
    compose.onNodeWithText("That doesn't look like a web address.").assertExists()
    compose.onNodeWithTag("media-url").performTextReplacement("https://vimeo.com/76979871")
    compose.onNodeWithTag("save-media").performClick()
    assertEquals(1, added.size)
    compose.onNodeWithTag("media-url").assertDoesNotExist()
  }
}
