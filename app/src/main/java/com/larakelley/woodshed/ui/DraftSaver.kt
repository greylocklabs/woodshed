package com.larakelley.woodshed.ui

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.json.Json

/** Keeps a serializable value across activity recreation, such as a rotation or the phone folding, by way of JSON. */
inline fun <reified T> jsonSaver(): Saver<T, String> = Saver(save = { Json.encodeToString(it) }, restore = { Json.decodeFromString(it) })
