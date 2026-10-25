package com.larakelley.woodshed.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A request to show something in another tab. */
sealed interface Destination {
  data class Tune(val id: Long) : Destination

  data class Solo(val id: Long) : Destination

  /** A main tab by name, such as a tool's, to go back to what it has playing. */
  data class Tab(val name: String) : Destination
}

/** Carries a [Destination] from one tab to the screen that shows it, which takes it once it has opened. */
class AppNavigation {
  var pending by mutableStateOf<Destination?>(null)
    private set

  fun go(to: Destination) {
    pending = to
  }

  fun take(): Destination? = pending.also { pending = null }
}

/** A solo as a tune's page lists it. */
data class SoloLink(val id: Long, val soloist: String, val detail: String)
