# The household's own F-Droid repository

F-Droid's main index is closed to this app until **2027-02-07**: the inclusion policy's Quality point 6 wants
three months of active development, and the first commit is 2026-11-07 (diary item 54). The phones still need
updates, and an F-Droid client can read **any** repository that serves a signed index — so the household
serves its own, and the main index is a February problem.

This file is the record of that repository: where it lives, how it is rebuilt, and what has to be done by hand.

## What exists (2026-11-13)

| Thing | Where | Notes |
| --- | --- | --- |
| data directory | `E:\Projects\BalanceCalories-fdroid\` | outside the repository, ACL-restricted to the owner: it holds a private key |
| repository key | `repo-signing-key.jks`, alias `balancecalories-repo` | signs **the index** only; certificate SHA-256 `395E3435…C438BC` |
| app key | `E:\Projects\BalanceCalories-secrets\balancecalories-release.jks`, alias `balancecalories` | signs **the APK**; certificate SHA-256 `2B:3B:02:71…2A:CF:F7`, RSA 4096, valid until 2056 |
| Gradle's view of it | `keystore.properties` at the repository root | git-ignored; an absolute path, so any checkout works |
| published branch | `gh-pages` of <https://github.com/shogun-kub/balance-calories> | the repository itself is the `repo/` directory there |
| client address | `https://shogun-kub.github.io/balance-calories/repo?fingerprint=395E3435199912D5FB024A914BD21D92CDF2BA499687A2E27254CDD048C438BC` | |
| first release | `com.balancecalories` **1.0.2** / versionCode 4 | 12 192 154 bytes, sha256 `21e705f1227bacaa8f31cc7b9e53c5b94226687a2c56d39d4ee4bcc2b65877a1` |

**Two keys, on purpose.** The repository key signs the index, the app key signs the APK. A leaked index key
lets somebody publish a *false catalogue* to our own client; a leaked app key lets somebody sign an *update to
the app itself*. They are separate files so that the first accident cannot become the second.

**Losing them.** The app key is the identity of the app on both phones: lose it and no future build can be
installed over the current one — only uninstall, losing the log, then reinstall. The repository key is
cheaper: a new one means re-adding the repository in the client. Both files and both passwords are in the two
directories above; the passwords are in `keystore.properties` (app) and `config.yml` (repository).

## Rebuilding the index

`fdroid update` reads the APKs in `repo/`, the metadata in `metadata/`, and the names, descriptions,
changelogs and screenshots from `build/<package>/fastlane/` — which is why that directory exists here: it is
a copy of the app repository's `fastlane/`, not a build.

```powershell
$py     = 'C:\Users\shogu\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
$fdroid = 'C:\Users\shogu\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\Scripts\fdroid.exe'
$env:PYTHONUTF8 = '1'          # without this, signindex decodes the JSON with cp1251 and dies on «ё»

& $py E:\Projects\BalanceCalories\tools\patch_fdroidserver.py   # after any fdroidserver upgrade
Set-Location E:\Projects\BalanceCalories-fdroid
& $fdroid update --clean --pretty
```

Two Windows accommodations, both recorded in `docs/08-environment.md` and in **P-45**: the patch normalises
path separators that `fdroidserver` splits on `'/'` literally, and `PYTHONUTF8` stops Python from using the
console code page for a UTF-8 JSON file. `apksigner` is configured as the `.bat` in `build-tools`, because
`fdroidserver` looks for a file without an extension and would otherwise fall back to `jarsigner`, which cannot
verify an APK signed with v2 only.

`fdroid update` also warns that `config.yml` has unsafe permissions and that the repository icon needs a
placeholder: on Windows `stat().st_mode` is fiction, and the icon is picked up from `icon.png` at the data
directory root.

## Publishing

The published content is the data directory's `repo/` directory, mirrored into the `pages` checkout, plus a
landing page that states the address and the fingerprint:

```powershell
robocopy E:\Projects\BalanceCalories-fdroid\repo E:\Projects\BalanceCalories-fdroid\pages\repo /MIR /NFL /NDL /NJH /NJS /NP
git -C E:\Projects\BalanceCalories-fdroid\pages add -A
git -C E:\Projects\BalanceCalories-fdroid\pages commit -m "BalanceCalories <versionName> (versionCode <n>)"
git -C E:\Projects\BalanceCalories-fdroid\pages push
```

**The push has to be run by the owner, from his own terminal** (P-45): the agent's network path cuts any
upload larger than about a megabyte, so the 12 MB APK cannot leave this session. Everything up to the push —
the index, the assets, the commit — is prepared here; the last command is his.

GitHub Pages then serves the branch. Enabling it is also his: **Settings → Pages → Source: branch `gh-pages`,
folder `/ (root)`**, after which <https://shogun-kub.github.io/balance-calories/> answers.

## The first install on each phone

The phones hold builds signed with the **debug** key, so 1.0.2 cannot replace them — the signature check is the
point of the exercise. One uninstall per phone, and the data has to leave and come back:

1. In the app: **Settings → Резервная копия → Экспорт** (both profiles, `Test` and the real one), and copy the
   file off the phone.
2. `adb uninstall com.balancecalories` — only after the export has been verified on the host.
3. Install 1.0.2, then restore the export from step 1.
4. Add the repository in the F-Droid client and let it install or confirm the build once Pages is live.

This is the one step in the whole self-hosted plan that touches real data, so it is done with the owner
present and never as a side effect of something else (hard rule 10).

## What the client shows

The index carries both locales from fastlane: `en-US` and `ru-RU`, with the app's own summary, the full
description, five screenshots per language, the changelog for versionCode 4, and the 512 px icon. The
`AntiFeatures` list is empty, and the `signer` field is the app key's certificate, which is what the client
verifies before it installs anything.

## Limits worth knowing

- **Every release adds its APK to the branch**, roughly 12 MB each. The branch grows; old APKs are never
  rewritten, which is correct for a repository but means a shallow clone is the sane way to look at it.
- **GitHub Pages is availability, not a promise.** If it stops serving, the repository is still versioned in
  git and can be re-published elsewhere; the client address would change and the repository would have to be
  added again.
- **This repository is not a review.** It carries exactly what the household built, signed by the household;
  the F-Droid build in February is the one that carries F-Droid's signature, and the two can never update
  each other in place.
