package com.larakelley.woodshed.ui.browse

/** The main tabs in their default order, each with the emoji shown before its name. */
val TAB_EMOJI = linkedMapOf(
  "Practice" to "🎷",
  "History" to "📜",
  "Random" to "🎲",
  "Transcribe" to "🎧",
  "Metronome" to "⏱️",
  "Drone" to "🔊",
  "Tuner" to "🎯",
  "Tunes" to "🎵",
  "Exercises" to "💪",
  "Help" to "❓",
)

val DEFAULT_TABS: List<String> = TAB_EMOJI.keys.toList()

/** [available] in the user's [saved] order; tabs the saved order does not name keep their default places after it. */
fun orderTabs(available: List<String>, saved: List<String>): List<String> = saved.filter { it in available } + available.filter { it !in saved }

/** [order] with [name] moved [by] places, staying within the list. */
fun moveTab(order: List<String>, name: String, by: Int): List<String> {
  val from = order.indexOf(name)
  if (from < 0) return order
  val to = (from + by).coerceIn(0, order.lastIndex)
  return order.toMutableList().apply { add(to, removeAt(from)) }
}
