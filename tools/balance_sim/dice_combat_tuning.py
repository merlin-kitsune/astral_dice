#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
骰战数值「修正 + 调参」仿真（v2）。

背景：v1 仿真（dice_combat_sim.py）已确证 —— 骰战在 LivingDamageEvent.Pre 用覆盖式
写入丢弃了原版整条前置链，导致 锋利 / 暴击 / 攻击冷却 / 保护 / 抗性 全部失效。本脚本在
「方案 A（去 getNewDamage）+ 简单暴击口径 + 冷却 + 附魔生效」的**修正公式**之上，把全部
公式系数与整体数值做成可调旋钮，用于回答：

    1. 修正后修饰器是否真的生效（复检矩阵）；
    2. 修正后难度曲线是否更陡（跨度比）；
    3. 用哪组参数可以把「前期太难 / 后期太高」压回合理区间。

只读依赖同目录 dice_combat_sim.py（提供 MOBS / ARMOR_SETS / WEAPONS / CARDS / 原版管线）。
本脚本**不修改任何 Java 代码**，纯离线数值实验，全部参数可从 JSON 独立复算。
"""

import argparse
import json
import math
import os
import random
import sys
from typing import Dict, List, Sequence, Tuple

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from dice_combat_sim import (  # noqa: E402
    ARMOR_SETS,
    CARDS,
    MOBS,
    WEAPONS,
    mc_stats,
    roll_card,
    ttk_mc,
    vanilla_mob_hit,
    vanilla_player_hit,
)

SEED = 20260927
PAIR_SEED = 424242


# ============================================================================================
# 1. 可调旋钮
# ============================================================================================
# 「现状」= v1 基线（骰战只读属性值、无附魔/冷却/暴击、受击方保护抗性只能经 getNewDamage 泄漏）
BASE: Dict[str, object] = dict(
    # —— 玩家防御公式  CombatFormula.playerDefense ——
    play_base=2.0,          # 起始项
    play_armor_coef=0.5,    # 护甲系数（原 1/2）
    play_tough_coef=1.4,    # 韧性系数
    # —— 生物防御公式  CombatFormula.mobDefenseInt ——
    mob_def_base=2.0,       # 敌对生物防御起始（其余 0）
    mob_armor_coef=0.5,     # 生物护甲系数（原 1/2）
    mob_tough_coef=1.125,   # 生物韧性系数
    # —— 生物攻击公式  TargetBattleStats.baseAttack + 属性值 ——
    mob_atk_base=5.0,       # 敌对攻击起始（中立 4 / 其余 0）
    mob_atk_attr_coef=1.0,  # 生物 ATTACK_DAMAGE 属性值系数
    # —— 收益压缩 ——
    card_coef=1.0,          # 卡牌点数整体系数
    dice_coef=1.0,          # 骰点整体系数
    bonus_softcap_threshold=0.0,  # 「骰点+卡牌」合计超过该值时进入收益递减（0 = 关闭）
    bonus_softcap_ratio=1.0,      # 超出部分保留比例（1.0 = 不衰减）
    # —— 下限语义（DiceBattleResolver.resolve）——
    resolve_floor_ratio=0.0,  # 0.0 = 现行「绝对 1 点下限」；>0 = 「攻击点 × ratio」相对下限
    # —— 机制开关 ——
    mitigation_mode="legacy",  # legacy = getNewDamage() 作加项；plan_a = pre-mitigation + 乘算因子
    magic_factor=False,        # 是否应用「保护 × 抗性」乘算因子（方案 A 必备）
    crit_mode="none",          # none / simple（×1.5 作用于整个攻击点）
    cooldown=False,            # 是否应用攻击冷却缩放 (0.2 + s²·0.8)
    enchanted=False,           # 是否让武器附魔加伤生效
)

PRESETS: Dict[str, Dict[str, object]] = {}

PRESETS["现状（v1 基线）"] = dict(BASE)

PRESETS["方案A·纯落地"] = dict(
    BASE,
    mitigation_mode="plan_a",
    magic_factor=True,
    crit_mode="simple",
    cooldown=True,
    enchanted=True,
)

# ---- 调参档位 ----
# 标定依据（解析式，用 MOBS/ARMOR_SETS 实数）：
#   输出 前期(石剑/裸装/无卡) = (5 + 3.5) − (mob_def_base + 僵尸护甲×coef + 3.5)
#   承伤 前期(僵尸3 → 裸装)   = mob_atk_base + 3 − play_base
# ⇒ 「mob_def_base↓」抬输出下限、「card_coef↓」压输出上限、「play_base↑」抬承伤下限。
PRESETS["档位B·仅校准基数"] = dict(
    BASE,
    play_base=4.0,          # 2.0 → 4.0：抬高基础防御 ⇒ 前期承伤 6.0 → 3.0（对齐原版）
    play_armor_coef=0.35,   # 0.5 → 0.35：削弱高甲斜率 ⇒ 压「裸装→满配」承伤跨度
    play_tough_coef=1.0,    # 1.4 → 1.0
    mob_def_base=0.0,       # 2.0 → 0.0：生物防御起始归零 ⇒ 抬高玩家输出下限（2.5 → 5.0）
    mob_armor_coef=0.40,    # 0.5 → 0.40
    mob_tough_coef=1.0,     # 1.125 → 1.0
    mob_atk_base=4.0,       # 5.0 → 4.0（配合 play_base=4，使前期承伤 = 原版）
    card_coef=1.0,          # **不压卡牌** ⇒ 用于观察「修机制 + 校基数」后的原生跨度
    mitigation_mode="plan_a",
    magic_factor=True,
    crit_mode="simple",
    cooldown=True,
    enchanted=True,
)

PRESETS["档位C·中度压缩（推荐）"] = dict(
    BASE,
    play_base=4.0,
    play_armor_coef=0.30,
    play_tough_coef=0.85,
    mob_def_base=0.0,
    mob_armor_coef=0.40,
    mob_tough_coef=1.0,
    mob_atk_base=4.0,
    card_coef=0.65,         # 中度压低卡牌上限（终局 3×特大 → ×0.65）
    resolve_floor_ratio=0.15,  # 下限由「绝对 1 点」改为「攻击点 ×15%」⇒ 治重甲档触底失真
    mitigation_mode="plan_a",
    magic_factor=True,
    crit_mode="simple",
    cooldown=True,
    enchanted=True,
)

PRESETS["档位D·重度压缩"] = dict(
    BASE,
    play_base=4.0,
    play_armor_coef=0.26,
    play_tough_coef=0.75,
    mob_def_base=0.0,
    mob_armor_coef=0.40,
    mob_tough_coef=1.0,
    mob_atk_base=4.0,
    card_coef=1.0,
    bonus_softcap_threshold=7.0,   # 「骰点+卡牌」>7 起衰减（前期 3.5 完全不受影响）
    bonus_softcap_ratio=0.45,      # 超出部分只保留 45%
    resolve_floor_ratio=0.15,
    mitigation_mode="plan_a",
    magic_factor=True,
    crit_mode="simple",
    cooldown=True,
    enchanted=True,
)


# ============================================================================================
# 2. 公式层（参数化）
# ============================================================================================

def softcap(x: float, T: Dict) -> float:
    """「骰点 + 卡牌」合计的收益递减：超过阈值 T 的部分只保留 ``ratio`` 比例。

    线性缩放（``card_coef``）会等比压小**所有**档位；软上限只削**高投入**档位，
    因此能在不伤害前期体验的前提下压低后期上限。
    """
    th = T.get("bonus_softcap_threshold", 0.0)
    q = T.get("bonus_softcap_ratio", 1.0)
    if th <= 0.0 or x <= th or q >= 1.0:
        return x
    return th + (x - th) * q


def p_def(armor: float, tough: float, T: Dict) -> float:
    """CombatFormula.playerDefense —— 参数化版本。"""
    return T["play_base"] + armor * T["play_armor_coef"] + tough * T["play_tough_coef"]


def m_def(cat: str, armor: float, tough: float, T: Dict) -> int:
    """CombatFormula.mobDefenseInt —— 参数化版本（向下取整）。"""
    b = T["mob_def_base"] if cat == "HOSTILE" else 0.0
    return int(math.floor(b + armor * T["mob_armor_coef"] + tough * T["mob_tough_coef"]))


def m_atk_base(cat: str, T: Dict) -> float:
    """TargetBattleStats.baseAttack —— 参数化版本。"""
    if cat == "HOSTILE":
        return T["mob_atk_base"]
    if cat == "NEUTRAL":
        return T["mob_atk_base"] - 1.0
    return 0.0


def magic_factor(prot_points: float, resistance_amp) -> float:
    """原版 getDamageAfterMagicAbsorb 的两通道 —— 纯乘法（库 VanillaMitigation 拟新增）。"""
    f = 1.0
    if resistance_amp is not None:
        f *= max(25.0 - 5.0 * (resistance_amp + 1), 0.0) / 25.0
    p = min(max(prot_points, 0.0), 20.0)
    f *= 1.0 - p / 25.0
    return f


def resolve(attack_power: float, defense_power: float, T: Dict) -> float:
    """DiceBattleResolver.resolve 的参数化版本。

    现行语义 = ``max(1, 攻击点 − 防御点)``（绝对 1 点下限）。
    当 ``resolve_floor_ratio > 0`` 时改用**相对下限** ``攻击点 × ratio``，
    用于消除「高防 + 低攻生物 ⇒ 恒触底」造成的读数失真。
    """
    raw = attack_power - defense_power
    floor_abs = 1.0
    floor_rel = attack_power * T.get("resolve_floor_ratio", 0.0)
    return max(raw, floor_abs, floor_rel)


def d_out(atk_attr: float, T: Dict, rng: random.Random, *,
          mob: Dict, cards: Sequence[str] = (), sharp: float = 0.0, cd: float = 1.0,
          crit: bool = False, full_power: bool = False, attack_mods: float = 0.0,
          curse_fourth: bool = False, victim_factor: float = 1.0) -> Tuple[float, float]:
    """骰战 玩家→生物 一次命中（方案 A 公式）。返回 (终值, 攻击点)。"""
    core = atk_attr + (sharp if T["enchanted"] else 0.0)
    attack_power = core * ((0.2 + cd * cd * 0.8) if T["cooldown"] else 1.0)
    attack_power += attack_mods
    bonus = (rng.randint(1, 6) * T["dice_coef"]
             + sum(roll_card(c, rng) for c in cards) * T["card_coef"])
    bonus = softcap(bonus, T)
    if curse_fourth:
        bonus *= 0.6
    attack_power += bonus
    if crit and T["crit_mode"] == "simple":
        attack_power *= 1.5
    if full_power:
        attack_power = math.ceil(attack_power * 1.5)
    defense_power = m_def(mob["cat"], mob["armor"], mob["tough"], T) + rng.randint(1, 6)
    return resolve(attack_power, defense_power, T) * victim_factor, attack_power


def d_in(mob: Dict, armor: float, tough: float, T: Dict, rng: random.Random, *,
         prot: float = 0.0, res=None, has_dice: bool = True,
         def_cards: Sequence[str] = (), mob_weapon: float = 0.0,
         victim_factor: float = 1.0) -> Tuple[float, float]:
    """骰战 生物→玩家 一次命中。legacy = 现状；plan_a = 方案 A。返回 (终值, 攻击点)。"""
    raw = mob["atk"] * T["mob_atk_attr_coef"] + mob_weapon
    if T["mitigation_mode"] == "legacy":
        # 现状：把已被护甲/抗性/保护减免过的值当**加项**捡回来（泄漏，且护甲被计入两次）
        post = _post_armor(raw, armor, tough, res, prot)
        attack_power = m_atk_base(mob["cat"], T) + raw + post
    else:
        # 方案 A：攻击点用 pre-mitigation 的原始伤害，减免改为受击方乘算因子
        attack_power = m_atk_base(mob["cat"], T) + raw
    if has_dice:
        attack_power += rng.randint(1, 6) * T["dice_coef"]
    defense_power = p_def(armor, tough, T)
    if has_dice:
        defense_power += rng.randint(1, 6) * T["dice_coef"]
        defense_power += sum(roll_card(c, rng) for c in def_cards) * T["card_coef"]
    dmg = resolve(attack_power, defense_power, T)
    if T["magic_factor"]:
        dmg *= magic_factor(prot, res)
    return dmg * victim_factor, attack_power


def _post_armor(raw: float, armor: float, tough: float, res, prot: float) -> float:
    """Legacy 通道专用的「护甲→抗性→保护」三步结果（vanilla_post_armor_value 的本地等价）。"""
    f = 2.0 + tough / 4.0
    f1 = min(max(armor - raw / f, armor * 0.2), 20.0)
    dmg = raw * (1.0 - f1 / 25.0)
    if res is not None:
        dmg = max(dmg * (25.0 - 5.0 * (res + 1)) / 25.0, 0.0)
    p = min(max(prot, 0.0), 20.0)
    return dmg * (1.0 - p / 25.0)


# ============================================================================================
# 3. 装备档位（前期 / 中期 / 后期）
# ============================================================================================
# (档位, 护甲档, 武器, 卡牌, 保护点数, 武器锋利加伤, 冷却, 是否暴击)
STAGES: List[Tuple[str, str, str, List[str], float, float, float, bool]] = [
    ("前期", "裸装",     "石剑",     [],                          0.0,  0.0, 1.0, False),
    ("中期", "铁全套",   "铁剑",     ["large"],                   8.0,  2.0, 1.0, False),
    ("后期", "钻石全套", "下界合金剑", ["epic", "epic", "epic"],    16.0, 3.0, 1.0, False),
]

OUT_TARGETS = ["僵尸", "监守者"]   # 输出侧靶
IN_SOURCES = ["僵尸", "监守者"]    # 承伤侧源


def _mean(fn, n: int, seed: int) -> float:
    rng = random.Random(seed)
    return sum(fn(rng) for _ in range(n)) / n


def eval_conf(T: Dict, n: int, ttk_trials: int) -> Dict:
    """对一组参数，算全档位曲线 + 跨度 + 修饰器复检。"""
    # ---------- 曲线 ----------
    out_curve, in_curve = [], []
    for si, (stage, armor_name, wname, cards, prot, sharp, cd, crit) in enumerate(STAGES):
        p_armor, p_tough = ARMOR_SETS[armor_name]
        atk = WEAPONS[wname]
        for target in OUT_TARGETS:
            mob = MOBS[target]
            van = vanilla_player_hit(atk, sharp, cd, crit, mob["armor"], mob["tough"])
            dice = _mean(lambda r: d_out(atk, T, r, mob=mob, cards=cards, sharp=sharp,
                                         cd=cd, crit=crit)[0],
                         n, SEED + si * 17 + OUT_TARGETS.index(target))
            out_curve.append({
                "stage": stage, "target": target, "weapon": wname, "armor_set": armor_name,
                "cards": cards, "sharp": sharp, "prot": prot,
                "vanilla": van, "dice": dice,
                "ratio": dice / van if van > 0 else float("nan"),
            })
        for src in IN_SOURCES:
            mob = MOBS[src]
            van = vanilla_mob_hit(mob["atk"], p_armor, p_tough, player_prot=prot)
            seed_in = SEED + si * 31 + IN_SOURCES.index(src)
            dice = _mean(lambda r: d_in(mob, p_armor, p_tough, T, r, prot=prot)[0], n, seed_in)
            in_curve.append({
                "stage": stage, "source": src, "armor_set": armor_name, "prot": prot,
                "player_armor": p_armor, "player_tough": p_tough,
                "vanilla": van, "dice": dice,
                "vanilla_hits": 20.0 / van if van > 0 else float("inf"),
                "dice_hits": 20.0 / dice if dice > 0 else float("inf"),
                "ratio": dice / van if van > 0 else float("nan"),
            })

    # ---------- 难度曲线陡峭度（跨度比）----------
    def span(rows, key, field):
        a = [r for r in rows if r[key] == "僵尸"]
        if len(a) < 1:
            return None
        first, last = a[0], a[-1]
        return {"first": first[field], "last": last[field],
                "span": last[field] / first[field] if first[field] > 0 else float("nan")}

    out_span_v = span(out_curve, "target", "vanilla")
    out_span_d = span(out_curve, "target", "dice")
    in_span_v = span(in_curve, "source", "vanilla")
    in_span_d = span(in_curve, "source", "dice")

    # ---------- 修饰器复检（关键：修正后应全部生效）----------
    Z = MOBS["僵尸"]
    zw, zt = ARMOR_SETS["钻石全套"]
    mod_rows = []

    def out_case(name, **kw):
        van = vanilla_player_hit(kw.get("atk", 7.0), kw.get("sharp", 0.0), kw.get("cd", 1.0),
                                 kw.get("crit", False), Z["armor"], Z["tough"])
        dic = _mean(lambda r: d_out(kw.get("atk", 7.0), T, r, mob=Z, cards=[],
                                    sharp=kw.get("sharp", 0.0), cd=kw.get("cd", 1.0),
                                    crit=kw.get("crit", False))[0], n, SEED + 900)
        mod_rows.append({"group": "输出", "modifier": name,
                         "vanilla": van, "dice": dic})

    out_case("基准（钻石剑，无修饰器）")
    out_case("锋利 V（+3）", sharp=3.0)
    out_case("攻击冷却 50%", cd=0.5)
    out_case("暴击 ×1.5", crit=True)

    def in_case(name, **kw):
        van = vanilla_mob_hit(30.0, zw, zt, player_prot=kw.get("prot", 0.0),
                              player_resistance_amp=kw.get("res"))
        dic = _mean(lambda r: d_in(MOBS["监守者"], zw, zt, T, r,
                                   prot=kw.get("prot", 0.0), res=kw.get("res"))[0], n, SEED + 901)
        mod_rows.append({"group": "承伤", "modifier": name,
                         "vanilla": van, "dice": dic})

    in_case("基准（监守者→钻石全套）")
    in_case("保护 IV 全套（16 点）", prot=16.0)
    in_case("抗性提升 II", res=1)

    base_out = mod_rows[0]["vanilla"], mod_rows[0]["dice"]
    base_in = mod_rows[4]["vanilla"], mod_rows[4]["dice"]
    for r in mod_rows:
        bv, bd = base_out if r["group"] == "输出" else base_in
        r["vanilla_delta_pct"] = (r["vanilla"] / bv - 1) * 100 if bv else 0.0
        r["dice_delta_pct"] = (r["dice"] / bd - 1) * 100 if bd else 0.0

    return {
        "out_curve": out_curve,
        "in_curve": in_curve,
        "output_span": {"vanilla": out_span_v, "dice": out_span_d},
        "intake_span": {"vanilla": in_span_v, "dice": in_span_d},
        "modifier_check": mod_rows,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="docs/balance/dice-combat-tuning.json")
    ap.add_argument("--n", type=int, default=40000)
    ap.add_argument("--ttk", type=int, default=2000)
    args = ap.parse_args()

    result = {
        "meta": {
            "mc_version": "1.21.1",
            "n_samples": args.n,
            "stages": [s[0] for s in STAGES],
            "out_targets": OUT_TARGETS,
            "in_sources": IN_SOURCES,
            "note": "方案A = 去 getNewDamage + 保护/抗性乘算因子 + 简单暴击 + 冷却 + 附魔生效",
        },
        "configs": {},
        "curves": {},
    }
    for name, T in PRESETS.items():
        result["configs"][name] = dict(T)
        result["curves"][name] = eval_conf(T, args.n, args.ttk)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)

    print(f"[tune] written {args.out}")
    ref = result["curves"]["现状（v1 基线）"]
    print("\n原版参考（同场景）:")
    print("  输出（僵尸靶）:", " → ".join(f'{r["vanilla"]:.2f}' for r in ref["out_curve"] if r["target"] == "僵尸"),
          f' | 跨度 {ref["output_span"]["vanilla"]["span"]:.2f}×')
    print("  承伤（僵尸源）:", " → ".join(f'{r["vanilla"]:.2f}' for r in ref["in_curve"] if r["source"] == "僵尸"),
          f' | 跨度 {ref["intake_span"]["vanilla"]["span"]:.3f}×')
    for name in PRESETS:
        c = result["curves"][name]
        print(f"\n=== {name} ===")
        print("  输出曲线（僵尸靶）: ", " → ".join(
            f'{r["dice"]:.2f}' for r in c["out_curve"] if r["target"] == "僵尸"),
            f' | 跨度 {c["output_span"]["dice"]["span"]:.2f}×')
        print("  承伤曲线（僵尸源）: ", " → ".join(
            f'{r["dice"]:.2f}' for r in c["in_curve"] if r["source"] == "僵尸"),
            f' | 跨度 {c["intake_span"]["dice"]["span"]:.3f}×')
        print("  修饰器复检:", " | ".join(
            f'{r["modifier"]}: 原版{r["vanilla_delta_pct"]:+.0f}%/骰战{r["dice_delta_pct"]:+.0f}%'
            for r in c["modifier_check"]))


if __name__ == "__main__":
    main()
