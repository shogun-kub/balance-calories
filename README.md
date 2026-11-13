# BalanceCalories · Баланс Калорий

Android app that turns a Huawei band into a **calorie balance tracker**: food in (OpenFoodFacts / manual /
recipes) minus energy burned (band activity + BMR), per person, per day. One honest number: energy in
minus energy out.

**M1–M5 and M7 are done and the app runs on both phones; M6 (hardening and release) is in progress.**
Source: <https://github.com/shogun-kub/balance-calories>. The
whole loop works today: profiles and targets, food products, barcode scanning, logging, recipes, activity
from Health Connect, the daily balance, history with an adaptive TDEE, weight, settings, backup.

No account, no backend, no analytics, no tracking. Nothing leaves the phone except the product searches
typed into Open Food Facts and the exports the user chooses to write.

## Documents

| Doc | What is in it |
| --- | --- |
| [docs/01-product.md](docs/01-product.md) | Problem, users, scope, MVP vs later, user stories, screens, UX rules |
| [docs/02-research.md](docs/02-research.md) | Verified findings: Gadgetbridge/Huawei, Health Connect, OpenFoodFacts, barcode libs, formulas, licences |
| [docs/03-architecture.md](docs/03-architecture.md) | Tech stack, modules, data model, sync flows, algorithms, edge cases, ADRs |
| [docs/04-plan.md](docs/04-plan.md) | Spikes, milestones, task breakdown, estimates, risk register |
| [docs/05-open-questions.md](docs/05-open-questions.md) | Open questions and deferred decisions |
| [docs/06-milestones.md](docs/06-milestones.md) | The diary: what each milestone changed, with the numbers of the day |
| [docs/07-pitfalls.md](docs/07-pitfalls.md) | Traps and judgement calls that cost hours |
| [docs/08-environment.md](docs/08-environment.md) | This machine, its devices, build and test commands |
| [AGENTS.md](AGENTS.md) | Operating instructions for anyone (human or not) working in the repository |

## What the app does

- **Today** — a half-doughnut of eaten against the target, one card per meal, and the day's balance. Burn
  comes from the band when it is there and from a formula when it is not, and the screen says which.
- **Food** — a searchable catalogue that starts with about a hundred common products, a barcode scanner
  that resolves codes offline, and a text search that falls back to Open Food Facts by itself when the
  phone has nothing. Recipes are ingredients plus the weight the dish has after cooking, so a portion is
  measured against what is actually on the plate.
- **History** — intake and burn per day, a weight trend, and an adaptive TDEE that refuses by name when it
  does not have enough data to be honest.
- **Weight** — manual entry and a chart, kg or lb.
- **Settings** — units, day start hour, language, macro norms (four presets or your own grams), and the
  meals themselves: names, times, order.
- **Backup** — a versioned JSON export and a CSV of the food log, both to a location the user picks.

## Installing it on a phone

1. Install the APK (from a release here, or the F-Droid build once it exists).
2. Open it and fill in the profile: height, weight, birth date, activity level and goal. The daily target
   is computed from these.
3. **For band data**, install [Gadgetbridge](https://gadgetbridge.org/), pair the band there, and turn on
   its Health Connect sync. Then grant this app the activity and body permissions in Health Connect —
   Today asks for them the first time it needs them.
4. Without a band the app still works: the burn is a formula estimate, clearly labelled as one.

Android 9 (API 28) or newer. Health Connect needs Android 14 or the Health Connect app from the store.

## TL;DR of the decisions

1. **Do not talk to the bands directly.** Gadgetbridge already supports Huawei Band 9, Band 10, Band 11
   and Band 11 Pro, and can export to **Health Connect**. The app reads Health Connect (official Android
   API); Gadgetbridge stays the device driver.

   ```
   Band → Bluetooth → Gadgetbridge → Health Connect (on-device store) → BalanceCalories
   ```

   Glossary: *Gadgetbridge* speaks Huawei's proprietary BLE protocol; *Health Connect* is Android's
   built-in health database that both apps share; neither talks to the internet about your health.
2. **Kotlin + Jetpack Compose**, single APK, local-first database (Room), no backend.
3. **Barcode scanning:** CameraX + **zxing-cpp** (`io.github.zxing-cpp:android:3.1.1`, Apache-2.0,
   offline). ML Kit stays out because it is proprietary and would block F-Droid distribution.
4. **OpenFoodFacts API v3.6** for product lookup, strictly cached: the read limit is 15 requests/min/IP,
   so every scanned barcode is stored locally and never re-fetched.
5. **Target intake is computed from a weekly TDEE estimate**, not by "eating back" today's exercise. The
   band's active calories feed the TDEE estimate, they are not added to the daily budget — this avoids the
   classic double-counting bug.
6. **Two people = two profiles, two phones.** Honor X7c (Android 14, Band 9) and Redmi Note 8 Pro
   (Android 11, Band 11). The Android 11 phone needs the Health Connect **app** installed; the Android 14
   phone has it built in. Profiles are universal — body data is entered, nothing is hardcoded — but band
   data can belong to only one profile per phone, because Health Connect has no per-device attribution.

## Target setup

| | Owner | Wife |
| --- | --- | --- |
| Phone | Honor X7c, Android 14 (MagicOS 8.0) | Redmi Note 8 Pro, Android 11 (MIUI 12.5.3) |
| Band | Huawei Band 9 | Huawei Band 11 |
| Health Connect | built in | Play Store app required |
| Data path | Band → Gadgetbridge → Health Connect → this app | same |
| `minSdk` | 28 (Health Connect's floor) | 28 |

## Building it

```bash
./gradlew :app:assembleDebug        # installable debug build
./gradlew :core:test :data:testDebugUnitTest :app:testDebugUnitTest
./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest   # needs a device
```

Release builds are minified with R8. They are **unsigned** unless a `keystore.properties` exists in the
repository root (it is git-ignored, and so is the keystore itself):

```properties
storeFile=../BalanceCalories-keys/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

That absence is deliberate: F-Droid builds from source and signs with its own key, so a repository that
could not build without a private keystore could never be packaged there.

## The code

| Module | What lives there |
| --- | --- |
| `:core` | Pure Kotlin: the maths, the day boundary, the models. No Android imports, no device zone. |
| `:data` | Room, Health Connect, Open Food Facts, Gadgetbridge, backup. |
| `:app` | Compose UI, one screen per file, four tabs. |

## Licence

Apache License 2.0 — see [LICENSE](LICENSE). Third-party components keep their own licences: AndroidX and
Compose (Apache-2.0), zxing-cpp (Apache-2.0), and Open Food Facts data (ODbL).
