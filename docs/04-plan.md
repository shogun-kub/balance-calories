# Implementation plan

Estimates are **dev-days** for one developer working alone, including tests but excluding app-store
bureaucracy. Total: **~44.5 dev-days of feature work + ~4 days of spikes**, i.e. roughly 11 weeks
part-time with normal life interference.

Milestones: M0 spikes · M1 skeleton · M2 food · M3 recipes · M4 band data · M5 trends · M6 hardening ·
**M7 interface** · M8 optional v1.1. M7 was added after M5, at the owner's request and before the release
build, because the app worked but the three things people look at daily lived in three different screens
reached by a column of text buttons.

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

- [x] History list (per-day: balance, intake, burn, weight, quality)
- [x] Charts: 7-day balance bars + weight EWMA line (the 30-day bar view is not built; the line covers 30 days of weigh-ins)
- [x] Weight entry + `body_measurement` merge (manual vs HC vs scale, one canonical point per day — preference order manual > scale > GB > HC)
- [x] Adaptive TDEE: window, guards, confidence, clamped change, `target_history` update; applying it is opt-in (default: ask), with a per-profile switch on the History card (ADR-024)
- [x] "Target updated" explanation with the two numbers that produced it (inline card, not a sheet)
- [x] Weekly recap: predicted vs actual weight change, streak of logged days
- [x] Projection to goal weight (with "estimate, not a promise" framing); needs `profile.goal_weight_kg`, schema **v6**
- [x] Math tests for TDEE guards and clamps (13 :core cases + 7 device cases)

## M6 — Hardening, export, release (6 d)

- [ ] Device matrix pass on both real phones: HC permission flows (system UI vs HC app), camera and
      torch, background sync under MIUI battery management, low-storage behaviour, font scaling
- [x] JSON export/import (schema-versioned) + CSV export; round-trip test — `:data/backup`, ADR-025
- [ ] Full i18n pass (en/ru), plurals, RTL-safe layouts (cheap insurance)
- [ ] Accessibility pass: TalkBack, contrast, 48 dp targets — font scaling to 200% is done (ADR-026)
- [ ] Empty/error/offline states everywhere; no dead ends
- [ ] Performance: cold start < 1.5 s, day render < 100 ms, DB size after 1 year < 50 MB
- [ ] ProGuard rules and release signing (no flavours: ADR-017 dropped the split)
- [ ] README for the household: how to install GB, pair, enable HC, pair the second phone

## M7 — Interface (6.5 d)

Planned before release, not after: the app currently works but it is driven by a column of text buttons at
the bottom of the Today screen, and the three things people actually want to see — today's balance, the
week, their weight — are buried in three different screens. Navigation is the difference between a working
prototype and something two people open every day, so it lands before the release build, not in v1.1.

### M7.1 — Navigation by tabs (2 d)

- [x] **Decided (owner, 2026-11-08): four tabs** — `Сегодня` · `Еда` · `История` · `Вес`.
      profile/settings/data/debug screens stay behind a gear icon in the top bar, not in the tab row.
      Rationale: a tab is a *place you return to*, and settings is a place you visit. Five tabs force a
      "More" tab, which is a drawer with extra steps, and the owner confirmed the four-tab set.
- [x] Icons: hand-written vector drawables (a plate, a bowl, a chart, a scale) rather than
      `material-icons-extended`. That artifact is ~4 MB of vectors to use four of them, and the four we need
      are the four definitions of the app. `material-icons-core` (~100 KB, includes the gear and back arrow)
      is the one dependency worth adding.
- [x] Keep the screens themselves unchanged in this step: tabs are a container, and mixing an IA change with
      a redesign makes it impossible to say which one broke something.
- [x] `Back` behaviour: tab switches do not stack; `back` from a tab returns to `Сегодня`, `back` on
      `Сегодня` leaves the app. Sub-screens (product, recipe, weight entry) keep the existing back arrow.
- [x] Remember the last tab across process death (`rememberSaveable`), so a phone that kills the app
      overnight does not send the person somewhere else.

**Done 2026-11-08.** Four tabs with hand-drawn icons, the gear in a top bar, `Back` semantics as
specified, and the selected tab survives process death. Two things the device changed: labels are
dropped above a 1.5× font scale (at 2× "Сегодня" needs more than a quarter of the bar and a squeezed
label breaks inside the word — the ADR-026 failure, seen again before it shipped), and the recipes
list moved under `Еда` instead of a fifth destination. Profiles moved into Settings: switching
profile is rare on a phone that belongs to one person, so it earns neither a tab nor a button on
Today. Verified on the Honor X7c (Android 14): all four tabs, the gear, back from a tab and back
from an overlay, at font scales 1.0, 1.3 and 2.0. ADR-027.

### M7.2 — Intake/burn chart with macro split (2 d)

- [x] One chart on `История`: per day, **eaten** as a stacked bar and **burned** as a second bar on the same
      baseline and the same scale. Both numbers on one scale is the entire point — a chart where the two
      quantities have different axes cannot answer "did I eat less than I burned".
- [x] Macro split inside the eaten bar: **green = protein, yellow = fat, red = carbs**, in fixed order, plus
      a legend that is always visible and never collapses to colour alone.
- [x] **Colour is never the only channel.** Green/red is the classic red-green confusion (~8% of men), so:
      every segment is labelled in the legend with its name and grams, the segments are separated by a 1 dp
      divider so they are distinguishable in greyscale, and each bar carries a content description naming
      all three values. If that makes the legend carry the meaning, the colour is decoration — which is the
      correct hierarchy for a health app.
- [x] A day with no food log draws **no eaten bar** (a gap), never a zero-height bar: the app's oldest rule
      is that an unlogged day is missing, not empty.
- [x] Target line across the chart at the current target, dashed, so the person can see the target rather
      than infer it.
- [x] Replaces the 7-day balance bar row, which shows the same days with less information.
- [x] 30 days by default with a 7/30/90 selector; horizontal scroll for 90, since squeezing 90 bars into
      360 dp is a texture, not a chart.

**Done 2026-11-08.** One Canvas chart on `История`, replacing the seven-day balance row: eaten as a
stacked macro bar, burned as a second bar, one baseline and one scale, the target dashed across both.
7/30/90 selector; 90 days scrolls sideways, opens **at the newest day** (`rememberScrollState` cannot
do that — the maximum is only known after layout, so the chart scrolls once it is).

Four decisions the device forced, each of which changed what the chart says:

- **The legend shows shares of energy, not grams.** Grams summed over thirty days are a number nobody
  can act on ("28.8 g of protein" without the day count), while a share is the same answer for any
  range — and explaining the colours is the legend's job. The plan asked for grams; shares turned out
  to be the honest version of that idea.
- **An estimated burn is drawn as an outline, not a fill.** Thirty days of `PAL_ESTIMATE` rendered as
  solid bars read as thirty measurements; as outlines the measured days are the ones the eye finds.
  This is the chart's version of hard rule 4.
- **The summary reports an average burn per day, not a total.** A first version printed "сожжено
  68 322 ккал" over 30 days, which is arithmetically true and rhetorically a lie: two thirds of those
  days are the same formula estimate. It now reads "в среднем сожжено 2346 ккал в день, из них оценка
  за 85 дн.".
- **`MacroEnergy.split` always fills the bar exactly.** Macros below the logged energy leave an
  "unlabelled energy" segment (alcohol, fibre, label rounding); macros above it are scaled down. A
  stacked bar whose parts do not add up to its height is a lie drawn in colour, so there is a property
  test over 500 random days, not just examples.

Two layout traps found at a 200% font scale, both the ADR-026 failure in a new place: the legend row
was a plain `Row`, so the target swatch squeezed into a vertical column of letters ("Ц е л ь 1 9"),
and `fillMaxWidth()` inside a `horizontalScroll` resolves against an unbounded width and collapses
the canvas to zero — the 90-day chart was blank until it got an explicit width. Verified on the
Honor X7c at 1.0 and 2.0 in Russian, on all three ranges. ADR-028.


### M7.2b — Today's half-doughnut — done 2026-11-08

- [x] A half-doughnut of today's energy on `Сегодня`, split by macro, with the **grams** of protein, fat and
      carbs printed beside it. Colour is decoration; the numbers carry the meaning (ADR-029).
- [x] `CalorieGauge` in `:core` (6 cases): the arc never exceeds half a circle, the excess is reported in
      words, no target draws no fill, nothing eaten draws the track with today's allowance as the big number.
- [x] One `ChartPalette` for both charts, in its lighter 300-level tones, so "lighter shades of all chart
      colours" covers the history chart too instead of only the new one.
- [x] Verified on the Honor X7c at 1.0, 2.0 and in the empty state: at 2× the phrases move under the arc,
      because inside it they overflow the inner opening; with nothing logged the headline is today's
      allowance and the subtitle says so, rather than a dash or a zero.

### M7.3 — Weight: manual entry and a proper chart (1.5 d)

The wife's request, and the one item on this list that is more than cosmetic: the adaptive TDEE needs
weigh-ins to work at all (`MIN_WEIGHT_POINTS = 3`, `MIN_WEIGHT_SPAN_DAYS = 7`), so a first-class entry point
makes the app's central number arrive sooner and refuse less often.

- [x] A `Вес` tab with today's weight, a one-field entry (keypad, 0.1 kg step), and the last reading as the
      prefill so the usual case is "same as yesterday, adjusted".
- [x] Writes `body_measurement` with `source = 'manual'`, which outranks scale, Gadgetbridge and Health
      Connect in the canonical ranking — a number the person typed on purpose should win over a number
      something else inferred.
- [x] Chart: **raw points as small dots**, the **EWMA line** on top, and the **goal weight as a dashed
      horizontal line** when one is set. The dots matter because hiding them behind a smooth line implies a
      precision the scale does not have; showing both is honest about the noise.
- [x] Range selector 30/90/365 days, and the trend line labelled with its current rate (`-0.35 kg/week`).
- [x] Currently weight can only be recorded by **editing the profile form**, which is why this needs its own
      place: an action you take twice a week should not be four taps deep in a settings-shaped screen.

**Entry half landed with M7.1 (2026-11-08)**, because a fourth tab cannot be a placeholder: the `Вес`
tab ships with the field, the prefill, the rate line, the recent readings and the save path; the chart
followed in the M7.3 block below.

- **Saving twice in one day appended a second row.** Typing 79.5 an hour after 80 left both, so
  "last reading" said 79.5 while the list said 80 — two screens disagreeing about one body. A source
  now owns **one row per day**: `record` deletes the day row from that source before inserting, so a
  re-typed weight corrects the day instead of being averaged with the value it replaced. Different
  sources still keep their own rows, which is what the canonical ranking reads.
- **The readings list showed the EWMA, not the readings.** It is now fed by
  `HistoryRepository.weightPoints`, the unsmoothed canonical per-day values. A list headed "recent
  weigh-ins" must print what the scale said; the smoothed line belongs to the chart in this section.

Verified on the Honor X7c: 80 to 79.5 to 78.2 left exactly one `manual` row for the day, the list and
the heading agreed at every step, and the seed target followed the weight (1924 to 1904).


**Built 2026-11-08.** The chart draws what the scale said (dots) and the EWMA through it (line), with the goal
dashed across when one is set. Three decisions the device forced:

- **The axis has a floor of 2 kg and the data sets it, not the goal.** With the goal folded into the range, a
  5 kg window became 8 kg and four months of movement collapsed into a band a few pixels tall — the goal was
  making the data unreadable. `WeightAxis` lives in `:core` with 10 cases, and scales to whole pounds in the
  imperial setting because a pound scale does not display 172.6.
- **An off-range goal is named, not drawn.** The legend says "Цель 75 кг · ниже графика" and shows no swatch,
  because a swatch promises a line on the canvas. Same rule for the trend: with one weigh-in the legend drops
  "Тренд" and the sentence "для тренда нужно минимум два взвешивания" explains why.
- **Gutters are measured, not assumed.** A fixed 46 dp label gutter overflowed at `font_scale 2.0`; the axis
  numbers and the date row are now measured with `TextMeasurer` and the plot is sized around them.

Verified on the Honor X7c at 1.0 and 2.0 on all three ranges, and with one weigh-in. The chart was looked at
with a **seeded copy** of the database (47 synthetic weigh-ins over 120 days, then a goal inside the range), and
the device was restored to its real state afterwards — one weigh-in, goal 75 kg. That copy is why `tmp/` is now
ignored: it was a pulled database sitting untracked in the working tree, which is the accident hard rule 1 exists
to prevent.

### M7.4 — Launcher icon — done 2026-11-08

- [x] The app had **no icon at all**: `AndroidManifest.xml` declared no `android:icon`, so it showed the
      platform's default robot. It now declares `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.
- [x] **Brief (owner, 2026-11-08, restated in Russian): a balance scale with a piece of pie in one pan and a
      dumbbell in the other.** `Кусок пирога в одной чаше весов, и гантеля — в другой.` One glyph for the whole
      product: what you eat weighed against what you spend.
- [x] Three sketches first, at 24, 48 and 108 dp: a full balance with the food inside its pans; a reduced beam
      with the two objects hanging; and a front-on balance whose pans are straight lines. The sheet is the point
      of the exercise — at 24 dp the pans of the first sketch merged with the food into one blob, which is exactly
      the failure the sketches existed to expose.
- [x] **The owner chose the reduced structure** (beam and objects, no pans) after seeing all three, then rejected
      two drafts of the objects in a row — which is what produced the final language:
      1. *"rotate the piece of cake in 3d-like style… dumbbells should be steel blue (#4682B4)… pie now looks like
         shit (by color). Could you at least add a second colour there?"* → the slice gained a depth face and a
         golden crust over berry filling; the dumbbell turned steel blue.
      2. *"now we need to rotate the dumbbell the same way, because now it looks like weirdly looking wide letter H"*
         → the dumbbell became a tilted bar with two rounded weights and a lit disc face on each outer end.
- [x] Shipped as an **adaptive icon**: `res/drawable/ic_launcher_foreground.xml` (8 `<path>` elements over a
      108 × 108 viewport), the cream background as a colour resource, and a `<monochrome>` layer for Android 13+
      themed icons. No PNG densities and no legacy square icon — `minSdk` 28 means an adaptive icon is the only
      form any supported phone can use.
- [x] `tools/icon_design.py` is the **source of the geometry**, not a sketchbook: it renders the 24/48/108
      previews and prints the exact `pathData` the drawable carries, so the approved picture and the shipped
      vector cannot drift apart. Each colour group is one path with several subpaths, so a shape built from
      many quads is rasterised once and shows no seams.
- [x] **Verified on the Honor X7c** (ALT-LX1, Android 14 / SDK 34): the system paints it in Settings → *О
      приложении*, in the recents card, and on the launcher's third home page as **Баланс Калорий**.

Four things the icon taught:

- **At 24 dp an icon is a silhouette test, not a drawing.** Every decision that survived was about outline;
  every decision that failed was about detail — pans, a domed crust, a single colour with no internal edge.
- **Two rectangles on a bar are a capital H**, whatever you do to them. A dumbbell needs round weights, and the
  lit disc face on each outer end is what says *weight seen from the side* rather than *letter*.
- **A swept solid is not a translated outline.** Filling the outline shifted down by the object's depth leaves a
  gap wherever the object is thinner than that depth; at the slice's tip it showed as two prongs with the
  background between them, which read as a pen nib. The side is the shape plus its copy plus a quad on every
  edge, all in one colour.
- **A generated icon has to be checkable.** Because the vectors come out of the same script as the previews, the
  only way they disagree is if the script is edited without re-rendering. The path data is printed, never
  hand-typed.

### M7.5 — Dark theme and contrast — done 2026-11-08

- [x] **`MaterialTheme {}` resolved to a light-only scheme** with no `darkColorScheme` and no idea what the phone
      was set to. `BalanceTheme` now follows `isSystemInDarkTheme()` with Material 3's baseline pair, and
      `values-night/themes.xml` gives the window a dark background so the app does not flash white on a cold start
      in dark mode (Compose cannot paint before it runs, and the platform theme is what the phone shows meanwhile).
- [x] **The palette problem was worse than "the colours need retuning" — they were never measured.** On cream, the
      six chart tones sat at **1.4 : 1 to 2.9 : 1**: below the 3 : 1 WCAG floor for graphical objects, and the fat
      tone at 1.4 : 1 was effectively invisible. `Contrast` in `:core` is the WCAG 2.x arithmetic (twelve cases,
      pinned against the published grey thresholds: black on white is exactly 21, `#767676` is 4.54 on white and
      `#808080` is 3.95), so a colour argument can now be settled with a number.
- [x] **Two sets of six tones, chosen per surface.** On cream the tones are dark (700-level, 4.7–6.2 : 1); on
      near-black they are the 300-level tones the light theme used to use (5.1–12.1 : 1) — they were always legible
      there, which is exactly why the light failure took a measurement to find. `LocalChartTones` carries the set
      with the theme, so no call site asks which mode it is in and `ChartPalette.protein` reads the same everywhere.
- [x] **`ChartPaletteContrastTest` is the guard**: every tone ≥ 4.5 : 1 on its own surface (text level, because
      these are also drawn as small legend swatches and chip dots), every tone distinguishable from the gauge track,
      the two modes actually different, and no tone transparent — contrast ignores alpha, so a typo'd alpha would
      pass the colour test and render invisible.
- [x] **Verified on the Honor X7c in both modes.** Light: gauge, history chart, weight chart. Dark: the same three
      plus the food list and settings, switched with `cmd uimode night yes|no` and left back on the phone's own
      setting (light). Status bar, top bar, tab bar, chips, dialogs and the search field all follow the scheme
      without a single hard-coded colour, because `ChartPalette` was the only place one existed.

Three things this milestone taught:

- **"Quiet colours" and "readable colours" were in a fight, and quiet was winning.** ADR-029 chose 300-level tones
  so a full carbohydrate arc would not read as a failure; the same choice made the arc invisible on cream. The
  compromise is lightness per surface rather than one set for both — the hue still does not shout, the value does
  its job.
- **A `@Composable` palette property is nearly free, except inside a `DrawScope`.** `MacroGauge` read the tones
  inside `Canvas { }` — a lambda that is not composition, which compiled fine while the palette was a plain object
  and stopped compiling the moment it became theme-aware. The fix is to hoist the colours above the canvas, which
  is also where they belonged.
- **A theme is a place, not a coat of paint.** Because every screen already asked `MaterialTheme.colorScheme` for
  its colours, the dark theme needed no screen changes at all; the work was the palette, the window background and
  the test that keeps the next palette honest.

### M7.6 — Strings, verification, and what this breaks

- [x] Every count whose noun inflects is a `<plurals>` in both languages: **10 strings** became plurals
      (`sync_result_synced`, `history_adaptive_detail`, `history_need_*`, `history_recap_streak`,
      `history_projection`, `backup_saved`, `backup_restored*`, `weight_chart_description`,
      `history_weigh_ins_too_close`); the 13 where the number is followed by an invariant unit (`kcal`,
      `kg`) or a count-last phrase keep `tools:ignore="PluralsCandidate"` and a reason.
- [x] An adaptive-TDEE refusal names its own noun: six reasons, six sentences, arguments built from the
      same constants the rule uses. The weigh-in shortfall used to say "needs 3 logged days". ADR-034.
- [x] `StringResourceParityTest` reads `<plurals>` too (3 new cases): same names in both files, every
      quantity of a block agrees on its format arguments, and each language declares the quantities its
      grammar needs (en `one`/`other`, ru `one`/`few`/`many`/`other`).
- [x] Six unused strings deleted; `history_need_more` split into `history_need_days`,
      `history_need_window` and `history_need_weigh_ins`; `history_no_estimate`,
      `history_impossible_change`, `history_impossible_tdee` added. Both files still 1:1.
- [x] `android:dataExtractionRules` with everything excluded, plus `fullBackupContent="false"` for
      API 28–30: the rule AGENTS.md already states, implemented for API 31+ as well as below it.
- [x] **Lint, first run: 37 findings.** Second run: **5**, and each of the five is a decision, not debt —
      see the table below.
- [x] Verified on the Honor at **100% and 200%**, Russian and English: all four tabs pass the squeezed-text
      check (a `uiautomator` pass that flags any text node taller than 2.5× its width, anything under the tab
      bar, anything off-screen), and the plurals render with the right Russian form on screen
      ("Синхронизировано 30 дней", "записей подряд: 2 дня", "нужно 10 дней с записями, есть 1").
- [x] Gallery: the four tabs, the History chart with a real month, the weight chart with the goal named in
      its legend, and the icon at three sizes. Screenshots stay in `tmp/` and out of git (hard rule 1).

#### The five warnings lint keeps, and why

| Warning | Kept because |
| --- | --- |
| `VectorPath` ×2 | The launcher icon's largest path is 3363 characters — about 130 line segments. A launcher icon is inflated **once**, by the launcher, at 48 dp; the check exists for vectors redrawn on screen. Re-cutting the geometry to shorten it would change a picture the owner approved, for no runtime gain |
| `ObsoleteSdkInt` | Lint says `mipmap-anydpi-v26` can be merged into `mipmap-anydpi` since `minSdk` is 28. **Tried it:** AAPT2 then fails resource linking with `resource mipmap/ic_launcher not found` — an `<adaptive-icon>` outside a `-v26` folder is not resolved at all. Reverted, and the reason recorded |
| `OldTargetApi` | `targetSdk` 36 while platform 37 exists. Platform 37 is not installed and the SDK lives under `Program Files`, so adding it needs an elevated shell — one deliberate UAC prompt, not a code change |
| `GradleDependency` | `compileSdk` 37, same reason as above |

### What it costs and what it buys

| Piece | Days | Risk |
| --- | --- | --- |
| Tabs + icons | 2 | Navigation rewiring touches every `onOpen*` callback; one screen at a time, verified after each |
| Intake/burn chart | 2 | Stacked bars per day over 90 days is where a naive Compose `Canvas` starts dropping frames on the Redmi; measure on the floor device, not the Honor |
| Weight tab and chart | 1.5 | Duplicates chart maths with History; extract one `DayChart` before writing the second |
| Launcher icon | 0.5 | Silhouette legibility at 24 dp; iterate at real sizes, not at 512 px |
| Dark theme | 0.5 | Contrast of the three macro hues; verify on a dark surface, not by eye |
| **Total** | **6.5** | |

Nothing here changes stored data or the schema, which is deliberate: an interface milestone that also
migrates the database cannot be reviewed or rolled back cleanly.
## M8 — Optional v1.1 (post-release)

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
| **M7 interface** | **6.5** | **44.5** |
| Buffer (20%) | 9 | **53.5** |

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