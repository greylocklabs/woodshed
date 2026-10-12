package com.larakelley.woodshed.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larakelley.woodshed.data.BackupException
import com.larakelley.woodshed.data.BackupService
import com.larakelley.woodshed.data.ThemeMode
import com.larakelley.woodshed.data.WoodshedRepository
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.StickerButton
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
  val transposition: Transposition = Transposition.C,
  val family: Family? = null,
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
    combine(repository.transposition, repository.family, repository.themeMode, repository.lastBackupAt, message) { t, f, m, last, msg ->
      SettingsUi(t, f, m, last, msg, version)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi(version = version))

  fun setTransposition(t: Transposition) = repository.setTransposition(t)

  fun setFamily(f: Family?) = repository.setFamily(f)

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
  val setTransposition: (Transposition) -> Unit = {},
  val setFamily: (Family?) -> Unit = {},
  val setThemeMode: (ThemeMode) -> Unit = {},
  val export: (Uri, ContentResolver) -> Unit = { _, _ -> },
  val restore: (Uri, ContentResolver) -> Unit = { _, _ -> },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(ui: SettingsUi, actions: SettingsActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
  BackHandler(onBack = onBack)
  val resolver = LocalContext.current.contentResolver
  val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    uri?.let { actions.export(it, resolver) }
  }
  val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { actions.restore(it, resolver) } }
  var confirmRestore by rememberSaveable { mutableStateOf(false) }

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BackButton("Back", onBack)
    Text("Settings", style = MaterialTheme.typography.headlineMedium)

    Section("🎷 Instrument")
    Text("Keys are written for this transposition.", style = MaterialTheme.typography.bodySmall)
    Transposition.entries.forEach { t ->
      Row(
        Modifier.fillMaxWidth().clickable { actions.setTransposition(t) }.testTag("transposition-${t.name}"),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(selected = ui.transposition == t, onClick = { actions.setTransposition(t) })
        Column {
          Text(t.name, style = MaterialTheme.typography.titleMedium)
          Text(t.examples, style = MaterialTheme.typography.bodySmall)
        }
      }
    }
    Text("Show exercises for", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(selected = ui.family == null, onClick = { actions.setFamily(null) }, label = { Text("All horns") })
      Family.entries.forEach { f ->
        FilterChip(selected = ui.family == f, onClick = { actions.setFamily(f) }, label = { Text(f.displayName) }, modifier = Modifier.testTag("family-${f.name}"))
      }
    }

    Section("🎨 Appearance")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ThemeMode.entries.forEach { m ->
        FilterChip(selected = ui.themeMode == m, onClick = { actions.setThemeMode(m) }, label = { Text(m.label) }, modifier = Modifier.testTag("theme-${m.name}"))
      }
    }

    Section("💾 Backup")
    Text(
      "Save your tunes, comfort, known keys, review history and settings to a file. Choose Google Drive, Dropbox or " +
        "a folder on this phone when the picker opens, then restore from that file on any device.",
      style = MaterialTheme.typography.bodyMedium,
    )
    Text(
      ui.lastBackupAt?.let { "Last saved ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" } ?: "Not backed up from this phone yet",
      style = MaterialTheme.typography.bodySmall,
      color = LocalCallie.current.textMuted,
      modifier = Modifier.testTag("last-backup"),
    )
    StickerButton(onClick = { saveFile.launch("woodshed-backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("export")) {
      Text("Save a backup")
    }
    OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth().testTag("restore")) { Text("Restore from a backup") }
    ui.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("backup-message")) }
    Text(
      "Android also backs Woodshed up to your Google account with the rest of the phone, when device backup is on.",
      style = MaterialTheme.typography.bodySmall,
      color = LocalCallie.current.textMuted,
    )

    Section("🪵 About")
    Text("Woodshed ${ui.version}", style = MaterialTheme.typography.bodyMedium)
    Text(
      "Tune data draws on standardrepertoire.com by David Miller, jazzstandards.com, Wikipedia, iReal Pro charts and the " +
        "Jazz Chord Progressions Corpus. The Nunito and Fraunces typefaces are used under the SIL Open Font License.",
      style = MaterialTheme.typography.bodySmall,
      color = LocalCallie.current.textMuted,
    )
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
private fun Section(title: String) {
  HorizontalDivider(Modifier.padding(top = 8.dp))
  Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
}
