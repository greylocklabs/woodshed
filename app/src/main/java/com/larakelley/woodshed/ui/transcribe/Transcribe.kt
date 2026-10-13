package com.larakelley.woodshed.ui.transcribe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.TranscriptionDraft
import com.larakelley.woodshed.data.TranscriptionService
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.data.clock
import com.larakelley.woodshed.data.db.TranscriptionEntity
import com.larakelley.woodshed.data.db.TranscriptionStateEntity
import com.larakelley.woodshed.data.db.TranscriptionStatus
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.MediaLink
import com.larakelley.woodshed.domain.ReportSubject
import com.larakelley.woodshed.domain.Tonality
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.EmptyState
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.neutralSticker
import com.larakelley.woodshed.ui.media.PracticePlayer
import com.larakelley.woodshed.ui.report.ReportButton
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A solo with the user's progress on it, as the tab lists it. */
data class SoloRow(val solo: TranscriptionEntity, val state: TranscriptionStateEntity?) {
  val status: TranscriptionStatus?
    get() = state?.status
}

/** The groups the tab lists solos in, in order. Solos the user has not touched come last. */
fun soloGroups(rows: List<SoloRow>): List<Pair<String, List<SoloRow>>> = listOf(
  "🎧 Transcribing" to rows.filter { it.status == TranscriptionStatus.ACTIVE },
  "💭 Wishlist" to rows.filter { it.status == TranscriptionStatus.WISHLIST },
  "✅ Done" to rows.filter { it.status == TranscriptionStatus.DONE },
  "Solos to try" to rows.filter { it.status == null },
).filter { it.second.isNotEmpty() }.map { (label, list) -> label to list.sortedBy { it.solo.title.lowercase() } }

/** The key a solo is played in: concert, and written for the user's transposition. */
fun soloKey(solo: TranscriptionEntity, transposition: Transposition): String? {
  val key = Key.of(solo.keyName, solo.tonality) ?: return null
  return if (transposition == Transposition.C) "Concert ${key.displayName}" else "${key.writtenFor(transposition).displayName} for ${transposition.name} (concert ${key.displayName})"
}

data class TranscribeUi(val rows: List<SoloRow> = emptyList(), val transposition: Transposition = Transposition.C)

class TranscribeViewModel(repository: WoodshedRepository, private val service: TranscriptionService) : ViewModel() {
  val ui: StateFlow<TranscribeUi> =
    combine(repository.transcriptions, repository.transcriptionStates, repository.transposition) { solos, states, transposition ->
      val byId = states.associateBy { it.transcriptionId }
      TranscribeUi(solos.map { SoloRow(it, byId[it.id]) }, transposition)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranscribeUi())

  val actions = SoloActions(
    wishlist = { launch { service.wishlist(it) } },
    start = { launch { service.start(it) } },
    finish = { launch { service.finish(it) } },
    clear = { launch { service.clear(it) } },
    setNotes = { id, notes -> launch { service.setNotes(id, notes) } },
    save = { id, draft, saved -> viewModelScope.launch { if (id == null) saved(service.add(draft)) else { service.edit(id, draft); saved(id) } } },
    delete = { launch { service.delete(it) } },
  )

  private fun launch(block: suspend () -> Unit) {
    viewModelScope.launch { block() }
  }
}

data class SoloActions(
  val wishlist: (Long) -> Unit = {},
  val start: (Long) -> Unit = {},
  val finish: (Long) -> Unit = {},
  val clear: (Long) -> Unit = {},
  val setNotes: (Long, String) -> Unit = { _, _ -> },
  /** Saves one of the user's own solos: a new one when the id is null. Reports its id. */
  val save: (Long?, TranscriptionDraft, (Long) -> Unit) -> Unit = { _, _, _ -> },
  val delete: (Long) -> Unit = {},
)

private const val NEW_SOLO = -1L

/** The Transcribe tab: solos grouped by progress, each opening to a page with the recording and practice controls. */
@Composable
fun TranscribeScreen(ui: TranscribeUi, actions: SoloActions) {
  var open by rememberSaveable { mutableStateOf<Long?>(null) }
  var editing by rememberSaveable { mutableStateOf<Long?>(null) }
  editing?.let { target ->
    val row = ui.rows.firstOrNull { it.solo.id == target }
    SoloEditor(
      row?.solo,
      onSave = { draft -> actions.save(row?.solo?.id, draft) { id -> open = id; editing = null } },
      onDelete = { row?.let { actions.delete(it.solo.id) }; open = null; editing = null },
      onBack = { editing = null },
    )
    return
  }
  val row = ui.rows.firstOrNull { it.solo.id == open }
  if (row != null) {
    SoloDetail(row, ui.transposition, actions, onBack = { open = null }, onEdit = { editing = row.solo.id })
    return
  }
  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("Solos to learn by ear", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
      StickerButton(onClick = { editing = NEW_SOLO }, modifier = Modifier.height(40.dp).testTag("add-solo"), contentPadding = PaddingValues(horizontal = 14.dp)) {
        Text("+ Add a solo", style = MaterialTheme.typography.labelLarge)
      }
    }
    if (ui.rows.isEmpty()) {
      EmptyState("🎧", "No solos yet", "Add a solo you want to learn by ear: the tune, who plays it, and a link to the recording.", action = "Add a solo", onAction = { editing = NEW_SOLO }, scrolls = true)
      return@Column
    }
    LazyColumn(Modifier.fillMaxSize()) {
      soloGroups(ui.rows).forEach { (label, rows) ->
        item(key = "group-$label") {
          Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        }
        rows.forEach { r ->
          item(key = r.solo.id) {
            Column(Modifier.fillMaxWidth().clickable { open = r.solo.id }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("solo-${r.solo.slug}")) {
              Text(r.solo.title, style = MaterialTheme.typography.titleMedium)
              Text(listOfNotNull("${r.solo.soloist}, ${r.solo.instrument.lowercase()}", r.solo.context).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
          }
        }
      }
    }
  }
}

@Composable
private fun SoloDetail(row: SoloRow, transposition: Transposition, actions: SoloActions, onBack: () -> Unit, onEdit: () -> Unit) {
  BackHandler(onBack = onBack)
  val solo = row.solo
  val id = solo.id
  val link = remember(solo.url) { MediaLink.parse(solo.url) }
  val uris = LocalUriHandler.current
  val callie = LocalCallie.current
  var notes by remember(id) { mutableStateOf(row.state?.notes.orEmpty()) }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      BackButton("Solos", onBack)
      Spacer(Modifier.weight(1f))
      if (solo.userCreated) {
        StickerButton(onClick = onEdit, sticker = neutralSticker, modifier = Modifier.height(40.dp).testTag("edit-solo")) { Text("✏️ Edit", style = MaterialTheme.typography.labelLarge) }
      }
    }
    Text(solo.title, style = MaterialTheme.typography.headlineMedium)
    Text("${solo.soloist}, ${solo.instrument.lowercase()}", style = MaterialTheme.typography.titleMedium)
    solo.context?.let { Text(it, color = callie.textMuted) }
    soloKey(solo, transposition)?.let { Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("solo-key")) }
    if (solo.startSeconds != null) {
      Text("Solo ${clock(solo.startSeconds)}" + (solo.endSeconds?.let { " to ${clock(it)}" } ?: ""), color = callie.textMuted, modifier = Modifier.testTag("solo-times"))
    }

    val pad = TranscriptionService.PADDING_SECONDS
    val embed = link?.embed(solo.startSeconds?.let { (it - pad).coerceAtLeast(0) }, solo.endSeconds?.let { it + pad })
    if (embed != null) {
      PracticePlayer(embed, solo.startSeconds)
    } else {
      OutlinedButton(onClick = { uris.openUri(solo.url) }, modifier = Modifier.testTag("open-solo")) { Text("Open the recording ↗") }
    }

    HorizontalDivider()
    Text(
      when (row.status) {
        TranscriptionStatus.ACTIVE -> "Transcribing now. It comes up first in practice sessions."
        TranscriptionStatus.WISHLIST -> "On your wishlist"
        TranscriptionStatus.DONE -> "Done"
        null -> "Not on your list"
      },
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.testTag("solo-status"),
    )
    when (row.status) {
      null -> {
        StickerButton(onClick = { actions.start(id) }, modifier = Modifier.fillMaxWidth().testTag("start-solo")) { Text("🎧 Start transcribing") }
        OutlinedButton(onClick = { actions.wishlist(id) }, modifier = Modifier.fillMaxWidth().testTag("wishlist-solo")) { Text("💭 Add to wishlist") }
      }
      TranscriptionStatus.WISHLIST -> StickerButton(onClick = { actions.start(id) }, modifier = Modifier.fillMaxWidth().testTag("start-solo")) { Text("🎧 Start transcribing") }
      TranscriptionStatus.ACTIVE -> StickerButton(onClick = { actions.finish(id) }, modifier = Modifier.fillMaxWidth().testTag("finish-solo")) { Text("✅ Mark as done") }
      TranscriptionStatus.DONE -> OutlinedButton(onClick = { actions.start(id) }, modifier = Modifier.fillMaxWidth().testTag("restart-solo")) { Text("Work on it again") }
    }
    if (row.status != null) TextButton(onClick = { actions.clear(id) }, modifier = Modifier.testTag("clear-solo")) { Text("Remove from my list", color = callie.textMuted) }

    SectionLabel("Notes")
    OutlinedTextField(
      value = notes,
      onValueChange = { notes = it },
      placeholder = { Text("Phrases done, tricky bars, fingerings...") },
      minLines = 3,
      modifier = Modifier.fillMaxWidth().testTag("solo-notes"),
    )
    if (notes != row.state?.notes.orEmpty()) {
      StickerButton(onClick = { actions.setNotes(id, notes) }, sticker = neutralSticker, modifier = Modifier.height(40.dp).testTag("save-notes")) { Text("Save notes") }
    }

    if (solo.performers.isNotEmpty()) {
      SectionLabel("Performers")
      solo.performers.forEach { p -> Text(if (p.instrument.isBlank()) p.name else "${p.name}, ${p.instrument.lowercase()}") }
    }
    ReportButton(
      ReportSubject.SOLO,
      item = "${solo.title} (${solo.soloist})",
      id = solo.slug,
      details = listOfNotNull(
        "Recording" to solo.url,
        solo.startSeconds?.let { "Solo" to clock(it) + (solo.endSeconds?.let { e -> " to ${clock(e)}" } ?: "") },
        Key.of(solo.keyName, solo.tonality)?.let { "Key" to "concert ${it.displayName}" },
        solo.context?.let { "Context" to it },
      ),
    )
  }
}

@Composable
private fun SoloEditor(solo: TranscriptionEntity?, onSave: (TranscriptionDraft) -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  var draft by remember(solo?.id) { mutableStateOf(solo?.let(TranscriptionDraft::of) ?: TranscriptionDraft()) }
  var tried by remember { mutableStateOf(false) }
  var confirm by rememberSaveable { mutableStateOf(false) }
  val callie = LocalCallie.current
  val problems = draft.problems()
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BackButton("Cancel", onBack)
    Text(if (solo == null) "Add a solo" else "Edit solo", style = MaterialTheme.typography.headlineMedium)
    Field("Tune", draft.title, "solo-title") { draft = draft.copy(title = it) }
    Field("Soloist", draft.soloist, "solo-soloist") { draft = draft.copy(soloist = it) }
    Field("Their instrument", draft.instrument, "solo-instrument") { draft = draft.copy(instrument = it) }
    Field("Where it was played (album, show)", draft.context, "solo-context") { draft = draft.copy(context = it) }
    Field("Link to the recording", draft.url, "solo-url", capitalize = false) { draft = draft.copy(url = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Field("Solo starts (2:00)", draft.start, "solo-start", capitalize = false, modifier = Modifier.weight(1f)) { draft = draft.copy(start = it) }
      Field("Solo ends (5:29)", draft.end, "solo-end", capitalize = false, modifier = Modifier.weight(1f)) { draft = draft.copy(end = it) }
    }
    SectionLabel("Key played in, concert")
    PillChoice(listOf<Pair<String, String?>>("Unknown" to null) + Key.MAJOR_SPELLING.map { it to it }, draft.keyName, { draft = draft.copy(keyName = it) }, tagPrefix = "solo-key")
    PillChoice(Tonality.entries.map { it.label to it }, draft.tonality ?: Tonality.MAJOR, { draft = draft.copy(tonality = it) }, tagPrefix = "solo-tonality")
    OutlinedTextField(
      value = draft.performers,
      onValueChange = { draft = draft.copy(performers = it) },
      label = { Text("Performers, one per line: Name - Instrument") },
      minLines = 3,
      modifier = Modifier.fillMaxWidth().testTag("solo-performers"),
    )
    Helper("Playback starts ${TranscriptionService.PADDING_SECONDS} seconds before the solo and stops ${TranscriptionService.PADDING_SECONDS} after it.")
    if (tried) problems.forEach { Text(it, color = callie.danger) }
    StickerButton(onClick = { tried = true; if (problems.isEmpty()) onSave(draft) }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("solo-save")) {
      Text(if (solo == null) "Add solo" else "Save")
    }
    if (solo?.userCreated == true) TextButton(onClick = { confirm = true }, modifier = Modifier.testTag("solo-delete")) { Text("Delete this solo", color = callie.danger) }
  }
  if (confirm) {
    AlertDialog(
      onDismissRequest = { confirm = false },
      title = { Text("Delete this solo?") },
      text = { Text("Your progress and notes on it go too. Its practice history is kept.") },
      confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Delete", color = callie.danger) } },
      dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun Field(label: String, value: String, tag: String, capitalize: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None),
    modifier = modifier.testTag(tag),
  )
}
