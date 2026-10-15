<img src="assets/logo.svg" alt="" width="96">

# woodshed

**Woodshed** is an Android practice app for jazz musicians: the standards repertoire, spaced repetition and
timed practice sessions, with a metronome, tuner and drone on the stand. It is free and MIT licensed, with no
account, ads or analytics. The website is [woodshedapp.org](https://woodshedapp.org).

- **Tunes:** 2,522 standards from the Hal Leonard Real Books 1-6 and the Real Dixieland Book, searchable and
  filterable, with keys written for any of 26 instruments. Add your own tunes, edit seeded ones, and keep
  playlists.
- **Spaced repetition:** tunes move from the wishlist through learning to repertoire and come back when due,
  each with a key and an exercise. Every graded rep is logged.
- **Sessions:** pick a length, an instrument, focus areas, tunes and a solo; review the plan, then step
  through it with a timer that keeps running with the screen off.
- **Transcribe:** solos to learn by ear, played at half or three-quarter speed with an A-B loop.
- **Tools:** metronome with tap tempo and 2-and-4 clicks, a tuner and a drone, all named for your instrument.
- **Dice:** a random tune, exercise and key, or a solo.
- **Backup and reports:** everything stays on the phone, backed up to a file of your choosing. Problem reports
  go through the report service below, without a GitHub account.

Install it from the [latest release](https://github.com/lararosekelley/woodshed/releases/latest) APK. Google
Play is in testing.

## What is here

| Path | What |
| --- | --- |
| `app/` | The Android app (Kotlin, Compose, Room) |
| `data/` | The seed, shipped as the app's assets: `standards.json` (2,522 tunes with composer, year, bars, meter, form, style and key where known), `exercises.json` (61 practice exercises) and `transcriptions.json` (seeded solos) |
| `CHANGELOG.md` | Notable changes per release |
| `PLAY.md` | What to enter in Google Play Console |
| `worker/` | The report service, a Cloudflare Worker |
| `www/` | The website |
| `scripts/` | Release, version and changelog scripts behind the Makefile |
| `tools/logo.py` | Generates `assets/logo.svg`, the website logo and the launcher, themed and notification icons from one shape list |
| `tools/play/` | Renders the Play icon and feature graphic |

## Design notes

The rules that are easy to get wrong:

- **Transposition.** Tunes are stored in concert pitch and practice history in written keys, so a ii-V-I in
  written G counts toward G on any instrument. Written key is concert key plus the instrument's offset (Bb +2,
  Eb +9, F +7, G +5), mod 12. A wrong offset makes every displayed key silently wrong.
- **Key spelling.** Major keys prefer flats and minor keys prefer sharps, so C# minor never shows as Db minor.
  A tune's key is its tonic plus a separate tonality; an unknown key stays null and shows as unknown rather
  than guessed. Practising round the keys walks the circle of fourths.
- **Cards and scheduling.** A tune is scheduled whole with SM-2 behind a `Scheduler` interface. Each time it
  comes up it is paired with the tune exercise used longest ago on it, in its usual key or the next one round
  the circle. Only learning and repertoire tunes are due; a tune becomes repertoire at a 21-day interval. Every
  rep is logged with the scheduler's inputs and outputs, so history can be replayed under another algorithm.
- **Sessions.** Five phases (warm-up, fundamentals, vocabulary, repertoire, cool-down) map the exercise
  categories. The phase shares are a guess that has not been checked against real practice.
- **Seeding.** The seed is upserted by natural key (tune slug, exercise name) and never deleted and reinserted,
  since history points at rows by id. Anything dropped is archived. The seed owns the metadata; lifecycle,
  notes and edits belong to the user and are never touched. User tunes use slugs starting `user-`.
- **Data.** Every field but title, slug and collection may be absent, and absent is kept distinct from empty.
  `verified` tunes were sourced field by field; the rest were filled from iReal Pro and the corpus below. An
  exercise with no `families` applies to every instrument.

Not built yet: a vocabulary deck of licks practised in all twelve keys, stats and reminders, Bluetooth pedal
support, and seeded classic recordings for each standard.

## Credit

`data/standards.json` started as factual metadata scraped from
[standardrepertoire.com](https://standardrepertoire.com), a jazz standards
reference by **David Miller**. Only factual fields were taken: composer, year,
bars, meter, form, style, common key, and deliberately not lyrics and not chord
charts, and the app credits it in About. Corrections and the
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

`PLAY.md` holds what to enter in Play Console, from the content rating to the Data Safety form, and the decision
to keep the Ko-fi button. The store listing's text and graphics are in `app/src/main/play/`: the phone
screenshots are the screenshot tests' `play` set, and `tools/play/render.sh` renders the icon and feature
graphic. Play signs with the same key as the GitHub releases, so either install updates from the other.

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
3. The tag starts the release workflow. It publishes a GitHub release with that changelog section as its notes
   and the app attached, signed with the release key so each release installs over the last. It then uploads
   the bundle to Play's internal testing track, with the section as plain text for its notes
   (`scripts/play-notes.sh <version>` prints them), using the service account in the
   `PLAY_SERVICE_ACCOUNT_JSON` secret.

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

## History

Woodshed started as jazz practice tools on [larakelley.com](https://github.com/lararosekelley/larakelley.com),
finished but never merged because the feature belongs in a phone app. That version lives in the closed
`feat/practice-*` branches on the site repo; its session planner (`src/practice/roller.rs`) and transposition
(`src/practice/keys.rs`) are the parts worth reading.
