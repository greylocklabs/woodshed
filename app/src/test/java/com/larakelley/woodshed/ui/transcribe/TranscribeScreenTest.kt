package com.larakelley.woodshed.ui.transcribe

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.Transposition
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TranscribeScreenTest {
  @get:Rule val compose = createComposeRule()

  // An "other" link keeps the test off the network: it opens outside rather than embedding a player.
  private val solo = TranscriptionEntity(
    id = 1, slug = "s", title = "Singin' In The Rain", soloist = "Patrick Bartley", instrument = "Alto sax", context = "Emmet's Place", recorded = "2022-08-13",
    url = "https://example.org/clip", startSeconds = 120, endSeconds = 329, keyName = "Db", keyPitchClass = 1, tonality = Tonality.MAJOR,
  )

  @Test
  fun groupsByProgressAndNamesTheKeyForTheHorn() {
    val rows = listOf(SoloRow(solo, TranscriptionStateEntity(1, TranscriptionStatus.ACTIVE)), SoloRow(solo.copy(id = 2, slug = "t", title = "Donna Lee"), null))
    assertEquals(listOf("🎧 Transcribing", "Solos to try"), soloGroups(rows).map { it.first })
    assertEquals("Concert Db", soloKey(solo, Transposition.C))
    assertEquals("Bb (Concert Db)", soloKey(solo, Transposition.Eb))
  }

  @Test
  fun aSoloOpensWithItsTimesAndCanBeStarted() {
    val started = mutableListOf<Long>()
    compose.setContent { TranscribeScreen(TranscribeUi(listOf(SoloRow(solo, null)), Transposition.Eb), SoloActions(start = { started += it })) }
    compose.onNodeWithTag("solo-s").performClick()
    compose.onNodeWithText("Solo 2:00 to 5:29").assertExists()
    compose.onNodeWithText("Emmet's Place · Aug 13, 2022").assertExists()
    compose.onNodeWithText("Bb (Concert Db)").assertExists()
    compose.onNodeWithTag("start-solo").performScrollTo().performClick()
    assertEquals(listOf(1L), started)
  }
}
