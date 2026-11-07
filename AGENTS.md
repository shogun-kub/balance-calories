# AGENTS.md

Operating instructions for any agent working in `E:\Projects\BalanceCalories`.

## What this repository is

An Android application, built by and for two people who want one honest number: **energy in minus
energy out**. The app reads body data and food, reads activity from the phone's Health Connect store,
and shows a daily balance plus a weekly trend.

Two outputs, everything else supports them:

1. **A working app** — Kotlin, Jetpack Compose, local-first, installable on both phones.
2. **A written record** of why it works that way — `docs/`, so a decision can be re-read in six months
   without reverse-engineering the code.

Current state: **M1 in progress.** `docs/` is complete through the plan, the toolchain is installed,
and `:core` — the pure Kotlin energy maths — builds and tests green (27 tests). `:data` and `:app` do
not exist yet.

## Toolchain (installed 2026-11-07, verified)

| Item | Location / version |
| --- | --- |
| Android Studio | `C:\Program Files\Android\Android Studio` — 2026.2.2.9, bundled JBR, Start Menu shortcut |
| Android SDK | `C:\Program Files\Android\Sdk` — platform-tools 37.0.1, platforms;android-36, build-tools;36.0.0, cmdline-tools 16111833 |
| `adb` | `C:\Program Files\Android\Sdk\platform-tools\adb.exe`, on the machine `PATH` |
| Licences | `C:\Program Files\Android\Sdk\licenses` — accepted; packages install without prompting |

Two things to know before the next package install:

1. **The SDK lives under `Program Files`, so it is read-only for this session.** Adding a platform, a
   build-tools version or an emulator requires an **elevated** shell; `android sdk install` from a
   normal shell fails with access denied. Budget one UAC prompt for any SDK change.
2. **`sdkmanager` is deprecated.** The supported command is `android sdk install <package>` (new CLI,
   replaces `sdkmanager`); `sdkmanager.bat` still exists but prints a deprecation warning and forwards
   to the new tool. The first `android.exe` run unpacks a payload into `ANDROID_USER_HOME`
   (default `%USERPROFILE%`), so under the DSH `workspace-write` sandbox it fails with
   `Failed to create bin dir: Access is denied` — run it unconfined, or point `ANDROID_USER_HOME` at a
   writable directory.

`local.properties` (with `sdk.dir`) is written in M1 and stays untracked; Gradle also resolves the SDK
from machine-wide `ANDROID_HOME`.

## Directory contract

| Path | Role | Rule |
| --- | --- | --- |
| `docs/` | Design and findings | Numbered Markdown, tracked in git. The plan, the research, the ADRs, milestone notes. |
| `app/`, `core/`, `data/` | Gradle modules (M1) | `:core` is pure Kotlin, zero Android imports. `:data` owns Room, Health Connect, OpenFoodFacts, Gadgetbridge. `:app` owns Compose UI. |
| `tools/` | Build-time generators | Starter-ingredient pack generator, fixture capture scripts. Runs on the host, never in the app. |
| `fixtures/` | Test inputs | Sanitised OpenFoodFacts payloads, synthetic Health Connect dumps, barcode images. **No real personal data.** |
| `gradle/libs.versions.toml` | Version truth | Every dependency version lives here and nowhere else. |
| `build/`, `.gradle/`, `local.properties`, `*.keystore`, `*.apk`, `*.aab` | Build output and secrets | Never tracked. |
| `E:\Projects\BalanceCalories-acl-recovery\` | ACL rollback record | Outside the repo. Leave in place; it documents the permission repair that made this workspace writable. |

## Hard rules

1. **Real health data never enters git.** Weight, food logs, exports, screenshots of the Today screen
   with real numbers, `logcat` dumps containing them — none of it. Fixtures are synthetic or
   sanitised, and say so in the file header.
2. **Never commit build artifacts or credentials.** APK/AAB, keystores, `keystore.properties`,
   `local.properties`, `.gradle/`, `build/`. A release keystore belongs to the owner, not
   to the repository.
3. **One source of versions.** `gradle/libs.versions.toml` only. No `+`, no `latest.release`, no
   `-SNAPSHOT` in anything that ships. A version bump is a reviewable diff, not a side effect of a
   build.
4. **A number is either measured or estimated, and the app says which.** Health Connect values, band
   values, formula values and user-typed values are four different things. This is a product rule, not
   a documentation style: never render an estimate with the same confidence as a measurement.
5. **Identify things precisely.** "It works on my phone" is not evidence. Device behaviour is named by
   model, OS build, app `versionName`/`versionCode` and git commit. A data claim names the Health
   Connect record type and the time window. A food claim names the OpenFoodFacts field and the
   product code.
6. **Separate observation from inference.** State what a tool or device returned, what you derived,
   and what remains unknown. Do not let a plausible explanation read like a measurement.
7. **A formula change needs a test vector.** Any edit to BMR, TDEE, deficit, flooring or recipe maths
   lands with a unit test that pins the expected numbers. No exceptions, no "obvious" changes.
8. **A schema change needs a migration and a migration test.** Room will happily wipe a user's year of
   food history otherwise.
9. **Do not patch third parties.** Gadgetbridge, Health Connect, OpenFoodFacts and AndroidX are
   read-only dependencies. Work around a limitation, record it in `docs/`, and never edit a vendored
   copy to make a test pass.
10. **Ask before anything irreversible on a device or on real data.** `adb uninstall`, `pm clear`,
    wiping app data, deleting an export, factory-resetting a band, re-pairing Gadgetbridge, changing
    Health Connect permissions. Especially on the wife's phone, which is a daily driver, not a test
    rig.
11. **Offline is the default, not a mode.** Logging food, viewing today and reading history must work
    with no network. Any feature that cannot is a design bug.
12. **The day boundary and time zone logic lives in `:core` and only there.** If two screens compute a
    "day" differently, the numbers will disagree and nobody will trust the app.

## Environment

| Fact | Value |
| --- | --- |
| Host | Windows, PowerShell (`pwsh`), NTFS; workspace `E:\Projects\BalanceCalories`, ~420 GB free |
| File policy | DSH `workspace-write`: writes outside the repository need approval |
| Java | Liberica JDK **21.0.11** LTS, `JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-21-Full\` |
| Git | **2.49.0.windows.1**; repository initialised on `main` (`core.autocrlf=false`), first commit `d195f3a` |
| Python | **3.14.2** — used for `tools/` generators, not in the app |
| Node.js | **v24.13.1** — available, not required by the app |
| Android SDK | `C:\Program Files\Android\Sdk` — platform-tools **37.0.1**, `platforms;android-36`, `build-tools;36.0.0`, cmdline-tools **16111833**; `ANDROID_HOME`/`ANDROID_SDK_ROOT` set at machine scope, `platform-tools` and `cmdline-tools\latest\bin` on the machine `PATH` |
| Android Studio | **2026.2.2.9** (`AI-262.10968.63.2622.16840690`) at `C:\Program Files\Android\Android Studio`, bundled JBR, Start Menu shortcut |
| Gradle | Wrapper **9.8.2** committed under `gradle/wrapper/`; no system Gradle. Kotlin **2.4.21** and JUnit **5.14.4** pinned in `gradle/libs.versions.toml` |
| Phones | Honor X7c — Android 14, MagicOS 8.0, Huawei Band 9 · Redmi Note 8 Pro — Android 11, MIUI 12.5.3, Huawei Band 11 |

## Remaining setup (M1)

1. Write `local.properties` with `sdk.dir` (escaped spaces) — untracked, needed once an Android module
   exists.
2. Create `:data` (Room, Health Connect, OpenFoodFacts) and `:app` (Compose, Hilt) and verify
   `.\gradlew.bat :app:assembleDebug`.

## Build and test

```powershell
.\gradlew.bat :core:test            # pure JVM maths: formulas, day bucketing, recipes
.\gradlew.bat :data:testDebugUnitTest
.\gradlew.bat :app:assembleDebug    # foss flavour by default
.\gradlew.bat :app:installDebug
.\gradlew.bat lint detekt           # if detekt is added, keep the config in the repo
```

- Two flavours: `foss` (zxing-cpp only) and `google` (adds ML Kit). `foss` is the default; a change
  must build in both before it is called done.
- Instrumented tests that touch Health Connect or the camera run on a **real device**, not an
  emulator — an emulator has no Health Connect data source and no real sensor noise.
- Under DSH `workspace-write`, Gradle cannot write `~/.gradle`: native extraction fails with
  `Couldn't open current thread, error = 5`. Run builds with `GRADLE_USER_HOME` pointing at a writable
  directory, and set `org.gradle.vfs.watch=false` there to silence the file-watcher. A normal shell
  (no sandbox) needs neither workaround.

## Devices and how to test on them

| Device | Role | Constraints |
| --- | --- | --- |
| Honor X7c (A14) | Owner's daily driver | Health Connect is built in; grant permissions via Settings |
| Redmi Note 8 Pro (A11) | Wife's daily driver | Health Connect **app** must be installed; MIUI kills background work; this is the performance floor |

- Ask before installing, uninstalling or clearing anything on the Redmi. A debug build replacing a
  release build with a different signing key requires an uninstall — which wipes data. Say so first.
- Band data is only valid if Gadgetbridge is installed, paired, and has Health Connect export enabled
  for that device. If the numbers look empty, check that chain before suspecting the app.
- Logcat is evidence, not conversation: capture it to a file, quote the relevant lines, and keep it
  out of git if it contains personal data.

## Workflow

1. **Scope** — which milestone item, which files, which device. If the request is ambiguous, read
   `docs/04-plan.md` and the relevant ADR before asking.
2. **Design** — for anything non-trivial, write the decision first: options, choice, consequence.
   Small ADRs live in `docs/03-architecture.md` §13.
3. **Implement** — smallest change that satisfies the item; `:core` before UI, tests alongside.
4. **Verify** — unit tests, then a real device when the change touches Health Connect, camera,
   permissions, background work or storage.
5. **Record** — update the docs when behaviour or a decision changed; a feature that contradicts the
   docs is unfinished, not "done with a note".

## Code standards (Android)

- Kotlin only, Compose only, no XML layouts, no Fragments.
- `:core` has no Android imports and no `java.time` calls that depend on the device default zone —
  pass the zone in.
- Coroutines + `Flow`; no blocking calls on the main thread, no `runBlocking` outside tests and
  `main()`.
- All user-visible text in `values/strings.xml` + `values-ru/strings.xml`. No hardcoded strings, no
  string concatenation for sentences; use `plurals` and format arguments.
- Locale-aware formatting for numbers, dates and units; store metric internally, convert at the edge.
- Room: explicit `@ColumnInfo` names, `exportSchema = true`, migrations tested with
  `MigrationTestHelper`.
- Permissions: request at the moment of use with a rationale, handle "denied permanently" by pointing
  at settings, never block the whole app on a denial.
- `PendingIntent` always with `FLAG_IMMUTABLE`; declare `<queries>` for Gadgetbridge, otherwise it
  looks uninstalled on Android 11+.
- No third-party SDK that phones home. New dependency = a version-catalog entry + a note in the PR
  description saying why the platform cannot do it.
- Release builds: R8 on, resource shrinking on, no reflection-dependent libraries without keep rules.

## Data and privacy

- No backend, no account, no analytics, no crash upload. If that ever changes, it is an ADR first.
- `allowBackup=false` and data-extraction rules that exclude the database: health data must not drift
  into a cloud backup.
- Network is restricted to OpenFoodFacts hosts; cleartext off; a request without our User-Agent is a
  bug.
- Exports are the user's data leaving the app: write them where the user chose, never to a fixed path,
  never automatically to a shared folder.

## Choosing instruments

| Question | Instrument |
| --- | --- |
| Why did the app crash or misbehave? | `adb logcat --pid=$(adb shell pidof -s <pkg>)`, plus the in-app debug screen |
| What does Health Connect actually contain? | The debug screen's raw-record dump, not guesses from the UI |
| Why is a background sync late? | `adb shell dumpsys jobscheduler`, WorkManager diagnostics, battery-optimisation state |
| Is the layout broken at 200% font size? | Compose previews at several font scales, then a device |
| Is the database right? | `adb exec-out run-as <pkg> ...` on a **debug** build only, or a Room export |
| Is scanning fast enough? | Macrobenchmark and manual timing on the Redmi, not the Honor |
| What does OpenFoodFacts return for this barcode? | `curl` against the staging host for experiments, a fixture for the test |
| Is a dependency compatible? | `.\gradlew.bat :app:dependencies`, AGP/Kotlin compatibility table, not memory |

## Definition of done

A change is done when all of these are true:

1. It builds in both flavours.
2. The maths has a unit test; the schema has a migration test.
3. It was exercised on a real device if it touches Health Connect, camera, permissions, background work
   or storage.
4. The relevant doc, ADR or plan checkbox is updated.
5. It introduces no new network call, permission, dependency or stored personal field without a line
   in the docs explaining why.

## Language

The user works in Russian and English interchangeably, and may mix both inside a single message. Read
either without asking for a translation, and never ask which language to use.

| Where | Language |
| --- | --- |
| Chat replies | **The language the user is writing in** — English in this project so far; if the user switches to Russian, answer in Russian. Never ask which language to use |
| Everything committed — `docs/`, code comments, commit messages, this file | **English** |
| App UI strings | English and Russian, both maintained; neither is a translation afterthought |
| Quoted material — error messages, `logcat` lines, API field names | Original, unchanged |
| Terms of art — `Health Connect`, `Gadgetbridge`, `record`, `barcode`, `TDEE` | Original, never translated |

A Russian question does not make the write-up Russian: investigate in whichever language is clearer,
write the finding in English, explain it back in Russian.

## Style

- Markdown, relative links inside the repo, absolute paths only when quoting a host location.
- Dates as `YYYY-MM-DD`. Hashes lowercase hex with the algorithm named. Versions as `major.minor.patch`.
- Prefer tables for inventories and states, lists for reasoning.
- Keep secrets, licence keys, personal data and real body measurements out of tracked files.
