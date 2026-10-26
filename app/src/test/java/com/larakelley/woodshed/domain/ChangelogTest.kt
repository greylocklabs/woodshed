package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
  private val markdown = """
    # Changelog

    Notes before the first release are not a release.

    ## Unreleased

    - not released yet, so not shown

    ## 0.10.0 - 2026-10-26

    ### Added

    - **tunes:** a bullet that wraps
      onto a second line.
    - another bullet

    ## 0.9.0

    The first release, in a paragraph
    over two lines.

    ### Fixed

    - a [link](https://example.com)
  """.trimIndent()

  private val releases = parseChangelog(markdown)

  @Test
  fun splitsTheChangelogIntoReleasesOfHeadingsParagraphsAndBullets() {
    assertEquals(listOf("0.10.0", "0.9.0"), releases.map { it.version })
    assertEquals(
      listOf(
        ChangelogBlock.Heading("Added"),
        ChangelogBlock.Bullet("**tunes:** a bullet that wraps onto a second line."),
        ChangelogBlock.Bullet("another bullet"),
      ),
      releases[0].blocks,
    )
    assertEquals(
      listOf(
        ChangelogBlock.Paragraph("The first release, in a paragraph over two lines."),
        ChangelogBlock.Heading("Fixed"),
        ChangelogBlock.Bullet("a [link](https://example.com)"),
      ),
      releases[1].blocks,
    )
  }

  @Test
  fun comparesVersionsNumerically() {
    assertTrue(compareVersions("0.10.0", "0.9.0") > 0)
    assertTrue(compareVersions("1.0.0", "0.99.99") > 0)
    assertEquals(0, compareVersions("0.2.3", "0.2.3"))
  }

  @Test
  fun showsEveryReleaseSinceTheOneLastSeenUpToTheInstalledOne() {
    assertEquals(listOf("0.10.0", "0.9.0"), releasesSince(releases, "0.8.0", "0.10.0").map { it.version })
    assertEquals(listOf("0.10.0"), releasesSince(releases, "0.9.0", "0.10.0").map { it.version })
    // A changelog ahead of the installed build, as on main between releases, shows nothing newer than the app.
    assertEquals(listOf("0.9.0"), releasesSince(releases, "0.8.0", "0.9.0").map { it.version })
  }
}
