package com.larakelley.woodshed.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.larakelley.woodshed.data.TuneDraft
import com.larakelley.woodshed.data.seed.SeedValidator
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.ui.jsonSaver

private val METERS = listOf("4/4", "3/4", "2/4", "5/4", "6/4", "6/8", "12/8")

/**
 * Adds a tune, or edits one: in place for the user's own tunes, as edits laid over the original for seeded ones.
 * [tune] is null when adding.
 */
@Composable
fun TuneEditor(tune: TuneRow?, onSave: (TuneDraft) -> Unit, onDelete: () -> Unit, onReset: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
  BackHandler(onBack = onBack)
  var draft by rememberSaveable(tune?.slug, stateSaver = jsonSaver()) { mutableStateOf(tune?.draft ?: TuneDraft()) }
  var tried by remember { mutableStateOf(false) }
  var confirm by rememberSaveable { mutableStateOf<String?>(null) }
  val problems = draft.problems()
  val callie = LocalCallie.current

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BackButton("Cancel", onBack)
    Text(if (tune == null) "Add a tune" else "Edit ${tune.title}", style = MaterialTheme.typography.headlineMedium)
    if (tune != null && !tune.userCreated) {
      Helper("Your changes are kept beside the original, so updates to the tune list never overwrite them. You can reset to the original any time.")
    }

    Field("Title", draft.title, "editor-title", capitalize = true) { draft = draft.copy(title = it) }
    Field("Composer", draft.composer, "editor-composer", capitalize = true) { draft = draft.copy(composer = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Field("Year", draft.year, "editor-year", number = true, modifier = Modifier.weight(1f)) { draft = draft.copy(year = it) }
      Field("Bars", draft.bars, "editor-bars", number = true, modifier = Modifier.weight(1f)) { draft = draft.copy(bars = it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Field("Form, like AABA", draft.form, "editor-form", modifier = Modifier.weight(1f)) { draft = draft.copy(form = it) }
      Field("Style, like Swing", draft.style, "editor-style", capitalize = true, modifier = Modifier.weight(1f)) { draft = draft.copy(style = it) }
    }

    SectionLabel("Key, in concert pitch")
    PillChoice(listOf<Pair<String, String?>>("Unknown" to null) + Key.MAJOR_SPELLING.map { it to it }, draft.keyName, { draft = draft.copy(keyName = it) }, tagPrefix = "editor-key")
    PillChoice(Tonality.entries.map { it.label to it }, draft.tonality ?: Tonality.MAJOR, { draft = draft.copy(tonality = it) }, tagPrefix = "editor-tonality")
    SectionLabel("Meter")
    PillChoice(listOf<Pair<String, String?>>("Unknown" to null) + METERS.map { it to it }, draft.timeSignature, { draft = draft.copy(timeSignature = it) }, tagPrefix = "editor-meter")
    SectionLabel("Collection")
    PillChoice(SeedValidator.COLLECTIONS.map { it to it }, draft.collection, { draft = draft.copy(collection = it) }, tagPrefix = "editor-collection")

    if (tried && problems.isNotEmpty()) {
      problems.forEach { Text(it, color = callie.danger, modifier = Modifier.testTag("editor-problem")) }
    }
    StickerButton(
      onClick = {
        tried = true
        if (problems.isEmpty()) onSave(draft)
      },
      modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp).testTag("editor-save"),
    ) { Text(if (tune == null) "Add tune" else "Save", style = MaterialTheme.typography.titleMedium) }

    when {
      tune?.userCreated == true ->
        TextButton(onClick = { confirm = "delete" }, modifier = Modifier.testTag("editor-delete")) { Text("Delete this tune", color = callie.danger) }
      tune?.edited == true ->
        TextButton(onClick = { confirm = "reset" }, modifier = Modifier.testTag("editor-reset")) { Text("Reset to the original", color = callie.danger) }
    }
  }

  confirm?.let { what ->
    AlertDialog(
      onDismissRequest = { confirm = null },
      title = { Text(if (what == "delete") "Delete ${tune?.title}?" else "Reset ${tune?.title}?") },
      text = {
        Text(
          if (what == "delete") "It leaves your tunes and practice. Its review history is kept."
          else "Your edits are dropped and the tune shows its original details again."
        )
      },
      confirmButton = {
        TextButton(onClick = { confirm = null; if (what == "delete") onDelete() else onReset() }, modifier = Modifier.testTag("editor-confirm")) {
          Text(if (what == "delete") "Delete" else "Reset", color = callie.danger)
        }
      },
      dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun Field(
  label: String,
  value: String,
  tag: String,
  modifier: Modifier = Modifier.fillMaxWidth(),
  number: Boolean = false,
  capitalize: Boolean = false,
  onChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(
      keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
      capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
    ),
    modifier = modifier.testTag(tag),
  )
}
