"""Check this clone's tags against the release rules in `docs/09-releases.md`.

    python tools/check_release.py                 # every tag in the repository
    python tools/check_release.py --tag v1.0.6p1  # one tag, before creating it

Exit 0 means the rules hold. Exit 1 prints what does not, and nothing else in the process cares — this is a
check, not a gate: a tag pushed by mistake is still pushed, so run it *before* the push.

The rules it enforces, and why each one is here:

1. a tag is `vX.Y.Z` (release) or `vX.Y.ZpN` (pre-release), digits only, no leading zeros;
2. every release has at least one pre-release of the same version — no release without a pre-release;
3. a pre-release may not be created after the release of its version — the version is spent, move it;
4. a pre-release is reachable from `dev`, a release from `main`, so the tag says where the tree came from.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys

RELEASE = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
PRERELEASE = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)p([1-9]\d*)$")

# Tags made before 2026-11-20, when the rules were written. They are reported and not judged: rewriting history
# to make them pass would be a lie about what was installed on the phones.
GRANDFATHERED = {"v1.0.0", "v1.0.1", "v1.0.2"}


def git(*args: str) -> str:
    result = subprocess.run(["git", *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"git {' '.join(args)} failed")
    return result.stdout


def tags() -> list[tuple[str, int]]:
    """(name, creation time) for every tag, oldest first."""
    out = git("for-each-ref", "--format=%(refname:short)\t%(creatordate:unix)", "refs/tags")
    found = []
    for line in out.splitlines():
        name, _, when = line.partition("\t")
        found.append((name, int(when or 0)))
    return sorted(found, key=lambda item: (item[1], item[0]))


def branch_exists(name: str) -> bool:
    result = subprocess.run(
        ["git", "rev-parse", "--verify", "--quiet", f"refs/heads/{name}"],
        capture_output=True,
        text=True,
    )
    return result.returncode == 0


def reachable(tag: str, branch: str) -> bool:
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", tag, branch],
        capture_output=True,
        text=True,
    )
    return result.returncode == 0


def parse(name: str) -> tuple[str, str] | None:
    """('release' | 'prerelease', 'X.Y.Z') or None when the name is not a version tag at all."""
    if RELEASE.match(name):
        return "release", name[1:]
    match = PRERELEASE.match(name)
    if match:
        return "prerelease", f"{match.group(1)}.{match.group(2)}.{match.group(3)}"
    return None


def check_one(name: str, known: list[tuple[str, int]]) -> str | None:
    """The verdict for a tag about to be created, given the tags that already exist."""
    parsed = parse(name)
    if parsed is None:
        return f"{name}: not a version tag (expected vX.Y.Z or vX.Y.ZpN)"
    kind, version = parsed
    released = [tag for tag, _ in known if parse(tag) == ("release", version)]
    pre = [tag for tag, _ in known if parse(tag) == ("prerelease", version)]
    if kind == "release":
        if not pre:
            return f"{name}: no pre-release of {version} exists — release it only after v{version}p1"
        return None
    if released:
        return f"{name}: {version} was already released as {released[0]} - bump a version number first"
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--tag", default=None, help="check one tag as if it were created now")
    args = parser.parse_args()

    if not branch_exists("main") or not branch_exists("dev"):
        print("both branches must exist: dev for work, main for releases (docs/09-releases.md)", file=sys.stderr)
        return 1

    known = tags()
    if args.tag:
        verdict = check_one(args.tag, known)
        print(f"{args.tag}: {'ok' if verdict is None else verdict}")
        return 0 if verdict is None else 1

    problems: list[str] = []
    print(f"{'tag':<16} {'kind':<11} {'version':<8} {'created':<11} {'branch':<6}")
    for name, when in known:
        parsed = parse(name)
        if parsed is None:
            problems.append(f"{name}: not a version tag (expected vX.Y.Z or vX.Y.ZpN)")
            print(f"{name:<16} {'?':<11} {'':<8} {'':<11} {'':<6}")
            continue
        kind, version = parsed
        wanted = "dev" if kind == "prerelease" else "main"
        placed = reachable(name, wanted)
        if not placed and name not in GRANDFATHERED:
            problems.append(f"{name}: not reachable from {wanted} — a {kind} belongs on {wanted}")
        stamp = __import__("datetime").datetime.fromtimestamp(when).strftime("%Y-%m-%d")
        print(f"{name:<16} {kind:<11} {version:<8} {stamp:<11} {wanted if placed else wanted + '?':<6}")

    # Release tags need a pre-release, and a pre-release may not follow its own release.
    created = dict(known)
    for name, when in known:
        parsed = parse(name)
        if parsed is None or name in GRANDFATHERED:
            continue
        kind, version = parsed
        if kind == "release":
            before = [tag for tag, at in known if parse(tag) == ("prerelease", version) and at <= when]
            if not before:
                problems.append(f"{name}: released without a pre-release of {version}")
        else:
            after = [tag for tag, at in known if parse(tag) == ("release", version) and at < when]
            if after:
                problems.append(f"{name}: {version} was released as {after[0]} before this pre-release")

    if GRANDFATHERED & created.keys():
        print(f"\nbefore the rules, not judged: {', '.join(sorted(GRANDFATHERED & created.keys()))}")

    if problems:
        print("\nproblems:")
        for line in problems:
            print(f"  - {line}")
        return 1
    print("\nOK: tags and branches agree with docs/09-releases.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
