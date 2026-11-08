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

Current state: **M1-M5 complete, M6 half done.** `:core` (107 JVM tests) and `:data` (schema **v6**, 10 DAOs, 12
repositories) build green, and `:app` has the whole loop: Today with burned/balance/quality, the food side
(products, search, scanning, logging, recipes) and **History** with the adaptive TDEE, the weekly recap,
a weight trend line and a projection, plus a backup screen. 107 :core + 42 :data + 10 :app JVM tests and
**113 device tests** (109 :data, 4 :app) pass. Next: the rest of M6 — device matrix, accessibility, release.

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
   `ProductScreen` (per-100 g table, portion, meal chips, log, **Edit**), `ProductFormScreen` (manual
   product, and the same form serves editing an existing one — a correction marks the row `is_override`).
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
8. ~~Edit a product from the UI~~ — the product screen opens the form prefilled and comes back with the
   corrected numbers; a correction to a starter or OFF row sets `is_override`. Prefilling exposed float noise
   (`7.6899999999999995`), fixed by `NumberText.trimNumber` (3 decimals, no exponent, no trailing zeros, 6
   unit tests).
9. ~~Recents in the food list~~ — an empty query lists **Recently eaten** (newest first, `2×` when logged more
   than once) above the browse list. One query serves recency and habit; soft-deleted entries are excluded
   and a row whose product vanished is dropped. Tapping opens the product, not a logged portion.
10. ~~M2 complete~~ — products, search, scanning, logging, Today and editing.

## M3 progress (recipes)

1. ~~Recipe model~~ — `recipe`, `recipe_item` and `food_entry.recipe_id` have existed since **schema v1**, so M3
   needed no migration: a DAO, a repository, `RecipeMath` and three screens. `RecipeMath` (in `:core`) turns raw
   components into a dish and applies the cooking semantics from docs/02-research.md §3.5: per-100 g is
   computed against the **measured cooked weight**, the raw sum is only a flagged fallback, and a dish with an
   ingredient that has no calorie data reports **no total** rather than the sum of the others (ADR-020).
2. ~~Recipe screens~~ — `RecipesScreen` (list), `RecipeEditorScreen` (name, servings, cooked weight, ingredient
   search over local products, grams per ingredient, live preview) and `RecipeScreen` (components, totals,
   per-100 g, per-serving, portion by grams defaulting to one serving, meal chips, log, edit, delete). The
   editor's preview and the detail screen call the same maths, so a typed change cannot show one number and
   store another.
3. ~~Logging a portion~~ — `FoodEntryRepository.logRecipe` snapshots the derived nutrition into the entry with
   `product_id = NULL`, `recipe_id` set and `was_estimated = 1`. Editing the recipe afterwards does not move a
   logged meal, and deleting the recipe leaves the meal on the day with only the link cleared.
4. ~~Tests~~ — 11 `RecipeMathTest` cases including the property the plan asked for (doubling every ingredient
   doubles energy and weight and leaves the density unchanged, over 200 seeded random dishes), and 9 device
   tests for `RecipeRepository` (ingredients replaced as a set, id survives an edit, per-profile scoping,
   deletion keeps the logged meal, an ingredient-less recipe refused).
5. Next: **M4** — band data through Health Connect and the real daily balance.


## M4 progress (band data and the daily balance)

1. ~~Health Connect read path~~ — `HealthConnectActivitySource` reads **one** bucketed request for a whole
   range: `AggregateGroupByDurationRequest` takes a mixed metric set (it has no `recordType`), so steps,
   active and total calories, distance and resting heart rate arrive together as hourly buckets. A 30-day
   backfill is one round trip. Measured behaviour, the two manifest traps and the phone's constant
   1858.25 kcal/day are written up in docs/02-research.md §2.1.
2. ~~Day mapping and upserts~~ — `:core` owns the boundary: `ActivityBucketMapper` folds buckets into the
   profile's day-start hour, and `DailyActivityRepository` upserts one row per day (a day with no readings
   becomes `no_band`, never a zero). Today's window is clipped to *now*, which is why `isToday` feeds the
   quality rule.
3. ~~Quality and the burn precedence~~ — `ActivityQuality.complete|partial|no_band|estimated`, decided by two
   observable facts (is there data; was the day fetched after it closed) rather than a coverage threshold
   that would read a sleeping user as a missing band. `DailyBurn` keeps the origin: band total → band active
   + our BMR → PAL estimate (ADR-021).
4. ~~Today v2~~ — burned, `Balance` (or `Balance (estimate)` when the burn is a formula), a one-line note
   naming the origin, steps, an "Allow activity access" button when the permission is missing, a "Sync band"
   button and "Last synced …". Verified on the Honor: 962 kcal burned, −555 balance, 98 steps.
5. ~~Background sync~~ — `ActivitySyncWorker` (WorkManager 2.10.5) every 3 h plus an expedited attempt on
   app start. A denied permission or a storeless phone returns `success` (retrying cannot fix either); only a
   real read failure returns `retry`.
6. ~~Schema v5~~ — `daily_activity.data_origins`, nullable and additive, because Health Connect merges every
   writer and the Honor's platform layer contributes a constant daily total with an *empty* origin set.
   Migrated in place on the Honor with its rows intact; migration tested.
7. Tests: 11 new `:core` cases (burn precedence, quality rules, bucket folding, origins) and 13 device cases
   for the repository.
8. Next: **M5** — history, trends, adaptive TDEE.

### Health Connect: the traps that cost this milestone

1. **Declare `android.permission.health.READ_*` in the manifest.** Without the declarations the dialog has
   nothing to offer and `pm grant` refuses; the app compiles and looks finished.
2. **Android 14 needs an activity handling `VIEW_PERMISSION_USAGE` with category `HEALTH_PERMISSIONS`.**
   Without it every read throws `Incorrect health permission state …` *even with all permissions granted*.
   The androidx `ACTION_SHOW_PERMISSIONS_RATIONALE` filter alone covers Android 11–13 only, so declare both.
3. **MagicOS kills the permission dialog.** Tapping the request button launches
   `GrantPermissionsActivity`, and the phone's memory manager kills that process within a second — the dialog
   flashes and closes, every time. It is not an app bug and cannot be fixed from the app. With the owner's
   consent, `adb shell pm grant com.balancecalories android.permission.health.READ_*` produced the same state.
4. **`Get-Content`/`>` corrupts a pulled SQLite file.** PowerShell's text redirection turns the database into
   UTF-16. Use `Start-Process -RedirectStandardOutput` and pull both `balancecalories.db` and its `-wal`;
   the DB is `balancecalories.db`, not `balance_calories.db`.
5. **After a schema bump, rebuild `:app` before believing a device test.** `installDebug` of a stale
   `app-debug.apk` keeps the old `:data` classes, so `PRAGMA user_version` stays behind and the "migration
   was verified" claim is false. The 4 → 5 run was only real once the app APK was rebuilt.
6. **`:data` device tests cannot use backticked method names.** D8 rejects the generated lambda classes
   (`Space characters in SimpleName … not allowed prior to DEX version 040`) because `minSdk` is 28; the
   module's convention is camelCase test names.

## M5 progress (history and adaptive TDEE)

1. ~~Adaptive TDEE~~ — `AdaptiveTdee` in `:core` fits `tdee = average intake − weight change × 7700 / days` over a
   14-day window that **ends yesterday**, and refuses with a named reason when the evidence is thin: fewer than
   10 logged days, fewer than 3 weigh-ins, weigh-ins closer than 7 days apart, a weight rate above 2 kg/week, or an
   implied TDEE outside 1.0–2.5 × BMR (ADR-022). 13 `:core` cases pin the arithmetic and every guard.
2. ~~Collecting the evidence~~ — `AdaptiveTdeeRepository` reads the window from Room. Two rules matter: a day
   with no entries is **missing**, not 0 kcal, and several weigh-ins on one day collapse to one point with a fixed
   source preference (manual > scale > Gadgetbridge > Health Connect). 7 device cases.
3. ~~Applying it~~ — `TargetRepository.recordAdaptive` writes `mode = adaptive` with the TDEE and its confidence,
   upserted per day so two runs cannot leave two contradictory targets. A `LOW` estimate is displayed and **not**
   applied; an estimate that would land on the current target writes nothing and says so.
4. ~~History screen~~ — `HistoryScreen` (reached from Today) shows the measured expenditure with its confidence and
   the numbers behind it, a 7-day balance bar row, and the last 30 days with eaten/burned/balance/weight. A day with
   no food log says "нет записей" and prints **no balance** — "eaten 0" would be a claim about a person, not a
   missing entry.
5. ~~Applying it without asking, if you want that~~ — `TdeeApplyPolicy` in `:core` holds the rule: the default is to
   ask, a per-profile switch (`app_setting.tdee_auto_apply_<id>`) opts into automation, and the confidence gate
   applies in **both** directions, so a `LOW` estimate never moves a target even with the switch on (ADR-024).
   `AdaptiveTargetUpdater` is the single implementation, called by both the History screen and `ActivitySyncWorker`
   so the target cannot change on one route and not the other. The switch renders in the refusal state too — a
   preference should be settable before there is anything to apply. Verified on the Honor: the switch is off by
   default, survives a restart, and stays per profile. The **applied** path is not yet observed end to end, because
   the test profile has one logged day and refuses; the first real observation will be on a profile with ten.
6. ~~Trends and recap~~ — `WeightTrend.ewma` smooths per **measurement** (a gap does not weight the next reading
   more), `RecapCalculator` prints predicted and actual change side by side, and `Projection` refuses a horizon
   past two years rather than dating a number nobody believes (ADR-023). The History screen draws the 30-day
   trend line and the recap; the projection needs a goal weight.
7. ~~Goal weight~~ — `profile.goal_weight_kg`, schema **v6**, nullable and additive: empty means "no goal", so
   the projection says nothing instead of guessing a destination. Migration 5 → 6 tested on device.
8. Tests: 20 new `:core` cases for trends, the recap, the projection and the apply policy; 24 new device cases
   across the adaptive repository, the settings table and the v5/v6 migrations. Totals: 107 :core, 42 :data,
   10 :app JVM; 88 :data, 4 :app device. Strings 257/257.
9. Next: **M6** — hardening, export, release.


## M6 progress (hardening, export, release)

1. ~~Backup, restore and the CSV report~~ — `:data/backup` holds four pieces: `BackupTables` (what travels and in
   what order), `BackupWriter` (rows to JSON through raw cursors, so a column no repository reads is still included),
   `BackupReader` (validates the header, then **replaces** everything in one transaction with foreign keys **on**),
   and `DaysCsv` (the report). `BackupService` is the three-call surface the app uses, because `:app` still never
   sees Room. Reached from Settings → Данные.
2. **The trap that cost an hour**: the first export skipped the 69 starter products to save space, and the first real
   restore refused its own file — a food entry from the starter pack pointed at a product the file did not contain,
   so it failed its own foreign key. A backup must be self-consistent; 69 rows is nothing. Restoring also has to tell
   the starter importer which world it is in (`markImported` vs `ensureImported`), otherwise a fresh install ends up
   with two of every ingredient.
3. **The product index is external-content FTS4.** Rows written straight into `product` are invisible to search
   until `INSERT INTO product_fts(product_fts) VALUES('rebuild')`, and nothing fails — the user just stops finding
   products. The restore does it, and a test asserts search works afterwards.
4. **`ImportOutcome.Refused` used to carry only a reason**, so "файл повреждён" was a dead end. It now carries the
   underlying message, which is what turned the first mystery into a one-line diagnosis.
5. Verified on the Honor X7c: a 61 395-character backup written through the system document picker to Downloads, a
   CSV of the same days, and a restore of that backup back onto the live database — **112 rows restored, schema 6**,
   69 starter products and no duplicates, FTS finding "Avocado", no orphaned entries, today's intake still 406.75.
6. ~~Font scaling to 200%~~ — every label/value pair, chip group and button row uses `WrappingRow`
   (`app/ui/LabelValueRow.kt`), a `FlowRow` behind a plain `@Composable` API so no screen needs an experimental
   opt-in. Two failures made this necessary, both visible only at `font_scale 2.0`: the Today nav row rendered
   **"Настройки" as a vertical column of single letters** about 1000 px tall and pushed the rest of the screen away,
   and the Settings unit chips did the same to the whole screen — one chip visible, nothing else reachable. The
   signature is a squeezed `Text`: it does not ellipsize, it breaks inside words, and `uiautomator dump` shows it as
   a text node whose bounds are tall and narrow. Check that before believing a screenshot. Verified at 1.0, 1.3 and
   2.0 in Russian and at 2.0 in English (whose strings are longer); 1.0 is visually unchanged because `FlowRow` only
   wraps when it must. ADR-026.
7. Tests: 21 new device cases (11 backup round-trip, 10 CSV). Totals: 107 :core, 42 :data, 10 :app JVM; 109 :data,
   4 :app device. Strings 280/280.
8. Next: the rest of M6 — device matrix on both phones, TalkBack and contrast, release signing, README.
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
# OK (109 tests) — database behaviour, migrations, repositories, starter pack, products, food, recipes, band activity, adaptive TDEE
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
- **`input keyevent 4` closes the IME when a field has focus, and navigates back when it does not.** Both
  halves were observed. With no IME open it pops the screen, and a following fixed-coordinate tap lands on
  whatever is underneath (a bell pepper product screen opened instead of a log action). With the keyboard open
  it closes the keyboard and stays on the screen — the reliable way to get the layout back before dumping
  coordinates, because while the keyboard is up a tap aimed at a form row hits a key instead (observed: a tap at
  a list row typed a space into the query, and a later tap on "Save" typed a digit into the last field).
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
