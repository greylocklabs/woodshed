package com.larakelley.woodshed.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.R
import com.larakelley.woodshed.data.Playlist
import com.larakelley.woodshed.data.db.TuneStateEntity
import com.larakelley.woodshed.data.seed.SeedValidator
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.KeyMask
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.ReportSubject
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.delayLabel
import com.larakelley.woodshed.domain.titleSortKey
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.CallieChip
import com.larakelley.woodshed.theme.EmptyState
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.neutralSticker
import com.larakelley.woodshed.ui.report.ReportButton

/** "Add to playlist", or which playlists the tune is in, opening the picker. */
@Composable
private fun PlaylistButton(tune: TuneRow, playlists: List<Playlist>, actions: PlaylistActions) {
  var picking by rememberSaveable { mutableStateOf(false) }
  val inLists = playlists.filter { tune.id in it.tuneIds }.map { it.name }
  StickerButton(onClick = { picking = true }, sticker = neutralSticker, modifier = Modifier.height(40.dp).testTag("add-to-playlist")) {
    Text(if (inLists.isEmpty()) "📋 Add to playlist" else "📋 In ${inLists.joinToString(", ")}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
  }
  if (picking) PlaylistPicker(tune, playlists, actions, onDismiss = { picking = false })
}

/** The exercise editor's target while adding rather than editing. */
private const val NEW_EXERCISE = -1L

/** The editor's target while adding a tune rather than editing one. */
private const val NEW_TUNE = "+new"

/** Shown before each tab name in the main navigation. */
private val TAB_EMOJI = mapOf(
  "Practice" to "🎷",
  "Random" to "🎲",
  "Transcribe" to "🎧",
  "Metronome" to "⏱️",
  "Tuner" to "🎯",
  "Tunes" to "🎵",
  "Exercises" to "💪",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
  state: BrowseState,
  onSelectTransposition: (Transposition) -> Unit,
  modifier: Modifier = Modifier,
  actions: TuneActions = TuneActions(),
  playlistActions: PlaylistActions = PlaylistActions(),
  exerciseActions: ExerciseActions = ExerciseActions(),
  mediaActions: MediaActions = MediaActions(),
  onTuneFilter: ((TuneFilter) -> TuneFilter) -> Unit = {},
  onExerciseFilter: ((ExerciseFilter) -> ExerciseFilter) -> Unit = {},
  now: () -> Long = System::currentTimeMillis,
  practiceTab: (@Composable () -> Unit)? = null,
  diceTab: (@Composable () -> Unit)? = null,
  transcribeTab: (@Composable () -> Unit)? = null,
  metronomeTab: (@Composable () -> Unit)? = null,
  tunerTab: (@Composable () -> Unit)? = null,
  settings: (@Composable (onBack: () -> Unit, modifier: Modifier) -> Unit)? = null,
) {
  var settingsOpen by rememberSaveable { mutableStateOf(false) }
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var openSlug by rememberSaveable { mutableStateOf<String?>(null) }
  /** A tune's slug while editing it, [NEW_TUNE] while adding one, or null. */
  var editing by rememberSaveable { mutableStateOf<String?>(null) }
  var playlistsOpen by rememberSaveable { mutableStateOf(false) }
  var openPlaylist by rememberSaveable { mutableStateOf<Long?>(null) }
  var openExercise by rememberSaveable { mutableStateOf<Long?>(null) }
  /** An exercise's id while editing it, [NEW_EXERCISE] while adding one, or null. */
  var editingExercise by rememberSaveable { mutableStateOf<Long?>(null) }
  val tabs = buildList {
    if (practiceTab != null) add("Practice")
    if (diceTab != null) add("Random")
    if (transcribeTab != null) add("Transcribe")
    if (metronomeTab != null) add("Metronome")
    if (tunerTab != null) add("Tuner")
    add("Tunes")
    add("Exercises")
  }

  Scaffold(
    modifier = modifier,
    topBar = { TopAppBar(title = { AppTitle() }, actions = {
        TranspositionPicker(state.transposition, onSelectTransposition)
        if (settings != null) {
          TextButton(onClick = { settingsOpen = true }, modifier = Modifier.testTag("open-settings")) { Text("⚙️", style = MaterialTheme.typography.titleLarge) }
        }
      }) },
  ) { padding ->
    if (settingsOpen && settings != null) {
      settings({ settingsOpen = false }, Modifier.padding(padding))
      return@Scaffold
    }
    editing?.let { target ->
      val tune = state.tunes.firstOrNull { it.slug == target }
      TuneEditor(
        tune,
        onSave = { draft -> actions.saveTune(tune?.id, draft) { slug -> openSlug = slug; editing = null } },
        onDelete = { tune?.let { actions.deleteTune(it.id) }; openSlug = null; editing = null },
        onReset = { tune?.let { actions.resetTune(it.id) }; editing = null },
        onBack = { editing = null },
        modifier = Modifier.padding(padding),
      )
      return@Scaffold
    }
    // A tune opened from a playlist is looked up among all tunes, since the list may be filtered.
    val open = (state.allTunes.ifEmpty { state.tunes }).firstOrNull { it.slug == openSlug }
    if (open != null) {
      BackHandler { openSlug = null }
      TuneDetail(open, actions, now, onBack = { openSlug = null }, onEdit = { editing = open.slug }, modifier = Modifier.padding(padding)) {
        PlaylistButton(open, state.playlists, playlistActions)
        MediaSection(open.id, state.media[open.id].orEmpty(), mediaActions)
      }
      return@Scaffold
    }
    val playlist = state.playlists.firstOrNull { it.id == openPlaylist }
    if (playlist != null) {
      PlaylistScreen(
        playlist,
        state.allTunes.associateBy { it.id },
        playlistActions,
        onOpenTune = { openSlug = it },
        onBack = { openPlaylist = null },
        modifier = Modifier.padding(padding),
      )
      return@Scaffold
    }
    if (playlistsOpen) {
      PlaylistsScreen(state.playlists, playlistActions, onOpen = { openPlaylist = it }, onBack = { playlistsOpen = false }, modifier = Modifier.padding(padding))
      return@Scaffold
    }
    editingExercise?.let { target ->
      val editing = state.allExercises.firstOrNull { it.id == target }
      ExerciseEditor(
        editing,
        categories = state.categories,
        takenNames = state.allExercises.map { it.name.lowercase() }.toSet(),
        onSave = { draft -> exerciseActions.save(editing?.id, draft) { id -> openExercise = id; editingExercise = null } },
        onDelete = { editing?.let { exerciseActions.delete(it.id) }; openExercise = null; editingExercise = null },
        onReset = { editing?.let { exerciseActions.reset(it.id) }; editingExercise = null },
        onBack = { editingExercise = null },
        modifier = Modifier.padding(padding),
      )
      return@Scaffold
    }
    val exercise = state.allExercises.firstOrNull { it.id == openExercise }
    if (exercise != null) {
      BackHandler { openExercise = null }
      ExerciseDetail(exercise, onBack = { openExercise = null }, onEdit = { editingExercise = exercise.id }, modifier = Modifier.padding(padding))
      return@Scaffold
    }
    Column(Modifier.padding(padding).fillMaxSize()) {
      state.seedError?.let {
        Text("Could not load the bundled data: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
      }
      PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
        tabs.forEachIndexed { i, label ->
          Tab(selected = tab == i, onClick = { tab = i }, text = { Text("${TAB_EMOJI[label].orEmpty()} $label".trim()) }, modifier = Modifier.testTag("tab-$label"))
        }
      }
      when (tabs[tab]) {
        "Practice" -> practiceTab?.invoke()
        "Random" -> diceTab?.invoke()
        "Transcribe" -> transcribeTab?.invoke()
        "Metronome" -> metronomeTab?.invoke()
        "Tuner" -> tunerTab?.invoke()
        "Tunes" -> {
          TuneFilters(state, onTuneFilter, onAdd = { editing = NEW_TUNE }, onPlaylists = { playlistsOpen = true })
          if (state.tunes.isEmpty()) {
            EmptyState(
              "🔍",
              "No tunes match",
              "Nothing fits this search and these filters. Clear them, or add the tune yourself.",
              action = "Clear search and filters",
              onAction = { onTuneFilter { TuneFilter(sort = it.sort) } },
              scrolls = true,
            )
          } else {
            TuneList(state.tunes, alphabetical = state.tuneFilter.sort == TuneSort.TITLE && state.tuneFilter.playlistId == null, onOpen = { openSlug = it })
          }
        }
        else -> {
          ExerciseFilters(state, onExerciseFilter, onAdd = { editingExercise = NEW_EXERCISE })
          if (state.exercises.isEmpty()) {
            EmptyState(
              "💪",
              "No exercises match",
              "Nothing fits this search and these filters.",
              action = "Clear search and filters",
              onAction = { onExerciseFilter { ExerciseFilter(instrument = it.instrument, sort = it.sort) } },
              scrolls = true,
            )
          } else {
            ExerciseList(state.exercises, onOpen = { openExercise = it })
          }
        }
      }
    }
  }
}

/** A filter chip that opens a menu of options; [anyLabel] clears it when [allowAny]. */
/** The logo and name, in the display face. */
@Composable
private fun AppTitle() {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(36.dp))
    Text("Woodshed", style = MaterialTheme.typography.headlineSmall)
  }
}

@Composable
fun <T> DropdownChip(
  name: String,
  current: String,
  options: List<Pair<String, T>>,
  allowAny: Boolean = false,
  anyLabel: String = "Any",
  onSelect: (T?) -> Unit,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box {
    CallieChip(selected = open, onClick = { open = true }, label = { Text("$name: $current") }, modifier = Modifier.testTag("chip-$name"))
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      if (allowAny) DropdownMenuItem(text = { Text(anyLabel) }, onClick = { onSelect(null); open = false })
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
private fun TuneFilters(state: BrowseState, onFilter: ((TuneFilter) -> TuneFilter) -> Unit, onAdd: () -> Unit, onPlaylists: () -> Unit) {
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
    if (state.playlists.isNotEmpty()) {
      DropdownChip("Playlist", state.playlists.firstOrNull { it.id == f.playlistId }?.name ?: "Any", state.playlists.map { it.name to it.id }, allowAny = true) { id ->
        onFilter { it.copy(playlistId = id) }
      }
    }
    if (f.playlistId == null) {
      DropdownChip("Sort", f.sort.label, TuneSort.entries.map { it.label to it }) { o -> onFilter { it.copy(sort = o ?: TuneSort.TITLE) } }
    }
  }
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(
      if (state.tunes.size == state.totalTunes) "${state.totalTunes} tunes" else "${state.tunes.size} of ${state.totalTunes} tunes",
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.weight(1f).testTag("tune-count"),
    )
    StickerButton(onClick = onPlaylists, sticker = neutralSticker, modifier = Modifier.height(40.dp).padding(end = 8.dp).testTag("open-playlists"), contentPadding = PaddingValues(horizontal = 12.dp)) {
      Text("📋 Playlists", style = MaterialTheme.typography.labelLarge)
    }
    StickerButton(onClick = onAdd, modifier = Modifier.height(40.dp).testTag("add-tune"), contentPadding = PaddingValues(horizontal = 14.dp)) {
      Text("+ Add tune", style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
private fun ExerciseFilters(state: BrowseState, onFilter: ((ExerciseFilter) -> ExerciseFilter) -> Unit, onAdd: () -> Unit) {
  val f = state.exerciseFilter
  SearchField(f.query, "Search exercises") { q -> onFilter { it.copy(query = q) } }
  Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    DropdownChip("Instrument", f.instrument?.label ?: "All mine", state.myInstruments.map { it.label to it }, allowAny = true, anyLabel = "All mine") { i ->
      onFilter { it.copy(instrument = i) }
    }
    DropdownChip("Category", f.category ?: "Any", state.categories.map { it to it }, allowAny = true) { c -> onFilter { it.copy(category = c) } }
    DropdownChip("Sort", f.sort.label, ExerciseSort.entries.map { it.label to it }) { o -> onFilter { it.copy(sort = o ?: ExerciseSort.CATEGORY) } }
  }
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(
      if (state.exercises.size == state.totalExercises) "${state.totalExercises} exercises" else "${state.exercises.size} of ${state.totalExercises} exercises",
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.weight(1f).testTag("exercise-count"),
    )
    StickerButton(onClick = onAdd, modifier = Modifier.height(40.dp).testTag("add-exercise"), contentPadding = PaddingValues(horizontal = 14.dp)) {
      Text("+ Add exercise", style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
private fun TranspositionPicker(selected: Transposition, onSelect: (Transposition) -> Unit) {
  var open by rememberSaveable { mutableStateOf(false) }
  Box {
    TextButton(onClick = { open = true }, modifier = Modifier.testTag("transposition-picker")) {
      Text(selected.name, style = MaterialTheme.typography.titleMedium)
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
private fun TuneList(tunes: List<TuneRow>, alphabetical: Boolean, onOpen: (String) -> Unit) {
  val listState = rememberLazyListState()
  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().testTag("tune-list"), state = listState) {
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
          Column(Modifier.padding(start = 12.dp, end = if (alphabetical) 20.dp else 0.dp), horizontalAlignment = Alignment.End) {
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
    if (alphabetical && tunes.size > 30) {
      AlphabetStrip(listState, tunes.map { titleSortKey(it.title) }, Modifier.align(Alignment.CenterEnd))
    }
  }
}

@Composable
private fun TuneDetail(
  tune: TuneRow,
  actions: TuneActions,
  now: () -> Long,
  onBack: () -> Unit,
  onEdit: () -> Unit,
  modifier: Modifier = Modifier,
  extra: @Composable () -> Unit = {},
) {
  val state = tune.state
  val id = tune.id
  var confirmRemove by rememberSaveable { mutableStateOf(false) }
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      BackButton("Back to tunes", onBack, Modifier.weight(1f, fill = false))
      Spacer(Modifier.weight(1f))
      StickerButton(onClick = onEdit, sticker = neutralSticker, modifier = Modifier.height(40.dp).testTag("edit-tune")) { Text("✏️ Edit", style = MaterialTheme.typography.labelLarge) }
    }
    Text(tune.title, style = MaterialTheme.typography.headlineMedium)
    when {
      tune.userCreated -> Text("🪵 Your tune", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("your-tune"))
      tune.edited -> Text("✏️ Edited by you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("edited-tune"))
    }
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

    extra()
    HorizontalDivider()
    Text(statusLine(state, now()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("tune-status"))
    when (state?.lifecycle) {
      null -> {
        StickerButton(onClick = { actions.addToWishlist(id) }, Modifier.fillMaxWidth()) { Text("💭 Add to wishlist") }
        OutlinedButton(onClick = { actions.startLearning(id) }, Modifier.fillMaxWidth()) { Text("🌱 Start learning now") }
        OutlinedButton(onClick = { actions.markKnown(id) }, Modifier.fillMaxWidth()) { Text("I already know it") }
      }
      Lifecycle.WISHLIST -> {
        StickerButton(onClick = { actions.startLearning(id) }, Modifier.fillMaxWidth()) { Text("🌱 Start learning now") }
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
      Lifecycle.RETIRED -> StickerButton(onClick = { actions.restore(id) }, Modifier.fillMaxWidth()) { Text("Bring back") }
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
    FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Comfort.entries.forEach { level ->
        CallieChip(
          selected = comfort == level,
          onClick = { actions.setComfort(id, if (comfort == level) null else level) },
          label = { Text(level.label) },
          modifier = Modifier.testTag("comfort-${level.name.lowercase()}"),
        )
      }
    }
    KnownKeys(tune, onToggle = { pc -> actions.toggleKnownKey(id, pc) })
    HorizontalDivider()
    ReportButton(
      ReportSubject.TUNE,
      item = tune.title,
      id = tune.slug,
      details = listOfNotNull(
        // Data is kept in concert pitch, so the report names the concert key whatever the user reads in.
        "Key" to (tune.homeKey?.let { "concert ${it.displayName}" + if (tune.transposition != Transposition.C) ", shown as ${tune.key} for ${tune.transposition.name}" else "" } ?: "unknown"),
        tune.credit.takeIf { it.isNotEmpty() }?.let { "Credit" to it },
        tune.shape.takeIf { it.isNotEmpty() }?.let { "Form" to it },
        "Collection" to tune.collection,
        "Checked" to if (tune.verified) "yes" else "no, bulk import",
      ),
    )
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
  FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Key.CIRCLE_OF_FOURTHS.forEach { written ->
      val concert = Math.floorMod(written - transposition.offset, 12)
      val label = Key(written, minor).displayName.removeSuffix(" minor") + if (minor) "m" else ""
      CallieChip(selected = KeyMask.contains(mask, concert), onClick = { onToggle(concert) }, label = { Text(label) }, modifier = Modifier.testTag("key-$concert"))
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
private fun ExerciseList(exercises: List<ExerciseRow>, onOpen: (Long) -> Unit) {
  LazyColumn(Modifier.fillMaxSize().testTag("exercise-list")) {
    items(exercises, key = { it.id }) { exercise ->
      Column(Modifier.fillMaxWidth().clickable { onOpen(exercise.id) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
        Text(exercise.summary + if (exercise.instruments != "All instruments") ", ${exercise.instruments}" else "", style = MaterialTheme.typography.bodySmall)
        Text(exercise.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
      }
      HorizontalDivider()
    }
  }
}
