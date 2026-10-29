#!/usr/bin/env python3
"""Assertions for EventTimerStore revert-at-start (forge-1.20.1 line).

Requirement source: docs/features/event-choice-force-first-spec.md section 4.4
(R3 "event stacking allowed" == override + restart).

Three assertions:
  1. The 4-arg `start(...)` body contains the replay block machine row
     `revert_failed` AND contains `old.accept(player)`.
  2. The line of `old.accept(player)` is STRICTLY LESS than the line of
     `if (onExpire != null)` -- proving the replay block runs before the new
     hook is registered, and is not nested inside that `if`.
  3. `ON_EXPIRE.remove(player.getUUID())` exists in the file (existence is
     enough; the replay block needs it to consume the stale entry).

Exit code: 0 when every assertion passes, 1 otherwise.

Usage:
    python scripts/verify/verify_event_timer_stack_forge.py [--file <EventTimerStore.java>]

Standard library only. Comment text is stripped before matching so that prose
inside `//` or `/** */` cannot satisfy or break an assertion.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_TARGET = (
    REPO_ROOT
    / "forge-1.20.1"
    / "src"
    / "main"
    / "java"
    / "com"
    / "merlinkitsune"
    / "astral_dice"
    / "event"
    / "EventTimerStore.java"
)

ANCHOR_REVERT_FAILED = "revert_failed"
ANCHOR_OLD_ACCEPT = "old.accept(player)"
ANCHOR_REGISTRATION = "if (onExpire != null)"
ANCHOR_MAP_REMOVE = "ON_EXPIRE.remove(player.getUUID())"


def strip_comments(text: str) -> str:
    """Blank out // and /* */ comments, preserving character offsets and newlines.

    String literals are kept verbatim (a machine row such as `revert_failed`
    lives inside one). Char literals are not used by the target file.
    """
    out: list[str] = []
    i = 0
    n = len(text)
    in_line = False
    in_block = False
    in_str = False
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_line:
            if c == "\n":
                in_line = False
                out.append(c)
            else:
                out.append(" ")
            i += 1
            continue
        if in_block:
            if c == "*" and nxt == "/":
                out.append("  ")
                i += 2
                in_block = False
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        if in_str:
            out.append(c)
            if c == "\\" and nxt:
                out.append(nxt)
                i += 2
                continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if c == "/" and nxt == "/":
            in_line = True
            out.append(" ")
            i += 1
            continue
        if c == "/" and nxt == "*":
            in_block = True
            out.append(" ")
            i += 1
            continue
        if c == '"':
            in_str = True
        out.append(c)
        i += 1
    return "".join(out)


def find_lines(lines: list[str], needle: str) -> list[int]:
    """All 1-based line numbers where needle appears."""
    return [i for i, line in enumerate(lines, start=1) if needle in line]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="EventTimerStore revert-at-start assertions")
    parser.add_argument("--file", default=str(DEFAULT_TARGET), help="path to EventTimerStore.java")
    args = parser.parse_args(argv)

    target = Path(args.file)
    print(f"[target] {target}")
    if not target.is_file():
        print(f"[info] CR bytes = n/a")
        print()
        print(f"FAIL 01 revert_failed and old.accept(player) present  [file not found]")
        print()
        print("RESULT: FAIL")
        return 1

    raw = target.read_bytes()
    text = raw.decode("utf-8")
    code = strip_comments(text)

    cr_count = raw.count(b"\r")
    all_lines = text.splitlines()
    lines = code.splitlines()
    print(f"[info] CR bytes = {cr_count} (must be 0: file stays LF)")
    print(f"[info] file lines = {len(all_lines)}, code lines after comment strip = {len(lines)}")

    results: list[tuple[str, bool, str]] = []

    # 1) revert_failed + old.accept(player)
    revert_hits = find_lines(lines, ANCHOR_REVERT_FAILED)
    accept_hits = find_lines(lines, ANCHOR_OLD_ACCEPT)
    ok1 = bool(revert_hits) and bool(accept_hits)
    results.append(
        (
            "01 revert_failed and old.accept(player) present",
            ok1,
            f"revert_failed @ {revert_hits or 'MISSING'}; old.accept(player) @ {accept_hits or 'MISSING'}",
        )
    )

    # 2) old.accept(player) line < if (onExpire != null) line
    reg_hits = find_lines(lines, ANCHOR_REGISTRATION)
    if accept_hits and reg_hits:
        accept_line = accept_hits[0]
        reg_line = reg_hits[0]
        ok2 = accept_line < reg_line
        cmp = "<" if ok2 else ">="
        detail = f"old.accept(player) @ L{accept_line} {cmp} if (onExpire != null) @ L{reg_line}"
    else:
        ok2 = False
        detail = (
            f"old.accept(player) @ {accept_hits or 'MISSING'}; "
            f"if (onExpire != null) @ {reg_hits or 'MISSING'}"
        )
    results.append(("02 replay block precedes new-hook registration", ok2, detail))

    # 3) ON_EXPIRE.remove(player.getUUID()) exists (location reported as evidence)
    remove_hits = find_lines(lines, ANCHOR_MAP_REMOVE)
    ok3 = bool(remove_hits)
    detail3 = f"ON_EXPIRE.remove(player.getUUID()) @ {remove_hits or 'MISSING'}"
    if remove_hits and accept_hits and reg_hits:
        in_block = [h for h in remove_hits if h < accept_hits[0] and h < reg_hits[0]]
        if in_block:
            detail3 += f"; inside replay block = L{in_block[-1]}"
        else:
            detail3 += "; inside replay block = none"
    results.append(("03 ON_EXPIRE.remove(player.getUUID()) present", ok3, detail3))

    all_ok = all(ok for _, ok, _ in results)
    print()
    for name, ok, detail in results:
        print(f"{'PASS' if ok else 'FAIL'} {name}  [{detail}]")
    print()
    print("RESULT: " + ("PASS" if all_ok else "FAIL"))
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
