package com.larakelley.woodshed.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.delayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
  state: BrowseState,
  onSelectTransposition: (Transposition) -> Unit,
  onSelectFamily: (Family?) -> Unit,
  modifier: Modifier = Modifier,
  actions: TuneActions = TuneActions(),
  now: () -> Long = System::currentTimeMillis,
  practiceTab: (@Composable () -> Unit)? = null,
) {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var openSlug by rememberSaveable { mutableStateOf<String?>(null) }
  val tabs = buildList {
    if (practiceTab != null) add("Practice")
    add("Tunes (${state.tunes.size})")
    add("Exercises (${state.exercises.size})")
  }

  Scaffold(
    modifier = modifier,
    topBar = { TopAppBar(title = { Text("Woodshed") }, actions = { TranspositionPicker(state.transposition, onSelectTransposition) }) },
  ) { padding ->
    val open = state.tunes.firstOrNull { it.slug == openSlug }
    if (open != null) {
      BackHandler { openSlug = null }
      TuneDetail(open, actions, now, onBack = { openSlug = null }, modifier = Modifier.padding(padding))
      return@Scaffold
    }
    Column(Modifier.padding(padding).fillMaxSize()) {
      state.seedError?.let {
        Text("Could not load the bundled data: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
      }
      PrimaryTabRow(selectedTabIndex = tab) {
        tabs.forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }) }
      }
      when (tabs[tab].substringBefore(" (")) {
        "Practice" -> practiceTab?.invoke()
        "Tunes" -> TuneList(state.tunes, onOpen = { openSlug = it })
        else -> {
          FamilyFilter(state.family, onSelectFamily)
          ExerciseList(state.exercises)
        }
      }
    }
  }
}

@Composable
private fun TranspositionPicker(selected: Transposition, onSelect: (Transposition) -> Unit) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box {
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("transposition-picker")) {
      Text(if (selected == Transposition.C) "Concert" else "${selected.name} horn")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      Transposition.entries.forEach { transposition ->
        DropdownMenuItem(
          text = {
            Column {
              Text(transposition.name, style = MaterialTheme.typography.titleMedium)
              Text(transposition.examples, style = MaterialTheme.typography.bodySmall)
            }
          },
          onClick = { onSelect(transposition); open = false },
        )
      }
    }
  }
}

@Composable
private fun FamilyFilter(selected: Family?, onSelect: (Family?) -> Unit) {
  Row(
    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
    Family.entries.forEach { family ->
      FilterChip(selected = selected == family, onClick = { onSelect(family) }, label = { Text(family.displayName) })
    }
  }
}

@Composable
private fun TuneList(tunes: List<TuneRow>, onOpen: (String) -> Unit) {
  LazyColumn(Modifier.fillMaxSize().testTag("tune-list")) {
    items(tunes, key = { it.slug }) { tune ->
      Row(
        Modifier.fillMaxWidth().clickable { onOpen(tune.slug) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(tune.title, style = MaterialTheme.typography.titleMedium)
          if (tune.credit.isNotEmpty()) Text(tune.credit, style = MaterialTheme.typography.bodySmall)
          if (tune.shape.isNotEmpty()) Text(tune.shape, style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
          Text(tune.key, style = MaterialTheme.typography.titleMedium)
          tune.status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        }
      }
      HorizontalDivider()
    }
  }
}

@Composable
private fun TuneDetail(tune: TuneRow, actions: TuneActions, now: () -> Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val state = tune.state
  val id = tune.id
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    TextButton(onClick = onBack) { Text("Back to tunes") }
    Text(tune.title, style = MaterialTheme.typography.headlineMedium)
    if (tune.credit.isNotEmpty()) Text(tune.credit)
    if (tune.shape.isNotEmpty()) Text(tune.shape)
    Text(tune.key, style = MaterialTheme.typography.titleLarge)

    HorizontalDivider()
    Text(statusLine(state, now()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("tune-status"))
    when (state?.lifecycle) {
      null -> {
        Button(onClick = { actions.addToWishlist(id) }, Modifier.fillMaxWidth()) { Text("Add to wishlist") }
        OutlinedButton(onClick = { actions.startLearning(id) }, Modifier.fillMaxWidth()) { Text("Start learning now") }
        OutlinedButton(onClick = { actions.markKnown(id) }, Modifier.fillMaxWidth()) { Text("I already know it") }
      }
      Lifecycle.WISHLIST -> {
        Button(onClick = { actions.startLearning(id) }, Modifier.fillMaxWidth()) { Text("Start learning now") }
        OutlinedButton(onClick = { actions.markKnown(id) }, Modifier.fillMaxWidth()) { Text("I already know it") }
        OutlinedButton(onClick = { actions.removeFromWishlist(id) }, Modifier.fillMaxWidth()) { Text("Remove from wishlist") }
      }
      Lifecycle.LEARNING, Lifecycle.REPERTOIRE -> {
        if (Key.parse(tune.rawKey) != null) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Practice in every key")
              Text("One step round the circle of fourths each time it comes up", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
              checked = state.keyPolicy == KeyPolicy.CYCLE,
              onCheckedChange = { actions.setKeyPolicy(id, if (it) KeyPolicy.CYCLE else KeyPolicy.FIXED) },
              modifier = Modifier.testTag("cycle-keys"),
            )
          }
        }
        OutlinedButton(onClick = { actions.retire(id) }, Modifier.fillMaxWidth()) { Text("Retire") }
      }
      Lifecycle.RETIRED -> Button(onClick = { actions.restore(id) }, Modifier.fillMaxWidth()) { Text("Bring back") }
    }
  }
}

private fun statusLine(state: TuneStateEntity?, now: Long): String {
  if (state == null) return "In the library"
  val due = state.dueAt
  return when (state.lifecycle) {
    Lifecycle.WISHLIST -> "On your wishlist"
    Lifecycle.RETIRED -> "Retired"
    else -> buildString {
      append(state.lifecycle.label)
      when {
        due == null -> {}
        due <= now -> append(", due now")
        else -> append(", due in ${delayLabel(now, due)}")
      }
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
