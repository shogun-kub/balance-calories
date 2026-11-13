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

### E (continued). A second food source for Russia — **closed 2026-11-12: none**

The owner's decision after the research: Open Food Facts search is enough, and no bundled Russian pack is
built. That closes the three options below and removes them from the plan, so the coverage numbers stay in
`docs/02-research.md` §3.1.3 as a **known limitation** rather than as work waiting to be done: a Russian
packaged product that OFF does not hold with a full nutrition block reaches the app the two ways it already
has - typed once by hand, or scanned from a pack whose label we type.

| Route | Status |
| --- | --- |
| ФИЦ питания (ion.ru) national composition database | **closed** to public access, per their own statement |
| Скурихин / Тутельян tables | a **book**; FAO/INFOODS Russia page is a bibliography, no dataset |
| Честный знак «Национальный каталог» | GTIN-keyed API, but a participant key and per-organisation grants; no individual route |
| health-diet.ru, kalorizator.ru | ToS forbids extraction, parsers and republishing; database-maker right claimed |
| Edamam / Nutritionix / FatSecret / spoonacular / CalorieNinjas | paid, caching bans, English-only or shutting down; UPCitemdb resolved **0 of 4** real Russian codes |
| barcodelookup, EAN-Search | $99/month and paid-per-lookup |
| Bundled OFF pack (460–469, complete nutrition) | **not built** - the owner does not want it |
| Hand-typed generic-food pack | **not built** - the starter ingredient pack already covers cooking |

### H. Writing our products back to Open Food Facts — open since 2026-11-12

The owner asked whether a product typed here can also be **added to** Open Food Facts instead of only being
stored locally. **Yes**, and this is what the current documentation says (`API v2`, marked deprecated, and
`API v3` which supersedes it):

| Fact | Source |
| --- | --- |
| `POST /cgi/product_jqm2.pl` with `code` **creates** the product when the barcode is unknown and **edits** it when it is known (v3: `POST /api/v3/product/{barcode}`, with `PATCH` for partial updates) | [Edit Product](https://openfoodfacts.github.io/documentation/docs/Product-Opener/v2/products/post-cgi-product_jqm2.pl/) |
| Every write needs a user: a session cookie from `/cgi/session.pl`, or `user_id` + `password` in the body | same |
| **Anonymous creation** of a *new* product is possible; anonymous *modification* of an existing one is not | [API/Write](https://wiki.openfoodfacts.org/API/Write) |
| Fields available: `product_name_<lang>`, `brands`, `quantity`, `serving_size`, `nutrition_data_per`, `nutriment_*`, `comment`, and `app_name` / `app_version` / `app_uuid` for an app that writes under one account | [Edit Product](https://openfoodfacts.github.io/documentation/docs/Product-Opener/v2/products/post-cgi-product_jqm2.pl/) |
| A wrong password fails **silently** with an HTML 200 page | [API/Write](https://wiki.openfoodfacts.org/API/Write) |
| The server now checks that a write carries an identified user, where it previously did not | [openfoodfacts-server #11596](https://github.com/openfoodfacts/openfoodfacts-server/pull/11596) |
| Whatever is written is public under the **ODbL** and can be edited by anybody afterwards | [Open Food Facts](https://world.openfoodfacts.org/data) |

Three ways to do it, with the consequence of each:

1. **The person's own OFF account**, logged in inside the app. Honest attribution, but the app then holds a
   password for a third-party service, and we have a hard rule against storing credentials it does not own;
   it also needs a network call at save time, which is the one place the app is currently absolute about.
2. **One house account**, with `app_uuid` per person as OFF itself suggests for apps. One tap to contribute,
   but the password ships inside the APK, which is a public repository's worst place for a secret, and every
   edit would be attributed to us rather than to the person who typed the label.
3. **No write from the app**: type the product locally, and offer a share/copy of the values so a person can
   paste them into the OFF site from a browser when they care. Nothing leaves the phone by itself, no
   credentials, no new network host, and the offline rule stays intact.

**Recommendation, to be decided rather than assumed:** option 3 now, option 1 later if contributing becomes
something the household actually wants, and option 2 never - an embedded credential in a published APK is a
leak waiting to be found, and the licence consequence (a public, editable record) deserves a deliberate
decision rather than a convenient one. Nothing is implemented until the owner picks.

### I. Do the bottom tabs keep their names at a 200 % font scale?

`MainActivity` hides the labels below the icons when `fontScale >= 1.5` (`showLabels`), because four labels under
four icons at twice the size are the first thing to break inside words (P-19, ADR-026). The side effect, seen on the
Redmi on 2026-11-13: **at 200 % the navigation is four pictograms with no words** - and 200 % is precisely the
setting somebody uses because small text is hard to read. The icons are a house, a plate, bars and scales; "История"
against "Вес" is a guess from a glyph.

Options: keep as is (labels return at 100 %); show labels always and let them wrap to two lines at 200 % (needs
checking that «История» does not break inside a word); show labels always and accept a slightly smaller label style
at large scales. Owner's call - it is a product question, not a technical one.

### J. F-Droid's three-month rule, and what ships on the phones until then — open since 2026-11-13

The maintainer closed the first merge request with "app is not ready, see Policy Quality point 6": three
months of active development, unless the app has proven users, the author already maintains other apps on
F-Droid, or it was written for an event. The first commit here is **2026-11-07**, so the main index is closed
to this app until **2027-02-07**. The other three points of the review are fixed, and the next submission has
a green buildserver build behind it.

What happens in between is a decision:

1. **Nothing changes.** Both phones keep the APK installed by hand (`adb install -r`) on each release, and the
   submission waits for February. Cheapest, and it is what the household already does.
2. **A self-hosted F-Droid repository.** `fdroid server update` produces a signed index that can live on
   GitHub Pages; the client adds it as a repository and updates arrive with an "Update" button on both
   phones. This is exactly what F-Droid's policy suggests for apps outside the main repo. It has one
   prerequisite that is a decision rather than a task: **a signing key the household owns**, generated here,
   kept out of the repository forever (hard rule 2), and never thrown away casually - an app signed by a new
   key cannot be installed over the old one. That is also why switching costs one uninstall on each phone, and
   the current builds are signed with the debug key: the food log and the weight history would have to be
   exported and restored first.
3. **Both** - the self-hosted index for updates now, the main index in February.

Not recommended: reopening the request before February. The maintainer was explicit, and a premature second
look spends goodwill that the February submission will want.

**Recommendation, to be decided rather than assumed:** option 1 until a release is actually worth distributing
that way, then option 3 - with an export taken before the first switch, because a key that signs nothing is
cheaper to plan than a migration done in a hurry. Nothing is generated until the owner picks.

**Decided 2026-11-13: option 3, then trimmed to what is actually used.** The repository exists on the home LAN
(`http://KATANA:8080/repo`, served by `tools/serve_lan_repo.ps1`, index signed by a repository key, APKs signed
by the household's own app key) after GitHub proved unable to take the APK (**P-45**); `docs/fdroid/self-hosted.md`
is the record. The phone migration is done: the owner exported, uninstalled and reinstalled both phones on the
household key (diary item 57), so later builds install over the current one. What is *not* done, and was
deliberately left as a choice rather than a task: **no F-Droid client is installed, so no repository is added**
and updates stay manual until somebody wants the button. The main index remains a February task, unchanged.

### K. "We see no band data" is two different facts, and only one of them is fixable in the app — open since 2026-11-13

Measured on the Redmi (P-48): for as long as the app had existed on that phone, every day carried Health
Connect's own `_platform` baseline and nothing else, because **Gadgetbridge had never been granted write access**
— its Health Connect export was off and its permissions were never asked for. What the screen said during all
that time was *"нет данных браслета — показана оценка за весь день"*, which is true, unhelpful, and
indistinguishable from the same sentence printed for a person who genuinely did not move. The fix in that case
was two taps inside a *different* app.

Can this app tell the difference? Partly, and the parts matter:

| Signal | Available to us? |
| --- | --- |
| Record origins other than `_platform` | **yes** — a read gives `DataOrigin.packageName` per bucket, which is how the `_platform` rule works (P-37) |
| Gadgetbridge installed | **yes** — `<queries>` already declares it, so `PackageManager` answers honestly on Android 11+ |
| Gadgetbridge's Health Connect export switch | **no** — it is a preference inside GB's private storage; there is no documented contract for it (§1.4) and this project does not patch third parties (hard rule 9) |
| Whether Gadgetbridge holds HC write permissions | **no** — Health Connect exposes *our* grants to *us*; another app's grants are not readable |

So the honest version is a hint, not a check: when the last several days hold nothing but `_platform` *and*
Gadgetbridge is installed, say which app the switch lives in — *"Gadgetbridge не передал данные в Health Connect:
Настройки → Внешние интеграции → Health Connect app → Включить экспорт"* — and keep the wording about our own
permissions separate. US-8 in `docs/01-product.md` promises exactly this kind of check, with a deep link, and
the promise is currently unimplemented for the Gadgetbridge half.

Open because it is a product decision, not a mechanism: how many empty days make the hint appear (one? three?),
whether it belongs on the band card or in settings, and whether a "проверить настройки Gadgetbridge" button
should deep-link into GB's `HealthConnectPreferencesActivity` — a component name of a third-party app, which
is exactly the sort of thing that breaks silently on a GB update. Recommended: the hint, no deep link, three
empty days, and one line in the band card rather than a new screen.