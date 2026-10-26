package com.larakelley.woodshed.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.BackupException
import com.larakelley.woodshed.data.BackupService
import com.larakelley.woodshed.data.ResetPart
import com.larakelley.woodshed.data.ThemeMode
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.InstrumentGroup
import com.larakelley.woodshed.domain.ReportSubject
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.theme.AppIcons
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.SwitchRow
import com.larakelley.woodshed.theme.neutralSticker
import com.larakelley.woodshed.ui.browse.DEFAULT_TABS
import com.larakelley.woodshed.ui.browse.DropdownChip
import com.larakelley.woodshed.ui.browse.TAB_EMOJI
import com.larakelley.woodshed.ui.browse.moveTab
import com.larakelley.woodshed.ui.browse.orderTabs
import com.larakelley.woodshed.ui.report.ReportButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import kotlin.math.roundToInt

data class SettingsUi(
  val instrument: Instrument? = null,
  val myInstruments: Set<Instrument> = emptySet(),
  val transposition: Transposition = Transposition.C,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val lastBackupAt: Long? = null,
  /** The outcome of the last export or restore, shown until the next one. */
  val message: String? = null,
  val version: String = "",
  /** Every main tab, in the order the user has them. */
  val tabOrder: List<String> = DEFAULT_TABS,
)

class SettingsViewModel(
  private val repository: WoodshedRepository,
  private val backups: BackupService,
  private val version: String,
  private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
  private val message = MutableStateFlow<String?>(null)

  val ui: StateFlow<SettingsUi> =
    combine(
      combine(repository.instrument, repository.myInstruments, repository.transposition, ::Triple),
      repository.themeMode,
      repository.lastBackupAt,
      message,
      repository.tabOrder,
    ) { (i, mine, t), m, last, msg, order ->
      SettingsUi(i, mine, t, m, last, msg, version, orderTabs(DEFAULT_TABS, order))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi(version = version))

  fun setInstrument(i: Instrument) = repository.setInstrument(i)

  fun setTransposition(t: Transposition) = repository.setTransposition(t)

  /** Adds or removes an instrument from the ones the user plays. */
  fun toggleMyInstrument(i: Instrument, plays: Boolean) {
    val mine = repository.myInstruments.value
    repository.setMyInstruments(if (plays) mine + i else mine - i)
  }

  fun setThemeMode(m: ThemeMode) = repository.setThemeMode(m)

  /** Moves a main tab [by] places; negative moves it towards the start. */
  fun moveTab(name: String, by: Int) = repository.setTabOrder(moveTab(orderTabs(DEFAULT_TABS, repository.tabOrder.value), name, by))

  fun resetTabs() = repository.setTabOrder(emptyList())

  fun setTabOrder(order: List<String>) = repository.setTabOrder(order)

  fun export(uri: Uri, resolver: ContentResolver) {
    viewModelScope.launch {
      message.value = try {
        val text = backups.export()
        withContext(Dispatchers.IO) { resolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
        repository.markBackedUp(now())
        "Backup saved."
      } catch (e: Exception) {
        "Could not save the backup: ${e.message ?: e}"
      }
    }
  }

  fun restore(uri: Uri, resolver: ContentResolver) {
    viewModelScope.launch {
      message.value = try {
        val text = withContext(Dispatchers.IO) { resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
        val r = backups.restore(text)
        val skipped = if (r.skipped > 0) " ${r.skipped} entries named tunes this version does not have and were skipped." else ""
        "Restored ${r.tunes} tunes and ${r.reviews} reviews.$skipped"
      } catch (e: BackupException) {
        e.message
      } catch (e: Exception) {
        "Could not read the backup: ${e.message ?: e}"
      }
    }
  }

  /** Clears [parts] of the user's data, leaving the rest; see [BackupService.reset]. */
  fun reset(parts: Set<ResetPart>) {
    if (parts.isEmpty()) return
    viewModelScope.launch {
      message.value = try {
        backups.reset(parts)
        "Cleared ${ResetPart.entries.filter { it in parts }.joinToString(", ") { it.label.lowercase() }}."
      } catch (e: Exception) {
        "Could not clear it: ${e.message ?: e}"
      }
    }
  }

  fun clearMessage() = message.update { null }
}

/** What the settings page can do; the file pickers are handled inside it. */
data class SettingsActions(
  val setInstrument: (Instrument) -> Unit = {},
  val toggleMyInstrument: (Instrument, Boolean) -> Unit = { _, _ -> },
  val setTransposition: (Transposition) -> Unit = {},
  val setThemeMode: (ThemeMode) -> Unit = {},
  val export: (Uri, ContentResolver) -> Unit = { _, _ -> },
  val restore: (Uri, ContentResolver) -> Unit = { _, _ -> },
  val reset: (Set<ResetPart>) -> Unit = {},
  val moveTab: (String, Int) -> Unit = { _, _ -> },
  val resetTabs: () -> Unit = {},
  val setTabOrder: (List<String>) -> Unit = {},
)

enum class SettingsTab(val label: String) {
  GENERAL("General"),
  INSTRUMENTS("Instruments"),
  BACKUP("Backup"),
  ABOUT("About"),
}

/** Past this many instruments, the default is picked from a dropdown rather than a row of pills. */
private const val MAX_INSTRUMENT_PILLS = 3

const val KO_FI = "https://ko-fi.com/sayonara_lara"
const val AUTHOR_SITE = "https://larakelley.com"
const val SOURCE = "https://github.com/greylocklabs/woodshed"
const val WEBSITE = "https://woodshedapp.org"
const val PRIVACY_POLICY = "https://woodshedapp.org/privacy.html"
const val TERMS = "https://woodshedapp.org/terms.html"
const val CHANGELOG = "$SOURCE/blob/main/CHANGELOG.md"
const val PLAY_LISTING = "https://play.google.com/store/apps/details?id=com.larakelley.woodshed"

/** Laid out like Callie's settings: a back sticker and title, pill tabs, then labelled sections. */
@Composable
fun SettingsScreen(ui: SettingsUi, actions: SettingsActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
  BackHandler(onBack = onBack)
  var tab by rememberSaveable { mutableStateOf(SettingsTab.GENERAL) }
  Column(modifier.fillMaxSize()) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
      BackButton("Back", onBack, Modifier.testTag("close-settings"))
      Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
    }
    PillChoice(SettingsTab.entries.map { it.label to it }, tab, { tab = it }, Modifier.padding(horizontal = 12.dp, vertical = 12.dp), tagPrefix = "tab")
    // No side padding here: toggle rows span the width so their pressed highlight does too; other content pads itself.
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
      when (tab) {
        SettingsTab.GENERAL -> GeneralTab(ui, actions)
        SettingsTab.INSTRUMENTS -> InstrumentsTab(ui, actions)
        SettingsTab.BACKUP -> BackupTab(ui, actions)
        SettingsTab.ABOUT -> AboutTab(ui)
      }
    }
  }
}

@Composable
private fun GeneralTab(ui: SettingsUi, actions: SettingsActions) {
  Padded {
    SectionLabel("Appearance")
    PillChoice(ThemeMode.entries.map { it.label to it }, ui.themeMode, actions.setThemeMode, tagPrefix = "theme")
    Helper("System follows the phone's light or dark setting.")
    SectionLabel("Tab order")
    Helper("The order of the tabs along the top of the app. Hold a tab and drag it, or use the arrows.")
  }
  TabOrderList(ui.tabOrder, actions.moveTab, actions.setTabOrder)
  Padded {
    TextButton(onClick = actions.resetTabs, enabled = ui.tabOrder != DEFAULT_TABS, modifier = Modifier.testTag("reset-tabs")) {
      Text("Reset to the default order")
    }
  }
}

/** Settings content other than full-width rows sits on the page's 16dp margins. */
@Composable
private fun Padded(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.padding(horizontal = 16.dp), content = content)

@Composable
private fun InstrumentsTab(ui: SettingsUi, actions: SettingsActions) {
  val offered = Instrument.entries.filter { ui.myInstruments.isEmpty() || it in ui.myInstruments }
  Padded {
    SectionLabel("Default instrument")
    if (offered.size > MAX_INSTRUMENT_PILLS) {
      DropdownChip("Instrument", ui.instrument?.label ?: "Choose", offered.map { it.label to it }) { i -> i?.let(actions.setInstrument) }
    } else if (offered.isNotEmpty()) {
      PillChoice(offered.map { it.label to it }, ui.instrument, { it?.let(actions.setInstrument) }, tagPrefix = "now")
    }
    Helper("Sets the key music is written in and which exercises sessions suggest. You can also change it when you start a session.")
    SectionLabel("Instruments I play")
    Helper("Only these are offered when you start a session.")
  }
  InstrumentGroup.entries.forEach { group ->
    Padded { SectionLabel(group.label) }
    Instrument.entries.filter { it.group == group }.forEach { i ->
      SwitchRow(
        label = i.label,
        checked = i in ui.myInstruments,
        onChange = { actions.toggleMyInstrument(i, it) },
        helper = "Reads in ${i.transposition.name}",
        horizontalPadding = 16.dp,
        modifier = Modifier.testTag("plays-${i.name.lowercase()}"),
      )
    }
  }
}

@Composable
private fun BackupTab(ui: SettingsUi, actions: SettingsActions) {
  val resolver = LocalContext.current.contentResolver
  val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    uri?.let { actions.export(it, resolver) }
  }
  val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { actions.restore(it, resolver) } }
  var confirmRestore by rememberSaveable { mutableStateOf(false) }
  // Kept by name, so the choice survives a rotation.
  var chosen by rememberSaveable { mutableStateOf(emptyList<String>()) }
  val parts = ResetPart.entries.filter { it.name in chosen }.toSet()
  var confirmReset by rememberSaveable { mutableStateOf(false) }
  val callie = LocalCallie.current

  Padded {
    SectionLabel("Backup file")
    Text(
      "Save your tunes, comfort, known keys, review history and settings to a file. Choose Google Drive, Dropbox or " +
        "a folder on this phone when the picker opens, then restore from that file on any device.",
      style = MaterialTheme.typography.bodyMedium,
    )
    Helper(
      ui.lastBackupAt?.let { "Last saved ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" }
        ?: "Not backed up from this phone yet",
      Modifier.padding(vertical = 8.dp).testTag("last-backup"),
    )
    StickerButton(onClick = {
      saveFile.launch("woodshed-backup-${LocalDate.now()}.json")
    }, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("export")) {
      Text("Save a backup")
    }
    StickerButton(
      onClick = { confirmRestore = true },
      sticker = neutralSticker,
      modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp).testTag("restore"),
    ) { Text("Restore from a backup") }
    ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp).testTag("backup-message")) }
    SectionLabel("Automatic")
    Helper("Android also backs Woodshed up to your Google account with the rest of the phone, when device backup is on.")
    SectionLabel("Start fresh")
    Helper("Clear some of your data, or all of it, and keep the rest. The bundled tunes, exercises and solos always stay.")
  }
  SwitchRow(
    label = "Everything",
    checked = parts.size == ResetPart.entries.size,
    onChange = { all -> chosen = if (all) ResetPart.entries.map { it.name } else emptyList() },
    horizontalPadding = 16.dp,
    modifier = Modifier.testTag("reset-everything"),
  )
  ResetPart.entries.forEach { part ->
    SwitchRow(
      label = part.label,
      checked = part in parts,
      onChange = { on -> chosen = if (on) chosen + part.name else chosen - part.name },
      helper = part.description,
      horizontalPadding = 16.dp,
      modifier = Modifier.testTag("reset-${part.name.lowercase()}"),
    )
  }
  Padded {
    if (ResetPart.MATERIAL in parts && !parts.containsAll(setOf(ResetPart.TUNE_PROGRESS, ResetPart.SOLO_PROGRESS))) {
      Helper("Your own tunes and solos take their progress and grades with them.", Modifier.testTag("reset-knock-on"))
    }
    TextButton(
      onClick = { confirmReset = true },
      enabled = parts.isNotEmpty(),
      modifier = Modifier.padding(top = 4.dp).testTag("reset"),
    ) { Text("Clear what's chosen", color = if (parts.isNotEmpty()) callie.danger else callie.textFaint) }
  }

  if (confirmReset) {
    AlertDialog(
      onDismissRequest = { confirmReset = false },
      title = { Text("Clear ${ResetPart.entries.filter { it in parts }.joinToString(", ") { it.label.lowercase() }}?") },
      text = { Text("This can't be undone. Save a backup first to keep a copy you can restore.") },
      confirmButton = {
        TextButton(onClick = {
          confirmReset = false
          actions.reset(parts)
          chosen = emptyList()
        }, modifier = Modifier.testTag("confirm-reset")) { Text("Clear", color = callie.danger) }
      },
      dismissButton = {
        Row {
          TextButton(onClick = { saveFile.launch("woodshed-backup-${LocalDate.now()}.json") }, modifier = Modifier.testTag("reset-backup-first")) {
            Text("Save a backup first")
          }
          TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
        }
      },
    )
  }

  if (confirmRestore) {
    AlertDialog(
      onDismissRequest = { confirmRestore = false },
      title = { Text("Restore from a backup?") },
      text = { Text("Your tunes, comfort, known keys and review history on this phone are replaced by the backup's.") },
      confirmButton = {
        TextButton(onClick = {
          confirmRestore = false
          openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }, modifier = Modifier.testTag("confirm-restore")) {
          Text("Choose file")
        }
      },
      dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun AboutTab(ui: SettingsUi) {
  val uris = LocalUriHandler.current
  val accent = LocalCallie.current.accent
  Padded {
    SectionLabel("Woodshed ${ui.version}")
    Text(
      buildAnnotatedString {
        append("Made with \u2764\uFE0F in Brooklyn by ")
        withLink(LinkAnnotation.Url(AUTHOR_SITE, TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.Bold)))) { append("Lara Kelley") }
        append(".")
      },
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.testTag("made-by"),
    )
    SectionLabel("What's new")
    Text(
      buildAnnotatedString {
        append("See what changed in each release in the ")
        withLink(LinkAnnotation.Url(CHANGELOG, TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.Bold)))) { append("changelog") }
        append(".")
      },
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.testTag("changelog"),
    )
    SectionLabel("Source")
    Text(
      buildAnnotatedString {
        append("Woodshed is open source. ")
        withLink(LinkAnnotation.Url(SOURCE, TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.Bold)))) { append("See the code on GitHub") }
        append(".")
      },
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.testTag("source"),
    )
    SectionLabel("Feedback")
    Helper("Found a wrong key or a broken link? Each tune and exercise page has a report button too.")
    ReportButton(ReportSubject.APP, label = "🚩 Report a bug or idea")
    SectionLabel("Support")
    Helper("Woodshed is free. If it helps your practice, a review on Google Play helps others find it, or you can buy me a coffee.")
    StickerButton(
      onClick = { uris.openUri(PLAY_LISTING) },
      sticker = neutralSticker,
      modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp).testTag("review"),
    ) { Text("\u2B50 Review on Google Play") }
    StickerButton(onClick = { uris.openUri(KO_FI) }, modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp).testTag("donate")) {
      Text("\u2615 Donate on Ko-fi")
    }
    SectionLabel("Privacy")
    Text(
      buildAnnotatedString {
        append("Everything stays on your phone, apart from videos you play and reports you send. Read the ")
        withLink(LinkAnnotation.Url(PRIVACY_POLICY, TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.Bold)))) { append("privacy policy") }
        append(" and ")
        withLink(LinkAnnotation.Url(TERMS, TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.Bold)))) { append("terms") }
        append(" at ")
        withLink(LinkAnnotation.Url(WEBSITE, TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.Bold)))) { append("woodshedapp.org") }
        append(".")
      },
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.testTag("privacy"),
    )
    SectionLabel("Credits")
    Helper(
      "Tune data draws on standardrepertoire.com by David Miller, jazzstandards.com, Wikipedia, iReal Pro charts and the " +
        "Jazz Chord Progressions Corpus. The Nunito and Fraunces typefaces are used under the SIL Open Font License.",
    )
  }
}

private val TAB_ROW_HEIGHT = 52.dp

/**
 * The main tabs in order. Hold a row and drag it to move it; the arrows move a tab one place, and are what screen
 * readers use. A drag is applied when it ends, so the bar along the top changes once.
 */
@Composable
private fun TabOrderList(order: List<String>, onMove: (String, Int) -> Unit, onSet: (List<String>) -> Unit) {
  val callie = LocalCallie.current
  val rowPx = with(LocalDensity.current) { TAB_ROW_HEIGHT.toPx() }
  var items by remember(order) { mutableStateOf(order) }
  var dragging by remember { mutableStateOf<String?>(null) }
  var offset by remember { mutableFloatStateOf(0f) }
  Column(Modifier.testTag("tab-order")) {
    items.forEachIndexed { i, name ->
      key(name) {
        val dragged = name == dragging
        Row(
          Modifier.fillMaxWidth().height(TAB_ROW_HEIGHT).zIndex(if (dragged) 1f else 0f)
            .graphicsLayer { translationY = if (dragged) offset else 0f }
            .background(if (dragged) callie.surfaceAlt else Color.Transparent)
            .pointerInput(name) {
              detectDragGesturesAfterLongPress(
                onDragStart = {
                  dragging = name
                  offset = 0f
                },
                onDrag = { change, amount ->
                  change.consume()
                  offset += amount.y
                  // Past half a row, swap with the neighbour and carry the leftover offset to the new place.
                  val from = items.indexOf(name)
                  val to = (from + (offset / rowPx).roundToInt()).coerceIn(0, items.lastIndex)
                  if (to != from) {
                    items = moveTab(items, name, to - from)
                    offset -= (to - from) * rowPx
                  }
                },
                onDragEnd = {
                  dragging = null
                  offset = 0f
                  if (items != order) onSet(items)
                },
                onDragCancel = {
                  dragging = null
                  offset = 0f
                  items = order
                },
              )
            }
            .padding(start = 16.dp, end = 8.dp)
            .testTag("tab-order-$name"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(AppIcons.DragHandle, contentDescription = null, tint = callie.textMuted, modifier = Modifier.padding(end = 12.dp))
          Text("${TAB_EMOJI[name].orEmpty()} $name", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
          IconButton(onClick = { onMove(name, -1) }, enabled = i > 0, modifier = Modifier.testTag("tab-up-$name")) {
            Icon(AppIcons.ChevronUp, contentDescription = "Move $name earlier")
          }
          IconButton(onClick = { onMove(name, 1) }, enabled = i < items.lastIndex, modifier = Modifier.testTag("tab-down-$name")) {
            Icon(AppIcons.ChevronDown, contentDescription = "Move $name later")
          }
        }
      }
    }
  }
}
