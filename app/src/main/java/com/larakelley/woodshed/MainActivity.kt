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
import com.larakelley.woodshed.ui.practice.PracticeHome
import com.larakelley.woodshed.ui.practice.PracticeViewModel
import com.larakelley.woodshed.ui.practice.ReviewScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val app = application as WoodshedApplication

    enableEdgeToEdge()
    setContent {
      WoodshedTheme {
        val browse: BrowseViewModel = viewModel { BrowseViewModel(app.repository, app.practice) }
        val practice: PracticeViewModel = viewModel { PracticeViewModel(app.repository, app.practice) }
        val browseState by browse.state.collectAsStateWithLifecycle()
        val practiceUi by practice.ui.collectAsStateWithLifecycle()

        Surface(color = MaterialTheme.colorScheme.background) {
          if (practiceUi.reviewing) {
            ReviewScreen(practiceUi, practice::grade, practice::stop)
          } else {
            BrowseScreen(
              browseState,
              browse::selectTransposition,
              browse::selectFamily,
              actions = browse.actions,
              practiceTab = {
                LaunchedEffect(Unit) { practice.refresh() }
                PracticeHome(practiceUi, practice::start, practice::setDailyNewLimit)
              },
            )
          }
        }
      }
    }
  }
}
