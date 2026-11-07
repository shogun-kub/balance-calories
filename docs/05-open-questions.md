# Open questions — status and what is still missing

Last updated 2026-11-07, after the first round of answers.

## Answered

| Question | Answer | Consequence |
| --- | --- | --- |
| Universal or hardcoded to two people? | Universal: user enters sex, birth year, height, weight, target, kg/week | Profile = row; onboarding creates one; more can be added. Band data attaches to at most one profile per phone |
| One phone or two? | Two: Honor X7c / Android 14 / Band 9, Redmi Note 8 Pro / Android 11 / Band 11 | No cross-device merge problem. The Android 11 phone needs the Health Connect **app** installed |
| Cooking or packaged? | Both, mostly cooking | A ~150-item starter ingredient pack and raw-weight recipes become MVP, not "later" |

What this changed in the plan: `minSdk` is now 28 (Health Connect's floor), the starter ingredient pack
moved into M2 (+1 day), a device-matrix pass was added to M6 (+1 day), and the estimate is now ~38
dev-days (+ buffer ≈ 46).

---

## Still needed

### A. Body data 🔴 (blocks the first target calculation, 5 minutes to fill)

| Field | You (Band 9) | Wife (Band 11) |
| --- | --- | --- |
| Sex | | |
| Birth year | | |
| Height (cm) | | |
| Current weight (kg) | | |
| Target weight (kg) | | |
| Goal rate (kg/week, e.g. −0.5) | | |
| Body-fat % (if known) | | |
| Activity outside the band (desk job / on feet / gym 3×/w) | | |
| Any constraint (pregnancy, medication, ED history, <18) | | |

Mifflin needs sex/age/height/weight; the deficit needs a rate; the floors need to know whether they
apply.

### B. Gadgetbridge state 🔴 (narrowed — phones are known)

The phones and bands are settled, but not the software state:

1. Is **Gadgetbridge** actually installed and paired with each band today, or are the bands paired to
   **Huawei Health** only? If Huawei Health was ever used, Gadgetbridge needs the 17-digit account ID
   on first pair (a 5-minute step, documented in our plan).
2. Is **Health Connect export** enabled in Gadgetbridge, with the device selected?
   (Settings → External integrations → Health Connect)
3. Is **Developer options → Intent API** enabled? (only needed for the "Sync band" button; without it
   we fall back to Gadgetbridge's own schedule)
4. On the Redmi: is the **Health Connect app** already installed from Play? (Android 11 has no built-in
   Health Connect)
5. On the Redmi: is Gadgetbridge exempt from MIUI battery restrictions / is Autostart allowed?

Defaults: Gadgetbridge-only (no Huawei Health), HC export on, Intent API on, HC app not yet installed,
battery exemption not yet set.

### C. Food logging details 🔴 (shapes 60% of the UX budget)

1. How many meals/snacks per day do you actually want to log? (2 / 3 / 3 + snacks)
2. Do you weigh portions on a kitchen scale, or estimate by "one plate / one pack"?
   Cooking implies a scale for ingredients — but do you want to weigh *portions* too?
3. Do you want **protein** as a tracked goal, or only calories? Any other macro?
4. Do you drink alcohol / sugary drinks regularly? → separate quick-add category or not.
5. Should the log support one-tap **"same as yesterday"**? (assumed yes)
6. Will your wife log her own food, or will you enter both?
7. Which ~20 products/barcodes do you buy most often? (They become the S4 test set and the
   "frequent" seed list.)

Defaults: 3 meals + 1 snack, scale for ingredients and main meals, calories + protein, no alcohol
tracking, copy-yesterday yes, each person logs their own.

### D. Weight data 🔴 (adaptive TDEE depends on it)

1. Do you have a smart scale? Which model? (Gadgetbridge supports Xiaomi, QN-Scale, LeFu, Viatom.)
2. How often would you weigh in — daily, weekly, rarely?
3. Are you both willing to weigh in daily for the first month? That is what makes the TDEE adaptation
   work; without it the app stays a calculator.

Defaults: no smart scale, manual entry, 3–4×/week, willing for the first month.

### E. Product decisions ⚪ (defaults are fine)

1. Personal use only, or publish (Play / F-Droid / open source)? → decides ML Kit vs zxing-cpp only,
   and whether a Play health declaration is needed.
2. UI language(s): English, Russian, or both with a switcher?
3. Home-screen widget ("kcal left") in v1 or later?
4. Notifications: dinner reminder? "band hasn't synced"? neither?
5. Do the two phones need to **share recipes**, or is each independent?
6. App name for the launcher and the OpenFoodFacts User-Agent (plus a contact e-mail for OFF's
   etiquette requirement).
7. Minimum Android version you care about — currently 28 because of Health Connect.

Defaults: personal use, both languages, widget later, dinner reminder only, independent phones,
name "BalanceCalories", minSdk 28.

### F. Measurements to run, not questions ⚪🔴

These are spikes (M0) and need the real phones and 3–4 days:

1. **S1 calorie semantics** — for 3 days compare Huawei Health's daily calories, Gadgetbridge's number,
   and Health Connect's `ActiveCaloriesBurned` + `BasalMetabolicRate`. This decides the whole burn
   formula and is the single highest-risk unknown.
2. **S2 Health Connect on both phones** — including the Play install and permission flow on Android 11.
3. **S4 OpenFoodFacts coverage** — 20 real barcodes from your kitchen; now also: how many *ingredients*
   you cook with would you want in the starter pack.
4. **S5 scanner bake-off** — must run on the Redmi Note 8 Pro specifically; 2019 hardware is the
   performance floor.

### G. What we do **not** need

- A server, domain, account system, or Play Console account (unless publishing).
- Huawei Health Kit / HMS credentials — we deliberately avoid Huawei's cloud APIs.
- Root, custom firmware, or a Gadgetbridge fork.
- Paid components: the whole stack is free (Gadgetbridge, Health Connect, OFF, zxing-cpp).

---

## If you answer only three things

1. Section A (both bodies + goal) — otherwise there is no target number.
2. Section B.1–B.2 (is Gadgetbridge actually paired, is Health Connect export on) — otherwise we cannot
   promise band data.
3. Section D.1–D.3 (scale and weigh-in habit) — it decides whether the app can self-correct or stays a
   formula with a nice UI.
