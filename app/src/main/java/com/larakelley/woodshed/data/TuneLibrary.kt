package com.larakelley.woodshed.data

import androidx.room.withTransaction
import com.larakelley.woodshed.data.db.StandardEntity
import com.larakelley.woodshed.data.db.TuneOverrideEntity
import com.larakelley.woodshed.data.db.WoodshedDatabase
import com.larakelley.woodshed.data.db.clearedFields
import com.larakelley.woodshed.data.seed.SeedValidator
import com.larakelley.woodshed.domain.Key
import com.larakelley.woodshed.domain.Tonality
import java.text.Normalizer
import kotlinx.serialization.Serializable

/** A tune as the editor fills it in. Blank text means unknown. */
@Serializable
data class TuneDraft(
  val title: String = "",
  val composer: String = "",
  val year: String = "",
  val form: String = "",
  val bars: String = "",
  val timeSignature: String? = "4/4",
  val style: String = "",
  /** Concert tonic, such as "Eb", or null for unknown. */
  val keyName: String? = null,
  val tonality: Tonality? = Tonality.MAJOR,
  val collection: String = "Standards",
) {
  /** What stops the draft being saved; empty when it is fine. */
  fun problems(): List<String> = buildList {
    if (title.isBlank()) add("A title is needed.")
    if (year.isNotBlank() && year.trim().toIntOrNull()?.takeIf { it in 1800..2100 } == null) add("Year should be a year, like 1959.")
    if (bars.isNotBlank() && bars.trim().toIntOrNull()?.takeIf { it > 0 } == null) add("Bars should be a whole number.")
    if (keyName != null && Key.pitchClassOf(keyName) == null) add("That key is not one the app knows.")
  }

  companion object {
    fun of(s: StandardEntity) = TuneDraft(
      title = s.title,
      composer = s.composer.orEmpty(),
      year = s.year?.toString().orEmpty(),
      form = s.form.orEmpty(),
      bars = s.bars?.toString().orEmpty(),
      timeSignature = s.timeSignature,
      style = s.style.orEmpty(),
      keyName = s.keyName,
      tonality = s.tonality,
      collection = s.collection,
    )
  }
}

private fun String.orNull() = trim().ifEmpty { null }

/** Adds, edits and removes tunes on the user's behalf. Seeded tunes are edited through overrides, never in place. */
class TuneLibrary(private val db: WoodshedDatabase, private val now: () -> Long = System::currentTimeMillis) {
  /** Adds a tune of the user's own and returns its slug. */
  suspend fun add(draft: TuneDraft): String = db.withTransaction {
    require(draft.problems().isEmpty()) { draft.problems().joinToString(" ") }
    val slug = freeSlug(draft.title)
    db.standards().insert(entity(draft, slug))
    slug
  }

  /** Saves [draft] over tune [id]: in place for the user's own tunes, as an override for seeded ones. */
  suspend fun edit(id: Long, draft: TuneDraft) = db.withTransaction {
    require(draft.problems().isEmpty()) { draft.problems().joinToString(" ") }
    val current = db.standards().byId(id) ?: return@withTransaction
    if (current.userCreated) {
      db.standards().update(listOf(entity(draft, current.slug).copy(id = id)))
      return@withTransaction
    }
    // Only fields that differ from the seed are stored, so a later seed correction still shows through the rest.
    val wanted = entity(draft, current.slug)
    fun <T> diff(mine: T?, seed: T?): T? = mine?.takeIf { it != seed }
    val blanked = mapOf(
      "composer" to (wanted.composer to current.composer),
      "year" to (wanted.year to current.year),
      "bars" to (wanted.bars to current.bars),
      "timeSignature" to (wanted.timeSignature to current.timeSignature),
      "form" to (wanted.form to current.form),
      "style" to (wanted.style to current.style),
      "key" to (wanted.keyName to current.keyName),
      "tonality" to (wanted.tonality to current.tonality),
    ).filterValues { (mine, seed) -> mine == null && seed != null }.keys
    val override = TuneOverrideEntity(
      standardId = id,
      title = diff(wanted.title, current.title),
      composer = diff(wanted.composer, current.composer),
      year = diff(wanted.year, current.year),
      bars = diff(wanted.bars, current.bars),
      timeSignature = diff(wanted.timeSignature, current.timeSignature),
      form = diff(wanted.form, current.form),
      style = diff(wanted.style, current.style),
      keyName = diff(wanted.keyName, current.keyName),
      keyPitchClass = diff(wanted.keyPitchClass, current.keyPitchClass),
      tonality = diff(wanted.tonality, current.tonality),
      collection = diff(wanted.collection, current.collection),
      updatedAt = now(),
      cleared = clearedFields(blanked),
    )
    if (override == TuneOverrideEntity(id, updatedAt = override.updatedAt)) db.tuneOverrides().delete(id) else db.tuneOverrides().upsert(override)
  }

  /** Drops the user's edits to a seeded tune. */
  suspend fun resetToOriginal(id: Long) = db.tuneOverrides().delete(id)

  /** Removes one of the user's own tunes from lists and practice. It is archived, so its review history still has a name. */
  suspend fun delete(id: Long) = db.withTransaction {
    val tune = db.standards().byId(id)?.takeIf { it.userCreated } ?: return@withTransaction
    db.tuneStates().delete(id)
    db.standards().update(listOf(tune.copy(archivedAt = now())))
  }

  private fun entity(d: TuneDraft, slug: String) = StandardEntity(
    slug = slug,
    title = d.title.trim(),
    composer = d.composer.orNull(),
    year = d.year.trim().toIntOrNull(),
    bars = d.bars.trim().toIntOrNull(),
    timeSignature = d.timeSignature,
    form = d.form.orNull(),
    style = d.style.orNull(),
    keyName = d.keyName,
    keyPitchClass = Key.pitchClassOf(d.keyName),
    tonality = d.tonality,
    collection = d.collection,
    verified = true,
    userCreated = true,
  )

  private suspend fun freeSlug(title: String): String {
    val base = SeedValidator.USER_SLUG_PREFIX + slugify(title).ifEmpty { "tune" }
    var slug = base
    var n = 2
    while (db.standards().bySlug(slug) != null) slug = "$base-${n++}"
    return slug
  }

  companion object {
    /** Lowercase ASCII words joined by hyphens, as the seed's slugs are. */
    fun slugify(title: String): String =
      Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        .replace("'", "").replace("&", " and ").replace(Regex("[^a-z0-9]+"), "-").trim('-')
  }
}
