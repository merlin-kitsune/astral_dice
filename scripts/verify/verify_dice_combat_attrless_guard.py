#!/usr/bin/env python3
"""P0 dual-line gate: a mob without ATTACK_DAMAGE must not crash the server.

Incident (2026-10-29, player-reported, whole server went down):

    java.lang.IllegalArgumentException: Can't find attribute minecraft:attack_damage
      at AttributeSupplier.getAttributeInstance / LivingEntity.getAttributeValue
      at DiceCombatEvents.enchantedMobAttack(DiceCombatEvents.java:1225)
      at DiceCombatEvents.resolveMobMeleeAttack(...)
      at DiceCombatEvents.onLivingDamagePre(...)
      at ServerPlayer.hurt <- twilightforest HydraHeadContainer.executeAttacks(:690)

Twilight's Hydra has no `attack_damage` attribute at all (its damage is a
scripted fixed value handed straight to `player.hurt(...)`), so any attribute-less
mob meleeing a dice-carrying player crashed the server.  `AttributeSupplier`
:18-25 throws `IllegalArgumentException("Can't find attribute " + key)` from
`getAttributeInstance`, and 1.20.1 and 1.21.1 share that code.

Required shape on BOTH lines (`neoforge-1.21.1` and `forge-1.20.1`)
=================================================================
C1 (never crash)      read the attribute through `getAttribute(...)` (nullable)
                      and never reach `getAttributeValue(...)` for a mob.
C2 (keep the mechanism) `resolveMobMeleeAttack` detects "attrless" and uses this
                      hit's vanilla damage as the attack-power proxy; the
                      player-side `magicAbsorbFactor(...)` re-multiplication is
                      skipped **iff** the proxy already contains the mitigation.
                      Resistance/protection must land exactly once: never zero
                      (that would delete the mitigation for such mobs) and never
                      twice (that would double-nerf them).

Why the two lines are not mirror images
======================================
Each line hooks a different point of the vanilla pipeline, so "this hit's
vanilla damage" is a different quantity on each side (verified from the shipped
`*-sources.jar` artifacts, not from memory):

* neoforge-1.21.1 -- `LivingEntity#hurt` builds
  `new DamageContainer(source, amount)` from the **raw `hurt()` argument**
  (:1152); armor (:1787) and resistance+protection (:1788) only rewrite
  `newDamage`; `LivingDamageEvent.Pre` is dispatched afterwards (:1789).
  => `event.getOriginalDamage()` is PRE-mitigation.  The proxy is therefore an
  attack-side value and `magicAbsorbFactor` must still be applied (once).
  Using `getNewDamage()` here would count armor twice (also inside
  `playerDefense`), the structural defect forbidden by the 2026-09-26 ruling.
* forge-1.20.1 -- `ForgeHooks#onLivingDamage` is called from `actuallyHurt`
  :1680, i.e. AFTER armor (:1667) -> resistance+protection (:1668) ->
  absorption (:1669-1670), and the class comment says "armor, potion and
  absorption modifiers have already been applied ... this is FINAL value".
  => `event.getAmount()` is POST-mitigation, so re-multiplying would mitigate
  twice => skip.  1.20.1 exposes no pre-mitigation value at that hook, so
  "fold back to the attack side" is not an available option there.
* Also structural: forge has no `enchantedMobAttack` helper (the attribute read
  is inline in `resolveMobMeleeAttack`) and its method returns `void`, so there
  is no `return 0.0;` to write.  Its C1 equivalent is a null-derived flag whose
  guarded branch is the only place the instance is dereferenced.  Assertion 04
  accepts exactly that form and reports which one it found.

Assertions (semantic / regex anchored -- never hard line numbers)
==============================================================
 01 neo    no unguarded `mob.getAttributeValue(Attributes.ATTACK_DAMAGE)` in the file
 02 forge  same
 03 neo    `enchantedMobAttack` reads the instance and early-returns on null
 04 forge  the inline equivalent: null-derived flag + instance only dereferenced
           on the guarded (non-attrless) branch
 05 neo    `resolveMobMeleeAttack` has an `attrless` branch fed by the PRE-mitigation
           vanilla damage (`event.getOriginalDamage()`)
 06 forge  ... fed by the POST-mitigation vanilla damage (`event.getAmount()`)
 07 neo    pairing: pre-mitigation proxy => `magicAbsorbFactor` stays UNCONDITIONAL
 08 forge  pairing: post-mitigation proxy => `magicAbsorbFactor` skipped when attrless
 09 neo    `magicAbsorbFactor` still present in the mob melee path
 10 forge  same (deleting it would silently disable mitigation for every mob)
 11 both   worktree EOL: zero CR bytes (this repo's index and worktree are both
           LF, so a CRLF worktree file -- or any stray CR -- is EOL drift and
           FAILS the gate; it is not a warning)

Exit code 0 when every assertion holds, 1 otherwise.

Usage:
    python scripts/verify/verify_dice_combat_attrless_guard.py [--root <repo-root>]

`--root` runs the same assertions against another tree, e.g. a
`git show HEAD:<file>` snapshot, so the gate can be proven to FAIL on the old
code without touching the working tree.

Standard library only. Comments are stripped before matching, so prose can never
satisfy an assertion.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

REL_NEO = "neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java"
REL_FORGE = "forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java"

# --- anchors -----------------------------------------------------------------
ATTR = "Attributes.ATTACK_DAMAGE"
# The crashing form: an unguarded read that goes through AttributeSupplier.
CRASH_READ = "mob.getAttributeValue(" + ATTR + ")"
# The safe form: fetch the instance (nullable) instead.
SAFE_READ = "mob.getAttribute(" + ATTR + ")"
MAGIC_FACTOR = "magicAbsorbFactor"

FN_ENCHANTED = "private static double enchantedMobAttack("
FN_RESOLVE = "private static void resolveMobMeleeAttack("

# `attrless ? (double) event.getXxx()` -- the proxy selection (whitespace-insensitive).
NEO_PROXY_RE = re.compile(r"attrless\s*\?\s*\(\s*double\s*\)\s*event\.getOriginalDamage\s*\(\s*\)")
NEO_PROXY_POST_RE = re.compile(r"attrless\s*\?\s*\(\s*double\s*\)\s*event\.getNewDamage\s*\(\s*\)")
FORGE_PROXY_RE = re.compile(r"attrless\s*\?\s*\(\s*double\s*\)\s*event\.getAmount\s*\(\s*\)")
# Null early-return: `if (x == null) { return 0.0; }` (body may span lines).
NULL_GUARD_RE = re.compile(r"if\s*\(\s*\w+\s*==\s*null\s*\)\s*\{?\s*return\s+0\.0")
# Instance assignment: `AttributeInstance x = mob.getAttribute(Attributes.ATTACK_DAMAGE);`
INST_RE = re.compile(
    r"(\w+)\s*=\s*mob\.getAttribute\(\s*Attributes\.ATTACK_DAMAGE\s*\)"
)
# Null-derived flag: `boolean attrless = <inst> == null;`
FLAG_RE_TMPL = r"boolean\s+(\w+)\s*=\s*{inst}\s*==\s*null"
# Factor skipped when attrless: `if (!attrless) { ... magicAbsorbFactor`
SKIP_GUARD_RE = re.compile(r"if\s*\(\s*!\s*attrless\s*\)\s*\{[^{}]*?" + MAGIC_FACTOR, re.S)


def strip_comments(text: str) -> str:
    """Blank out // and /* */ comments, preserving offsets and newlines.

    String literals are kept verbatim (machine rows such as `AP_CURSE_APPLY`
    live inside one).
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


def hits(lines: list[str], needle: str) -> list[int]:
    """1-based line numbers where needle appears."""
    return [i for i, line in enumerate(lines, start=1) if needle in line]


def method_span(lines: list[str], signature: str) -> tuple[int, int] | None:
    """(first line, last line) of the method whose declaration contains signature.

    Brace matched, so the span covers the whole body no matter how the
    declaration is wrapped.
    """
    start = None
    for i, line in enumerate(lines, start=1):
        if signature in line:
            start = i
            break
    if start is None:
        return None
    depth = 0
    opened = False
    for i in range(start, len(lines) + 1):
        for ch in lines[i - 1]:
            if ch == "{":
                depth += 1
                opened = True
            elif ch == "}":
                depth -= 1
                if opened and depth == 0:
                    return start, i
    return None


def guarded_deref(body: str, inst: str, flag: str) -> tuple[bool, str]:
    """Every `<inst>.` dereference sits in a statement that mentions `flag`.

    The ternary form keeps the dereference in the false branch of
    `flag ? proxy : <inst>.getValue() + ...`, so the whole statement carries the
    flag.  A bare `if (!flag) { ... }` block would need the enclosing header and
    is therefore reported as unguarded -- rewrite to the ternary if that ever
    becomes the chosen shape.
    """
    deref = inst + "."
    found: list[int] = []
    bad: list[int] = []
    for m in re.finditer(re.escape(deref), body):
        pos = m.start()
        found.append(pos)
        start = body.rfind(";", 0, pos) + 1
        end = body.find(";", pos)
        stmt = body[start : end if end != -1 else len(body)]
        # The flag's own declaration (`boolean f = x == null;`) is not a deref
        # target; anything else must carry the flag.
        if flag not in stmt and not re.search(r"boolean\s+" + flag + r"\b", stmt):
            bad.append(pos)
    if not found:
        return False, f"no `{deref}` found"
    if bad:
        return False, f"{len(bad)} of {len(found)} `{deref}` reads are unguarded by `{flag}`"
    return True, f"all {len(found)} `{deref}` reads sit in a `{flag}`-guarded statement"


class Line:
    """One loader line: path, raw text, comment-stripped code text."""

    def __init__(self, tag: str, path: Path):
        self.tag = tag
        self.path = path
        self.exists = path.is_file()
        if not self.exists:
            self.raw = ""
            self.code = ""
            self.lines: list[str] = []
            self.cr = 0
            self.bytes_len = 0
            return
        raw_bytes = path.read_bytes()
        self.bytes_len = len(raw_bytes)
        self.cr = raw_bytes.count(b"\r")
        self.raw = raw_bytes.decode("utf-8")
        self.code = strip_comments(self.raw)
        self.lines = self.code.splitlines()

    def span(self, signature: str) -> tuple[int, int] | None:
        return method_span(self.lines, signature) if self.exists else None

    def body(self, signature: str) -> str:
        span = self.span(signature)
        if span is None:
            return ""
        return "\n".join(self.lines[span[0] - 1 : span[1]])

    def lines_in(self, span: tuple[int, int] | None, needle: str) -> list[int]:
        if span is None:
            return []
        return [i for i in range(span[0], span[1] + 1) if needle in self.lines[i - 1]]

    def first_line_in(self, span: tuple[int, int] | None, needle: str) -> int | None:
        found = self.lines_in(span, needle)
        return found[0] if found else None

    def line_of(self, span: tuple[int, int] | None, regex: re.Pattern[str]) -> int | None:
        """First line inside span matching a multi-line regex (matched on the body)."""
        if span is None:
            return None
        body = "\n".join(self.lines[span[0] - 1 : span[1]])
        m = regex.search(body)
        if not m:
            return None
        return span[0] + body[: m.start()].count("\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Dual-line attrless (no ATTACK_DAMAGE) mob-melee guard assertions"
    )
    parser.add_argument(
        "--root",
        default=str(REPO_ROOT),
        help="repo root to inspect (default: two levels above this script)",
    )
    args = parser.parse_args(argv)
    root = Path(args.root)

    neo = Line("neo", root / REL_NEO)
    forge = Line("forge", root / REL_FORGE)

    for line in (neo, forge):
        if line.exists:
            print(f"[target] {line.tag}: {line.path}  ({line.bytes_len} bytes)")
            print(
                f"[info]   {line.tag}: CR bytes = {line.cr} "
                f"(LF-only baseline -- asserted hard as the EOL gate below; a CRLF "
                f"worktree file is EOL drift, never a warning)"
            )
        else:
            print(f"[target] {line.tag}: {line.path}  [FILE NOT FOUND]")
    print()

    results: list[tuple[str, bool, str]] = []

    def add(name: str, ok: bool, detail: str) -> None:
        results.append((name, ok, detail))

    # ---- C1: no read that can reach AttributeSupplier#getAttributeInstance ---
    for line in (neo, forge):
        if not line.exists:
            add(f"{line.tag}: no unguarded `{CRASH_READ}`", False, f"{line.path}: file not found")
            continue
        crash_hits = hits(line.lines, CRASH_READ)
        safe_hits = hits(line.lines, SAFE_READ)
        ok = not crash_hits and bool(safe_hits)
        if crash_hits:
            detail = (
                f"{line.path}:{crash_hits} still reads through getAttributeValue "
                f"(crash path); safe `{SAFE_READ}` @ {safe_hits or 'MISSING'}"
            )
        elif not safe_hits:
            detail = f"{line.path}: `{SAFE_READ}` MISSING (attribute is never read? investigate)"
        else:
            detail = (
                f"{line.path}: no `{CRASH_READ}`; null-checked `{SAFE_READ}` @ {safe_hits}"
            )
        add(f"{line.tag}: no unguarded `{CRASH_READ}`", ok, detail)

    # ---- C1 (neo): null early-return inside the helper ----------------------
    neo_span = neo.span(FN_ENCHANTED)
    neo_body = neo.body(FN_ENCHANTED)
    if not neo.exists:
        add("neo: enchantedMobAttack early-returns on a null attribute instance", False,
            f"{neo.path}: file not found")
    elif neo_span is None:
        add("neo: enchantedMobAttack early-returns on a null attribute instance", False,
            f"{neo.path}: `{FN_ENCHANTED}` not found")
    else:
        guard_line = neo.line_of(neo_span, NULL_GUARD_RE)
        inst_line = neo.first_line_in(neo_span, SAFE_READ)
        value_line = neo.first_line_in(neo_span, "mobAttackAttr.getValue()")
        ok = bool(NULL_GUARD_RE.search(neo_body)) and inst_line is not None
        add(
            "neo: enchantedMobAttack early-returns on a null attribute instance",
            ok,
            f"{neo.path}: enchantedMobAttack @ L{neo_span[0]}-{neo_span[1]}; "
            f"`{SAFE_READ}` @ {'L' + str(inst_line) if inst_line else 'MISSING'}; "
            f"null early-return @ {'L' + str(guard_line) if guard_line else 'MISSING'}; "
            f"`AttributeInstance#getValue` @ {'L' + str(value_line) if value_line else 'MISSING'}",
        )

    # ---- C1 (forge): inline equivalent, because forge has no helper --------
    forge_span = forge.span(FN_RESOLVE)
    forge_body = forge.body(FN_RESOLVE)
    if not forge.exists:
        add("forge: resolveMobMeleeAttack null-guards the inline attribute read", False,
            f"{forge.path}: file not found")
    elif forge_span is None:
        add("forge: resolveMobMeleeAttack null-guards the inline attribute read", False,
            f"{forge.path}: `{FN_RESOLVE}` not found")
    else:
        inst_m = INST_RE.search(forge_body)
        guard_ok = False
        detail_bits: list[str] = []
        if not inst_m:
            detail_bits.append(f"`{SAFE_READ}` MISSING")
        else:
            inst = inst_m.group(1)
            flag_m = re.search(FLAG_RE_TMPL.format(inst=re.escape(inst)), forge_body)
            inst_line = forge.first_line_in(forge_span, SAFE_READ)
            detail_bits.append(f"`{SAFE_READ}` @ {'L' + str(inst_line) if inst_line else 'MISSING'}")
            if not flag_m:
                detail_bits.append(f"null-derived flag `boolean <flag> = {inst} == null` MISSING")
            else:
                flag = flag_m.group(1)
                deref_ok, deref_detail = guarded_deref(forge_body, inst, flag)
                flag_line = forge.first_line_in(forge_span, f"boolean {flag} =")
                guard_ok = deref_ok
                detail_bits.append(
                    f"`boolean {flag} = {inst} == null` @ "
                    f"{'L' + str(flag_line) if flag_line else 'MISSING'}; {deref_detail}"
                )
                detail_bits.append(
                    "no `return 0.0` possible here (void settle method; the guarded "
                    "ternary branch is the inline equivalent of the early return)"
                )
        add(
            "forge: resolveMobMeleeAttack null-guards the inline attribute read",
            guard_ok,
            f"{forge.path}: resolveMobMeleeAttack @ L{forge_span[0]}-{forge_span[1]}; "
            + "; ".join(detail_bits),
        )

    # ---- C2: attrless branch fed by this hit's vanilla damage ---------------
    def proxy_assert(line: Line, regex: re.Pattern[str], accessor: str, wording: str) -> None:
        span = line.span(FN_RESOLVE)
        name = f"{line.tag}: attrless branch uses this hit's {wording} damage as proxy"
        if not line.exists:
            add(name, False, f"{line.path}: file not found")
            return
        if span is None:
            add(name, False, f"{line.path}: `{FN_RESOLVE}` not found")
            return
        body = line.body(FN_RESOLVE)
        flag_line = line.first_line_in(span, "boolean attrless")
        accessor_line = line.first_line_in(span, accessor)
        proxy_line = line.line_of(span, regex)
        ok = flag_line is not None and accessor_line is not None and proxy_line is not None
        add(
            name,
            ok,
            f"{line.path}: `boolean attrless` @ {'L' + str(flag_line) if flag_line else 'MISSING'}; "
            f"`{accessor}` @ {'L' + str(accessor_line) if accessor_line else 'MISSING'}; "
            f"`attrless ? (double) {accessor}` @ {'L' + str(proxy_line) if proxy_line else 'MISSING'}"
            + ("" if MAGIC_FACTOR in body else f"; WARNING `{MAGIC_FACTOR}` absent from this body"),
        )

    proxy_assert(neo, NEO_PROXY_RE, "event.getOriginalDamage()", "PRE-mitigation")
    proxy_assert(forge, FORGE_PROXY_RE, "event.getAmount()", "POST-mitigation")

    # ---- C2: mitigation exactly once, paired with the proxy口径 ------------
    def pairing_assert(
        line: Line,
        pre_regex: re.Pattern[str],
        post_regex: re.Pattern[str],
        require_skip: bool,
    ) -> None:
        span = line.span(FN_RESOLVE)
        if not line.exists:
            add(f"{line.tag}: mitigation applied exactly once", False, f"{line.path}: file not found")
            return
        if span is None:
            add(f"{line.tag}: mitigation applied exactly once", False,
                f"{line.path}: `{FN_RESOLVE}` not found")
            return
        body = line.body(FN_RESOLVE)
        factor_line = line.first_line_in(span, MAGIC_FACTOR)
        guard_line = line.first_line_in(span, "!attrless")
        skipped = bool(SKIP_GUARD_RE.search(body))
        post = bool(post_regex.search(body))
        pre = bool(pre_regex.search(body))
        if not factor_line:
            ok, which = False, f"`{MAGIC_FACTOR}` MISSING (mitigation deleted => not once, but never)"
        elif post:
            ok = skipped
            which = (
                "POST-mitigation proxy => must SKIP"
                + (" (skipped)" if skipped else " (NOT skipped => double mitigation)")
            )
        elif pre:
            ok = not skipped
            which = (
                "PRE-mitigation proxy => must KEEP"
                + (" (kept)" if not skipped else " (skipped => mitigation never lands)")
            )
        else:
            ok, which = False, "attrless proxy accessor not recognised"
        if require_skip and not skipped:
            ok = False
        expectation = "skip" if post else "keep" if pre else "unpaired"
        add(
            f"{line.tag}: mitigation applied exactly once ({expectation} expected)",
            ok,
            f"{line.path}: {which}; `{MAGIC_FACTOR}` @ "
            f"{'L' + str(factor_line) if factor_line else 'MISSING'}; "
            f"`if (!attrless)` @ {'L' + str(guard_line) if guard_line else 'none'}",
        )

    pairing_assert(neo, NEO_PROXY_RE, NEO_PROXY_POST_RE, require_skip=False)
    pairing_assert(forge, NEO_PROXY_RE, FORGE_PROXY_RE, require_skip=True)

    # ---- false-green guard: the factor must still exist in both mob paths --
    for line in (neo, forge):
        span = line.span(FN_RESOLVE)
        in_body = bool(span) and MAGIC_FACTOR in line.body(FN_RESOLVE)
        add(
            f"{line.tag}: magicAbsorbFactor still applied in the mob melee path",
            in_body,
            f"{line.path}:{line.lines_in(span, MAGIC_FACTOR) or 'MISSING'}",
        )

    # ---- EOL gate (assertion 11): repo baseline is LF, so no CR at all ------
    for line in (neo, forge):
        add(
            f"{line.tag}: worktree file is LF-only (zero CR bytes) [EOL gate]",
            line.exists and line.cr == 0,
            f"{line.path}: CR bytes = {line.cr} (expected 0; a CRLF worktree file or "
            f"a stray CR is EOL drift)",
        )

    all_ok = all(ok for _, ok, _ in results)
    for name, ok, detail in results:
        print(f"[{'OK' if ok else 'FAIL'}] {name}")
        print(f"       {detail}")
    print()
    if not all_ok:
        print("[FAIL] missing invariants:")
        for name, ok, _ in results:
            if not ok:
                print(f"       - {name}")
        print()
    print("RESULT: " + ("PASS" if all_ok else "FAIL"))
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
