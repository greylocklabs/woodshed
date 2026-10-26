package com.larakelley.woodshed.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import com.larakelley.woodshed.domain.Family
import com.larakelley.woodshed.domain.Grade
import kotlinx.coroutines.flow.Flow

@Dao
interface StandardDao {
  @Query("SELECT * FROM standards")
  suspend fun all(): List<StandardEntity>

  @Query("SELECT * FROM standards WHERE archivedAt IS NULL")
  fun observeActive(): Flow<List<StandardEntity>>

  @Query("SELECT * FROM standards WHERE slug = :slug")
  suspend fun bySlug(slug: String): StandardEntity?

  @Query("SELECT * FROM standards WHERE id = :id")
  suspend fun byId(id: Long): StandardEntity?

  @Query("SELECT * FROM standards WHERE userCreated = 1")
  suspend fun userCreated(): List<StandardEntity>

  @Insert suspend fun insert(standard: StandardEntity): Long

  @Update suspend fun update(standards: List<StandardEntity>)
}

@Dao
interface ExerciseDao {
  @Query("SELECT * FROM exercises")
  suspend fun all(): List<ExerciseEntity>

  @Query("SELECT * FROM exercises WHERE archivedAt IS NULL")
  fun observeActive(): Flow<List<ExerciseEntity>>

  @Query("SELECT * FROM exercises WHERE name = :name")
  suspend fun byName(name: String): ExerciseEntity?

  @Query("SELECT * FROM exercises WHERE id = :id")
  suspend fun byId(id: Long): ExerciseEntity?

  @Query("SELECT * FROM exercises WHERE userCreated = 1")
  suspend fun userCreated(): List<ExerciseEntity>

  @Insert suspend fun insert(exercise: ExerciseEntity): Long

  @Update suspend fun update(exercises: List<ExerciseEntity>)
}

@Dao
interface TuneStateDao {
  @Query("SELECT * FROM tune_state")
  fun observeAll(): Flow<List<TuneStateEntity>>

  @Query("SELECT * FROM tune_state")
  suspend fun all(): List<TuneStateEntity>

  @Query("SELECT * FROM tune_state WHERE standardId = :standardId")
  suspend fun get(standardId: Long): TuneStateEntity?

  @Upsert suspend fun upsert(state: TuneStateEntity)

  @Query("DELETE FROM tune_state WHERE standardId = :standardId")
  suspend fun delete(standardId: Long)

  @Query("DELETE FROM tune_state")
  suspend fun deleteAll()

  @Query(
    "SELECT * FROM tune_state WHERE lifecycle IN ('LEARNING', 'REPERTOIRE') AND dueAt IS NOT NULL AND dueAt <= :now ORDER BY dueAt",
  )
  suspend fun due(now: Long): List<TuneStateEntity>

  @Query("SELECT * FROM tune_state WHERE lifecycle = 'WISHLIST' ORDER BY wishlistedAt, standardId")
  suspend fun wishlist(): List<TuneStateEntity>
}

@Dao
interface TuneKnowledgeDao {
  @Query("SELECT * FROM tune_knowledge")
  fun observeAll(): Flow<List<TuneKnowledgeEntity>>

  @Query("SELECT * FROM tune_knowledge")
  suspend fun all(): List<TuneKnowledgeEntity>

  @Query("SELECT * FROM tune_knowledge WHERE standardId = :standardId")
  suspend fun get(standardId: Long): TuneKnowledgeEntity?

  @Upsert suspend fun upsert(knowledge: TuneKnowledgeEntity)

  @Query("DELETE FROM tune_knowledge")
  suspend fun deleteAll()
}

@Dao
interface TuneOverrideDao {
  @Query("SELECT * FROM tune_overrides")
  fun observeAll(): Flow<List<TuneOverrideEntity>>

  @Query("SELECT * FROM tune_overrides")
  suspend fun all(): List<TuneOverrideEntity>

  @Query("SELECT * FROM tune_overrides WHERE standardId = :standardId")
  suspend fun get(standardId: Long): TuneOverrideEntity?

  @Upsert suspend fun upsert(override: TuneOverrideEntity)

  @Query("DELETE FROM tune_overrides WHERE standardId = :standardId")
  suspend fun delete(standardId: Long)

  @Query("DELETE FROM tune_overrides")
  suspend fun deleteAll()
}

@Dao
interface PlaylistDao {
  @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
  fun observeAll(): Flow<List<PlaylistEntity>>

  @Query("SELECT * FROM playlists")
  suspend fun all(): List<PlaylistEntity>

  @Query("SELECT * FROM playlist_tunes ORDER BY playlistId, position")
  fun observeEntries(): Flow<List<PlaylistTuneEntity>>

  @Query("SELECT * FROM playlist_tunes ORDER BY playlistId, position")
  suspend fun entries(): List<PlaylistTuneEntity>

  @Query("SELECT * FROM playlist_tunes WHERE playlistId = :playlistId ORDER BY position")
  suspend fun entries(playlistId: Long): List<PlaylistTuneEntity>

  @Insert suspend fun insert(playlist: PlaylistEntity): Long

  @Update suspend fun update(playlist: PlaylistEntity)

  @Query("DELETE FROM playlists WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("DELETE FROM playlists")
  suspend fun deleteAll()

  @Upsert suspend fun upsertEntries(entries: List<PlaylistTuneEntity>)

  @Query("DELETE FROM playlist_tunes WHERE playlistId = :playlistId AND standardId = :standardId")
  suspend fun removeEntry(playlistId: Long, standardId: Long)

  @Query("DELETE FROM playlist_tunes WHERE playlistId = :playlistId")
  suspend fun clear(playlistId: Long)
}

@Dao
interface ExerciseOverrideDao {
  @Query("SELECT * FROM exercise_overrides")
  fun observeAll(): Flow<List<ExerciseOverrideEntity>>

  @Query("SELECT * FROM exercise_overrides")
  suspend fun all(): List<ExerciseOverrideEntity>

  @Query("SELECT * FROM exercise_overrides WHERE exerciseId = :exerciseId")
  suspend fun get(exerciseId: Long): ExerciseOverrideEntity?

  @Upsert suspend fun upsert(override: ExerciseOverrideEntity)

  @Query("DELETE FROM exercise_overrides WHERE exerciseId = :exerciseId")
  suspend fun delete(exerciseId: Long)

  @Query("DELETE FROM exercise_overrides")
  suspend fun deleteAll()
}

@Dao
interface TuneMediaDao {
  @Query("SELECT * FROM tune_media ORDER BY standardId, position, id")
  fun observeAll(): Flow<List<TuneMediaEntity>>

  @Query("SELECT * FROM tune_media ORDER BY standardId, position, id")
  suspend fun all(): List<TuneMediaEntity>

  @Query("SELECT * FROM tune_media WHERE standardId = :standardId ORDER BY position, id")
  suspend fun forTune(standardId: Long): List<TuneMediaEntity>

  @Insert suspend fun insert(media: TuneMediaEntity): Long

  @Query("DELETE FROM tune_media WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("DELETE FROM tune_media")
  suspend fun deleteAll()
}

@Dao
interface TranscriptionDao {
  @Query("SELECT * FROM transcriptions")
  suspend fun all(): List<TranscriptionEntity>

  @Query("SELECT * FROM transcriptions WHERE archivedAt IS NULL")
  fun observeActive(): Flow<List<TranscriptionEntity>>

  @Query("SELECT * FROM transcriptions WHERE id = :id")
  suspend fun byId(id: Long): TranscriptionEntity?

  @Query("SELECT * FROM transcriptions WHERE slug = :slug")
  suspend fun bySlug(slug: String): TranscriptionEntity?

  @Insert suspend fun insert(t: TranscriptionEntity): Long

  @Update suspend fun update(ts: List<TranscriptionEntity>)

  @Query("SELECT * FROM transcription_state")
  fun observeStates(): Flow<List<TranscriptionStateEntity>>

  @Query("SELECT * FROM transcription_state")
  suspend fun states(): List<TranscriptionStateEntity>

  @Query("SELECT * FROM transcription_state WHERE transcriptionId = :id")
  suspend fun state(id: Long): TranscriptionStateEntity?

  @Upsert suspend fun upsertState(state: TranscriptionStateEntity)

  @Query("DELETE FROM transcription_state WHERE transcriptionId = :id")
  suspend fun deleteState(id: Long)

  @Query("DELETE FROM transcription_state")
  suspend fun deleteAllStates()
}

@Dao
interface ReviewEventDao {
  @Insert suspend fun insert(event: ReviewEventEntity): Long

  @Query("SELECT * FROM review_events ORDER BY reviewedAt, id")
  suspend fun all(): List<ReviewEventEntity>

  @Query("DELETE FROM review_events")
  suspend fun deleteAll()

  @Query("SELECT * FROM review_events WHERE itemKind = :kind AND itemId = :itemId ORDER BY reviewedAt, id")
  suspend fun forItem(kind: String, itemId: Long): List<ReviewEventEntity>

  @Query("SELECT COUNT(*) FROM review_events WHERE reviewedAt >= :since")
  suspend fun countSince(since: Long): Int

  @Query("SELECT reviewedAt FROM review_events")
  suspend fun times(): List<Long>
}

@Dao
interface MyChangesDao {
  @Query("SELECT * FROM my_changes ORDER BY name COLLATE NOCASE")
  fun observeAll(): Flow<List<MyChangesEntity>>

  @Query("SELECT * FROM my_changes")
  suspend fun all(): List<MyChangesEntity>

  @Insert suspend fun insert(changes: MyChangesEntity): Long

  @Update suspend fun update(changes: MyChangesEntity)

  @Query("DELETE FROM my_changes WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("DELETE FROM my_changes")
  suspend fun deleteAll()
}

@Dao
interface SessionDao {
  @Insert suspend fun insert(session: SessionEntity): Long

  @Insert suspend fun insertBlocks(blocks: List<SessionBlockEntity>)

  @Query(
    "UPDATE session_blocks SET outcome = :outcome, grade = :grade, elapsedMs = :elapsedMs, finishedAt = :finishedAt " +
      "WHERE sessionId = :sessionId AND position = :position",
  )
  suspend fun finishBlock(sessionId: Long, position: Int, outcome: BlockOutcome, grade: Grade?, elapsedMs: Long, finishedAt: Long)

  @Query("UPDATE sessions SET endedAt = :endedAt, completed = :completed WHERE id = :id")
  suspend fun end(id: Long, endedAt: Long, completed: Boolean)

  @Query("SELECT * FROM sessions ORDER BY startedAt DESC, id DESC")
  suspend fun all(): List<SessionEntity>

  @Query("SELECT * FROM session_blocks ORDER BY sessionId, position")
  suspend fun allBlocks(): List<SessionBlockEntity>

  @Query("DELETE FROM sessions")
  suspend fun deleteAll()

  /** Deletes a session; its blocks go with it. */
  @Query("DELETE FROM sessions WHERE id = :id")
  suspend fun delete(id: Long)
}

class Converters {
  private val json = kotlinx.serialization.json.Json

  @TypeConverter fun performersToString(performers: List<Performer>): String = json.encodeToString(performers)

  @TypeConverter fun stringToPerformers(value: String): List<Performer> = if (value.isBlank()) emptyList() else json.decodeFromString(value)

  @TypeConverter fun familiesToNullableString(families: List<Family>?): String? = families?.let(::familiesToString)

  @TypeConverter fun nullableStringToFamilies(value: String?): List<Family>? = value?.let(::stringToFamilies)

  @TypeConverter fun familiesToString(families: List<Family>): String = families.joinToString(",") { it.name }

  @TypeConverter
  fun stringToFamilies(value: String): List<Family> = if (value.isEmpty()) emptyList() else value.split(",").map { Family.valueOf(it) }
}

/** A schema change bumps the version and adds an AutoMigration (or a Migration) from the previous one, with a test. */
@Database(
  entities = [
    StandardEntity::class, ExerciseEntity::class, TuneStateEntity::class, ReviewEventEntity::class, TuneKnowledgeEntity::class,
    TuneOverrideEntity::class, PlaylistEntity::class, PlaylistTuneEntity::class, ExerciseOverrideEntity::class, TuneMediaEntity::class,
    TranscriptionEntity::class, TranscriptionStateEntity::class, SessionEntity::class, SessionBlockEntity::class,
    MyChangesEntity::class,
  ],
  version = 16,
  autoMigrations = [AutoMigration(from = 14, to = 15), AutoMigration(from = 15, to = 16)],
)
@TypeConverters(Converters::class)
abstract class WoodshedDatabase : RoomDatabase() {
  abstract fun standards(): StandardDao

  abstract fun exercises(): ExerciseDao

  abstract fun tuneStates(): TuneStateDao

  abstract fun reviewEvents(): ReviewEventDao

  abstract fun tuneKnowledge(): TuneKnowledgeDao

  abstract fun tuneOverrides(): TuneOverrideDao

  abstract fun playlists(): PlaylistDao

  abstract fun exerciseOverrides(): ExerciseOverrideDao

  abstract fun tuneMedia(): TuneMediaDao

  abstract fun transcriptions(): TranscriptionDao

  abstract fun sessions(): SessionDao

  abstract fun myChanges(): MyChangesDao

  companion object {
    /** Releases up to 0.3 shipped schema 13, from before the schema settled; those start over. */
    private val UNSUPPORTED = (1..13).toList().toIntArray()

    fun create(context: Context): WoodshedDatabase = Room.databaseBuilder(context, WoodshedDatabase::class.java, "woodshed.db")
      .fallbackToDestructiveMigrationFrom(true, *UNSUPPORTED)
      .build()
  }
}
