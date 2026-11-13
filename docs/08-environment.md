# Environment and commands

This machine, its devices, and the commands that work on them. Everything here was measured on this host,
not recalled. When a version changes, it changes here and in `gradle/libs.versions.toml` — nowhere else.

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

## Environment

| Fact | Value |
| --- | --- |
| Host | Windows, PowerShell (`pwsh`), NTFS; workspace `E:\Projects\BalanceCalories`, ~420 GB free |
| File policy | DSH sandbox; has run under both `workspace-write` and `danger-full-access`. Writes outside the repo may need approval, so treat anything under `Program Files` as read-only unless proven otherwise |
| Java | Liberica JDK **21.0.11** LTS, `JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-21-Full\` |
| Git | **2.49.0.windows.1**; repository initialised on `main` (`core.autocrlf=false`), first commit `d195f3a` |
| Python | **3.14.2** — used for `tools/` generators, not in the app |
| Node.js | **v24.13.1** — available, not required by the app |
| Android SDK | `C:\Program Files\Android\Sdk` — platform-tools **37.0.1**, `platforms;android-36`, `build-tools;36.0.0`, cmdline-tools **16111833**; `ANDROID_HOME`/`ANDROID_SDK_ROOT` set at machine scope, `platform-tools` and `cmdline-tools\latest\bin` on the machine `PATH` |
| Android Studio | **2026.2.2.9** (`AI-262.10968.63.2622.16840690`) at `C:\Program Files\Android\Android Studio`, bundled JBR, Start Menu shortcut |
| Gradle | Wrapper **9.8.2** committed under `gradle/wrapper/`; no system Gradle. Kotlin **2.3.20** (held back for KSP — see the build notes above) and JUnit **5.14.4** pinned in `gradle/libs.versions.toml` |
| Phones | Honor X7c — Android 14, MagicOS 8.0, Huawei Band 9 · Redmi Note 8 Pro — Android 11, MIUI 12.5.3, Huawei Band 11 |

### adb on this machine — use a private port

Honor Suite (`C:\Program Files (x86)\HonorSuite`, service `HonorSuiteService64.exe`) bundles an adb
server **v39** and keeps it on port **5037**; SDK platform-tools is **v41**. The two evict each
other's daemon — `adb server version (39) doesn't match this client (41); killing...` — and
`adb devices` then reports nothing even though the phone is healthy. Run every adb call on a private
port instead of fighting over 5037:

```powershell
$env:ANDROID_ADB_SERVER_PORT = '5039'
& 'C:\Program Files\Android\Sdk\platform-tools\adb.exe' devices -l
# <serial>   device   product:ALT-LX1 model:ALT_LX1 device:HNALT-QL
```

The Honor X7c (`ALT-LX1`, Android 14 / SDK 34, MagicOS `8.0.0.302(C185E8R2P5)`) exposes the real
adb interface as `MI_02` — `bInterfaceClass=255, SubClass=66, Prot=1`, bound to `WINUSB`. `MI_01` is
a `CDROM Linux File-CD Gadget` (Honor's installer disc), not mass storage. The interface only
appears after Honor Suite's driver is installed; MagicOS then shows the `Allow USB debugging` dialog.
An adb server started from this sandbox dies with the shell that launched it, so bootstrap and query
in the same command.

The Redmi Note 8 Pro (`begonia_ru`, Android 11 / SDK 30, MIUI `V12.5.3.0.RGGRUXM`, arm64-v8a,
1080×2340 @ 440, locale `ru-RU`) exposes adb on `MI_01`, also bound to `WINUSB`, and needs no vendor
driver. It was invisible to adb for an hour for a reason that looks like a permissions bug and is not
(P-35): **VMware's USB arbitrator held the interface** — `VMUSBArbService`, process
`vmware-usbarbitrator64` — so `CreateFile` returned `Error [5]: Access denied` for this user *and* for an
elevated daemon. `sc stop VMUSBArbService` fixes it until VMware raises the service again; the service is
`Automatic`, so a reboot brings the problem back. With it stopped, a plain unelevated daemon opens the
phone:

```powershell
# 4lvscukrauuwkrzh   device   product:begonia_ru model:Redmi_Note_8_Pro device:begonia
```

Android 11 has no system Health Connect: on this phone the app is a separate install
(`com.google.android.apps.healthdata`), and without it the whole activity side reads as unavailable.

## Build and test

```powershell
.\gradlew.bat :core:test            # pure JVM maths: formulas, day bucketing, recipes
.\gradlew.bat :data:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
.\gradlew.bat lint detekt           # if detekt is added, keep the config in the repo
```

- **One build, no flavours.** The `foss`/`google` split was dropped when zxing-cpp turned out to be
  enough (ADR-017); adding ML Kit later would be a flavour again, not a rewrite, because the decoder sits
  behind `BarcodeScanner`.
- Instrumented tests that touch Health Connect or the camera run on a **real device**, not an
  emulator — an emulator has no Health Connect data source and no real sensor noise.
- Under DSH `workspace-write`, Gradle cannot write `~/.gradle`: native extraction fails with
  `Couldn't open current thread, error = 5`. Run builds with `GRADLE_USER_HOME` pointing at a writable
  directory, and set `org.gradle.vfs.watch=false` there to silence the file-watcher. A normal shell
  (no sandbox) needs neither workaround.

### Instrumentation tests: install and instrument by hand

`:data:connectedDebugAndroidTest` **reports BUILD SUCCESSFUL while running nothing** on this machine.
AGP's test engine talks to a different adb server than the private port, prints
`Failed to get device API level … device not found`, and leaves an empty results XML. Do not trust its
exit code; check that the XML has a `tests=` count.

Run the tests explicitly instead:

```powershell
$env:ANDROID_ADB_SERVER_PORT = '5039'
adb = 'C:\Program Files\Android\Sdk\platform-tools\adb.exe'
.\gradlew.bat :data:assembleDebugAndroidTest
& $adb install -r -t data\build\outputs\apk\androidTest\debug\data-debug-androidTest.apk
& $adb shell "am instrument -w com.balancecalories.data.test/androidx.test.runner.AndroidJUnitRunner"
# OK (109 tests) — database behaviour, migrations, repositories, starter pack, products, food, recipes, band activity, adaptive TDEE
```

App instrumentation tests are a **separate** APK and runner: `com.balancecalories.test` (not
`com.balancecalories.app.test`), installed with `-t` alongside the app APK, and the app APK must be
reinstalled whenever a dependency is added — the test APK alone resolves classes from the installed app.

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

### Previewing English on a Russian phone

Without touching system settings, then undo the override:

```powershell
adb shell "cmd locale set-app-locales com.balancecalories --locales en"
adb shell "cmd locale get-app-locales com.balancecalories"   # expect [en]
# ... screenshot / test ...
adb shell "cmd locale set-app-locales com.balancecalories --locales ''"
```

The Honor's system locale is `ru-US`; the screens were checked this way on 2026-11-08 and again on
2026-11-11. Setting it recreates the activity. Changing the
override recreates the activity, which resets in-memory navigation to Today.

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

## Pushing to GitHub from this machine

The repository is published at <https://github.com/shogun-kub/balance-calories> and pushed over SSH with the
key `E:\ssh-keys\KATANA-dev.ppk`.

**The key is a PuTTY file and this `puttygen` cannot convert it.** The Windows build installed here has no
command-line options for conversion (`-O` in particular is a Linux-only flag in that build), so the usual
one-liner does not exist. What does work, without installing anything global:

```powershell
& $py -m pip install --quiet cryptography          # only needs doing once
& $py tmp\key_conv.py                              # parses the PPK and writes an OpenSSH key
```

The script reads the PPK header (`PuTTY-User-Key-File-3: ecdsa-sha2-nistp384`, `Encryption: none`), takes
the private scalar out of the base64 blob (an SSH `mpint`) and re-serialises it with
`cryptography`'s `PrivateFormat.OpenSSH`. The result is `E:\ssh-keys\KATANA-dev.openssh` plus the
`.pub`, and `ssh-keygen -lf` on it gives `SHA256:hrX9BjAZhGWPrikhgm8ZwzAJ6680FhpA7GQdlyU1Zm0` — the same
key the `.ppk` holds.

Two things that will otherwise cost an hour:

1. **Windows ACLs.** `ssh` refuses the key with `UNPROTECTED PRIVATE KEY FILE` until the file belongs to
   one user only: `icacls <key> /inheritance:r /grant:r "<USERDOMAIN>\<USERNAME>:R"`.
2. **Host keys, without trusting blindly.** `ssh-keyscan` returned nothing from this network, so the
   `known_hosts` file is written from GitHub's own published list
   (`https://api.github.com/meta` → `.ssh_keys`). That is a stronger check than trusting whatever answers
   the port first.

The repository then carries the key path in its own config, not in `~/.ssh/config`:

```powershell
git config core.sshCommand "ssh -i E:/ssh-keys/KATANA-dev.openssh -o UserKnownHostsFile=E:/ssh-keys/known_hosts -o IdentitiesOnly=yes -o BatchMode=yes"
```

`git config` writes to `.git/config`, which is not tracked, so the path never enters the repository —
`AGENTS.md` hard rule 2 is about keystores, but a private key is the same kind of thing.
