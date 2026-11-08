# AGENTS.md

Operating instructions for any agent working in `E:\Projects\BalanceCalories`.

## What this repository is

An Android application, built by and for two people who want one honest number: **energy in minus
energy out**. The app reads body data and food, reads activity from the phone's Health Connect store,
and shows a daily balance plus a weekly trend.

Two outputs, everything else supports them:

1. **A working app** — Kotlin, Jetpack Compose, local-first, installable on both phones.
2. **A written record** of why it works that way — `docs/`, so a decision can be re-read in six months
   without reverse-engineering the code.

Current state: **M1 nearly done.** `docs/` is complete through the plan, the toolchain is installed,
`:core` — the pure Kotlin energy maths — builds and tests green (27 tests), and the modules exist:
`:data` (Health Connect status probe, Room schema **v2** with 12 tables and 5 DAOs, Profile /
BodyMeasurement / Target / AppSettings repositories behind domain mappers) and `:app` (Application
container, create/edit/delete/switch profiles, target screen reading the database). `assembleDebug`
succeeds and the APK installs and runs on the Honor X7c, where the v1 → v2 migration ran against a real
database; 16 instrumentation tests pass there and the profile flows were exercised by hand through the
UI. What is still missing from M1: settings (units, day start, language), the debug screen, and an
archive action.

### Android build: three things that bite (2026-11-08)

1. **AGP 9 has Kotlin built in.** Applying `org.jetbrains.kotlin.android` fails with *"no longer
   required for Kotlin support since AGP 9.0"*. Android modules apply only
   `com.android.application` / `com.android.library`. The **Compose compiler plugin is still
   required** once `buildFeatures.compose = true`. `:core` keeps the plain Kotlin JVM plugin.
2. **`compileSdk` is 36, and dependencies must respect it.** Platform 37 is not installed and the SDK
   lives under `Program Files`, so adding it needs an elevated shell. Current pins are chosen to fit
   36: Compose BOM `2026.06.01` (ui 1.11.4, material3 1.4.0) and lifecycle `2.10.0`. Newer Compose
   (ui 1.12.x) and lifecycle `2.11.0` declare `minCompileSdk=37` and fail `checkDebugAarMetadata`.
   Before upgrading either, check the AAR's `aar-metadata.properties` — not the release notes.
   Raising the ceiling means installing platform 37, then bumping `compileSdk`.
3. **Kotlin is 2.3.20 on purpose, and Room uses KSP.** Kotlin 2.4.21 builds this project, but KSP's
   newest release (`2.3.13`) declares `kotlinBaseVersion=2.3.20`, and Room needs a working annotation
   processor. Measured, not assumed: with Kotlin **2.3.20** + KSP **2.3.13** + Room **2.8.5**, Room
   generates `*_Impl.kt` and exports `schemas/…/<version>.json`. The alternative — KAPT — works but is
   slower and deprecated; it is not needed. Revisit when KSP ships a 2.4.x build.
4. **After a schema version bump, rebuild the androidTest APK with `--rerun-tasks` once.** The exported
   `N.json` becomes a test asset, but AGP keeps a stale asset merge and the new file never reaches the
   APK, so `MigrationTestHelper` fails with *"Cannot find the schema file in the assets folder"*. The
   tell is a test APK containing only the previous version: check with
   `python -c "import zipfile;print([n for n in zipfile.ZipFile(p).namelist() if n.endswith('.json')])"`.
   `.\gradlew.bat :data:assembleDebugAndroidTest --rerun-tasks` fixes it.

### Naming and localisation (2026-11-08)

- **Display name:** `Balance Calories` (English) and `Баланс Калорий` (Russian), both from
  `app_name`. The repository, Kotlin packages and `applicationId` stay `BalanceCalories` /
  `com.balancecalories`; only the label the user sees is renamed. See ADR-010 and ADR-011 in
  `docs/03-architecture.md` §13.
- **Every user-visible string is a resource**, in both files, added in the same commit. The UI follows
  the system locale; an in-app language switch is deferred to settings (M2), not forgotten.
- **`StringResourceParityTest` is the guard** (`.\gradlew.bat :app:testDebugUnitTest`): it compares the
  key sets and the format arguments of every `strings.xml` against `values/`, and pins the two app
  names. It runs on the JVM, so it is fast enough to run every time.
- **Previewing English on a Russian phone** without touching system settings, then undo the override:

  ```powershell
  adb shell "cmd locale set-app-locales com.balancecalories --locales en"
  adb shell "cmd locale get-app-locales com.balancecalories"   # expect [en]
  # ... screenshot / test ...
  adb shell "cmd locale set-app-locales com.balancecalories --locales ''"
  ```

  The Honor's system locale is `ru-US`; both screens were checked this way (2026-11-08).

## Remaining setup (M1)

1. ~~`local.properties` with `sdk.dir`~~ — written; untracked.
2. ~~Create `:data` and `:app`~~ — both exist and build.
3. ~~Room schema v1~~ — 11 entities, 4 DAOs, schema JSON committed under `data/schemas/`, and a
   `MigrationTestHelper` harness proven by `MigrationHarnessTest`.
4. ~~Repositories and mappers over the DAOs~~ — `ProfileRepository`, `BodyMeasurementRepository`,
   `TargetRepository`, and `Profile` as a `:core` domain type; `:app` never sees an entity or Room.
5. ~~Onboarding (steps 1–3)~~ — one scrollable form validates, then writes profile, first weight and
   seed target in order. Verified on device: rows land in `profile`, `body_measurement`,
   `target_history`.
6. ~~Profile deletion~~ — Profiles screen lists active profiles and deletes with a confirmation that
   cascades through weights, food and target history (ADR-012). Verified on device: after deleting the
   last profile the app returns to onboarding and every dependent table is empty. `archived_at` stays
   for the reversible case, but archive has no UI yet.
7. ~~Profile edit and switch~~ — `ProfileFormScreen` serves create and edit (prefilled, records a new
   weight only when it actually changed, upserts today's target). The current profile is stored in
   `app_setting` (schema **v2**, migrated on a real v1 database: `user_version = 2`, rows preserved),
   and tapping a profile card switches to it. Edit verified on device: goal −0.25 → −0.5 made the target
   2130 → 1924, where the safety floor (1.1 × BMR = 1923.6) engaged and the screen said so.
8. Archive has no UI yet (`archived_at` and `ProfileRepository.archive` exist); deletion is the
   user-facing destructive action for now.
9. Settings (units, day start, language); debug screen.


## Toolchain (installed 2026-11-07, verified)

| Item | Location / version |
| --- | --- |
| Android Studio | `C:\Program Files\Android\Android Studio` — 2026.2.2.9, bundled JBR, Start Menu shortcut |
| Android SDK | `C:\Program Files\Android\Sdk` — platform-tools 37.0.1, platforms;android-36, build-tools;36.0.0, cmdline-tools 16111833 |
| `adb` | `C:\Program Files\Android\Sdk\platform-tools\adb.exe`, on the machine `PATH` |
| Licences | `C:\Program Files\Android\Sdk\licenses` — accepted; packages install without prompting |

Two things to know before the next package install:

1. **The SDK lives under `Program Files`, so it is read-only for this session.** Adding a platform, a
   build-tools version or an emulator requires an **elevated** shell; `android sdk install` from a
   normal shell fails with access denied. Budget one UAC prompt for any SDK change.
2. **`sdkmanager` is deprecated.** The supported command is `android sdk install <package>` (new CLI,
   replaces `sdkmanager`); `sdkmanager.bat` still exists but prints a deprecation warning and forwards
   to the new tool. The first `android.exe` run unpacks a payload into `ANDROID_USER_HOME`
   (default `%USERPROFILE%`), so under the DSH `workspace-write` sandbox it fails with
   `Failed to create bin dir: Access is denied` — run it unconfined, or point `ANDROID_USER_HOME` at a
   writable directory.

`local.properties` (with `sdk.dir`) is written in M1 and stays untracked; Gradle also resolves the SDK
from machine-wide `ANDROID_HOME`.

## Directory contract

| Path | Role | Rule |
| --- | --- | --- |
| `docs/` | Design and findings | Numbered Markdown, tracked in git. The plan, the research, the ADRs, milestone notes. |
| `app/`, `core/`, `data/` | Gradle modules (M1) | `:core` is pure Kotlin, zero Android imports. `:data` owns Room, Health Connect, OpenFoodFacts, Gadgetbridge. `:app` owns Compose UI. |
| `tools/` | Build-time generators | Starter-ingredient pack generator, fixture capture scripts. Runs on the host, never in the app. |
| `fixtures/` | Test inputs | Sanitised OpenFoodFacts payloads, synthetic Health Connect dumps, barcode images. **No real personal data.** |
| `gradle/libs.versions.toml` | Version truth | Every dependency version lives here and nowhere else. |
| `build/`, `.gradle/`, `local.properties`, `*.keystore`, `*.apk`, `*.aab` | Build output and secrets | Never tracked. |
| `E:\Projects\BalanceCalories-acl-recovery\` | ACL rollback record | Outside the repo. Leave in place; it documents the permission repair that made this workspace writable. |

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
    key or a format argument exists in only one file, so drift is a red build, not a Russian screen with
    English sentences in it.

## Environment

| Fact | Value |
| --- | --- |
| Host | Windows, PowerShell (`pwsh`), NTFS; workspace `E:\Projects\BalanceCalories`, ~420 GB free |
| File policy | DSH sandbox; has run under both `workspace-write` and `danger-full-access`. Writes outside the repo may need approval, so treat anything under `Program Files` as read-only unless proven otherwise |
| Java | Liberica JDK **21.0.11** LTS, `JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-21-Full\` |
| Git | **2.49.0.windows.1**; repository initialised on `main` (`core.autocrlf=false`), first commit `d195f3a` |
| Python | **3.14.2** — used for `tools/` generators, not in the app |
| Node.js | **v24.13.1** — available, not required by the app |
| Android SDK | `C:\Program Files\Android\Sdk` — platform-tools **37.0.1**, `platforms;android-36`, `build-tools;36.0.0`, cmdline-tools **16111833**; `ANDROID_HOME`/`ANDROID_SDK_ROOT` set at machine scope, `platform-tools` and `cmdline-tools\latest\bin` on the machine `PATH` |
| Android Studio | **2026.2.2.9** (`AI-262.10968.63.2622.16840690`) at `C:\Program Files\Android\Android Studio`, bundled JBR, Start Menu shortcut |
| Gradle | Wrapper **9.8.2** committed under `gradle/wrapper/`; no system Gradle. Kotlin **2.3.20** (held back for KSP — see the build notes above) and JUnit **5.14.4** pinned in `gradle/libs.versions.toml` |
| Phones | Honor X7c — Android 14, MagicOS 8.0, Huawei Band 9 · Redmi Note 8 Pro — Android 11, MIUI 12.5.3, Huawei Band 11 |

### adb on this machine — use a private port

Honor Suite (`C:\Program Files (x86)\HonorSuite`, service `HonorSuiteService64.exe`) bundles an adb
server **v39** and keeps it on port **5037**; SDK platform-tools is **v41**. The two evict each
other's daemon — `adb server version (39) doesn't match this client (41); killing...` — and
`adb devices` then reports nothing even though the phone is healthy. Run every adb call on a private
port instead of fighting over 5037:

```powershell
$env:ANDROID_ADB_SERVER_PORT = '5039'
& 'C:\Program Files\Android\Sdk\platform-tools\adb.exe' devices -l
# <serial>   device   product:ALT-LX1 model:ALT_LX1 device:HNALT-QL
```

The Honor X7c (`ALT-LX1`, Android 14 / SDK 34, MagicOS `8.0.0.302(C185E8R2P5)`) exposes the real
adb interface as `MI_02` — `bInterfaceClass=255, SubClass=66, Prot=1`, bound to `WINUSB`. `MI_01` is
a `CDROM Linux File-CD Gadget` (Honor's installer disc), not mass storage. The interface only
appears after Honor Suite's driver is installed; MagicOS then shows the `Allow USB debugging` dialog.
An adb server started from this sandbox dies with the shell that launched it, so bootstrap and query
in the same command.

## Build and test

```powershell
.\gradlew.bat :core:test            # pure JVM maths: formulas, day bucketing, recipes
.\gradlew.bat :data:testDebugUnitTest
.\gradlew.bat :app:assembleDebug    # foss flavour by default
.\gradlew.bat :app:installDebug
.\gradlew.bat lint detekt           # if detekt is added, keep the config in the repo
```

- Two flavours: `foss` (zxing-cpp only) and `google` (adds ML Kit). `foss` is the default; a change
  must build in both before it is called done.
- Instrumented tests that touch Health Connect or the camera run on a **real device**, not an
  emulator — an emulator has no Health Connect data source and no real sensor noise.
- Under DSH `workspace-write`, Gradle cannot write `~/.gradle`: native extraction fails with
  `Couldn't open current thread, error = 5`. Run builds with `GRADLE_USER_HOME` pointing at a writable
  directory, and set `org.gradle.vfs.watch=false` there to silence the file-watcher. A normal shell
  (no sandbox) needs neither workaround.

### Instrumentation tests: install and instrument by hand

`:data:connectedDebugAndroidTest` **reports BUILD SUCCESSFUL while running nothing** on this machine.
AGP's test engine talks to a different adb server than the private port, prints
`Failed to get device API level … device not found`, and leaves an empty results XML. Do not trust its
exit code; check that the XML has a `tests=` count.

Run the tests explicitly instead:

```powershell
$env:ANDROID_ADB_SERVER_PORT = '5039'
adb = 'C:\Program Files\Android\Sdk\platform-tools\adb.exe'
.\gradlew.bat :data:assembleDebugAndroidTest
& $adb install -r -t data\build\outputs\apk\androidTest\debug\data-debug-androidTest.apk
& $adb shell "am instrument -w com.balancecalories.data.test/androidx.test.runner.AndroidJUnitRunner"
# OK (16 tests) — database behaviour, migration harness, repositories
```

## Devices and how to test on them

| Device | Role | Constraints |
| --- | --- | --- |
| Honor X7c (A14) | Owner's daily driver | Health Connect is built in; grant permissions via Settings |
| Redmi Note 8 Pro (A11) | Wife's daily driver | Health Connect **app** must be installed; MIUI kills background work; this is the performance floor |

- Ask before installing, uninstalling or clearing anything on the Redmi. A debug build replacing a
  release build with a different signing key requires an uninstall — which wipes data. Say so first.
- Band data is only valid if Gadgetbridge is installed, paired, and has Health Connect export enabled
  for that device. If the numbers look empty, check that chain before suspecting the app.
- Logcat is evidence, not conversation: capture it to a file, quote the relevant lines, and keep it
  out of git if it contains personal data.

## Workflow

1. **Scope** — which milestone item, which files, which device. If the request is ambiguous, read
   `docs/04-plan.md` and the relevant ADR before asking.
2. **Design** — for anything non-trivial, write the decision first: options, choice, consequence.
   Small ADRs live in `docs/03-architecture.md` §13.
3. **Implement** — smallest change that satisfies the item; `:core` before UI, tests alongside.
4. **Verify** — unit tests, then a real device when the change touches Health Connect, camera,
   permissions, background work or storage.
5. **Record** — update the docs when behaviour or a decision changed; a feature that contradicts the
   docs is unfinished, not "done with a note".

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
- `allowBackup=false` and data-extraction rules that exclude the database: health data must not drift
  into a cloud backup.
- Network is restricted to OpenFoodFacts hosts; cleartext off; a request without our User-Agent is a
  bug.
- Exports are the user's data leaving the app: write them where the user chose, never to a fixed path,
  never automatically to a shared folder.

## Choosing instruments

| Question | Instrument |
| --- | --- |
| Why did the app crash or misbehave? | `adb logcat --pid=$(adb shell pidof -s <pkg>)`, plus the in-app debug screen |
| What does Health Connect actually contain? | The debug screen's raw-record dump, not guesses from the UI |
| Why is a background sync late? | `adb shell dumpsys jobscheduler`, WorkManager diagnostics, battery-optimisation state |
| Is the layout broken at 200% font size? | Compose previews at several font scales, then a device |
| Is the database right? | `adb exec-out run-as <pkg> ...` on a **debug** build only, or a Room export |
| Is scanning fast enough? | Macrobenchmark and manual timing on the Redmi, not the Honor |
| What does OpenFoodFacts return for this barcode? | `curl` against the staging host for experiments, a fixture for the test |
| Is a dependency compatible? | `.\gradlew.bat :app:dependencies`, AGP/Kotlin compatibility table, not memory |

## Definition of done

A change is done when all of these are true:

1. It builds in both flavours.
2. The maths has a unit test; the schema has a migration test.
3. It was exercised on a real device if it touches Health Connect, camera, permissions, background work
   or storage.
4. The relevant doc, ADR or plan checkbox is updated.
5. It introduces no new network call, permission, dependency or stored personal field without a line
   in the docs explaining why.

## Language

The user works in Russian and English interchangeably, and may mix both inside a single message. Read
either without asking for a translation, and never ask which language to use.

| Where | Language |
| --- | --- |
| Chat replies | **The language the user is writing in** — English in this project so far; if the user switches to Russian, answer in Russian. Never ask which language to use |
| Everything committed — `docs/`, code comments, commit messages, this file | **English** |
| App UI strings | English and Russian, both maintained; neither is a translation afterthought |
| Quoted material — error messages, `logcat` lines, API field names | Original, unchanged |
| Terms of art — `Health Connect`, `Gadgetbridge`, `record`, `barcode`, `TDEE` | Original, never translated |

A Russian question does not make the write-up Russian: investigate in whichever language is clearer,
write the finding in English, explain it back in Russian.

## Style

- Markdown, relative links inside the repo, absolute paths only when quoting a host location.
- Dates as `YYYY-MM-DD`. Hashes lowercase hex with the algorithm named. Versions as `major.minor.patch`.
- Prefer tables for inventories and states, lists for reasoning.
- Keep secrets, licence keys, personal data and real body measurements out of tracked files.
