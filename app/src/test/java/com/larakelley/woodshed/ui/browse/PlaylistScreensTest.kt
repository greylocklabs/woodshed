package com.larakelley.woodshed.ui.browse

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.larakelley.woodshed.data.Playlist
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlaylistScreensTest {
  @get:Rule val compose = createComposeRule()

  private fun row(id: Long, title: String) = TuneRow(id, "t$id", title, null, credit = "", shape = "", style = null, key = "C", homeKey = null, tonality = null)

  @Test
  fun thePickerAddsRemovesAndCreates() {
    val calls = mutableListOf<String>()
    val actions = PlaylistActions(
      add = { p, t -> calls += "add $p $t" },
      remove = { p, t -> calls += "remove $p $t" },
      create = { name, t, _ -> calls += "create $name $t" },
    )
    val lists = listOf(Playlist(1, "Gig", listOf(5L)), Playlist(2, "Jam", emptyList()))
    compose.setContent { PlaylistPicker(row(5, "Solar"), lists, actions, onDismiss = {}) }
    compose.onNodeWithTag("pick-playlist-Gig").performClick()
    compose.onNodeWithTag("pick-playlist-Jam").performClick()
    compose.onNodeWithTag("new-playlist-name").performTextInput("Ballads")
    compose.onNodeWithTag("create-playlist").performClick()
    assertEquals(listOf("remove 1 5", "add 2 5", "create Ballads 5"), calls)
  }

  @Test
  fun aPlaylistReordersRemovesAndDeletes() {
    val calls = mutableListOf<String>()
    var back = 0
    val actions = PlaylistActions(move = { p, a, b -> calls += "move $p $a $b" }, remove = { p, t -> calls += "remove $p $t" }, delete = { calls += "delete $it" })
    val tunes = mapOf(5L to row(5, "Solar"), 6L to row(6, "Nardis"))
    compose.setContent { PlaylistScreen(Playlist(1, "Gig", listOf(5L, 6L)), tunes, actions, onOpenTune = {}, onBack = { back++ }) }
    compose.onNodeWithText("Nardis").assertExists()
    compose.onNodeWithTag("down-0").performClick()
    compose.onNodeWithTag("remove-1").performClick()
    compose.onNodeWithTag("delete-playlist").performClick()
    compose.onNodeWithTag("confirm-delete-playlist").performClick()
    assertEquals(listOf("move 1 0 1", "remove 1 6", "delete 1"), calls)
    assertEquals(1, back)
  }

  @Test
  fun noPlaylistsOffersToStartOne() {
    compose.setContent { PlaylistsScreen(emptyList(), PlaylistActions(), onOpen = {}, onBack = {}) }
    compose.onNodeWithText("No playlists yet").assertExists()
  }
}
