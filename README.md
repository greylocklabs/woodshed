<img src="assets/logo.svg" alt="" width="96">

# woodshed

Notes, data, and a build plan for **Woodshed**, an Android practice app for
horn players. Carried out of an abandoned attempt to build jazz practice tools
into [larakelley.com](https://github.com/lararosekelley/larakelley.com).

The app is at milestone M1 of `ANDROID_PLAN.md`: search and filter 2,522 tunes
with keys written for your transposition, pick tunes to learn, and practice them
as cards scheduled by spaced repetition, with a metronome on the stand. A dice
tab rolls a random card within constraints. Each tune can carry a comfort rating
and a checklist of keys you know it in. Every graded rep is logged.

## What is here

| Path | What |
| --- | --- |
| `DESIGN.md` | The decisions worth keeping, and the traps that cost time |
| `ANDROID_PLAN.md` | How this becomes the app: stack, domain model, build order |
| `data/standards.json` | 2,522 tunes: every title in the Hal Leonard Real Books 1-6 and Real Dixieland Book, with composer, year, bars, meter, form, style, key where known |
| `data/exercises.json` | 61 instrument-scoped practice exercises |
| `app/` | The Android app (Kotlin, Compose, Room); `data/` ships as its assets |
| `tools/cards.py` | Prints random tune x exercise x written-key cards for an instrument |
| `tools/logo.py` | Generates `assets/logo.svg` and the app's launcher icon from one shape list |

## Credit

`data/standards.json` started as factual metadata scraped from
[standardrepertoire.com](https://standardrepertoire.com), a jazz standards
reference by **David Miller**. Only factual fields were taken — composer, year,
bars, meter, form, style, common key — deliberately not lyrics and not chord
charts. If any of this ships, that credit ships with it. Corrections and the
verified additions were checked against [jazzstandards.com](https://www.jazzstandards.com),
Wikipedia and the iReal Pro jazz playlist. Bulk rows take key and style from the
iReal Pro main playlists and composer, meter and bars from the
[Jazz Chord Progressions Corpus](https://github.com/carey-bunks/Jazz-Chord-Progressions-Corpus)
(Bunks et al., ISMIR 2023), derived from Impro-Visor's Imaginary Book. Book
contents come from Hal Leonard's published song lists.

`data/exercises.json` is original, written for this project.

The look follows the Callie theme from the Callie calendar app: its plum and pink
palette, raised "sticker" buttons, and the Nunito and Fraunces typefaces, which
ship under the SIL Open Font License (`licenses/`).

## Building

Needs a JDK and the Android SDK (`android sdk` or Android Studio); Gradle fetches
the rest, including a Java 21 toolchain.

```
./gradlew :app:testDebugUnitTest   # unit, Room and Robolectric UI tests
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

## Trying the cards

```
python3 tools/cards.py -i tenor-sax -n 20 --detail
python3 -m unittest discover tools
```

Instruments are named like `alto-sax`, `trumpet`, `alto-flute`; `--help` lists
them. `--seed` repeats a draw and `--style Ballad` narrows the tunes.

## The earlier web version

It lives in ten closed-but-undeleted branches on the site repo,
`feat/practice-*`, stacked in this order:

```
scraper -> schema -> vocab -> library-pages -> seed
        -> recordings -> roller -> log -> e2e -> form-polish
```

Roughly 11,500 lines of Rust, Maud templates, and CSS, with 241 tests. It was
finished and green, not half-built: the reason it is not merged is that the
feature belongs in a phone app, not on a website.

Worth reading if you want it: `src/practice/roller.rs` (the session planner) and
`src/practice/keys.rs` (transposition) are the two files with real thinking in
them and no web coupling.

## Still open on the site repo

Issue #73, unrelated to practice: a production environment-detection bug, found
while working on this. Details are in the issue. Independent of whether any of
this ever gets built.

Three fixes were made to shared site CSS during this work and died with the
branches. Each stands alone if it is ever worth redoing:

- `header nav` has no `flex-wrap`, so with the admin-only links visible the page
  scrolls sideways at 768px.
- `.btn` has no `text-align`, so an `<a class="btn">` inherits its cell's
  alignment while a real `<button>` centres. They look different side by side in
  a right-aligned table cell.
- A `<select>` sizes to its widest option and ignores `line-height` in Chromium,
  so selects render shorter than text inputs and can overflow a narrow container.
