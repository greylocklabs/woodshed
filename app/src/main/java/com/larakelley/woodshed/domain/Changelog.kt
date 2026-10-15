package com.larakelley.woodshed.domain

/** One `## <version>` section of CHANGELOG.md. */
data class ChangelogRelease(val version: String, val blocks: List<ChangelogBlock>)

/** The Markdown the changelog uses: `###` headings, paragraphs and bullets. Inline `**bold**` and links stay in the text. */
sealed interface ChangelogBlock {
  val text: String

  data class Heading(override val text: String) : ChangelogBlock

  data class Paragraph(override val text: String) : ChangelogBlock

  data class Bullet(override val text: String) : ChangelogBlock
}

private val VERSION_HEADING = Regex("""^## (\d+\.\d+\.\d+)\s*$""")

/** The releases in [markdown], newest first as written. Text before the first version heading is skipped. */
fun parseChangelog(markdown: String): List<ChangelogRelease> {
  val releases = mutableListOf<ChangelogRelease>()
  var version: String? = null
  val blocks = mutableListOf<ChangelogBlock>()
  val pending = StringBuilder()
  var pendingBullet = false

  fun flushBlock() {
    if (pending.isNotEmpty()) blocks += if (pendingBullet) ChangelogBlock.Bullet(pending.toString()) else ChangelogBlock.Paragraph(pending.toString())
    pending.clear()
    pendingBullet = false
  }

  fun flushRelease() {
    flushBlock()
    version?.let { releases += ChangelogRelease(it, blocks.toList()) }
    blocks.clear()
  }

  for (line in markdown.lines()) {
    val heading = VERSION_HEADING.find(line)
    when {
      heading != null -> {
        flushRelease()
        version = heading.groupValues[1]
      }

      version == null -> Unit

      line.startsWith("### ") -> {
        flushBlock()
        blocks += ChangelogBlock.Heading(line.removePrefix("### ").trim())
      }

      line.startsWith("- ") -> {
        flushBlock()
        pendingBullet = true
        pending.append(line.removePrefix("- ").trim())
      }

      line.isBlank() -> flushBlock()

      else -> {
        if (pending.isNotEmpty()) pending.append(' ')
        pending.append(line.trim())
      }
    }
  }
  flushRelease()
  return releases
}

/** Orders `major.minor.patch` versions numerically, so 0.10.0 comes after 0.9.0. */
fun compareVersions(a: String, b: String): Int {
  val x = a.split(".").map { it.toIntOrNull() ?: 0 }
  val y = b.split(".").map { it.toIntOrNull() ?: 0 }
  for (i in 0 until maxOf(x.size, y.size)) {
    val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
    if (c != 0) return c
  }
  return 0
}

/**
 * The releases to show after updating from [seen] to [current], newest first. With no [seen] version, from before
 * the app kept one, only [current]'s release is shown.
 */
fun releasesSince(releases: List<ChangelogRelease>, seen: String?, current: String): List<ChangelogRelease> = releases.filter {
  compareVersions(it.version, current) <= 0 &&
    if (seen == null) compareVersions(it.version, current) == 0 else compareVersions(it.version, seen) > 0
}
