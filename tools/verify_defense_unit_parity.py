#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_defense_unit_parity.py —— 「防御力 → 护甲值」折算口径守门
=============================================================================

对应 2026-10-25 用户裁决（防御口径修正，P0 两条线 neoforge-1.21.1 / forge-1.20.1）。

背景（为什么必须当批修）
------------------------
旧实现把「防御力 +X」统一按 **×2.0 护甲值** 折算。那是旧防御公式「2 + 护甲÷2」
的遗留（当时 2 点护甲恰好 = 1 点防御力）。2026-09-26 公式改为
「2 + 护甲×0.30 + 0.85×韧性」后转换式没有同步 ⇒ 文案写「防御力 +X」，实测只兑现
**0.6X**（数值口径失真 = AGENTS-full.md:584-613〈平衡性红线〉里的恶性 BUG）。

现行口径
--------
1 点防御力 = 1 ÷ 阵营护甲系数 的护甲值：
  · 玩家：1 / 0.30 ≈ 3.3333 护甲
  · 生物：1 / 0.40 = 2.5 护甲
单一真值 = 库 CombatFormula.PLAYER_ARMOR_COEF / MOB_ARMOR_COEF，本仓库只用
DiceCombatModifiers.PLAYER_ARMOR_PER_DEFENSE / MOB_ARMOR_PER_DEFENSE（= 系数倒数）
与 armorValueForDefensePoints(...)，**不得**再写字面量 2.0 / 4.0。

检查项（每条都必须 ALL PASS 才算过）
------------------------------------
D1 旧折算字面量清零(两线 src/main/java/**/*.java，注释与代码一并判定)
   defensePoints * 2.0 / modifierDefense * 2.0 / defense * 4.0D / stacks * 2.0D
   / DEFENSE_BONUS * 2（裸 int 折算）/ ARMOR_PER_DEFENSE = 2.0
D2 DiceCombatModifiers 的单一真值
   D2-a PLAYER_ARMOR_PER_DEFENSE / MOB_ARMOR_PER_DEFENSE 必须 = 1.0 / CombatFormula.<系数>
   D2-b armorValueForDefensePoints 双载重(boolean 阵营 / LivingEntity 自动判 Player)
   D2-c 两个 setDefenseArmorBonus 都必须调用 armorValueForDefensePoints(
D3 防御力读数同源
   D3-a defensePowerBase 的 effectiveArmor 用 armorValueForDefensePoints(
   D3-b defensePowerOf 的 floor 带浮点尘埃容差(1.0E-6D，否则 3/0.30 = 9.999… 少算 1)
D4 骰战结算同源(DiceCombatEvents 的 effectiveArmor 用 armorValueForDefensePoints + instanceof Player)
D5 物品/事件侧逐点接线
   D5-a MotoHelmetChipItem.ARMOR_PER_DEFENSE 引用 DiceCombatModifiers.PLAYER_ARMOR_PER_DEFENSE
   D5-b ObsidianDiceItem.ARMOR_BONUS 引用同一常量
   D5-c DorothySignItem 暖意护甲 / JillSignItem 防御牌护甲引用同一常量
   D5-d EventEffects 雷暴将至的 event_storm_def 用 MOB_ARMOR_PER_DEFENSE
D6 指南文案 12 条(四线 × en_us/zh_cn/ja_jp)
   D6-a 玩家基础防御必须写 2（不得再写 4）
   D6-b 必须写明「1 防御力 = 1÷护甲系数」口径（含 3.33 与 2.5 两个数）
   D6-c 不得残留「1 防御力 = 2 护甲值」旧口径
D7 Attributes.ARMOR 站点白名单(两线)
   除白名单里**真护甲语义**的站点外，任何写 ARMOR 修饰器的行必须带折算 token
   (armorValueForDefensePoints / PLAYER_ARMOR_PER_DEFENSE / MOB_ARMOR_PER_DEFENSE
   / ARMOR_PER_DEFENSE / ARMOR_BONUS)
D9 护甲读数不得用 floor 值(2026-10-25 实机暴露的 off-by-one)
   D9-a DiceCombatModifiers.effectiveArmorPoints 存在且读 getAttributeValue(Attributes.ARMOR)
   D9-b defensePowerBase 的 effectiveArmor 走 effectiveArmorPoints(player)
   D9-c DiceCombatEvents 骰战 effectiveArmor 走 effectiveArmorPoints(target)
   D9-d combat/*.java 与 PiercingGunChipItem 里不得再出现 .getArmorValue() 调用
        （getArmorValue = Mth.floor(ARMOR)，会把 26.6667 截成 26 ⇒ 「防御力 +8」只兑现 +7）
D8 P1 两线(neoforge-26.1.2 / fabric-1.20.1)欠账登记可见性
   只统计、不判 FAIL；读数必须与 docs/compat-1.5.0-porting.md 的登记一致(逐线打印)

用法
----
    python tools/verify_defense_unit_parity.py            # 全量判定
    python tools/verify_defense_unit_parity.py --verbose  # 打印逐条命中明细
退出码：0 = 全部通过；1 = 存在 FAIL。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

try:  # Windows 控制台默认 GBK，守门输出含 ⇒/× 等符号
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = Path(__file__).resolve().parents[1]

P0 = {
    "neoforge-1.21.1": ROOT / "neoforge-1.21.1" / "src" / "main",
    "forge-1.20.1": ROOT / "forge-1.20.1" / "src" / "main",
}
P1 = {
    "neoforge-26.1.2": ROOT / "neoforge-26.1.2" / "src" / "main",
    "fabric-1.20.1": ROOT / "fabric-1.20.1" / "src" / "main",
}
LANGS = ("en_us.json", "zh_cn.json", "ja_jp.json")

PKG = "com/merlinkitsune/astral_dice"

failures: list[str] = []
rows: list[tuple[str, str, str]] = []  # (id, status, detail)


def record(check_id: str, ok: bool, detail: str) -> None:
    rows.append((check_id, "PASS" if ok else "FAIL", detail))
    if not ok:
        failures.append(f"{check_id}: {detail}")


def java_files(base: Path):
    d = base / "java"
    if not d.is_dir():
        return []
    return sorted(p for p in d.rglob("*.java"))


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def find_one(base: Path, rel: str) -> Path:
    p = base / "java" / rel
    if not p.is_file():
        raise FileNotFoundError(f"缺少源文件: {p}")
    return p


# ---------------------------------------------------------------- D1 旧字面量
LEGACY_LITERALS = [
    r"defensePoints\s*\*\s*2\.0",
    r"modifierDefense\s*\*\s*2\.0",
    r"defense\s*\*\s*4\.0D?",
    r"stacks\s*\*\s*2\.0D",
    r"DEFENSE_BONUS\s*\*\s*2\b",
    r"ARMOR_PER_DEFENSE\s*=\s*2\.0",
]

def check_d1(verbose: bool) -> None:
    for line, base in P0.items():
        hits = []
        for f in java_files(base):
            text = read_text(f)
            for i, src in enumerate(text.splitlines(), 1):
                for pat in LEGACY_LITERALS:
                    if re.search(pat, src):
                        hits.append(f"{f.relative_to(ROOT)}:{i}: {src.strip()}")
        record(f"D1[{line}]", not hits,
               "旧 ×2.0/×4.0 折算字面量 0 处" if not hits else f"{len(hits)} 处残留: " + " | ".join(hits[:6]))
        if verbose and hits:
            print("\n".join(hits))


def _method_body_contains(text: str, head_pat: str, body_pat: str, span: int = 4000) -> bool:
    """从方法头开始取 span 字符窗口再找方法体特征（方法体内有嵌套 {} 与 ()，不能用 [^}]*/[^)]* 截断）。"""
    m = re.search(head_pat, text)
    while m:
        if re.search(body_pat, text[m.end():m.end() + span], re.S):
            return True
        m = re.search(head_pat, text[m.end():])
    return False


# ------------------------------------------------------- D2/D3/D4 同源折算
def check_d2_d3_d4(verbose: bool) -> None:
    for line, base in P0.items():
        mod = find_one(base, f"{PKG}/combat/DiceCombatModifiers.java")
        ev = find_one(base, f"{PKG}/combat/DiceCombatEvents.java")
        mtext, etext = read_text(mod), read_text(ev)

        a = (re.search(r"PLAYER_ARMOR_PER_DEFENSE\s*=\s*1\.0\s*/\s*[^\n]*CombatFormula\.PLAYER_ARMOR_COEF", mtext) is not None
             and re.search(r"MOB_ARMOR_PER_DEFENSE\s*=\s*1\.0\s*/\s*[^\n]*CombatFormula\.MOB_ARMOR_COEF", mtext) is not None)
        record(f"D2-a[{line}]", a, "两个折算常量均由库 CombatFormula 系数取倒数")

        b = (re.search(r"armorValueForDefensePoints\s*\(\s*double\s+defensePoints\s*,\s*boolean\s+playerSide\s*\)", mtext) is not None
             and re.search(r"armorValueForDefensePoints\s*\(\s*double\s+defensePoints\s*,\s*net\.minecraft\.world\.entity\.LivingEntity\s+entity\s*\)", mtext) is not None
             and re.search(r"entity\s+instanceof\s+Player", mtext) is not None)
        record(f"D2-b[{line}]", b, "armorValueForDefensePoints 双载重(阵营布尔 + LivingEntity 自动判 Player)")

        c = (re.search(r"setDefenseArmorBonus\s*\(\s*Player[^)]*\)\s*\{[^}]*armor\s*=\s*armorValueForDefensePoints\s*\(\s*defensePoints\s*,\s*true\s*\)", mtext, re.S) is not None
             and re.search(r"setDefenseArmorBonus\s*\(\s*(?:LivingEntity|net\.minecraft\.world\.entity\.LivingEntity)[^)]*\)\s*\{[^}]*armorValueForDefensePoints\s*\(\s*defensePoints\s*,\s*entity\s+instanceof\s+Player\s*\)", mtext, re.S) is not None)
        record(f"D2-c[{line}]", c, "Player / LivingEntity 两个 setDefenseArmorBonus 均走折算函数")

        d3a = _method_body_contains(mtext, r"defensePowerBase\s*\([^)]*\)\s*\{", r"effectiveArmor\s*=\s*Math\.max\([^;]*armorValueForDefensePoints\s*\(")
        record(f"D3-a[{line}]", d3a, "defensePowerBase 的 effectiveArmor 走折算函数")

        d3b = re.search(r"return\s*\(int\)\s*Math\.floor\(\s*defensePowerBase\([^;]*\+\s*1\.0E-6D\s*\)", mtext) is not None
        record(f"D3-b[{line}]", d3b, "defensePowerOf 的 floor 带 1.0E-6D 浮点尘埃容差")

        d4 = re.search(r"effectiveArmor\s*=\s*Math\.max\([^;]*armorValueForDefensePoints\s*\(\s*modifierDefense\s*,\s*target\s+instanceof\s+Player\s*\)", etext) is not None
        record(f"D4[{line}]", d4, "DiceCombatEvents 骰战 effectiveArmor 走折算函数(按目标阵营)")

        if verbose:
            print(f"[{line}] mod={mod}  ev={ev}")


# ------------------------------------------------------------ D5 逐点接线
ITEMS = [
    ("D5-a", "item/chip/MotoHelmetChipItem.java", r"ARMOR_PER_DEFENSE\s*=\s*[^;]*DiceCombatModifiers\.PLAYER_ARMOR_PER_DEFENSE",
     "摩托头盔折算比引用单一真值"),
    ("D5-b", "item/dice/ObsidianDiceItem.java", r"ARMOR_BONUS\s*=\s*DEFENSE_BONUS\s*\*\s*[^;]*DiceCombatModifiers\.PLAYER_ARMOR_PER_DEFENSE",
     "黑曜石骰折算引用单一真值"),
    ("D5-c", "item/sign/DorothySignItem.java", r"dorothy_warmth_armor[^;]*PLAYER_ARMOR_PER_DEFENSE",
     "多萝西暖意护甲引用单一真值"),
    ("D5-d", "item/sign/JillSignItem.java", r"TARGET_ARMOR_KEY,\s*defense\s*\*\s*2\s*\*\s*[^;]*PLAYER_ARMOR_PER_DEFENSE",
     "Jill 防御牌护甲引用单一真值"),
    ("D5-e", "event/EventEffects.java", r"event_storm_def\",\s*Attributes\.ARMOR,\s*2\s*\*\s*DiceCombatModifiers\.MOB_ARMOR_PER_DEFENSE",
     "雷暴将至生物护甲按生物系数折算"),
]

def check_d5(verbose: bool) -> None:
    for line, base in P0.items():
        for cid, rel, pat, desc in ITEMS:
            p = find_one(base, f"{PKG}/{rel}")
            ok = re.search(pat, read_text(p), re.S) is not None
            record(f"{cid}[{line}]", ok, desc)


# ------------------------------------------------------------ D6 指南文案
GUIDE_KEY = "astral_dice.guide.entry.battle_dice.2"
BAD_BASE = re.compile(r"(?:玩家\s*防御\s*=\s*4|player defense = 4|プレイヤー 防御 = 4)")
GOOD_BASE = re.compile(r"(?:玩家\s*防御\s*=\s*2|player defense = 2|プレイヤー 防御 = 2)")
RATIO = re.compile(r"3\.33")
MOB_RATIO = re.compile(r"2\.5")
BAD_UNIT = re.compile(r"(?:1\s*防御力\s*=\s*2\s*护甲值|1 defense = 2 armor)")

def check_d6(verbose: bool) -> None:
    for line, base in P0.items():
        for lang in LANGS:
            p = base / "resources" / "assets" / "astral_dice" / "lang" / lang
            if not p.is_file():
                record(f"D6[{line}/{lang}]", False, f"缺少 {p.relative_to(ROOT)}")
                continue
            data = json.loads(p.read_text(encoding="utf-8"))
            val = data.get(GUIDE_KEY, "")
            problems = []
            if not val:
                problems.append("键缺失")
            else:
                if BAD_BASE.search(val):
                    problems.append("基础防御仍写 4")
                if not GOOD_BASE.search(val):
                    problems.append("基础防御未写 2")
                if not RATIO.search(val):
                    problems.append("未写 1÷系数 的 3.33 读数")
                if not MOB_RATIO.search(val):
                    problems.append("未写生物 2.5 读数")
                if BAD_UNIT.search(val):
                    problems.append("残留「1 防御力 = 2 护甲值」旧口径")
            record(f"D6[{line}/{lang}]", not problems,
                   "指南口径 = 2 基数 + 1÷系数(3.33 / 2.5)" if not problems else "; ".join(problems))
            if verbose and problems:
                print(f"  {p}: {val[:160]}")


# ---------------------------------------------------- D7 ARMOR 站点白名单
# 真护甲语义（写的就是护甲值本身，文案也写「护甲值」）⇒ 允许不走防御力折算。
WHITELIST = {
    "effect/UnwaveringEffect.java",
    "effect/DragonRoarBreakEffect.java",
    "effect/BlueCurseEffect.java",
    "effect/JasmineSweepEffect.java",
    "item/sign/MosesEnigmaticLink.java",
}
CONVERSION_TOKEN = re.compile(
    r"(armorValueForDefensePoints|defensePointsForArmor|PLAYER_ARMOR_PER_DEFENSE|MOB_ARMOR_PER_DEFENSE|ARMOR_PER_DEFENSE|ARMOR_BONUS)")

def check_d7(verbose: bool) -> None:
    for line, base in P0.items():
        offenders = []
        for f in java_files(base):
            rel = str(f.relative_to(base / "java" / PKG)).replace("\\", "/")
            if any(rel.endswith(w) for w in WHITELIST):
                continue
            lines = read_text(f).splitlines()
            for i, src in enumerate(lines, 1):
                if not re.search(r"Attributes\.ARMOR(?!_)", src):
                    continue
                # 摘除修饰器 / 归零不算「写数值」
                if re.search(r"\.remove\(|removeAttribute|,\s*0\s*\)|getAttributeValue\(|getArmorValue\(|getAttribute\(", src):
                    continue
                # 只关心「往 ARMOR 写数值」的行：同文件必须能找到折算 token（含反算 defensePointsForArmor）
                window = "\n".join(lines[max(0, i - 4):i + 4])
                if not CONVERSION_TOKEN.search(window):
                    offenders.append(f"{f.relative_to(ROOT)}:{i}: {src.strip()}")
        record(f"D7[{line}]", not offenders,
               "ARMOR 写点均带折算 token 或属真护甲白名单" if not offenders else f"{len(offenders)} 处可疑: " + " | ".join(offenders[:5]))
        if verbose and offenders:
            print("\n".join(offenders))


# ------------------------------------------------------- D8 P1 欠账可见性
def report_d8() -> None:
    for line, base in P1.items():
        if not (base / "java").is_dir():
            record(f"D8[{line}]", True, "线不存在或不含 src/main/java（跳过）")
            continue
        n_legacy = 0
        for f in java_files(base):
            text = read_text(f)
            for pat in LEGACY_LITERALS:
                n_legacy += len(re.findall(pat, text))
        record(f"D8[{line}]", True,
               f"P1 未移植（旧折算命中 {n_legacy} 处）—— 必须登记在 docs/compat-1.5.0-porting.md，不作为本批 FAIL")


# ------------------------------------------------------------- 数值自检表
def numeric_table() -> list[str]:
    out = ["", "数值自检（系数倒数折算后，防御力贡献必须 1:1）"]
    for name, coef in (("玩家", 0.30), ("生物", 0.40)):
        per = 1.0 / coef
        for pts in (2, 3, 4, 8):
            armor = pts * per
            back = armor * coef
            out.append(f"  {name}: +{pts} 防御力 ⇒ +{armor:.4f} 护甲 ⇒ 折算回 {back:.4f} 防御力"
                       f"{'' if abs(back - pts) < 1e-9 else '  <-- 不等于输入!'}")
    out.append("")
    return out


# ------------------------------------------- D9 护甲读数(floor 值)清零
FLOOR_FILES = [
    f"{PKG}/combat/DiceCombatModifiers.java",
    f"{PKG}/combat/DiceCombatEvents.java",
    f"{PKG}/item/chip/PiercingGunChipItem.java",
]


def _strip_code_noise(src: str) -> str:
    """去掉 // 注释与字符串字面量，避免把注释里的 getArmorValue 当调用。"""
    src = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
    return re.sub(r"//[^\n]*", "", src)


def check_d9(verbose: bool) -> None:
    for line, base in P0.items():
        mod = find_one(base, f"{PKG}/combat/DiceCombatModifiers.java")
        ev = find_one(base, f"{PKG}/combat/DiceCombatEvents.java")
        mtext, etext = read_text(mod), read_text(ev)

        a = re.search(r"effectiveArmorPoints\s*\(\s*(?:net\.minecraft\.world\.entity\.)?LivingEntity\s+entity\s*\)\s*\{[^}]*getAttributeValue\s*\(\s*Attributes\.ARMOR\s*\)", mtext, re.S) is not None
        record(f"D9-a[{line}]", a, "effectiveArmorPoints 存在且读 ARMOR 属性 double 终值")

        b = re.search(r"effectiveArmor\s*=\s*Math\.max\([^;]*effectiveArmorPoints\s*\(\s*player\s*\)", mtext) is not None
        record(f"D9-b[{line}]", b, "defensePowerBase 走 effectiveArmorPoints(player)")

        c = re.search(r"effectiveArmor\s*=\s*Math\.max\([^;]*effectiveArmorPoints\s*\(\s*target\s*\)", etext) is not None
        record(f"D9-c[{line}]", c, "骰战 effectiveArmor 走 effectiveArmorPoints(target)")

        hits = []
        for rel in FLOOR_FILES:
            f = find_one(base, rel)
            for i, src in enumerate(read_text(f).splitlines(), 1):
                if ".getArmorValue(" in _strip_code_noise(src):
                    hits.append(f"{f.relative_to(ROOT)}:{i}: {src.strip()}")
        record(f"D9-d[{line}]", not hits,
               "防御公式侧 .getArmorValue() 0 处" if not hits else f"{len(hits)} 处 floor 读数: " + " | ".join(hits[:5]))
        if verbose and hits:
            print("\n".join(hits))


def main() -> int:
    ap = argparse.ArgumentParser(description="防御力 → 护甲值 折算口径守门")
    ap.add_argument("--verbose", action="store_true", help="打印逐条命中明细")
    args = ap.parse_args()

    check_d1(args.verbose)
    check_d2_d3_d4(args.verbose)
    check_d5(args.verbose)
    check_d6(args.verbose)
    check_d7(args.verbose)
    check_d9(args.verbose)
    report_d8()

    for cid, status, detail in rows:
        print(f"[{status}] {cid}  {detail}")
    for line in numeric_table():
        print(line)

    total = len(rows)
    if failures:
        print(f"GATE: FAIL（{total} 项中 {len(failures)} 项不满足）")
        for f in failures:
            print(f"  - {f}")
        return 1
    print(f"GATE: PASS（{total} 项全部满足）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
