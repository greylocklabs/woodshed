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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.larakelley.woodshed.data.ThemeMode
import com.larakelley.woodshed.theme.WoodshedTheme
import com.larakelley.woodshed.ui.browse.BrowseScreen
import com.larakelley.woodshed.ui.browse.BrowseViewModel
import com.larakelley.woodshed.ui.dice.DiceScreen
import com.larakelley.woodshed.ui.dice.DiceViewModel
import com.larakelley.woodshed.ui.practice.PracticeHome
import com.larakelley.woodshed.ui.practice.PracticeViewModel
import com.larakelley.woodshed.ui.practice.SessionScreen
import com.larakelley.woodshed.ui.practice.SetupActions
import com.larakelley.woodshed.ui.report.LocalAppVersion
import com.larakelley.woodshed.ui.settings.SettingsActions
import com.larakelley.woodshed.ui.settings.SettingsScreen
import com.larakelley.woodshed.ui.settings.SettingsViewModel
import com.larakelley.woodshed.ui.tools.MetronomeActions
import com.larakelley.woodshed.ui.tools.MetronomeScreen
import com.larakelley.woodshed.ui.tools.TunerActions
import com.larakelley.woodshed.ui.tools.TunerScreen
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
  private val app get() = application as WoodshedApplication

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val metronome = app.metronome

    enableEdgeToEdge()
    val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
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
          val browse: BrowseViewModel = viewModel { BrowseViewModel(app.repository, app.practice, app.tunes, app.playlists) }
          val practice: PracticeViewModel = viewModel { PracticeViewModel(app.repository, app.practice) }
          val dice: DiceViewModel = viewModel { DiceViewModel(app.repository, app.practice) }
          val browseState by browse.state.collectAsStateWithLifecycle()
          val practiceUi by practice.ui.collectAsStateWithLifecycle()
          val diceUi by dice.ui.collectAsStateWithLifecycle()
          val click by metronome.state.collectAsStateWithLifecycle()
          val tuning by app.tuner.state.collectAsStateWithLifecycle()

          LaunchedEffect(browseState.styles) {
            val tuneExercises = app.repository.exercises.first().filter { it.appliesToTune }.map { it.name }
            dice.setOptions(browseState.styles, tuneExercises)
          }
          val setupActions = SetupActions(
            setInstrument = practice::setInstrument,
            setMinutes = practice::setMinutes,
            toggleFocus = practice::toggleFocus,
            toggleTune = practice::toggleTune,
          usePlaylist = practice::usePlaylist,
            buildPlan = practice::buildPlan,
            discardPlan = practice::discardPlan,
            swap = practice::swap,
            remove = practice::removeBlock,
            start = practice::start,
          )

          Surface(color = MaterialTheme.colorScheme.background) {
            BrowseScreen(
              browseState,
              browse::selectTransposition,
              actions = browse.actions,
            playlistActions = browse.playlistActions,
              onTuneFilter = browse::updateTuneFilter,
              onExerciseFilter = browse::updateExerciseFilter,
              practiceTab = {
                // A running session lives in this tab, so switching to the metronome or tuner and back keeps it.
                if (practiceUi.running) {
                  SessionScreen(practiceUi, practice::grade, practice::next, onStop = practice::stop, onUseTempo = { metronome.setBpm(it); metronome.start() })
                } else {
                  LaunchedEffect(Unit) { practice.refresh() }
                  PracticeHome(practiceUi, setupActions)
                }
              },
              metronomeTab = {
                MetronomeScreen(click, MetronomeActions(metronome::toggle, metronome::setBpm, metronome::setBeatsPerBar, metronome::setAccentFirst, metronome::setFeel))
              },
              tunerTab = {
                // The microphone is only on while this tab is showing.
                DisposableEffect(Unit) { onDispose { app.tuner.stop() } }
                TunerScreen(tuning, browseState.transposition, TunerActions(app.tuner::start, app.tuner::stop, app.tuner::setA4))
              },
              settings = { onBack, modifier ->
                SettingsScreen(
                  settingsUi,
                  SettingsActions(settings::setInstrument, settings::toggleMyInstrument, settings::setTransposition, settings::setThemeMode, settings::export, settings::restore),
                  onBack = { settings.clearMessage(); onBack() },
                  modifier = modifier,
                )
              },
              diceTab = {
                DiceScreen(diceUi, dice::constrain, dice::roll, dice::grade, dice::toggleKeyKnown)
              },
            )
          }
        }
      }
    }
  }

  override fun onStop() {
    super.onStop()
    // The metronome and tuner only run while the app is on screen.
    app.metronome.stop()
    app.tuner.stop()
  }
}
