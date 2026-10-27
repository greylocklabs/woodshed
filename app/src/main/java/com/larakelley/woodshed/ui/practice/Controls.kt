package com.larakelley.woodshed.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.theme.Grades
import org.greylocklabs.ui.LocalColors
import org.greylocklabs.ui.Sticker
import org.greylocklabs.ui.StickerButton

/** Four full-height grade columns: one imprecise thumb tap from a music stand should land. */
@Composable
fun GradeButtons(buttons: List<Pair<Grade, String>>, onGrade: (Grade) -> Unit, modifier: Modifier = Modifier) {
  val callie = LocalColors
  Row(modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    buttons.forEach { (grade, delay) ->
      val sticker = when (grade) {
        Grade.AGAIN -> Grades.again
        Grade.HARD -> Grades.hard
        Grade.GOOD -> Sticker(callie.accent, callie.accentText, callie.accentEdge)
        Grade.EASY -> Grades.easy
      }
      val content = sticker.ink
      StickerButton(
        onClick = { onGrade(grade) },
        modifier = Modifier.weight(1f).fillMaxHeight().testTag("grade-${grade.name.lowercase()}"),
        sticker = sticker,
        radius = 16.dp,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
          BasicText(
            grade.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge.copy(color = content, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 26.sp),
          )
          Text(delay, style = MaterialTheme.typography.titleMedium, color = content.copy(alpha = 0.8f), maxLines = 1)
        }
      }
    }
  }
}
