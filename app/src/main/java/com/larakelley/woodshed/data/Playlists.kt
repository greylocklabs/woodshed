package com.larakelley.woodshed.data

import androidx.room.withTransaction
import com.larakelley.woodshed.data.db.PlaylistEntity
import com.larakelley.woodshed.data.db.PlaylistTuneEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase

/** A playlist with its tunes in order, as the app shows it. */
data class Playlist(val id: Long, val name: String, val tuneIds: List<Long>)

/** Joins playlists to their entries, keeping each playlist's order. */
fun playlists(lists: List<PlaylistEntity>, entries: List<PlaylistTuneEntity>): List<Playlist> {
  val byList = entries.groupBy { it.playlistId }
  return lists.map { p -> Playlist(p.id, p.name, byList[p.id].orEmpty().sortedBy { it.position }.map { it.standardId }) }
}

/** Creates and edits the user's playlists. Positions are rewritten as 0, 1, 2... on every change, so order never has gaps. */
class PlaylistService(private val db: WoodshedDatabase, private val now: () -> Long = System::currentTimeMillis) {
  private val dao get() = db.playlists()

  suspend fun create(name: String): Long = dao.insert(PlaylistEntity(name = name.trim().ifEmpty { "New playlist" }, createdAt = now()))

  suspend fun rename(id: Long, name: String) {
    val p = dao.all().firstOrNull { it.id == id } ?: return
    dao.update(p.copy(name = name.trim().ifEmpty { p.name }))
  }

  /** Deletes the playlist and its entries; the tunes themselves are untouched. */
  suspend fun delete(id: Long) = dao.delete(id)

  /** Adds [tuneId] at the end; a tune already in the playlist stays where it is. */
  suspend fun add(id: Long, tuneId: Long) = db.withTransaction {
    val order = dao.entries(id).map { it.standardId }
    if (tuneId !in order) write(id, order + tuneId)
  }

  suspend fun remove(id: Long, tuneId: Long) = db.withTransaction { write(id, dao.entries(id).map { it.standardId } - tuneId) }

  /** Moves [tune] to [onto]'s place, shifting the tunes between. Ids rather than positions, since archived tunes are hidden. */
  suspend fun move(id: Long, tune: Long, onto: Long) = db.withTransaction {
    val order = dao.entries(id).map { it.standardId }.toMutableList()
    val from = order.indexOf(tune)
    val to = order.indexOf(onto)
    if (from < 0 || to < 0) return@withTransaction
    order.add(to, order.removeAt(from))
    write(id, order)
  }

  private suspend fun write(id: Long, order: List<Long>) {
    dao.clear(id)
    dao.upsertEntries(order.mapIndexed { i, tune -> PlaylistTuneEntity(id, tune, i) })
  }
}
