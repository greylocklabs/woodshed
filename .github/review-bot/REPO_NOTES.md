# Repo notes - Woodshed

Facts the review bot cannot infer from one pass over the diff. The bot's base prompt holds the
philosophy, finding categories, scoring and output format; this file only refines them for Woodshed.

## The stack

An Android app for jazz horn practice: Kotlin, Jetpack Compose with Material3, Room with exported
schemas and auto-migrations, kotlinx.serialization, and Robolectric for JVM tests. `data/` holds the
seed JSON and ships as app assets. `tools/` holds the logo script and the Play graphics renderer.

PRs come as a stack managed with git-stk, so a PR's base is often the branch below it, not `main`.

## What CI already covers, so never mention it

On every PR: the changelog check, Kotlin formatting, Android lint, the JVM unit, Robolectric and
screenshot test suite (including any Room migration tests), and commitlint.

## What CI cannot cover, so it is yours

- **User data must survive.** Re-seeding never touches user-owned tables (tune state, knowledge,
  review events, overrides, playlists) or rows the user created (`userCreated`, `user-` slugs). A
  migration or importer change that could drop, archive or overwrite them is a high-severity finding.
- **Concert versus written pitch.** Data is stored in concert pitch; the UI writes keys for the
  chosen transposition. A value stored written, or shown concert where written is expected, is wrong
  even when tests pass.
- **Privacy.** Problem reports and backups carry no personal details. Nothing sends the user's
  identity anywhere. Network or intent changes that could leak data deserve scrutiny.
- **Audio threads.** The metronome and tuner run on their own threads and publish through
  `StateFlow`; races, leaked threads or a microphone left on after leaving the Tuner tab are bugs.
- **Device-only behaviour.** File pickers, permissions and audio are not exercised by Robolectric.

## Style

Authored prose (comments, docs, UI text) uses plain ASCII punctuation. Comments justify a
non-obvious constraint in a sentence or two and describe current behaviour.
