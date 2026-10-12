package com.larakelley.woodshed.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.domain.ReportSubject
import com.larakelley.woodshed.theme.BackButton
import com.larakelley.woodshed.theme.StickerButton
import com.larakelley.woodshed.theme.neutralSticker
import com.larakelley.woodshed.ui.report.ReportButton

/** Full page for one exercise, opened from the Exercises tab. */
@Composable
fun ExerciseDetail(exercise: ExerciseRow, onBack: () -> Unit, modifier: Modifier = Modifier, onEdit: () -> Unit = {}) {
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      BackButton("Back to exercises", onBack)
      Spacer(Modifier.weight(1f))
      StickerButton(onClick = onEdit, sticker = neutralSticker, modifier = Modifier.height(40.dp).testTag("edit-exercise")) {
        Text("✏️ Edit", style = MaterialTheme.typography.labelLarge)
      }
    }
    Text(exercise.name, style = MaterialTheme.typography.headlineMedium)
    when {
      exercise.userCreated -> Text("💪 Your exercise", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("your-exercise"))
      exercise.edited -> Text("✏️ Edited by you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("edited-exercise"))
    }
    ExerciseFacts(exercise)
    ReportButton(
      ReportSubject.EXERCISE,
      item = exercise.name,
      details = listOfNotNull(
        "For" to exercise.instruments,
        "Category" to exercise.category,
        "Time" to "${exercise.minutes} min",
        exercise.tempoBpm?.let { "Tempo" to "$it bpm" },
      ),
    )
  }
}

/** The facts and description of an exercise, shared by its page and the session plan's pop-up. */
@Composable
fun ExerciseFacts(exercise: ExerciseRow) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Fact("For", exercise.instruments, Modifier.testTag("exercise-instruments"))
    Fact("Category", exercise.category)
    if (exercise.focus.isNotEmpty()) Fact("Focus", exercise.focus.joinToString(", "))
    Fact("Time", "${exercise.minutes} min")
    Fact("Difficulty", "${exercise.difficulty} of 5")
    exercise.tempoBpm?.let { Fact("Tempo", "$it bpm") }
    if (exercise.appliesToTune) Fact("Used with", "A tune, on practice cards and in sessions")
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text(exercise.description, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
  Row(modifier) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
    Text(value, style = MaterialTheme.typography.bodyLarge)
  }
}
