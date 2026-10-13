package com.larakelley.woodshed.data.seed

import com.larakelley.woodshed.data.parseRecorded
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.MediaLink
import com.larakelley.woodshed.domain.Tonality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class StandardSeed(
  val title: String,
  val slug: String,
  val composer: String?,
  val year: Int?,
  val bars: Int?,
  @SerialName("time_signature") val timeSignature: String?,
  val form: String?,
  val style: String?,
  /** Concert tonic of the key the tune is usually called in, such as "Eb" or "F#". */
  val key: String?,
  /** "major" or "minor". */
  val tonality: String?,
  /** "Standards", or "Trad" for New Orleans and Dixieland repertoire. */
  val collection: String,
  /** False for rows imported in bulk whose fields have not been checked tune by tune. */
  val verified: Boolean,
)

@Serializable
data class ExerciseSeed(
  val name: String,
  val category: String,
  val description: String,
  val minutes: Int,
  val difficulty: Int,
  @SerialName("tempo_bpm") val tempoBpm: Int?,
  val families: List<String>,
  @SerialName("applies_to_tune") val appliesToTune: Boolean,
)

@Serializable
data class PerformerSeed(val name: String, val instrument: String)

/** A solo to transcribe: who played it, where, and the recording. */
@Serializable
data class TranscriptionSeed(
  val slug: String,
  /** The tune's slug in standards.json, when the tune is in the library. */
  val tune: String? = null,
  val title: String,
  val soloist: String,
  val instrument: String,
  /** Where it was played, such as an album or a live series. */
  val context: String? = null,
  /** When it was played: "2022-08-13", "2022-08" or "2022". */
  val recorded: String? = null,
  val url: String,
  @SerialName("start_seconds") val startSeconds: Int? = null,
  @SerialName("end_seconds") val endSeconds: Int? = null,
  /** Concert tonic of the key the solo is played in. */
  val key: String? = null,
  val tonality: String? = null,
  val performers: List<PerformerSeed> = emptyList(),
)

class SeedException(val problems: List<String>) : Exception(problems.joinToString("; "))

data class Seed(val standards: List<StandardSeed>, val exercises: List<ExerciseSeed>, val transcriptions: List<TranscriptionSeed> = emptyList()) {
  companion object {
    // Strict on purpose: an unknown or misspelled field is a data bug, not something to skip.
    private val json = Json { ignoreUnknownKeys = false }

    /** Parses and validates both files, throwing [SeedException] listing everything wrong. */
    fun parse(standardsJson: String, exercisesJson: String, transcriptionsJson: String = "[]"): Seed {
      val standards = decode<List<StandardSeed>>("standards.json", standardsJson)
      val exercises = decode<List<ExerciseSeed>>("exercises.json", exercisesJson)
      val transcriptions = decode<List<TranscriptionSeed>>("transcriptions.json", transcriptionsJson)
      val seed = Seed(standards, exercises, transcriptions)
      val problems = SeedValidator.validate(seed)
      if (problems.isNotEmpty()) throw SeedException(problems)
      return seed
    }

    private inline fun <reified T> decode(file: String, text: String): T = try {
      json.decodeFromString<T>(text)
    } catch (e: SerializationException) {
      throw SeedException(listOf("$file: ${e.message}"))
    } catch (e: IllegalArgumentException) {
      throw SeedException(listOf("$file: ${e.message}"))
    }
  }
}

object SeedValidator {
  private val SLUG = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
  private val TIME_SIGNATURE = Regex("^\\d+/\\d+$")
  val COLLECTIONS = listOf("Standards", "Trad")

  /** The user's own tunes have slugs with this prefix, so a seed tune can never claim one. */
  const val USER_SLUG_PREFIX = "user-"

  fun validate(seed: Seed): List<String> = buildList {
    if (seed.standards.isEmpty()) add("standards.json is empty")
    if (seed.exercises.isEmpty()) add("exercises.json is empty")

    duplicates(seed.standards.map { it.slug }).forEach { add("duplicate slug: $it") }
    for (s in seed.standards) {
      val at = "standard ${s.slug}"
      if (!SLUG.matches(s.slug)) add("$at: malformed slug")
      if (s.slug.startsWith(USER_SLUG_PREFIX)) add("$at: slugs starting $USER_SLUG_PREFIX are kept for the user's own tunes")
      if (s.title.isBlank()) add("$at: blank title")
      if (s.timeSignature != null && !TIME_SIGNATURE.matches(s.timeSignature)) add("$at: bad time signature ${s.timeSignature}")
      if (s.collection !in COLLECTIONS) add("$at: unknown collection ${s.collection}")
      if (s.year != null && s.year !in 1800..2100) add("$at: implausible year ${s.year}")
      if (s.bars != null && s.bars <= 0) add("$at: bars must be positive")
      if (s.composer != null && s.composer.isBlank()) add("$at: blank composer, use null for unknown")
      if (s.form != null && s.form.isBlank()) add("$at: blank form, use null for unknown")
      if (s.key != null && Key.pitchClassOf(s.key) == null) add("$at: bad key ${s.key}")
      if (s.tonality != null && Tonality.fromSeedName(s.tonality) == null) add("$at: bad tonality ${s.tonality}")
      if (s.key != null && s.tonality == null) add("$at: key without a tonality")
    }

    val tuneSlugs = seed.standards.map { it.slug }.toSet()
    duplicates(seed.transcriptions.map { it.slug }).forEach { add("duplicate transcription: $it") }
    for (t in seed.transcriptions) {
      val at = "transcription ${t.slug}"
      if (!SLUG.matches(t.slug)) add("$at: malformed slug")
      if (t.slug.startsWith(USER_SLUG_PREFIX)) add("$at: slugs starting $USER_SLUG_PREFIX are kept for the user's own solos")
      if (t.tune != null && t.tune !in tuneSlugs) add("$at: no tune ${t.tune}")
      if (t.title.isBlank() || t.soloist.isBlank() || t.instrument.isBlank()) add("$at: title, soloist and instrument are needed")
      if (MediaLink.parse(t.url) == null) add("$at: bad url ${t.url}")
      if (t.recorded != null && parseRecorded(t.recorded) != t.recorded) add("$at: bad recorded date ${t.recorded}")
      if (t.key != null && Key.pitchClassOf(t.key) == null) add("$at: bad key ${t.key}")
      if (t.tonality != null && Tonality.fromSeedName(t.tonality) == null) add("$at: bad tonality ${t.tonality}")
      if (t.startSeconds != null && t.endSeconds != null && t.endSeconds <= t.startSeconds) add("$at: ends before it starts")
    }

    duplicates(seed.exercises.map { it.name }).forEach { add("duplicate exercise: $it") }
    for (e in seed.exercises) {
      val at = "exercise ${e.name}"
      if (e.name.isBlank()) add("$at: blank name")
      if (e.category.isBlank()) add("$at: blank category")
      if (e.description.isBlank()) add("$at: blank description")
      if (e.minutes <= 0) add("$at: minutes must be positive")
      if (e.difficulty !in 1..5) add("$at: difficulty ${e.difficulty} outside 1-5")
      if (e.tempoBpm != null && e.tempoBpm <= 0) add("$at: tempo must be positive")
      e.families.filter { Family.fromSeedName(it) == null }.forEach { add("$at: unknown family $it") }
    }
  }

  private fun duplicates(values: List<String>): Set<String> = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
}
