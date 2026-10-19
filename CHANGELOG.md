# Changelog

Notable changes per release. Notes collect under `## Unreleased`, which
`make release` renames to `## <version>`; that section is published as the
release's GitHub notes, and a release refuses to go out without one.

## 0.5.0

### Changed

- **library:** updating from 0.3 or earlier starts the library over: tune
  progress, review history, playlists, overrides and added tunes are cleared.
  Updates from 0.4 keep everything.
- **reports:** reports go to the shared Greylock Labs report service and are
  filed in its format, the same whether sent from the app or opened on GitHub.

## 0.4.0

### Added

- **practice:** a History tab of past sessions and a calendar of practice
  days. Sessions are recorded from this version on, and can be deleted.
- **practice:** Stats: time practised, streaks, repertoire, the most
  practised tunes and reps per key.
- **app:** a Help tab.
- **app:** an occasional prompt to rate Woodshed on Google Play after a
  session, and a Review button in About.
- **app:** reorder the tabs in Settings.

### Changed

- **app:** in light mode, primary buttons are deeper pink with white text.
- **practice:** session controls fit in one row, and the drone button names
  its note.

## 0.3.0

### Added

- **practice:** Shuffle, Replace and Drop on each planned block. A dropped
  block's minutes go to the rest, so the plan still fills the session.
- **practice:** go back a block, and control the metronome and drone without
  leaving the session.
- **practice:** drone exercises name the drone's note and play it.
- **random:** roll an exercise.
- **exercises:** filter by difficulty.

### Changed

- **app:** a new logo: a pink shed whose siding is a staff.
- **app:** reading in C, keys show without "Concert".
- **practice:** past five instruments, pick yours from a dropdown.
- **reports:** reports about the app need a description.

## 0.2.4

### Added

- **app:** a What's new dialog after each update, showing the changes since
  the version you last opened, and a link to the full changelog in About.

## 0.2.3

### Added

- **release:** Woodshed is on Google Play for testers, and each release is now
  uploaded there as well as attached to its GitHub release. Both are signed
  with the same key, so a copy installed from either one updates from the
  other.

## 0.2.2

### Added

- **settings:** About links to the privacy policy, the terms and
  [woodshedapp.org](https://woodshedapp.org), Woodshed's new website.
- **license:** Woodshed is now MIT licensed.

## 0.2.1

### Changed

- **release:** the app attached to each release is now a shrunk build signed
  with the release key, so later releases install over it. Copies installed
  from 0.1.0 or 0.2.0, which were debug builds, need uninstalling once first;
  back up from Settings beforehand and restore afterwards to keep your data.

## 0.2.0

### Added

- **reports:** reports are sent straight from the app and filed as GitHub
  issues by the woodshed-reports app, so reporting no longer needs a GitHub
  account. The dialog says where the report was filed, and can still open it
  on GitHub instead.

## 0.1.0

The first release: a practice app for jazz horn players, built around the
standards repertoire, spaced repetition and timed practice sessions.

### Added

- **tunes:** 2,522 standards from Real Books 1 to 6 and the Real Dixieland
  Book, each with composer, year, form and, for most, a researched key and
  major or minor tonality. Search, filters by status, style, collection,
  tonality and comfort, sorting, and an A-Z strip for the list.
- **tunes:** add, edit and delete your own tunes. Edits to seeded tunes are
  kept beside the original, survive updates to the tune list and can be reset.
- **tunes:** a wishlist, learning and repertoire lifecycle with SM-2 spaced
  repetition, a comfort rating, the keys you know each tune in, and practice
  round the circle of fourths.
- **instruments:** choose what you play, from 26 instruments. Keys are written
  for its transposition, and exercises are limited to ones that suit it.
- **practice:** plan a session by length, focus areas, instrument, tunes and a
  solo to transcribe, review and edit the plan, then step through it with a
  timer for each block and grading on tune blocks.
- **practice:** sessions keep running with the screen off or another app open,
  with a countdown notification offering Done or Skip, Pause and End, an alert
  when a block's time is up, and pickup after the system closes the app.
- **random:** roll a tune with an exercise and a key, or a solo, within limits
  such as a playlist, style, collection or keys you don't know yet.
- **exercises:** 61 exercises with details and the instruments each suits.
  Add, edit and delete your own, with seeded edits kept as for tunes.
- **playlists:** named, ordered lists of tunes, for the Tunes tab, sessions
  and rolls.
- **transcribe:** a Transcribe tab of solos to learn by ear, seeded with
  Patrick Bartley on Singin' In The Rain, Chet Baker on Tangerine and Miles
  Davis on Blues By Five. Each solo plays in place from just before it starts,
  with half and three-quarter speed, five-second skips, a jump to the solo's
  start and an A-B loop, or opens in YouTube or Vimeo. Solos link to their
  tunes, and you can add your own.
- **recordings:** attach recordings and videos to any tune. YouTube and Vimeo
  play in place; other links open outside the app.
- **tools:** a sample-accurate metronome with tap tempo, jazz tempo presets,
  meters and 2-and-4 clicks; a tuner; and a drone with an optional fifth, all
  named for your instrument and kept playing during a session in the
  background.
- **backup:** back up everything you have added and your practice history to a
  file, and restore it, through the system file picker.
- **reports:** report a problem with a tune, exercise, solo or the app, which
  opens a prefilled GitHub issue.
- **settings:** light, dark or system theme, the instruments you play, and an
  About page.
