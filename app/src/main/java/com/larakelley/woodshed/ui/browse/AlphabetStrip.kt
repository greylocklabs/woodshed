package com.larakelley.woodshed.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larakelley.woodshed.theme.LocalCallie
import kotlinx.coroutines.launch

/** "#" for titles that start with a digit or symbol, then A to Z. */
val ALPHABET = listOf('#') + ('A'..'Z')

/** The strip letter a sort key files under. */
fun letterOf(sortKey: String): Char = sortKey.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

/** The index of the first row filed under [letter], or of the next letter that has rows. */
fun firstIndexFor(letter: Char, sortKeys: List<String>): Int? {
  val from = ALPHABET.indexOf(letter)
  for (l in ALPHABET.drop(from)) {
    val i = sortKeys.indexOfFirst { letterOf(it) == l }
    if (i >= 0) return i
  }
  return null
}

/** Which letter sits nearest [y] when [ALPHABET] is spread with equal gaps over [height], as SpaceEvenly lays it out. */
fun letterIndexAt(y: Float, height: Float): Int = (Math.round(y / height * (ALPHABET.size + 1)) - 1).coerceIn(0, ALPHABET.lastIndex)

/**
 * Letters down the right edge of an alphabetical list. Tapping or dragging along it jumps the list to that letter,
 * with a bubble showing which letter you are on.
 */
@Composable
fun AlphabetStrip(listState: LazyListState, sortKeys: List<String>, modifier: Modifier = Modifier) {
  val callie = LocalCallie.current
  val scope = rememberCoroutineScope()
  var active by remember { mutableStateOf<Char?>(null) }
  var stripHeight by remember { mutableStateOf(1) }

  fun jumpTo(y: Float) {
    val letter = ALPHABET[letterIndexAt(y, stripHeight.toFloat())]
    if (letter == active) return
    active = letter
    firstIndexFor(letter, sortKeys)?.let { scope.launch { listState.scrollToItem(it) } }
  }

  Row(modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
    active?.let { letter ->
      Box(Modifier.size(56.dp).background(callie.accent, CircleShape), contentAlignment = Alignment.Center) {
        Text("$letter", style = MaterialTheme.typography.headlineMedium, color = callie.accentText)
      }
    }
    Column(
      Modifier
        .width(22.dp)
        .fillMaxHeight()
        .padding(vertical = 8.dp)
        .background(if (active != null) callie.surfaceAlt else callie.background.copy(alpha = 0f), RoundedCornerShape(11.dp))
        .testTag("alphabet-strip")
        .pointerInput(sortKeys) {
          stripHeight = size.height.coerceAtLeast(1)
          detectTapGestures(onPress = { jumpTo(it.y); tryAwaitRelease(); active = null })
        }
        .pointerInput(sortKeys) {
          detectVerticalDragGestures(onDragEnd = { active = null }, onDragCancel = { active = null }) { change, _ -> jumpTo(change.position.y) }
        },
      horizontalAlignment = Alignment.CenterHorizontally,
      // Letters spread over the list's height, so every one shows and a drag maps evenly onto them.
      verticalArrangement = Arrangement.SpaceEvenly,
    ) {
      ALPHABET.forEach { letter ->
        Text("$letter", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (letter == active) callie.accent else callie.textMuted)
      }
    }
  }
}
