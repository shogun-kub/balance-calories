# Product definition

## 1. One sentence

> Open the app, see one number: **how much energy you have left today** — and over the week, whether
> the scale agrees with it.

## 2. Who it is for

Two people, one app: the owner (Huawei Band 9) and his wife (Huawei Band 11). Both want to lose
weight without spreadsheet archaeology. Both already wear a band 24/7 and already own the only
sensor that matters for expenditure.

Design assumptions:

- Technically comfortable (installing Gadgetbridge is acceptable), but not willing to babysit data.
- Wants a **decision**, not a dashboard: "can I eat this?" / "how am I doing this week?".
- Cares about privacy: health + food data stays on the phone.
- Mixed language household → UI must be translatable from day one (en + ru at minimum).

## 3. Core loop

```
        ┌──────────────────────────── every evening / after meals ───────────────────────────┐
        │                                                                                     │
  scan barcode ──► product found in cache/OFF ──► enter grams ──► meal logged ──► intake += kcal
        │                    │                                                             │
        │                    └─ not found ──► manual entry / "my product" ──────────────────┘
        │
  band syncs (Gadgetbridge → Health Connect) ──► active kcal + BMR ──► burned += kcal
        │
  scale (manual or GB) ──► weight ──► weekly TDEE recalibration ──► tomorrow's target shifts
```

The daily answer is a single signed number: `intake − burned`. Negative = deficit = losing.

## 4. Scope

### MVP (must exist before the app is useful at all)

| Area | In MVP |
| --- | --- |
| Profiles | Universal profile model: 1 by default, more allowed; each with sex, birth date, height, activity level, goal rate. Body data is entered by the user, nothing is hardcoded |
| Targets | BMR via Mifflin–St Jeor, TDEE via PAL seed, daily kcal target, macro targets optional |
| Food DB | OFF v3 lookup by barcode, local cache, full manual product creation, edit/override of cached product, **~150-item starter pack of cooking ingredients** imported on first run |
| Logging | add product + grams to any meal of the profile (four by default: breakfast/lunch/dinner/snack; the set is editable, the snack bucket is fixed), edit, delete, copy yesterday, quick-add kcal |
| Recipes | build a recipe from logged products, store raw component weights + measured cooked weight + servings, log a portion by grams or by fraction |
| Band data | Health Connect read of active kcal, BMR, steps, weight, sleep duration — attached to **at most one profile per phone**; "Sync band" button via GB intent; manual refresh |
| Daily view | eaten / burned / remaining, macro rings, meals list, "day complete" confidence marker |
| History | day list + 7/30-day chart of balance and weight |
| Setup | onboarding wizard that checks Gadgetbridge and Health Connect and walks the user through enabling both |
| Data | local only; JSON+CSV export; import of own export |

### v1.1 (right after MVP)

- Adaptive TDEE from weight trend (28-day window) with confidence levels
- Weekly recap screen ("−3 100 kcal this week ≈ −0.4 kg, scale says −0.3 kg")
- Projection to goal weight ("at this rate: 2027-03-14"), plateau detection
- Weight entry screen with EWMA trend line, optional body-fat %
- Notifications: reminder to log dinner, "band hasn't synced for 18 h"
- Home-screen widget with remaining kcal
- Product search by name (single OFF `/api/v2/search` call, debounced)
- Barcode-less quick add from "recent" and "frequent"

### Later / nice to have

- Write `NutritionRecord` and `WeightRecord` back into Health Connect (interop with other apps)
- Sleep and workout drill-down screens
- Micronutrients (fiber, sugar, salt) and a "protein goal" mode
- Photo of the plate as a log entry attachment
- Shared recipe library between the two phones (export/import via file or QR)
- Wear OS tile / watch-face complication
- Fasting windows, alcohol tracking, water

### Explicit non-goals

- Not a medical device, no diagnosis, no insulin/Mediterranean-diet advice, no "your TDEE is 2143.7".
- No social feed, no gamification streak guilt, no ads, no cloud account, no telemetry.
- No direct Bluetooth handling of Huawei bands (Gadgetbridge owns that).
- No food database of our own beyond user-created products and OFF.
- No meal planning / shopping lists in v1.
- No automatic food recognition from photos.

## 5. User stories (acceptance criteria in Given/When/Then)

**US-1 — log a scanned product**
Given a valid EAN-13 for a product present in OFF, when the user scans it and enters 180 g, then the
entry appears in the selected meal with `kcal = 180 × energy-kcal_100g / 100`, rounded to 1 kcal, and
the remaining-kcal number updates immediately without a network call.

**US-2 — product not in OFF**
Given a barcode OFF does not know, when the scan returns 404, then the app offers "create product"
with the barcode pre-filled, manual nutrition per 100 g, and optional pack size; the created product
is cached and reused on the next scan of the same barcode.

**US-3 — correct a wrong product**
Given a cached OFF product with wrong data, when the user edits nutrition, then a local override is
stored, all *future* entries use it, and *past* entries keep their snapshot values (the history must
not silently change).

**US-4 — recipe**
Given a recipe of 450 g total made of 4 components, when the user logs "120 g", then the entry gets
`120/450` of the recipe's totals; the recipe screen shows per-100 g and per-serving values and flags
components whose nutrition was estimated.

**US-5 — band data arrives late**
Given yesterday was already computed with partial band data, when Health Connect later returns more
active calories for yesterday, then yesterday's aggregate is recomputed and the history chart updates;
the day is marked "complete" only when the local day has ended and data is present.

**US-6 — band not worn**
Given a day with zero steps and zero active calories, when the daily view renders, then it shows
"no band data" and uses `TDEE = Mifflin × PAL` for that day instead of pretending 0 kcal were burned,
and the day is excluded from the adaptive TDEE window.

**US-7 — weekly recalibration**
Given 14 days with ≥10 logged days and ≥4 weigh-ins, when the weekly job runs, then a new TDEE is
computed, the change is clamped to ±5%, and the user sees "target updated: 1 940 → 1 870 kcal" with a
one-tap "why?" explanation.

**US-8 — onboarding**
Given a fresh install, when the user finishes onboarding, then the app has verified that Health
Connect is available, that Gadgetbridge has HC export enabled and this device selected, has read at
least one record, and has created a profile with a target — or has told the user exactly which step is
missing, with a deep link.

**US-9 — offline**
Given no network, when the user logs a cached product, or scans an unknown barcode, then logging still
works (unknown barcode is queued for later lookup and shows "will resolve when online").

**US-10 — export**
Given the user taps export, then a single JSON file (schema-versioned) plus a flat CSV of entries is
written via SAF, containing everything needed to restore, including overrides and recipes.

## 6. Screens

1. **Today** — big remaining-kcal number, eaten/burned bar, macro row, meal sections, FAB "scan".
2. **Scanner** — CameraX preview, overlay, torch, manual barcode entry, last-scanned list.
3. **Product** — name/brand/image, per-100 g table, portion input (g, and "1 serving" if known), meal
   picker, "log" button, edit/override.
4. **Search / add** — recent, frequent, OFF name search, "create product", "quick kcal".
5. **Recipes** — list, editor (components + grams, cooked weight, servings), nutrition preview, log.
6. **History** — day rows (balance, intake, burn, weight), 7/30-day chart, tap → day detail.
7. **Trends** — weight EWMA + intake bars + TDEE estimate + confidence, weekly recap card.
8. **Profile / settings** — body data, goal, activity level, target mode, units, language, data
   sources (HC status, GB status), export/import, disclaimer.
9. **Onboarding** — 5 steps: welcome → profile → target → Gadgetbridge → Health Connect → done.

## 7. UX rules that prevent the classic bugs

| Rule | Reason |
| --- | --- |
| The day rolls over at a configurable local hour (default 04:00) | Late dinners and night shifts would otherwise land on the wrong day |
| "Remaining" is `target − eaten`, and burn is shown separately | Mixing burn into the daily number produces a number that swings wildly before midnight |
| Burned-today is labelled "so far" until the day closes | Partial-day active calories look like a huge deficit at 10:00 |
| Every day carries a completeness flag: `complete / partial / no-band / no-food` | The trend maths must know what to trust |
| Values are rounded for display, stored at full precision | Repeated rounding drifts |
| Nothing is deleted silently; entries are soft-deleted for 30 days | Mis-taps are common while shopping |
| Units: kg/cm by default, lb/in optional, kcal only (kJ shown in product detail) | Users think in kcal; OFF mixes kJ |
| All colours pass WCAG AA; the balance number is also encoded as text and sign | Colour-blind users, and "red = bad" is not universal |
| No "you failed" language; a positive balance is "surplus", not "error" | Retention, and honesty about normal variation |

## 8. What success looks like (personal metrics)

- Logging a meal takes **< 15 s** from pocket to logged (scan → grams → done).
- 90% of working days have ≥ 3 meals and band data → target error vs adaptive TDEE **< 8%**.
- The weekly recap's predicted weight change is within **±0.3 kg** of the scale's 4-week trend.
- Zero days silently counted as "0 burned".
- Both users open it daily for 8 weeks without being nagged.
