<img src="assets/logo.svg" alt="" width="96">

# woodshed

Notes, data, and a build plan for **Woodshed**, an Android practice app for
horn players. Carried out of an abandoned attempt to build jazz practice tools
into [larakelley.com](https://github.com/lararosekelley/larakelley.com).

The app is at milestone M1 of `ANDROID_PLAN.md`: search and filter 2,522 tunes
with keys written for your transposition, pick tunes to learn, and plan sessions
by length, focus and tunes, with spaced-repetition grading and a metronome on the
stand. The Random tab rolls a card within constraints. Each tune can carry a
comfort rating and a checklist of keys you know it in. Every graded rep is
logged, and Settings saves and restores a backup file through the system file
picker, so it can live in Google Drive, Dropbox or local storage.

## What is here

| Path | What |
| --- | --- |
| `DESIGN.md` | The decisions worth keeping, and the traps that cost time |
| `ANDROID_PLAN.md` | How this becomes the app: stack, domain model, build order |
| `data/standards.json` | 2,522 tunes: every title in the Hal Leonard Real Books 1-6 and Real Dixieland Book, with composer, year, bars, meter, form, style, key where known |
| `data/exercises.json` | 61 instrument-scoped practice exercises |
| `app/` | The Android app (Kotlin, Compose, Room); `data/` ships as its assets |
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
make build               # app/build/outputs/apk/debug/app-debug.apk
make test                # unit, Room and Robolectric tests, comparing screenshots with app/src/test/screenshots
make record-screenshots  # rewrites the screenshots after a deliberate design change
make e2e                 # end-to-end tests on an emulator Gradle downloads (*)
make format              # formats Kotlin with ktlint
make lint                # formatting, Android lint (any warning fails) and the changelog
make check               # CI's lint, test and worker jobs
```

`make help` lists every task; each wraps a Gradle command you can also run directly. Debug builds install as
their own app, "Woodshed (dev)" (`com.larakelley.woodshed.debug`), beside an installed release.

(*) If the emulator crashes on start, as it does on some Linux hosts with its default software renderer, use
`make e2e EMULATOR_GPU=host`. Never run `connectedAndroidTest` against a phone with real practice data: it
reinstalls the app.

CI runs formatting, lint, the unit and screenshot tests, and commitlint on every PR. The end-to-end tests run
on PRs into main, nightly and on demand. Dependabot opens weekly update PRs.

## Website

`www/` is [woodshedapp.org](https://woodshedapp.org): the landing page, privacy policy and terms, styled like
the app. `make www` serves it locally at http://localhost:8000. The pages workflow publishes it to GitHub Pages
whenever `www/` changes on main. Its screenshots are
copies of the screenshot tests' images in `app/src/test/screenshots`. DNS for the domain, including Resend's
mail records, is in Route 53.

## Google Play

`PLAY.md` holds what to enter in Play Console, from the content rating to the Data Safety form, and the open
question about the Ko-fi button. The store listing's text and graphics are in `app/src/main/play/`: the phone
screenshots are the screenshot tests' `play` set, and `tools/play/render.sh` renders the icon and feature
graphic. Tagged releases also build a bundle, uploaded to Play's internal testing track once the
`PLAY_SERVICE_ACCOUNT_JSON` secret exists, with the version's changelog section as its notes.

## Report service

`worker/` is a Cloudflare Worker that files problem reports from the app as GitHub issues, signed in as the
woodshed-reports GitHub App. It runs at `https://woodshed-reports.lararosekelley.workers.dev/report`.

```
make worker-test    # type-check and test it
make worker-deploy  # deploy it; needs `npx wrangler login` with the personal Cloudflare account
```

The app's private key is a Worker secret, stored in the PKCS#8 form Web Crypto needs:
`openssl pkcs8 -topk8 -nocrypt -in <key>.pem | npx wrangler secret put GITHUB_PRIVATE_KEY` from `worker/`.

## Releasing

The version lives in `VERSION`, and Play's version code is derived from it. To release:

1. Add a `## <version>` section to `CHANGELOG.md` for the version you are about to cut, and merge it to main.
2. On an up-to-date main, run `make release PART=patch` (or `minor`, `major`). It bumps `VERSION`, commits,
   tags `v<version>` and pushes. `make release PART=current` tags the version already in `VERSION`, and
   `DRY_RUN=1` checks everything without changing anything.
3. The tag starts the release workflow, which publishes a GitHub release with that changelog section as its
   notes and the app attached, signed with the release key so each release installs over the last.

Release builds are shrunk with R8. `make e2e-release` runs the end-to-end tests against the shrunk build, as CI
does nightly and on PRs into main.

### The release key

The release key is a PKCS#12 keystore (alias `woodshed`) whose password is also its key password. The release
workflow reads it from the `WOODSHED_KEYSTORE_BASE64` and `WOODSHED_KEYSTORE_PASSWORD` repository secrets. To
build a signed release locally:

```
WOODSHED_KEYSTORE=/path/to/release.p12 WOODSHED_KEYSTORE_PASSWORD=... ./gradlew :app:assembleRelease
```

Keep a backup of the keystore and its password somewhere safe, such as a password manager. Without them, no
later release can update an installed copy.

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
