package com.larakelley.woodshed.data

import com.larakelley.woodshed.domain.Report
import com.larakelley.woodshed.domain.ReportSubject
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

/** The sender against a local stand-in for the report service, answering whatever each test needs. */
class ReportSenderTest {
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  private val received = mutableListOf<String>()
  private val report = Report(ReportSubject.APP, "An idea", note = "More drones")

  private fun answer(status: Int, body: String): HttpReportSender {
    server.createContext("/report") { exchange ->
      received += exchange.requestBody.bufferedReader().readText()
      val bytes = body.toByteArray()
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    server.start()
    return HttpReportSender("http://127.0.0.1:${server.address.port}/report")
  }

  @After
  fun stop() = server.stop(0)

  @Test
  fun aFiledReportComesBackWithItsIssue() = runTest {
    val result = answer(201, """{"number":42,"url":"https://github.com/greylocklabs/woodshed/issues/42"}""").send(report, "0.2.0", "16")
    assertEquals(FiledReport(42, "https://github.com/greylocklabs/woodshed/issues/42"), result.getOrThrow())
    assertTrue(received.single().contains("\"problem\":\"An idea\""))
  }

  @Test
  fun anUnreadableAnswerIsAFailureNotACrash() = runTest {
    val result = answer(201, "<html>not json</html>").send(report, "0.2.0", "16")
    assertEquals("The report service sent an answer the app couldn't read.", result.exceptionOrNull()?.message)
  }

  @Test
  fun aFloodIsExplained() = runTest {
    val result = answer(429, """{"error":"too many"}""").send(report, "0.2.0", "16")
    assertEquals("Too many reports just now. Try again in a minute.", result.exceptionOrNull()?.message)
  }
}
