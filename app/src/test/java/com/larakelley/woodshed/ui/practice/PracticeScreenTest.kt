package com.larakelley.woodshed.ui.practice

import android.Manifest
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.ui.browse.ExerciseRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PracticeScreenTest {
  @get:Rule val compose = createComposeRule()

  private val card = CardView(
    title = "Autumn Leaves",
    key = "Written A minor",
    exercise = "Changes without the melody",
    instructions = "Arpeggiate the changes in time with no melody.",
    shape = "AABC, 32 bars, Swing",
    buttons = listOf(Grade.AGAIN to "10m", Grade.HARD to "1d", Grade.GOOD to "1d", Grade.EASY to "4d"),
  )

  private val tuneStep = StepView.Tune(card, minutes = 10)

  /** The step timer ticks forever, so a test clock that advances itself until idle would never stop. */
  private fun session(ui: PracticeUi, onGrade: (Grade) -> Unit = {}, onNext: () -> Unit = {}, onUseTempo: (Int) -> Unit = {}, onPrevious: () -> Unit = {}) {
    compose.mainClock.autoAdvance = false
    compose.setContent { SessionScreen(ui, onGrade = onGrade, onNext = onNext, onStop = {}, onUseTempo = onUseTempo, onPrevious = onPrevious) }
    compose.mainClock.advanceTimeByFrame()
  }

  @Test
  fun tuneStepShowsTheCardAndReportsTheGrade() {
    val graded = mutableListOf<Grade>()
    session(PracticeUi(running = true, step = tuneStep, stepCount = 3), onGrade = { graded += it })

    compose.onNodeWithTag("card-title").assertExists()
    compose.onNodeWithText("Written A minor").assertExists()
    compose.onNodeWithText("1 of 3").assertExists()
    compose.onNodeWithTag("grade-good").performClick()
    assertEquals(listOf(Grade.GOOD), graded)
  }

  @Test
  fun backGoesToThePreviousStepPastTheFirst() {
    var previous = 0
    session(PracticeUi(running = true, step = tuneStep, stepIndex = 1, stepCount = 3), onPrevious = { previous++ })
    compose.onNodeWithTag("step-previous").performClick()
    assertEquals(1, previous)
  }

  @Test
  fun theMetronomeAndDroneAreControlledFromTheSession() {
    var toggled = 0
    val bpms = mutableListOf<Int>()
    val drones = mutableListOf<Int?>()
    var droneStopped = false
    var ui by mutableStateOf(PracticeUi(running = true, step = tuneStep.copy(card = card.copy(concertPitchClass = 7)), stepCount = 3))
    var running by mutableStateOf(false)
    var droning by mutableStateOf(false)
    compose.mainClock.autoAdvance = false
    compose.setContent {
      SessionScreen(
        ui,
        onGrade = {},
        onNext = {},
        onStop = {},
        dronePlaying = droning,
        onDrone = {
          drones += it
          droning = true
        },
        onStopDrone = { droneStopped = true },
        metronomeRunning = running,
        bpm = 120,
        onToggleMetronome = {
          toggled++
          running = !running
        },
        onSetBpm = { bpms += it },
      )
    }
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithText("⏱️ 120 bpm").performClick()
    compose.mainClock.advanceTimeByFrame()
    compose.mainClock.advanceTimeBy(500)
    compose.onNodeWithText("◼ 120 bpm").assertExists()
    compose.onNodeWithTag("session-faster").performClick()
    compose.onNodeWithTag("session-slower").performClick()
    assertEquals(1, toggled)
    assertEquals(listOf(125, 115), bpms)

    // On a tune, the drone sounds the card's key; a second tap stops it.
    compose.onNodeWithTag("use-drone").performClick()
    compose.mainClock.advanceTimeByFrame()
    compose.onNodeWithTag("use-drone").performClick()
    assertEquals(listOf<Int?>(7), drones)
    assertTrue(droneStopped)
  }

  @Test
  fun theFirstStepHasNoBack() {
    session(PracticeUi(running = true, step = tuneStep, stepCount = 3))
    compose.onNodeWithTag("step-previous").assertDoesNotExist()
  }

  @Test
  fun exerciseStepIsFinishedWithDone() {
    var next = 0
    val tempos = mutableListOf<Int>()
    val step = StepView.Exercise("Long tones", "Tone", "Hold each note.", tempoBpm = 60, minutes = 5)
    session(PracticeUi(running = true, step = step, stepIndex = 1, stepCount = 3), onNext = { next++ }, onUseTempo = { tempos += it })

    compose.onNodeWithText("Tone").assertExists()
    compose.onNodeWithTag("use-tempo").performClick()
    assertEquals(listOf(60), tempos)
    compose.onNodeWithText("2 of 3").assertExists()
    compose.onNodeWithTag("step-timer").assertExists()
    compose.onNodeWithTag("grade-good").assertDoesNotExist()
    compose.onNodeWithTag("step-done").performClick()
    assertEquals(1, next)
  }

  @Test
  fun finishedSessionSummarises() {
    session(PracticeUi(running = true, step = null, stepCount = 4, reviewedThisSession = 2))
    compose.onNodeWithText("4 blocks, 2 tunes reviewed.").assertExists()
  }

  @Test
  fun setupListsTunesByGroupAndReportsChoices() {
    val tunes = listOf(SessionTune(1, "Solar", TuneGroup.DUE), SessionTune(2, "Afro Blue", TuneGroup.LEARNING), SessionTune(3, "Nardis", TuneGroup.WISHLIST))
    val toggled = mutableListOf<Any>()
    val actions = SetupActions(toggleTune = { toggled += it }, toggleFocus = { toggled += it }, setMinutes = { toggled += it })
    compose.setContent { PracticeHome(PracticeUi(setup = SetupUi(minutes = 30, tunes = tunes, selected = setOf(1))), actions) }

    compose.onNodeWithTag("focus-speed").performScrollTo().performClick()
    compose.onNodeWithTag("length-45").performScrollTo().performClick()
    compose.onNodeWithText("Tunes (1 picked)").assertExists()
    // The tune list sits below the fold on a test-sized screen.
    val list = compose.onNode(hasScrollAction())
    list.performScrollToNode(hasText("Due for review", substring = true))
    list.performScrollToNode(hasText("Wishlist", substring = true))
    list.performScrollToNode(hasTestTag("pick-3"))
    compose.onNodeWithTag("pick-3").performClick()
    assertEquals(listOf<Any>(Focus.SPEED, 45, 3L), toggled)
  }

  @Test
  fun setupOffersSolosWithTheActiveOneChosen() {
    val picked = mutableListOf<Long?>()
    val solos = listOf(SessionSolo(7, "Tangerine", "Chet Baker", active = true), SessionSolo(8, "Blues By Five", "Miles Davis", active = false))
    compose.setContent { PracticeHome(PracticeUi(setup = SetupUi(solos = solos, soloId = 7)), SetupActions(pickSolo = { picked += it })) }

    compose.onNodeWithText("🎧 Tangerine (Chet Baker)").performScrollTo().assertIsSelected()
    compose.onNodeWithTag("solo-8").performScrollTo().performClick()
    compose.onNodeWithTag("solo-none").performScrollTo().performClick()
    assertEquals(listOf(8L, null), picked)
  }

  @Test
  fun sessionSolosLeaveOutDoneOnesAndPutActiveFirst() {
    fun solo(id: Long, title: String) =
      TranscriptionEntity(id = id, slug = "s$id", title = title, soloist = "X", instrument = "Trumpet", url = "https://example.org")
    val solos = listOf(solo(1, "Airegin"), solo(2, "Blues By Five"), solo(3, "Cherokee"), solo(4, "Donna Lee"))
    val states = listOf(
      TranscriptionStateEntity(1, TranscriptionStatus.WISHLIST),
      TranscriptionStateEntity(2, TranscriptionStatus.DONE),
      TranscriptionStateEntity(3, TranscriptionStatus.ACTIVE),
    )
    assertEquals(listOf(3L, 1L), sessionSolos(solos, states).map { it.id })
  }

  @Test
  fun planningWaitsForAnInstrument() {
    val picked = mutableListOf<Instrument>()
    var ui by mutableStateOf(PracticeUi(setup = SetupUi(instrument = null)))
    compose.setContent {
      PracticeHome(
        ui,
        SetupActions(setInstrument = {
          picked += it
          ui = ui.copy(setup = ui.setup.copy(instrument = it))
        }),
      )
    }

    compose.onNodeWithTag("needs-instrument").assertExists()
    compose.onNodeWithTag("build-plan").assertDoesNotExist()
    // With every instrument on offer, they come in a dropdown.
    compose.onNodeWithTag("chip-Instrument").performClick()
    compose.onNodeWithText("Flute").performClick()
    assertEquals(listOf(Instrument.FLUTE), picked)
    compose.onNodeWithTag("build-plan").assertExists()
    compose.onNodeWithTag("chip-Instrument").assertTextContains("Flute", substring = true)
  }

  @Test
  fun aFewInstrumentsShowAsChips() {
    val picked = mutableListOf<Instrument>()
    val few = listOf(Instrument.ALTO_SAX, Instrument.TENOR_SAX, Instrument.FLUTE)
    compose.setContent { PracticeHome(PracticeUi(setup = SetupUi(instruments = few)), SetupActions(setInstrument = { picked += it })) }
    compose.onNodeWithTag("chip-Instrument").assertDoesNotExist()
    compose.onNodeWithTag("instrument-tenor_sax").performClick()
    assertEquals(listOf(Instrument.TENOR_SAX), picked)
  }

  @Test
  fun planShowsBlocksAndTotalAndCanBeEdited() {
    val longTones = ExerciseRow("Long tones", "Tone", "", "Hold each note.", instruments = "All instruments", minutes = 10, difficulty = 2)
    val plan = listOf(
      PlanItem(PlanBlock.Exercise(1, 5), "Long tones", "Tone", longTones),
      PlanItem(PlanBlock.Tune(7, 2, 25), "Solar", "Guide-tone solo"),
    )
    val shuffled = mutableListOf<Int>()
    val replaced = mutableListOf<Pair<Int, Long>>()
    val melody = ExerciseRow("Melody from memory", "Standards", "Standards, 10 min, difficulty 3", "", id = 11, appliesToTune = true)
    var started = false
    // With notifications allowed, Start goes straight to the session rather than asking first.
    shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    compose.setContent {
      PracticeHome(
        PracticeUi(setup = SetupUi(plan = plan)),
        SetupActions(
          shuffle = { shuffled += it },
          replacements = { if (it == 1) listOf(melody) else emptyList() },
          replace = { i, id -> replaced += i to id },
          start = { started = true },
        ),
      )
    }

    compose.onNodeWithText("30 min planned").assertExists()
    compose.onNodeWithText("Tune: Guide-tone solo").assertExists()
    compose.onNodeWithTag("plan-0").performClick()
    compose.onNodeWithText("All instruments").assertExists()
    compose.onNodeWithText("Close").performClick()
    compose.onNodeWithTag("shuffle-1").performClick()
    compose.onNodeWithTag("replace-1").performClick()
    compose.onNodeWithTag("replacement-11").performClick()
    compose.onNodeWithTag("replacements").assertDoesNotExist()
    compose.onNodeWithTag("start").performClick()
    assertEquals(listOf(1), shuffled)
    assertEquals(listOf(1 to 11L), replaced)
    assertTrue(started)
  }
}
