package com.larakelley.woodshed.ui.report

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.data.FiledReport
import com.larakelley.woodshed.data.HttpReportSender
import com.larakelley.woodshed.data.ReportSender
import com.larakelley.woodshed.domain.Report
import com.larakelley.woodshed.domain.ReportSubject
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.ui.jsonSaver
import kotlinx.coroutines.launch

/** The installed version, shown in reports. Provided once at the top of the app. */
val LocalAppVersion = compositionLocalOf { "" }

/** "🚩 Report a problem", which opens [ReportDialog] for one item. */
@Composable
fun ReportButton(
  subject: ReportSubject,
  modifier: Modifier = Modifier,
  item: String? = null,
  id: String? = null,
  details: List<Pair<String, String>> = emptyList(),
  label: String = "🚩 Report a problem",
) {
  var open by rememberSaveable { mutableStateOf(false) }
  TextButton(onClick = { open = true }, modifier = modifier.testTag("report")) { Text(label, color = LocalCallie.current.textMuted) }
  if (open) ReportDialog(subject, item, id, details, onDismiss = { open = false })
}

/** How reports leave the app. Tests provide their own. */
val LocalReportSender = staticCompositionLocalOf<ReportSender> { HttpReportSender() }

/**
 * Pick what is wrong, optionally say more, then send it to be filed as a GitHub issue, or open a prefilled issue
 * on GitHub instead.
 */
@Composable
fun ReportDialog(subject: ReportSubject, item: String?, id: String?, details: List<Pair<String, String>>, onDismiss: () -> Unit) {
  val uris = LocalUriHandler.current
  val sender = LocalReportSender.current
  val version = LocalAppVersion.current
  val android = Build.VERSION.RELEASE ?: "unknown"
  val scope = rememberCoroutineScope()
  var problem by rememberSaveable { mutableStateOf<String?>(null) }
  var note by rememberSaveable { mutableStateOf("") }
  var sending by remember { mutableStateOf(false) }
  // Kept across rotation, so the thanks stays up instead of reopening a form that would file the report again.
  var filed by rememberSaveable(stateSaver = jsonSaver()) { mutableStateOf<FiledReport?>(null) }
  var failure by rememberSaveable { mutableStateOf<String?>(null) }
  // A tune or exercise report has the item's details to go on; one about the app has only what the user writes.
  val noteRequired = subject == ReportSubject.APP
  val report = problem?.takeIf { !noteRequired || note.isNotBlank() }?.let { Report(subject, it, item, id, details, note.trim()) }
  val openOnGitHub = {
    report?.let { uris.openUri(it.issueUrl(version, android)) }
    onDismiss()
  }

  filed?.let { issue ->
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Thanks!") },
      text = { Text("Your report is filed as issue #${issue.number}. You can follow it on GitHub.", modifier = Modifier.testTag("report-filed")) },
      confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("report-done")) { Text("Done") } },
      dismissButton = { TextButton(onClick = { uris.openUri(issue.url) }) { Text("View it ↗") } },
    )
    return
  }

  AlertDialog(
    onDismissRequest = { if (!sending) onDismiss() },
    title = { Text(if (subject == ReportSubject.APP) "Report a bug or idea" else "Report a problem") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        item?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
        subject.problems.forEach { p ->
          Row(
            Modifier.fillMaxWidth().selectable(selected = problem == p, role = Role.RadioButton, onClick = { problem = p }).testTag("problem-$p"),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = problem == p, onClick = null)
            Text(p, modifier = Modifier.padding(start = 8.dp))
          }
        }
        OutlinedTextField(
          value = note,
          onValueChange = { note = it.take(Report.MAX_NOTE) },
          label = { Text(if (noteRequired) "What happened, or what's your idea?" else "What should it be? (optional)") },
          supportingText = { if (note.length > Report.MAX_NOTE * 3 / 4) Text("${note.length} / ${Report.MAX_NOTE}") },
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("report-note"),
        )
        failure?.let { Text(it, color = LocalCallie.current.danger, modifier = Modifier.padding(top = 8.dp).testTag("report-failed")) }
        Helper(
          "Sending files a public GitHub issue with what you chose and wrote, the item's details, and the app and Android versions. " +
            "No account is needed, and nothing about you is included.",
          Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = openOnGitHub, enabled = report != null && !sending, modifier = Modifier.testTag("open-github")) {
          Text("Open it on GitHub instead ↗")
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = report != null && !sending,
        onClick = {
          val toSend = report ?: return@TextButton
          sending = true
          failure = null
          scope.launch {
            sender.send(toSend, version, android).onSuccess { filed = it }.onFailure { failure = it.message ?: "Couldn't send the report." }
            sending = false
          }
        },
        modifier = Modifier.testTag("send-report"),
      ) {
        Text(
          if (sending) {
            "Sending..."
          } else if (failure != null) {
            "Try again"
          } else {
            "Send"
          },
        )
      }
    },
    dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") } },
  )
}
