# BalanceCalories

Android app that turns a Huawei band into a **calorie balance tracker**: food in (OpenFoodFacts /
manual / recipes) minus energy burned (band activity + BMR), per person, per day.

Planning stage — this repository currently holds the research and the implementation plan only.

## Documents

| Doc | What is in it |
| --- | --- |
| [docs/01-product.md](docs/01-product.md) | Problem, users, scope, MVP vs later, user stories, screens, UX rules |
| [docs/02-research.md](docs/02-research.md) | Verified findings: Gadgetbridge/Huawei, Health Connect, OpenFoodFacts, barcode libs, formulas, licences |
| [docs/03-architecture.md](docs/03-architecture.md) | Tech stack, modules, data model, sync flows, algorithms, edge cases, ADRs |
| [docs/04-plan.md](docs/04-plan.md) | Spikes, milestones, task breakdown, estimates, risk register |
| [docs/05-open-questions.md](docs/05-open-questions.md) | Information we still need from you + measurements to run before coding |

## TL;DR of the decisions

1. **Do not talk to the bands directly.** Gadgetbridge already supports Huawei Band 9, Band 10,
   Band 11 and Band 11 Pro, and can export to **Health Connect**. The app reads Health Connect
   (official Android API); Gadgetbridge stays the device driver.

   ```
   Band → Bluetooth → Gadgetbridge → Health Connect (on-device store) → BalanceCalories
   ```

   Glossary: *Gadgetbridge* speaks Huawei's proprietary BLE protocol; *Health Connect* is Android's
   built-in health database that both apps share; neither talks to the internet about your health.
2. **Kotlin + Jetpack Compose**, single APK, local-first database (Room), no backend.
3. **Barcode scanning:** CameraX + **zxing-cpp** (`io.github.zxing-cpp:android:3.1.1`, Apache-2.0,
   offline). ML Kit stays an optional build flavour because it is proprietary and would block
   F-Droid distribution.
4. **OpenFoodFacts API v3.6** for product lookup, strictly cached: the read limit is
   **15 requests/min/IP**, so every scanned barcode is stored locally and never re-fetched.
5. **Target intake is computed from a weekly TDEE estimate**, not by "eating back" today's
   exercise. The band's active calories feed the TDEE estimate, they are not added to the daily
   budget — this avoids the classic double-counting bug.
6. **Two people = two profiles, two phones.** Honor X7c (Android 14, Band 9) and Redmi Note 8 Pro
   (Android 11, Band 11). The Android 11 phone needs the Health Connect **app** installed; the Android
   14 phone has it built in. Profiles are universal — body data is entered, nothing is hardcoded —
   but band data can belong to only one profile per phone, because Health Connect has no per-device
   attribution.

## Target setup

| | Owner | Wife |
| --- | --- | --- |
| Phone | Honor X7c, Android 14 (MagicOS 8.0) | Redmi Note 8 Pro, Android 11 (MIUI 12.5.3) |
| Band | Huawei Band 9 | Huawei Band 11 |
| Health Connect | built in | Play Store app required |
| Data path | Band → Gadgetbridge → Health Connect → this app | same |
| `minSdk` | 28 (Health Connect's floor) | 28 |

Effort estimate: **~38 dev-days + 4 days of spikes ≈ 46 dev-days with buffer** (see
[docs/04-plan.md](docs/04-plan.md)).
