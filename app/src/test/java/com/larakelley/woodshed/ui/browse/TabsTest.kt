package com.larakelley.woodshed.ui.browse

import org.junit.Assert.assertEquals
import org.junit.Test

class TabsTest {
  @Test
  fun savedOrderComesFirstAndNewTabsKeepTheirDefaultPlaces() {
    val available = listOf("Practice", "History", "Tunes", "Help")
    assertEquals(listOf("Tunes", "Practice", "History", "Help"), orderTabs(available, listOf("Tunes", "Practice")))
    // A saved tab this build does not show is ignored.
    assertEquals(available, orderTabs(available, listOf("Gone")))
    assertEquals(available, orderTabs(available, emptyList()))
  }

  @Test
  fun movingATabStaysWithinTheList() {
    val order = listOf("A", "B", "C")
    assertEquals(listOf("B", "A", "C"), moveTab(order, "B", -1))
    assertEquals(listOf("A", "C", "B"), moveTab(order, "B", 1))
    assertEquals(order, moveTab(order, "A", -1))
    assertEquals(order, moveTab(order, "C", 1))
    assertEquals(order, moveTab(order, "Z", 1))
  }
}
