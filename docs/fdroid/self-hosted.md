# The household's own F-Droid repository

F-Droid's main index is closed to this app until **2027-02-07**: the inclusion policy's Quality point 6 wants
three months of active development, and the first commit is 2026-11-07 (diary item 54). The phones still need
updates, and an F-Droid client can read **any** repository that serves a signed index — so the household
serves its own, **on the home LAN**, and the main index is a February problem.

The LAN is not the first idea. The first was GitHub Pages, and it died on our own uplink: nothing above about a
megabyte leaves this network (**P-45**). Throttling let a 5 MB file through but not the 12 MB APK, and since a
git object cannot be pushed in pieces, a file server at home became the honest answer. It has no upload ceiling
at all — the phone pulled the whole 12 192 154-byte APK from it in one request (verified 2026-11-13).

This file is the record of that repository: where it lives, how it is rebuilt, and what has to be done by hand.

## What exists (2026-11-13)

| Thing | Where | Notes |
| --- | --- | --- |
| data directory | `E:\Projects\BalanceCalories-fdroid\` | outside the repository, ACL-restricted to the owner: it holds a private key |
| repository key | `repo-signing-key.jks`, alias `balancecalories-repo` | signs **the index** only; certificate SHA-256 `395E3435…C438BC` |
| app key | `E:\Projects\BalanceCalories-secrets\balancecalories-release.jks`, alias `balancecalories` | signs **the APK**; certificate SHA-256 `2B:3B:02:71…2A:CF:F7`, RSA 4096, valid until 2056 |
| Gradle's view of it | `keystore.properties` at the repository root | git-ignored; an absolute path, so any checkout works |
| published branch | none: the repository is served straight from `repo/` by `tools/serve_lan_repo.ps1` | `gh-pages` on GitHub exists from the abandoned attempt and is stale |
| client address | `http://KATANA:8080/repo?fingerprint=395E3435199912D5FB024A914BD21D92CDF2BA499687A2E27254CDD048C438BC` | `KATANA` is this machine's name; the router resolves it from the phone (verified 2026-11-13), and it survives a DHCP lease change, which the IP would not |
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

## Serving it

Nothing is published anywhere: the data directory **is** the repository, and one small script serves it.

```powershell
.\tools\serve_lan_repo.ps1          # Python's http.server on 0.0.0.0:8080, serving the data directory
```

While that window is open, the phones see `http://KATANA:8080/repo`. When it is closed, the F-Droid client
simply says the repository is unreachable and the app on the phone is unaffected — this is a file server, not a
service, and it deliberately has no autostart.

Three things were checked before believing it (2026-11-13): the index and the whole 12 192 154-byte APK came
back `200` from the Honor using the phone's own `curl`; `http://KATANA:8080/repo` resolves from the phone
while `katana.local` does not; and Windows Firewall already allows Python on the private profile, so no rule had
to be added. A fetch from the host itself needs `-NoProxy`, because the agent's proxy answers `501` for a LAN
address — that is the proxy, not the server.

If the machine name ever stops resolving, use its IP instead (`Get-NetIPAddress`), but then update `repo_url` in
`config.yml` and rebuild the index, because the address is part of what the index says about itself.

## The first install on each phone

**Done on 2026-11-13 by the owner**: both builds were signed with the debug key, so 1.0.2 could not replace
them — the signature check is the point of the exercise. The record of what that cost, kept because the next
signing change will cost the same:

1. In the app: **Settings → Резервная копия → Экспорт** (both profiles, `Test` and the real one), and copy the
   file off the phone.
2. `adb uninstall com.balancecalories` — only after the export has been verified on the host.
3. Install 1.0.2: `adb install -r app\build\outputs\apk\release\app-release.apk`, or the copy already pushed to
   `/sdcard/Download/` and tapped on the phone.
4. Restore the export from step 1.
5. *(Optional, not done.)* Install the F-Droid client, add `http://KATANA:8080/repo?fingerprint=…` while the
   server runs, and from then on updates arrive by button — at home, with the server window open.

The uninstall was the one step in the whole plan that touched real data, so it was done with the owner present
and never as a side effect of something else (hard rule 10).

**Where that leaves the update channel:** no client is installed, so the repository is served but unread, and a
new build is installed by hand (`adb install -r app\build\outputs\apk\release\app-release.apk`, which now works
in place because the signature is ours). The repository exists for the day one tap is preferable; adding it
later costs nothing and changes no data.

## What the client shows

The index carries both locales from fastlane: `en-US` and `ru-RU`, with the app's own summary, the full
description, five screenshots per language, the changelog for versionCode 4, and the 512 px icon. The
`AntiFeatures` list is empty, and the `signer` field is the app key's certificate, which is what the client
verifies before it installs anything.

## Limits worth knowing

- **Updates need the house.** The repository exists only while `tools/serve_lan_repo.ps1` runs on this machine
  and the phone is on the home Wi-Fi. That is the price of not having an upload channel, and it is a small one
  for two phones that live here.
- **HTTP, not HTTPS.** The client warns that the connection is unencrypted; on the LAN that is acceptable
  because the index is signed and every APK is checked against the hash and the signer in that index — a
  tampered file is rejected, not installed.
- **`gh-pages` on GitHub is a leftover.** It holds the index from the abandoned attempt, without the APK and
  pointing at a URL that no longer exists. It can be deleted; nothing reads it.
- **This repository is not a review.** It carries exactly what the household built, signed by the household;
  the F-Droid build in February is the one that carries F-Droid's signature, and the two can never update
  each other in place.
