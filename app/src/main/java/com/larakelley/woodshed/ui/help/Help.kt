package com.larakelley.woodshed.ui.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.larakelley.woodshed.theme.LocalCallie
import com.larakelley.woodshed.ui.inlineMarkdown

/** One topic in the help: a question as its title, and short paragraphs that answer it. */
data class HelpTopic(val title: String, val paragraphs: List<String>)

val HELP_TOPICS = listOf(
  HelpTopic(
    "Getting started",
    listOf(
      "Choose the instruments you play in **⚙️ Settings › Instruments**. Keys are then written for your instrument, and exercises are kept to ones that suit it.",
      "Find tunes in the **🎵 Tunes** tab. Open one and tap **🌱 Start learning now** to practise it, or **💭 Add to wishlist** to save it for later.",
      "Then plan a session from the **🎷 Practice** tab.",
    ),
  ),
  HelpTopic(
    "Planning a session",
    listOf(
      "Pick what you're playing, how long, any focus areas, the tunes to work on, and a solo to transcribe. Tunes due for review start ticked.",
      "Woodshed plans a warm-up, exercises for your focus, the solo and your tunes, filling the time you chose. Before you start, each block can be **shuffled** for another exercise, **replaced** with one you pick, or **dropped**; dropping a block shares its minutes among the rest.",
    ),
  ),
  HelpTopic(
    "During a session",
    listOf(
      "Each block has a timer. Blocks don't move on by themselves: tap **✅ Done** when you finish an exercise, or grade a tune or solo. **‹ Back** returns to the previous block if you tapped too soon.",
      "The metronome and drone can be started, stopped and retimed along the bottom of every block. Drone exercises name the note to play against.",
      "A session keeps running with the screen off or another app open, with a notification showing the block and its time left. **End** stops it early; blocks you finished stay logged.",
    ),
  ),
  HelpTopic(
    "Grading and when tunes come back",
    listOf(
      "After working a tune or solo, grade how it went. **Again** brings it back in a few minutes, **Hard**, **Good** and **Easy** space it out further each time. Each button shows when you'll see it next.",
      "Tunes move from your wishlist to learning when you start them, and to repertoire once they're coming back three weeks apart. A slip moves them back to learning.",
      "On a tune's page, **Practice in every key** moves it one step round the circle of fourths each time it comes up, instead of always using its usual key.",
    ),
  ),
  HelpTopic(
    "Keys and your instrument",
    listOf(
      "Tunes are stored in concert pitch and shown written for your instrument: an alto player sees a concert F tune in D. Reading in C, keys show as they are.",
      "Change what you're playing in **⚙️ Settings**, or when you plan a session.",
    ),
  ),
  HelpTopic(
    "Transcribing solos",
    listOf(
      "The **🎧 Transcribe** tab holds solos to learn by ear. A solo plays from just before it starts, at half or three-quarter speed, with five-second skips and an A-B loop for the phrase you're working out.",
      "Add a solo you're transcribing to a session from practice setup, or add your own solos with a link to the recording.",
    ),
  ),
  HelpTopic(
    "Random, and the tools",
    listOf(
      "**🎲 Random** rolls a tune with an exercise and a key, an exercise, or a solo, within limits you set, for when you'd rather not plan.",
      "The **⏱️ Metronome** has tap tempo, jazz tempo presets and clicks on 2 and 4. The **🔊 Drone** sounds a note named for your instrument, with an optional fifth. The **🎯 Tuner** reads your pitch in your instrument's key.",
    ),
  ),
  HelpTopic(
    "History and stats",
    listOf(
      "The **📜 History** tab lists your sessions and what you did in each. The calendar marks the days you practised, and **Stats** shows your time, streaks, most practised tunes and keys.",
    ),
  ),
  HelpTopic(
    "Your data",
    listOf(
      "Everything stays on your phone. Save a backup file from **⚙️ Settings › Backup** to Google Drive, Dropbox or a folder, and restore it on any device.",
      "Woodshed has no account, ads or analytics. It goes online only to play a video you tap and to send a problem report you choose to send. " +
        "Installed from Google Play, it may now and then ask Play to show its rating prompt after a session; it sends nothing for that.",
    ),
  ),
  HelpTopic(
    "Problems and ideas",
    listOf(
      "Each tune, exercise and solo has a **🚩 Report a problem** button for a wrong key or a broken link, " +
        "and **⚙️ Settings › About** has one for anything else. " +
        "You can also write to [hello@woodshedapp.org](mailto:hello@woodshedapp.org).",
    ),
  ),
)

/** The Help tab: topics that open to their answers, so the list stays short to scan. */
@Composable
fun HelpScreen(topics: List<HelpTopic> = HELP_TOPICS) {
  LazyColumn(Modifier.fillMaxSize().testTag("help")) {
    item {
      Text(
        "Help documentation",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
      )
    }
    items(topics, key = { it.title }) { topic -> Topic(topic) }
  }
}

@Composable
private fun Topic(topic: HelpTopic) {
  val callie = LocalCallie.current
  var open by rememberSaveable(topic.title) { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }
        .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
        .padding(horizontal = 16.dp, vertical = 14.dp).testTag("help-${topic.title}"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(topic.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
      Text(if (open) "−" else "+", style = MaterialTheme.typography.titleLarge, color = callie.textMuted)
    }
    if (open) {
      Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        topic.paragraphs.forEach { p ->
          Text(inlineMarkdown(p, callie.accent), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        }
      }
    }
    HorizontalDivider()
  }
}
