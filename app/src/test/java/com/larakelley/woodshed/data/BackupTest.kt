package com.larakelley.woodshed.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.larakelley.woodshed.data.db.BlockOutcome
import com.larakelley.woodshed.data.db.ReviewEventEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.seed.Seed
import com.larakelley.woodshed.data.seed.SeedImporter
import com.larakelley.woodshed.domain.Comfort
import com.larakelley.woodshed.domain.Focus
import com.larakelley.woodshed.domain.Grade
import com.larakelley.woodshed.domain.Instrument
import com.larakelley.woodshed.domain.KeyPolicy
import com.larakelley.woodshed.domain.Lifecycle
import com.larakelley.woodshed.domain.PlanBlock
import com.larakelley.woodshed.domain.Transposition
import com.larakelley.woodshed.domain.bars
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupTest {
  private val context = ApplicationProvider.getApplicationContext<Application>()
  private lateinit var db: WoodshedDatabase
  private lateinit var repository: WoodshedRepository
  private lateinit var practice: PracticeService
  private lateinit var backups: BackupService

  @Before
  fun setUp() = runTest {
    db = Room.inMemoryDatabaseBuilder(context, WoodshedDatabase::class.java).allowMainThreadQueries().build()
    val read = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json")))
    repository = WoodshedRepository(db, context.getSharedPreferences("backup-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }, context.assets)
    practice = PracticeService(db, random = Random(1))
    backups = BackupService(db, repository, now = { 42L })
  }

  @After
  fun tearDown() = db.close()

  private suspend fun id(slug: String) = db.standards().bySlug(slug)!!.id

  @Test
  fun theUsersOwnChangesComeBack() = runTest {
    val library = MyChangesLibrary(db)
    library.save(null, "Stella bridge", bars("IIm7b5 | V7b9 | Im7 | Im7"))
    val text = backups.export()
    library.save(null, "made after the backup", bars("I"))
    backups.restore(text)
    assertEquals(listOf("Stella bridge" to bars("IIm7b5 | V7b9 | Im7 | Im7")), library.all.first().map { it.name to it.bars })
  }

  /** Some of everything a reset can clear: history, progress on a seeded and an own tune, material and settings. */
  private suspend fun practiceSome(): String {
    val own = TuneLibrary(db).add(TuneDraft(title = "My tune", keyName = "F"))
    for (slug in listOf("solar", own)) practice.startLearning(id(slug))
    practice.setComfort(id("solar"), Comfort.SHAKY)
    val tuneExercise = db.exercises().all().first { it.appliesToTune }
    practice.grade(practice.cardForBlock(PlanBlock.Tune(id(own), tuneExercise.id, 10), Transposition.Bb)!!, Grade.GOOD)
    val log = SessionLog(db)
    log.end(log.start(listOf(PlanBlock.Tune(id("solar"), tuneExercise.id, 10)), "ALTO_SAX", at = 1_000), at = 2_000, completed = true)
    val playlists = PlaylistService(db)
    playlists.add(playlists.create("Gig"), id("solar"))
    MyChangesLibrary(db).save(null, "Vamp", bars("I | IV"))
    repository.setInstrument(Instrument.ALTO_SAX)
    repository.setThemeMode(ThemeMode.DARK)
    return own
  }

  @Test
  fun aResetClearsOnlyWhatIsChosen() = runTest {
    practiceSome()
    backups.reset(setOf(ResetPart.HISTORY, ResetPart.SETTINGS))
    assertTrue(db.sessions().all().isEmpty())
    assertEquals(null, repository.instrument.value)
    assertEquals(ThemeMode.SYSTEM, repository.themeMode.value)
    assertEquals(Transposition.C, repository.transposition.value)
    // Progress, material and the user's own tune stay.
    assertEquals(2, db.tuneStates().all().size)
    assertEquals(1, db.playlists().all().size)
    assertEquals(1, db.myChanges().all().size)
    assertTrue(db.standards().userCreated().all { it.archivedAt == null })
  }

  @Test
  fun ownMaterialTakesItsProgressWithIt() = runTest {
    val own = practiceSome()
    backups.reset(setOf(ResetPart.MATERIAL))
    assertTrue(db.standards().userCreated().all { it.archivedAt != null })
    assertTrue(db.playlists().all().isEmpty())
    assertTrue(db.myChanges().all().isEmpty())
    // The own tune's progress and grades went with it; the seeded tune's stayed, and so did history.
    assertEquals(listOf(id("solar")), db.tuneStates().all().map { it.standardId })
    assertTrue(db.reviewEvents().all().none { it.itemId == id(own) })
    assertEquals(1, db.sessions().all().size)
    assertEquals(Instrument.ALTO_SAX, repository.instrument.value)
  }

  /** A bundled solo and an own one, each started and graded, with a seeded tune learnt and graded too; returns their ids. */
  private suspend fun solosAndATune(): Pair<Long, Long> {
    val read = { name: String -> context.assets.open(name).bufferedReader().use { it.readText() } }
    SeedImporter(db).import(Seed.parse(read("standards.json"), read("exercises.json"), read("transcriptions.json")))
    val solos = TranscriptionService(db)
    val bundled = db.transcriptions().bySlug("singin-in-the-rain-patrick-bartley")!!.id
    val own = solos.add(TranscriptionDraft(title = "Solar", soloist = "Me", instrument = "Alto sax", url = "https://youtu.be/dQw4w9WgXcQ"))
    for (id in listOf(bundled, own)) {
      solos.start(id)
      solos.grade(id, Grade.GOOD)
    }
    practice.startLearning(id("solar"))
    val tuneExercise = db.exercises().all().first { it.appliesToTune }
    practice.grade(practice.cardForBlock(PlanBlock.Tune(id("solar"), tuneExercise.id, 10), Transposition.Bb)!!, Grade.GOOD)
    return bundled to own
  }

  @Test
  fun eachKindOfProgressClearsAlone() = runTest {
    solosAndATune()
    backups.reset(setOf(ResetPart.TUNE_PROGRESS))
    assertTrue(db.tuneStates().all().isEmpty())
    assertTrue(db.reviewEvents().all().none { it.itemKind == ReviewEventEntity.TUNE })
    assertEquals(2, db.transcriptions().states().size)
    assertEquals(2, db.reviewEvents().all().count { it.itemKind == ReviewEventEntity.TRANSCRIPTION })

    solosAndATune()
    backups.reset(setOf(ResetPart.SOLO_PROGRESS))
    assertTrue(db.transcriptions().states().isEmpty())
    assertTrue(db.reviewEvents().all().none { it.itemKind == ReviewEventEntity.TRANSCRIPTION })
    assertEquals(listOf(id("solar")), db.tuneStates().all().map { it.standardId })
    assertTrue(db.reviewEvents().all().any { it.itemKind == ReviewEventEntity.TUNE })
  }

  @Test
  fun ownMaterialTakesItsSolosProgressWithIt() = runTest {
    val (bundled, own) = solosAndATune()
    backups.reset(setOf(ResetPart.MATERIAL))
    assertTrue(db.transcriptions().all().filter { it.userCreated }.all { it.archivedAt != null })
    assertEquals(listOf(bundled), db.transcriptions().states().map { it.transcriptionId })
    val solos = db.reviewEvents().all().filter { it.itemKind == ReviewEventEntity.TRANSCRIPTION }.map { it.itemId }
    assertEquals(listOf(bundled), solos)
    assertTrue(own !in solos)
  }

  @Test
  fun everythingLeavesTheBundledLibrary() = runTest {
    val seeded = db.standards().all().count { !it.userCreated }
    practiceSome()
    backups.reset(ResetPart.entries.toSet())
    assertTrue(db.tuneStates().all().isEmpty())
    assertTrue(db.tuneKnowledge().all().isEmpty())
    assertTrue(db.reviewEvents().all().isEmpty())
    assertTrue(db.sessions().all().isEmpty())
    assertTrue(db.playlists().all().isEmpty())
    assertEquals(seeded, db.standards().all().count { !it.userCreated && it.archivedAt == null })
    assertEquals(null, repository.instrument.value)
  }

  @Test
  fun exportThenRestoreBringsEverythingBack() = runTest {
    practice.startLearning(id("solar"))
    practice.setKeyPolicy(id("solar"), KeyPolicy.CYCLE)
    val tuneExercise = db.exercises().all().first { it.appliesToTune }.id
    practice.grade(practice.cardForBlock(PlanBlock.Tune(id("solar"), tuneExercise, 10), Transposition.Bb)!!, Grade.GOOD)
    practice.addToWishlist(id("nardis"))
    practice.setComfort(id("solar"), Comfort.SHAKY)
    practice.toggleKnownKey(id("solar"), 0)
    repository.setInstrument(Instrument.ALTO_SAX)
    repository.setThemeMode(ThemeMode.DARK)
    repository.rememberSession(45, setOf(Focus.SPEED))
    repository.setTabOrder(listOf("Tunes", "Practice"))
    val states = db.tuneStates().all().sortedBy { it.standardId }
    val knowledge = db.tuneKnowledge().all()
    val events = db.reviewEvents().all().map { it.copy(id = 0) }

    val text = backups.export()
    assertTrue(text.contains("\"format\": \"woodshed-backup\"") && text.contains("\"version\": 1"))
    db.tuneStates().deleteAll()
    db.tuneKnowledge().deleteAll()
    db.reviewEvents().deleteAll()
    repository.setTransposition(Transposition.C)
    repository.setThemeMode(ThemeMode.SYSTEM)
    repository.setTabOrder(emptyList())

    val result = backups.restore(text)
    assertEquals(RestoreResult(tunes = 2, knowledge = 1, reviews = 1, skipped = 0), result)
    assertEquals(states, db.tuneStates().all().sortedBy { it.standardId })
    assertEquals(knowledge, db.tuneKnowledge().all())
    assertEquals(events, db.reviewEvents().all().map { it.copy(id = 0) })
    assertEquals(Instrument.ALTO_SAX, repository.instrument.value)
    assertEquals(Transposition.Eb, repository.transposition.value)
    assertEquals(ThemeMode.DARK, repository.themeMode.value)
    assertEquals(setOf(Focus.SPEED), repository.sessionFocus)
    assertEquals(listOf("Tunes", "Practice"), repository.tabOrder.value)
  }

  @Test
  fun sessionsComeBackWithTheirBlocks() = runTest {
    val log = SessionLog(db)
    val drill = db.exercises().all().first { !it.appliesToTune }
    val tuneExercise = db.exercises().all().first { it.appliesToTune }
    val plan = listOf(PlanBlock.Exercise(drill.id, 5), PlanBlock.Tune(id("solar"), tuneExercise.id, 10))
    val session = log.start(plan, "ALTO_SAX", at = 1_000)
    log.finish(session, 0, BlockOutcome.DONE, null, 300_000, at = 301_000)
    log.finish(session, 1, BlockOutcome.GRADED, Grade.EASY, 500_000, at = 801_000)
    log.end(session, at = 802_000, completed = true)
    val sessions = db.sessions().all().map { it.copy(id = 0) }
    val blocks = db.sessions().allBlocks().map { it.copy(sessionId = 0) }

    val text = backups.export()
    db.sessions().deleteAll()
    backups.restore(text)
    assertEquals(sessions, db.sessions().all().map { it.copy(id = 0) })
    assertEquals(blocks, db.sessions().allBlocks().map { it.copy(sessionId = 0) })
  }

  @Test
  fun restoreReplacesRatherThanMerges() = runTest {
    practice.addToWishlist(id("solar"))
    val text = backups.export()
    practice.addToWishlist(id("nardis"))
    backups.restore(text)
    assertEquals(listOf(id("solar")), db.tuneStates().all().map { it.standardId })
  }

  @Test
  fun unknownTunesAreSkippedAndCounted() = runTest {
    practice.addToWishlist(id("solar"))
    val text = backups.export().replace("\"solar\"", "\"no-such-tune\"")
    val result = backups.restore(text)
    assertEquals(1, result.skipped)
    assertTrue(db.tuneStates().all().isEmpty())
  }

  @Test
  fun aFileThatIsNotABackupIsRejectedWithoutTouchingData() = runTest {
    practice.addToWishlist(id("solar"))
    assertThrows(BackupException::class.java) { kotlinx.coroutines.runBlocking { backups.restore("{\"hello\": 1}") } }
    assertThrows(BackupException::class.java) { kotlinx.coroutines.runBlocking { backups.restore("not json") } }
    val unmarked = backups.export().replace("\"format\": \"woodshed-backup\",", "")
    assertThrows(BackupException::class.java) { kotlinx.coroutines.runBlocking { backups.restore(unmarked) } }
    assertEquals(1, db.tuneStates().all().size)
    assertEquals(Lifecycle.WISHLIST, db.tuneStates().all().single().lifecycle)
  }

  private val format = Json { encodeDefaults = true }

  @Test
  fun restoreRefusesRowsThatWouldTakeOverSeedDataOrCrash() = runTest {
    val seeded = db.exercises().all().first { !it.userCreated }
    val base = format.decodeFromString(Backup.serializer(), backups.export())
    val text = format.encodeToString(
      Backup.serializer(),
      base.copy(
        myTunes = listOf(BackupMyTune(slug = "solar", title = "Not Solar")),
        myExercises = listOf(BackupExercise(seeded.name, "Tone", "Mine.", 5, 1)),
        media = listOf(BackupMedia("solar", "javascript:alert(1)")),
      ),
    )
    backups.restore(text)
    // Restoring the same file again must not trip over the copy made the first time.
    backups.restore(text)

    val solar = db.standards().bySlug("solar")!!
    assertEquals("Solar", solar.title)
    assertTrue(!solar.userCreated)
    assertEquals(listOf("${seeded.name} (yours)"), db.exercises().userCreated().map { it.name })
    assertTrue(db.tuneMedia().all().isEmpty())
  }
}
