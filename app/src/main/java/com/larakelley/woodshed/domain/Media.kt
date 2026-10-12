package com.larakelley.woodshed.domain

import java.net.URI

/** Where a link points, and whether the app can play it in place. */
enum class MediaKind(val label: String, val emoji: String, val inline: Boolean) {
  YOUTUBE("YouTube", "▶️", true),
  VIMEO("Vimeo", "▶️", true),
  SPOTIFY("Spotify", "🎧", false),
  APPLE_MUSIC("Apple Music", "🎧", false),
  SOUNDCLOUD("SoundCloud", "🎧", false),
  BANDCAMP("Bandcamp", "🎧", false),
  OTHER("Link", "🔗", false),
}

/** A recording or video link, parsed enough to embed it or to name where it opens. */
data class MediaLink(val url: String, val kind: MediaKind, val videoId: String? = null, val startSeconds: Int? = null) {
  /** The address of the embeddable player, for links the app can play in place. */
  val embedUrl: String?
    get() = when (kind) {
      MediaKind.YOUTUBE -> "https://www.youtube-nocookie.com/embed/$videoId?playsinline=1" + (startSeconds?.let { "&start=$it" } ?: "")
      MediaKind.VIMEO -> "https://player.vimeo.com/video/$videoId?playsinline=1" + (startSeconds?.let { "#t=${it}s" } ?: "")
      else -> null
    }

  /** The site's name, such as "open.spotify.com", for links shown as a row to open. */
  val host: String
    get() = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

  companion object {
    private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val VIMEO_ID = Regex("^\\d{5,12}$")

    /** Parses [raw], or returns null when it is not an http or https address. */
    fun parse(raw: String): MediaLink? {
      val text = raw.trim()
      val uri = runCatching { URI(text) }.getOrNull() ?: return null
      if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
      val host = uri.host.lowercase().removePrefix("www.").removePrefix("m.")
      val path = uri.rawPath.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
      val query = queryOf(uri.rawQuery)
      val start = parseTime(query["t"] ?: query["start"])
      when (host) {
        "youtube.com", "music.youtube.com", "youtube-nocookie.com" -> {
          val id = when (path.firstOrNull()) {
            "watch" -> query["v"]
            "shorts", "embed", "live", "v" -> path.getOrNull(1)
            else -> null
          }
          if (id != null && YOUTUBE_ID.matches(id)) return MediaLink(text, MediaKind.YOUTUBE, id, start)
        }
        "youtu.be" -> path.firstOrNull()?.takeIf { YOUTUBE_ID.matches(it) }?.let { return MediaLink(text, MediaKind.YOUTUBE, it, start) }
        "vimeo.com", "player.vimeo.com" -> {
          val id = path.lastOrNull { VIMEO_ID.matches(it) }
          val fragmentStart = parseTime(uri.rawFragment?.removePrefix("t="))
          if (id != null) return MediaLink(text, MediaKind.VIMEO, id, start ?: fragmentStart)
        }
      }
      val kind = when {
        host.endsWith("spotify.com") -> MediaKind.SPOTIFY
        host == "music.apple.com" -> MediaKind.APPLE_MUSIC
        host.endsWith("soundcloud.com") -> MediaKind.SOUNDCLOUD
        host.endsWith("bandcamp.com") -> MediaKind.BANDCAMP
        else -> MediaKind.OTHER
      }
      return MediaLink(text, kind)
    }

    private fun queryOf(raw: String?): Map<String, String> =
      raw.orEmpty().split('&').filter { '=' in it }.associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    /** "90", "90s", "1m30s" or "1h2m3s" as seconds. */
    fun parseTime(raw: String?): Int? {
      val s = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
      s.toIntOrNull()?.let { return it }
      val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$").matchEntire(s) ?: return null
      val (h, min, sec) = m.destructured
      if (h.isEmpty() && min.isEmpty() && sec.isEmpty()) return null
      return (h.toIntOrNull() ?: 0) * 3600 + (min.toIntOrNull() ?: 0) * 60 + (sec.toIntOrNull() ?: 0)
    }
  }
}
