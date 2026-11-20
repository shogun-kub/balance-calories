# 09 · Releases: branches, tags, and what may be published

The rules below were set by the owner on **2026-11-20**. They exist because the first three versions were
handed out by `adb install -r` with nothing in the repository saying which commit a phone was running: `1.0.3`
and `1.0.4` reached the Honor that way and were never tagged at all. From now on the *tag* is the record.

## The four rules

1. **Two long-lived branches.** `dev` gets everything that is being worked on; `main` holds only trees that were
   released. Nothing else is long-lived.
2. **A pre-release tag is `v<X.Y.Z>p<N>`** — the literal `v`, the version of the **next** release, the literal
   `p`, and the build number: `v1.0.6p1`, `v1.0.6p2`. It is created on `dev` **by an explicit command** and
   nothing else publishes anything: a push to `dev` is work, not a release.
3. **`main` never receives a commit without a release tag `v<X.Y.Z>`.** The version must already exist as at
   least one pre-release — `v1.0.6` only after `v1.0.6p1`. There is no release without a pre-release.
4. **A version that has been released cannot be pre-released again.** After `v1.0.6`, the next pre-release is
   `v1.0.7p1` (or a minor/major bump): at least one of major, minor, patch moves. A pre-release of an already
   released version is refused, not renamed.

## What "publish" means in this project

There is no app store: a build reaches the household through [`docs/fdroid/self-hosted.md`](fdroid/self-hosted.md)
(the LAN repository) or `adb install -r` on a phone, and nothing above about a megabyte leaves this machine
(**P-45**). So *publishing* is: build the release APK from the tagged commit, put it on the phone(s), and push
the tag. The tag is what makes the build identifiable later — hard rule 5 asks for a version and a commit, and
this is where the commit comes from.

## The commands

Pre-release, when the owner says so:

```powershell
git switch dev
# versionName is the target release (1.0.6), versionCode goes up so a phone can upgrade over the last one
git commit -am "1.0.6 pre-release 1"
git tag -a v1.0.6p1 -m "1.0.6 pre-release 1"
git push origin dev v1.0.6p1
```

The release, when the owner says so — and only then:

```powershell
git switch main
git merge --ff-only dev
git tag -a v1.0.6 -m "1.0.6"
git push origin main v1.0.6
python tools\check_release.py        # exit 0, or the rules do not hold
```

`--ff-only` is deliberate: `main` only ever moves forward onto the tree that was tested, so a release cannot
carry a merge commit whose contents nobody installed.

## The checker

`tools/check_release.py` reads the tags and branches of this clone and refuses the states the four rules
forbid:

- a tag that is neither `vX.Y.Z` nor `vX.Y.ZpN` (a typo'd tag is a lost record);
- a release tag with no pre-release of the same version;
- a pre-release of a version that was already released, or a pre-release number used twice;
- a pre-release that is not reachable from `dev`, or a release that is not reachable from `main`.

Run it before pushing a tag (`python tools\check_release.py`) and with `--tag v1.0.6p1` before creating one.

## Where this starts, and what is grandfathered

| Tag | When | Why it is exempt |
| --- | --- | --- |
| `v1.0.0`, `v1.0.1`, `v1.0.2` | 2026-11-13 | predate the rules; no pre-release was ever made |
| `v1.0.5p1`, `v1.0.5` | 2026-11-20, same commit | `1.0.5` was built, installed and tested on the Honor *before* the rules existed; the pair records what happened rather than changing it |
| `1.0.3`, `1.0.4` | — | never tagged; they were ad-hoc installs, and the device record is the `versionName`/`versionCode` on the phone |

The next cycle is the first one run under the rules: `1.0.6` needs `v1.0.6p1` on `dev` before `v1.0.6` may
land on `main`. The version numbers live in `app/build.gradle.kts` (`versionName`, `versionCode`); a pre-release
keeps the release's `versionName` and only the tag says which build it was.

Where the branches stand today (2026-11-20): `main` is the `1.0.5` tree (`a610bcc`, tags `v1.0.5p1`/`v1.0.5`);
`dev` carries that plus the release rules, the profile-list and empty-state fixes, and `1.0.6` / `versionCode 8`,
tagged **`v1.0.6p1`** — the first pre-release cut under these rules. `v1.0.6` and the merge to `main` wait for the
owner. Checkout `dev` to work; checkout `main` only to look at what was released.
