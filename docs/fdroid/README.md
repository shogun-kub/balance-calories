# BalanceCalories — submission to fdroiddata

The payload for a new-app merge request is the block below, between the two `---` lines: it follows
`fdroiddata`'s `.gitlab/merge_request_templates/Default.md`, which the first attempt ignored and the
maintainer rightly flagged. The rest of this file is the record around it — what the first review said, what
the recipe must look like, and what has to be true before the next submission.

---

## Abstract for new app inclusion

### Please introduce the functions and usage of your app in summary, and also attach some screenshots.

BalanceCalories is a local-first calorie balance app for a household of two, in Russian and English. One day
has four numbers: energy eaten, energy burned, weight, and the balance between the first two. Energy eaten
comes from a food log with 100 starter products, barcode scanning that resolves offline for known codes, a
fallback to Open Food Facts when the local search finds nothing, and recipes that compute per-portion
macros; energy burned comes from Health Connect, which Gadgetbridge fills from a band; weight is typed or
comes from the band. History shows an adaptive TDEE learned from the person's own intake and weight, a
recap, an intake/burn chart, a weight trend and a projection. Settings cover units, the day boundary,
language, macro norms and the meal set.

Screenshots are in `fastlane/metadata/android/{en-US,ru-RU}/images/phoneScreenshots/`, five per language,
taken from a fixture profile that holds no real person's data. The store icon is
`fastlane/metadata/android/en-US/images/icon.png`, drawn by `tools/icon_design.py` from the same geometry as
the launcher icon.

### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other
similar apps in F-Droid.

Two reasons, one personal and one about honesty of numbers.

The personal one: the app was written for the author and his wife, for two phones and one band, with no
account to create and nothing to upload. A calorie app that wants an account, or that cannot work on a
basement gym without signal, is the thing we did not want.

The technical one: the app never lets an estimate wear the clothes of a measurement. A burned-calorie number
from the band is stored and shown as a Health Connect record with a type and a time window; a typed weight is
typed; the daily target comes from an adaptive TDEE that learns from that person's own intake and weight
rather than from a table; and the day boundary and time zone live in one module (`:core`) so two screens
cannot disagree about which day a meal belongs to. Everything except the Open Food Facts lookups works
offline, including the whole food log and the whole history.

## Checklist

### Policy

* [x] The app complies with the [inclusion policy](https://f-droid.org/docs/Inclusion_Policy). _Pay special
      attention to Quality point 6._ — Quality point 6 asks for three months of active development. The first
      commit is dated **2026-11-07** and development has been continuous since (88 commits, diary in
      `docs/06-milestones.md`); this submission is made after **2027-02-07**, when the requirement is met.
* [x] The original app author has been notified (and does not oppose the inclusion). — The author is the
      submitter.
* [x] The upstream app source code repo contains the app metadata in a Fastlane folder structure. The
      summary, the full description, an icon, five screenshots per language and a changelog per version are
      in `fastlane/metadata/android/{en-US,ru-RU}/`; `en-US` is present.

### Docs

* [x] Please read [the guide](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) first if
      this is your first contribution. — Read, as was
      [`docs/Submitting_to_F-Droid_Quick_Start_Guide`](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).
* [x] Please make sure your metadata follows the best practice in [our
      templates](https://gitlab.com/fdroid/fdroiddata/tree/master/templates). — `fdroid rewritemeta` leaves
      the file unchanged, and the file carries no comments.
* [x] Please read the [Build Metadata
      Reference](https://f-droid.org/docs/Build_Metadata_Reference/) and make sure your metadata is valid.
* [x] Please read the [Quick Start
      Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).

### Merge Request Setup

* [x] The title of this merge request should follow "New app: app name" format. —
      `New app: BalanceCalories`.
* [x] Please make sure your fdroiddata fork is public and your branch is not protected.
* [x] Please read [our Git guide](https://gitlab.com/fdroid/wiki/-/wikis/Tips-for-fdroiddata-contributors/Git-Usage)
      if you don't know how to rebase your branch. Don't rebase your branch if there is no conflict.
* [x] All related [fdroiddata](https://gitlab.com/fdroid/fdroiddata/issues) and [RFP
      issues](https://gitlab.com/fdroid/rfp/issues) have been referenced in this merge request. — There is no
      RFP issue; the `Closes` line is omitted.
* [x] Please only submit one app in one MR.

### Metadata

* [x] Metadata must be put in `metadata/<applicationId>.yml`. — `metadata/com.balancecalories.yml`.
* [x] Metadata must be a valid YAML file.
* [x] Metadata must use LF as line ending.
* [x] Don't add summary/description/changelog/images or anything that should be provided in upstream repo. —
      The MR adds one file and nothing else; everything textual lives in the app's fastlane directory.
* [x] Releases are tagged and auto update is enabled unless there is a special reason. — `AutoUpdateMode:
      Version`, `UpdateCheckMode: Tags`.
* [x] There is an issue tracker and contact info of the author so that we can report bugs and contact the
      author. — `IssueTracker` is the GitHub tracker of the repository, where the author is reachable.
* [x] An AuthorName must be added. It doesn't need to be the real name.
* [ ] External repos are added as git submodules instead of srclibs. — Not applicable: no external repos.
* [ ] Enable [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds). — **Not enabled**, and the
      reason is that the release build is unsigned by design: `app/build.gradle.kts` applies a signing config
      only when `keystore.properties` is present, and that file is deliberately not in the repository, so
      there is no developer key whose certificate could be published. The APKs that ship are signed by
      F-Droid. If a reproducible build is wanted later, it needs a key kept by the author, which is a
      decision for the author and not a property of the code.
* [x] Setup abi split if the APK is large and the splitted ones can be much smaller. — Not needed: 11.6 MB,
      no native code of our own (`zxing-cpp` brings one `.so` through Maven).
* [x] Only the latest versions should be kept in the metadata before it's merged. — One build entry.
* [x] Don't add any disabled versions in the metadata.
* [x] The `commit` field should be the full hash. Please don't use tag or branch in commit. — Full hash, not
      the tag name.

### Pipeline

* [x] All pipelines should pass. — On the previous attempt: nine jobs green in 509 s, `fdroid build` in
      356 s, `check apk` in 153 s. The one red job was `fdroid rewritemeta`, caused by comments in the
      recipe, which are gone.
* [x] All warnings and errors in the Reports tab should be fixed or explained.
* [x] F-Droid CI runners are under GitLab's FOSS program, so there's no need for you to pay for any CI time.

---

## What the first review said, and what changed

[!57200](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57200) was closed by the maintainer on
2026-11-13 with four points. All four were checked, and three of them were real.

| Point | What it meant | What changed |
| --- | --- | --- |
| "template not used" | the description must follow `.gitlab/merge_request_templates/Default.md`, not a free-form summary | the block above is that template, filled in |
| ".yml should not have comments" | `fdroid rewritemeta` stops the pipeline on a recipe with comments (**P-44**) | the file is exactly `rewritemeta`'s output |
| "you need an icon.png in …/en-US/images" | F-Droid reads the store icon from the app's fastlane directory at the built commit | `tools/icon_design.py` now renders `fastlane/metadata/android/en-US/images/icon.png` (512 × 512) |
| "app is not ready, see Policy Quality point 6" | three months of active development, unless the app has users, the author maintains other apps on F-Droid, or it was written for an event | none of the exceptions apply; the first commit is **2026-11-07**, so re-submission is **2027-02-07** at the earliest |

The pipeline lesson came before the review and cost two runs: comments in the recipe failed
`fdroid rewritemeta`, which in turn cancelled `fdroid build`. The fixed recipe built on their buildserver,
which answered the three questions the image definition had raised (JDK 21 present, `gradle-9.8.2` in
their transparency log, `compileSdk 36` fetched by AGP) — see `docs/06-milestones.md` item 53.

## Where the merge request stands

Nothing is open. The branch `com.balancecalories` on the fork
`git@gitlab.com:Ne_Che_Guevara/fdroiddata.git` holds one commit that adds `metadata/com.balancecalories.yml`
and nothing else; its pipeline is green; the request itself is closed and is not reopened before the policy
condition is met.

Until February the phones are updated from **the household's own F-Droid repository on the home LAN** —
`http://KATANA:8080/repo`, `versionName 1.0.2` / `versionCode 4`, signed with a key the household owns. That is
[`self-hosted.md`](self-hosted.md); it also changes what this recipe will say at re-submission, because
`commit` must be the full hash of a release that contains the icon, and `v1.0.2` is the first such tag.

Two mechanics are worth keeping for the next round:

- **A push option cannot carry a description.** `git push` refuses a newline in a push option
  (`fatal: push options must not have new line characters`), so `-o merge_request.create` opens the request
  with a title and at most one line of text; the checklist above is pasted in the browser.
- **`commit` must be the full hash**, and the hash has to be the release that contains the icon — fastlane
  metadata is read at the built commit, not from the default branch.

The commands, for the next round:

```powershell
git clone --depth 1 -b master git@gitlab.com:Ne_Che_Guevara/fdroiddata.git
Copy-Item docs\fdroid\metadata\com.balancecalories.yml fdroiddata\metadata\
git -C fdroiddata checkout -b com.balancecalories
git -C fdroiddata add metadata/com.balancecalories.yml
git -C fdroiddata commit -m "New app: BalanceCalories"
git -C fdroiddata push -o merge_request.create -o merge_request.target=master `
    -o merge_request.title="New app: BalanceCalories" origin HEAD
```

## Why the recipe has no comments

fdroiddata's pipeline runs `fdroid rewritemeta` on every changed metadata file and then
`git diff --exit-code`. `rewritemeta` never keeps comments, so a commented recipe fails that job, and the
diff it prints contains nothing but the deleted comment lines — which reads like "no change". The file in
`metadata/` is therefore exactly the canonical output. **P-44** in `docs/07-pitfalls.md` has the failure as
it happened.

## Before pressing submit

- [x] Repository is public: <https://github.com/shogun-kub/balance-calories> (`private: false`, Apache-2.0).
- [x] Tag `v1.0.1` exists and points at `a2201e0`, the commit whose release APK was exercised on both phones;
      the GitHub API lists it, which is what the buildserver clones.
- [x] `LICENSE` is Apache-2.0, the canonical text.
- [x] Metadata passes `fdroid rewritemeta` (schema) and the category `Sports & Health` is checked against
      fdroiddata's own `config/categories.yml`.
- [x] `fastlane/metadata/android/{en-US,ru-RU}/` holds title, short and full description, a changelog per
      version, five screenshots per language, and now the 512 × 512 `icon.png`.
- [x] Author name and website in the metadata: `AuthorName: Ne_Che_Guevara`, `WebSite` the repository — the
      owner's decision.
- [x] Fork of `fdroiddata` exists: <https://gitlab.com/Ne_Che_Guevara/fdroiddata>.
- [ ] **Three months of development**: 2027-02-07. Everything else on this list can be true before that date;
      this one cannot be hurried.
- [ ] At re-submission, the recipe's `commit` becomes the full hash of the **then-current** release tag, which
      is the first tag that contains `icon.png`.
