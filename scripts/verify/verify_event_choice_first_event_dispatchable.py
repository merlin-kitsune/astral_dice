#!/usr/bin/env python3
"""Assertions for the R1 first-event invariant (neoforge-1.21.1 line).
本文件与其 `_forge` 孪生文件必须逐条同步，任何一侧新增断言都要镜像。

Requirement source: 2026-10-29 独立审查 P1/P2
(「大侦探立牌选择界面被任何方式退出时，强制触发界面上第一个事件」).

The invariant this script keeps checkable:

    CANDIDATE SET  subset-of  DISPATCHABLE SET

i.e. the event-choice screen must never offer an event that the dispatcher is
guaranteed to reject for that same player / trigger source, and the forced
settlement path must re-check with the very same predicate instead of
re-implementing (or silently skipping) it.

Eleven groups of assertions, all static (source text, comments stripped):

  01 the single predicate `InvestigationArbitration.canDispatchSecretOnly`
     exists and delegates to `teamHasSecretDetective`.
  02 `pickHighestTier` guards on that predicate BEFORE it builds the detective
     list -- so the dispatcher's `AP_EVENT_NO_DETECTIVE` early-return condition
     (an empty `picked`) is literally the same predicate the pools filter on.
  03 `AstralEventRegistry.dispatchableFor` = `event.allows(source)` AND the
     secret-only predicate (both early-return causes of the dispatcher covered).
  04 the candidate-pool builder `publicPool(Player, AstralEventTrigger)` filters
     with `dispatchableFor`, and the 1-arg `publicPool(Player)` uses the shared
     predicate too (the old, independently spelled `teamHasSecretDetective`
     filter is gone from it).
  05 `EventChoiceManager.start(...)` builds its candidates with the source-aware
     pool (`ACTIVE_SKILL`), not the 1-arg one.
  06 `EventChoiceManager.forceFirst(...)` re-checks with the same predicate via
     `firstDispatchable` -> `isDispatchable` -> `dispatchableFor`, and the
     dispatch line uses that re-checked id.
  07 P2 ordering: `instanceof ServerPlayer` precedes `SESSIONS.remove(...)` in
     `forceFirst`, and `session.resolving = 0;` still follows the removal.
  08 the machine rows are byte-identical where they must be (`AP_EVENT_CHOICE:
     force:...`, `AP_EVENT_NO_DETECTIVE: ...`), plus the new fallback row.
  09 both event constructors still declare `ACTIVE_SKILL` in their trigger set,
     so the source-aware pool cannot degenerate into an empty candidate set.
  10 every edited java file is LF-only (CR count 0).
  11 (present on BOTH lines -- this assertion is mirrored in the sibling script,
     so any change here MUST be copied to the other side) the dispatcher
     registers the event timer (`EventTimerStore.start(...)`, the onExpire = null
     form) on a line that is STRICTLY BEFORE the line applying the effect
     (`event.effect().apply(...)`). This is the only precondition that makes
     the replay block in `EventTimerStore.start(...)` safe: the old cycle's
     expiry consumer unwinds the PREVIOUS state while the new state has not
     been written yet. Without this assertion, a swap of those two dispatcher
     lines still passes 01-10 (assertion 02 only checks the replay sits before
     `if (onExpire != null)`).

Exit code: 0 when every assertion passes, 1 otherwise.

Usage:
    python scripts/verify/verify_event_choice_first_event_dispatchable.py
    python scripts/verify/verify_event_choice_first_event_dispatchable.py --root <mod root>

Standard library only. Comment text is stripped before matching so that prose
inside `//` or `/** */` (including the P1/P2 repair notes) cannot satisfy or
break an assertion.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
EVENT_PKG = Path("src") / "main" / "java" / "com" / "merlinkitsune" / "astral_dice" / "event"

DEFAULT_ROOT = REPO_ROOT / "neoforge-1.21.1"


# ────────────────────────── source helpers ──────────────────────────
def strip_comments(text: str) -> str:
    """Blank out // and /* */ comments, preserving character offsets and newlines.

    String literals are kept verbatim (machine rows such as `AP_EVENT_NO_DETECTIVE`
    live inside them). Char literals are not used by the target files.
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


class Doc:
    """A java source file, comment-stripped, with line-numbered lookups."""

    def __init__(self, path: Path) -> None:
        self.path = path
        self.raw = path.read_bytes()
        self.text = self.raw.decode("utf-8")
        self.code = strip_comments(self.text)
        self.lines = self.code.splitlines()
        self.raw_lines = self.text.splitlines()

    @property
    def cr(self) -> int:
        return self.raw.count(b"\r")

    def find(self, needle: str) -> list[int]:
        """All 1-based line numbers where needle appears (comments stripped)."""
        return [i for i, line in enumerate(self.lines, start=1) if needle in line]

    def first(self, needle: str) -> int | None:
        hits = self.find(needle)
        return hits[0] if hits else None

    def body(self, signature: str) -> tuple[int, int, str] | None:
        """Brace-matched body of the method whose declaration contains `signature`.

        Returns (first_body_line, last_body_line, body_text) with 1-based line
        numbers, or None when the signature is absent / unbalanced.
        """
        start = self.first(signature)
        if start is None:
            return None
        depth = 0
        opened = False
        body_lines: list[str] = []
        for idx in range(start, len(self.lines) + 1):
            line = self.lines[idx - 1]
            for ch in line:
                if ch == "{":
                    depth += 1
                    if not opened:
                        opened = True
                        continue
                elif ch == "}":
                    depth -= 1
                    if opened and depth == 0:
                        return (start, idx, "\n".join(body_lines))
            if opened:
                body_lines.append(line)
            if depth == 0 and opened:
                return (start, idx, "\n".join(body_lines))
        return None

    def region(self, begin: str, end: str) -> tuple[int, str] | None:
        """Text between the line holding `begin` and the next line holding `end`."""
        start = self.first(begin)
        if start is None:
            return None
        for idx in range(start, len(self.lines) + 1):
            if end in self.lines[idx - 1]:
                return (start, "\n".join(self.lines[start - 1 : idx]))
        return None


# ────────────────────────── assertions ──────────────────────────
def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="R1 invariant: candidate set subset-of dispatchable set (neoforge-1.21.1)"
    )
    parser.add_argument("--root", default=str(DEFAULT_ROOT), help="mod line root (contains src/)")
    args = parser.parse_args(argv)

    root = Path(args.root)
    pkg = root / EVENT_PKG
    targets = {
        "arb": pkg / "InvestigationArbitration.java",
        "reg": pkg / "AstralEventRegistry.java",
        "mgr": pkg / "EventChoiceManager.java",
        "disp": pkg / "AstralEventDispatcher.java",
    }
    for label, path in targets.items():
        print(f"[target:{label}] {path}")
    missing = [str(p) for p in targets.values() if not p.is_file()]
    if missing:
        print()
        print("FAIL 00 target files present  [missing: " + ", ".join(missing) + "]")
        print()
        print("RESULT: FAIL")
        return 1

    docs = {label: Doc(path) for label, path in targets.items()}
    for label, doc in docs.items():
        print(f"[info:{label}] CR bytes = {doc.cr}, lines = {len(doc.lines)}")
    print()

    results: list[tuple[str, bool, str]] = []

    def check(name: str, ok: bool, detail: str) -> None:
        results.append((name, ok, detail))

    arb, reg, mgr, disp = docs["arb"], docs["reg"], docs["mgr"], docs["disp"]

    # ── 01 single predicate ─────────────────────────────────────────
    pred = arb.body("public static boolean canDispatchSecretOnly(Player triggerer)")
    if pred:
        p_start, p_end, p_body = pred
        ok = "teamHasSecretDetective(triggerer)" in p_body and "return" in p_body
        detail = f"canDispatchSecretOnly @ L{p_start}-L{p_end} -> teamHasSecretDetective(triggerer)"
        if not ok:
            detail += "; body missing delegation"
    else:
        ok = False
        detail = "canDispatchSecretOnly(...) MISSING in InvestigationArbitration"
    check("01 shared predicate canDispatchSecretOnly delegates to teamHasSecretDetective", ok, detail)

    # ── 02 dispatcher guard lives inside pickHighestTier, before the list ──
    pick = arb.body("public static Optional<Player> pickHighestTier(Player triggerer)")
    if pick:
        start, end, body = pick
        body_lines = body.splitlines()
        guard_off = next((i for i, l in enumerate(body_lines) if "canDispatchSecretOnly(triggerer)" in l), None)
        list_off = next((i for i, l in enumerate(body_lines) if "teamDetectives(triggerer)" in l), None)
        if guard_off is not None and list_off is not None:
            ok2 = guard_off < list_off
            detail2 = (
                f"pickHighestTier @ L{start}-L{end}: guard canDispatchSecretOnly @ L{start + guard_off} "
                f"{'<' if ok2 else '>='} teamDetectives list @ L{start + list_off}"
            )
        else:
            ok2 = False
            detail2 = (
                f"pickHighestTier @ L{start}-L{end}: guard {'@ ' + str(start + guard_off) if guard_off is not None else 'MISSING'}; "
                f"teamDetectives {'@ ' + str(start + list_off) if list_off is not None else 'MISSING'}"
            )
    else:
        ok2 = False
        detail2 = "pickHighestTier(...) MISSING"
    check("02 dispatch guard uses the shared predicate before building the detective list", ok2, detail2)

    # ── 03 dispatchableFor covers allows(source) + secret-only ─────
    dfor = reg.body("public static boolean dispatchableFor(Player triggerer, AstralEvent event, AstralEventTrigger source)")
    if dfor:
        d_start, d_end, d_body = dfor
        has_allows = "event.allows(source)" in d_body
        has_pred = "canDispatchSecretOnly(triggerer)" in d_body
        ok3 = has_allows and has_pred
        detail3 = (
            f"dispatchableFor @ L{d_start}-L{d_end}: allows(source)={'yes' if has_allows else 'MISSING'}, "
            f"canDispatchSecretOnly={'yes' if has_pred else 'MISSING'}"
        )
    else:
        ok3 = False
        detail3 = "AstralEventRegistry.dispatchableFor(...) MISSING"
    check("03 dispatchableFor == allows(source) && secret-only predicate", ok3, detail3)

    # ── 04 candidate pool filters through the shared judgement ─────
    src_pool = reg.body("public static List<AstralEvent> publicPool(Player triggerer, AstralEventTrigger source)")
    plain_pool = reg.body("public static List<AstralEvent> publicPool(Player triggerer)")
    ok4a = bool(src_pool) and "dispatchableFor(triggerer, e, source)" in src_pool[2]
    if ok4a:
        detail4a = f"publicPool(Player, source) @ L{src_pool[0]}-L{src_pool[1]} filters with dispatchableFor"
    else:
        detail4a = "publicPool(Player, AstralEventTrigger) MISSING or does not filter with dispatchableFor"
    ok4b = bool(plain_pool) and "canDispatchSecretOnly(triggerer)" in plain_pool[2]
    if plain_pool:
        stale = "teamHasSecretDetective(triggerer)" in plain_pool[2]
        ok4b = ok4b and not stale
        detail4b = (
            f"publicPool(Player) @ L{plain_pool[0]}-L{plain_pool[1]}: canDispatchSecretOnly="
            f"{'yes' if 'canDispatchSecretOnly(triggerer)' in plain_pool[2] else 'MISSING'}"
            f", stale teamHasSecretDetective={'PRESENT' if stale else 'none'}"
        )
    else:
        detail4b = "publicPool(Player) MISSING"
    check("04 candidate pool built from the shared judgement", ok4a and ok4b, detail4a + " | " + detail4b)

    # ── 05 choice screen builds candidates with the source-aware pool ──
    start_body = mgr.body("static boolean start(ServerPlayer")
    if start_body:
        s_start, s_end, s_body = start_body
        src_hits = [i for i, l in enumerate(s_body.splitlines(), start=s_start) if "publicPool(player, AstralEventTrigger.ACTIVE_SKILL)" in l]
        one_arg = [i for i, l in enumerate(s_body.splitlines(), start=s_start) if "AstralEventRegistry.publicPool(player)" in l]
        ok5 = bool(src_hits) and not one_arg
        detail5 = (
            f"start() @ L{s_start}-L{s_end}: publicPool(player, ACTIVE_SKILL) @ {src_hits or 'MISSING'}; "
            f"1-arg publicPool(player) @ {one_arg or 'none'}"
        )
    else:
        ok5 = False
        detail5 = "EventChoiceManager.start(ServerPlayer, ...) MISSING"
    check("05 candidate construction uses publicPool(player, ACTIVE_SKILL)", ok5, detail5)

    # ── 06 forceFirst re-checks through the same predicate ─────────
    ff = mgr.body("private static void forceFirst(Player player, Session session, String reason)")
    fd = mgr.body("private static ResourceLocation firstDispatchable(")
    isd = mgr.body("private static boolean isDispatchable(")
    ok6 = True
    bits: list[str] = []
    if ff:
        f_start, f_end = ff[0], ff[1]
        f_body = ff[2]
        has_call = "firstDispatchable(serverPlayer, session)" in f_body
        trigger_line = next(
            (
                f_start + i
                for i, l in enumerate(f_body.splitlines())
                if "AstralEventDispatcher.trigger(serverPlayer, forced, AstralEventTrigger.ACTIVE_SKILL)" in l
            ),
            None,
        )
        ok6 = ok6 and has_call and trigger_line is not None
        bits.append(f"forceFirst @ L{f_start}-L{f_end}: firstDispatchable={'yes' if has_call else 'MISSING'}, dispatch(forced) @ {trigger_line or 'MISSING'}")
    else:
        ok6 = False
        bits.append("forceFirst(...) MISSING")
    if fd:
        ok6 = ok6 and "isDispatchable(player, session.firstEvent)" in fd[2] and "isDispatchable(player, session.secondEvent)" in fd[2]
        bits.append(f"firstDispatchable @ L{fd[0]}-L{fd[1]}: first/second re-checked")
    else:
        ok6 = False
        bits.append("firstDispatchable(...) MISSING")
    if isd:
        ok6 = ok6 and "dispatchableFor(player, e, AstralEventTrigger.ACTIVE_SKILL)" in isd[2]
        bits.append(f"isDispatchable @ L{isd[0]}-L{isd[1]}: dispatchableFor(ACTIVE_SKILL)")
    else:
        ok6 = False
        bits.append("isDispatchable(...) MISSING")
    check("06 forced settlement re-checks with dispatchableFor (shared source)", ok6, "; ".join(bits))

    # ── 07 P2: type check before session removal ───────────────────
    if ff:
        f_start = ff[0]
        f_lines = ff[2].splitlines()
        type_off = next((i for i, l in enumerate(f_lines) if "instanceof ServerPlayer serverPlayer" in l), None)
        remove_off = next((i for i, l in enumerate(f_lines) if "SESSIONS.remove(player.getUUID(), session)" in l), None)
        resolving_off = next((i for i, l in enumerate(f_lines) if "session.resolving = 0;" in l), None)
        if None not in (type_off, remove_off, resolving_off):
            ok7 = type_off < remove_off < resolving_off
            detail7 = (
                f"forceFirst @ L{f_start}: instanceof @ L{f_start + type_off} "
                f"{'<' if type_off < remove_off else '>='} SESSIONS.remove @ L{f_start + remove_off} "
                f"{'<' if remove_off < resolving_off else '>='} resolving=0 @ L{f_start + resolving_off}"
            )
        else:
            ok7 = False
            detail7 = "forceFirst: one of instanceof / SESSIONS.remove / resolving=0 MISSING"
    else:
        ok7 = False
        detail7 = "forceFirst(...) MISSING"
    check("07 forceFirst: instanceof before SESSIONS.remove, resolving set after", ok7, detail7)

    # ── 08 machine rows keep their exact shape ────────────────────
    rows = {
        "AP_EVENT_CHOICE: force:{}:pick=0:reason={}:event={}": "mgr",
        "AP_EVENT_CHOICE: force_fallback:{}:from={}:to={}": "mgr",
        "AP_EVENT_NO_DETECTIVE: {}:src={}": "disp",
    }
    row_bits: list[str] = []
    ok8 = True
    for literal, where in rows.items():
        hits = docs[where].find(literal)
        ok8 = ok8 and bool(hits)
        row_bits.append(f"{literal.split(':')[1].strip()} @ {where}{hits or 'MISSING'}")
    check("08 AP_EVENT_* machine rows unchanged / present", ok8, "; ".join(row_bits))

    # ── 09 ACTIVE_SKILL still declared by both constructors ───────
    pub = reg.body("private static void publicEvent(ResourceLocation id")
    inv = reg.region("AstralEventIds.INVESTIGATION_STAGE,", "1200))")
    ok9 = bool(pub) and "AstralEventTrigger.ACTIVE_SKILL" in pub[2]
    detail9 = f"publicEvent @ {'L%d-L%d' % (pub[0], pub[1]) if pub else 'MISSING'}: ACTIVE_SKILL={'yes' if ok9 else 'MISSING'}"
    if inv:
        inv_ok = "AstralEventTrigger.ACTIVE_SKILL" in inv[1]
        ok9 = ok9 and inv_ok
        detail9 += f"; investigation_stage @ L{inv[0]}: ACTIVE_SKILL={'yes' if inv_ok else 'MISSING'}"
    else:
        ok9 = False
        detail9 += "; investigation_stage registration MISSING"
    check("09 candidate set cannot degenerate: both constructors allow ACTIVE_SKILL", ok9, detail9)

    # ── 10 edited files are LF-only ───────────────────────────────
    cr_bits = [f"{label}={doc.cr}" for label, doc in docs.items()]
    ok10 = all(doc.cr == 0 for label, doc in docs.items() if label != "disp")
    cr_bits.append("(disp is out of this repair's edit scope)" if docs["disp"].cr else "")
    check("10 edited java files are LF-only (CR=0)", ok10, ", ".join(b for b in cr_bits if b))

    # ── 11 dispatcher registers the timer before applying the effect ──
    # EventTimerStore.start(Player, ResourceLocation, int) -- the onExpire = null form the
    # dispatcher calls -- first replays the PREVIOUS cycle's onExpire consumer, then stores
    # the new one. That replay is harmless ONLY because the dispatcher registers the timer
    # BEFORE applying the effect: the old cycle unwinds while the new state is not written yet.
    # Swap those two dispatcher lines and every re-trigger of a timed event undoes the state
    # it just wrote -- while assertions 01-10 still pass (02 only pins the replay before
    # `if (onExpire != null)` inside EventTimerStore).
    start_hits = disp.find("EventTimerStore.start(")
    apply_hits = disp.find("effect().apply(")
    if start_hits and apply_hits:
        s_line, a_line = start_hits[0], apply_hits[0]
        ok11 = s_line < a_line
        detail11 = (
            f"AstralEventDispatcher: EventTimerStore.start @ L{s_line} "
            f"{'<' if ok11 else '>='} effect().apply @ L{a_line}"
        )
        if len(start_hits) > 1 or len(apply_hits) > 1:
            ok11 = all(h < a_line for h in start_hits) and len(apply_hits) == 1
            detail11 += f"; all start lines @ {start_hits}, all apply lines @ {apply_hits}"
    else:
        ok11 = False
        detail11 = (
            "AstralEventDispatcher: EventTimerStore.start @ "
            f"{start_hits or 'MISSING'}; effect().apply @ {apply_hits or 'MISSING'}"
        )
    check("11 dispatcher registers the timer before applying the effect", ok11, detail11)

    all_ok = all(ok for _, ok, _ in results)
    print()
    for name, ok, detail in results:
        print(f"{'PASS' if ok else 'FAIL'} {name}  [{detail}]")
    print()
    print("RESULT: " + ("PASS" if all_ok else "FAIL"))
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
