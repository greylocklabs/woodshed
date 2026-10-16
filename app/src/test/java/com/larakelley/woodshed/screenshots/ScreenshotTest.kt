package com.larakelley.woodshed.screenshots

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.larakelley.woodshed.audio.DroneState
import com.larakelley.woodshed.audio.MetronomeState
import com.larakelley.woodshed.audio.TunerState
import com.larakelley.woodshed.data.PastBlock
import com.larakelley.woodshed.data.PastSession
import com.larakelley.woodshed.data.PracticeDay
import com.larakelley.woodshed.data.PracticeStats
import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.NoteReading
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.theme.WoodshedTheme
import com.larakelley.woodshed.ui.help.HelpScreen
import com.larakelley.woodshed.ui.history.HistoryActions
import com.larakelley.woodshed.ui.history.HistoryScreen
import com.larakelley.woodshed.ui.history.HistoryUi
import com.larakelley.woodshed.ui.history.HistoryView
import com.larakelley.woodshed.ui.practice.PlanItem
import com.larakelley.woodshed.ui.practice.PracticeHome
import com.larakelley.woodshed.ui.practice.PracticeUi
import com.larakelley.woodshed.ui.practice.SessionScreen
import com.larakelley.woodshed.ui.practice.SessionSolo
import com.larakelley.woodshed.ui.practice.SessionTune
import com.larakelley.woodshed.ui.practice.SetupActions
import com.larakelley.woodshed.ui.practice.SetupUi
import com.larakelley.woodshed.ui.practice.StepView
import com.larakelley.woodshed.ui.practice.TuneGroup
import com.larakelley.woodshed.ui.practice.droneNote
import com.larakelley.woodshed.ui.tools.DroneActions
import com.larakelley.woodshed.ui.tools.DroneScreen
import com.larakelley.woodshed.ui.tools.MetronomeActions
import com.larakelley.woodshed.ui.tools.MetronomeScreen
import com.larakelley.woodshed.ui.tools.TunerActions
import com.larakelley.woodshed.ui.tools.TunerScreen
import com.larakelley.woodshed.ui.transcribe.SoloActions
import com.larakelley.woodshed.ui.transcribe.SoloRow
import com.larakelley.woodshed.ui.transcribe.TranscribeScreen
import com.larakelley.woodshed.ui.transcribe.TranscribeUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * The main screens in light and dark, compared against the images in src/test/screenshots. Each screen gets fixed
 * data and a stopped clock, so the pictures only change when the design does.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = RobolectricDeviceQualifiers.Pixel7, sdk = [35])
class ScreenshotTest(private val mode: String) {
  @get:Rule val compose = createComposeRule()

  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    fun modes() = listOf(arrayOf("light"), arrayOf("dark"), arrayOf("play"))

    /** The Play listing's screenshots, in the order the store shows them. */
    private val PLAY_ORDER = listOf("session-setup", "plan-review", "session-step", "solos", "drone", "tuner", "metronome")
  }

  private fun capture(name: String, content: @Composable () -> Unit) {
    // Only the listing's screens are shot for Play.
    if (mode == "play" && name !in PLAY_ORDER) return
    // The theme follows the system's night mode, as it does in the app. The Play set is dark on a 9:16, 1080 x 1920
    // phone, since Play refuses screenshots more than twice as tall as they are wide.
    when (mode) {
      "dark" -> RuntimeEnvironment.setQualifiers("+night")
      "play" -> RuntimeEnvironment.setQualifiers("+w411dp-h731dp-night-420dpi")
    }
    compose.mainClock.autoAdvance = false
    // The app draws its background on a Surface behind every tab, so the screens are shot on one too.
    compose.setContent { WoodshedTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    compose.mainClock.advanceTimeBy(1_000)
    val path =
      if (mode == "play") {
        "src/main/play/listings/en-US/graphics/phone-screenshots/${PLAY_ORDER.indexOf(name) + 1}-$name.png"
      } else {
        "src/test/screenshots/$name-$mode.png"
      }
    compose.onRoot().captureRoboImage(path)
  }

  private val solo =
    TranscriptionEntity(
      id = 1, slug = "tangerine-chet-baker", standardId = 3, title = "Tangerine", soloist = "Chet Baker", instrument = "Trumpet",
      context = "She Was Too Good to Me, Van Gelder Studio", recorded = "1974-07-17", url = "https://example.org/clip",
      startSeconds = 48, endSeconds = 138, keyName = "Eb", keyPitchClass = 3, tonality = Tonality.MAJOR,
    )

  @Test
  fun sessionSetup() = capture("session-setup") {
    PracticeHome(
      PracticeUi(
        setup = SetupUi(
          instrument = Instrument.ALTO_SAX,
          instruments = listOf(Instrument.TRUMPET, Instrument.ALTO_SAX, Instrument.TENOR_SAX),
          instrumentsChosen = true,
          focus = setOf(Focus.TONE),
          solos = listOf(SessionSolo(1, "Tangerine", "Chet Baker", active = true)),
          soloId = 1,
          tunes = listOf(SessionTune(3, "Solar", TuneGroup.DUE), SessionTune(4, "Blue Bossa", TuneGroup.LEARNING)),
          selected = setOf(3),
        ),
      ),
      SetupActions(),
    )
  }

  @Test
  fun planReview() = capture("plan-review") {
    PracticeHome(
      PracticeUi(
        setup = SetupUi(
          plan = listOf(
            PlanItem(PlanBlock.Exercise(1, 5), "Long tones", "Tone"),
            PlanItem(PlanBlock.Solo(1, 7), "🎧 Tangerine", "Transcribe Chet Baker"),
            PlanItem(PlanBlock.Tune(3, 2, 18), "Solar", "Guide-tone solo"),
          ),
        ),
      ),
      SetupActions(),
    )
  }

  @Test
  fun sessionStep() = capture("session-step") {
    SessionScreen(
      PracticeUi(
        running = true,
        step = StepView.Exercise(
          "Long tones",
          "Tone",
          "Low to mid range, eight counts a note against a drone.",
          60,
          5,
          droneNote(5, "Tone", Transposition.Eb),
        ),
        stepIndex = 0,
        stepCount = 3,
        // Paused, so the block timer shows the same time on every run.
        stepStartedAt = 1_000_000L,
        pausedAt = 1_061_000L,
      ),
      onGrade = {},
      onNext = {},
      onStop = {},
      transposition = Transposition.Eb,
    )
  }

  @Test
  fun solos() = capture("solos") {
    val bartley =
      solo.copy(
        id = 2,
        slug = "singin-in-the-rain-patrick-bartley",
        title = "Singin' In The Rain",
        soloist = "Patrick Bartley",
        instrument = "Alto sax",
        context = "Emmet's Place",
        recorded = "2022-08-13",
      )
    val miles =
      solo.copy(
        id = 3,
        slug = "blues-by-five-miles-davis",
        title = "Blues By Five",
        soloist = "Miles Davis",
        context = "Cookin' With The Miles Davis Quintet, Van Gelder Studio",
        recorded = "1956-10-26",
      )
    val rows =
      listOf(
        SoloRow(solo, TranscriptionStateEntity(1, TranscriptionStatus.ACTIVE)),
        SoloRow(miles, TranscriptionStateEntity(3, TranscriptionStatus.WISHLIST)),
        SoloRow(bartley, null),
      )
    TranscribeScreen(TranscribeUi(rows, Transposition.Bb), SoloActions())
  }

  @Test
  fun metronome() = capture("metronome") {
    MetronomeScreen(MetronomeState(bpm = 120), MetronomeActions())
  }

  @Test
  fun drone() = capture("drone") {
    DroneScreen(DroneState(pitchClass = 3, fifth = true), Transposition.Bb, 440, DroneActions())
  }

  @Test
  fun tuner() = capture("tuner") {
    TunerScreen(TunerState(listening = true, reading = NoteReading.of(442.0)), Transposition.Eb, TunerActions())
  }

  private val historySession = PastSession(
    id = 1,
    startedAt = 1_792_093_200_000L,
    endedAt = 1_792_095_000_000L,
    plannedMinutes = 30,
    completed = true,
    blocks = listOf(
      PastBlock("Long tones", "Tone", 5, BlockOutcome.DONE, null, 300_000),
      PastBlock("🎧 Tangerine", "Transcribe Chet Baker", 7, BlockOutcome.GRADED, Grade.GOOD, 420_000),
      PastBlock("Solar", "Guide-tone solo", 18, BlockOutcome.GRADED, Grade.HARD, 1_080_000),
    ),
  )

  @Test
  fun historyList() = capture("history-list") {
    HistoryScreen(
      HistoryUi(
        sessions = listOf(historySession, historySession.copy(id = 2, startedAt = historySession.startedAt - 86_400_000L, completed = false)),
        loaded = true,
      ),
      HistoryActions(),
      zone = ZoneOffset.UTC,
    )
  }

  @Test
  fun historyCalendar() = capture("history-calendar") {
    val days = listOf(2, 3, 5, 8, 9, 10, 12, 14, 15).associate { d ->
      val date = LocalDate.of(2026, 10, d)
      date to PracticeDay(date, (d % 4 + 1) * 15 * 60_000L, d % 3, 1)
    }
    HistoryScreen(
      HistoryUi(sessions = listOf(historySession), days = days, month = YearMonth.of(2026, 10), selected = LocalDate.of(2026, 10, 15), loaded = true),
      HistoryActions(sessionsOn = { listOf(historySession) }),
      zone = ZoneOffset.UTC,
      initialView = HistoryView.CALENDAR,
    )
  }

  @Test
  fun historyStats() = capture("history-stats") {
    val stats = PracticeStats(
      practicedMs = 754 * 60_000L, sessions = 23, currentStreak = 4, longestStreak = 9,
      topTunes = listOf("Solar" to 14, "Blue Bossa" to 11, "Autumn Leaves" to 9, "Nardis" to 6, "Tangerine" to 3),
      repsByKey = listOf(12, 3, 7, 9, 2, 11, 1, 8, 4, 5, 10, 2),
      learning = 6, repertoire = 4, reachedRepertoireThisMonth = 2,
    )
    HistoryScreen(HistoryUi(sessions = listOf(historySession), stats = stats, loaded = true), HistoryActions(), initialView = HistoryView.STATS)
  }

  @Test
  fun help() = capture("help") { HelpScreen() }
}
