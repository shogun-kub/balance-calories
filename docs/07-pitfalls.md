# Pitfalls and judgement calls

Two things that cost hours, kept apart on purpose.

**Part 1 — Traps** (`P-nn`): a thing that breaks, the symptom you actually see, and what to do. Every one
of these was hit on this machine or this phone, not imagined.

**Part 2 — Judgement calls** (`J-nn`): no build fails when you get these wrong. They are the decisions that
turned out to be right and are easy to undo by accident.

An entry earns its place when a trap costs more than half an hour, or when a judgement call would be
re-litigated without it. The milestone narrative that produced it is in `docs/06-milestones.md` and is not
repeated here.

## Part 1 — Traps

### Build and toolchain

**P-01 · AGP 9 has Kotlin built in.** Applying `org.jetbrains.kotlin.android` fails with *"no longer
required for Kotlin support since AGP 9.0"*. Android modules apply only `com.android.application` /
`com.android.library`; the **Compose compiler plugin is still required** once `buildFeatures.compose = true`;
`:core` keeps the plain Kotlin JVM plugin.

**P-02 · `compileSdk` is 36, and dependencies must respect it.** Platform 37 is not installed and the SDK
lives under `Program Files`, so adding it needs an elevated shell. Current pins fit 36: Compose BOM
`2026.06.01` (ui 1.11.4, material3 1.4.0), lifecycle `2.10.0`. Newer Compose (ui 1.12.x) and lifecycle
`2.11.0` declare `minCompileSdk=37` and fail `checkDebugAarMetadata` — check the AAR's
`aar-metadata.properties` before upgrading, not the release notes.

**P-03 · Kotlin is 2.3.20 on purpose, and Room uses KSP.** Kotlin 2.4.21 builds this project, but KSP's
newest release (`2.3.13`) declares `kotlinBaseVersion=2.3.20`. Measured: **2.3.20** + KSP **2.3.13** + Room
**2.8.5** generates `*_Impl.kt` and exports `schemas/…/<version>.json`. KAPT works but is slower and
deprecated. Revisit when KSP ships a 2.4.x build.

**P-04 · After a schema bump, package the androidTest APK twice.** The exported `N.json` becomes a test
asset, but the asset merge can run before KSP writes the new JSON, so the file never reaches the APK and
`MigrationTestHelper` fails with *"Cannot find the schema file in the assets folder"*. Build once to export
the schema, then `:data:assembleDebugAndroidTest` again and confirm the APK lists every version:

```powershell
python -c "import zipfile;print(sorted(n.split('/')[-1] for n in zipfile.ZipFile(p).namelist() if n.endswith('.json')))"
```

`--rerun-tasks` does not help when the schema is generated in the same build.

**P-05 · An `<adaptive-icon>` outside a `-v26` folder is not resolved at all.** Lint's `ObsoleteSdkInt`
suggests merging `mipmap-anydpi-v26` into `mipmap-anydpi` because `minSdk` is 28. Done, the build fails
resource linking with `resource mipmap/ic_launcher not found`. The folder stays; the warning is documented,
not suppressed.

**P-06 · An XML comment cannot contain `--`.** AAPT2 reports the result as *"Failed to parse XML file"* with
no mention of comments, which sends you looking at the resource itself. Use one em dash or a colon.

**P-07 · PowerShell corrupts files in two ways.** `Get-Content`/`>` turns a pulled SQLite database into
UTF-16 — pull with `Start-Process -RedirectStandardOutput` and take both `balancecalories.db` and its
`-wal`. The file on the device is `balancecalories.db`, not `balance_calories.db`, so a pull that reports
success can still have grabbed nothing. And `-replace` with a `\n` pattern silently matches nothing in a CRLF file: the edit reports
success and changes nothing. Verify with `Select-String` after any scripted edit, or use an editor that
preserves the endings.

### adb and the phone

**P-08 · Two adb servers fight over port 5037.** Honor Suite ships an adb **v39** server; SDK
platform-tools is **v41**. They evict each other's daemon (`adb server version (39) doesn't match this
client (41); killing...`) and `adb devices` reports nothing while the phone is healthy. Use a private port
for every call — see `docs/08-environment.md`.

**P-09 · The Honor reverts to landscape after a reinstall, and `settings` cannot stop it.** Both
`settings put system user_rotation 0` and `accelerometer_rotation 0` were accepted and ignored; the dump kept
reporting `rotation="1"` while the taps went to the wrong places. The command that actually holds is
`wm user-rotation lock 0` (Android 12+), and it has to be re-issued after every `install -r`. Then dump and
tap **by the reported bounds** — a landscape dump reports x up to 1610 on a 720-pixel-wide phone, which is
its own kind of wrong answer.

**P-10 · `input keyevent 4` is two different keys.** With an IME open it closes the keyboard and stays on
the screen; with no IME open it pops the screen, and a fixed-coordinate tap immediately after lands on
whatever is underneath. Both were observed. Use it to dismiss the keyboard, then dump again.

**P-11 · Dump before every tap — and check the keyboard, because a fresh dump can still lie.** Compose
dialogs and fields push buttons upward, so a tap aimed at a button hits the IME (an avocado became 1503 g);
while the keyboard is up a tap aimed at a list row types a space into the query instead. The nastier half:
`uiautomator dump` reports the **app window**, so a node at `y=1167` comes back with coordinates that are
current and still wrong when the IME covers that area — three taps "on" a settings row typed digits into a
text field instead (2026-11-11, a macro-norm field went from `63.467` to `63311.467`). Before tapping
anything in the lower half, check `dumpsys input_method | grep mIsInputViewShown`; if the view is up, a
`keyevent 4` may close it, and tapping a non-field area (a title) defocuses the field for good. Also:
`input text` is ASCII only, so a Cyrillic product cannot be typed — pick a row by what is visible.

**P-35 · Windows can hide a phone from adb with `Access denied`, and it is not a permission problem.**
The symptom is `adb devices` empty while the Device Manager shows `Android Composite ADB Interface` with
status OK and MTP still works, and `ADB_TRACE=usb` prints `Failed to open device handle … Error [5]:
Access denied`. Every natural hypothesis is wrong: it survives killing every `adb` process, closing the
vendor suites (Honor Suite, Microsoft Phone Link), a replug, and both adb 36.0.0 and 41 — and the decisive
tell is that a daemon started **elevated** fails identically. VMware Workstation's `VMUSBArbService`
(`vmware-usbarbitrator64`) claims USB devices exclusively so it can hand one to a virtual machine, and a
handle claimed that way is refused to everybody, administrators included. Fix: `sc stop VMUSBArbService`
(reversible with `sc start`, and VMware raises it again by itself on the next launch), then `adb kill-server`.
Check `vmware-usbarbitrator64` and `usbipd` (the WSL passthrough) **before** suspecting a driver or an ACL:
the error names permissions while the resource is exclusive, which is why an elevation test settles it in one
step instead of an afternoon.

### Health Connect

**P-12 · Declare `android.permission.health.READ_*` in the manifest.** Without the declarations the dialog
has nothing to offer and `pm grant` refuses; the app compiles and looks finished.

**P-13 · Android 14 needs an activity handling `VIEW_PERMISSION_USAGE` with category
`HEALTH_PERMISSIONS`.** Without it every read throws `Incorrect health permission state …` *even with all
permissions granted*. The androidx `ACTION_SHOW_PERMISSIONS_RATIONALE` filter covers Android 11–13 only, so
declare both.

**P-14 · MagicOS kills the permission dialog.** Tapping the request button launches
`GrantPermissionsActivity` and the phone's memory manager kills that process within a second — the dialog
flashes and closes, every time. Not an app bug, not fixable from the app; the owner granted the permissions
with `adb shell pm grant` instead.

**P-15 · A stale APK hides a migration.** `installDebug` of an old `app-debug.apk` keeps the old `:data`
classes, so `PRAGMA user_version` stays behind and "the migration was verified" is false. Rebuild `:app`
before believing a device test after a schema bump.

**P-36 · "Update required" can mean "not installed at all".**
`HealthConnectClient.getSdkStatus` answers `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` for **both** "this
device has no provider" and "the provider is too old to serve this client", so an app that maps the code
straight to a sentence tells the user to update something that is not there. Measured on the Redmi Note 8
Pro (Android 11, Health Connect never installed): the Today row read "требуется обновление". The package
table is the fact that separates the two — `packageManager.getPackageInfo("com.google.android.apps.healthdata",
0)` — so the state enum carries `NOT_INSTALLED` on its own and the decision is a pure function with a JVM
test pinning the precedence. The same idiom applies to any provider API that collapses "missing" and
"outdated" into one code: check the package, do not trust the status for the sentence the user reads.

### Camera

**P-16 · `PreviewView`'s internal `SurfaceView` ignores its parent's bounds.** The preview painted over the
title and the torch button, at screen size, inside a 240 dp box; `implementationMode = COMPATIBLE` did not
help. `adb shell dumpsys activity top` showed it: `PreviewView 0,0-656,240` but `SurfaceView 0,0-720,1280`
inside it. Fix: own the surface — a plain `TextureView` plus `Preview.setSurfaceProvider` (ADR-018).
Diagnose with the view hierarchy, not with screenshots.

**P-17 · A missing `INTERNET` permission reads as a network error.** Every lookup returned
`network error: Permission denied (missing INTERNET permission?)`, and the offline queue stored it — the
queue worked perfectly and the app simply could not open a socket. The error text names the cause; read the
recorded `last_error` before suspecting the network.

### Strings and localisation

**P-18 · A format string and its call site drift apart silently.** The device threw
`IllegalFormatConversionException: d != java.lang.String` and killed the tab: the string was
`%1$d … %2$d … %3$s %4$s` and the call passed `(count, first, last, days)`.
`StringResourceParityTest` compares the two string files, not the call. Launch the screen.

**P-33 · A display fragment obeys different rules than a sentence, and getting it wrong is silent.** The
first device build of M7.7 showed three such mistakes in one line: the macro *labels* were declared as
`"Белки %1$d"` and rendered with `stringResource(id)` and no arguments, so the row printed
`Белки %1$d 20 · Жиры %1$d 12`; the separator `" · "` lost its spaces because AAPT trims leading and trailing
whitespace (a quoted value keeps them, and `\u00A0` after the dot stops it dangling at the end of a wrapped
line); and the grams were formatted with the *editable field* formatter, printing `1.1` under `187 ккал` on a
Russian phone. None of the three fails a test: the parity test compares the files to each other, and a
rendered screen is what a JVM test cannot see.

The rules that came out of it: a fragment holds a noun and no format specifier (`MacroLabelTest` pins the
three labels); punctuation that must keep its whitespace is quoted; a number shown to a person goes through
`displayNumber`, and the ASCII `trimNumber` stays where a value will be parsed back.

**P-19 · Labels break inside words at a 200 % font scale.** A squeezed `Text` does not ellipsize: it breaks
mid-word, so "Настройки" becomes a vertical column of single letters about 1000 px tall and pushes
everything else away. The signature is a text node whose bounds are much taller than wide in
`uiautomator dump` — check that before believing a screenshot. Fixes: `WrappingRow` (`FlowRow`) for label /
value pairs and chip groups, and an icon-only tab bar above 1.5× with the label moved into the icon's
content description.

**P-20 · A missing Russian `few` falls back to `other` silently.** Nothing fails; the screen prints "5
дня". `StringResourceParityTest` therefore asserts the quantity set each language needs — en
`one`/`other`, ru `one`/`few`/`many`/`other`.

**P-21 · A refusal must name its own noun.** History told a person with too few weigh-ins *"needs 3 logged
days"* — the wrong thing to fix — and reused that sentence for "weigh-ins too close" and both implausible
refusals. One message per named reason, with the thresholds passed in from the constants the rule uses, so
text and arithmetic cannot drift. The same class of bug as P-18, one level up: the sentence and the code
that fed it had drifted apart, and only saying it out loud caught it.

### Compose

**P-22 · A `@Composable` read inside a `DrawScope` does not compile.** `Canvas { }` is a `DrawScope`, not
composition: `MacroGauge` read `ChartPalette.protein` inside it, which was fine while the palette was a
plain object and stopped the moment it became theme-aware. Hoist the colours above the canvas.

**P-23 · `fillMaxWidth()` inside `horizontalScroll` collapses to zero.** The 90-day chart was blank until
the canvas got an explicit width. And `rememberScrollState(initial = …)` cannot open at the end, because
the maximum is only known after layout — scroll in a `LaunchedEffect(scrollState.maxValue)`.

**P-24 · Gutters must be measured, not fixed.** A 46 dp label gutter overflowed at `font_scale 2.0`; the
axis numbers and the date row are now measured with `TextMeasurer` and the plot sizes around them — the
ADR-026 family, this time inside a `Canvas`.

**P-25 · `Int.times(Dp)` does not exist and `min` over `Dp` needs `minOf`.** `2 * arcThickness` does not
compile for `Dp`. Trivial, and it costs a build cycle every time it is forgotten.

**P-37 · A platform baseline wears an origin, so "has an origin" is not "was measured".**
Health Connect's own layer writes a daily `TotalCaloriesBurned` figure that no installed app produced, and it
shows up in two different shapes. On the Honor X7c the origin set was **empty** and the constant was
1858.25 kcal/day, which the app already treated as unattributed. On the Redmi Note 8 Pro (Android 11, Health
Connect app, no band) the same baseline arrived tagged **`_platform`** — a non-empty origin — so a check of
the form `origins.isNotEmpty()` called it attributed, the day became `complete`, and the screen printed HC's
BMR guess (a constant 1564.5 kcal/day for thirty consecutive days) as if a band had measured it. The
constant is the tell: scan the history for a value that does not move. The rule now excludes the
`_platform` pseudo-origin explicitly (`PLATFORM_ORIGIN` in `:core`, used by the mapper and by both read
paths), and the two symptoms are documented together, because fixing one shape left the other alive.

### Data and storage

**P-26 · A backup must be self-consistent.** The first export skipped the 69 starter products to save
space, and the first real restore then refused its own file: a food entry from the starter pack pointed at
a product the file did not contain, so it failed its own foreign key. 69 rows is nothing. Restoring also
has to tell the starter importer which world it is in (`markImported` vs `ensureImported`), or a fresh
install ends up with two of every ingredient.

**P-27 · The product index is external-content FTS4.** Rows written straight into `product` are invisible
to search until `INSERT INTO product_fts(product_fts) VALUES('rebuild')`, and nothing fails — the user just
stops finding products. The restore does it; a test asserts search works afterwards.

**P-28 · A refusal that carries only a reason is a dead end.** `ImportOutcome.Refused` said "файл
повреждён" and nothing else; carrying the underlying message turned a mystery into a one-line diagnosis.

**P-29 · A seeded history is the only way to see a chart.** One weigh-in draws one dot, so 47 synthetic
weigh-ins went into a copy of the live database, were pushed with `run-as`, and the device was restored
afterwards. That copy is why `tmp/` is git-ignored — a pulled database sat in the working tree as an
untracked file, which is exactly what hard rule 1 forbids.

### Data on a real phone

**P-34 · The selected profile is app state, not a test default.** The app stores `active_profile_id` in
`app_setting` and falls back to `profiles.first()` when it is absent; creating a profile **switches to it**.
So a test run that starts logging without looking at the header can write into the owner's day — the rows
look identical, because the fixture profile is called `Test` and the real one has a person's name. Before
the first write of a session, read the profile name in the header (or `active_profile_id` together with the
row's name), and switch deliberately. The same rule protects the reverse direction: leaving the app on the
fixture at the end of a run means the owner opens his app tomorrow and finds somebody else's day. The same
selection is the sync destination: `ActivitySyncWorker` reads `activeProfileId` and then checks
`band_enabled` on that profile, so a run parked on the fixture silently stops the owner's band sync as well
as hiding his day.

### Kotlin

**P-30 · `kotlin.math.round` sends ties to even.** `round(12.5)` is 12, while every kcal in the app rounds
up with `roundToInt`. A percentage printed beside a rounded kcal now uses `roundToInt`, with a test pinning
12.5 → 13.

### Tests

**P-32 · `:data` device tests cannot use backticked method names.** D8 rejects the generated lambda classes
(`Space characters in SimpleName … not allowed prior to DEX version 040`) because `minSdk` is 28. `:core`
uses JUnit 5 and backticked names; `:data` and `:app` use JUnit 4 and camelCase. It is a module convention,
not a preference.

### Docs

**P-31 · Appending to a Markdown table can glue rows in front of the title.** `docs/03-architecture.md` had
two ADR rows stuck to the end of line 1, in front of `# Architecture`. Markdown renders that as a table at
the top of the page and no test reads the docs. When appending, check the first line of the file after the
write, not just that the text landed somewhere.

## Part 2 — Judgement calls

**J-01 · A dash is not a number.** With nothing logged, the gauge printed "—" over "из 1992 ккал". The one
figure a person wants before eating is how much they have, so the headline became the allowance and the
subtitle names it. Never "0 ккал" — that is a claim, not an absence.

**J-02 · The centre of a half circle is narrow.** At `font_scale 2.0` the phrases were twice as wide and ran
across the track. They are constrained to the inner opening, and above 1.5× the remaining/over line moves
under the arc. Measure the opening; do not guess it.

**J-03 · Quiet colours and readable colours can be in a fight, and quiet wins by default.** The 300-level
chart tones stopped a carbohydrate arc from reading as a failure — and made it invisible on cream at
1.4 : 1 (ADR-029, corrected by ADR-033). Lightness is per surface; the hue still does not shout, the value
does the job.

**J-04 · A theme is a place, not a coat of paint.** Every screen already asked
`MaterialTheme.colorScheme`, so dark mode needed no screen changes: the work was the palette, the window
background and the test.

**J-05 · A percentage needs a denominator.** "5 % of the daily norm" is meaningless in an app that knows
only a calorie target. The fix was not a formula but a number the person can see and change.

**J-06 · Compare shares, not grams.** 30 g of protein is a quarter of a day's protein and an eighth of a
day's carbs, so the same 30 g has to win for the chicken and lose for the rice.

**J-07 · Being new is not a reason to own the band.** A throwaway second profile silently took
`band_enabled` from the owner and stopped his sync; deleting it then left nobody reading the band, and
Today lost its sync button with nothing on screen to explain it. Ownership moves only by an explicit tap.

**J-08 · A total over estimates is a lie of emphasis.** "Сожжено 68 322 ккал" for 30 days was true
arithmetic and a wrong sentence. It now gives an average per day and counts the estimated days.

**J-09 · An estimated burn is an outline, not a fill.** Thirty days of `PAL_ESTIMATE` drawn solid reads as
thirty measurements. Shape carries the measured / estimated distinction that colour cannot.

**J-10 · At 24 dp an icon is a silhouette test, not a drawing.** Everything that survived was outline;
everything that failed was detail — pans, a domed crust, one flat colour with no internal edge.

**J-11 · Two rectangles on a bar are a capital H.** Whatever you do to them, a dumbbell needs round
weights, and a lit disc face on each outer end is what says *weight seen from the side* rather than
*letter*.

**J-12 · A swept solid is not a translated outline.** Filling the outline shifted down by the object's
depth leaves a gap wherever the object is thinner than that depth; at the slice's tip it showed as two
prongs with the background between them. The side is the shape plus its copy plus a quad on every edge, one
colour, one path.
