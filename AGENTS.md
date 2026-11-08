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

Current state: **M1 complete, M2 nearly done.** `:core` (46 JVM tests) and `:data` (schema **v4**, 9 DAOs,
7 repositories, OFF client, product persistence, food logging) build green, and `:app` has the full food
loop: Today with target/eaten/remaining/macros/meal sections, product list and search, product screen with
portion preview, manual product form, entry edit, repeat-yesterday, and a working **barcode scanner**
(CameraX 1.6.2 + zxing-cpp 3.1.1, single FOSS build). 46 :core + 42 :data JVM tests and **53 device tests**
(49 :data, 4 :app) pass. Next in M2: recents/frequents, the edit-product entry point, then M3 recipes.

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
4. **After a schema version bump, package the androidTest APK twice.** The exported `N.json` becomes a
   test asset, but the asset merge can run before KSP writes the new JSON, so the file never reaches the
   APK and `MigrationTestHelper` fails with *"Cannot find the schema file in the assets folder"*. Build
   once to export the schema, then build `:data:assembleDebugAndroidTest` again and confirm the APK lists
   all versions:
   `python -c "import zipfile;print(sorted(n.split('/')[-1] for n in zipfile.ZipFile(p).namelist() if n.endswith('.json')))"`
   `--rerun-tasks` does not help when the schema is generated in the same build.

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
   last profile the app returns to onboarding and every dependent table is empty.
7. ~~Profile edit and switch~~ — `ProfileFormScreen` serves create and edit (prefilled, records a new
   weight only when it actually changed, upserts today's target). The current profile is stored in
   `app_setting` (schema **v2**, migrated on a real v1 database: `user_version = 2`, rows preserved),
   and tapping a profile card switches to it. Edit verified on device: goal −0.25 → −0.5 made the target
   2130 → 1924, where the safety floor (1.1 × BMR = 1923.6) engaged and the screen said so.
8. ~~Archive~~ — `ProfileRepository.archive` hides a profile without destroying it, clears the stored
   selection if that profile was current, and `unarchive` brings it back. The profiles screen lists
   archived profiles behind a toggle that opens by itself when nothing else is visible, and says in one
   line that archiving deletes nothing. Verified on device both ways.
9. ~~Settings~~ — units (metric/imperial, applied to height/weight entry and converted back to metric
   before storage), day-start hour, and language. Language uses Android's per-app locale
   (`LocaleManager`) on Android 13+; below that the screen says the phone follows the system language
   instead of offering a control that does nothing. Two traps worth remembering: clearing the override
   needs `LocaleList.getEmptyLocaleList()` (a list built from `""` is silently ignored), and changing it
   recreates the activity, which resets in-memory navigation to Today.
10. ~~Debug screen~~ — app version, device, schema version, Health Connect state, the active profile's
   inputs, the latest stored target row, and per-table row counts. Read-only by design.

## M2 progress (food)

1. ~~Starter ingredient pack~~ — `tools/generate_starter_pack.py` emits `data/src/main/assets/starter_products.json`
   (69 items, 12 categories, English + Russian names, per 100 g). `StarterPackImporter` imports it once in
   one transaction, guarded by `app_setting.starter_pack_version`, and marks every row `source='starter'`.
   Schema **v3** added `product.name_ru` (nullable) for the Russian names; migration 2 → 3 is additive and
   tested. Values are composition-table estimates, which is exactly why the product keeps a `source` column
   and why a scanned OpenFoodFacts product or a user edit outranks it.
2. ~~OpenFoodFacts client~~ — `OpenFoodFactsClient` (v3, User-Agent, one request per 6 s against a published
   15/min quota, bounded retries for network errors/429/5xx, no retry for 404, local barcode validation) and
   `OffResponseParser`, which accepts **both** status shapes (v3 `"success"`, v2 `1`). `LabelEnergy` in
   `:core` decides energy precedence: stated kcal (measured) → kJ ÷ 4.184 (estimated) → Atwater from macros
   (estimated) → nothing. The contract in docs/02-research.md §3.1.1 was measured, not recalled: absent
   products answer **HTTP 404**, malformed codes answer 200 + `status: 0`, and responses carry **no**
   rate-limit headers. Junk codes really exist in OFF (`9999999999999` is a cucumber), so a found product
   can still be unusable — `OffProduct.isUsable` says why. No new shipping dependency: `HttpURLConnection`
   plus `org.json`; `org.json:json` is test-only, because Android's version is a stub on the JVM.
3. ~~OFF → `product` persistence~~ — `ProductRepository` (local first, OFF second, user above both) and
   `BarcodeResolver` (cache → cached miss → fetch → unusable/offline, with the fetch injected so the tree is
   testable without a network). Three properties are pinned by device tests: the row **id survives a
   refresh** (entries and recipe items point at it), an **`is_override` correction is never overwritten**
   by a later scan, and a product with **no energy is refused** rather than stored as zero calories.
   `pending_lookup` does double duty — offline retry queue and cached 404 — separated by the `not_found`
   marker (ADR-015). Domain maths lives in `:core`: `Product` (with `displayName(languageTag)` and
   `hasEnergy`), `FoodMath.portion` (linear, missing stays missing, negative grams throws), `Barcode`.
4. ~~Logging and Today v1~~ — `FoodEntryRepository` writes a **snapshot** (grams, scaled nutrition and the
   product's names, schema **v4**) so renaming or deleting a product cannot rewrite yesterday; delete is a
   soft delete and the day ignores the row. `TodayScreen` shows target, eaten, remaining, macros and meal
   sections, and keeps BMR/TDEE/safety rule visible. Food screens: `ProductsScreen` (local search + browse),
   `ProductScreen` (per-100 g table, portion, meal chips, log), `ProductFormScreen` (manual product; accepts
   an existing one but no screen opens it yet).
   Verified on the Honor X7c: schema 3 → 4 migrated in place with 69 products intact; 150 g of avocado
   previewed 240 kcal and stored `150.0 g / 240.0 kcal / P 3.0 / F 22.05` with the name snapshot; Today read
   240 eaten, 1684 remaining; deleting the row set `deleted_at` and the day went back to 0.
5. ~~Edit a portion and copy yesterday~~ — `FoodEntryRepository.updateGrams` rescales the entry's **own
   snapshot** by the weight ratio, deliberately not re-reading the product, so history does not absorb a
   later product correction. `copyDay` duplicates only visible rows with fresh timestamps and leaves the
   source day alone. UI: tapping an entry row opens a portion dialog with a live preview; the Today screen
   offers "Repeat yesterday (n)" when today is empty and yesterday is not.
   Verified on the Honor X7c: 100 g → 50 g of bell pepper previewed 13 kcal and stored 13; copying moved
   yesterday's row to today as a new row (id 3) while the deleted avocado entry stayed deleted.
6. ~~Barcode scanner~~ — see the build notes below; one FOSS engine, a `TextureView` preview, and a real
   package read on the device.
7. ~~Product search over OpenFoodFacts~~ — the local list still answers every keystroke with no network; the
   remote search is an explicit button (quota per client, Cyrillic names would fire per character) against
   `search.openfoodfacts.org`, and a hit becomes a row only when tapped, through `saveFromOff` (ADR-019).
   The measured shape is in docs/02-research.md §3.1.2; the trap is that **`brands` is an array in a search
   hit and a string in a product lookup**, which threw until `OffResponseParser.text` accepted both.
8. Next: recents/frequents in the food list, the edit-product entry point, then M3 recipes.


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
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
.\gradlew.bat lint detekt           # if detekt is added, keep the config in the repo
```

- **One build, no flavours.** The `foss`/`google` split was dropped when zxing-cpp turned out to be
  enough (ADR-017); adding ML Kit later would be a flavour again, not a rewrite, because the decoder sits
  behind `BarcodeScanner`.
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
# OK (49 tests) — database behaviour, migrations, repositories, starter pack, products, food entries
```

### The scanner: two traps that cost an hour each (2026-11-08)

1. **`PreviewView`'s internal `SurfaceView` ignores its parent's bounds.** The preview painted over the
   title and the torch button, at screen size, inside a 240 dp box. `implementationMode = COMPATIBLE` did
   not help. `adb shell dumpsys activity top` showed it plainly: `PreviewView 0,0-656,240` but
   `SurfaceView 0,0-720,1280` inside it. The fix is to own the surface: a plain `TextureView` plus
   `Preview.setSurfaceProvider` (ADR-018). Diagnose such things with the view hierarchy, not screenshots
   alone.
2. **A missing `INTERNET` permission fails as a *network error* at runtime.** Every lookup returned
   `network error: Permission denied (missing INTERNET permission?)`, which the offline queue then stored —
   the queue worked perfectly and the app was simply not allowed to open a socket. The error text names the
   cause; read the recorded `last_error` before suspecting the network.

App instrumentation tests are a **separate** APK and runner: `com.balancecalories.test` (not
`com.balancecalories.app.test`), installed with `-t` alongside the app APK, and the app APK must be
reinstalled whenever a dependency is added — the test APK alone resolves classes from the installed app.

### Driving the UI with adb without lying to yourself

Learned the hard way, several times:

- **Dump before every tap.** `uiautomator dump` then read the bounds; hardcoded coordinates go stale the
  moment a section grows or a row appears.
- **An open keyboard moves everything.** Compose dialogs and fields push buttons upward, so a tap aimed at
  a button lands on the IME (an avocado became 1503 g). Type into the field, dump again, then tap the
  button by its new bounds.
- **`input keyevent 4` is "navigate back", not "hide the keyboard".** With no IME open it pops the screen —
  and a following fixed-coordinate tap lands on whatever was underneath (observed: a bell pepper product
  screen opened instead of a log action).
- Text entry is ASCII only: Cyrillic cannot be typed with `input text`, so pick a row by what is visible
  instead of searching for it.

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
- Network is restricted to OpenFoodFacts hosts (`world.` for barcode lookup, `search.` for text search);
  cleartext off; a request without our User-Agent is a bug.
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

1. It builds (one build: there are no flavours any more, see ADR-017).
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
