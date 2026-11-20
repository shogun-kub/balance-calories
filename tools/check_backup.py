"""Prove that a backup written **today** exists on a phone before anything touches the installation.

Why: on 2026-11-20 a Gradle instrumentation task uninstalled the app on the Honor and took the owner's
database with it; the newest surviving copy was three days old (**P-50**, diary item 60). The rule is now
hard rule 14 in `AGENTS.md`: no `adb uninstall`, no `adb install`/`-r`, no `pm clear`, no restore and no
`connected*AndroidTest` until a backup dated today is on the phone and parses.

    python tools/check_backup.py --serial A7UPBB5802104075

Exit code 0 means one of the `balancecalories-*.json` files on the phone was exported today, has a schema
version this app understands, and carries rows. Anything else means: make one first (Настройки → Данные →
«Сохранить копию (JSON)»), pull it somewhere safe, and only then continue.

The file is health data: it is pulled into a temporary directory, parsed and deleted again. Nothing is left
on this machine by running this check.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

FORMAT_ID = "balancecalories.backup"
# The app writes format 2 since 2026-11-20 (v2 added the meal set); a v1 file still restores, so both are accepted.
FORMAT_VERSION = 2
# Every schema this build can restore. A backup newer than this cannot be read back, so it is not a backup.
MAX_SCHEMA_VERSION = 7
# The tables every backup has carried since format 1. `meal` arrived in format 2 and a v1 file does not have it,
# so it is not required — the app seeds default slots when a file brings none.
REQUIRED_TABLES = (
    "profile",
    "body_measurement",
    "target_history",
    "product",
    "recipe",
    "recipe_item",
    "food_entry",
    "daily_activity",
)


def run(adb: list[str], *args: str) -> str:
    result = subprocess.run([*adb, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"adb failed: {' '.join(args)}")
    return result.stdout


def backup_directories(adb: list[str]) -> list[str]:
    """Internal Download plus each **real** external volume's.

    `/storage/sdcard0`, `sdcard1` and `emulated` are legacy aliases of the same two volumes, and following
    them lists every internal file twice; a removable card's directory is named by its UUID.
    """
    directories = ["/sdcard/Download"]
    try:
        listing = run(adb, "shell", "ls -d /storage/*/Download 2>/dev/null")
    except RuntimeError:
        listing = ""
    for line in listing.split():
        line = line.strip()
        volume = line.split("/")[2] if line.startswith("/storage/") else ""
        if not re.fullmatch(r"[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}", volume):
            continue
        if line not in directories:
            directories.append(line)
    return directories


def candidates(adb: list[str]) -> list[tuple[str, str, int]]:
    """(volume path, file name, size) for every backup-shaped file visible on the phone."""
    found: list[tuple[str, str, int]] = []
    for directory in backup_directories(adb):
        try:
            listing = run(adb, "shell", f"ls -l {directory} 2>/dev/null")
        except RuntimeError:
            continue
        for line in listing.splitlines():
            parts = line.split()
            if len(parts) < 8 or not parts[-1].startswith("balancecalories-"):
                continue
            if not parts[-1].endswith(".json"):
                continue
            try:
                size = int(parts[4])
            except ValueError:
                continue
            found.append((directory, parts[-1], size))
    return found


def inspect(adb: list[str], directory: str, name: str, workdir: str) -> dict | None:
    """Pulls one file and reports what the app's restore would make of it, or None when it is not a backup."""
    remote = f"{directory}/{name}"
    local = os.path.join(workdir, name)
    try:
        run(adb, "pull", remote, local)
        with open(local, encoding="utf-8") as handle:
            document = json.load(handle)
    except (RuntimeError, OSError, ValueError):
        return None
    finally:
        if os.path.exists(local):
            os.remove(local)

    tables = document.get("tables") or {}
    # Count every array the file carries, not a fixed list: a format that grows must not report a smaller number
    # than a person can see in the app (249 rows when the count stopped at the eight base tables).
    rows = sum(len(value) for value in tables.values() if isinstance(value, list))
    exported_at = document.get("exportedAt")
    schema = document.get("schemaVersion")
    valid = (
        document.get("format") == FORMAT_ID
        and isinstance(document.get("formatVersion"), int)
        and 1 <= document["formatVersion"] <= FORMAT_VERSION
        and isinstance(schema, int)
        and 1 <= schema <= MAX_SCHEMA_VERSION
        and all(table in tables for table in REQUIRED_TABLES)
        and rows > 0
    )
    exported = dt.datetime.fromtimestamp(exported_at / 1000.0) if isinstance(exported_at, int) else None
    return {
        "remote": remote,
        "schema": schema,
        "rows": rows,
        "exported": exported,
        "valid": valid,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--adb", default=os.environ.get("ADB", "adb"), help="adb executable (default: adb)")
    parser.add_argument("--serial", default=None, help="device serial; omit when exactly one is attached")
    args = parser.parse_args()
    # A console code page that cannot hold "→" must not turn a failed check into a crash.
    sys.stdout.reconfigure(errors="replace")

    adb = [args.adb] + (["-s", args.serial] if args.serial else [])
    if shutil.which(args.adb) is None and not os.path.exists(args.adb):
        print(f"cannot find adb at {args.adb!r}: pass --adb or set ADB", file=sys.stderr)
        return 2

    try:
        today = run(adb, "shell", "date +%F").strip()
    except RuntimeError as failure:
        print(f"cannot read the phone's date: {failure}", file=sys.stderr)
        return 2

    found = candidates(adb)
    if not found:
        print(f"no balancecalories-*.json on the phone; today is {today} on the device")
        return 1

    workdir = tempfile.mkdtemp(prefix="balancecalories-check-")
    try:
        reports = []
        for directory, name, size in found:
            report = inspect(adb, directory, name, workdir)
            reports.append((size, name, report))
    finally:
        shutil.rmtree(workdir, ignore_errors=True)

    print(f"device date: {today}")
    print(f"{'file':<44} {'size':>9} {'exported':<12} {'schema':>6} {'rows':>6}  verdict")
    fresh = 0
    for size, name, report in sorted(reports, key=lambda item: (item[1], item[0])):
        if report is None:
            print(f"{name:<44} {size:>9} {'':<12} {'':>6} {'':>6}  not a backup")
            continue
        exported = report["exported"].strftime("%Y-%m-%d") if report["exported"] else "?"
        is_today = bool(report["exported"]) and exported == today
        verdict = "TODAY" if is_today and report["valid"] else ("valid but old" if report["valid"] else "unreadable")
        if is_today and report["valid"]:
            fresh += 1
        print(
            f"{name:<44} {size:>9} {exported:<12} {str(report['schema']):>6} {report['rows']:>6}  {verdict}",
        )

    if fresh:
        print(f"\nOK: {fresh} backup(s) from today. Safe to uninstall/install - but pull a copy first.")
        return 0
    print("\nNO backup from today. Make one before touching the installation:")
    print("  Settings - Data - 'Save a copy (JSON)', then pull it off the phone (hard rule 14).")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
