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
    assertTrue(url.startsWith("https://github.com/lararosekelley/woodshed/issues/new?"))
    assertTrue("spaces must not become plus signs", !url.contains("+"))
    val q = query(url)
    assertEquals("Wrong key in \"Body & Soul\"", q["title"])
    assertEquals("bug", q["labels"])
    val body = q.getValue("body")
    assertTrue(body.contains("body-and-soul"))
    assertTrue(body.contains("- Key: Concert Db"))
    assertTrue(body.contains("most bands call it in Eb?"))
    assertTrue(body.endsWith("Woodshed 1.0, Android 17"))
  }

  @Test
  fun anEmptyNoteAsksForOneAndIdeasAreLabelledAsEnhancements() {
    val idea = Report(ReportSubject.APP, "An idea")
    assertTrue(idea.body("1.0", "17").contains("(please describe)"))
    assertEquals("enhancement", query(idea.issueUrl("1.0", "17"))["labels"])
    assertEquals("An idea", idea.title)
  }

  @Test
  fun theServiceGetsTheReportAsJson() {
    val json = Json.parseToJsonElement(
      Report(ReportSubject.TUNE, "Wrong key", "Solar", "solar", listOf("Key" to "Concert C minor"), "").json("0.1.0", "16"),
    ).jsonObject
    assertEquals("TUNE", json["subject"]!!.jsonPrimitive.content)
    assertEquals("Solar", json["item"]!!.jsonPrimitive.content)
    assertEquals("[[\"Key\",\"Concert C minor\"]]", json["details"].toString())
    assertEquals(JsonNull, json["note"])
    assertEquals("16", json["androidVersion"]!!.jsonPrimitive.content)
  }
}
