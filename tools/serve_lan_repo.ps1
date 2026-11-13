# Serve the household's F-Droid repository on the LAN.
#
# The phones add `http://KATANA:8080/repo` as an F-Droid repository and update by button while this runs. That is
# the whole point: GitHub refused every upload above about a megabyte from this network (P-45), and a file server
# on the home LAN has no such ceiling — the 12 MB APK serves in under a second.
#
#   python tools/serve_lan_repo.ps1      # or: .\tools\serve_lan_repo.ps1
#
# Stop it with Ctrl+C. Nothing here is a service: when it is not running, the client simply reports that the
# repository is unreachable, and the app on the phone is unaffected.
#
# The address uses the machine name, `KATANA`, not its IP: the router resolves it (verified from the Honor,
# 2026-11-13) and it survives a DHCP lease change. If the name ever stops resolving, `http://<ip>:8080/repo` works
# the same way; the repository's own metadata then needs `repo_url` in config.yml updated to match.

param(
    [int]$Port = 8080,
    [string]$DataDir = 'E:\Projects\BalanceCalories-fdroid'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path (Join-Path $DataDir 'repo\index-v1.jar'))) {
    throw "No repository index in '$DataDir\repo' - build it first: fdroid update --clean --pretty"
}

$listening = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($listening) {
    Write-Host "Port $Port is already in use (PID $($listening[0].OwningProcess)) - probably this server already."
    exit 0
}

$python = 'C:\Users\shogu\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
if (-not (Test-Path $python)) {
    $python = (Get-Command python -ErrorAction SilentlyContinue).Source
    if (-not $python) { throw 'No Python interpreter found to serve with.' }
}

Write-Host "Serving $DataDir on http://$env:COMPUTERNAME`:$Port/repo"
Write-Host 'Add that address (with ?fingerprint=... from docs/fdroid/self-hosted.md) in the F-Droid client.'
& $python -m http.server $Port --bind 0.0.0.0 --directory $DataDir
