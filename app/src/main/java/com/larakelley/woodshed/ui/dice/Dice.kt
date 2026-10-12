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
import com.larakelley.woodshed.data.TunePool
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.seed.SeedValidator
import com.larakelley.woodshed.domain.ClickTrack
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.KeyMask
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.delayLabel
import com.larakelley.woodshed.theme.EmptyState
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.ui.browse.DropdownChip
import com.larakelley.woodshed.ui.practice.GradeButtons
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

data class DiceUi(
  val constraints: DiceConstraints = DiceConstraints(),
  val card: RolledCard? = null,
  val nothingMatches: Boolean = false,
  val styles: List<String> = emptyList(),
  val exercises: List<String> = emptyList(),
  val graded: Grade? = null,
)

class DiceViewModel(
  private val repository: WoodshedRepository,
  private val practice: PracticeService,
  private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val _ui = MutableStateFlow(DiceUi())
  val ui: StateFlow<DiceUi> = _ui.asStateFlow()
  private var roll: Roll? = null

  fun setOptions(styles: List<String>, exercises: List<String>) = _ui.update { it.copy(styles = styles, exercises = exercises) }

  fun constrain(change: (DiceConstraints) -> DiceConstraints) = _ui.update { it.copy(constraints = change(it.constraints)) }

  fun roll() {
    viewModelScope.launch {
      val r = practice.roll(_ui.value.constraints)
      roll = r
      val card = r?.let { present(it) }
      _ui.update { it.copy(card = card, nothingMatches = r == null, graded = null) }
    }
  }

  fun grade(grade: Grade) {
    val card = roll?.let { practice.cardFor(it, repository.transposition.value) } ?: return
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
      transposition == Transposition.C -> "Concert ${written.displayName}"
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
) {
  val c = ui.constraints
  Column(Modifier.fillMaxSize()) {
    Column(
      Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      FlowRow(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DropdownChip("Tunes", c.pool.label, TunePool.entries.map { it.label to it }) { p -> onConstrain { it.copy(pool = p ?: TunePool.MY_TUNES) } }
        DropdownChip("Style", c.style ?: "Any", ui.styles.map { it to it }, allowAny = true) { s -> onConstrain { it.copy(style = s) } }
        DropdownChip("Collection", c.collection ?: "Any", SeedValidator.COLLECTIONS.map { it to it }, allowAny = true) { v -> onConstrain { it.copy(collection = v) } }
        DropdownChip("Exercise", c.exerciseName ?: "Any", ui.exercises.map { it to it }, allowAny = true) { e -> onConstrain { it.copy(exerciseName = e) } }
        DropdownChip("Key", c.keyChoice.label, KeyChoice.entries.map { it.label to it }) { k -> onConstrain { it.copy(keyChoice = k ?: KeyChoice.USUAL) } }
      }
      StickerButton(onClick = onRoll, modifier = Modifier.fillMaxWidth().height(64.dp).testTag("roll")) {
        Text(if (ui.card == null) "🎲 Roll" else "🎲 Roll again", style = MaterialTheme.typography.titleLarge)
      }
      if (ui.nothingMatches) {
        EmptyState("🎲", "Nothing to roll", "No tunes fit these choices. Loosen them, or add tunes to your list from the Tunes tab.")
      }
      ui.card?.let { card ->
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
    ui.card?.buttons?.let { GradeButtons(it, onGrade, Modifier.fillMaxHeight(0.25f)) }
  }
}
