package com.larakelley.woodshed.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.data.Playlist
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.EmptyState
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.neutralSticker

/** Ticks the playlists a tune is in; ticking or unticking adds or removes it straight away. */
@Composable
fun PlaylistPicker(tune: TuneRow, playlists: List<Playlist>, actions: PlaylistActions, onDismiss: () -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Playlists for ${tune.title}") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        playlists.forEach { p ->
          val inList = tune.id in p.tuneIds
          Row(
            Modifier.fillMaxWidth().toggleable(value = inList, role = Role.Checkbox) { on ->
              if (on) actions.add(p.id, tune.id) else actions.remove(p.id, tune.id)
            }
              .testTag("pick-playlist-${p.name}"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = inList, onCheckedChange = null)
            Text(p.name, modifier = Modifier.padding(start = 8.dp))
          }
        }
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("New playlist") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("new-playlist-name"),
        )
        TextButton(
          onClick = {
            actions.create(name, tune.id) {}
            name = ""
          },
          enabled = name.isNotBlank(),
          modifier = Modifier.testTag("create-playlist"),
        ) { Text("Create and add") }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("playlists-done")) { Text("Done") } },
  )
}

/** Every playlist with its size, and a way to start a new one. */
@Composable
fun PlaylistsScreen(playlists: List<Playlist>, actions: PlaylistActions, onOpen: (Long) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
  BackHandler(onBack = onBack)
  var naming by rememberSaveable { mutableStateOf(false) }
  Column(modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      BackButton("Back to tunes", onBack)
      Spacer(Modifier.weight(1f))
      StickerButton(onClick = {
        naming = true
      }, modifier = Modifier.height(40.dp).testTag("new-playlist"), contentPadding = PaddingValues(horizontal = 14.dp)) {
        Text("+ New playlist", style = MaterialTheme.typography.labelLarge)
      }
    }
    Text("Playlists", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 16.dp))
    if (playlists.isEmpty()) {
      EmptyState(
        "📋",
        "No playlists yet",
        "Gather tunes for a gig, a jam or a theme. Add tunes from their pages, or start a playlist here.",
        action = "Start a playlist",
        onAction = { naming = true },
        scrolls = true,
      )
    }
    LazyColumn(Modifier.fillMaxSize()) {
      itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
        Row(Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(16.dp).testTag("playlist-${p.name}"), verticalAlignment = Alignment.CenterVertically) {
          Text("📋 ${p.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
          Text(if (p.tuneIds.size == 1) "1 tune" else "${p.tuneIds.size} tunes", color = LocalCallie.current.textMuted)
        }
        HorizontalDivider()
      }
    }
  }
  if (naming) {
    NameDialog("New playlist", "", onDismiss = { naming = false }) { name ->
      actions.create(name, null) { onOpen(it) }
      naming = false
    }
  }
}

/** One playlist: its tunes in order, with moving, removing, renaming and deleting. */
@Composable
fun PlaylistScreen(
  playlist: Playlist,
  tunes: Map<Long, TuneRow>,
  actions: PlaylistActions,
  onOpenTune: (String) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)
  var renaming by rememberSaveable { mutableStateOf(false) }
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  val rows = playlist.tuneIds.mapNotNull { tunes[it] }
  val callie = LocalCallie.current
  Column(modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      BackButton("Playlists", onBack)
      Spacer(Modifier.weight(1f))
      StickerButton(onClick = { renaming = true }, sticker = neutralSticker, modifier = Modifier.height(40.dp).testTag("rename-playlist")) {
        Text("✏️ Rename", style = MaterialTheme.typography.labelLarge)
      }
    }
    Text(playlist.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 16.dp))
    if (rows.isEmpty()) {
      EmptyState("📋", "This playlist is empty", "Open a tune and use \"Add to playlist\" to put it here.", scrolls = true)
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
      itemsIndexed(rows, key = { _, t -> t.id }) { i, tune ->
        Row(
          Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp).testTag("entry-$i"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = callie.textMuted, modifier = Modifier.padding(end = 12.dp))
          Column(Modifier.weight(1f).clickable { onOpenTune(tune.slug) }) {
            Text(tune.title, style = MaterialTheme.typography.titleMedium)
            Text(tune.key, style = MaterialTheme.typography.bodySmall, color = callie.textMuted)
          }
          TextButton(onClick = {
            actions.move(playlist.id, tune.id, rows[i - 1].id)
          }, enabled = i > 0, modifier = Modifier.size(44.dp).testTag("up-$i"), contentPadding = PaddingValues(0.dp)) { Text("▲") }
          TextButton(onClick = {
            actions.move(playlist.id, tune.id, rows[i + 1].id)
          }, enabled = i < rows.lastIndex, modifier = Modifier.size(44.dp).testTag("down-$i"), contentPadding = PaddingValues(0.dp)) { Text("▼") }
          TextButton(onClick = {
            actions.remove(playlist.id, tune.id)
          }, modifier = Modifier.size(44.dp).testTag("remove-$i"), contentPadding = PaddingValues(0.dp)) { Text("✕") }
        }
        HorizontalDivider()
      }
      item {
        TextButton(onClick = {
          confirmDelete = true
        }, modifier = Modifier.padding(16.dp).testTag("delete-playlist")) { Text("Delete this playlist", color = callie.danger) }
      }
    }
  }
  if (renaming) {
    NameDialog("Rename playlist", playlist.name, onDismiss = { renaming = false }) {
      actions.rename(playlist.id, it)
      renaming = false
    }
  }
  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Delete ${playlist.name}?") },
      text = { Text("The playlist goes; its tunes stay in your library.") },
      confirmButton = {
        TextButton(onClick = {
          confirmDelete = false
          actions.delete(playlist.id)
          onBack()
        }, modifier = Modifier.testTag("confirm-delete-playlist")) {
          Text("Delete", color = callie.danger)
        }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
  var name by rememberSaveable { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.testTag("playlist-name"))
    },
    confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("save-playlist-name")) { Text("Save") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
