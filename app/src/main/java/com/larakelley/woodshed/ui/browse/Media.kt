package com.larakelley.woodshed.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.larakelley.woodshed.data.db.TuneMediaEntity
import com.larakelley.woodshed.domain.MediaLink
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.theme.SectionLabel
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.neutralSticker
import com.larakelley.woodshed.ui.media.rememberPlayerView

/** What the tune page's listening section can do. */
data class MediaActions(
  /** Attaches a link; reports false when it is not a web address. */
  val add: (Long, String, String?, (Boolean) -> Unit) -> Unit = { _, _, _, _ -> },
  val remove: (Long) -> Unit = {},
)

/**
 * Recordings and videos on a tune. YouTube and Vimeo play here once tapped, so nothing loads from the network
 * until then; anything else opens in its own app or the browser.
 */
@Composable
fun MediaSection(tuneId: Long, media: List<TuneMediaEntity>, actions: MediaActions) {
  var adding by rememberSaveable { mutableStateOf(false) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    SectionLabel("🎧 Listen", Modifier.weight(1f))
    StickerButton(onClick = { adding = true }, sticker = neutralSticker, modifier = Modifier.height(36.dp).testTag("add-media")) {
      Text("+ Add a link", style = MaterialTheme.typography.labelLarge)
    }
  }
  if (media.isEmpty()) Helper("Add a recording or video: YouTube and Vimeo play here, and Spotify, SoundCloud or any other link opens outside.")
  media.forEach { m -> MediaItem(m, onRemove = { actions.remove(m.id) }) }
  if (adding) AddMediaDialog(onDismiss = { adding = false }) { url, title, done -> actions.add(tuneId, url, title, done) }
}

@Composable
private fun MediaItem(m: TuneMediaEntity, onRemove: () -> Unit) {
  val link = remember(m.url) { MediaLink.parse(m.url) }
  val uris = LocalUriHandler.current
  val callie = LocalCallie.current
  var playing by rememberSaveable(m.id) { mutableStateOf(false) }
  var confirmRemove by rememberSaveable(m.id) { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("media-${m.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(link?.kind?.emoji ?: "🔗", modifier = Modifier.padding(end = 8.dp))
      Column(Modifier.weight(1f)) {
        Text(m.title ?: link?.kind?.label ?: "Link", style = MaterialTheme.typography.titleMedium)
        Text(link?.host ?: m.url, style = MaterialTheme.typography.bodySmall, color = callie.textMuted, maxLines = 1)
      }
      if (link?.kind?.inline == true && !playing) {
        StickerButton(onClick = {
          playing = true
        }, modifier = Modifier.height(36.dp).testTag("play-${m.id}")) { Text("▶ Play", style = MaterialTheme.typography.labelLarge) }
      } else if (link?.kind?.inline != true) {
        StickerButton(onClick = {
          uris.openUri(m.url)
        }, sticker = neutralSticker, modifier = Modifier.height(36.dp).testTag("open-${m.id}")) { Text("Open ↗", style = MaterialTheme.typography.labelLarge) }
      }
      TextButton(onClick = { confirmRemove = true }, modifier = Modifier.size(40.dp).testTag("remove-media-${m.id}")) { Text("✕", color = callie.textMuted) }
    }
    if (playing) link?.embedUrl?.let { InlinePlayer(it) }
    if (playing) TextButton(onClick = { uris.openUri(m.url) }) { Text("Open in ${link?.kind?.label ?: "the browser"} ↗") }
  }
  if (confirmRemove) {
    AlertDialog(
      onDismissRequest = { confirmRemove = false },
      title = { Text("Remove this link?") },
      text = { Text(m.title ?: m.url) },
      confirmButton = {
        TextButton(onClick = {
          confirmRemove = false
          onRemove()
        }, modifier = Modifier.testTag("confirm-remove-media")) { Text("Remove", color = callie.danger) }
      },
      dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun InlinePlayer(embedUrl: String) {
  val webView = rememberPlayerView(embedUrl)
  AndroidView(
    factory = { webView },
    // No clip or background: see PracticePlayer.
    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).testTag("player"),
  )
}

@Composable
private fun AddMediaDialog(onDismiss: () -> Unit, onAdd: (String, String?, (Boolean) -> Unit) -> Unit) {
  var url by rememberSaveable { mutableStateOf("") }
  var title by rememberSaveable { mutableStateOf("") }
  var error by rememberSaveable { mutableStateOf(false) }
  val parsed = MediaLink.parse(url)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add a recording or video") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = url, onValueChange = {
          url = it
          error = false
        }, label = { Text("Link") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("media-url"))
        OutlinedTextField(value = title, onValueChange = {
          title = it
        }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("media-title"))
        when {
          error -> Text("That doesn't look like a web address.", color = LocalCallie.current.danger)
          parsed != null -> Helper(if (parsed.kind.inline) "${parsed.kind.label}: plays here." else "${parsed.kind.label}: opens outside the app.")
          else -> Spacer(Modifier.height(0.dp))
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onAdd(url, title) { ok -> if (ok) onDismiss() else error = true } },
        enabled = url.isNotBlank(),
        modifier = Modifier.testTag("save-media"),
      ) { Text("Add") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
