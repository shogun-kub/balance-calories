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

- [ ] Project setup: Gradle KTS, version catalog, Hilt, Compose theme, `:core`/`:data`/`:app` modules
- [ ] Room schema v1 + DAOs + migration test harness
- [ ] `:core` energy module: Mifflin, PAL, deficit, floors/caps, day bucketing — with tests
- [ ] Universal profile model: create/edit, switch, archive; `band_enabled` enforced (one per device)
- [ ] Profile create/edit, onboarding step 1–3 (profile, body data, goal rate)
- [ ] Target calculation + `target_history` write
- [ ] Settings: units, day-start hour, language
- [ ] Debug screen: show computed BMR/TDEE/target inputs and outputs

Exit criteria: I can create two profiles and see a defensible daily target with a visible explanation.

## M2 — Food: products, barcode, entries (9 d)

- [ ] Starter ingredient pack: build-time JSON (~150 cooking basics, per 100 g), import on first run
      as `source='starter'`, editable/deletable, plus a small script that regenerates it
- [ ] OFF client: fields, UA, token bucket, backoff, 404 caching, `pending_lookup`
- [ ] OFF → `product` mapper incl. kJ-only, serving-string parsing, `energy_estimated`
- [ ] Product screen (per-100 g table, portion input, meal picker, log)
- [ ] Manual product creation + local override flow
- [ ] Scanner screen: CameraX + zxing-cpp, overlay, torch, haptics, manual entry, debounce
- [ ] Food entries: create/edit/delete (soft), copy yesterday, quick kcal
- [ ] Today screen v1: eaten, target, remaining, macro row, meal sections
- [ ] Product search (explicit, debounced) + recents/frequents
- [ ] Golden tests for OFF payloads; fake-clock tests for rate limiter

Exit criteria: a full day of eating can be logged faster than with a notes app, offline included.

## M3 — Recipes (3 d)

- [ ] Recipe editor: components (from product search/recent), grams, cooked weight, servings
- [ ] Derived per-100 g / per-serving preview, estimated-component warning
- [ ] Log a portion by grams or fraction; recipe detail screen
- [ ] Recipe scaling tests (property-based: doubling grams doubles kcal)

## M4 — Band data + daily balance (6 d)

- [ ] `HealthConnectDataSource`: permission flow, 30 d backfill, 7 d incremental, SDK status handling
- [ ] Interval clipping + local-day aggregation (midnight split, DST) + upserts
- [ ] `quality` classification (`complete/partial/no_band/estimated`) and the PAL fallback for `no_band`
- [ ] "Sync band" via GB intents + timeout path + status line ("last synced …")
- [ ] Today screen v2: burned (so far / final), remaining, confidence banner, refresh
- [ ] WorkManager periodic sync + expedited on app open
- [ ] Onboarding steps 4–5: GB detection, HC permission, live verification, deep links
- [ ] Aggregation tests with synthetic HC records

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
