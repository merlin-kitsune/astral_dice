#!/usr/bin/env python3
"""Assertions for the R1 exit-path wiring (BOTH release lines: neoforge + forge).

Requirement source: 2026-10-29 独立复核 "最后一个缺口"
(「大侦探立牌选择界面被任何方式退出时，强制触发界面上第一个事件」).

Why this script exists
----------------------
The four existing gate scripts
(`verify_event_timer_stack.py`, `verify_event_timer_stack_forge.py`,
`verify_event_choice_first_event_dispatchable.py`,
`verify_event_choice_first_event_dispatchable_forge.py`) never read the client
screen file and never assert that the server-side exit path actually CALLS the
forced dispatch. Measured consequence: deleting / commenting the single line
`forceFirst(player, session, reason);` inside
`EventChoiceManager#exitWithoutChoice` keeps all four of them green, i.e. the
whole "exit == force first event" requirement silently disappears while the
gateboard stays green. This script closes that hole.

It checks the two lines in one run:

    neoforge-1.21.1  and  forge-1.20.1

and every assertion is written against *semantics / relative order of matched
lines*, never against hard-coded line numbers -- the two lines differ by a
global offset (forge file layouts are shifted), so absolute numbers would rot.

SERVER SIDE -- `event/EventChoiceManager.java`
  01 `exitWithoutChoice(...)` body CONTAINS a `forceFirst(` call. This is the
     real R1 wiring; removing it must turn the board red.
  02 the old `void cancel(ServerPlayer ...)` method no longer exists (guards
     against the pre-R1 signature coming back under the old name).
  03 inside `forceFirst(...)`: `instanceof ServerPlayer` < `SESSIONS.remove(` <
     `resolving = 0` (the P2 order: a failed type check must not eat the
     session before the removal, otherwise the forced settlement is lost).
  04 `cancelFor(...)` -- the event-card animation path, the explicit exception
     to R1 -- does NOT call `forceFirst(`. Without this, someone "helpfully"
     completing the wiring turns the animation takeover into a second forced
     trigger.

CLIENT SIDE -- `client/EventChoiceScreen.java`
  05 `shouldCloseOnEsc()` returns false (vanilla must not close the screen
     behind our back; the ESC two-stage logic lives in `keyPressed`).
  06 in the ESC branch of `keyPressed`: `confirmed` guard < `escalationWarned`
     test < the first warning action (the `escalationWarned = true` set).
  07 the exit-intent packet is sent from exactly ONE place in the file, and
     that place is inside `requestExit()`; every other send call in the file
     must be the confirm packet (a second exit send anywhere else double-fires
     the forced settlement).
     NOTE (measured, spec correction): the file legitimately has TWO send
     calls -- the confirm click (`mouseReleased`) and the exit intent
     (`requestExit`). The requirement "requestExit() is the file's only send
     point" is therefore literally false for the current source; what is
     actually verifiable and load-bearing is "the EXIT payload is sent from
     exactly one site and it is `requestExit()`". That is what 07 pins.
  08 `keyPressed` carries both branches of the two-stage ESC: already warned =>
     straight to `requestExit()`; not warned yet => set the flag + actionbar
     warning, return (no send). Asserted on the source shape actually present
     in both lines (identical modulo offsets).
  09 `removed()` and `onClose()` both route to `requestExit()`, and `removed()`
     is gated by `closedByServer` (a server-initiated close must not echo an
     exit intent back).

  10 line endings: CR bytes == 0 for every file read here. Per the task's own
     wording this is a *soft* check -- if a file legitimately carries historic
     CRLF it is reported as `WARN <id>` (evidence printed, overall result not
     forced red) instead of being silently swallowed.

Exit code: 0 when every assertion passes, 1 otherwise.

Usage:
    python scripts/verify/verify_event_choice_exit.py
    python scripts/verify/verify_event_choice_exit.py --root <repo root>
    python scripts/verify/verify_event_choice_exit.py --root <temp copy, both lines present>

`--root` is the REPOSITORY root (the directory holding `neoforge-1.21.1/` and
`forge-1.20.1/`), not a single mod line -- this script deliberately reads both.

Standard library only. Comment text is stripped before matching, so prose inside
`//` or `/** */` (including the R1 repair notes that quote `forceFirst`) can
neither satisfy nor break an assertion.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

SRC_PKG = Path("src") / "main" / "java" / "com" / "merlinkitsune" / "astral_dice"
EVENT_PKG = SRC_PKG / "event"
CLIENT_PKG = SRC_PKG / "client"

# Per-line naming map: the only things that legitimately differ between the two
# release lines. Everything else (method names, ordering, guard names) is the
# same shape in both.
LINES: dict[str, dict[str, str]] = {
    "neo": {
        "dir": "neoforge-1.21.1",
        "send": "PacketDistributor.sendToServer",
        "exit_payload": "EventChoiceCancelPayload",
        "confirm_payload": "EventChoiceConfirmPayload",
    },
    "forge": {
        "dir": "forge-1.20.1",
        "send": "ModNetwork.sendToServer",
        "exit_payload": "EventChoiceCancelMessage",
        "confirm_payload": "EventChoiceConfirmMessage",
    },
}


# ────────────────────────── source helpers ──────────────────────────
def strip_comments(text: str) -> str:
    """Blank out // and /* */ comments, preserving character offsets and newlines.

    String literals are kept verbatim (machine rows and lang keys live inside
    them). Char literals are not used by the target files.
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

    def __init__(self, path: Path, line_root: Path) -> None:
        self.path = path
        # Short, unambiguous label for the evidence column: the mod-line tag on
        # each PASS/FAIL row already says which release line this is.
        self.name = path.relative_to(line_root).as_posix()
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

    def find_code(self, needle: str) -> list[int]:
        """Like find(), but ignores `package` / `import` lines.

        A payload class name also occurs on its own import line; that is not a
        send site and must not be counted as one.
        """
        return [
            i
            for i in self.find(needle)
            if not self.lines[i - 1].lstrip().startswith(("import ", "package "))
        ]

    def first(self, needle: str) -> int | None:
        hits = self.find(needle)
        return hits[0] if hits else None

    def line(self, n: int) -> str:
        return self.lines[n - 1] if 1 <= n <= len(self.lines) else ""

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


def hit(doc: Doc, n: int | None) -> str:
    """`path:Lnn` evidence string, tolerant of a miss."""
    return f"{doc.name}:L{n}" if n is not None else f"{doc.name}:MISSING"


def span(doc: Doc, n: int | None) -> str:
    return f"{doc.name}:L{n}" if n is not None else f"{doc.name}:MISSING"


# ────────────────────────── assertions ──────────────────────────
def check_line(
    tag: str,
    cfg: dict[str, str],
    root: Path,
    results: list[tuple[str, bool, str]],
    warnings: list[tuple[str, str]],
) -> None:
    """Run assertions 01-10 for one release line."""

    def check(name: str, ok: bool, detail: str) -> None:
        results.append((f"{tag}-{name}", ok, detail))

    pkg = root / cfg["dir"]
    line_root = pkg / SRC_PKG
    mgr = Doc(pkg / EVENT_PKG / "EventChoiceManager.java", line_root)
    screen = Doc(pkg / CLIENT_PKG / "EventChoiceScreen.java", line_root)

    exit_body = mgr.body("public static void exitWithoutChoice(ServerPlayer player, int token, String reason)")
    force_body = mgr.body("private static void forceFirst(Player player, Session session, String reason)")
    cancel_for_body = mgr.body("public static void cancelFor(ServerPlayer player, String reason)")
    esc_body = screen.body("public boolean shouldCloseOnEsc()")
    key_body = screen.body("public boolean keyPressed(int keyCode, int scanCode, int modifiers)")
    on_close_body = screen.body("public void onClose()")
    removed_body = screen.body("public void removed()")
    request_exit_body = screen.body("private void requestExit()")

    # ── 01 exitWithoutChoice really calls forceFirst(...) ──────────
    if exit_body:
        e_start, e_end, e_text = exit_body
        call_off = next(
            (i for i, l in enumerate(e_text.splitlines(), start=e_start) if "forceFirst(" in l),
            None,
        )
        ok1 = call_off is not None
        detail1 = (
            f"{span(mgr, e_start)}-L{e_end}: forceFirst( call @ {hit(mgr, call_off)}"
            if ok1
            else f"{span(mgr, e_start)}-L{e_end}: forceFirst( MISSING -> exit no longer forces the first event"
        )
    else:
        ok1 = False
        detail1 = "exitWithoutChoice(ServerPlayer, int, String) MISSING"
    check("01 exitWithoutChoice body calls forceFirst(", ok1, detail1)

    # ── 02 old `void cancel(ServerPlayer` signature is gone ────────
    old = mgr.find("void cancel(")
    new_name = mgr.find("void cancelFor(ServerPlayer")
    ok2 = not old
    detail2 = (
        f"void cancel(ServerPlayer @ {[f'L{n}' for n in old] or 'absent'} (expected absent); "
        f"void cancelFor(ServerPlayer @ {[f'L{n}' for n in new_name] or 'MISSING'}"
    )
    check("02 no legacy void cancel(ServerPlayer ...) method", ok2, detail2)

    # ── 03 forceFirst: instanceof < SESSIONS.remove < resolving = 0 ─
    if force_body:
        f_start = force_body[0]
        f_lines = force_body[2].splitlines()
        type_off = next((i for i, l in enumerate(f_lines) if "instanceof ServerPlayer" in l), None)
        remove_off = next((i for i, l in enumerate(f_lines) if "SESSIONS.remove(" in l), None)
        resolving_off = next((i for i, l in enumerate(f_lines) if "resolving = 0" in l), None)
        if None not in (type_off, remove_off, resolving_off):
            ok3 = type_off < remove_off < resolving_off
            detail3 = (
                f"{span(mgr, f_start)}+ (body L{f_start}-L{force_body[1]}): "
                f"instanceof ServerPlayer @ {hit(mgr, f_start + type_off)} "
                f"{'<' if type_off < remove_off else '>='} SESSIONS.remove @ {hit(mgr, f_start + remove_off)} "
                f"{'<' if remove_off < resolving_off else '>='} resolving=0 @ {hit(mgr, f_start + resolving_off)}"
            )
        else:
            ok3 = False
            missing = [
                n
                for n, v in (
                    ("instanceof ServerPlayer", type_off),
                    ("SESSIONS.remove(", remove_off),
                    ("resolving = 0", resolving_off),
                )
                if v is None
            ]
            detail3 = f"forceFirst @ {span(mgr, f_start)}: missing " + ", ".join(missing)
    else:
        ok3 = False
        detail3 = "forceFirst(Player, Session, String) MISSING"
    check("03 forceFirst order: instanceof < SESSIONS.remove < resolving=0", ok3, detail3)

    # ── 04 cancelFor must NOT force (explicit R1 exception) ────────
    if cancel_for_body:
        c_start, c_end, c_text = cancel_for_body
        forced = [i for i, l in enumerate(c_text.splitlines(), start=c_start) if "forceFirst(" in l]
        ok4 = not forced
        detail4 = (
            f"{span(mgr, c_start)}-L{c_end}: forceFirst( @ {[f'L{n}' for n in forced] or 'absent (expected absent)'}"
        )
    else:
        ok4 = False
        detail4 = "cancelFor(ServerPlayer, String) MISSING"
    check("04 cancelFor does NOT call forceFirst( (R1 exception)", ok4, detail4)

    # ── 05 shouldCloseOnEsc() returns false ───────────────────────
    if esc_body:
        s_start, s_end, s_text = esc_body
        false_off = next(
            (i for i, l in enumerate(s_text.splitlines(), start=s_start) if "return false" in l), None
        )
        true_hits = [i for i, l in enumerate(s_text.splitlines(), start=s_start) if "return true" in l]
        ok5 = false_off is not None and not true_hits
        detail5 = (
            f"{span(screen, s_start)}-L{s_end}: return false @ {hit(screen, false_off)}; "
            f"return true @ {[f'L{n}' for n in true_hits] or 'absent'}"
        )
    else:
        ok5 = False
        detail5 = "shouldCloseOnEsc() MISSING"
    check("05 shouldCloseOnEsc() returns false", ok5, detail5)

    # ── 06 ESC branch: confirmed guard < escalationWarned < warning ─
    k_start = key_body[0] if key_body else None
    if key_body:
        k_lines = key_body[2].splitlines()
        confirmed_off = next((i for i, l in enumerate(k_lines) if "if (this.confirmed)" in l), None)
        warn_test_off = next((i for i, l in enumerate(k_lines) if "if (!this.escalationWarned)" in l), None)
        warn_set_off = next((i for i, l in enumerate(k_lines) if "this.escalationWarned = true" in l), None)
        if None not in (confirmed_off, warn_test_off, warn_set_off):
            ok6 = confirmed_off < warn_test_off < warn_set_off
            detail6 = (
                f"keyPressed @ {span(screen, k_start)}: confirmed guard @ {hit(screen, k_start + confirmed_off)} "
                f"{'<' if confirmed_off < warn_test_off else '>='} escalationWarned test @ "
                f"{hit(screen, k_start + warn_test_off)} "
                f"{'<' if warn_test_off < warn_set_off else '>='} warning action (escalationWarned=true) @ "
                f"{hit(screen, k_start + warn_set_off)}"
            )
        else:
            ok6 = False
            detail6 = "keyPressed: confirmed / escalationWarned test / warning set MISSING"
    else:
        ok6 = False
        detail6 = "keyPressed(int, int, int) MISSING"
    check("06 ESC branch: confirmed guard < escalationWarned < warning action", ok6, detail6)

    # ── 07 exit payload sent from exactly ONE site, inside requestExit ──
    exit_hits = screen.find_code(cfg["exit_payload"])
    send_hits = screen.find_code(cfg["send"])
    if request_exit_body:
        r_start, r_end, r_text = request_exit_body
        in_body = [h for h in exit_hits if r_start <= h <= r_end]
        sends = [h for h in exit_hits if cfg["send"] in screen.line(h)]
        exit_sent_off = next(
            (i for i, l in enumerate(r_text.splitlines(), start=r_start) if "exitSent = true" in l), None
        )
        send_off = in_body[0] if in_body else None
        stray = [
            h
            for h in send_hits
            if h not in exit_hits and cfg["confirm_payload"] not in screen.line(h)
        ]
        ok7 = (
            len(exit_hits) == 1
            and len(sends) == 1
            and len(in_body) == 1
            and exit_sent_off is not None
            and send_off is not None
            and exit_sent_off < send_off
            and not stray
        )
        detail7 = (
            f"{cfg['exit_payload']} @ {[f'L{n}' for n in exit_hits] or 'MISSING'} "
            f"(send sites {[f'L{n}' for n in sends]}); requestExit @ {span(screen, r_start)}-L{r_end} "
            f"contains it @ {hit(screen, send_off)}; exitSent=true @ {hit(screen, exit_sent_off)} "
            f"{'<' if (exit_sent_off is not None and send_off is not None and exit_sent_off < send_off) else '>='} send; "
            f"other {cfg['send']} sites @ {[f'L{n}' for n in send_hits] or 'none'}, "
            f"stray (neither confirm nor exit) @ {[f'L{n}' for n in stray] or 'none'}"
        )
    else:
        ok7 = False
        detail7 = "requestExit() MISSING"
    check("07 exit packet sent from one site only, inside requestExit()", ok7, detail7)

    # ── 08 two-stage ESC: warned => requestExit, else warn only ─────
    if key_body:
        k_lines = key_body[2].splitlines()
        warn_test_off = next((i for i, l in enumerate(k_lines) if "if (!this.escalationWarned)" in l), None)
        warn_set_off = next((i for i, l in enumerate(k_lines) if "this.escalationWarned = true" in l), None)
        warn_act_off = next(
            (i for i, l in enumerate(k_lines) if "displayClientMessage(" in l), None
        )
        exit_call_off = next(
            (i for i, l in enumerate(k_lines) if l.strip() == "requestExit();"), None
        )
        # the warning must be an ACTIONBAR message: first statement after
        # displayClientMessage( ... up to `;` must carry the `true` actionbar flag.
        actionbar = False
        if warn_act_off is not None:
            stmt: list[str] = []
            for l in k_lines[warn_act_off:]:
                stmt.append(l)
                if ";" in l:
                    break
            actionbar = "true" in " ".join(stmt)
        # requestExit() must sit in the fall-through AFTER the warn block closes
        prev_is_brace = (
            exit_call_off is not None
            and exit_call_off > 0
            and k_lines[exit_call_off - 1].strip() == "}"
        )
        ok8 = (
            None not in (warn_test_off, warn_set_off, warn_act_off, exit_call_off)
            and warn_test_off < warn_set_off < warn_act_off < exit_call_off
            and actionbar
            and prev_is_brace
        )
        detail8 = (
            f"keyPressed @ {span(screen, k_start)}: not-warned branch (test @ "
            f"{hit(screen, k_start + warn_test_off if warn_test_off is not None else None)}, "
            f"set @ {hit(screen, k_start + warn_set_off if warn_set_off is not None else None)}, "
            f"actionbar warn @ {hit(screen, k_start + warn_act_off if warn_act_off is not None else None)}"
            f"{'' if actionbar else ' [warning arg is NOT actionbar!]'}); "
            f"warned branch => requestExit() @ "
            f"{hit(screen, k_start + exit_call_off if exit_call_off is not None else None)}"
            f"{'' if prev_is_brace else ' [requestExit() is not the fall-through after the warn block]'}"
        )
    else:
        ok8 = False
        detail8 = "keyPressed(int, int, int) MISSING"
    check("08 two-stage ESC: warned => requestExit(), else set flag + actionbar warn", ok8, detail8)

    # ── 09 removed() / onClose() both route to requestExit() ───────
    oc_hits = []
    rm_hits = []
    if on_close_body:
        oc_hits = [
            i
            for i, l in enumerate(on_close_body[2].splitlines(), start=on_close_body[0])
            if "requestExit()" in l
        ]
    rm_gate = []
    if removed_body:
        rm_hits = [
            i
            for i, l in enumerate(removed_body[2].splitlines(), start=removed_body[0])
            if "requestExit()" in l
        ]
        rm_gate = [
            i
            for i, l in enumerate(removed_body[2].splitlines(), start=removed_body[0])
            if "closedByServer" in l
        ]
    ok9 = (
        bool(on_close_body)
        and bool(removed_body)
        and bool(oc_hits)
        and bool(rm_hits)
        and bool(rm_gate)
    )
    detail9 = (
        f"onClose @ {span(screen, on_close_body[0] if on_close_body else None)}: "
        f"requestExit() @ {[f'L{n}' for n in oc_hits] or 'MISSING'}; "
        f"removed @ {span(screen, removed_body[0] if removed_body else None)}: "
        f"requestExit() @ {[f'L{n}' for n in rm_hits] or 'MISSING'}, "
        f"closedByServer gate @ {[f'L{n}' for n in rm_gate] or 'MISSING'}"
    )
    check("09 removed() and onClose() both send exit intent; removed() gated", ok9, detail9)

    # ── 10 line endings (soft: WARN, not a hard FAIL) ──────────────
    cr_bits = f"{mgr.name} CR={mgr.cr}, {screen.name} CR={screen.cr}"
    ok10 = mgr.cr == 0 and screen.cr == 0
    if ok10:
        check("10 relevant files are LF-only (CR=0)", True, cr_bits)
    else:
        warnings.append(
            (
                f"{tag}-10",
                f"relevant files are LF-only (CR=0)  [{cr_bits}] -- reported, NOT counted as FAIL",
            )
        )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="R1 exit-path wiring: exit == force first event (neoforge + forge, one run)"
    )
    parser.add_argument(
        "--root",
        default=str(REPO_ROOT),
        help="repo root holding both neoforge-1.21.1/ and forge-1.20.1/ (default: repo of this script)",
    )
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    print(f"[root] {root}")
    for tag, cfg in LINES.items():
        pkg = root / cfg["dir"]
        print(f"[target:{tag}:mgr]    {pkg / EVENT_PKG / 'EventChoiceManager.java'}")
        print(f"[target:{tag}:screen] {pkg / CLIENT_PKG / 'EventChoiceScreen.java'}")

    missing = [
        str(p)
        for tag, cfg in LINES.items()
        for p in (
            root / cfg["dir"] / EVENT_PKG / "EventChoiceManager.java",
            root / cfg["dir"] / CLIENT_PKG / "EventChoiceScreen.java",
        )
        if not p.is_file()
    ]
    if missing:
        print()
        print("FAIL 00 target files present  [missing: " + ", ".join(missing) + "]")
        print()
        print("RESULT: FAIL")
        return 1

    print()
    results: list[tuple[str, bool, str]] = []
    warnings: list[tuple[str, str]] = []
    for tag, cfg in LINES.items():
        check_line(tag, cfg, root, results, warnings)

    all_ok = all(ok for _, ok, _ in results)
    print()
    for name, ok, detail in results:
        print(f"{'PASS' if ok else 'FAIL'} {name}  [{detail}]")
    for name, detail in warnings:
        print(f"WARN {name}  [{detail}]")
    print()
    print("RESULT: " + ("PASS" if all_ok else "FAIL"))
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
