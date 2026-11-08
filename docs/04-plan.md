# Implementation plan

Estimates are **dev-days** for one developer working alone, including tests but excluding app-store
bureaucracy. Total: **~38 dev-days of feature work + ~4 days of spikes**, i.e. roughly 9 weeks
part-time or 8 weeks full-time with normal life interference.

The order is deliberate: every unknown that can invalidate the product is resolved in M0, before any
UI exists.

---

## M0 — Spikes (4 days) — *do these first, do not build UI yet*

Each spike is time-boxed and has an explicit pass/fail. If a spike fails, we change the plan, not the
deadline.

### S1 · Calorie semantics (1.5 d) — **the project-critical one**

Question: what exactly does Gadgetbridge report for a Huawei Band 9/11 day, and is it active-only?

Procedure:
1. Both bands paired to their phones with Gadgetbridge, Health Connect export on, device selected.
2. For 3 days, at ~23:30 write down: Huawei Health "calories", GB's own activity screen for the day,
   and Health Connect values (`ActiveCaloriesBurned`, `BasalMetabolicRate`, `TotalCaloriesBurned`).
3. Compare against a Mifflin estimate and against step count.

Pass criteria: `active_kcal` excludes BMR (i.e. `active + bmr ≈ Huawei Health total` within ~5%) **and**
the value is non-zero and plausible (1 500–3 500 kcal total for a normal day).
Fail: numbers are total energy, duplicated, or zero → switch to plan B (see risk R1).

Deliverable: a short note in `docs/notes/s1-calories.md` with the raw table, plus the decision on
whether we can trust `bmr_kcal` from the band or must always compute Mifflin.

### S2 · Health Connect availability & permissions (0.5 d)

Android 14 phone (Honor): nothing to install, grant the 7 permissions via Settings.
Android 11 phone (Redmi): install the **Health Connect** app from Play, then grant the same 7
permissions inside it. Then read 7 days of steps + active calories on both, and confirm that
late-arriving data updates an already-read past day.
Pass: both phones produce non-empty records for a past day.
Fail: HC unavailable/blocked on the Redmi (no Play services, region lock) → that person runs in
"manual mode" (food + weight + PAL estimate) and we say so on the setup screen instead of failing
silently.

### S3 · Gadgetbridge intent round-trip (0.5 d)

Enable Intent API, send `ACTIVITY_SYNC` with `setPackage`, receive `ACTIVITY_SYNC_FINISH` in a
runtime-registered receiver. Pass: sync triggered and finish observed within 90 s (or the timeout
path behaves predictably). Fail: hide the "Sync band" button, rely on GB's own scheduler.

### S4 · OpenFoodFacts reality check (0.5 d)

Scan 20 real products from your kitchen (Russian and imported), measure hit rate, and inspect the
field quality: how many have `energy-kcal_100g`? how many have only kJ? how many have wrong/missing
serving size? Confirm rate-limit behaviour with a burst of 20 requests.
Pass: ≥70% of daily products resolve with usable energy; the rest are handled by manual entry.
Deliverable: table in `docs/notes/s4-off-coverage.md` — this drives how much manual-entry UX matters.

### S5 · Barcode engine bake-off (0.5 d)

Throwaway app: zxing-cpp vs ML Kit on 30 scans (good light, kitchen light, curved packaging, damaged
label). Measure time-to-first-decode and failure rate.
Pass: zxing-cpp ≥ 85% first-try success → default engine confirmed.

### S6 · Measurement baseline (0.5 d)

Log weight, height, body fat % (if known), and 3 days of typical meals for both people. This also
produces the fixture data used by unit tests and gives a realistic feel for the logging flow before
it exists.

---

## M1 — Skeleton, profiles, targets (4 d)

- [x] Project setup: Gradle **9.8.2** wrapper committed, version catalog, `:core` as a pure JVM module,
      `:data` and `:app` as Android modules (Kotlin **2.3.20**, JUnit 5.14.4, AGP 9.5.1, compileSdk 36).
      Hilt is still deferred; the app uses a manual container.
- [x] Room schema v1 + DAOs + migration test harness — 11 tables, schema JSON committed, 12
      instrumentation tests green on the Honor X7c
- [x] `:core` energy module: Mifflin–St Jeor and Harris–Benedict BMR, PAL seed, deficit cap, safety
      floors, weekly clamp, DST-aware day bucketing — 27 tests green (`.\gradlew.bat :core:test`)
- [x] Universal profile model: create, edit, switch, delete and archive (with a restore path) are done
      and verified on device, and `band_enabled` is enforced one-per-device.
- [x] Profile create, onboarding step 1–3 (profile, body data, goal rate) — verified by hand on the
      Honor: a typed profile wrote `profile`, `body_measurement` and `target_history` rows and survived
      a restart
- [x] Target calculation + `target_history` write (seed mode; adaptive and pinned arrive in M5)
- [x] Settings: units (metric/imperial, applied to data entry), day-start hour, language (per-app locale
      on Android 13+; below that the phone follows the system language and the screen says so)
- [x] Debug screen: BMR/TDEE/target inputs and outputs, schema version, database row counts, stored
      target row and device/Health Connect state

Exit criteria: I can create two profiles and see a defensible daily target with a visible explanation.

## M2 — Food: products, barcode, entries (9 d)

- [x] Starter ingredient pack: generated JSON asset (`tools/generate_starter_pack.py`), **69 items** across
      12 categories with English and Russian names, per 100 g, imported once on first run in a single
      transaction, marked `source='starter'` so it stays editable and deletable like any other product.
      Smaller than the planned ~150: 69 reviewable entries beat 150 guessed ones, and the pack grows from
      user entries and OpenFoodFacts lookups.
- [x] OFF client: `OpenFoodFactsClient` on v3 with our User-Agent, a 6-second request throttle (OFF sends no
      rate-limit headers, so we choose to be slow), bounded retries for network errors/429/5xx, no retry for
      404, and local barcode validation. Parser understands both v3 (`status: "success"`) and v2 (`status: 1`).
- [x] OFF → `product` persistence: `ProductRepository` + `ProductMappers`; the row id stays stable across a
      refresh because food entries and recipe items point at it, a user correction (`is_override`) is never
      overwritten, an OFF product OFF has not revised is not rewritten, and a product with no energy is
      refused rather than stored as zero calories. `BarcodeResolver` runs the decision tree — local hit →
      cached miss → fetch → unusable/offline — with the fetch injected so it is testable without a network.
- [x] Product screen (per-100 g table, portion input, meal picker, log): the portion preview and the stored
      entry come from the same `FoodMath.portion` call, so the number shown cannot disagree with the number
      saved. An estimated energy is labelled **before** the user commits.
- [x] Manual product creation: name (English and optional Russian), energy and macros per 100 g, liquid
      toggle. Saving a correction to a scanned product marks it `is_override`.
- [x] Editing an existing product from the UI: the product screen has an **Edit** action that opens the form
      prefilled and returns to the product with the corrected numbers (not to Today — a correction is not a
      log). Saving marks a starter or OFF row `is_override`, so a later scan cannot undo the fix.
      Prefilling exposed a display bug: `7.6899999999999995` from OpenFoodFacts reached the field verbatim, so
      numbers are now rounded to three decimals with the exponent form and trailing zeros removed
      (`NumberText.kt`, 6 unit tests).
      Verified on the Honor X7c: an OFF product went 519.2 → 500 kcal and showed `ваши цифры` with the source
      still `OpenFoodFacts`; cancelling returned to the product screen, and a starter product prefilled
      `160 / 2 / 14.7` with both names.
- [x] Scanner screen: CameraX + zxing-cpp, overlay, torch, haptics, manual entry, debounce. Shipped as a
      **single FOSS build** — the `foss`/`google` flavour split is dropped (ADR-017): one Apache-2.0 engine,
      2.9 MB, no Play services. The preview is an owned `TextureView` because `PreviewView`'s internal
      SurfaceView ignored its parent's bounds (ADR-018). Decoder verified on the Honor X7c with a generated
      EAN-13 (4 tests) and with a **real package**: the live camera read `59032823` off a jar and the
      resolver found it in OpenFoodFacts.
      Prerequisites measured 2026-11-08 (docs/02-research.md §4.1): zxing-cpp 3.1.1 is Apache-2.0, 2.9 MB,
      `minSdk 21`, built on Kotlin 2.3.20, reads `ImageProxy` directly, and its `.so` files are 16 KB-page
      aligned; CameraX **1.6.2** is the newest family that stays within `compileSdk 36`.
- [ ] Scanner polish: torch state after returning to the screen, beep/haptic on the first successful read,
      and a "torch stays on while open" preference.
- [x] Food entries: create and soft delete, with the product name and nutrition snapshotted at log time
      (schema v4). The row survives a delete for history; the day ignores it.
- [x] Food entries: **edit** a portion (rescales the entry's own snapshot by the weight ratio, not the
      current product) and **copy yesterday** (visible rows only, fresh timestamps, original day untouched).
- [ ] Food entries: partial edit (meal change), quick kcal without a product
- [x] Today screen v1: target, eaten, remaining, macro row, meal sections, per-entry delete, and the target's
      inputs (BMR, TDEE, safety rule) kept visible underneath.
- [x] Product search: local search answers on every keystroke (no network); OpenFoodFacts search is a
      **button**, because the quota is per client and a Cyrillic name would otherwise fire one request per
      character. A hit is a candidate — it is written only when tapped, through the same `saveFromOff` path
      as a scan (ADR-019). Measured host and shape in docs/02-research.md §3.1.2; the legacy
      `/cgi/search.pl` never answered and is not used.
      Verified on the Honor X7c: "nutella" → `В OpenFoodFacts найдено: 631`, tapping the first hit stored
      product id 72 (`source='off'`, revision recorded, no override) and opened it at 519.2 kcal.
- [x] Recents in the food list: an empty query shows **Recently eaten** above the browse list, newest first,
      with a `2×` marker once a product has been logged more than once. One query does both jobs
      (`GROUP BY product_id` with `COUNT(*)` and `MAX(consumed_at)`), soft-deleted rows are excluded, and an
      entry whose product is gone is dropped — a shortcut that cannot be tapped is not a shortcut. Tapping
      opens the product screen rather than logging at once: portion and meal are choices, not guesses.
      Verified on the Honor X7c with the real log: `Недавно съеденное → Перец болгарский 26 ккал 2×`, and
      tapping it opened the product. Device tests: 52 in `:data` (3 new — ordering and counts, deleted rows
      excluded, another profile's plate does not leak in, limit honoured, empty before anything is logged).
- [x] Golden tests for OFF payloads (`fixtures/off/`, captured live on 2026-11-08); fake-clock tests for the
      rate limiter and a scripted transport for the retry state machine.

Exit criteria: a full day of eating can be logged faster than with a notes app, offline included.

## M3 — Recipes (3 d)

- [x] Recipe editor: components from product search, grams per ingredient, cooked weight, servings. The live
      preview uses the same `RecipeMath` the detail screen uses, so what is typed is what is stored.
- [x] Derived per-100 g / per-serving preview, with the two warnings that matter: the total is **unknown** when
      an ingredient has no calorie data (never the sum of the others), and per-100 g is flagged when it falls
      back to the raw weight because the cooked weight was not measured (ADR-020).
- [x] Log a portion by grams, defaulting to one serving; recipe detail screen with edit and delete. The entry
      snapshots the derived numbers and keeps only a link to the recipe, so editing or deleting the recipe
      cannot rewrite what was eaten.
- [x] Recipe scaling tests, property-based over a fixed seed: doubling every ingredient doubles the energy and
      the weight and leaves the per-100 g density unchanged (200 random dishes), plus 10 targeted tests for the
      unknown-value, zero-basis and empty-recipe rules.

      **No schema change was needed**: `recipe`, `recipe_item` and `food_entry.recipe_id` have existed since
      schema v1 — M3 was DAO, maths and UI, which is why it did not touch the migration chain.

      Verified on the Honor X7c: chicken breast 200 g + white rice 150 g, cooked weight 350 g, 2 servings
      previewed **788 kcal total / 225 per 100 g / 394 per serving**; logging the 175 g default wrote
      `393.75 kcal` with `recipe_id = 1` and `product_id = NULL`, and Today read 407 eaten (394 + 13 from a
      pepper) against the 1924 target. Device tests 61 in `:data` (9 new for recipes).

## M4 — Band data + daily balance (6 d)

- [x] `HealthConnectDataSource`: permission flow, 30 d backfill, 7 d incremental, SDK status handling
- [x] Interval clipping + local-day aggregation (midnight split, DST) + upserts
- [x] `quality` classification (`complete/partial/no_band/estimated`) and the PAL fallback for `no_band`
- [x] "Sync band" (Health Connect pull; the GB intent path is not needed — see the note below) + status line ("last synced …")
- [x] Today screen v2: burned, balance, quality line, permission/sync control, last-synced
- [x] WorkManager periodic sync + expedited on app open
- [ ] Onboarding steps 4–5: GB detection, HC permission, live verification, deep links
- [x] Aggregation tests with synthetic HC records

Done 2026-11-08 on the Honor X7c: burned 962 kcal, balance −555 kcal, 98 steps, 30 days synced, and the
schema migrated 4 → 5 on a real database with its rows intact.

Still open in M4:

- [ ] Onboarding steps 4–5: Gadgetbridge detection, Health Connect permission, live verification, deep
  links. Not started: the band chain on the owner's phone was completed by hand, and onboarding for a
  *new* phone (the Redmi) is what these steps are for.
- [ ] "Sync band" via **Gadgetbridge intents**. Health Connect is pulled instead (`ActivitySyncWorker`),
  because GB's export runs on its own schedule and a pull reads whatever has arrived. The intent path
  ("start export now") is a separate, unverified convenience: the GB intent contract needs measuring on
  the device before it is written down as working.

Exit criteria: the number on the Today screen survives a day of real use, including a band that syncs
at 21:00 and a day where the band stayed home.

## M5 — History, trends, adaptive TDEE (6 d)

- [ ] History list (per-day: balance, intake, burn, weight, quality)
- [ ] Charts: 7/30-day balance bars + weight EWMA line
- [ ] Weight entry + `body_measurement` merge (manual vs HC vs scale, one canonical point per day)
- [ ] Adaptive TDEE: window, guards, confidence, clamped change, `target_history` update
- [ ] "Target updated" explanation sheet with the two numbers that produced it
- [ ] Weekly recap card: predicted vs actual weight change, streak of logged days
- [ ] Projection to goal weight (with "estimate, not a promise" framing)
- [ ] Math tests for TDEE guards and clamps

## M6 — Hardening, export, release (6 d)

- [ ] Device matrix pass on both real phones: HC permission flows (system UI vs HC app), camera and
      torch, background sync under MIUI battery management, low-storage behaviour, font scaling
- [ ] JSON export/import (schema-versioned) + CSV export; round-trip test
- [ ] Full i18n pass (en/ru), plurals, RTL-safe layouts (cheap insurance)
- [ ] Accessibility pass: TalkBack, contrast, 48 dp targets, font scaling to 200%
- [ ] Empty/error/offline states everywhere; no dead ends
- [ ] Performance: cold start < 1.5 s, day render < 100 ms, DB size after 1 year < 50 MB
- [ ] ProGuard rules, release signing, `foss` and `google` flavours built
- [ ] README for the household: how to install GB, pair, enable HC, pair the second phone

## M7 — Optional v1.1 (post-release)

Adaptive-TDEE refinements, HC write-back (`NutritionRecord`, `WeightRecord`), widget, notifications,
sleep/workout screens, shared recipe export, micronutrients.

---

## Effort summary

| Milestone | Days | Cumulative |
| --- | --- | --- |
| M0 spikes | 4 | 4 |
| M1 skeleton | 4 | 8 |
| M2 food | 9 | 17 |
| M3 recipes | 3 | 20 |
| M4 band data | 6 | 26 |
| M5 trends | 6 | 32 |
| M6 hardening | 6 | 38 |
| Buffer (20%) | 8 | **46** |

If only one thing can be built: **M0 + M2 + M4** (food + band + one number). Recipes and trends are
what make it pleasant, not what make it work.

---

## Risk register

| ID | Risk | L | I | Mitigation / trigger |
| --- | --- | --- | --- | --- |
| R1 | GB's Huawei calories are total, not active (or unreliable) | M | **Critical** | S1 first. Plan B: derive expenditure from steps + HR + PAL, or from weight-trend adaptive TDEE only; the app still works, band becomes a step counter |
| R2 | Health Connect needs a separate Play app + its own permission UI on the Android 11 phone | H | Medium | S2 covers the install end-to-end; onboarding detects `UNAVAILABLE` and deep-links; manual mode keeps the app useful without it |
| R3 | OFF rate limits / poor Russian product coverage | M | Medium | Cache-first, manual entry first-class, starter ingredient pack covers cooking, S4 measures the real hit rate before we over-invest in scanning UX |
| R4 | GB setup friction kills adoption (auth key, re-pairing, HC toggles) | H | Medium | Onboarding wizard with screenshots + deep links; a "doctor" screen that names the exact missing switch; accept that setup is a 20-minute one-off |
| R5 | Huawei firmware update breaks GB support | M | High | Pin a known-good GB version, document it; our app depends only on HC, so a GB fix restores everything without an app update |
| R6 | Aggressive Huawei/Xiaomi battery management stops background sync | M | Low | Rely on foreground open + expedited work; show "last synced" honestly; document battery exemption |
| R7 | Data loss (phone lost, app reinstalled) | L | High | Weekly export reminder, JSON export, optional auto-export to a SAF folder |
| R8 | Scope creep (food DB, social, AI, meal plans) | **H** | Medium | Non-goals list in the product doc is contractual; new ideas go to a `docs/ideas.md` backlog |
| R9 | The balance number is *wrong* and demotivates | M | High | Confidence labels, "estimated" flags, weekly predicted-vs-actual check that visibly self-corrects |
| R10 | Health/legal: someone treats it as medical advice | L | Medium | Disclaimer, floors on targets, no diagnoses, no advice for minors/pregnancy/eating disorders |
| R11 | ODbL attribution missed | L | Low | Attribution string built into the product screen + settings + about |
| R12 | Android 11 phone (2019 hardware) makes camera preview janky / scanning slow | M | Medium | Cap `ImageAnalysis` at 1280×720, `KEEP_ONLY_LATEST`, zxing-cpp only, test S5 on the actual Redmi, not on an emulator |
| R13 | Phone replacement silently loses Health Connect history | L | Medium | Export/import is in MVP; onboarding text says "export before switching phones"; HC history cannot be re-read after the fact |

**Kill/continue gate after M0:** if S1 and S2 both fail (no trustworthy burn number on either phone),
stop and re-scope the product as a *food + weight trend* app with the band as a step counter. Decide
this before writing UI, not after.

---

## Recommended order for the first week

1. Day 1: install GB on both phones, pair both bands, enable HC export, enable Intent API — S1/S2/S3 setup.
2. Day 2: run S1 measurements (needs a full day, so start it the evening before), start S4 scan test.
3. Day 3: finish S1/S2/S4 tables, decide the burn formula, create the Gradle skeleton.
4. Day 4: `:core` energy maths + tests; Room schema v1.
5. Day 5: profile + target screens, first real target computed from real data.
