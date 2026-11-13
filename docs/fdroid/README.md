# Submitting BalanceCalories to F-Droid

The app is prepared for F-Droid rather than Google Play (owner's decision, 2026-11-13): no proprietary
dependencies, an Apache-2.0 licence, and a release build that does not need a private keystore. What is
left is publishing the repository and opening one merge request.

## What the repository already has

| Requirement | Where |
| --- | --- |
| A licence | [`LICENSE`](../../LICENSE) — Apache-2.0, the full canonical text |
| Version, monotonic | `versionCode = 2`, `versionName = "1.0.0"` in `app/build.gradle.kts` |
| Build from source, no signing secret | `app/build.gradle.kts`: `keystore.properties` is consulted **only** when it exists; without it the release build is unsigned |
| Minified release | R8 and resource shrinking on, keep rules with a reason each in [`app/proguard-rules.pro`](../../app/proguard-rules.pro) |
| Metadata, two languages | [`fastlane/metadata/android/`](../../fastlane/metadata/android) — title, short and full description, changelog for versionCode 2, five phone screenshots each in `en-US` and `ru-RU` |
| No tracking, no anti-features | see the note at the bottom of the recipe draft |
| Readable design record | [`docs/`](../) — the reviewer can read why every dependency is there |

## What must happen before the merge request

1. ~~Publish the repository~~ — done 2026-11-13: <https://github.com/shogun-kub/balance-calories>.
2. ~~Tag the release~~ — done: `v1.0.0` is pushed, and `UpdateCheckMode: Tags` needs nothing more.
3. Check that a clean clone builds without `local.properties` — F-Droid sets `ANDROID_HOME` itself:
   `git clone … && cd BalanceCalories && ./gradlew :app:assembleRelease`.
4. Open the merge request against <https://gitlab.com/fdroid/fdroiddata> with
   [`metadata/com.balancecalories.yml`](metadata/com.balancecalories.yml) added, following
   <https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/>.

## Notes for the reviewer (paste into the MR description)

> **Health Connect.** `androidx.health.connect:connect-client` is Apache-2.0 and builds without Play
> Services. On Android 14 Health Connect is a system module; on Android 9–13 it is an app the user
> installs, and the app says so on the Today screen instead of failing silently. It is read-only: the app
> never writes health records.
>
> **Network.** Only `world.openfoodfacts.org` (barcode) and `search.openfoodfacts.org` (text search),
> over HTTPS, with a User-Agent that names the app and the version. Product data is cached locally and a
> scanned code is never re-fetched (15 requests/min/IP limit). Offline logging, Today and History work
> with no network at all.
>
> **Signing.** `app/build.gradle.kts` reads `keystore.properties` only when the file exists; it is not in
> the repository and is git-ignored. `./gradlew :app:assembleRelease` therefore produces an unsigned APK,
> which is what the buildserver signs.
>
> **Screenshots** were taken on a Honor X7c (Android 14) from a **fixture** profile that holds no real
> person's data.
>
> **Reproducibility.** The build takes no environment variables other than the SDK location, writes no
> generated source into the tree, and `gradle/libs.versions.toml` is the only place a version is stated
> (no `+`, no snapshots). R8 is enabled and the keep rules are justified line by line.

## What this does not do

It does not publish anything and it does not touch the owner's keystore: F-Droid signs the APK with its
own key, and a build signed by the owner would be a different app that cannot update over the F-Droid
one. The two are mutually exclusive for a given `applicationId`, which is why the release build here is
unsigned by default.
