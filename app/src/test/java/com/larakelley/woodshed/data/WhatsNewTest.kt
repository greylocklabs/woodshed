package com.larakelley.woodshed.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.domain.parseChangelog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WhatsNewTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private val prefs = context.getSharedPreferences("whats-new-test", Context.MODE_PRIVATE)
  private val changelog = """
    ## 0.3.0

    - three

    ## 0.2.0

    - two
  """.trimIndent()
  private val whatsNew = WhatsNew(prefs) { changelog }

  @Before
  fun setUp() {
    prefs.edit().clear().commit()
  }

  @Test
  fun aFreshInstallShowsNothingNowOrUntilTheNextUpdate() {
    assertTrue(whatsNew.pending("0.2.0", freshInstall = true).isEmpty())
    assertEquals(listOf("0.3.0"), whatsNew.pending("0.3.0", freshInstall = false).map { it.version })
  }

  @Test
  fun anUpdateShowsUntilDismissed() {
    whatsNew.markSeen("0.2.0")
    assertEquals(listOf("0.3.0"), whatsNew.pending("0.3.0", freshInstall = false).map { it.version })
    // Closing the app without dismissing shows it again next launch.
    assertEquals(listOf("0.3.0"), whatsNew.pending("0.3.0", freshInstall = false).map { it.version })
    whatsNew.markSeen("0.3.0")
    assertTrue(whatsNew.pending("0.3.0", freshInstall = false).isEmpty())
  }

  @Test
  fun anUpdateFromBeforeTheDialogExistedShowsTheInstalledRelease() {
    assertEquals(listOf("0.3.0"), whatsNew.pending("0.3.0", freshInstall = false).map { it.version })
  }

  @Test
  fun theBundledChangelogHasASectionForTheAppsVersion() {
    val bundled = context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
    val version = File("../VERSION").readText().trim()
    assertTrue("CHANGELOG.md needs a ## $version section", parseChangelog(bundled).any { it.version == version })
  }
}
