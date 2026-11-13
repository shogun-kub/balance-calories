# AGENTS.md

Operating instructions for any agent working in `E:\Projects\BalanceCalories`.

## What this repository is

An Android application, built by and for two people who want one honest number: **energy in minus energy
out**. It reads body data and food, reads activity from the phone's Health Connect store, and shows a daily
balance plus a weekly trend.

Two outputs, everything else supports them:

1. **A working app** — Kotlin, Jetpack Compose, local-first, installable on both phones.
2. **A written record** of why it works that way — `docs/`, so a decision can be re-read in six months
   without reverse-engineering the code.

## Current state

**M1–M5 and M7.1–M7.7 complete; M6 half done.** `:core` (176 JVM tests) and `:data` (schema **v7**, 11 DAOs,
13 repositories) build green, and `:app` has the whole loop: Today with the macro gauge, one card per meal (the set is the person's), burned and balance;
the food side (two tabs: products and recipes, search that falls back to Open Food Facts by itself, scanning, logging, recipes); History with the adaptive TDEE, the recap,
the intake/burn chart, a weight trend and a projection; a weight tab with entry and chart; settings with
units, day start, language and macro norms (four presets or one's own grams) and the meal editor; and backup / restore / CSV.

176 :core + 46 :data + 33 :app JVM tests and **126 device tests** (122 :data, 4 :app) pass. The app
navigates by **four tabs** (`Сегодня` · `Еда` · `История` · `Вес`), follows the phone's dark setting, and
prints each logged portion's protein, fat and carbs with the macro it is proportionally richest in marked
in that macro's colour, and a product can carry a **barcode** that a scan resolves without the network.
The launcher icon is a **vector adaptive icon** — a balance beam with a slice of pie
on one side and a steel-blue dumbbell on the other. Strings are **386/386** (374 `<string>` + 12
`<plurals>` per language) and lint is down to five reviewed warnings.

Next: the rest of **M6** — device matrix on both phones, TalkBack, release signing, README — then the
release.

## Where everything lives

`AGENTS.md` is loaded automatically; the files below are not, so open the one you need **before** working.

| File | What it holds | Read it when |
| --- | --- | --- |
| `docs/01-product.md` | the product: what it is for, who uses it | changing behaviour a person sees |
| `docs/02-research.md` | measured API and device behaviour (Health Connect, OpenFoodFacts, cooking maths) | touching those integrations |
| `docs/03-architecture.md` | module layout, contracts, and **every ADR** in §13 | making a decision worth re-reading |
| `docs/04-plan.md` | the milestone plan, what is done and what it cost | choosing what to do next |
| `docs/05-open-questions.md` | open questions, deferred decisions | something looks unresolved |
| `docs/06-milestones.md` | the diary: what each milestone changed, with the numbers of the day | you want to know *when* and *what it took* |
| `docs/07-pitfalls.md` | **traps** (`P-nn`) and **judgement calls** (`J-nn`) | before and after anything that bites |
| `docs/08-environment.md` | this machine, its devices, build and test commands, instrumentation recipe | running anything |

## Ten traps that cost hours here

One line each; the detail, the symptom and the fix are in `docs/07-pitfalls.md`.

| Trap | One line |
| --- | --- |
| **P-01–P-03** | AGP 9 has Kotlin built in, `compileSdk` is pinned at 36 with matching dependency versions, and Kotlin is held at 2.3.20 for KSP |
| **P-04, P-15** | after a schema bump build `:data:assembleDebugAndroidTest` **twice**, and rebuild `:app` before believing any device test |
| **P-05** | an `<adaptive-icon>` outside `-v26` is not resolved at all — `mipmap-anydpi-v26` stays |
| **P-07** | `Get-Content`/`>` turns a pulled SQLite file into UTF-16; `-replace` with `\n` silently matches nothing in a CRLF file |
| **P-08, P-09** | every adb call runs on port **5039**, and the Honor reverts to landscape after `install -r` |
| **P-12–P-14** | Health Connect needs the `health.READ_*` permissions declared and, on Android 14, a `VIEW_PERMISSION_USAGE` activity; MagicOS kills the permission dialog |
| **P-18** | a format string and its call site can drift apart and pass every test — launch the screen |
| **P-19** | at a 200 % font scale a squeezed `Text` breaks inside words: check `uiautomator dump` for tall, narrow nodes |
| **P-23** | `fillMaxWidth()` inside `horizontalScroll` collapses to zero, and `rememberScrollState(initial = …)` cannot open at the end |
| **P-29** | a chart needs a seeded history; seed a **copy** of the database and leave no pulled file in the tree |
| **P-40** | `connectedDebugAndroidTest` ignores `ANDROID_ADB_SERVER_PORT`: move adb to 5037 for an instrumentation run (P-08 is the opposite for plain adb) |
| **P-41** | never pipe a database into `adb shell`: push it and copy it on the device, then check the size |

## Naming and the visible language

- **Display name:** `Balance Calories` (English) and `Баланс Калорий` (Russian), both from `app_name`. The
  repository, Kotlin packages and `applicationId` stay `BalanceCalories` / `com.balancecalories`; only the
  label the user sees is renamed. ADR-010 and ADR-011 in `docs/03-architecture.md` §13.
- **The UI follows the system locale**, and Android's per-app locale overrides it from Settings.
- **`StringResourceParityTest` is the guard** (`.\gradlew.bat :app:testDebugUnitTest`): key sets, format
  arguments, plural quantity sets and the two app names. It runs on the JVM — run it every time.
- **Previewing English on a Russian phone** without touching system settings: the `cmd locale
  set-app-locales` recipe is in `docs/08-environment.md`.

## Directory contract

| Path | Role | Rule |
| --- | --- | --- |
| `docs/` | Design and findings | Numbered Markdown, tracked in git. |
| `app/`, `core/`, `data/` | Gradle modules | `:core` is pure Kotlin, zero Android imports. `:data` owns Room, Health Connect, OpenFoodFacts, Gadgetbridge. `:app` owns Compose UI. |
| `tools/` | Build-time generators | Starter-ingredient pack generator, icon geometry, fixture capture. Runs on the host, never in the app. |
| `fixtures/` | Test inputs | Sanitised OpenFoodFacts payloads, synthetic Health Connect dumps, barcode images. **No real personal data.** |
| `gradle/libs.versions.toml` | Version truth | Every dependency version lives here and nowhere else. |
| `build/`, `.gradle/`, `local.properties`, `*.keystore`, `*.apk`, `*.aab` | Build output and secrets | Never tracked. |
| `tmp/` | Scratch: screenshots, previews, pulled databases | Git-ignored. Nothing here is evidence, and nothing here is safe to commit. |
| `E:\Projects\BalanceCalories-acl-recovery\` | ACL rollback record | Outside the repo. Leave in place: it documents the permission repair that made this workspace writable. |

## Testing on a phone that holds real data

Two profiles live on the Honor: **`Test` (id 3) is the fixture**, and the owner's own profile is his life.
The app remembers which profile is selected, so anything logged after a fresh start lands in whichever one
was selected last.

- **Switch to `Test` before logging, editing or deleting anything**, and **switch back to the owner's
  profile when the run is over**: Settings → Профили → tap the card.
- **Never touch the owner's profile** — no delete, archive, weight, entry or target, and no `pm clear`,
  `adb uninstall`, restore over live data or permission change without asking first (hard rule 10).
- **Creating a profile switches the app to it** (`ProfileFormScreen` calls `setActiveProfile`), so right
  after a create the app is *not* on the fixture.
- **Band data lands in the *active* profile, and only if that profile holds `band_enabled`**
  (`ActivitySyncWorker` reads `activeProfileId` first, then checks the flag), so the selection decides both
  what the screens show and where the next sync writes. While a run sits on `Test`, the owner's sync is
  paused — switch back when the run ends.
- **A new profile does not take the band** while another profile holds it (ADR-030). Moving it is one tap on
  «Читать браслет» on that profile's card, and it moves the owner's band data with it — ask before doing it.
- **Database pulls for diagnosis go to `tmp/`**, the database and its `-wal` together (P-07), never into
  git (hard rule 1), and are deleted when the run ends. Nothing about the owner's day is a screenshot.

## Hard rules

1. **Real health data never enters git.** Weight, food logs, exports, screenshots of the Today screen
   with real numbers, `logcat` dumps containing them — none of it. Fixtures are synthetic or
   sanitised, and say so in the file header.
2. **Never commit build artifacts or credentials.** APK/AAB, keystores, `keystore.properties`,
   `local.properties`, `.gradle/`, `build/`. A release keystore belongs to the owner, not
   to the repository.
3. **One source of versions.** `gradle/libs.versions.toml` only. No `+`, no `latest.release`, no
   `-SNAPSHOT` in anything that ships. A version bump is a reviewable diff, not a side effect of a
   build.
4. **A number is either measured or estimated, and the app says which.** Health Connect values, band
   values, formula values and user-typed values are four different things. This is a product rule, not
   a documentation style: never render an estimate with the same confidence as a measurement.
5. **Identify things precisely.** "It works on my phone" is not evidence. Device behaviour is named by
   model, OS build, app `versionName`/`versionCode` and git commit. A data claim names the Health
   Connect record type and the time window. A food claim names the OpenFoodFacts field and the
   product code.
6. **Separate observation from inference.** State what a tool or device returned, what you derived,
   and what remains unknown. Do not let a plausible explanation read like a measurement.
7. **A formula change needs a test vector.** Any edit to BMR, TDEE, deficit, flooring or recipe maths
   lands with a unit test that pins the expected numbers. No exceptions, no "obvious" changes.
8. **A schema change needs a migration and a migration test.** Room will happily wipe a user's year of
   food history otherwise.
9. **Do not patch third parties.** Gadgetbridge, Health Connect, OpenFoodFacts and AndroidX are
   read-only dependencies. Work around a limitation, record it in `docs/`, and never edit a vendored
   copy to make a test pass.
10. **Ask before anything irreversible on a device or on real data.** `adb uninstall`, `pm clear`,
    wiping app data, deleting an export, factory-resetting a band, re-pairing Gadgetbridge, changing
    Health Connect permissions. Especially on the wife's phone, which is a daily driver, not a test
    rig.
11. **Offline is the default, not a mode.** Logging food, viewing today and reading history must work
    with no network. Any feature that cannot is a design bug.
12. **The day boundary and time zone logic lives in `:core` and only there.** If two screens compute a
    "day" differently, the numbers will disagree and nobody will trust the app.
13. **Two languages, always both, in the same commit.** A new string lands in `values/strings.xml` and
    `values-ru/strings.xml` together. `StringResourceParityTest` (`:app:testDebugUnitTest`) fails when a
    key, a format argument or a plural's quantity set exists in only one file, so drift is a red build,
    not a Russian screen with English sentences in it.

## Workflow

1. **Scope** — which milestone item, which files, which device. If the request is ambiguous, read
   `docs/04-plan.md` and the relevant ADR before asking.
2. **Design** — for anything non-trivial, write the decision first: options, choice, consequence.
   Small ADRs live in `docs/03-architecture.md` §13.
3. **Implement** — smallest change that satisfies the item; `:core` before UI, tests alongside.
4. **Verify** — unit tests, then a real device when the change touches Health Connect, camera,
   permissions, background work or storage.
5. **Record** — update the docs when behaviour or a decision changed; a feature that contradicts the
   docs is unfinished, not "done with a note". A milestone entry goes into `docs/06-milestones.md`, and a
   trap or a judgement call that cost more than half an hour into `docs/07-pitfalls.md`.

## Code standards (Android)

- Kotlin only, Compose only, no XML layouts, no Fragments.
- `:core` has no Android imports and no `java.time` calls that depend on the device default zone —
  pass the zone in.
- Coroutines + `Flow`; no blocking calls on the main thread, no `runBlocking` outside tests and
  `main()`.
- All user-visible text in `values/strings.xml` + `values-ru/strings.xml`. No hardcoded strings, no
  string concatenation for sentences; use `plurals` and format arguments.
- Locale-aware formatting for numbers, dates and units; store metric internally, convert at the edge.
- Room: explicit `@ColumnInfo` names, `exportSchema = true`, migrations tested with
  `MigrationTestHelper`.
- Permissions: request at the moment of use with a rationale, handle "denied permanently" by pointing
  at settings, never block the whole app on a denial.
- `PendingIntent` always with `FLAG_IMMUTABLE`; declare `<queries>` for Gadgetbridge, otherwise it
  looks uninstalled on Android 11+.
- No third-party SDK that phones home. New dependency = a version-catalog entry + a note in the PR
  description saying why the platform cannot do it.
- Release builds: R8 on, resource shrinking on, no reflection-dependent libraries without keep rules.

## Data and privacy

- No backend, no account, no analytics, no crash upload. If that ever changes, it is an ADR first.
- `allowBackup=false`, `dataExtractionRules` and `fullBackupContent="false"`: health data must not drift
  into a cloud backup or a device transfer.
- Network is restricted to OpenFoodFacts hosts (`world.` for barcode lookup, `search.` for text search);
  cleartext off; a request without our User-Agent is a bug.
- Exports are the user's data leaving the app: write them where the user chose, never to a fixed path,
  never automatically to a shared folder.

## Definition of done

A change is done when all of these are true:

1. It builds (one build: there are no flavours any more, see ADR-017).
2. The maths has a unit test; the schema has a migration test.
3. It was exercised on a real device if it touches Health Connect, camera, permissions, background work
   or storage.
4. The relevant doc, ADR or plan checkbox is updated.
5. It introduces no new network call, permission, dependency or stored personal field without a line
   in the docs explaining why.

## Language

The user mixes Russian and English freely; read either, never ask which to use.

| Where | Language |
| --- | --- |
| Chat replies | **Russian**, unless asked otherwise |
| Everything committed — `docs/`, `README`, comments, commit messages | **English** |
| Quoted material — error messages, log lines, UI strings | Original, unchanged |
| Terms of art — `patch`, `offset`, tool and API names | Original, never translated |

A Russian question does not make the write-up Russian: investigate in whichever language is clearer,
write the finding in English, explain it back in Russian.

## Style

- Markdown, relative links inside the repo, absolute paths only when quoting a host location.
- Dates as `YYYY-MM-DD`. Hashes lowercase hex with the algorithm named. Versions as `major.minor.patch`.
- Prefer tables for inventories and states, lists for reasoning.
- Keep secrets, licence keys, personal data and real body measurements out of tracked files.
