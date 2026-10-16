package com.larakelley.woodshed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import com.larakelley.woodshed.data.ThemeMode
import com.larakelley.woodshed.data.recordedLabel
import com.larakelley.woodshed.domain.ChangelogRelease
import com.larakelley.woodshed.domain.Phase
import com.larakelley.woodshed.theme.WoodshedTheme
import com.larakelley.woodshed.ui.AppNavigation
import com.larakelley.woodshed.ui.Destination
import com.larakelley.woodshed.ui.SoloLink
import com.larakelley.woodshed.ui.WhatsNewDialog
import com.larakelley.woodshed.ui.browse.BrowseScreen
import com.larakelley.woodshed.ui.browse.BrowseViewModel
import com.larakelley.woodshed.ui.dice.DiceScreen
import com.larakelley.woodshed.ui.dice.DiceViewModel
import com.larakelley.woodshed.ui.help.HelpScreen
import com.larakelley.woodshed.ui.history.HistoryActions
import com.larakelley.woodshed.ui.history.HistoryScreen
import com.larakelley.woodshed.ui.history.HistoryViewModel
import com.larakelley.woodshed.ui.practice.PracticeHome
import com.larakelley.woodshed.ui.practice.PracticeViewModel
import com.larakelley.woodshed.ui.practice.SessionScreen
import com.larakelley.woodshed.ui.practice.SetupActions
import com.larakelley.woodshed.ui.report.LocalAppVersion
import com.larakelley.woodshed.ui.settings.SettingsActions
import com.larakelley.woodshed.ui.settings.SettingsScreen
import com.larakelley.woodshed.ui.settings.SettingsViewModel
import com.larakelley.woodshed.ui.tools.DroneActions
import com.larakelley.woodshed.ui.tools.DroneScreen
import com.larakelley.woodshed.ui.tools.MetronomeActions
import com.larakelley.woodshed.ui.tools.MetronomeScreen
import com.larakelley.woodshed.ui.tools.TunerActions
import com.larakelley.woodshed.ui.tools.TunerScreen
import com.larakelley.woodshed.ui.transcribe.TranscribeScreen
import com.larakelley.woodshed.ui.transcribe.TranscribeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  private val app get() = application as WoodshedApplication

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val metronome = app.metronome

    enableEdgeToEdge()
    val info = packageManager.getPackageInfo(packageName, 0)
    val version = info.versionName ?: ""
    val freshInstall = info.firstInstallTime == info.lastUpdateTime
    setContent {
      val themeMode by app.repository.themeMode.collectAsStateWithLifecycle()
      val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }
      WoodshedTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalAppVersion provides version) {
          val settings: SettingsViewModel = viewModel { SettingsViewModel(app.repository, app.backups, version) }
          val settingsUi by settings.ui.collectAsStateWithLifecycle()
          val browse: BrowseViewModel = viewModel { BrowseViewModel(app.repository, app.practice, app.tunes, app.playlists, app.exercises, app.media) }
          val practice: PracticeViewModel = viewModel { PracticeViewModel(app.repository, app.practice, app.sessions) }
          val dice: DiceViewModel = viewModel { DiceViewModel(app.repository, app.practice) }
          val transcribe: TranscribeViewModel = viewModel { TranscribeViewModel(app.repository, app.transcriptions) }
          val transcribeUi by transcribe.ui.collectAsStateWithLifecycle()
          val navigation = remember { AppNavigation() }
          val tuneSolos = remember(transcribeUi.rows) {
            transcribeUi.rows.filter { it.solo.standardId != null }.groupBy({ it.solo.standardId!! }) { r ->
              SoloLink(
                r.solo.id,
                "${r.solo.soloist}, ${r.solo.instrument.lowercase()}",
                listOfNotNull(r.solo.context, r.solo.recorded?.let(::recordedLabel)).joinToString(" · "),
              )
            }
          }
          val browseState by browse.state.collectAsStateWithLifecycle()
          val practiceUi by practice.ui.collectAsStateWithLifecycle()
          val diceUi by dice.ui.collectAsStateWithLifecycle()
          val click by metronome.state.collectAsStateWithLifecycle()
          val tuning by app.tuner.state.collectAsStateWithLifecycle()
          val droning by app.drone.state.collectAsStateWithLifecycle()

          LaunchedEffect(browseState.styles) {
            val exercises = app.repository.exercises.first()
            val categories = exercises.map { it.category }.distinct().sortedBy { Phase.categoryRank(it) }
            dice.setOptions(browseState.styles, exercises.filter { it.appliesToTune }.map { it.name }, categories)
          }
          val history: HistoryViewModel = viewModel { HistoryViewModel(app.history) }
          val historyUi by history.ui.collectAsStateWithLifecycle()
          val setupActions = SetupActions(
            setInstrument = practice::setInstrument,
            setMinutes = practice::setMinutes,
            toggleFocus = practice::toggleFocus,
            toggleTune = practice::toggleTune,
            usePlaylist = practice::usePlaylist,
            pickSolo = practice::pickSolo,
            buildPlan = practice::buildPlan,
            discardPlan = practice::discardPlan,
            shuffle = practice::shuffle,
            replacements = practice::replacements,
            replace = practice::replace,
            remove = practice::removeBlock,
            start = practice::start,
          )

          var whatsNew by remember { mutableStateOf(emptyList<ChangelogRelease>()) }
          LaunchedEffect(Unit) { whatsNew = withContext(Dispatchers.IO) { app.whatsNew.pending(version, freshInstall) } }
          if (whatsNew.isNotEmpty()) {
            WhatsNewDialog(whatsNew) {
              app.whatsNew.markSeen(version)
              whatsNew = emptyList()
            }
          }

          Surface(color = MaterialTheme.colorScheme.background) {
            BrowseScreen(
              browseState,
              browse::selectTransposition,
              actions = browse.actions,
              playlistActions = browse.playlistActions,
              exerciseActions = browse.exerciseActions,
              mediaActions = browse.mediaActions,
              onTuneFilter = browse::updateTuneFilter,
              onExerciseFilter = browse::updateExerciseFilter,
              practiceTab = {
                // A running session lives in this tab, so switching to the metronome or tuner and back keeps it.
                if (practiceUi.running) {
                  SessionScreen(
                    practiceUi, practice::grade, practice::next, onStop = practice::stop, onUseTempo = {
                      metronome.setBpm(it)
                      metronome.start()
                    },
                    onPause = practice::pause, onResume = practice::resume,
                    dronePlaying = droning.playing,
                    onDrone = { pc ->
                      pc?.let(app.drone::setPitchClass)
                      app.drone.start()
                    },
                    onStopDrone = app.drone::stop,
                    onPrevious = practice::previous,
                    onFinish = {
                      practice.stop()
                      maybeAskForReview()
                    },
                    metronomeRunning = click.running,
                    bpm = click.bpm,
                    onToggleMetronome = metronome::toggle,
                    onSetBpm = metronome::setBpm,
                  )
                } else {
                  LaunchedEffect(Unit) { practice.refresh() }
                  PracticeHome(practiceUi, setupActions)
                }
              },
              transcribeTab = { TranscribeScreen(transcribeUi, transcribe.actions, navigation) },
              metronomeTab = {
                MetronomeScreen(
                  click,
                  MetronomeActions(metronome::toggle, metronome::setBpm, metronome::setBeatsPerBar, metronome::setAccentFirst, metronome::setFeel),
                )
              },
              tuneSolos = tuneSolos,
              navigation = navigation,
              droneTab = {
                val drone = app.drone
                DroneScreen(
                  droning,
                  browseState.transposition,
                  tuning.a4,
                  DroneActions(drone::toggle, drone::setPitchClass, drone::setOctave, drone::setFifth, drone::setVolume),
                )
              },
              historyTab = {
                LaunchedEffect(Unit) { history.refresh() }
                HistoryScreen(historyUi, HistoryActions(history::showMonth, history::select, history::sessionsOn, history::delete))
              },
              helpTab = { HelpScreen() },
              tunerTab = {
                // The microphone is only on while this tab is showing.
                DisposableEffect(Unit) { onDispose { app.tuner.stop() } }
                TunerScreen(tuning, browseState.transposition, TunerActions(app.tuner::start, app.tuner::stop, app.tuner::setA4))
              },
              settings = { onBack, modifier ->
                SettingsScreen(
                  settingsUi,
                  SettingsActions(
                    settings::setInstrument,
                    settings::toggleMyInstrument,
                    settings::setTransposition,
                    settings::setThemeMode,
                    settings::export,
                    settings::restore,
                  ),
                  onBack = {
                    settings.clearMessage()
                    onBack()
                  },
                  modifier = modifier,
                )
              },
              diceTab = {
                DiceScreen(diceUi, dice::constrain, dice::roll, dice::grade, dice::toggleKeyKnown, onOpenSolo = { navigation.go(Destination.Solo(it)) })
              },
            )
          }
        }
      }
    }
  }

  /** Asks for a Play review after a finished session, when nothing is sounding and the prompt is due. */
  private fun maybeAskForReview() {
    if (app.metronome.state.value.running || app.drone.state.value.playing || app.tuner.state.value.listening) return
    lifecycleScope.launch {
      if (!app.reviewPrompt.shouldAsk(app.practiceTimes())) return@launch
      app.reviewPrompt.asked()
      // Play may decline to show it, and a failure here only means no prompt this time.
      runCatching {
        val reviews = ReviewManagerFactory.create(this@MainActivity)
        reviews.launchReview(this@MainActivity, reviews.requestReview())
      }
    }
  }

  override fun onStop() {
    super.onStop()
    // The tuner only listens while the app is on screen. The metronome and drone may keep playing for a running
    // session, whose service keeps the app alive; otherwise they stop too.
    if (!app.sessions.state.value.running) {
      app.metronome.stop()
      app.drone.stop()
    }
    app.tuner.stop()
  }
}
