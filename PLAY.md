# Google Play

What to enter in Play Console for Woodshed, kept here so the answers stay true to the app. The store listing's
text and graphics live in `app/src/main/play/`, laid out for Gradle Play Publisher; the phone screenshots come
from the screenshot tests (`make record-screenshots` rewrites them), and `tools/play/render.sh` renders the icon
and feature graphic.

Update this file when the app starts doing something these answers do not cover, such as collecting data,
showing ads or adding a permission.

## App setup

| Field | Answer |
| --- | --- |
| App name | Woodshed |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free (permanent) |
| Category | Music & Audio |
| Tags | Music, Education |
| Contact email | hello@woodshedapp.org |
| Website | https://woodshedapp.org |
| Privacy policy | https://woodshedapp.org/privacy.html |

## App content

**App access:** all functionality is available without special access. There is no sign-in.

**Ads:** no, the app contains no ads.

**Content rating** (the IARC questionnaire): category *All Other App Types* (it is a practice tool, not a game
or social app). Answer no to violence, sexual content, profanity, drugs, gambling and fear. Users cannot
interact with or share content with other users in the app, and it does not share the user's location. It has
no purchases. Expect a rating of Everyone / PEGI 3.

**Target audience:** 13 and older (the 13-15, 16-17 and 18+ groups). Choosing under 13 brings the Families
policy's extra requirements, which the app is not built for. The app is not designed for children.

**News app:** no. **Government app:** no. **Financial features:** none. **Health:** none.

## Data safety

Woodshed keeps everything on the phone. Only a problem report the user chooses to send leaves it, through the
report service (a Cloudflare Worker) to a public GitHub issue.

- **Does the app collect or share user data?** Yes, because of problem reports.
- **Is all collected data encrypted in transit?** Yes (HTTPS).
- **Can users request deletion?** Yes: ask at hello@woodshedapp.org, or close the issue on GitHub.

Data types to declare:

| Data type | Collected | Shared | Optional | Purpose |
| --- | --- | --- | --- | --- |
| App activity: other user-generated content (the report's note) | Yes | No | Yes | App functionality (support) |
| App info and performance: other app performance data (app and Android versions in a report) | Yes | No | Yes | App functionality (support) |

Not shared, because each report is a transfer the user starts and expects, which Play excludes from sharing.
Not collected: location, personal info, contacts, files, photos, audio (the tuner reads the microphone on the
phone and keeps nothing), health, financial info, messages, identifiers and device IDs. The IP address the
report service sees for rate limiting is processed ephemerally and not declared.

To check before submitting: whether Play treats YouTube and Vimeo videos loaded in a WebView, after the user
taps play, as data collection by the app. As written, the app sends nothing to them itself; the services
receive what any website they serve receives.

## Permissions and declarations

- **Foreground service (special use):** Play asks what it is for. Answer: "A practice session timer. While a
  session runs, the service shows the current block and its time left in a notification with Done, Pause and
  End, alerts when a block's time is up, and keeps the metronome and drone playing with the screen off. It
  runs only while the user has a session going and stops when it ends." Play may ask for a short video: record
  starting a session, locking the phone, and the notification counting down.
- **Microphone (RECORD_AUDIO):** no declaration form; the store listing and privacy policy say the tuner uses
  it on the device only.
- **Notifications:** no declaration.

## Payments policy

The About screen's Ko-fi button asks for a voluntary tip. Google Play's Payments policy generally requires
Play's billing for in-app payments for digital goods and services, and may treat tips to the developer as one.
Decide before the first production release: remove the button from the Play build, replace it with Play billing
tips, or confirm with Google that the link is allowed. The GitHub build can keep it either way.

## Releases

New personal developer accounts must run a closed test with at least 12 testers opted in for 14 days before
they can apply for production. The first upload to a new app must be made by hand in Play Console; later
uploads can come from the release workflow.

Each upload's "What's new" is the version's CHANGELOG.md section as plain text, cut to Play's 500 characters;
`scripts/play-notes.sh <version>` prints it.
