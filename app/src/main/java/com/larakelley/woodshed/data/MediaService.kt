package com.larakelley.woodshed.data

import com.larakelley.woodshed.data.db.TuneMediaEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.domain.MediaLink

/** Attaches and removes the user's recording and video links on tunes. */
class MediaService(private val db: WoodshedDatabase, private val now: () -> Long = System::currentTimeMillis) {
  /** Adds [url] at the end of the tune's links; returns false when it is not a web address. */
  suspend fun add(tuneId: Long, url: String, title: String?): Boolean {
    val link = MediaLink.parse(url) ?: return false
    val next = (db.tuneMedia().forTune(tuneId).maxOfOrNull { it.position } ?: -1) + 1
    db.tuneMedia().insert(TuneMediaEntity(standardId = tuneId, url = link.url, title = title?.trim()?.ifEmpty { null }, position = next, createdAt = now()))
    return true
  }

  suspend fun remove(id: Long) = db.tuneMedia().delete(id)
}
