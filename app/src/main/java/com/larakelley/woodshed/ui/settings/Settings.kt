package com.larakelley.woodshed.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.BackupException
import com.larakelley.woodshed.data.BackupService
import com.larakelley.woodshed.data.ThemeMode
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.InstrumentGroup
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.PillChoice
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.SwitchRow
import com.larakelley.woodshed.theme.neutralSticker
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUi(
  val instrument: Instrument? = null,
  val myInstruments: Set<Instrument> = emptySet(),
  val transposition: Transposition = Transposition.C,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val lastBackupAt: Long? = null,
  /** The outcome of the last export or restore, shown until the next one. */
  val message: String? = null,
  val version: String = "",
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
    ) { (i, mine, t), m, last, msg ->
      SettingsUi(i, mine, t, m, last, msg, version)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi(version = version))

  fun setInstrument(i: Instrument) = repository.setInstrument(i)

  fun setTransposition(t: Transposition) = repository.setTransposition(t)

  /** Adds or removes an instrument from the ones the user plays. */
  fun toggleMyInstrument(i: Instrument, plays: Boolean) {
    val mine = repository.myInstruments.value
    repository.setMyInstruments(if (plays) mine + i else mine - i)
  }

  fun setThemeMode(m: ThemeMode) = repository.setThemeMode(m)

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
)

enum class SettingsTab(val label: String) {
  GENERAL("General"),
  INSTRUMENTS("Instruments"),
  BACKUP("Backup"),
  ABOUT("About"),
}

const val KO_FI = "https://ko-fi.com/sayonara_lara"
const val AUTHOR_SITE = "https://larakelley.com"
const val SOURCE = "https://github.com/lararosekelley/woodshed"

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
  }
}

/** Settings content other than full-width rows sits on the page's 16dp margins. */
@Composable
private fun Padded(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.padding(horizontal = 16.dp), content = content)

@Composable
private fun InstrumentsTab(ui: SettingsUi, actions: SettingsActions) {
  val offered = Instrument.entries.filter { ui.myInstruments.isEmpty() || it in ui.myInstruments }
  Padded {
      SectionLabel("Playing now")
      if (offered.isNotEmpty()) {
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

  Padded {
    SectionLabel("Backup file")
    Text(
      "Save your tunes, comfort, known keys, review history and settings to a file. Choose Google Drive, Dropbox or " +
        "a folder on this phone when the picker opens, then restore from that file on any device.",
      style = MaterialTheme.typography.bodyMedium,
    )
    Helper(
      ui.lastBackupAt?.let { "Last saved ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" } ?: "Not backed up from this phone yet",
      Modifier.padding(vertical = 8.dp).testTag("last-backup"),
    )
    StickerButton(onClick = { saveFile.launch("woodshed-backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("export")) {
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
  }

  if (confirmRestore) {
    AlertDialog(
      onDismissRequest = { confirmRestore = false },
      title = { Text("Restore from a backup?") },
      text = { Text("Your tunes, comfort, known keys and review history on this phone are replaced by the backup's.") },
      confirmButton = {
        TextButton(onClick = { confirmRestore = false; openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.testTag("confirm-restore")) {
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
    SectionLabel("Support")
    Helper("Woodshed is free. If it helps your practice, you can buy me a coffee.")
    StickerButton(onClick = { uris.openUri(KO_FI) }, modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp).testTag("donate")) {
      Text("\u2615 Donate on Ko-fi")
    }
    SectionLabel("Credits")
    Helper(
      "Tune data draws on standardrepertoire.com by David Miller, jazzstandards.com, Wikipedia, iReal Pro charts and the " +
        "Jazz Chord Progressions Corpus. The Nunito and Fraunces typefaces are used under the SIL Open Font License."
    )
  }
}
