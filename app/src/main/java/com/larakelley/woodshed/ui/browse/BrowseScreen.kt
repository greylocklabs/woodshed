package com.larakelley.woodshed.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
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
import com.larakelley.woodshed.data.seed.SeedValidator
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.KeyMask
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
  onTuneFilter: ((TuneFilter) -> TuneFilter) -> Unit = {},
  onExerciseFilter: ((ExerciseFilter) -> ExerciseFilter) -> Unit = {},
  now: () -> Long = System::currentTimeMillis,
  practiceTab: (@Composable () -> Unit)? = null,
  diceTab: (@Composable () -> Unit)? = null,
) {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var openSlug by rememberSaveable { mutableStateOf<String?>(null) }
  val tabs = buildList {
    if (practiceTab != null) add("Practice")
    if (diceTab != null) add("Dice")
    add("Tunes")
    add("Exercises")
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
      PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
        tabs.forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }) }
      }
      when (tabs[tab]) {
        "Practice" -> practiceTab?.invoke()
        "Dice" -> diceTab?.invoke()
        "Tunes" -> {
          TuneFilters(state, onTuneFilter)
          TuneList(state.tunes, onOpen = { openSlug = it })
        }
        else -> {
          ExerciseFilters(state, onSelectFamily, onExerciseFilter)
          ExerciseList(state.exercises)
        }
      }
    }
  }
}

/** A filter chip that opens a menu of options; "Any" clears it when [allowAny]. */
@Composable
fun <T> DropdownChip(name: String, current: String, options: List<Pair<String, T>>, allowAny: Boolean = false, onSelect: (T?) -> Unit) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box {
    FilterChip(selected = open, onClick = { open = true }, label = { Text("$name: $current") }, modifier = Modifier.testTag("chip-$name"))
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      if (allowAny) DropdownMenuItem(text = { Text("Any") }, onClick = { onSelect(null); open = false })
      options.forEach { (label, value) -> DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(value); open = false }) }
    }
  }
}

@Composable
private fun SearchField(query: String, placeholder: String, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = query,
    onValueChange = onChange,
    placeholder = { Text(placeholder) },
    singleLine = true,
    trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("Clear") } },
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search"),
  )
}

@Composable
private fun TuneFilters(state: BrowseState, onFilter: ((TuneFilter) -> TuneFilter) -> Unit) {
  val f = state.tuneFilter
  SearchField(f.query, "Search title or composer") { q -> onFilter { it.copy(query = q) } }
  Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    DropdownChip("Show", f.status.label, StatusFilter.entries.map { it.label to it }) { s -> onFilter { it.copy(status = s ?: StatusFilter.ALL) } }
    DropdownChip("Style", f.style ?: "Any", state.styles.map { it to it }, allowAny = true) { s -> onFilter { it.copy(style = s) } }
    DropdownChip("Collection", f.collection ?: "Any", SeedValidator.COLLECTIONS.map { it to it }, allowAny = true) { c -> onFilter { it.copy(collection = c) } }
    DropdownChip("Tonality", f.tonality?.label ?: "Any", Tonality.entries.map { it.label to it }, allowAny = true) { t -> onFilter { it.copy(tonality = t) } }
    DropdownChip("Comfort", f.minComfort?.let { "${it.label}+" } ?: "Any", Comfort.entries.map { "${it.label} or better" to it }, allowAny = true) { c ->
      onFilter { it.copy(minComfort = c) }
    }
  }
  Text(
    if (state.tunes.size == state.totalTunes) "${state.totalTunes} tunes" else "${state.tunes.size} of ${state.totalTunes} tunes",
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("tune-count"),
  )
}

@Composable
private fun ExerciseFilters(state: BrowseState, onSelectFamily: (Family?) -> Unit, onFilter: ((ExerciseFilter) -> ExerciseFilter) -> Unit) {
  val f = state.exerciseFilter
  SearchField(f.query, "Search exercises") { q -> onFilter { it.copy(query = q) } }
  Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    DropdownChip("Category", f.category ?: "Any", state.categories.map { it to it }, allowAny = true) { c -> onFilter { it.copy(category = c) } }
    FilterChip(selected = state.family == null, onClick = { onSelectFamily(null) }, label = { Text("All horns") })
    Family.entries.forEach { family ->
      FilterChip(selected = state.family == family, onClick = { onSelectFamily(family) }, label = { Text(family.displayName) })
    }
  }
  Text(
    if (state.exercises.size == state.totalExercises) "${state.totalExercises} exercises" else "${state.exercises.size} of ${state.totalExercises} exercises",
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("exercise-count"),
  )
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
          val badges = listOfNotNull(tune.status, tune.knowledge?.comfort?.label)
          if (badges.isNotEmpty()) {
            Text(badges.joinToString(", "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
          }
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
  var confirmRemove by rememberSaveable { mutableStateOf(false) }
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    TextButton(onClick = onBack) { Text("Back to tunes") }
    Text(tune.title, style = MaterialTheme.typography.headlineMedium)
    if (tune.credit.isNotEmpty()) Text(tune.credit)
    if (tune.shape.isNotEmpty()) Text(tune.shape)
    Text(tune.key, style = MaterialTheme.typography.titleLarge)
    if (tune.collection == "Trad") Text("Trad repertoire", style = MaterialTheme.typography.bodyMedium)
    if (!tune.verified) {
      Text(
        "Details for this tune were imported in bulk and not yet checked; blanks mean unknown.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("unverified"),
      )
    }

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
      }
      Lifecycle.LEARNING, Lifecycle.REPERTOIRE -> {
        if (tune.homeKey != null) {
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
    if (state != null) {
      OutlinedButton(
        onClick = { if (state.lifecycle == Lifecycle.WISHLIST) actions.removeFromMyTunes(id) else confirmRemove = true },
        modifier = Modifier.fillMaxWidth().testTag("remove"),
      ) { Text(if (state.lifecycle == Lifecycle.WISHLIST) "Remove from wishlist" else "Remove from my tunes") }
    }

    HorizontalDivider()
    Text("How well I know it", style = MaterialTheme.typography.titleMedium)
    val comfort = tune.knowledge?.comfort
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Comfort.entries.forEach { level ->
        FilterChip(
          selected = comfort == level,
          onClick = { actions.setComfort(id, if (comfort == level) null else level) },
          label = { Text(level.label) },
          modifier = Modifier.testTag("comfort-${level.name.lowercase()}"),
        )
      }
    }
    KnownKeys(tune, onToggle = { pc -> actions.toggleKnownKey(id, pc) })
  }

  if (confirmRemove) {
    AlertDialog(
      onDismissRequest = { confirmRemove = false },
      title = { Text("Remove ${tune.title}?") },
      text = { Text("It goes back to the library and its schedule resets. Your review history, comfort and known keys are kept.") },
      confirmButton = { TextButton(onClick = { actions.removeFromMyTunes(id); confirmRemove = false }, modifier = Modifier.testTag("confirm-remove")) { Text("Remove") } },
      dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
    )
  }
}

/** Twelve keys round the circle of fourths from written C, stored as concert pitch. */
@Composable
private fun KnownKeys(tune: TuneRow, onToggle: (Int) -> Unit) {
  val mask = tune.knowledge?.knownKeys ?: 0
  val minor = tune.tonality == Tonality.MINOR
  val transposition = tune.transposition
  Text("Keys I know it in (${KeyMask.count(mask)} of 12)", style = MaterialTheme.typography.titleMedium)
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Key.CIRCLE_OF_FOURTHS.forEach { written ->
      val concert = Math.floorMod(written - transposition.offset, 12)
      val label = Key(written, minor).displayName.removeSuffix(" minor") + if (minor) "m" else ""
      FilterChip(selected = KeyMask.contains(mask, concert), onClick = { onToggle(concert) }, label = { Text(label) }, modifier = Modifier.testTag("key-$concert"))
    }
  }
}

private fun statusLine(state: TuneStateEntity?, now: Long): String {
  if (state == null) return "Not in my tunes"
  val due = state.dueAt
  return when (state.lifecycle) {
    Lifecycle.WISHLIST -> "On my wishlist"
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
