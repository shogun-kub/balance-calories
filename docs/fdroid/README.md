# BalanceCalories — merge request to fdroiddata

Paste the block below into the merge request description. It is written for a reviewer who has never seen
this repository, and every claim in it can be checked from the metadata or the repository. It is delimited
by the two `---` lines, so the exact text can be extracted with a script instead of by hand.

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
- Checked against the buildserver image definition (`fdroidserver/buildserver`), because the three things
  that usually break a first build are all decided there: the image is Debian trixie, whose
  `default-jdk-headless` is 2:1.21-76, so **JDK 21 is installed** while JVM auto-provisioning is switched
  off — the `jvmToolchain(21)` in `:core` has a real JDK to find; `gradlew-fdroid` takes the version from
  `gradle/wrapper/gradle-wrapper.properties` and verifies the download against the gradle-transparency log,
  where **`gradle-9.8.2-bin.zip` is present** (sha256 `21bd5da2…`); and the image installs **no Android
  platform** (`provision-android-sdk "tools;25.2.5"`), so `compileSdk 36` is downloaded by AGP using the
  licences the image pre-accepts.

### Anti-features

None claimed. No ads, no tracking, no non-free network service, no non-free dependency, no non-free assets
(the launcher icon is a vector drawn in the repository by `tools/icon_design.py`).

---

## Why the recipe has no comments

fdroiddata's pipeline runs `fdroid rewritemeta` on every changed metadata file and then
`git diff --exit-code`. `rewritemeta` never keeps comments, so a commented recipe fails that job, and the
diff it prints contains nothing but the deleted comment lines — which reads like "no change". The file in
`metadata/` is therefore exactly the canonical output, and the reasoning that would have lived in comments
lives above instead: the two deliberate absences are points 1 and 2 of the description, the anti-features are
the section before the last one. **P-44** in `docs/07-pitfalls.md` has the failure as it happened.

## Where the merge request stands

Fork `git@gitlab.com:Ne_Che_Guevara/fdroiddata.git`, branch `com.balancecalories`, one commit that adds
`metadata/com.balancecalories.yml` and nothing else. The merge request is
<https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57200>.

Two things about it are worth knowing before the next round:

- **The description there is one paragraph, not the block above.** `git push` refuses a push option that
  contains a newline — `fatal: push options must not have new line characters` — so
  `-o merge_request.description=…` cannot carry Markdown, and the long line in the merge request points back
  at this file instead. The block above belongs in the description and is pasted in the browser, from
  `tmp/mr-description.md`, which is exactly the text between the two `---` lines.
- **The first pipeline went red on `fdroid rewritemeta` and on nothing else.** `schema validation`,
  `git redirect`, `fdroid lint`, `checkupdates`, `check source code` and `tools check scripts` passed; the
  cause is the comment block the recipe used to carry — see "Why the recipe has no comments" above.

The commands that did the push, for the next round:

```powershell
git clone --depth 1 -b master git@gitlab.com:Ne_Che_Guevara/fdroiddata.git
Copy-Item docs\fdroid\metadata\com.balancecalories.yml fdroiddata\metadata\
git -C fdroiddata checkout -b com.balancecalories
git -C fdroiddata add metadata/com.balancecalories.yml
git -C fdroiddata commit -m "New app: BalanceCalories"
git -C fdroiddata push -o merge_request.create -o merge_request.target=master `
    -o merge_request.title="New app: BalanceCalories" origin HEAD
```

`fdroid build` is the job that matters and the slow one: it clones `v1.0.1` on their buildserver and runs
Gradle there. The three things it needs are listed under "What was verified" above, so a failure should be a
real defect here rather than a missing tool on their side — read the log and reply in the merge request.

The SSH key already in the agent authenticates as `@Ne_Che_Guevara`
(`ssh -T git@gitlab.com` → `Welcome to GitLab, @Ne_Che_Guevara!`), so pushes need no new credential and no
token; what a token would unlock is the API, and with it the description and the review replies.

## Before pressing submit

- [x] Repository is public: <https://github.com/shogun-kub/balance-calories> (`private: false`, Apache-2.0).
- [x] Tag `v1.0.1` exists and points at `a2201e0`, the commit whose release APK was exercised on both phones;
      the GitHub API lists it, which is what the buildserver clones.
- [x] `LICENSE` is Apache-2.0, the canonical text.
- [x] Metadata passes `fdroid rewritemeta` (schema) and the category `Sports & Health` is checked against
      fdroiddata's own `config/categories.yml`.
- [x] `fastlane/metadata/android/{en-US,ru-RU}/` holds title, short and full description, a changelog for
      `versionCode 3`, and five screenshots per language.
- [x] Author name and website in the metadata: `AuthorName: Ne_Che_Guevara`, `WebSite` the repository — the
      owner's decision.
- [x] Fork of `fdroiddata` exists and the merge request is open:
      <https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57200>.
