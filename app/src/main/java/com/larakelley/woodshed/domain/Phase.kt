package com.larakelley.woodshed.domain

/** Session phases in order, each with the exercise categories it draws from. */
enum class Phase(val categories: List<String>) {
  WARMUP(listOf("Tone", "Air & Breathing", "Articulation")),
  FUNDAMENTALS(listOf("Technique", "Scales & Patterns", "Rhythm & Time", "Etudes")),
  VOCABULARY(listOf("Licks", "ii-V-I & Progressions")),
  REPERTOIRE(listOf("Standards")),
  COOLDOWN(listOf("Improvisation", "Transcription", "Ear Training", "Reading"));

  companion object {
    /** Every category in session order; unknown categories sort last. */
    val CATEGORY_ORDER: List<String> = entries.flatMap { it.categories }

    fun categoryRank(category: String): Int = CATEGORY_ORDER.indexOf(category).let { if (it < 0) Int.MAX_VALUE else it }
  }
}

/** Sorts titles the way a fake book indexes them: ignoring a leading article and punctuation. */
fun titleSortKey(title: String): String =
  title.lowercase().trimStart('\'').removePrefix("the ").removePrefix("a ").filter { it.isLetterOrDigit() || it == ' ' }
