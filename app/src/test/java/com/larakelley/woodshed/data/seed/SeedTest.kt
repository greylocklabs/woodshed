package com.larakelley.woodshed.data.seed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Runs the parser and validator over the real seed files, and over deliberately broken copies of them. */
class SeedTest {
  private val dataDir = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "data") }.first { File(it, "standards.json").exists() }
  private val standards = File(dataDir, "standards.json").readText()
  private val exercises = File(dataDir, "exercises.json").readText()
  private val transcriptions = File(dataDir, "transcriptions.json").readText()

  @Test
  fun realSeedIsValid() {
    val seed = Seed.parse(standards, exercises)
    assertTrue(seed.standards.size > 200)
    assertTrue(seed.exercises.size > 40)
  }

  // Each negative case breaks one thing in otherwise real data and checks it is reported.

  @Test
  fun rejectsDuplicateSlug() =
    assertRejected("duplicate slug: a-foggy-day", standards = editRow(standards, 1) { it + ("slug" to JsonPrimitive("a-foggy-day")) })

  @Test
  fun rejectsBlankTitle() = assertRejected("blank title", standards = editRow(standards, 0) { it + ("title" to JsonPrimitive(" ")) })

  @Test
  fun rejectsEmptyStringWhereNullMeansUnknown() = assertRejected("blank form", standards = editRow(standards, 0) { it + ("form" to JsonPrimitive("")) })

  @Test
  fun rejectsDifficultyOutOfRange() = assertRejected("difficulty 9", exercises = editRow(exercises, 0) { it + ("difficulty" to JsonPrimitive(9)) })

  @Test
  fun rejectsUnknownFamily() =
    assertRejected("unknown family Kazoo", exercises = editRow(exercises, 0) { it + ("families" to JsonArray(listOf(JsonPrimitive("Kazoo")))) })

  @Test
  fun rejectsNonPositiveMinutes() = assertRejected("minutes must be positive", exercises = editRow(exercises, 0) { it + ("minutes" to JsonPrimitive(0)) })

  @Test
  fun rejectsMissingField() = assertRejected("time_signature", standards = editRow(standards, 0) { it - "time_signature" })

  @Test
  fun rejectsMisspelledField() = assertRejected("tempo", exercises = editRow(exercises, 0) { (it - "tempo_bpm") + ("tempo" to JsonPrimitive(60)) })

  @Test
  fun rejectsWrongType() = assertRejected("year", standards = editRow(standards, 0) { it + ("year" to JsonPrimitive("c. 1937")) })

  @Test
  fun acceptsUnknownTimeSignature() {
    Seed.parse(editRow(standards, 0) { it + ("time_signature" to kotlinx.serialization.json.JsonNull) }, exercises)
  }

  @Test
  fun rejectsUnknownCollection() = assertRejected(
    "unknown collection Bebop",
    standards = editRow(standards, 0) {
      it + ("collection" to JsonPrimitive("Bebop"))
    },
  )

  @Test
  fun rejectsAKeyItCannotRead() = assertRejected("bad key H", standards = editRow(standards, 0) { it + ("key" to JsonPrimitive("H")) })

  @Test
  fun rejectsATonalityOtherThanMajorOrMinor() =
    assertRejected("bad tonality dorian", standards = editRow(standards, 0) { it + ("key" to JsonPrimitive("D")) + ("tonality" to JsonPrimitive("dorian")) })

  @Test
  fun rejectsAKeyWithoutATonality() =
    assertRejected("key without a tonality", standards = editRow(standards, 0) { it + ("key" to JsonPrimitive("C")) + ("tonality" to JsonNull) })

  @Test
  fun rejectsSlugsKeptForTheUsersOwnTunes() =
    assertRejected("kept for the user's own tunes", standards = editRow(standards, 0) { it + ("slug" to JsonPrimitive("user-blues")) })

  @Test
  fun realSolosAreValid() {
    assertTrue(Seed.parse(standards, exercises, transcriptions).transcriptions.size >= 3)
  }

  @Test
  fun rejectsSolosThatPointNowhereOrRunBackwards() {
    val cases = mapOf(
      "no tune not-a-tune" to ("tune" to JsonPrimitive("not-a-tune")),
      "bad url" to ("url" to JsonPrimitive("not a link")),
      "ends before it starts" to ("end_seconds" to JsonPrimitive(1)),
      "bad recorded date" to ("recorded" to JsonPrimitive("Aug 2022")),
      "kept for the user's own solos" to ("slug" to JsonPrimitive("user-solo")),
    )
    for ((expected, change) in cases) {
      val broken = editRow(transcriptions, 0) { it + change }
      val e = try {
        Seed.parse(standards, exercises, broken)
        fail("'$expected' was accepted")
        throw AssertionError()
      } catch (e: SeedException) {
        e
      }
      assertTrue("expected '$expected' in ${e.problems}", e.problems.any { expected in it })
    }
  }

  @Test
  fun everyBulkRowIsMarkedUnverified() {
    val seed = Seed.parse(standards, exercises)
    assertTrue(seed.standards.any { !it.verified })
    assertTrue(seed.standards.filter { it.year != null && it.form != null }.any { it.verified })
  }

  @Test
  fun rejectsMalformedJson() = assertRejected("standards.json", standards = standards.dropLast(3))

  @Test
  fun reportsEveryProblemAtOnce() {
    val broken = editRow(exercises, 0) { it + ("difficulty" to JsonPrimitive(0)) + ("minutes" to JsonPrimitive(-1)) }
    val e = expectRejection(standards, broken)
    assertEquals(2, e.problems.size)
  }

  private fun assertRejected(expected: String, standards: String = this.standards, exercises: String = this.exercises) {
    val e = expectRejection(standards, exercises)
    assertTrue("expected '$expected' in ${e.problems}", e.problems.any { expected in it })
  }

  private fun expectRejection(standards: String, exercises: String): SeedException {
    try {
      Seed.parse(standards, exercises)
    } catch (e: SeedException) {
      return e
    }
    fail("seed was accepted")
    throw AssertionError()
  }

  private fun editRow(json: String, index: Int, edit: (Map<String, JsonElement>) -> Map<String, JsonElement>): String {
    val rows = Json.parseToJsonElement(json).jsonArray.toMutableList()
    rows[index] = JsonObject(edit(rows[index].jsonObject))
    return JsonArray(rows).toString()
  }
}
