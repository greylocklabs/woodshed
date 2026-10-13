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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.domain.Report
import com.larakelley.woodshed.domain.ReportSubject
import com.larakelley.woodshed.theme.Helper
import com.larakelley.woodshed.theme.LocalCallie

/** The installed version, shown in reports. Provided once at the top of the app. */
val LocalAppVersion = compositionLocalOf { "" }

/** "🚩 Report a problem", which opens [ReportDialog] for one item. */
@Composable
fun ReportButton(
  subject: ReportSubject,
  item: String? = null,
  id: String? = null,
  details: List<Pair<String, String>> = emptyList(),
  label: String = "🚩 Report a problem",
  modifier: Modifier = Modifier,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  TextButton(onClick = { open = true }, modifier = modifier.testTag("report")) { Text(label, color = LocalCallie.current.textMuted) }
  if (open) ReportDialog(subject, item, id, details, onDismiss = { open = false })
}

/** Pick what is wrong, optionally say more, then open a prefilled GitHub issue in the browser. */
@Composable
fun ReportDialog(subject: ReportSubject, item: String?, id: String?, details: List<Pair<String, String>>, onDismiss: () -> Unit) {
  val uris = LocalUriHandler.current
  val version = LocalAppVersion.current
  var problem by rememberSaveable { mutableStateOf<String?>(null) }
  var note by rememberSaveable { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
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
          label = { Text("What should it be? (optional)") },
          supportingText = { if (note.length > Report.MAX_NOTE * 3 / 4) Text("${note.length} / ${Report.MAX_NOTE}") },
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("report-note"),
        )
        Helper(
          "Opens a new GitHub issue with the details filled in. You need a GitHub account, and nothing is sent until you submit it.",
          Modifier.padding(top = 8.dp),
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = problem != null,
        onClick = {
          val report = Report(subject, problem!!, item, id, details, note.trim())
          uris.openUri(report.issueUrl(version, Build.VERSION.RELEASE ?: "unknown"))
          onDismiss()
        },
        modifier = Modifier.testTag("open-github"),
      ) { Text("Open GitHub") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
