"""
Apply the two-line Windows fix that `fdroid update` needs, and nothing else.

`fdroidserver` is a Linux tool. It walks the build tree with `os.walk` — which on Windows hands back paths with
backslashes — and then does

    segments = root.split('/')      # and, for a file, f.split('/')

Correct on Linux, wrong here: the list has one element, and the line after it reads `segments[1]` and raises
`IndexError`. fdroidserver 2.4.5, the current release, still has all of it, so there is no upgrade that fixes
this.

The patch normalises the separator before splitting, in every place that does it on a walked path. It touches
the installed site-packages copy, never anything in this repository, and `pip install --upgrade fdroidserver`
reverts it — re-run this script after any upgrade. It is a host tool for publishing our own F-Droid repository;
the app build never runs fdroidserver, so nothing that ships depends on it.

Usage:  python tools/patch_fdroidserver.py [--check]
"""

import sys
from pathlib import Path

# (old, new) — applied everywhere the old text appears; both are the same bug on two different variables.
REPLACEMENTS = [
    ("segments = root.split('/')",
     "segments = root.replace('\\\\', '/').split('/')  # Windows: os.walk returns backslashes"),
    ("segments = f.split('/')",
     "segments = f.replace('\\\\', '/').split('/')  # Windows: os.walk returns backslashes"),
]


def main() -> int:
    check_only = "--check" in sys.argv[1:]
    try:
        import fdroidserver
    except ImportError:
        print("fdroidserver is not installed in this interpreter")
        return 2

    target = Path(fdroidserver.__file__).parent / "update.py"
    text = target.read_text(encoding="utf-8")
    changed = []

    for old, new in REPLACEMENTS:
        if old in text:
            if check_only:
                print(f"would patch {old!r} in {target}")
                changed.append(old)
                continue
            text = text.replace(old, new)
            changed.append(old)

    if not changed:
        print(f"already patched: {target}")
        return 0
    if check_only:
        return 1

    target.write_text(text, encoding="utf-8")
    print(f"patched {len(changed)} line(s) in {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
