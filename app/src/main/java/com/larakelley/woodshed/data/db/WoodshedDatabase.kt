package com.larakelley.woodshed.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.AutoMigrationSpec
import com.larakelley.woodshed.domain.Family
import kotlinx.coroutines.flow.Flow

@Dao
interface StandardDao {
  @Query("SELECT * FROM standards") suspend fun all(): List<StandardEntity>

  @Query("SELECT * FROM standards WHERE archivedAt IS NULL") fun observeActive(): Flow<List<StandardEntity>>

  @Query("SELECT * FROM standards WHERE slug = :slug") suspend fun bySlug(slug: String): StandardEntity?

  @Query("SELECT * FROM standards WHERE id = :id") suspend fun byId(id: Long): StandardEntity?

  @Insert suspend fun insert(standard: StandardEntity): Long

  @Update suspend fun update(standards: List<StandardEntity>)
}

@Dao
interface ExerciseDao {
  @Query("SELECT * FROM exercises") suspend fun all(): List<ExerciseEntity>

  @Query("SELECT * FROM exercises WHERE archivedAt IS NULL") fun observeActive(): Flow<List<ExerciseEntity>>

  @Query("SELECT * FROM exercises WHERE name = :name") suspend fun byName(name: String): ExerciseEntity?

  @Insert suspend fun insert(exercise: ExerciseEntity): Long

  @Update suspend fun update(exercises: List<ExerciseEntity>)
}

@Dao
interface TuneStateDao {
  @Query("SELECT * FROM tune_state") fun observeAll(): Flow<List<TuneStateEntity>>

  @Query("SELECT * FROM tune_state") suspend fun all(): List<TuneStateEntity>

  @Query("SELECT * FROM tune_state WHERE standardId = :standardId") suspend fun get(standardId: Long): TuneStateEntity?

  @Upsert suspend fun upsert(state: TuneStateEntity)

  @Query("DELETE FROM tune_state WHERE standardId = :standardId") suspend fun delete(standardId: Long)

  @Query(
    "SELECT * FROM tune_state WHERE lifecycle IN ('LEARNING', 'REPERTOIRE') AND dueAt IS NOT NULL AND dueAt <= :now ORDER BY dueAt"
  )
  suspend fun due(now: Long): List<TuneStateEntity>

  @Query("SELECT * FROM tune_state WHERE lifecycle = 'WISHLIST' ORDER BY wishlistedAt, standardId")
  suspend fun wishlist(): List<TuneStateEntity>

  @Query("SELECT COUNT(*) FROM tune_state WHERE introducedAt >= :since") suspend fun introducedSince(since: Long): Int
}

@Dao
interface TuneKnowledgeDao {
  @Query("SELECT * FROM tune_knowledge") fun observeAll(): Flow<List<TuneKnowledgeEntity>>

  @Query("SELECT * FROM tune_knowledge") suspend fun all(): List<TuneKnowledgeEntity>

  @Query("SELECT * FROM tune_knowledge WHERE standardId = :standardId") suspend fun get(standardId: Long): TuneKnowledgeEntity?

  @Upsert suspend fun upsert(knowledge: TuneKnowledgeEntity)
}

@Dao
interface ReviewEventDao {
  @Insert suspend fun insert(event: ReviewEventEntity): Long

  @Query("SELECT * FROM review_events WHERE itemKind = :kind AND itemId = :itemId ORDER BY reviewedAt, id")
  suspend fun forItem(kind: String, itemId: Long): List<ReviewEventEntity>

  @Query("SELECT COUNT(*) FROM review_events WHERE reviewedAt >= :since") suspend fun countSince(since: Long): Int
}

class Converters {
  @TypeConverter fun familiesToString(families: List<Family>): String = families.joinToString(",") { it.name }

  @TypeConverter
  fun stringToFamilies(value: String): List<Family> =
    if (value.isEmpty()) emptyList() else value.split(",").map { Family.valueOf(it) }
}

@Database(
  entities = [StandardEntity::class, ExerciseEntity::class, TuneStateEntity::class, ReviewEventEntity::class, TuneKnowledgeEntity::class],
  version = 6,
  autoMigrations = [
    AutoMigration(from = 1, to = 2),
    AutoMigration(from = 2, to = 3),
    AutoMigration(from = 3, to = 4),
    AutoMigration(from = 4, to = 5, spec = WoodshedDatabase.DropBooks::class),
    AutoMigration(from = 5, to = 6, spec = WoodshedDatabase.SplitKey::class),
  ],
)
@TypeConverters(Converters::class)
abstract class WoodshedDatabase : RoomDatabase() {
  abstract fun standards(): StandardDao

  abstract fun exercises(): ExerciseDao

  abstract fun tuneStates(): TuneStateDao

  abstract fun reviewEvents(): ReviewEventDao

  abstract fun tuneKnowledge(): TuneKnowledgeDao

  /** Version 4 listed every fake book a tune is printed in; version 5 keeps only its collection. */
  @DeleteColumn(tableName = "standards", columnName = "books")
  class DropBooks : AutoMigrationSpec

  /** Version 6 stores the tonic and the tonality separately; re-seeding fills both. */
  @DeleteColumn.Entries(DeleteColumn(tableName = "standards", columnName = "commonKeyRaw"), DeleteColumn(tableName = "standards", columnName = "commonKeyMinor"))
  @RenameColumn(tableName = "standards", fromColumnName = "commonKeyPitchClass", toColumnName = "keyPitchClass")
  class SplitKey : AutoMigrationSpec

  companion object {
    fun create(context: Context): WoodshedDatabase =
      Room.databaseBuilder(context, WoodshedDatabase::class.java, "woodshed.db").build()
  }
}
