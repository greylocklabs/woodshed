package com.larakelley.woodshed.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class ReportTest {
  private val report = Report(
    ReportSubject.TUNE,
    "Wrong key",
    item = "Body & Soul",
    id = "body-and-soul",
    details = listOf("Key" to "Concert Db"),
    note = "Real Book has it in Db, but most bands call it in Eb?",
  )

  private fun query(url: String): Map<String, String> =
    URI(url).rawQuery.split("&").associate { part -> part.substringBefore("=") to URLDecoder.decode(part.substringAfter("="), "UTF-8") }

  @Test
  fun opensTheRepositorysNewIssuePageWithEverythingEncoded() {
    val url = report.issueUrl("1.0", "17")
    assertTrue(url.startsWith("https://github.com/greylocklabs/woodshed/issues/new?"))
    assertTrue("spaces must not become plus signs", !url.contains("+"))
    val q = query(url)
    assertEquals("Wrong key in \"Body & Soul\"", q["title"])
    assertEquals("bug", q["labels"])
    assertEquals(
      """
      **What happened:**
      Real Book has it in Db, but most bands call it in Eb?

      What the app shows:
      - Tune: Body & Soul
      - Id: body-and-soul
      - Key: Concert Db

      Woodshed 1.0, Android 17
      """.trimIndent(),
      q["body"],
    )
  }

  @Test
  fun anEmptyNoteAsksForOneAndIdeasAreLabelledAsEnhancements() {
    val idea = Report(ReportSubject.APP, "An idea")
    assertEquals("**The idea:**\n(please describe)\n\nWoodshed 1.0, Android 17", idea.body("1.0", "17"))
    assertEquals("enhancement", query(idea.issueUrl("1.0", "17"))["labels"])
    assertEquals("An idea", idea.summary)
  }

  @Test
  fun aLongItemNameIsCutShortToFitTheSummary() {
    val long = Report(ReportSubject.TUNE, "Wrong key", "x".repeat(200))
    assertEquals(Report.MAX_SUMMARY, long.summary.length)
    assertTrue(long.summary.startsWith("Wrong key in \"xxx"))
    assertTrue(long.summary.endsWith("...\""))
  }

  @Test
  fun theServiceGetsTheReportInTheSharedFormat() {
    val json = Json.parseToJsonElement(
      Report(ReportSubject.TUNE, "Wrong key", "Solar", "solar", listOf("Key" to "Concert C minor"), "").json("0.1.0", "16"),
    ).jsonObject
    assertEquals(setOf("app", "kind", "summary", "note", "details", "appVersion", "platform"), json.keys)
    assertEquals("woodshed", json["app"]!!.jsonPrimitive.content)
    assertEquals("bug", json["kind"]!!.jsonPrimitive.content)
    assertEquals("Wrong key in \"Solar\"", json["summary"]!!.jsonPrimitive.content)
    assertEquals("[[\"Tune\",\"Solar\"],[\"Id\",\"solar\"],[\"Key\",\"Concert C minor\"]]", json["details"].toString())
    assertEquals(JsonNull, json["note"])
    assertEquals("0.1.0", json["appVersion"]!!.jsonPrimitive.content)
    assertEquals("Android 16", json["platform"]!!.jsonPrimitive.content)
    assertEquals("idea", Json.parseToJsonElement(Report(ReportSubject.APP, "An idea").json("0.1.0", "16")).jsonObject["kind"]!!.jsonPrimitive.content)
  }
}
