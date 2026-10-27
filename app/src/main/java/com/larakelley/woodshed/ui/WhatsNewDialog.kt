package com.larakelley.woodshed.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.domain.ChangelogBlock
import com.larakelley.woodshed.domain.ChangelogRelease
import com.larakelley.woodshed.ui.settings.CHANGELOG
import org.greylocklabs.ui.LocalColors

/** Shown once after an update: the changelog sections since the version last opened, newest first. */
@Composable
fun WhatsNewDialog(releases: List<ChangelogRelease>, onDismiss: () -> Unit) {
  val uris = LocalUriHandler.current
  val accent = LocalColors.accent
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (releases.size == 1) "What's new in ${releases.single().version}" else "What's new") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()).testTag("whats-new")) {
        releases.forEach { release ->
          if (releases.size > 1) Text(release.version, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
          release.blocks.forEach { block ->
            when (block) {
              is ChangelogBlock.Heading -> Text(block.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))

              is ChangelogBlock.Paragraph -> Text(
                inlineMarkdown(block.text, accent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
              )

              is ChangelogBlock.Bullet -> Row(Modifier.padding(top = 4.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                Text(inlineMarkdown(block.text, accent), style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("whats-new-done")) { Text("Got it") } },
    dismissButton = {
      TextButton(onClick = {
        uris.openUri(CHANGELOG)
        onDismiss()
      }) { Text("Full changelog") }
    },
  )
}

private val INLINE = Regex("""\*\*(.+?)\*\*|\[(.+?)]\((.+?)\)""")

/** Renders the changelog's inline Markdown: `**bold**` and `[text](url)` links. */
fun inlineMarkdown(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
  var at = 0
  for (match in INLINE.findAll(text)) {
    append(text.substring(at, match.range.first))
    val (bold, label, url) = match.destructured
    if (bold.isNotEmpty()) {
      withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
    } else {
      withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold)))) { append(label) }
    }
    at = match.range.last + 1
  }
  append(text.substring(at))
}
