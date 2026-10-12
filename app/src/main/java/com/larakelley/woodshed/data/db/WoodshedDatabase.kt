package com.larakelley.woodshed.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.larakelley.woodshed.domain.Family
import kotlinx.coroutines.flow.Flow

@Dao
interface StandardDao {
  @Query("SELECT * FROM standards") suspend fun all(): List<StandardEntity>

  @Query("SELECT * FROM standards WHERE archivedAt IS NULL") fun observeActive(): Flow<List<StandardEntity>>

  @Query("SELECT * FROM standards WHERE slug = :slug") suspend fun bySlug(slug: String): StandardEntity?

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

class Converters {
  @TypeConverter fun familiesToString(families: List<Family>): String = families.joinToString(",") { it.name }

  @TypeConverter
  fun stringToFamilies(value: String): List<Family> =
    if (value.isEmpty()) emptyList() else value.split(",").map { Family.valueOf(it) }
}

@Database(entities = [StandardEntity::class, ExerciseEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class WoodshedDatabase : RoomDatabase() {
  abstract fun standards(): StandardDao

  abstract fun exercises(): ExerciseDao

  companion object {
    fun create(context: Context): WoodshedDatabase =
      Room.databaseBuilder(context, WoodshedDatabase::class.java, "woodshed.db").build()
  }
}
