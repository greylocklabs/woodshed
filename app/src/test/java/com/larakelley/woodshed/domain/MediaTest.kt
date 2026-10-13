package com.larakelley.woodshed.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaTest {
  private fun parse(url: String) = MediaLink.parse(url)!!

  @Test
  fun youtubeLinksInEveryCommonShapePlayInPlace() {
    for (url in listOf(
      "https://www.youtube.com/watch?v=jNQXAC9IVRw",
      "https://m.youtube.com/watch?v=jNQXAC9IVRw&feature=share",
      "https://youtu.be/jNQXAC9IVRw",
      "https://www.youtube.com/shorts/jNQXAC9IVRw",
      "https://music.youtube.com/watch?v=jNQXAC9IVRw",
      "https://www.youtube.com/embed/jNQXAC9IVRw",
    )) {
      val link = parse(url)
      assertEquals(url, MediaKind.YOUTUBE, link.kind)
      assertEquals(url, "jNQXAC9IVRw", link.videoId)
    }
  }

  @Test
  fun startTimesCarryIntoTheEmbed() {
    assertEquals(90, parse("https://youtu.be/jNQXAC9IVRw?t=90").startSeconds)
    assertEquals(90, parse("https://www.youtube.com/watch?v=jNQXAC9IVRw&t=1m30s").startSeconds)
    assertEquals("https://www.youtube-nocookie.com/embed/jNQXAC9IVRw?playsinline=1&start=90", parse("https://youtu.be/jNQXAC9IVRw?t=90s").embedUrl)
    assertEquals("https://www.youtube.com/watch?v=jNQXAC9IVRw&t=115s", parse("https://youtu.be/jNQXAC9IVRw?t=90").watch(115))
    assertEquals("https://vimeo.com/76979871#t=115s", parse("https://vimeo.com/76979871").watch(115))
    assertEquals("https://youtu.be/jNQXAC9IVRw", parse("https://youtu.be/jNQXAC9IVRw").watch(null))
    assertEquals(3723, MediaLink.parseTime("1h2m3s"))
    assertNull(MediaLink.parseTime("soon"))
  }

  @Test
  fun vimeoLinksPlayInPlace() {
    val link = parse("https://vimeo.com/76979871")
    assertEquals(MediaKind.VIMEO, link.kind)
    assertEquals("https://player.vimeo.com/video/76979871?playsinline=1", link.embedUrl)
    assertEquals("76979871", parse("https://vimeo.com/channels/staffpicks/76979871").videoId)
    assertEquals(65, parse("https://vimeo.com/76979871#t=65s").startSeconds)
  }

  @Test
  fun otherServicesOpenOutside() {
    assertEquals(MediaKind.SPOTIFY, parse("https://open.spotify.com/track/abc").kind)
    assertEquals(MediaKind.APPLE_MUSIC, parse("https://music.apple.com/us/album/x/1").kind)
    assertEquals(MediaKind.SOUNDCLOUD, parse("https://soundcloud.com/a/b").kind)
    assertEquals(MediaKind.BANDCAMP, parse("https://artist.bandcamp.com/track/x").kind)
    val other = parse("https://www.example.org/liner-notes")
    assertEquals(MediaKind.OTHER, other.kind)
    assertEquals("example.org", other.host)
    assertNull(other.embedUrl)
    assertEquals(MediaKind.OTHER, parse("https://www.youtube.com/@somechannel").kind)
  }

  @Test
  fun anythingButAWebAddressIsRefused() {
    for (bad in listOf("", "not a link", "ftp://example.com/x", "javascript:alert(1)", "file:///etc/passwd", "https://")) assertNull(bad, MediaLink.parse(bad))
  }
}
