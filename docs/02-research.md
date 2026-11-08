# Research findings

All statements below were checked against the listed sources on **2026-11-07**. Anything that is an
assumption or still unverified is marked **⚠ UNVERIFIED** and has a matching spike in
[docs/04-plan.md](04-plan.md).

---

## 1. Gadgetbridge + Huawei Band 9 / Band 11

### 1.1 Device support — confirmed

Gadgetbridge ships Huawei/Honor support (merged as PR #2462) and its device catalogue explicitly
lists **Huawei Band 9**, **Huawei Band 10**, **Huawei Band 11** and **Huawei Band 11 Pro**
(plus Honor Band 9 / 10). So both of your bands are supported models, and they are supported by the
same protocol family, which means one integration path covers both.

- Device catalogue: <https://gadgetbridge.org/gadgets/wearables/huawei-honor/>
- Feature list for Huawei/Honor: <https://gadgetbridge.org/basics/topics/huawei-honor/>

### 1.2 Which data the integration gives us

From the official feature list (Huawei/Honor section), the relevant items for a calorie app:

| Data | Status in Gadgetbridge | Use in our app |
| --- | --- | --- |
| Step count | Synchronised | Secondary "was the band worn" signal, NEAT sanity check |
| **Calories** | Synchronised | **Primary expenditure input** |
| Heart rate / resting HR | Synchronised (not on every model) | Optional; HR-based expenditure cross-check |
| Sleep (incl. TruSleep stages on HarmonyOS 5+) | Synchronised | Context only in v1, not in the balance formula |
| Workouts (calories, HR, distance, GPS) | Synchronised | Cross-check, optional drill-down |
| SpO₂, stress, HRV, skin temperature | Device dependent | Out of scope |
| Weight | Only via a paired scale (GB has Xiaomi / QN-Scale / LeFu / Viatom drivers) | Optional weight source |

Known limitation that matters: **no intensity value** — Huawei devices report the maximum activity
intensity constantly (`docs/internals/topics/huawei-honor-specifics.md`), so we cannot derive
"active minutes at intensity X" from Gadgetbridge. Another one: TruSleep stages are only available
for HarmonyOS 5+ devices; older ones give begin/end only.

### 1.3 Pairing, and living next to Huawei Health

- Gadgetbridge can pair Huawei bands **without** the official app and without a factory reset.
- If the band has ever been connected to Huawei Health, Gadgetbridge needs the **Huawei account ID**
  (17-digit `userId`, obtainable from the cloud.huawei.com cookie, from
  `/data/data/com.huawei.health/shared_prefs/login_data.xml` on a rooted phone, or from `adb logcat`
  by grepping `huid=`). Only then can both apps take turns.
- Re-connecting to Gadgetbridge always requires re-pairing from the "+" button, **not** deleting the
  device (deleting destroys the stored data).

Source: <https://gadgetbridge.org/basics/pairing/huawei-honor-pairing/>

Consequence for us: the app must **not** ask for Bluetooth permissions or try to own the band. The
setup guide in our app is "install Gadgetbridge → pair band → enable Health Connect export".

### 1.4 Integration surfaces — and why Health Connect wins

The data path, in one picture:

```
Huawei Band 9/11
      │  Bluetooth LE (Huawei's proprietary protocol)
      ▼
Gadgetbridge            ← owns the protocol, the pairing, the sync schedule
      │  writes standard records (steps, active kcal, BMR, sleep, weight)
      ▼
Health Connect          ← Android's on-device health store + permission system
      │  read, per data type, only after the user grants it
      ▼
BalanceCalories         ← our app: food log, targets, trends
```

**Gadgetbridge is the driver. Health Connect is the socket.** We are not replacing Gadgetbridge and we
never touch Bluetooth — we cannot, because Huawei's band protocol is exactly the thing Gadgetbridge
spent years reverse-engineering.

Why not read Gadgetbridge's data directly? Because it has no public interface for it:

- Its activity data lives in a **private SQLite database** inside its own sandbox. Since Android 11,
  `Android/data/<package>/` is not readable by other apps, and reading it would need root or a manual
  file import.
- It has **no documented ContentProvider** for activity/step data (only Pebble and heart-rate
  providers, which are internal and not a contract).
- Its documented APIs are **commands** (trigger a sync) and **exports** (write a file) — neither is a
  data feed a UI can query.

Health Connect is the documented, stable, permissioned way out. The cost is one setup step, and the
benefit is that our app is not Huawei-specific at all.

Surfaces evaluated:

| Surface | Reality | Verdict |
| --- | --- | --- |
| **Health Connect export** | Documented, first-class. GB writes Steps, Distance, **Active calories**, HR, SpO₂, HRV, temperature, resting HR, **BMR (nightly builds)**, sleep, weight, VO₂max, hydration, exercise sessions. User picks which devices to share; can auto-sync after each device sync. | ✅ **Use this** |
| **Intent API** | Documented. `nodomain.freeyourgadget.gadgetbridge.command.ACTIVITY_SYNC` triggers a device sync; `…action.ACTIVITY_SYNC_FINISH` is broadcast when done; `…command.TRIGGER_EXPORT` exports the DB. Requires the user to enable *Settings → Developer options → Intent API*. | ✅ Use for "Sync band now" button |
| **SQLite export** | `Gadgetbridge` SQLite file is written under `/sdcard/Android/data/nodomain.freeyourgadget.gadgetbridge/files/` | ❌ **Not readable** by our app: `Android/data/<pkg>` is inaccessible to other apps since Android 11 scoped storage. Only via SAF/manual import or root. |
| **ContentProvider** | GB contains `PebbleContentProvider` and an HR content provider, but there is **no documented public provider** for activity/step data | ❌ Not a contract; would break silently |

Health Connect docs: <https://gadgetbridge.org/basics/integrations/health-connect/>
Intent docs: <https://gadgetbridge.org/internals/automations/intents/>
Data management / DB export: <https://gadgetbridge.org/internals/development/data-management/>

### 1.4.1 Measured on the owner's phone — 2026-11-08

Observed over `adb` on the Honor X7c (Android 14, SDK 34, MagicOS `8.0.0.302(C185E8R2P5)`); none of
the rows below is inferred:

| Observation | Value |
| --- | --- |
| Gadgetbridge installed? | **No** — `pm list packages -u` contains no `nodomain.freeyourgadget.gadgetbridge` |
| Huawei Health | `com.huawei.health` 17.0.10.300, `targetSdk 35` |
| Honor Health | `com.hihonor.health` 17.26.10.301 |
| Health Connect | framework service `healthconnect` present, `com.google.android.healthconnect.controller` v17, DB `Version 27`, size `1396 kb` |
| Band 9 Bluetooth state | bonded as `HUAWEI Band 9-660` (`[LE]`); no active connection observed at sample time |
| Huawei Health → Health Connect | `Fitness: GRANTED_AND_DECLARED`, **`Contributed Data: false`** |
| Health types granted to Huawei Health | `WRITE_STEPS`, `WRITE_ACTIVE_CALORIES_BURNED`, `WRITE_SLEEP` — all `granted=true`, `USER_SET` |
| Apps that have contributed HC data | exactly one, and it is not a health app: `app.chompass` |

Two conclusions, both of which change the plan:

1. **Gadgetbridge may not be needed on this phone.** Huawei Health 17.x declares, and has already been
   granted, write access to exactly the three record types this app consumes — steps, active calories,
   sleep. If its Health Connect export can be switched on and is fed by a synced band, the pairing step
   and the Huawei-account-ID workaround from §1.3 disappear for the owner.
2. **Today no band data reaches Health Connect at all.** The permissions are granted, so the break is
   upstream of Health Connect: either the band never syncs into Huawei Health, or Huawei Health's
   export is switched off. Nothing downstream can be validated until `Contributed Data` flips to
   `true`.

Consequence: test the Huawei Health path first, because it is one toggle instead of one installation;
keep Gadgetbridge as the documented fallback and as the likely answer for the wife's Redmi. The
calorie-semantics spike (§1.6) is blocked on this until records exist.

### 1.4.2 Outcome — Huawei Health cannot export, Gadgetbridge is the path (2026-11-08)

The Huawei Health route was tested and closed, not assumed:

- The APK (289,300,035 bytes, pulled from `/data/app/…/base.apk`) **does** contain the feature:
  `com.huawei.ui.thirdpartservice.activity.healthconnect.HealthConnectActivity`, the
  `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` filter, and the strings
  *"Authorize the Health app to share health and fitness data to Health Connect"*,
  *"…Fitness data, calories, step count, and sleep data will all be shared with Health Connect"*,
  *"…To stop sharing data with Health Connect, tap REVOKE AUTHORIZATION"*.
- The entry is nevertheless **absent from the UI**: the Settings list runs
  `Цель → Синхронизация вручную → … → Очистить кэш` with no Health Connect row, and the
  *Предоставление данных и авторизация* screen (`huaweischeme://healthapp/thirdPartService`) lists
  only ECG / arterial stiffness / sleep-breathing services. The activity is `not exported`, so no
  adb intent can reach it either.
- `dumpsys healthconnect` stayed at `com.huawei.health … Contributed Data: false` across three checks
  spanning ~30 minutes while the band synced normally (Huawei Health itself showed
  `Синхронизировано: 08.11.2026 09:42`).
- Working hypothesis, recorded as inference: the app gates its export on the **standalone Health
  Connect app** (`com.google.android.apps.healthdata`), which does not exist on Android 14 because
  Health Connect is part of the OS.

Gadgetbridge was therefore installed over adb and paired:

| Step | Result |
| --- | --- |
| Install | `nodomain.freeyourgadget.gadgetbridge` **2026.11.0**, `versionCode 256`, from F-Droid |
| Device support | `HuaweiBand9Coordinator` present in the build |
| First pairing attempt | failed: *"Не удалось согласовать ключ аутентификации"* — the Huawei account key |
| Account ID | not obtainable without root: `huid=` was not in logcat, and Huawei Health redacts account names in `dumpsys account` |
| Resolution | **band factory reset** (user's choice), which clears the stored key — no Huawei ID needed |
| Pairing | succeeded: `HUAWEI Band 9-660`, connected, battery 62 % |
| Health Connect export | enabled, band selected as source, **30-day backfill**, `Синхронизация после синхронизации устройства` on |
| HC permissions for GB | granted all write types via `PermissionsActivity` |
| First export run | `Синхронизация данных Health Connect завершена (синхронизировать нечего)` — GB had no activity yet, because the band had just been reset |

Open item: the end-to-end write is **not yet observed**. Health Connect still reports
`Contributed Data: false` for Gadgetbridge, because the freshly reset band has recorded no steps or
active calories. The next device sync after real activity is the test that closes it — and it is also
the precondition for the §1.6 calorie-semantics spike.

Note for the wife's Redmi (Android 11): the same Gadgetbridge path applies, but it needs the
standalone Health Connect app from Play Store, and the Band 11 pairing will hit the same
account-key question — so decide **before** resetting whether her band's history matters.

### 1.5 Topology — confirmed: one phone per person

Gadgetbridge keeps data per device (`device_id` in its own DB) and can host several gadgets, but
**Health Connect has no per-device attribution** — each record only carries a data origin *app* (the
Gadgetbridge package), not the band's MAC. Two bands on one phone merge into one stream that no
downstream app can separate. (GB's own note that notifications "do not reliably show up on both"
devices with multiple Huawei gadgets connected points the same way.)

Confirmed setup (2026-11-07):

| | Owner | Wife |
| --- | --- | --- |
| Phone | Honor X7c | Redmi Note 8 Pro |
| OS | Android 14, MagicOS 8.0 | Android 11, MIUI 12.5.3 |
| Band | Huawei Band 9 | Huawei Band 11 |
| Health Connect | **built into Android 14** | **Play Store app must be installed** |
| App instance | 1 (own profile) | 1 (own profile) |

```
Honor X7c  : Band 9  → Gadgetbridge → Health Connect → BalanceCalories (profile "owner")
Redmi N8P  : Band 11 → Gadgetbridge → Health Connect → BalanceCalories (profile "wife")
```

Consequences for the product:

- The app is **universal** in the sense that nothing is hardcoded to two people or two band models:
  a profile is a row, the band is whatever Health Connect contains. Onboarding asks for body data,
  computes a target, then optionally attaches *this phone's* Health Connect as the activity source.
- Because Health Connect is the only input, the app is **device-agnostic by construction**: a Garmin,
  a Xiaomi band, a Samsung watch or a Withings scale that writes to HC works with zero code changes.
  Huawei is just the first device that happens to be in the house.
- **Band data belongs to the phone, not to the profile.** A second profile created on the same phone
  can log food, weight and targets, but must be marked "no band on this phone" — otherwise it would
  silently inherit the first person's calories. This is a schema field, not a hidden assumption.
- If a phone is ever replaced, the profile and its history move via export/import; the band pairing
  is re-done in Gadgetbridge, and Health Connect history stays behind on the old device (a known
  limitation — export before switching phones).

### 1.6 Calorie semantics — the biggest unknown

Huawei's UI shows a calorie number that, in Huawei Health, is typically **total** energy
(BMR + activity). Gadgetbridge exports **Active calories** to Health Connect and (in nightly) **BMR**
separately, which implies:

```
energy_out(day) = BMR(from GB/HC if present, else formula) + active_kcal(from GB/HC)
```

This is an inference from the HC export documentation, not a measured fact for Band 9/11 firmware.
**⚠ UNVERIFIED:** whether GB's "active calories" for these bands is truly active-only, and whether the
band's own number is total or active. Getting this wrong shifts every day's balance by ~1500–1800 kcal,
i.e. the app would be useless. Spike S1 in the plan verifies it against Huawei Health over 3–5 days
before any UI work.

Second, subtler trap: **do not also add workout calories**. Health Connect's
`ActiveCaloriesBurnedRecord` already contains workout burn; adding `ExerciseSessionRecord` calories on
top double-counts.

---

## 2. Health Connect

**What it is:** a database plus a permission system that lives on the phone. Health apps *write*
standardised records into it (steps, calories, heart rate, weight, sleep, nutrition…) and other apps
*read* the record types the user explicitly allows. It is the Android equivalent of Apple Health — a
hub, not an app you look at.

**What it is not:** not a Google cloud service, not an account, not a sync. The data stays on the
device; there is no upload to Google, and no server we could query even if we wanted to. On Android 13
and below the "app" you install is just the UI/store implementation, made by Google, still running
locally.

- Built into the OS since **Android 14**; on Android 13 and below it is the Health Connect app from
  Play (which is why the Redmi needs it installed).
- Client library: `androidx.health.connect:connect-client` — stable **1.1.0**, current
  `1.2.0-alpha06` (published 2026-08-26).
- Data types we need: `StepsRecord`, `ActiveCaloriesBurnedRecord`, `BasalMetabolicRateRecord`,
  `TotalCaloriesBurnedRecord`, `WeightRecord`, `SleepSessionRecord`, `ExerciseSessionRecord`
  (+ optional writes: `NutritionRecord`, `WeightRecord`).
- Access is permission-scoped per data type, granted through the Health Connect UI; the user can
  revoke at any time. `HealthConnectClient.getSdkStatus()` tells us whether HC is installed /
  available / needs update.
- If the app is ever published on Google Play with HC permissions, Google requires the **Health apps
  declaration** and a privacy policy. Irrelevant for a private build, relevant if we publish.

Docs: <https://developer.android.com/health-and-fitness/health-connect/develop/get-started>

Requirement quoted from the availability page: *"Health Connect requires a mobile device running
**Android 9 (API 28)** or higher with **Google Play services** installed."* On Android 13 and lower it
is a Play Store app; on Android 14+ it is part of the system.

That makes the two phones behave differently, and onboarding must branch:

| Phone | What the user must do |
| --- | --- |
| Honor X7c, Android 14 | Nothing to install; grant permissions via *Settings → Security & privacy → Privacy → Health Connect* |
| Redmi Note 8 Pro, Android 11 | Install the **Health Connect** app from Play Store, then grant permissions inside it |

Both phones have Google Play services (MIUI Global and MagicOS both ship GMS), so the Android 11 path
is available — but it is a manual install and a separate permission UI, which is exactly the kind of
step that silently kills a personal app. The onboarding wizard has to detect it
(`HealthConnectClient.getSdkStatus()`) and deep-link to the Play listing.
Source: <https://developer.android.com/health-and-fitness/health-connect/availability>

(Parenthetical: Health Connect does not work inside a work profile — irrelevant here, but worth
knowing if either phone is ever managed by an employer.)

Practical consequences:

- Reading is **pull-based**; there is no push. We sync on app open + periodic WorkManager job.
- GB writes to HC only if the user enabled it and selected the device — onboarding must check that
  and deep-link to GB settings.
- HC records are UTC instants → day bucketing must use the device's local time zone (DST-safe).
- Data arrives late (band syncs when in range), so **past days must be recomputable**; every daily
  aggregate is an upsert keyed by (profile, local date), never an append.

---

## 3. OpenFoodFacts

### 3.1 API version and endpoint

v3 is the current API (**v3.6**); v2 is deprecated but still supported. Read a product:

```
GET https://world.openfoodfacts.org/api/v3/product/{barcode}.json?fields=code,product_name,brands,quantity,serving_size,serving_quantity,nutriments,nutriscore_grade,image_front_small_url
```

Real response for `3017620422003` (trimmed), fetched 2026-11-07:

```json
{
  "code": "3017620422003",
  "product": {
    "product_name": "Nutella",
    "brands": "Nutella, Ferrero",
    "quantity": "400 g e",
    "product_quantity": 400,
    "product_quantity_unit": "g",
    "nutriments": {
      "energy-kcal_100g": 539, "energy-kj_100g": 2252,
      "proteins_100g": 6.3, "fat_100g": 30.9, "saturated-fat_100g": 10.6,
      "carbohydrates_100g": 57.5, "sugars_100g": 56.3, "fiber_100g": 0,
      "salt_100g": 0.107, "sodium_100g": 0.0428
    }
  },
  "status": "success"
}
```

Not-found response (`0000000000000`, HTTP **404**):

```json
{"errors":[{"message":{"id":"invalid_code"}},{"message":{"id":"product_not_found"}}],
 "result":{"id":"product_not_found"},"status":"failure"}
```

Notes:
- Values are **per 100 g / 100 ml** by default; per-serving values exist as `*_serving` only when the
  product has a serving size and the server computed them. Never assume `_serving` exists.
- Energy may exist only as kJ (`energy-kj_100g`) — convert with `kcal = kJ / 4.184`.
- `quantity` is a human string ("400 g e"); `product_quantity` is the numeric one.
- Server normalises/pads barcodes, so send what the scanner gives, but validate the check digit and
  handle 8/12/13-digit inputs yourself so the UI can say "this doesn't look like a valid barcode".
- The response also carries `nutriments_estimated`, `nova-group`, `nutriscore_grade` — useful for
  display, never for the balance math.

### 3.2 Rate limits — the design constraint

Straight from the docs:

- **15 requests/min/IP** for product reads (`GET /api/v*/product/…` or the product page)
- **10 requests/min/IP** for search (`/api/v2/search`, `/cgi/search.pl`); not for search-as-you-type
- A **custom User-Agent is mandatory**: `AppName/Version (ContactEmail)`
- Mobile apps: limits apply per user IP; bulk needs (>a few hundred products) should use the
  database dumps instead of the API

Design consequences: persistent product cache keyed by barcode, request coalescing, exponential
backoff on 429/5xx, no prefetching of "similar products", manual entry as a first-class fallback.
Search (for manual add) is a single explicit user action, debounced, with a hard client-side budget.

Docs: <https://openfoodfacts.github.io/openfoodfacts-server/api/>

### 3.3 Licence

- Database: **ODbL 1.0**; contents: DbCL 1.0; images: CC BY-SA.
- Querying the API and caching on-device is fine, but the app must **attribute** OpenFoodFacts
  ("Data from Open Food Facts, ODbL") and must not redistribute a derived database without
  share-alike. We do not bundle or redistribute OFF data, so the practical obligation is attribution
  plus a link to the licence.
- OFF also asks every API user to fill their usage form — one-time action, worth doing.

### 3.4 Data-quality traps to handle in code

1. Missing energy but macros present → compute `4P + 4C + 9F` and flag the entry as estimated.
2. Energy inconsistent with macros by >15% → trust the label energy, show a warning in the product
   detail, never block logging.
3. Only per-serving data without a numeric serving size → unusable for gram-based logging; ask the
   user for the pack size.
4. Liquids: `ml` vs `g`; without density we assume 1 g/ml and say so in the UI.
5. "Prepared" variants (`*_prepared`) — ignore for v1, keep the raw fields.
6. Crowd-sourced errors are normal (wrong brand, wrong pack size). The app must allow
   **"fix this product for me"**, storing a local override that shadows the OFF values, and must
   snapshot nutrition into the log entry so later edits never rewrite history.

---

### 3.5 Cooking: OFF is the wrong tool for half the plate

Both of you mostly cook. That splits the food problem in two:

| | Packaged food | Raw ingredients |
| --- | --- | --- |
| Identifier | barcode | no barcode (loose produce, butcher counter, bulk) |
| OFF coverage | good to excellent | poor and inconsistent (generic entries, per-piece data, wrong units) |
| Best source | OFF by barcode | a small curated table + the user's own entries |

Options considered for ingredients:

1. **Search OFF by name** — hits exist for "chicken breast", but values are per 100 g of arbitrary
   products (marinated, skin-on), and 10 search calls/min makes it painful. Keep as a fallback.
2. **Let the user create ingredients** — necessary anyway ("my buckwheat", "my protein powder"), but
   repeating it for 100 basics is bad onboarding.
3. **Ship a starter pack** — ~120–150 common cooking ingredients (per 100 g: kcal, protein, fat,
   carbs, fiber), imported as ordinary user products on first run, fully editable, deletable.
   Source: USDA FoodData Central (US government data, generally public domain — confirm the exact
   terms for the branded subset before bundling; SR Legacy is the clean one).
4. **Bundled OFF extract** — hundreds of MB, ODbL share-alike on redistribution, overkill for two users.

**Decision: 3 + 2, with 1 as fallback.** The pack is a build-time JSON asset, not a second database:
it imports into the same `product` table with `source = 'starter'`, so it behaves like anything the
user typed (edit, delete, override) and no special-case code paths exist downstream.

Cooking semantics that the recipe model has to encode (this is where calorie apps usually lie):

- Ingredients are weighed **raw** and logged raw — that is what the user actually puts in the pan.
- Water loss (meat 20–30%), water gain (rice/pasta ~2.2×), and fat absorbed during frying change the
  *total weight*, not the total energy. So a recipe stores raw component grams **and** the measured
  cooked weight; per-100 g is then computed against cooked weight, which is what a portion is served
  from.
- Frying oil must be a component (or the user will under-count 100–200 kcal per dish).

## 4. Barcode scanning

| Option | Facts (checked 2026-11-07) | Fit |
| --- | --- | --- |
| **ML Kit** `com.google.mlkit:barcode-scanning` | Latest **17.3.0**, published **2024-08-07** (no release in ~2 years). Bundled model, runs on-device, needs Google Play services for the unbundled variant. Proprietary. | Best raw accuracy, but proprietary + stale + Play-services dependency |
| **zxing-cpp** `io.github.zxing-cpp:android` | **3.1.1** on Maven Central, Apache-2.0, C++ core with JNI, accepts `ImageProxy` directly (`BarcodeReader().read(image)`) | ✅ **Chosen default**: FOSS, fast, offline, no Play deps |
| **ZXing Android Embedded** `com.journeyapps:zxing-android-embedded` | 4.3.0, Apache-2.0, Java, brings its own activity | Fine fallback, but slower on low-end sensors and less accurate in poor light than zxing-cpp |
| **Google Code Scanner** | No camera permission needed, Play-services backed, UI is Google's | Rejected: no custom overlay, requires Play services |
| **Scandit** | Commercial, best-in-class | Rejected: cost |

CameraX: stable **1.6.2**, **1.7.0-rc02** published 2026-11-04. Use CameraX `ImageAnalysis` +
`BarcodeScanner` interface, so the engine is swappable via a build flavour.

Formats to enable: EAN-13, EAN-8, UPC-A, UPC-E (Russian consumer goods are EAN-13 with 460–469
prefix, which is plain EAN-13). Do not enable QR/DataMatrix in the scanning screen — food packs have
plenty of QR codes and they cause false captures.

UX requirements from OFF's own guide (`docs/api/tutorials/scanning-barcodes.md`): target overlay,
torch button, haptic + beep on success, manual-entry escape hatch, "product not found → be the first
to add it" link, and immediate feedback before the network call.

---

## 5. Energy math

### 5.1 BMR / RMR formulas (kcal/day, weight kg, height cm, age years)

| Formula | Equation | Note |
| --- | --- | --- |
| **Mifflin–St Jeor** (1990) | M: `10W + 6.25H − 5A + 5`; F: `10W + 6.25H − 5A − 161` | Best general-purpose default; ±10% typical error |
| Harris–Benedict (Roza–Shizgal 1984) | M: `88.362 + 13.397W + 4.799H − 5.677A`; F: `447.593 + 9.247W + 3.098H − 4.330A` | Historic; tends to overestimate |
| Katch–McArdle | `370 + 21.6 × LBM(kg)` | Only with a body-fat % |
| Cunningham (1980) | `500 + 22 × LBM(kg)` | Athletes |

Mifflin MD, St Jeor ST, et al. *Am J Clin Nutr* 1990;51(2):241–7. doi:10.1093/ajcn/51.2.241

### 5.2 Activity multipliers (PAL)

`1.2` sedentary · `1.375` light (1–3 sessions/wk) · `1.55` moderate (3–5) · `1.725` hard (6–7) ·
`1.9` athlete/manual labour. Used **only** to seed the first TDEE estimate before band data exists —
after ~2 weeks we have measured expenditure and the multiplier becomes irrelevant.

### 5.3 Energy per kg of body mass

The 7700 kcal/kg figure is a useful approximation, not a law: Hall (2008) shows the real cost of
weight loss changes as body composition and metabolic rate adapt
(doi:10.1038/sj.ijo.0803720). Use it for *projections and adaptive TDEE*, never as a promise; expect
the first week to be dominated by glycogen/water.

### 5.4 Adaptive TDEE (the part that makes the app actually work)

Over a window of N days (use 28, stepped weekly):

```
ΔW       = trendWeight(end) − trendWeight(start)      // EWMA-smoothed, in kg
TDEE_hat = meanIntake − (ΔW × 7700) / N               // kcal/day
TDEE     = 0.5 × TDEE_hat + 0.5 × TDEE_band           // band-based estimate
```

with guards: require ≥10 logged days and ≥4 weigh-ins in the window, reject days with <60% logging
completeness, clamp week-over-week change to ±5%, and mark the estimate "low confidence" until two
full weeks exist. This is what separates the app from a calculator: the band says what you burned,
the scale says whether it was true, and the model reconciles them.

### 5.5 Budget model (ADR-004)

Two candidate models:

- **Fixed daily target** (recommended): `target = TDEE − deficit`, recomputed weekly. Exercise is
  already inside TDEE, so nothing is "added back".
- **Dynamic "eat back"**: `target_today = BMR + active_so_far + deficit`. Noisy before midnight,
  rewards wearing the band, and over-eats on heavy days.

Ship the fixed target, show "burned so far today" as context only, and allow a manual +kcal
adjustment for genuinely unusual days (long hike). Rationale and failure modes in
[docs/03-architecture.md](03-architecture.md#8-algorithms).

### 5.6 Safety floors

Never propose a target below `max(1.1 × BMR, 1200 kcal female / 1500 kcal male)`, cap the deficit at
25% of TDEE (or ~1% of body weight per week), and refuse to compute anything for users under 18 or
with BMI < 18.5 without an explicit warning. Not a medical device; the disclaimer must say so.

---

## 6. Decisions that follow from this research

| # | Decision | Why |
| --- | --- | --- |
| D1 | Health Connect is the only band data source | Only documented, stable path; GB content provider does not exist |
| D2 | Gadgetbridge is a hard prerequisite, not bundled | We cannot legally/practically pair Huawei bands ourselves |
| D3 | One phone per person; band data is an attribute of the profile, not of the app | HC has no per-device attribution; confirmed topology |
| D4 | zxing-cpp, ML Kit optional flavour | FOSS distribution without Play Services |
| D5 | OFF v3 + aggressive cache | 15 req/min/IP |
| D6 | Snapshot nutrition per log entry | OFF is editable by anyone |
| D7 | Fixed daily target from weekly TDEE | Avoids eat-back double counting |
| D8 | Everything local, export as JSON/CSV | Health data, no backend, no analytics |
| D9 | `minSdk 28`, with a degraded "no band" mode | HC needs API 28 + Play services; the Android 11 phone is the floor |
| D10 | Bundle a small starter pack of cooking ingredients | OFF covers packaged food well, raw ingredients poorly; both users cook |
