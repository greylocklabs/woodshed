package com.larakelley.woodshed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.larakelley.woodshed.ui.practice.StepView
import com.larakelley.woodshed.ui.settings.SettingsActions
import com.larakelley.woodshed.ui.settings.SettingsScreen
import com.larakelley.woodshed.ui.settings.SettingsViewModel
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
        val settings: SettingsViewModel = viewModel { SettingsViewModel(app.repository, app.backups, version) }
        val settingsUi by settings.ui.collectAsStateWithLifecycle()
        val browse: BrowseViewModel = viewModel { BrowseViewModel(app.repository, app.practice) }
        val practice: PracticeViewModel = viewModel { PracticeViewModel(app.repository, app.practice) }
        val dice: DiceViewModel = viewModel { DiceViewModel(app.repository, app.practice) }
        val browseState by browse.state.collectAsStateWithLifecycle()
        val practiceUi by practice.ui.collectAsStateWithLifecycle()
        val diceUi by dice.ui.collectAsStateWithLifecycle()
        val click by metronome.state.collectAsStateWithLifecycle()

        LaunchedEffect(browseState.styles) {
          val tuneExercises = app.repository.exercises.first().filter { it.appliesToTune }.map { it.name }
          dice.setOptions(browseState.styles, tuneExercises)
        }
        val step = practiceUi.step
        val beats = if (practiceUi.running) (step as? StepView.Tune)?.card?.beatsPerBar ?: 4 else diceUi.card?.beatsPerBar
        LaunchedEffect(beats) { beats?.let(metronome::setBeatsPerBar) }
        // An exercise with a set tempo starts the metronome there; the player can still change it.
        val tempo = (step as? StepView.Exercise)?.tempoBpm
        LaunchedEffect(practiceUi.stepIndex, tempo) { tempo?.let(metronome::setBpm) }
        val setupActions = SetupActions(
          setMinutes = practice::setMinutes,
          toggleFocus = practice::toggleFocus,
          toggleTune = practice::toggleTune,
          buildPlan = practice::buildPlan,
          discardPlan = practice::discardPlan,
          swap = practice::swap,
          remove = practice::removeBlock,
          start = practice::start,
        )

        Surface(color = MaterialTheme.colorScheme.background) {
          if (practiceUi.running) {
            SessionScreen(
              practiceUi,
              practice::grade,
              practice::next,
              onStop = { metronome.stop(); practice.stop() },
              metronome = click,
              onToggleMetronome = metronome::toggle,
              onSetBpm = metronome::setBpm,
            )
          } else {
            BrowseScreen(
              browseState,
              browse::selectTransposition,
              browse::selectFamily,
              actions = browse.actions,
              onTuneFilter = browse::updateTuneFilter,
              onExerciseFilter = browse::updateExerciseFilter,
              practiceTab = {
                LaunchedEffect(Unit) { practice.refresh() }
                PracticeHome(practiceUi, setupActions)
              },
              settings = { onBack, modifier ->
                SettingsScreen(
                  settingsUi,
                  SettingsActions(settings::setTransposition, settings::setFamily, settings::setThemeMode, settings::export, settings::restore),
                  onBack = { settings.clearMessage(); onBack() },
                  modifier = modifier,
                )
              },
              diceTab = {
                DiceScreen(diceUi, dice::constrain, dice::roll, dice::grade, dice::toggleKeyKnown, click, metronome::toggle, metronome::setBpm)
              },
            )
          }
        }
      }
    }
  }

  override fun onStop() {
    super.onStop()
    // The metronome only plays while the app is on screen.
    app.metronome.stop()
  }
}
