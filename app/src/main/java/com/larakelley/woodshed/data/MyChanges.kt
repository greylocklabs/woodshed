package com.larakelley.woodshed.data

import com.larakelley.woodshed.data.db.MyChangesEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.Bar
import com.larakelley.woodshed.domain.bars
import com.larakelley.woodshed.domain.numerals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Changes the user wrote, relative to the key like the built-in forms. */
data class MyChanges(val id: Long, val name: String, val bars: List<Bar>)

/** Keeps the user's own changes. */
class MyChangesLibrary(private val db: WoodshedDatabase, private val now: () -> Long = System::currentTimeMillis) {
  val all: Flow<List<MyChanges>> = db.myChanges().observeAll().map { rows -> rows.mapNotNull(::read) }

  /** Saves [bars] as [name], adding new changes when [id] is null; returns the id. */
  suspend fun save(id: Long?, name: String, bars: List<Bar>): Long {
    val row = MyChangesEntity(id = id ?: 0, name = name.trim(), bars = numerals(bars), updatedAt = now())
    if (id == null) return db.myChanges().insert(row)
    db.myChanges().update(row)
    return id
  }

  suspend fun delete(id: Long) = db.myChanges().delete(id)

  /** A row that no longer parses, as a hand-edited backup could leave, is left out rather than failing the list. */
  private fun read(row: MyChangesEntity): MyChanges? = runCatching { MyChanges(row.id, row.name, bars(row.bars)) }.getOrNull()
}
