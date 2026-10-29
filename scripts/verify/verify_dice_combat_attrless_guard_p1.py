#!/usr/bin/env python3
"""attrless 守卫断言 —— P1 双线 (`neoforge-26.1.2` + `fabric-1.20.1`)。

需求来源: 2026-10-29 rc.6 实测崩溃(服务端 `Description: Ticking entity`)——

    java.lang.IllegalArgumentException: Can't find attribute minecraft:generic.attack_damage
      at LivingEntity.getAttributeValue
      at DiceCombatEvents.enchantedMobAttack(DiceCombatEvents.java:1225)
      at DiceCombatEvents.resolveMobMeleeAttack(DiceCombatEvents.java:1122)
      at ServerPlayer.hurt            <- twilightforest HydraHeadContainer.executeAttacks(:690)

暮色九头蛇的属性表里**没有** `attack_damage`(它的伤害由 HydraHeadContainer 用固定值直接
`player.hurt(...)` 给出),旧代码在 `resolveMobMeleeAttack` 里无条件
`mob.getAttributeValue(Attributes.ATTACK_DAMAGE)` ⇒ `AttributeSupplier#getAttributeInstance` 抛
`IllegalArgumentException("Can't find attribute …")` ⇒ 整服崩。任何「近战打玩家但属性表无
ATTACK_DAMAGE」的生物同理。

用户裁决的修复口径(C1 + C2):
  C1  绝不崩: 先取 `AttributeInstance`,为空则早退(neo: `enchantedMobAttack` 内 `return 0.0;`;
      fabric: 同一读点内联,属性项贡献 0)。
  C2  不破坏伤害机制: 无 ATTACK_DAMAGE 时**用这一击的原版伤害当攻击力代理**,其余公式项
      (playerDefense / mobRoll / playerRoll / victimFactor / DiceBattleResolver.resolve /
      诅咒倍率)不动;若代理值**未**过「护甲 → 抗性 → 保护」三步,则 `VanillaMitigation.
      magicAbsorbFactor` 的重乘**必须保留**(抗性/保护只生效一次)。

本脚本钉的是「语义 / 相对位置」,不钉绝对行号(两线行号本就不齐,且会随下一次改动漂移)。
注释文本在结构断言前被剔除(避免注释里的旧写法骗过断言);唯一需要**读取**注释的是
`04b`(要求代码里留下判据说明),那一处刻意用原文。

逐条断言(每线 01-07,另有全局 08/09):
  01 崩溃读点已消失: 文件里不存在 `mob.getAttributeValue(Attributes.ATTACK_DAMAGE)`。
  02 C1 存在: `AttributeInstance x = mob.getAttribute(Attributes.ATTACK_DAMAGE);` 且
     `boolean attrless = x == null;` 与之同处(resolveMobMeleeAttack 体内)。
  03 C2 的 attrless 分支存在,且代理值 = **本击原版伤害**(neo `event.getOriginalDamage()`,
     fabric `event.getAmount() / <受击侧倍率>`;fabric 还必须带 `victimFactor > 0.0` 保护,
     否则倍率为 0 时会出现除零)。
  04 `magicAbsorbFactor` **没有**在 attrless 下被跳过: 调用仍在 resolveMobMeleeAttack 体内、
     不被 `if (attrless) { … }` 或 `attrless ? … ` 包住(C2 判据:代理值未过三步减免 ⇒ 保留重乘)。
  04b 代码里留了判据说明(注释锚点),供后来者不改回「跳过」。
  05 C1 的早退真的在**任何取值之前**: neo 的 `enchantedMobAttack` 内 `if (x == null) { return 0.0; }`
     出现在 `x.getValue()` 之前;fabric 的 attrless 分支内不出现 `x.getValue()`。
  06 玩家侧读点未被误伤: `player.getAttributeValue(Attributes.ATTACK_DAMAGE)` 仍在
     (Player 恒有该属性,不在本次修复范围)。
  07 neo 的 `enchantedMobAttack` 本身也已守卫(它可能在别的调用点被复用)。
  08 **负向对照**: 对 `git show HEAD:<file>` 的旧文本跑同一组断言,必须 FAIL
     (旧文本必然含 `mob.getAttributeValue(...)` 且没有 attrless)。若旧文本反而 PASS,
     说明门禁是假绿(或 HEAD 已被别人修过),一律判 FAIL 并打印旧文本命中的行号。
  09 行尾: 工作树必须是**纯 LF** —— CR 字节数为 0(本仓索引与工作树都以 LF 为准)。
     出现任何 CR(含 CRLF)即判 FAIL,计入 RESULT;不再有软检查通道。

退出码: 全绿 0,任一硬断言失败 1。

用法:
    python scripts/verify/verify_dice_combat_attrless_guard_p1.py
    python scripts/verify/verify_dice_combat_attrless_guard_p1.py --root <repo root>
    python scripts/verify/verify_dice_combat_attrless_guard_p1.py --no-head-check   # 跳过 08

纯标准库。
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

SRC_PKG = Path("src") / "main" / "java" / "com" / "merlinkitsune" / "astral_dice"
COMBAT_REL = SRC_PKG / "combat" / "DiceCombatEvents.java"

# 两线之间唯一合法的差异面:目录、C2 代理值的取法、属性项原本叫什么。
LINES: dict[str, dict[str, str]] = {
    "neo-26.1.2": {
        "dir": "neoforge-26.1.2",
        "proxy": "event.getOriginalDamage()",
        "proxy_re": r"\?\s*\(double\)\s*event\s*\.\s*getOriginalDamage\s*\(\s*\)",
        "attr_term": "enchantedMobAttack(",
        "has_enchanted_mob_attack": "1",
    },
    "fabric-1.20.1": {
        "dir": "fabric-1.20.1",
        "proxy": "event.getAmount() / <victimFactor>",
        "proxy_re": r"attackPoints\s*=\s*event\s*\.\s*getAmount\s*\(\s*\)\s*/\s*\w+\s*;",
        "attr_term": "enchantDamageBonus(",
        "has_enchanted_mob_attack": "0",
    },
}

# ── 正则锚点(全部按语义写,不按行号) ──────────────────────────────────────────
RE_CRASH = re.compile(r"\bmob\s*\.\s*getAttributeValue\s*\(\s*Attributes\s*\.\s*ATTACK_DAMAGE\s*\)")
RE_ATTR_INIT = re.compile(
    r"\bAttributeInstance\s+(\w+)\s*=\s*\w+\s*\.\s*getAttribute\s*\(\s*Attributes\s*\.\s*ATTACK_DAMAGE\s*\)"
)
RE_ATTRLESS = re.compile(r"\bboolean\s+attrless\s*=\s*(\w+)\s*==\s*null\s*;")
RE_EARLY_RETURN = re.compile(r"if\s*\(\s*(\w+)\s*==\s*null\s*\)\s*\{\s*return\s+0\.0\s*;\s*\}")
RE_DIV_GUARD = re.compile(r"victimFactor\s*>\s*0\.0\s*\?\s*victimFactor\s*:\s*1\.0")
RE_MAGIC = re.compile(r"magicAbsorbFactor\s*\(")
RE_GATE_BLOCK = re.compile(r"if\s*\(\s*!?\s*attrless\s*\)\s*\{[^{}]*magicAbsorbFactor", re.S)
RE_GATE_TERN = re.compile(r"attrless\s*\?[^;]{0,400}magicAbsorbFactor", re.S)
RE_PLAYER_READ = re.compile(r"\bplayer\s*\.\s*getAttributeValue\s*\(\s*Attributes\s*\.\s*ATTACK_DAMAGE\s*\)")

CRITERION_MARKER = "无属性分支(attrless)的代理值同样"
SCRIPT_MARKER = "verify_dice_combat_attrless_guard_p1.py"


# ────────────────────────── source helpers ──────────────────────────
def strip_comments(text: str) -> str:
    """把 `//` 与 `/* */` 注释清空,保留字符偏移与换行;字符串字面量原样保留。"""
    out: list[str] = []
    i = 0
    n = len(text)
    in_line = in_block = in_str = False
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_line:
            out.append("\n" if c == "\n" else " ")
            if c == "\n":
                in_line = False
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
    """一个 java 源文件:原文 + 去注释代码 + 行号查找。"""

    def __init__(self, text: str, name: str, raw: bytes | None = None) -> None:
        self.name = name
        self.text = text
        self.raw = raw if raw is not None else text.encode("utf-8")
        self.code = strip_comments(text)
        self.lines = self.code.splitlines()
        self.raw_lines = self.text.splitlines()

    @classmethod
    def from_path(cls, path: Path, line_root: Path) -> "Doc":
        raw = path.read_bytes()
        return cls(raw.decode("utf-8"), path.relative_to(line_root).as_posix(), raw)

    def find_re(self, pattern: re.Pattern[str]) -> list[int]:
        return [i for i, line in enumerate(self.lines, start=1) if pattern.search(line)]

    def find_all_re(self, pattern: re.Pattern[str]) -> list[int]:
        """跨行匹配;返回命中起始行(按 `\\n` 前缀计数)。"""
        text = "\n".join(self.lines)
        return [text[: m.start()].count("\n") + 1 for m in pattern.finditer(text)]

    def first(self, needle: str) -> int | None:
        for i, line in enumerate(self.lines, start=1):
            if needle in line:
                return i
        return None

    def body(self, needle: str) -> tuple[int, int, str] | None:
        """`needle` 所在方法的花括号配平体,返回 (起行, 止行, 体文本)。"""
        start = self.first(needle)
        if start is None:
            return None
        depth = 0
        opened = False
        body: list[str] = []
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
                        return (start, idx, "\n".join(body))
            if opened:
                body.append(line)
        return None

    def body_of_text_region(self, start: int, end: int) -> str:
        return "\n".join(self.lines[start - 1 : end])


def loc(doc: Doc, n: int | None) -> str:
    return f"{doc.name}:L{n}" if n is not None else f"{doc.name}:MISSING"


def locs(doc: Doc, ns: list[int]) -> str:
    return "[" + ", ".join(f"L{n}" for n in ns) + "]" if ns else "[none]"


# ────────────────────────── assertions ──────────────────────────
def evaluate(tag: str, cfg: dict[str, str], root: Path) -> tuple[list[tuple[str, bool, str]], list[tuple[str, str]]]:
    """跑 01-07;返回 (results, warnings)。"""
    results: list[tuple[str, bool, str]] = []
    warnings: list[tuple[str, str]] = []
    pkg = root / cfg["dir"]
    line_root = pkg / SRC_PKG
    path = pkg / COMBAT_REL
    doc = Doc.from_path(path, line_root)

    def check(name: str, ok: bool, detail: str) -> None:
        results.append((f"{tag}-{name}", ok, detail))

    mob_body = doc.body("private static void resolveMobMeleeAttack(")
    mob_start, mob_end = (mob_body[0], mob_body[1]) if mob_body else (None, None)
    mob_text = mob_body[2] if mob_body else ""

    # ── 01 崩溃读点已消失 ─────────────────────────────────────────
    crash = doc.find_re(RE_CRASH)
    check(
        "01 no unguarded mob.getAttributeValue(ATTACK_DAMAGE)",
        bool(mob_body) and not crash,
        f"hits {locs(doc, crash)} (expected [none]); resolveMobMeleeAttack @ {loc(doc, mob_start)}-L{mob_end}"
        if mob_body
        else "resolveMobMeleeAttack( MISSING",
    )

    # ── 02 C1 存在: 实例 + attrless ───────────────────────────────
    inits = [
        (m.group(1), n)
        for n in doc.find_re(RE_ATTR_INIT)
        for m in [RE_ATTR_INIT.search(doc.lines[n - 1])]
        if m
    ]
    attrless_hits = [
        (m.group(1), n)
        for n in doc.find_re(RE_ATTRLESS)
        for m in [RE_ATTRLESS.search(doc.lines[n - 1])]
        if m
    ]
    attrvar = next((v for v, _ in attrless_hits), None)
    init_var = next((v for v, _ in inits), None)
    ok2 = (
        bool(mob_body)
        and attrvar is not None
        and init_var is not None
        and attrvar == init_var
        and any(mob_start is not None and mob_start <= n <= mob_end for _, n in inits)
        and any(mob_start is not None and mob_start <= n <= mob_end for _, n in attrless_hits)
    )
    check(
        "02 C1: AttributeInstance null-check (attrless flag)",
        ok2,
        f"AttributeInstance {init_var or '?'} = mob.getAttribute(...) @ {locs(doc, [n for _, n in inits])}; "
        f"attrless = {attrvar or '?'} == null @ {locs(doc, [n for _, n in attrless_hits])}",
    )

    # ── 03 C2 attrless 分支 + 代理值 ──────────────────────────────
    proxy_hits = [n for n in doc.find_all_re(re.compile(cfg["proxy_re"], re.S))]
    proxy_in_mob = [n for n in proxy_hits if mob_start is not None and mob_start <= n <= mob_end]
    ok3 = bool(proxy_in_mob)
    detail3 = f"proxy '{cfg['proxy']}' @ {locs(doc, proxy_hits)} (inside resolveMobMeleeAttack: {locs(doc, proxy_in_mob)})"
    if tag == "fabric-1.20.1":
        guard_hits = [n for n in doc.find_all_re(re.compile(RE_DIV_GUARD.pattern, re.S))]
        ok3 = ok3 and bool(guard_hits)
        detail3 += f"; divide-by-factor guard @ {locs(doc, guard_hits)}"
    check("03 C2: attrless branch exists, proxy = this hit's vanilla damage", ok3, detail3)

    # ── 04 magicAbsorbFactor 未在 attrless 下被跳过 ───────────────
    magic_hits = [n for n in doc.find_all_re(RE_MAGIC) if mob_start is not None and mob_start <= n <= mob_end]
    gate_block = RE_GATE_BLOCK.search(mob_text)
    gate_tern = RE_GATE_TERN.search(mob_text)
    ok4 = len(magic_hits) >= 1 and gate_block is None and gate_tern is None
    detail4 = f"magicAbsorbFactor( @ {locs(doc, magic_hits)}; gated-by-attrless: block={bool(gate_block)} tern={bool(gate_tern)}"
    check("04 magicAbsorbFactor NOT skipped on the attrless path", ok4, detail4)

    # ── 04b 判据说明留在代码里 ───────────────────────────────────
    anchor = magic_hits[0] if magic_hits else None
    window = ""
    if anchor is not None:
        window = "\n".join(doc.raw_lines[max(0, anchor - 10) : anchor])
    ok4b = CRITERION_MARKER in window and SCRIPT_MARKER in window
    check(
        "04b in-code criterion note above magicAbsorbFactor",
        ok4b,
        f"markers {('ok' if CRITERION_MARKER in window else 'missing')}/"
        f"{('ok' if SCRIPT_MARKER in window else 'missing')} within 10 lines above {loc(doc, anchor)}",
    )

    # ── 05 早退在取值之前 (neo) / attrless 分支内不取值 (fabric) ──
    if cfg["has_enchanted_mob_attack"] == "1":
        ench = doc.body("private static double enchantedMobAttack(")
        if ench:
            e_start, e_end, e_text = ench
            early = RE_EARLY_RETURN.search(e_text)
            getval = re.search(rf"\b{re.escape(init_var or '@@')}\s*\.\s*getValue\s*\(", e_text)
            early_off = e_start + e_text[: early.start()].count("\n") if early else None
            getval_off = e_start + e_text[: getval.start()].count("\n") if getval else None
            ok5 = early is not None and getval is not None and early_off < getval_off
            detail5 = (
                f"enchantedMobAttack @ {loc(doc, e_start)}-L{e_end}: "
                f"null-return @ {loc(doc, early_off)} {'<' if ok5 else '>='} getValue @ {loc(doc, getval_off)}"
            )
        else:
            ok5 = False
            detail5 = "enchantedMobAttack( MISSING"
    else:
        # fabric: 属性项只在 else 分支取值,attrless 分支不得触碰 AttributeInstance
        branch = None
        m = re.search(r"if\s*\(\s*attrless\s*\)\s*\{", mob_text) if mob_body else None
        if m and mob_start is not None:
            start_line = mob_start + mob_text[: m.start()].count("\n")
            acc: list[str] = []
            depth = 0
            for idx in range(start_line, (mob_end or start_line) + 1):
                line = doc.lines[idx - 1]
                depth += line.count("{") - line.count("}")
                acc.append(line)
                if depth <= 0:
                    break
            branch = "\n".join(acc)
        ok5 = branch is not None and not re.search(
            rf"\b{re.escape(init_var or '@@')}\s*\.\s*getValue\s*\(", branch or ""
        )
        detail5 = f"attrless branch @ {loc(doc, (mob_start + mob_text[: m.start()].count(chr(10))) if (m and mob_start) else None)}; "
        detail5 += f"getValue( inside it = {'absent (expected)' if ok5 else 'present'}"
    check("05 C1 early return precedes any attribute value read", ok5, detail5)

    # ── 06 玩家侧读点未被误伤 ───────────────────────────────────
    player_hits = doc.find_re(RE_PLAYER_READ)
    check(
        "06 player-side getAttributeValue(ATTACK_DAMAGE) intact",
        bool(player_hits),
        f"player.getAttributeValue(ATTACK_DAMAGE) @ {locs(doc, player_hits)}",
    )

    # ── 09 行尾(硬断言): 本仓以 LF 为准,工作树不得出现任何 CR ──────────────
    crlf = doc.raw.count(b"\r\n")
    cr = doc.raw.count(b"\r")
    bare_cr = cr - crlf
    check(
        "09 line endings: LF only (crlf=0, bare_cr=0)",
        cr == 0,
        f"LF-only baseline: crlf={crlf}, bare_cr={bare_cr}, cr_bytes={cr} "
        f"(expected cr_bytes=0; index and worktree are both LF in this repo)",
    )

    # 软检查通道保留(当前没有软检查): 09 已从 WARN 升为硬断言。
    return results, warnings


def head_negative_control(
    root: Path, results: list[tuple[str, bool, str]]
) -> None:
    """08: 同一组断言跑在 `git show HEAD:<file>` 的旧文本上必须 FAIL。"""
    for tag, cfg in LINES.items():
        rel = (Path(cfg["dir"]) / COMBAT_REL).as_posix()
        try:
            old = subprocess.run(
                ["git", "-C", str(root), "show", f"HEAD:{rel}"],
                capture_output=True,
                check=True,
            ).stdout.decode("utf-8")
        except (OSError, subprocess.CalledProcessError) as exc:
            results.append(
                (
                    f"{tag}-08 negative control (HEAD text must FAIL)",
                    False,
                    f"cannot read HEAD:{rel} ({type(exc).__name__}) -> gate unproven",
                )
            )
            continue
        odoc = Doc(old, f"HEAD:{rel}")
        crash = odoc.find_re(RE_CRASH)
        attrless = odoc.find_re(RE_ATTRLESS)
        inits = odoc.find_re(RE_ATTR_INIT)
        proxy = odoc.find_all_re(re.compile(cfg["proxy_re"], re.S))
        # 旧文本必须:含崩溃读点、且没有 attrless 分支 ⇒ 同一组断言必然判红。
        ok = bool(crash) and not attrless and not inits and not proxy
        detail = (
            f"HEAD crash-read @ {locs(odoc, crash)}; attrless @ {locs(odoc, attrless)}; "
            f"AttributeInstance @ {locs(odoc, inits)}; proxy @ {locs(odoc, proxy)}"
        )
        if not crash:
            detail += " -> HEAD already has no crash read (someone fixed it?); gate cannot be contrasted"
        results.append((f"{tag}-08 negative control (HEAD text must FAIL)", ok, detail))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="attrless guard gate for DiceCombatEvents (neoforge-26.1.2 + fabric-1.20.1)"
    )
    parser.add_argument(
        "--root",
        default=str(REPO_ROOT),
        help="repo root holding both neoforge-26.1.2/ and fabric-1.20.1/ (default: repo of this script)",
    )
    parser.add_argument("--no-head-check", action="store_true", help="skip assertion 08 (git show HEAD)")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    print(f"[root] {root}")
    for tag, cfg in LINES.items():
        print(f"[target:{tag}] {root / cfg['dir'] / COMBAT_REL}")

    missing = [str(root / cfg["dir"] / COMBAT_REL) for cfg in LINES.values() if not (root / cfg["dir"] / COMBAT_REL).is_file()]
    if missing:
        print()
        print("[FAIL] 00 target files present  [missing: " + ", ".join(missing) + "]")
        print()
        print("RESULT: FAIL")
        return 1

    print()
    results: list[tuple[str, bool, str]] = []
    warnings: list[tuple[str, str]] = []
    for tag, cfg in LINES.items():
        line_results, line_warnings = evaluate(tag, cfg, root)
        results.extend(line_results)
        warnings.extend(line_warnings)
    if not args.no_head_check:
        head_negative_control(root, results)

    print()
    for name, ok, detail in results:
        print(f"[{'OK' if ok else 'FAIL'}] {name}  [{detail}]")
    for name, detail in warnings:
        print(f"[WARN] {name}  [{detail}]")
    all_ok = all(ok for _, ok, _ in results)
    print()
    print("RESULT: " + ("PASS" if all_ok else "FAIL"))
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
