package com.larakelley.woodshed.data

import com.larakelley.woodshed.domain.Report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A report the service filed: its issue number and page. */
@Serializable
data class FiledReport(val number: Int, val url: String)

/** Sends a report to be filed as a GitHub issue. */
fun interface ReportSender {
  /** The filed issue, or why it was not filed, in words to show the user. */
  suspend fun send(report: Report, appVersion: String, androidVersion: String): Result<FiledReport>
}

/** Posts reports to the report service. */
class HttpReportSender(private val endpoint: String = Report.SERVICE) : ReportSender {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun send(report: Report, appVersion: String, androidVersion: String): Result<FiledReport> = withContext(Dispatchers.IO) {
    try {
      val connection = URL(endpoint).openConnection() as HttpURLConnection
      connection.requestMethod = "POST"
      connection.doOutput = true
      connection.connectTimeout = 10_000
      connection.readTimeout = 20_000
      connection.setRequestProperty("Content-Type", "application/json")
      connection.outputStream.use { it.write(report.json(appVersion, androidVersion).toByteArray()) }
      val status = connection.responseCode
      if (status == 201) {
        Result.success(json.decodeFromString<FiledReport>(connection.inputStream.bufferedReader().use { it.readText() }))
      } else {
        Result.failure(IOException(if (status == 429) "Too many reports just now. Try again in a minute." else "The report service answered $status."))
      }
    } catch (e: IOException) {
      Result.failure(IOException("Couldn't reach the report service. Check your connection.", e))
    }
  }
}
