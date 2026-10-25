package com.larakelley.woodshed.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.data.MyChanges
import com.larakelley.woodshed.domain.Bar
import com.larakelley.woodshed.domain.ChordGroup
import com.larakelley.woodshed.domain.ChordQuality
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.ProgressionChord
import com.larakelley.woodshed.domain.bars
import com.larakelley.woodshed.domain.numerals
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.CallieChip
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.neutralSticker

/** Writes changes of one's own: bars of one or two chords each, named in [writtenKey] and kept relative to it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChangesEditor(
  existing: MyChanges?,
  writtenKey: Int,
  onSave: (name: String, bars: List<Bar>) -> Unit,
  onCancel: () -> Unit,
  onDelete: (() -> Unit)? = null,
) {
  val callie = LocalCallie.current
  var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
  // Kept as numerals so it survives being saved and restored with the screen.
  var written by rememberSaveable { mutableStateOf(numerals(existing?.bars ?: bars("I | I | I | I"))) }
  var selected by rememberSaveable { mutableIntStateOf(0) }
  var half by rememberSaveable { mutableIntStateOf(0) }
  var moreChords by rememberSaveable { mutableStateOf(false) }
  var tried by rememberSaveable { mutableStateOf(false) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  val bars = bars(written)
  val bar = bars[selected.coerceIn(bars.indices)]
  val chord = bar.chords[half.coerceIn(bar.chords.indices)]
  fun edit(next: List<Bar>) {
    written = numerals(next)
  }
  fun setChord(next: ProgressionChord) = edit(
    bars.mapIndexed { i, b ->
      if (i ==
        selected
      ) {
        Bar(b.chords.mapIndexed { h, c -> if (h == half) next else c })
      } else {
        b
      }
    },
  )
  fun name(c: ProgressionChord) = c.name(writtenKey)

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BackButton("Cancel", onCancel)
    Text(if (existing == null) "New changes" else "Edit ${existing.name}", style = MaterialTheme.typography.headlineMedium)
    OutlinedTextField(
      value = name,
      onValueChange = { name = it },
      label = { Text("Name") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      modifier = Modifier.fillMaxWidth().testTag("changes-name"),
    )
    Helper("Chords are named in ${Key.MAJOR_SPELLING[writtenKey]}, the Changes tab's key. Your changes play in any key, as the others do.")

    SectionLabel("Bars")
    val shape = RoundedCornerShape(6.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      bars.chunked(4).forEachIndexed { row, four ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          four.forEachIndexed { i, b ->
            val index = row * 4 + i
            val now = index == selected
            Box(
              Modifier.weight(1f).height(44.dp)
                .then(if (now) Modifier.background(MaterialTheme.colorScheme.secondaryContainer, shape) else Modifier.border(1.dp, callie.border, shape))
                .clickable(role = Role.Button) {
                  selected = index
                  half = 0
                }
                .testTag("changes-bar-$index"),
              contentAlignment = Alignment.Center,
            ) {
              Text(b.chords.joinToString(" / ") { name(it) }, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
          }
          repeat(4 - four.size) { Spacer(Modifier.weight(1f)) }
        }
      }
    }
    EditorButton("+ Add a bar", "changes-add-bar") {
      edit(bars.take(selected + 1) + Bar(listOf(bar.chords.last())) + bars.drop(selected + 1))
      selected += 1
      half = 0
    }

    SectionLabel("Bar ${selected + 1}")
    if (bar.chords.size > 1) {
      PillChoice(listOf("First half" to 0, "Second half" to 1), half, { half = it }, tagPrefix = "changes-half")
    }
    // The chords already in these changes come first, since changes mostly reuse a few.
    val used = bars.flatMap { it.chords }.distinct()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      used.forEach { c -> CallieChip(selected = c == chord, onClick = { setChord(c) }, label = { Text(name(c)) }) }
      CallieChip(selected = moreChords, onClick = {
        moreChords = !moreChords
      }, label = { Text("More chords") }, modifier = Modifier.testTag("changes-more-chords"))
    }
    if (moreChords) {
      Helper("Root", Modifier.padding(top = 8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (0..11).forEach { root ->
          CallieChip(
            selected = root == chord.root,
            onClick = { setChord(chord.copy(root = root)) },
            label = { Text(Key.MAJOR_SPELLING[Math.floorMod(writtenKey + root, 12)]) },
            modifier = Modifier.testTag("changes-root-$root"),
          )
        }
      }
      ChordChips(chord.quality, { setChord(chord.copy(quality = it)) }, "changes-quality", ChordGroup.entries.filter { it != ChordGroup.SIMPLE })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
      if (bar.chords.size == 1) {
        EditorButton("Split in two", "changes-split") { edit(bars.mapIndexed { i, b -> if (i == selected) Bar(b.chords + b.chords) else b }) }
      } else {
        EditorButton("Make it one", "changes-join") {
          edit(bars.mapIndexed { i, b -> if (i == selected) Bar(listOf(b.chords[half])) else b })
          half = 0
        }
      }
      EditorButton("Copy", "changes-copy") {
        edit(bars.take(selected + 1) + bar + bars.drop(selected + 1))
        selected += 1
      }
      if (bars.size > 1) {
        EditorButton("Remove", "changes-remove") {
          edit(bars.filterIndexed { i, _ -> i != selected })
          selected = selected.coerceAtMost(bars.size - 2)
          half = 0
        }
      }
    }

    if (tried && name.isBlank()) Text("Give these changes a name.", color = callie.danger, modifier = Modifier.testTag("changes-problem"))
    StickerButton(
      onClick = {
        tried = true
        if (name.isNotBlank()) onSave(name, bars)
      },
      modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp).testTag("changes-save"),
    ) { Text(if (existing == null) "Add changes" else "Save", style = MaterialTheme.typography.titleMedium) }
    if (onDelete != null) {
      TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("changes-delete")) { Text("Delete these changes", color = callie.danger) }
    }
  }

  if (confirmDelete && onDelete != null) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Delete ${existing?.name ?: "these changes"}?") },
      text = { Text("They go from your changes for good.") },
      confirmButton = {
        TextButton(onClick = {
          confirmDelete = false
          onDelete()
        }, modifier = Modifier.testTag("changes-delete-confirm")) { Text("Delete", color = callie.danger) }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun EditorButton(label: String, tag: String, onClick: () -> Unit) {
  StickerButton(
    onClick = onClick,
    sticker = neutralSticker,
    modifier = Modifier.height(40.dp).testTag(tag),
    contentPadding = PaddingValues(horizontal = 14.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelLarge)
  }
}
