# Milestones

The diary of what each milestone changed and what it cost. The code says what happens,
`docs/03-architecture.md` says why, and this file says **when it happened and what it took**.

An entry is written when the milestone lands, in order, with the numbers measured at the time — device
model, test counts, string counts. The numbering is this file's own chronology, not stable IDs. Where the
milestone produced a lesson worth re-reading, the lesson lives in `docs/07-pitfalls.md` under a stable ID
(`P-nn` for a trap, `J-nn` for a judgement call) and the entry points at it instead of repeating it.

## M1 - setup and the first profile

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

## M2 - food

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

## M3 - recipes

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

## M4 - band data and the daily balance

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

## M5 - history and adaptive TDEE

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

## M6 - hardening, export, release, and the whole M7 series

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
9. ~~**M7.1 — tabs**~~ — four destinations (`Сегодня` · `Еда` · `История` · `Вес`) in a `NavigationBar`, with
   settings, profiles, products and recipes as **overlays** on top of the current tab. The flat `Screen` enum
   became `Tab` + `Overlay`; back pops one overlay, then returns to `Сегодня`, then leaves the app, and tab
   switches do not stack. The tab survives process death (`rememberSaveable`). Icons are **hand-drawn** vector
   drawables (home, bowl-and-fork, bars, balance, sliders) rather than `material-icons-extended` — ~4 MB of
   vectors to use five glyphs. Recipes moved under `Еда`, profiles moved into Settings. ADR-027.
10. Two traps in M7.1, both visible only on a device: the tab-bar labels and the screen the app lands on after `install -r`. Extracted to `docs/07-pitfalls.md` **P-08** and **P-19**. Just before them: the first Android 12+ launch on the Honor and the four-tab structure that ADR-027 records.
11. **The `Вес` tab ships with working entry, not a placeholder** — field, prefill from the last reading, rate
    line, recent readings, save. It exposed two real bugs, both fixed:
    - **Saving twice in one day appended a second row.** `record` now deletes the day's row from that source
      before inserting, so a re-typed weight corrects the day; different sources still keep their own rows.
    - **The readings list printed the EWMA** instead of the readings; it now uses `weightPoints` (unsmoothed).
    The chart is still M7.3. Two device tests pin the replace rule and the canonical preference.
12. Tests: 107 :core, 42 :data, 10 :app JVM; **111 :data, 4 :app device** (115 device). Strings **296/296**.
13. ~~**M7.2 — the intake/burn chart**~~ — one Canvas on `История`: eaten as a stacked macro bar (protein/fat/
    carbs from `MacroEnergy` in `:core`, in `:app/history/IntakeBurnChart.kt`), burned as a second bar, one
    baseline and one scale, the target dashed across both; a 7/30/90 selector where 90 scrolls sideways and
    opens at the newest day. `MacroEnergy.split` guarantees the segments fill the bar exactly — macros below
    the logged energy leave an "unlabelled energy" segment, macros above it are scaled down — and a property
    test over 500 random days pins that. The window query is one `GROUP BY local_date`
    (`FoodEntryDao.macroTotalsBetween`), so a day with no log is **absent**, which is what makes the chart draw
    a gap instead of a zero bar. ADR-028.
14. Three things the intake/burn chart taught. The lessons are **J-08** (a total over estimates is a lie of emphasis), **J-09** (an estimated burn is an outline, not a fill) and **P-23** (`fillMaxWidth()` inside `horizontalScroll` collapses to zero).
15. Tests: 115 :core, 42 :data, 10 :app JVM; **113 :data, 4 :app device** (117 device). Strings **306/306**.
16. ~~**M7.2b — the Today gauge**~~ — a half-doughnut of today's energy split by macro, with the **grams**
    of protein, fat and carbs printed beside it. The scale is the target and it clamps at half a circle
    (`CalorieGauge` in `:core`, 6 cases): eating past the allowance fills the arc and reports the excess
    in words, because a sweep past 180° wraps and would look like *less* than a full day, and "142% of
    target" is harder to feel than "перебор на 500 ккал". One `ChartPalette` now feeds both this gauge and
    the history chart, in the lighter 300-level tones; the grams and the spoken description carry the
    meaning, so the colour is decoration and a big carbohydrate band is not a verdict. ADR-029.
17. Three things the gauge taught: **J-01** (a dash is not a number), **J-02** (the centre of a half circle is narrow) and **P-25** (`2 * arcThickness` does not compile for `Dp`).
18. **A second profile used to steal the band** — found while verifying the gauge, when a throwaway
    profile created to look at an *empty* day took `band_enabled` from the owner's profile (the form
    set it on every create), stopped his sync, and then left **nobody** reading the band once the
    throwaway was deleted — Today simply lost its sync button, with nothing on screen to say why.
    `create` now grants the band only when no active profile has it, and the profiles screen has a
    **Читать браслет** action, so moving it is one deliberate tap. Three device tests pin the rule.
    ADR-030.
19. ~~**M7.3 — the weight chart**~~ — dots for what the scale said, the EWMA through them, the goal
    dashed across, over 30/90/365 days. `WeightAxis` in `:core` (10 cases) gives the range a **2 kg
    floor** and rounds bounds to half kilos — or whole pounds — because the axis is where a weight
    chart lies: rescaling to a 0.2 kg week turns water retention into a cliff. The **data sets the
    range, not the goal**: folded in, a 5 kg window became 8 and flattened the fortnight the person
    was looking at, so an off-range goal is *named* in the legend ("Цель 75 кг · ниже графика")
    instead of drawn. A swatch promises something on the canvas, so with one weigh-in the trend
    entry disappears and a sentence says why. ADR-031.
20. Three things the weight chart taught: **P-24** (gutters must be measured, not fixed), **P-18** (a format string and its call site can drift apart and still pass every test) and **P-29** (a seeded history is the only way to see a chart).
21. Tests: 131 :core, 42 :data, 10 :app JVM; **115 :data, 4 :app device** (119 device). Strings **320/320**.
22. ~~**M7.4 — the launcher icon**~~ — a vector **adaptive icon**: an ink balance beam with a slice of pie on
    one side and a steel-blue dumbbell on the other, **both solid objects seen at an angle**. The owner's brief
    was a balance *with pans*; three sketches at 24/48/108 dp showed that at 24 dp each pan and its food merge
    into one blob, so he picked the reduced structure after seeing exactly that failure. Two drafts of the
    objects were then rejected — a flat triangle, then a dumbbell that was a capital H — which produced the
    final language: the slice is a circular sector with a crust band about a third of its radius and its side
    swept downwards, the dumbbell is a tilted bar with two rounded weights and a lit disc face on each outer end.
    Shipped as `res/drawable/ic_launcher_foreground.xml` (8 paths), a cream background colour and a
    `<monochrome>` layer; no PNG densities, because `minSdk` 28 makes the adaptive icon the only form any phone
    can use. `tools/icon_design.py` is the source of the geometry — it renders the review previews and prints
    the exact `pathData` the drawable carries. Verified on the Honor: Settings → *О приложении*, the recents
    card, and the third home page. ADR-032.
23. Three things the launcher icon taught: **J-10** (at 24 dp an icon is a silhouette test, not a drawing), **J-11** (two rectangles on a bar are a capital H) and **J-12** (a swept solid is not a translated outline).
24. **`docs/03-architecture.md` had two ADR rows glued in front of its own title**, left by an earlier append.
    Nothing caught it because Markdown renders the damage as a table at the top of the page, and no test reads
    the docs. Repaired and both rows moved into §13; when appending to a table, check the first line of the file
    after the write, not just that the text landed somewhere.
25. ~~**M7.5 — the dark theme and contrast**~~ — the app follows `isSystemInDarkTheme()` with Material 3's baseline
    pair, and `values-night/themes.xml` keeps the cold-start window dark. The milestone's real finding was the
    palette: measuring it for the first time showed the light theme's six chart tones at **1.4–2.9 : 1** on
    cream, below the 3 : 1 graphic floor, with the fat tone effectively invisible. `Contrast` in `:core` is the
    WCAG arithmetic (nine cases, pinned against the published grey thresholds) and `ChartPaletteContrastTest`
    refuses any tone under 4.5 : 1 per surface. The palette is now **two sets of six tones at the lightness each
    surface needs**, carried by `LocalChartTones`; the dark set is the 300-level tones the light theme used to
    use, because they were always fine on near-black. No in-app switch: the phone already knows. ADR-033.
26. Three things the dark theme taught: **J-03** (quiet colours and readable colours were in a fight), **P-22** (a `@Composable` read inside a `DrawScope`) and **J-04** (a theme is a place, not a coat of paint).
27. Tests: 140 :core, 42 :data, 16 :app JVM; **115 :data, 4 :app device** (119 device). Strings **320/320**.
28. ~~**M7.6 — strings and the verification sweep**~~ — lint's first run found **37** things, and the triage was
    the work: 22 `PluralsCandidate`, 6 unused strings, a `<vector>` warning, a manifest attribute and four
    small code smells. Ten strings became `<plurals>` in both languages ("Synced 1 days", "restored 1 rows",
    "1 weigh-ins"); the twelve where the number is followed by an invariant unit (`kcal`, `kg`) or a
    count-last phrase keep `tools:ignore` and a reason in the file. `StringResourceParityTest` now reads
    `<plurals>` too and asserts the quantities each grammar needs — a missing Russian `few` fails **silently**
    at runtime by falling back to `other` ("5 дня"). `android:dataExtractionRules` excludes everything, with
    `fullBackupContent="false"` for API 28–30. Second lint run: **5 warnings**, each a decision recorded in
    the plan table. ADR-034.
29. **The refusal branch was lying, and only reading it out loud showed it.** With fewer weigh-ins than the rule needs, History said *"needs 3 logged days"* — the wrong noun, aimed at the wrong thing to fix; "weigh-ins too close" and both implausible-change refusals said it too. Six refusals now give six sentences, with the thresholds passed in from the constants the rule uses, so the text cannot drift from the arithmetic. Lesson: **P-21**.
30. **`mipmap-anydpi-v26` stays, and an XML comment cannot contain `--`.** Lint's `ObsoleteSdkInt` advice was tried and failed (`resource mipmap/ic_launcher not found`), which is **P-05**; AAPT2 reporting a malformed comment as an unparseable resource is **P-06**.
31. Tests: 140 :core, 42 :data, 19 :app JVM; **115 :data, 4 :app device** (119 device). Strings **320/320**
    (308 `<string>` + 12 `<plurals>` in each language).
32. ~~**M7.7 — the macros where the food is**~~ — every logged portion on Today prints its protein, fat and carb
    grams, and the macro with the **largest share of its own daily norm** is bold and coloured: protein green, fat
    yellow, carbs red — the gauge's and chart's own three tones. The denominator did not exist, so `MacroNorm` in
    `:core` derives it (25/30/45 % of the target, same Atwater factors as everywhere), Settings shows it and takes
    the person's own, and a stored norm outranks the derivation. A manual product with macros and no kcal computes
    its energy (`MacroEnergy.atwaterKcal`) and stores it as *estimated*; a typed kcal still wins. A freshly typed
    product now lands on its portion screen instead of back on Today with nothing logged. ADR-035.
33. Three things the macros taught: **J-05** (a percentage needs a denominator), **J-06** (compare shares, not grams) and **P-30** (`kotlin.math.round` sends ties to even).
34. Tests: 160 :core, 42 :data, 19 :app JVM; **115 :data, 4 :app device** (119 device) — the 5 new
    `AppSettingsRepository` cases need the phone. Strings **324 `<string>` + 12 `<plurals>` per language**
35. Next: the **device pass for M7.7** (the phone was unplugged when it landed), then the rest of **M6** — device
    matrix on both phones, TalkBack, release signing, README — then the release.
36. ~~**The M7.7 device pass**~~ - the Honor came back and disagreed with the tests five times. Three rendering
    defects in the macro line itself (labels declared as `Белки %1$d` and printed with the placeholder, AAPT
    trimming the separator's spaces, grams with an ASCII dot under a kcal value with a comma), one design defect
    in Settings (empty fields under a line that already named the derived norm) and one typographic one (the
    separator dangling at the end of a wrapped line at 200 %). All five fixed; **P-33** and ADR-036 record the
    rules. Verified afterwards on the Honor X7c: three rows with three different winners in the right colours
    (`#81C784`, `#FFD54F`, `#E57373` sampled from the screenshot, not eyeballed), a stored norm of 60/200/230
    flips beef from fat to protein, "Из цели" clears it and the derivation returns, a macros-only product stores
    `energy_estimated = 1` and opens its portion screen, Back keeps the product and logs nothing, "no energy and
    no macros" is refused in words, and 200 % and English both hold. The 5 new `AppSettingsRepository` cases pass
    on the phone. Tests: 160 :core, 42 :data, **25** :app JVM; **120 :data, 4 :app device** (124). Strings
    **324 `<string>` + 12 `<plurals>` per language**.
37. ~~**M7.8 - a barcode of your own**~~ - the manual product form gained an optional barcode field, the local
    search started matching the digits, and a code that already belongs to another product is refused by name.
    The last part is the interesting one: `saveUserProduct` resolves a barcode to an existing row *on purpose*
    (a correction to a scanned product must update it), so a typed code would have rewritten another food's
    name and numbers silently; `barcodeOwner` asks first and the form answers "этот штрихкод уже занят
    продуктом «Nutella»". A bad check digit stays a hint, because a shop's internal code is a real identifier.
    Verified on the Honor X7c end to end, including the search by digits; two device cases added (122 :data).
    ADR-037.
38. **The second phone, and the state it changed** - the Redmi Note 8 Pro came online for the M6 matrix
    (Android 11 / SDK 30, MIUI 12.5.3.0, 1080x2340 @ 440, `ru-RU`). Getting it to appear took P-35: VMware's
    USB arbitrator held the adb interface exclusively, so `Error 5` came back to this user *and* to an
    elevated daemon. With the service stopped the phone is a plain unelevated adb target. The first Android
    11 findings, in the order they were found:
    1. Onboarding renders and works in the **light** theme at 1080x2340, and its live preview prints
       "Примерная цель: 1540 ккал в день" before the profile exists. The «Сохранить» button sits at the very
       bottom of a scrollable column and needs a scroll before a tap can reach it (`uiautomator` reported it
       clipped to 23 px) - not a defect, but the reason a careless tap at the screenshot's coordinates lands
       on the navigation bar.
    2. Today on a phone with no band shows "Сожжено 1815 ккал", "Баланс (оценка) -1815 ккал" and
       "нет данных браслета - показана оценка за весь день", which is hard rule 4 doing its job, and the
       macro line prints "Белки 0 г Жиры 0 г Углеводы 0 г" - the M7.7 fix holds on a second device.
    3. **A real defect**: with Health Connect absent the app said "требуется обновление" about an app that
       was not installed. `HealthConnectClient.getSdkStatus` returns
       `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` both for a missing provider and for an outdated one, and
       only the package table separates them. `HealthConnectState` gained `NOT_INSTALLED`, the mapping moved
       to a pure `HealthConnectStateMapping.decide(available, updateRequired, providerInstalled)` in
       `:data/health/HealthConnectState.kt`, and four JVM tests pin the precedence. Verified on the Redmi:
       the row now reads "Health Connect   не установлен".
    Totals after the change: 46 :data JVM (+4), 25 :app JVM, 122 :data and 4 :app device tests unchanged.
    Still to do on this phone: install Health Connect, the permission flow, the camera, and MIUI's
    background-sync behaviour.
39. **The platform baseline came back wearing a name** - with Health Connect installed and permissions
    granted on the Redmi, the sync read 30 days and Today stopped saying "оценка": "Сожжено 271 ккал",
    "Баланс -271 ккал". The database explained it: every one of the 30 rows had `steps`, `active_kcal` and
    `distance_m` empty, `total_kcal` frozen at **1564.5**, and `data_origins = _platform`. So Health Connect's
    own layer was answering with a constant, and the app - which had already learned to distrust an *empty*
    origin on the Honor - counted `_platform` as an attribution. Two fixes, one rule: `PLATFORM_ORIGIN` is
    excluded in `:core`'s `hasAttributedOrigin`, and both read paths (Today and History) use the same
    predicate instead of `isNullOrBlank()`. One JVM test pins it, and after a re-sync all 30 days are stored
    `partial` while Today reads "Сожжено 1815 ккал / Баланс (оценка) -1815 ккал". The lesson generalises: a
    constant is the signature of a baseline, and a baseline must never borrow the authority of a measurement.
    Totals: 161 :core (+1), 46 :data, 25 :app JVM.
40. **Three bugs from the owner's phone, two of them structural** - the owner reported landing on `Test`
    although he had chosen his own profile, the manual product form losing everything it held when the phone
    rotated, and the last fields of that form sitting under the keyboard.
    The profile bug was P-39: a repair effect could not tell "the setting has not been read yet" from "no
    setting", so on a cold start it persisted the first profile in the table over the chosen one, and the same
    ambiguity let the create-profile form flash while the list was still empty. `MainActivity` now holds a
    three-state read and shows nothing until both queries answer, and `repairedActiveId` is a pure function
    with four JVM tests. The rule is covered by those tests and the loading gate is visible on the device; the
    end-to-end check (two profiles, the newer one active, cold start keeps it) is **not** done: writing the
    extra row into the app database needs a document provider on MIUI - `run-as cp` from `/sdcard`, the
    external files directory and root all refused - so it stays on the owner's phone to confirm.
    The form bug was `remember` where `rememberSaveable` belongs, in four screens (product, portion, profile,
    recipe); the state now survives a configuration change, verified by rotating the Redmi in the middle of a
    filled form (`Rotation` and `250` came back). The keyboard bug was the missing half of the same story:
    `android:windowSoftInputMode="adjustResize"` plus `imePadding()` on the scroll container, and the focused
    `Углеводы` field now sits above the keyboard instead of behind it. Totals: 161 :core, 46 :data, 33 :app JVM.
41. **The interface pass the owner asked for, and the correction to it** - five items in one request:
    rounded shapes, the chart's resolution, macro presets, meals, and the scanner.
    1. *Charts.* The gauge's arc got round caps with the gap recomputed from stroke width and radius (a round
       cap reaches half a stroke past its end and swallowed the old 1.2 degree gap), legend swatches became
       circles, and the third sentence inside the arc moved under it. Thirty and ninety days now draw one bar
       per **week** through `ChartBuckets`, seven consecutive days so the newest bucket ends on today; the
       target line is multiplied by seven, the legend says "в неделю", and a week holding one estimate is
       drawn as an outline. History opens on seven days. Six JVM tests.
    2. *The correction.* A first pass rounded every bar into a capsule, and the owner's answer was precise:
       "in the upper part the corners are round, but in the lower part sharp, as if the bars are standing on
       the axis". Bars are now drawn as capsules reaching half a width **below** the baseline inside a
       `clipRect` at the axis, so the top stays circular and the foot is square, while the stacked bar's
       internal dividers stay straight. Verified on the Honor with real data at 7 and 30 days.
    3. *Scanner.* A decoded code turns the frame green, prints "Найдено <code>" on a dark pill, vibrates and
       freezes the analyzer, then opens the product 400 ms later. Tap-to-focus works through a three-line
       `MeteringPointFactory` subclass, because `PreviewView` in `COMPATIBLE` mode still escaped its box and
       painted over the title (P-38). The found green is fixed rather than themed: the light theme's protein
       green was an olive that vanished into video.
    4. *Macro presets.* The owner does not think in grams: "30/30/40" is a split a person recognises, "96 g of
       protein" is not. Four presets now sit above the fields - по цели 25/30/45, ровно 30/30/40, больше белка
       40/30/30, много белка 45/25/30 - and a chip computes the grams for the current target, writes them
       through the same `setMacroNorm` a typed norm uses, and the fields show what was stored. `MacroPreset`
       lives in `:core` with seven tests: the shares are of **energy**, so the grams divide by each macro's
       Atwater factor, and they are whole grams because a rounded preset reads as a number somebody chose.
       A chip is selected by recomputing which preset the stored grams represent (exact grams first, then the
       energy split within two percent), so editing a field clears the selection without extra state.
       Verified on the Honor X7c twice: the owner's own `171,76,228` is recognised as "ровно" without being
       rewritten, and on the Test profile the 45/25/30 chip stored `214,53,143` for a 1904 kcal target.
    5. *Meal cards.* Today no longer lists meals as bare headings: four cards - Завтрак, Обед, Ужин, Перекус -
       each carry the planned time, the meal's kcal and its share of the **day target**, its protein/fat/carb
       sums in whole grams, and its entries. An empty meal stays visible and says "пока пусто", because a card
       with a time on it is an invitation and hiding it removed the very prompt the owner wanted. Each card has a
       `+` that opens the food tab with that meal already selected on the portion screen (`ProductScreen` takes
       an `initialMeal`, `MainActivity` carries it as `pendingMeal`, cleared when the flow ends), while the
       general "добавить еду" button stays for a person who does not think in meals. The times are a plan, not a
       measurement: they label and sort nothing, they live per profile in `app_setting` as `meal_times_<id>`
       (`480,780,1140`), they default to 08:00/13:00/19:00, a partly broken stored value falls back as a whole,
       and the Settings fields accept only `HH:MM` (`MealTimes.parseClock`). No schema change. Verified on the
       Honor X7c: the four cards with the owner's real day, `+` on Обед opening Яблоко with Обед preselected and
       nothing logged, and Save storing `480,780,1140` with his 29 entries, 90 products, 64 activity days and
       two weigh-ins unchanged.
    6. *Round ends only.* The owner's second correction on the gauge: rounding belongs to the two ends of the
       coloured part, while the three macro bands should meet **sharply and without gaps**. Previously every
       segment was its own round-capped arc with a computed degree gap between them (a round cap reaches half a
       stroke past its end, so the gap had to be at least thickness/radius or the caps swallowed it), which gave the
       doughnut four visible pill ends and three hairlines. The bands are now drawn butt-capped and back to back -
       green meets yellow meets red with nothing between them - and the outer ends are rounded afterwards by a
       half-thickness disc in the first and in the last band's own colour, which is exactly what a round cap is
       minus the internal ones. The grey track is unchanged. Verified on the Honor X7c at 689 of 2283 kcal, zoomed
       to pixel scale: three flush joins, two round outer ends, no hairline anywhere.

42. **The meals became data (schema v7)** - the owner's follow-up: "we should not limit ourselves with 3 meals",
    so the count, the names and the times are his, and Завтрак/Обед/Ужин are only what a new profile starts with.
    A `meal` table (one row per profile per slot) now holds `name`, `sort_order`, `planned_minutes` and `fixed`;
    `food_entry.meal` keeps storing the slot's **key**, which for every existing row is already `breakfast` /
    `lunch` / `dinner` / `snack`, so the migration rewrote no entry and 29 of the owner's rows moved intact.
    Identity is the key, never the name: a rename writes one column and cannot rewrite a year of entries.
    `name` empty means "print the localized default for this key", which keeps translations out of the database.
    "Перекус" is the single fixed slot: no name field, no time, no delete button - it is where unplanned food goes.
    Deleting a slot that still holds entries is **refused** with a sentence rather than silently re-homing somebody's
    lunch, and the last remaining slot cannot be deleted either; a new slot is inserted before the fixed bucket by
    pushing the bucket's order, so unplanned food stays last. The v6 -> v7 migration also carried the times a person
    had set in `meal_times_<id>` into the rows they described and removed the old keys.
    Verified: `MigrationHarnessTest` 7/7 on the Honor X7c, the new case included and schema-validated against the
    exported v7 json; the migration simulated on a copy of the owner's database, then run for real on the Honor X7c
    (`user_version` 6 -> 7, 29 entries, 90 products, 64 activity days, two weigh-ins, macro norm and active profile
    unchanged, zero orphan entries); on the device a fifth meal was added ("Second", 11:00), renamed, retimed,
    shown as its own card before Перекус, and deleted; deleting a meal with food in it was refused and left the
    entries alone. `MealSlot`, `MealDefaults`, `guessMealKey` and the card maths are in `:core` with tests (177),
    `MealRepository` owns the three rules, and ADR-040 records the shape.

    6. *The meals screen split in two.* The first version edited every meal in place - four names, four times and a
       save button - and the owner's answer was that this is not the interface he wants. It is now a **list** (name
       and time read-only, "Изменить" and "Удалить" on each row, one "Добавить" button) and a separate **editor**
       that serves both add and edit, with the fields prefilled when editing. Time is chosen with the Material 3
       **clock face** rather than parsed from text: a picker cannot produce `8.30` or `0830`, so `parseClock` and its
       eight assertions were deleted along with the two strings that only the text field used. A default slot keeps
       an empty `name` in the database, and the editor shows that default as supporting text under the name field -
       a placeholder would be hidden behind the M3 label - so saving an untouched name keeps the localized default,
       and only a **new** meal is required to have one. Verified on the Honor X7c: the list with three editable rows
       and the fixed bucket; "Изменить" on Завтрак opening prefilled (supporting text "Завтрак", time 08:00); the
       clock face setting 09:00 and saving `planned_minutes = 540`; then back to 08:00 the same way, leaving the
       four default slots exactly as they were.
    7. *No "Без времени".* The owner's last correction on this screen: a meal without a planned time should say
       nothing at all, not label the absence. The list therefore prints a time only when there is one, and the
       editor lost its clear action entirely - a meal that never got a time simply has none, and the picker button
       reads "Выбрать время" when there is nothing to show yet. `meals_no_time` was deleted from both languages,
       so the sentence cannot come back by accident.
    8. *The name field is a value, not a hint.* A hint under an empty field asked a person to retype "Завтрак" to
       change it, which is not an edit - the owner's last note on this screen. The editor now **prefills** the name
       with what the list shows, so the localization of a default slot is what it starts from and can be edited
       directly. Saving it untouched stores an empty name again (`trimmed == default name` means "keep the
       default"), so the slot still follows the phone language; anything typed is stored as typed, and clearing an
       existing name returns it to the default. Verified on the Honor X7c: "Завтрак" prefilled and saved unchanged
       leaving `name = ''`; Обед renamed to "LunchX" and stored; cleared back to empty and listed as "Обед" again;
       29 entries and the active profile untouched.
43. **Search that behaves like one feature, and a Food tab that says where you are** - four notes from the owner
    after living with the app for several days.
    1. *The Open Food Facts fallback is automatic.* The remote search used to be a button that had to be pressed even
       when the phone had answered nothing, which is a step nobody wants when the list is visibly empty. The local
       catalogue is still asked first - it is free and instant - and only when it returns nothing does the network
       get called, after a 450 ms pause so a five-letter word is not five requests to a free service. The button
       survives for the two cases the rule cannot cover: the phone answered something but not the right thing, and a
       network failure that deserves a retry.
    2. *The recipe editor catches up.* Ingredient search had no remote half at all. It now shares the same
       `RemoteState`/`searchOpenFoodFacts` helper and the same constants, so "find a food" behaves the same in both
       places, and the editor gained a **scan** button. The scanner is drawn **inside** the editor's Box rather than
       as an overlay: an overlay leaves composition and would throw away the recipe being written, which is exactly
       what a person scanning the fourth ingredient cannot afford. Found code -> the product is added as 100 g;
       unknown code -> the code is reported on screen instead of navigating away. One defect found on the device:
       with no overlay branch around it, the scanner had no background of its own and the recipe showed through the
       camera preview - fixed with an opaque `Surface`.
    3. *A search that found nothing names the manual product.* The empty remote result now offers
       "Добавить «<запрос>» вручную", and the product form opens with the name prefilled; `initialName` is the same
       mechanism the scanned barcode already used for `initialBarcode`.
    4. *"Еда" and "Рецепты" are tabs.* They were a header link, which read as either a tab bar or a filter with no
       "everything" option (the owner's own observation). They are a `TabRow` inside the Food tab now: the recipes
       list lost its back button and its duplicated title, the recipe detail's back button says «Рецепты» instead of
       «Назад», and the two lists state where you are instead of offering a jump.
    Verified on the Honor X7c (Android 14): both tabs and the recipe back label; a search for `qzxqzx` reaching Open
    Food Facts by itself and offering the named manual form (name prefilled); the editor's scanner opening over the
    recipe without losing it, and the camera returning to the same draft. The recipes in the database were unchanged
    after the run (3 recipes, 13 ingredients, 266 g in the edited one), and nothing was logged by accident.
    5. *The draft belongs to the screen above.* The owner then asked for the scanner back as a **separate screen**,
       and for the draft to be saved across rotation and cleared **only** on an explicit cancel. Those three asks are
       one problem: state that must outlive a screen cannot live in it. The scanner is a destination again
       (`Overlay.RECIPE_SCAN`), and the recipe being written moved into `RecipeDraft`, held by MainActivity and
       `rememberSaveable` through a small string-list saver. Ingredients are stored as **product id + grams as typed**,
       never as a copy of the product: the editor re-reads the products by id, so a label corrected today is already
       corrected in a draft from yesterday. Rotation is the same mechanism, which is also why nothing is lost by
       turning the phone mid-recipe. The draft is cleared in exactly two places - a successful save and «Отмена» -
       and nowhere else. One neighbouring defect fell out of the testing: `openedRecipe` was a plain `remember`, so a
       rotation in the recipe detail dropped back to the list; it is now an id in `rememberSaveable`, resolved with
       `recipes.byId`.
       Verified on the Honor X7c: a gram value edited to 70 survived the trip to the scanner as a separate screen and
       back, survived a rotation to landscape (name, servings and 70 all present), «Отмена» then left an empty editor
       for «Новый рецепт», and the recipe detail itself stayed open through a rotation.
44. **A states pass that found three defects in the paths nobody walks** - M6's "empty/error/offline states
    everywhere; no dead ends", started 2026-11-13.
    1. *A profile without a weigh-in was a dead end.* Today said «Веса пока нет» and «онбординг его спросит», and
       then stopped - but onboarding had already happened, and the profile form **requires** a weight, so the state
       is reached only through an import that carries no weigh-ins. The screen now offers «Добавить вес», which
       switches to the Вес tab. Verified on the Honor X7c by removing the Test profile's weigh-in (the only way to
       reach the state) and then by restoring one through the weight screen: «Сохранено: 75 кг», and Today went back
       to a target.
    2. *A failed save crashed the app.* `RecipeEditorScreen` re-threw the exception from `onFailure` inside the
       coroutine, which is an uncaught exception on the main thread: any database refusal while saving a recipe would
       have closed the app mid-edit. It now sets a message and keeps the draft. The two logging screens printed
       `it.message` - a system sentence in whatever language the platform felt like - so `food_log_failed` and
       `recipe_save_failed` name the failure in the app's own words and append the cause.
    3. *Everything else already had a state.* The empty catalogue, the empty day (with «Скопировать вчера»), the
       recipe list, the chart with no days, the adaptive-TDEE refusal (which names which of six reasons applies),
       the backup refusals, the scanner's denied-camera path and the offline barcode queue were all in place; the
       audit's value was finding the three places where a screen stopped instead of offering the next step.
    **One incident, recorded because it nearly cost the data.** Replacing the database to remove that weigh-in, I
    pushed it through host-side redirection into `adb shell`: the file arrived as 44 631 bytes, Room could not open
    it, and the app showed onboarding - both profiles and the whole history unreadable on the phone, with the
    `-wal` already deleted. The pulled copy in `tmp/` was intact (208 896 bytes, verified by opening it), and pushing
    it to `/data/local/tmp` and copying **on the device** restored everything; the counts afterwards were 2 profiles,
    42 entries, 100 products, 3 recipes, 13 items, 65 activity days. The pitfall is **P-41**, and its rule is the
    one that saved the day: keep the original pull untouched, and verify the destination size before starting the app.
45. **The numbers, measured rather than hoped for** - M6's performance item, 2026-11-13, Honor X7c (ALT-LX1,
    Android 14 / SDK 34), **debug** build, `versionName 0.1.0`, commit `44ed9c9`.
    | What | Target | Measured |
    | --- | --- | --- |
    | Cold start, `am start -W -S`, five runs | < 1 500 ms | **median 1 370 ms** (1 356 - 1 457) |
    | Frame time while scrolling and switching tabs, 381 frames | day render < 100 ms | p50 **9 ms**, p90 **14 ms**, p95 **16 ms**, p99 **93 ms**, janky **2.62 %** |
    | Database, 2 profiles / 42 entries / 100 products / 65 activity days | < 50 MB after a year | **204 KiB** now; a year of the model below is **3.34 MiB** |
    The year is a model, not a measurement, and it says so: measured bytes per row from `dbstat` (food entry
    292 B, activity day 189 B, product 204 B, weigh-in 2 048 B, recipe ingredient 315 B) times a heavy diet of
    15 entries a day, one weigh-in a day for two people, 300 new products and 50 recipes - **3.34 MiB**, with
    the weigh-ins alone accounting for 1.43 MiB because the unique index gives each row its own page. The gap to
    the 50 MB ceiling is two orders of magnitude, so there is no reason to build a retention policy now and every
    reason to note the two tables that would grow if a band ever wrote per-minute data.
    Two caveats worth keeping: the numbers are from a **debug** build without R8, so the release APK should be
    faster, and `am start -W` measures until the first frame, not until the first meal card has its numbers.
46. **Accessibility: the rows that were not buttons to a screen reader** - M6's TalkBack and 48 dp item,
    2026-11-13.
    The audit found the app in better shape than expected - the gauge, both charts and the tab bar already carry
    `contentDescription`, and the charts describe their data rather than their pixels - but **five tappable rows were
    invisible to a screen reader as controls**: a product row, a "recently eaten" row, a recipe row and the two
    "add this ingredient" rows in the editor were plain `Column`s with `Modifier.clickable`. TalkBack reads them as
    text, so a blind user hears the numbers and never learns that the row does anything; and `Text` plus 8 dp of
    padding lands a couple of dp under the 48 dp minimum for a touch target. All five now declare `role = Role.Button`,
    an `onClickLabel` («Открыть» / «Добавить в рецепт») and `heightIn(min = 48.dp)`.
    **What was not done, and why:** TalkBack itself was not turned on, because this is the owner's daily phone and a
    screen reader changes every gesture on it (hard rule 10). The claim here is therefore narrower than "TalkBack
    works": the semantics are present and compile, the labels exist in both languages, and the layout was checked on
    the device after the change - the food list still scrolls, the rows are a little taller, and nothing overlaps.
    Two follow-ups if a blind user ever appears: a real TalkBack pass, and `AccessibilityScanner`.
47. **F-Droid instead of Google Play: the licence, the unsigned release and the metadata** - 2026-11-13.
    The owner's decision changed the shape of the release, not just its venue: F-Droid builds from source and signs
    with its own key, so **the repository must build without a private keystore**, and everything the store shows has
    to live in the repository too.
    1. *Licence.* Apache-2.0, taken as the canonical text from apache.org rather than retyped (11 358 bytes, 202
       lines) - a licence with one typo is not that licence.
    2. *Version.* `versionCode 2`, `versionName "1.0.0"`: the first version meant for someone other than the two of
       us, and a reviewable diff rather than a side effect of a build.
    3. *Release build.* R8 and resource shrinking are on. The keep rules are four short blocks, each with the reason
       next to it: `zxingcpp.**` is called by name from C++ through JNI, Room instantiates its generated `_Impl` by
       name, Health Connect reflects over its record types, WorkManager builds our worker by name - and the line
       numbers stay, because a stack trace from a phone is the only evidence there will ever be.
    4. *Signing.* `keystore.properties` is read **only when it exists**. Without it the release build is simply
       unsigned, which is what the F-Droid buildserver wants; with it (owner's own keystore, outside the repository)
       the same command produces a signed APK for a sideload.
    5. *Metadata.* `fastlane/metadata/android/{en-US,ru-RU}/` with title, short description, a long description that
       says what the app does not do as plainly as what it does, a changelog for `versionCode 2`, and five phone
       screenshots per language, taken from the fixture profile - no real person's day is in a published screenshot.
    6. *The recipe.* [`docs/fdroid/`](../fdroid/) holds the draft `metadata/com.balancecalories.yml`, the list of what
       must happen before the merge request, and reviewer notes about Health Connect, the two Open Food Facts
       endpoints and signing.
    **Verified on the Honor X7c**, release build signed with the *debug* key for the test so that `install -r` could
    replace the debug build **without uninstalling and without touching the data**: cold start **366 ms** against
    1 370 ms for the debug build, the scanner opening with no `UnsatisfiedLinkError`, History drawing its chart and
    the adaptive-TDEE refusal, the food catalogue and the recently-eaten list reading from Room, and no `FATAL` in
    `logcat`. The temporary `keystore.properties` was then deleted and the tree rebuilt: `app-release-unsigned.apk`,
    11.6 MB, `apksigner verify` reporting "DOES NOT VERIFY" - which is the correct answer for a build that a store
    is supposed to sign.
