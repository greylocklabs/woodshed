package com.larakelley.woodshed.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.domain.Instrument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(state: BrowseState, onSelectInstrument: (Instrument?) -> Unit, modifier: Modifier = Modifier) {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  Scaffold(
    modifier = modifier,
    topBar = { TopAppBar(title = { Text("Woodshed") }, actions = { InstrumentPicker(state.instrument, onSelectInstrument) }) },
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      state.seedError?.let {
        Text("Could not load the bundled data: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
      }
      PrimaryTabRow(selectedTabIndex = tab) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Tunes (${state.tunes.size})") })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Exercises (${state.exercises.size})") })
      }
      when (tab) {
        0 -> TuneList(state.tunes)
        else -> ExerciseList(state.exercises)
      }
    }
  }
}

@Composable
private fun InstrumentPicker(selected: Instrument?, onSelect: (Instrument?) -> Unit) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box {
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("instrument-picker")) {
      Text(selected?.displayName ?: "Concert pitch")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(text = { Text("Concert pitch") }, onClick = { onSelect(null); open = false })
      Instrument.entries.forEach { instrument ->
        DropdownMenuItem(text = { Text(instrument.displayName) }, onClick = { onSelect(instrument); open = false })
      }
    }
  }
}

@Composable
private fun TuneList(tunes: List<TuneRow>) {
  LazyColumn(Modifier.fillMaxSize().testTag("tune-list")) {
    items(tunes, key = { it.slug }) { tune ->
      Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(tune.title, style = MaterialTheme.typography.titleMedium)
          if (tune.credit.isNotEmpty()) Text(tune.credit, style = MaterialTheme.typography.bodySmall)
          if (tune.shape.isNotEmpty()) Text(tune.shape, style = MaterialTheme.typography.bodySmall)
        }
        Text(tune.key, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
      }
      HorizontalDivider()
    }
  }
}

@Composable
private fun ExerciseList(exercises: List<ExerciseRow>) {
  LazyColumn(Modifier.fillMaxSize().testTag("exercise-list")) {
    items(exercises, key = { it.name }) { exercise ->
      Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
        Text(exercise.summary, style = MaterialTheme.typography.bodySmall)
        Text(exercise.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
      }
      HorizontalDivider()
    }
  }
}
