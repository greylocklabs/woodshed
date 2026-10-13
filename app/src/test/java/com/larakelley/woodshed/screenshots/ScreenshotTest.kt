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
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.NoteReading
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.theme.WoodshedTheme
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
    fun modes() = listOf(arrayOf("light"), arrayOf("dark"))
  }

  private fun capture(name: String, content: @Composable () -> Unit) {
    // The theme follows the system's night mode, as it does in the app.
    if (mode == "dark") RuntimeEnvironment.setQualifiers("+night")
    compose.mainClock.autoAdvance = false
    // The app draws its background on a Surface behind every tab, so the screens are shot on one too.
    compose.setContent { WoodshedTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    compose.mainClock.advanceTimeBy(1_000)
    compose.onRoot().captureRoboImage("src/test/screenshots/$name-$mode.png")
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
        step = StepView.Exercise("Long tones", "Tone", "Low to mid range, eight counts a note against a drone.", 60, 5),
        stepIndex = 0,
        stepCount = 3,
        // Paused, so the block timer shows the same time on every run.
        stepStartedAt = 1_000_000L,
        pausedAt = 1_061_000L,
      ),
      onGrade = {},
      onNext = {},
      onStop = {},
    )
  }

  @Test
  fun solos() = capture("solos") {
    TranscribeScreen(TranscribeUi(listOf(SoloRow(solo, TranscriptionStateEntity(1, TranscriptionStatus.ACTIVE))), Transposition.Bb), SoloActions())
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
}
