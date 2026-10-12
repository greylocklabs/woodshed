package com.larakelley.woodshed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.larakelley.woodshed.theme.WoodshedTheme
import com.larakelley.woodshed.ui.browse.BrowseScreen
import com.larakelley.woodshed.ui.browse.BrowseViewModel
import com.larakelley.woodshed.ui.dice.DiceScreen
import com.larakelley.woodshed.ui.dice.DiceViewModel
import com.larakelley.woodshed.ui.practice.PracticeHome
import com.larakelley.woodshed.ui.practice.PracticeViewModel
import com.larakelley.woodshed.ui.practice.ReviewScreen
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
  private val app get() = application as WoodshedApplication

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val metronome = app.metronome

    enableEdgeToEdge()
    setContent {
      WoodshedTheme {
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
        val beats = if (practiceUi.reviewing) practiceUi.card?.beatsPerBar else diceUi.card?.beatsPerBar
        LaunchedEffect(beats) { beats?.let(metronome::setBeatsPerBar) }

        Surface(color = MaterialTheme.colorScheme.background) {
          if (practiceUi.reviewing) {
            ReviewScreen(
              practiceUi,
              practice::grade,
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
                PracticeHome(practiceUi, practice::start, practice::setDailyNewLimit)
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
