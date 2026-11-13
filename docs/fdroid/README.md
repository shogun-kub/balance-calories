# BalanceCalories — merge request to fdroiddata

Paste the block below into the merge request description. It is written for a reviewer who has never seen
this repository, and every claim in it can be checked from the metadata or the repository.

---

## New app: BalanceCalories (`com.balancecalories`)

A local-first calorie balance app: activity from Health Connect (filled by Gadgetbridge) and food from its
own catalogue, shown as one number per day. No account, no backend, no analytics, no advertising.

**Why it is a good fit for F-Droid:** the whole point of the app is that a person's health data never leaves
the phone. There is nothing to upload it to. The only network calls are the product searches the user types
into Open Food Facts and a barcode lookup for a code the phone has never seen; both are HTTPS to
`world.openfoodfacts.org` and `search.openfoodfacts.org`, both are optional, and the app works offline.

### Points worth a reviewer's attention

1. **No `subdir` and no `prebuild`.** The Gradle project root is the repository root: `settings.gradle.kts`
   includes `:app`, `:core` and `:data`. The release build is **unsigned unless `keystore.properties`
   exists**, which it does not in the repository, so nothing needs patching before the build.
2. **`versionCode 3` / `versionName 1.0.1`**, tag `v1.0.1`. `UpdateCheckMode: Tags` follows later tags.
3. **Health Connect.** `androidx.health.connect:connect-client` is Apache-2.0 and builds without Play
   Services. On Android 14 Health Connect is a system module; on Android 9–13 it is an app the user
   installs, and the app says so on its Today screen instead of failing silently. It is read-only: the app
   never writes health records.
4. **Native code.** `io.github.zxing-cpp:android` (Apache-2.0) brings a prebuilt `.so` through Maven; the
   app's own code is Kotlin only.
5. **Release build:** R8 on, resource shrinking on, keep rules justified line by line in
   `app/proguard-rules.pro` (JNI entry points, Room's generated classes, Health Connect's reflective
   records, WorkManager's worker).
6. **Screenshots** in `fastlane/metadata/android/{en-US,ru-RU}/images/phoneScreenshots/` were taken from a
   fixture profile that holds no real person's data.

### What was verified before submitting

- `./gradlew :app:assembleRelease` from a **fresh clone with no `local.properties`** (SDK from
  `ANDROID_HOME`), producing `app/build/outputs/apk/release/app-release-unsigned.apk`;
  `apksigner` confirms it is unsigned.
- Installed and exercised on two phones: Honor X7c (Android 14) and Redmi Note 8 Pro (Android 11, MIUI).
  Health Connect read, barcode scanner and torch, 200 % font scale, food logging, backup export.
- Cold start 308 ms on the Android 11 device, 1 370 ms for a debug build on Android 14; database 204 KiB
  now, ~3.4 MiB modelled for a year.
- Two hundred and fifty-five JVM tests and 126 device tests.

### Anti-features

None claimed. No ads, no tracking, no non-free network service, no non-free dependency, no non-free assets
(the launcher icon is a vector drawn in the repository by `tools/icon_design.py`).

---

## What to do (for the owner)

The merge request itself needs the GitLab account, so these are the steps:

1. Fork <https://gitlab.com/fdroid/fdroiddata>.
2. In the fork, add [`metadata/com.balancecalories.yml`](metadata/com.balancecalories.yml) — exactly the file
   in this directory, nothing else.
3. Open a merge request against `fdroiddata` `master` with the description above.
4. Watch the pipeline: `fdroid build --test com.balancecalories` runs on their buildserver. If it fails on
   `compileSdk 36` (Android 16) because their SDK image is older, the fix is on their side — reply in the MR
   rather than lowering the SDK here.

## Before pressing submit

- [x] Repository is public: <https://github.com/shogun-kub/balance-calories>.
- [x] Tag `v1.0.1` exists and points at the commit whose release APK was exercised on both phones.
- [x] `LICENSE` is Apache-2.0, the canonical text.
- [x] Metadata passes `fdroid rewritemeta` (schema) and the category `Sports & Health` is checked against
      fdroiddata's own `config/categories.yml`.
- [x] `fastlane/metadata/android/{en-US,ru-RU}/` holds title, short and full description, a changelog for
      `versionCode 3`, and five screenshots per language.
- [ ] Author name and website in the metadata are `shogun-kub` / the repository — change them if they should
      read differently.
