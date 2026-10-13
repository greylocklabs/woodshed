package com.larakelley.woodshed.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.data.ExerciseDraft
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.CallieChip
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.SwitchRow
import com.larakelley.woodshed.ui.jsonSaver

/**
 * Adds an exercise, or edits one: in place for the user's own, as edits laid over the original for seeded ones.
 * [exercise] is null when adding. [categories] are offered as choices; [takenNames] are names already in use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditor(
  exercise: ExerciseRow?,
  categories: List<String>,
  takenNames: Set<String>,
  onSave: (ExerciseDraft) -> Unit,
  onDelete: () -> Unit,
  onReset: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)
  var draft by rememberSaveable(exercise?.id, stateSaver = jsonSaver()) { mutableStateOf(exercise?.draft ?: ExerciseDraft()) }
  var tried by remember { mutableStateOf(false) }
  var confirm by rememberSaveable { mutableStateOf<String?>(null) }
  val others = takenNames - setOfNotNull(exercise?.name?.lowercase())
  val problems = draft.problems { it.lowercase() in others }
  val callie = LocalCallie.current

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BackButton("Cancel", onBack)
    Text(if (exercise == null) "Add an exercise" else "Edit ${exercise.name}", style = MaterialTheme.typography.headlineMedium)
    if (exercise != null && !exercise.userCreated) {
      Helper("Your changes are kept beside the original, so updates never overwrite them. You can reset to the original any time.")
    }
    OutlinedTextField(
      value = draft.name,
      onValueChange = { draft = draft.copy(name = it) },
      label = { Text("Name") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      modifier = Modifier.fillMaxWidth().testTag("exercise-name"),
    )
    OutlinedTextField(
      value = draft.description,
      onValueChange = { draft = draft.copy(description = it) },
      label = { Text("How to do it") },
      minLines = 3,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      modifier = Modifier.fillMaxWidth().testTag("exercise-description"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = draft.minutes,
        onValueChange = { draft = draft.copy(minutes = it) },
        label = { Text("Minutes") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f).testTag("exercise-minutes"),
      )
      OutlinedTextField(
        value = draft.tempoBpm,
        onValueChange = { draft = draft.copy(tempoBpm = it) },
        label = { Text("Tempo, bpm") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f).testTag("exercise-tempo"),
      )
    }

    SectionLabel("Category")
    PillChoice((categories + draft.category).distinct().map { it to it }, draft.category, { draft = draft.copy(category = it) }, tagPrefix = "exercise-category")
    SectionLabel("Difficulty")
    PillChoice((1..5).map { "$it" to it }, draft.difficulty, { draft = draft.copy(difficulty = it) }, tagPrefix = "exercise-difficulty")
    SectionLabel("For")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      CallieChip(selected = draft.families.isEmpty(), onClick = { draft = draft.copy(families = emptySet()) }, label = { Text("All instruments") })
      Family.entries.forEach { f ->
        CallieChip(
          selected = f in draft.families,
          onClick = { draft = draft.copy(families = if (f in draft.families) draft.families - f else draft.families + f) },
          label = { Text(f.seedName) },
          modifier = Modifier.testTag("exercise-family-${f.name.lowercase()}"),
        )
      }
    }
    SwitchRow(
      "Done to a tune",
      draft.appliesToTune,
      { draft = draft.copy(appliesToTune = it) },
      helper = "Pairs with tunes on practice cards and in sessions",
      modifier = Modifier.testTag("exercise-tune"),
    )

    if (tried && problems.isNotEmpty()) problems.forEach { Text(it, color = callie.danger, modifier = Modifier.testTag("exercise-problem")) }
    StickerButton(
      onClick = {
        tried = true
        if (problems.isEmpty()) onSave(draft)
      },
      modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp).testTag("exercise-save"),
    ) { Text(if (exercise == null) "Add exercise" else "Save", style = MaterialTheme.typography.titleMedium) }

    when {
      exercise?.userCreated == true ->
        TextButton(onClick = { confirm = "delete" }, modifier = Modifier.testTag("exercise-delete")) { Text("Delete this exercise", color = callie.danger) }
      exercise?.edited == true ->
        TextButton(onClick = { confirm = "reset" }, modifier = Modifier.testTag("exercise-reset")) { Text("Reset to the original", color = callie.danger) }
    }
  }

  confirm?.let { what ->
    AlertDialog(
      onDismissRequest = { confirm = null },
      title = { Text(if (what == "delete") "Delete ${exercise?.name}?" else "Reset ${exercise?.name}?") },
      text = { Text(if (what == "delete") "It leaves your exercises and future sessions. Past practice is kept." else "Your edits are dropped and the original comes back.") },
      confirmButton = {
        TextButton(onClick = { confirm = null; if (what == "delete") onDelete() else onReset() }, modifier = Modifier.testTag("exercise-confirm")) {
          Text(if (what == "delete") "Delete" else "Reset", color = callie.danger)
        }
      },
      dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
    )
  }
}
