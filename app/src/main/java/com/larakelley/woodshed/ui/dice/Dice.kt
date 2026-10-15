package com.larakelley.woodshed.ui.dice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.DiceConstraints
import com.larakelley.woodshed.data.KeyChoice
import com.larakelley.woodshed.data.PracticeService
import com.larakelley.woodshed.data.Roll
import com.larakelley.woodshed.data.RollKind
import com.larakelley.woodshed.data.SoloPool
import com.larakelley.woodshed.data.TunePool
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.clock
import com.larakelley.woodshed.data.seed.SeedValidator
import com.larakelley.woodshed.domain.ClickTrack
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.KeyMask
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.delayLabel
import com.larakelley.woodshed.theme.EmptyState
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.ui.browse.DropdownChip
import com.larakelley.woodshed.ui.practice.GradeButtons
import com.larakelley.woodshed.ui.transcribe.soloKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RolledCard(
  val title: String,
  val key: String,
  val exercise: String,
  val instructions: String,
  val shape: String,
  val beatsPerBar: Int,
  /** Present when the tune is in the practice queue, so the roll can be graded. */
  val buttons: List<Pair<Grade, String>>?,
  /** The rolled key in concert pitch and whether it is already checked off, when there is one. */
  val concertPitchClass: Int?,
  val keyKnown: Boolean,
)

/** A rolled exercise and what it asks for. */
data class RolledExercise(val name: String, val detail: String, val instructions: String)

/** A rolled solo, with its key for the user's horn. */
data class RolledSolo(val id: Long, val title: String, val soloist: String, val key: String?, val times: String?)

data class DiceUi(
  val constraints: DiceConstraints = DiceConstraints(),
  val card: RolledCard? = null,
  val solo: RolledSolo? = null,
  val exercise: RolledExercise? = null,
  val nothingMatches: Boolean = false,
  val styles: List<String> = emptyList(),
  val exercises: List<String> = emptyList(),
  /** Exercise categories to roll from, in session order. */
  val categories: List<String> = emptyList(),
  val graded: Grade? = null,
  /** Playlists to roll from, by id and name. */
  val playlists: List<Pair<Long, String>> = emptyList(),
)

class DiceViewModel(
  private val repository: WoodshedRepository,
  private val practice: PracticeService,
  private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val _ui = MutableStateFlow(DiceUi())
  val ui: StateFlow<DiceUi> = _ui.asStateFlow()
  private var roll: Roll? = null

  /** When the current roll was shown, so a grade logs how long it was worked on. */
  private var rolledAt = 0L

  init {
    viewModelScope.launch {
      repository.playlists.collect { lists ->
        _ui.update { ui ->
          // A deleted playlist stops constraining the roll.
          val kept = ui.constraints.playlistId?.takeIf { id -> lists.any { it.id == id } }
          ui.copy(playlists = lists.map { it.id to it.name }, constraints = ui.constraints.copy(playlistId = kept))
        }
      }
    }
  }

  fun setOptions(styles: List<String>, exercises: List<String>, categories: List<String> = emptyList()) =
    _ui.update { it.copy(styles = styles, exercises = exercises, categories = categories) }

  fun constrain(change: (DiceConstraints) -> DiceConstraints) = _ui.update { ui ->
    val next = change(ui.constraints)
    // A result from the other kind of roll would show under the wrong heading.
    if (next.kind != ui.constraints.kind) {
      roll = null
      ui.copy(constraints = next, card = null, solo = null, exercise = null, nothingMatches = false, graded = null)
    } else {
      ui.copy(constraints = next)
    }
  }

  fun roll() {
    when (_ui.value.constraints.kind) {
      RollKind.SOLO -> return rollSolo()
      RollKind.EXERCISE -> return rollExercise()
      RollKind.TUNE -> Unit
    }
    viewModelScope.launch {
      val r = practice.roll(_ui.value.constraints)
      roll = r
      rolledAt = now()
      val card = r?.let { present(it) }
      _ui.update { it.copy(card = card, solo = null, exercise = null, nothingMatches = r == null, graded = null) }
    }
  }

  private fun rollExercise() {
    roll = null
    viewModelScope.launch {
      val e = practice.rollExercise(_ui.value.constraints, repository.instrument.value)
      val exercise = e?.let {
        val detail = listOfNotNull(it.category, "${it.minutes} min", "difficulty ${it.difficulty}", it.tempoBpm?.let { bpm -> "$bpm bpm" })
        RolledExercise(it.name, detail.joinToString(", "), it.description)
      }
      _ui.update { it.copy(card = null, solo = null, exercise = exercise, nothingMatches = e == null, graded = null) }
    }
  }

  private fun rollSolo() {
    roll = null
    viewModelScope.launch {
      val s = practice.rollSolo(_ui.value.constraints)
      val solo = s?.let {
        val times = it.startSeconds?.let { start -> "Solo ${clock(start)}" + (it.endSeconds?.let { end -> " to ${clock(end)}" } ?: "") }
        RolledSolo(it.id, it.title, "${it.soloist}, ${it.instrument.lowercase()}", soloKey(it, repository.transposition.value), times)
      }
      _ui.update { it.copy(card = null, solo = solo, exercise = null, nothingMatches = s == null, graded = null) }
    }
  }

  fun grade(grade: Grade) {
    // The buttons go as soon as one is tapped, so a second tap before the write lands is ignored.
    if (_ui.value.card?.buttons == null) return
    val card = roll?.let { practice.cardFor(it, repository.transposition.value, shownAt = rolledAt) } ?: return
    _ui.update { it.copy(card = it.card?.copy(buttons = null)) }
    viewModelScope.launch {
      practice.grade(card, grade)
      _ui.update { it.copy(graded = grade, card = it.card?.copy(buttons = null)) }
    }
  }

  fun toggleKeyKnown() {
    val r = roll ?: return
    val pc = r.concertKey?.pitchClass ?: return
    viewModelScope.launch {
      practice.toggleKnownKey(r.tune.id, pc)
      _ui.update { it.copy(card = it.card?.copy(keyKnown = !it.card.keyKnown)) }
    }
  }

  private suspend fun present(r: Roll): RolledCard {
    val transposition = repository.transposition.value
    val card = practice.cardFor(r, transposition)
    val written = r.concertKey?.writtenFor(transposition)
    val key = when {
      written == null -> "Key unknown"
      transposition == Transposition.C -> written.displayName
      else -> "Written ${written.displayName}"
    }
    val known = practice.knownKeys(r.tune.id)
    return RolledCard(
      title = r.tune.title,
      key = key,
      exercise = r.exercise.name,
      instructions = r.exercise.description,
      shape = listOfNotNull(r.tune.form, r.tune.bars?.let { "$it bars" }, r.tune.style.takeIf { it != r.tune.form }).joinToString(", "),
      beatsPerBar = ClickTrack.beatsPerBar(r.tune.timeSignature),
      buttons = card?.let { c -> practice.preview(c).map { (g, s) -> g to delayLabel(now(), s.dueAt ?: now()) } },
      concertPitchClass = r.concertKey?.pitchClass,
      keyKnown = r.concertKey?.let { KeyMask.contains(known, it.pitchClass) } ?: false,
    )
  }
}

@Composable
fun DiceScreen(
  ui: DiceUi,
  onConstrain: ((DiceConstraints) -> DiceConstraints) -> Unit,
  onRoll: () -> Unit,
  onGrade: (Grade) -> Unit,
  onToggleKeyKnown: () -> Unit,
  onOpenSolo: (Long) -> Unit = {},
) {
  val c = ui.constraints
  val solos = c.kind == RollKind.SOLO
  val exercises = c.kind == RollKind.EXERCISE
  Column(Modifier.fillMaxSize()) {
    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      PillChoice(RollKind.entries.map { it.label to it }, c.kind, { k -> onConstrain { it.copy(kind = k) } }, tagPrefix = "roll-kind")
      if (exercises) {
        FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          DropdownChip(
            "Category",
            c.exerciseCategory ?: "Any",
            ui.categories.map {
              it to it
            },
            allowAny = true,
          ) { v -> onConstrain { it.copy(exerciseCategory = v) } }
          DropdownChip("Difficulty", c.exerciseDifficulty?.toString() ?: "Any", (1..5).map { it.toString() to it }, allowAny = true) { d ->
            onConstrain { it.copy(exerciseDifficulty = d) }
          }
        }
      } else if (solos) {
        DropdownChip("Solos", c.soloPool.label, SoloPool.entries.map { it.label to it }) { p -> onConstrain { it.copy(soloPool = p ?: SoloPool.MINE) } }
      } else {
        FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          if (c.playlistId == null) {
            DropdownChip("Tunes", c.pool.label, TunePool.entries.map { it.label to it }) { p -> onConstrain { it.copy(pool = p ?: TunePool.MY_TUNES) } }
          }
          if (ui.playlists.isNotEmpty()) {
            DropdownChip(
              "Playlist",
              ui.playlists.firstOrNull {
                it.first == c.playlistId
              }?.second ?: "Any",
              ui.playlists.map { it.second to it.first },
              allowAny = true,
            ) { id ->
              onConstrain { it.copy(playlistId = id) }
            }
          }
          DropdownChip("Style", c.style ?: "Any", ui.styles.map { it to it }, allowAny = true) { s -> onConstrain { it.copy(style = s) } }
          DropdownChip(
            "Collection",
            c.collection ?: "Any",
            SeedValidator.COLLECTIONS.map {
              it to it
            },
            allowAny = true,
          ) { v -> onConstrain { it.copy(collection = v) } }
          DropdownChip("Exercise", c.exerciseName ?: "Any", ui.exercises.map { it to it }, allowAny = true) { e -> onConstrain { it.copy(exerciseName = e) } }
          DropdownChip("Key", c.keyChoice.label, KeyChoice.entries.map { it.label to it }) { k -> onConstrain { it.copy(keyChoice = k ?: KeyChoice.USUAL) } }
        }
      }
      StickerButton(onClick = onRoll, modifier = Modifier.fillMaxWidth().height(64.dp).testTag("roll")) {
        Text(if (ui.card == null && ui.solo == null && ui.exercise == null) "🎲 Roll" else "🎲 Roll again", style = MaterialTheme.typography.titleLarge)
      }
      if (ui.nothingMatches) {
        if (exercises) {
          EmptyState("💪", "Nothing to roll", "No exercises for your instrument fit these choices. Loosen them.")
        } else if (solos) {
          EmptyState("🎧", "Nothing to roll", "No solos fit. Pick a wider set, or add solos to your list from the Transcribe tab.")
        } else {
          EmptyState("🎲", "Nothing to roll", "No tunes fit these choices. Loosen them, or add tunes to your list from the Tunes tab.")
        }
      }
      ui.exercise?.takeIf { exercises }?.let { e ->
        Text(e.name, style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("dice-exercise-title"))
        Text(e.detail, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(e.instructions, style = MaterialTheme.typography.titleMedium)
      }
      ui.solo?.takeIf { solos }?.let { s ->
        Text(s.title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("dice-solo-title"))
        Text(s.soloist, style = MaterialTheme.typography.titleMedium)
        s.key?.let { Text(it, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
        s.times?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        StickerButton(onClick = { onOpenSolo(s.id) }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("dice-open-solo")) {
          Text("🎧 Open solo", style = MaterialTheme.typography.titleMedium)
        }
      }
      ui.card?.takeIf { c.kind == RollKind.TUNE }?.let { card ->
        Text(card.title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("dice-title"))
        Text(card.key, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        if (card.shape.isNotEmpty()) Text(card.shape, style = MaterialTheme.typography.titleMedium)
        Text(card.exercise, style = MaterialTheme.typography.headlineSmall)
        Text(card.instructions, style = MaterialTheme.typography.titleMedium)
        if (card.concertPitchClass != null) {
          OutlinedButton(onClick = onToggleKeyKnown, modifier = Modifier.testTag("dice-key-known")) {
            Text(if (card.keyKnown) "Known in this key (undo)" else "I know it in this key")
          }
        }
        ui.graded?.let { Text("Logged as ${it.name.lowercase()}.") }
      }
    }
    ui.card?.takeIf { c.kind == RollKind.TUNE }?.buttons?.let { GradeButtons(it, onGrade, Modifier.fillMaxHeight(0.25f)) }
  }
}
