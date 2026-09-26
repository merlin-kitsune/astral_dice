# -*- coding: utf-8 -*-
"""
《星之骰戏》1.21.1 —— 骰战 / 原版战斗 数值仿真器
==================================================

用途
----
对「新版骰战」与「原版战斗」做同口径数值对比，回答三类问题：
  1. 难度变化曲线：玩家→生物（输出）与 生物→玩家（承伤）随成长档位如何变化；
  2. 原版伤害修饰器（护甲/韧性/保护附魔/抗性提升/吸收/锋利/冷却/暴击）对骰战的实际影响；
  3. 《神秘遗物+》七咒之戒（第三诅咒 -30% 护甲与韧性；第四诅咒 -40% 骰点与卡牌）的实际影响。

公式唯一事实源（本脚本逐条对齐，不得凭记忆改动）
------------------------------------------------
- 库共享公式：F:/MCProject/starengine_lib/common/src/main/java/com/merlinkitsune/starenginelib/combat/
    CombatFormula.playerDefense / mobDefenseInt
    TargetBattleStats.baseAttack / baseDefense
    DiceBattleResolver.resolve                          (max(1, atk - def))
- 1.21.1 模组实现：neoforge-1.21.1/.../combat/DiceCombatEvents.java
    onLivingDamagePre                     玩家→生物（含七咒第四诅咒）
    resolveMobMeleeAttack                 生物→玩家（独立路径）
    consumeOneDefenseCardDurability       防御牌耐久（不影响单次伤害期望，本脚本不建模耐久）
  1.21.1 模组实现：neoforge-1.21.1/.../combat/CardRegistry.java（卡牌点数区间）
  1.21.1 模组实现：neoforge-1.21.1/.../event/ChipDamageHandler.java（@LOWEST 固定值减伤）
- 原版管线（取自 build/moddev/artifacts/neoforge-21.1.235-sources.jar）
    LivingEntity.actuallyHurt L1785-1807  ⇒ 次序 = 护甲 → 抗性/保护附魔 → LivingDamageEvent.Pre → 吸收
    LivingEntity.getDamageAfterArmorAbsorb L1725 / getDamageAfterMagicAbsorb L1739
    CombatRules.getDamageAfterAbsorb L16 / getDamageAfterMagicAbsorb L32
    Player.attack L1222（冷却缩放 0.2+cd²·0.8；附魔加伤单独一件；暴击 ×1.5）
    Mob.doHurtTarget L1491（f = ATTACK_DAMAGE，附魔 modifyDamage）
    DamageContainer.setReduction（NeoForge）就地扣减 newDamage ⇒ Pre 取值已含护甲/抗性/保护
- 七咒之戒（第三方，jar 反汇编 + 实例配置）
    auviotre/enigmatic/legacy/contents/item/rings/CursedRing#getArmorModifiers
      ARMOR 与 ARMOR_TOUGHNESS 各挂 ADD_MULTIPLIED_TOTAL = -0.01 × armorDebuff
    实例配置 D:/.minecraft/versions/狐の航空学 Voxy Edition/config/enigmaticlegacyplus-server.toml
      [sevenCurses] armorDebuff = 30 / monsterDamageDebuff = 40 / painMultiplier = 200

用法
----
    python dice_combat_sim.py --out dice-combat-simulation.json [--n 50000] [--ttk 4000] [--seed 20260926]
"""

from __future__ import annotations

import argparse
import json
import math
import random
from typing import Callable, Dict, List, Tuple

# ============================================================================================
# 0. 数据表（全部来自已取证的上游源码 / 实例配置，改动必须附来源）
# ============================================================================================

# 生物属性：实体类自带 .add(...) 的显式值；未显式声明者取属性默认值
#   ATTACK_DAMAGE 默认 2.0（Monster.createMonsterAttributes().add(Attributes.ATTACK_DAMAGE) 用默认基值）
#   ARMOR 默认 0.0 / ARMOR_TOUGHNESS 默认 0.0
# 来源：neoforge-21.1.235-sources.jar 下各实体类 + Attributes 默认基值
MOBS: Dict[str, Dict[str, object]] = {
    # 名称:        立场        攻击  护甲 韧性  生命
    "僵尸":        dict(cat="HOSTILE", atk=3.0,  armor=2.0, tough=0.0, hp=20.0),
    "骷髅":        dict(cat="HOSTILE", atk=2.0,  armor=0.0, tough=0.0, hp=20.0),
    "蜘蛛":        dict(cat="HOSTILE", atk=2.0,  armor=0.0, tough=0.0, hp=16.0),
    "洞穴蜘蛛":    dict(cat="HOSTILE", atk=2.0,  armor=0.0, tough=0.0, hp=12.0),
    "凋灵骷髅":    dict(cat="HOSTILE", atk=2.0,  armor=0.0, tough=0.0, hp=20.0),
    "末影人":      dict(cat="HOSTILE", atk=7.0,  armor=0.0, tough=0.0, hp=40.0),
    "猪灵":        dict(cat="HOSTILE", atk=5.0,  armor=0.0, tough=0.0, hp=16.0),
    "猪灵蛮兵":    dict(cat="HOSTILE", atk=7.0,  armor=0.0, tough=0.0, hp=50.0),
    "疃猪兽":      dict(cat="HOSTILE", atk=6.0,  armor=0.0, tough=0.0, hp=40.0),
    "劫掠兽":      dict(cat="HOSTILE", atk=12.0, armor=0.0, tough=0.0, hp=100.0),
    "卫道士":      dict(cat="HOSTILE", atk=5.0,  armor=0.0, tough=0.0, hp=24.0),
    "烈焰人":      dict(cat="HOSTILE", atk=6.0,  armor=0.0, tough=0.0, hp=20.0),
    "监守者":      dict(cat="HOSTILE", atk=30.0, armor=0.0, tough=0.0, hp=500.0),
    "凋灵":        dict(cat="HOSTILE", atk=2.0,  armor=4.0, tough=0.0, hp=300.0),
    "铁傀儡":      dict(cat="NEUTRAL", atk=15.0, armor=0.0, tough=0.0, hp=100.0),
    "北极熊":      dict(cat="NEUTRAL", atk=6.0,  armor=0.0, tough=0.0, hp=30.0),
    "狼":          dict(cat="NEUTRAL", atk=4.0,  armor=0.0, tough=0.0, hp=8.0),
    "牛":          dict(cat="PASSIVE", atk=2.0,  armor=0.0, tough=0.0, hp=10.0),
}

# 全套护甲（护甲值, 盔甲韧性）
# 来源：ArmorMaterials.java —— LEATHER(1/3/2/1) CHAIN(1/4/5/2) IRON(2/5/6/2)
#      DIAMOND(3/6/8/3, 韧性 2.0×4) NETHERITE(3/6/8/3, 韧性 3.0×4)
ARMOR_SETS: Dict[str, Tuple[float, float]] = {
    "裸装":       (0.0, 0.0),
    "皮革全套":   (7.0, 0.0),
    "锁链全套":   (12.0, 0.0),
    "铁全套":     (15.0, 0.0),
    "钻石全套":   (20.0, 8.0),
    "下界合金全套": (20.0, 12.0),
}

# 武器：玩家持有该武器时的 ATTACK_DAMAGE 属性终值
# 来源：SwordItem.createAttributes(tier, 3, …) + Tiers.attackDamageBonus + 玩家基值 1.0
WEAPONS: Dict[str, float] = {
    "空手": 1.0,
    "木剑": 4.0,     # 1 + (3 + 0)
    "石剑": 5.0,     # 1 + (3 + 1)
    "铁剑": 6.0,     # 1 + (3 + 2)
    "钻石剑": 7.0,   # 1 + (3 + 3)
    "下界合金剑": 8.0,  # 1 + (3 + 4)
}

# 卡牌点数：("rand2of", N) = max(两次 1dN)；("fixed", V) = 定值
# 来源：CardRegistry.init() 的 roller 与 maxRoll/minRoll
CARDS: Dict[str, Tuple[str, int]] = {
    "medium": ("rand2of", 3),
    "large": ("rand2of", 6),
    "epic": ("rand2of", 10),
    "meito": ("rand2of", 20),
    "shadow_strike": ("fixed", 3),
    "charge": ("fixed", 5),
    "full_power": ("fixed", 6),
    "bite": ("fixed", 3),
    "dragon_roar": ("fixed", 3),
    "defense_medium": ("rand2of", 3),
    "defense_large": ("rand2of", 6),
    "defense_epic": ("rand2of", 10),
}

# ⚠️⚠️ 已过期（2026-09-26 标注，本版未改）—— 下面的库侧常量仍停在**重标定前**的旧模型：
#   护甲项写死「统一除数 2」、PLAYER_BASE_DEFENSE 2.0 / PLAYER_TOUGHNESS_COEF 1.4 /
#   MOB_TOUGHNESS_COEF 1.125、BASE_ATTACK 敌对 5 / 中立 4、BASE_DEFENSE 敌对 2。
#   库 starengine_lib 自 2.0.0-SNAPSHOT.2 起已改为（并**删除**了 ARMOR_DIVISOR）：
#     playerDefense  = 4 + 护甲×0.30 + 0.85×韧性
#     mobDefenseInt  = 0 + 护甲×0.40 + 1.0×韧性   （两侧均无 20 上限，护甲取属性终值）
#     BASE_ATTACK: 敌对 4 / 中立 3 / 被动 0 / 友好 0 ; BASE_DEFENSE: 全 0
#   ⇒ **直接重跑本脚本会复现旧模型的曲线**。若要按现行口径重跑，必须先把下列常量与
#     player_defense / mob_defense_int 两个函数换成库现行值；重跑后 docs/balance/ 下的
#     历史报告需重新标注（那是旧模型的快照）。权威口径见 AGENTS.md「骰战闪避与防御规范」。
# ⚠️⚠️ 已过期（2026-09-26 标注，本版未改）—— 下面的库侧常量仍停在**重标定前**的旧模型：
#   护甲项写死「统一除数 2」、PLAYER_BASE_DEFENSE 2.0 / PLAYER_TOUGHNESS_COEF 1.4 /
#   MOB_TOUGHNESS_COEF 1.125、BASE_ATTACK 敌对 5 / 中立 4、BASE_DEFENSE 敌对 2。
#   库 starengine_lib 自 2.0.0-SNAPSHOT.2 起已改为（并**删除**了 ARMOR_DIVISOR）：
#     playerDefense  = 4 + 护甲×0.30 + 0.85×韧性
#     mobDefenseInt  = 0 + 护甲×0.40 + 1.0×韧性   （两侧均无 20 上限，护甲取属性终值）
#     BASE_ATTACK: 敌对 4 / 中立 3 / 被动 0 / 友好 0 ; BASE_DEFENSE: 全 0
#   ⇒ **直接重跑本脚本会复现旧模型的曲线**。若要按现行口径重跑，必须先把下列常量与
#     player_defense / mob_defense_int 两个函数换成库现行值；重跑后 docs/balance/ 下的
#     历史报告需重新标注（那是旧模型的快照）。权威口径见 AGENTS.md「骰战闪避与防御规范」。
# 库公式常量（CombatFormula）
PLAYER_BASE_DEFENSE = 2.0
PLAYER_TOUGHNESS_COEF = 1.4
MOB_TOUGHNESS_COEF = 1.125

# 库常量（TargetBattleStats）
BASE_ATTACK = {"HOSTILE": 5, "NEUTRAL": 4, "PASSIVE": 0, "FRIENDLY": 0}
BASE_DEFENSE = {"HOSTILE": 2, "NEUTRAL": 0, "PASSIVE": 0, "FRIENDLY": 0}

# 原版常量
SHARPNESS_PER_LEVEL_LINEAR = 1.5   # sharpness: base 1.0, per_level_above_first 0.5 ⇒ Lv5 = 1+4×0.5 = 3
PROTECTION_PER_LEVEL = 1.0         # protection: base 1.0, per_level_above_first 1.0 ⇒ Lv4 = 4 点/件
VANILLA_ARMOR_CAP = 20.0
# ⚠️ 原版下限是「护甲值 × 0.2」（= armor/5），不是「伤害 × 0.2」。
#    来源：CombatRules.getDamageAfterAbsorb L18 `Mth.clamp(armorValue - damage / f, armorValue * 0.2F, 20.0F)`。
#    推论：护甲为 0 时该下限也是 0 ⇒ 裸装玩家受满伤害（曾被误写为 damage×0.2，导致裸装被虚报 2.4% 减免）。
VANILLA_MIN_ARMOR_FRACTION = 0.2

# 模组常量
ENDER_DIE_RAIN_MULTIPLIER = 1.4     # EnderDiceHandler.RAIN_WATER_DAMAGE_MULTIPLIER

# 七咒之戒（实例配置 enigmaticlegacyplus-server.toml）
CURSE_ARMOR_DEBUFF = 0.30           # [sevenCurses] armorDebuff = 30  ⇒ ARMOR/ARMOR_TOUGHNESS ×0.70
CURSE_MONSTER_DAMAGE_DEBUFF = 0.40  # [sevenCurses] monsterDamageDebuff = 40 ⇒ 骰点+卡牌 ×0.60
CURSE_PAIN_MULTIPLIER = 2.00        # [sevenCurses] painMultiplier = 200（百分数）⇒ 受伤 ×2.0
CURSE_DIMNESS_CHARM_FACTOR = 0.60   # 再佩戴「晦暗护符」时护甲减益再 ×0.6（本仿真默认不佩戴）

# 配对采样种子：凡是「同一场景的前后对比」（如七咒 有/无戒指），前后两次必须用
# **各自全新的同一种子** 生成同一随机序列（抽点顺序一致），否则共用一条流会引入 MC 噪声，
# 让参数完全相同的退化档（如裸装：护甲 0×0.7 仍为 0）也显示出 ±0.0% 的假差异。
PAIR_SEED = 424242


# ============================================================================================
# 1. 库 / 骰战 公式
# ============================================================================================

def player_defense(armor: float, toughness: float) -> float:
    """CombatFormula.playerDefense —— 2 + 护甲÷2 + 1.4×韧性（无 20 上限）。"""
    return PLAYER_BASE_DEFENSE + armor / 2.0 + PLAYER_TOUGHNESS_COEF * toughness


def mob_defense_int(cat: str, armor: float, toughness: float) -> int:
    """CombatFormula.mobDefenseInt —— 起始项(敌对2/其余0) + 护甲÷2 + 1.125×韧性，向下取整。"""
    return int(math.floor(BASE_DEFENSE[cat] + armor / 2.0 + MOB_TOUGHNESS_COEF * toughness))


def dice_resolve(attack_power: float, defense_power: float) -> float:
    """DiceBattleResolver.resolve —— max(1, 攻击 − 防御)。"""
    return max(1.0, attack_power - defense_power)


def roll_card(card: str, rng: random.Random) -> int:
    kind, v = CARDS[card]
    if kind == "fixed":
        return v
    return max(rng.randint(1, v), rng.randint(1, v))


def card_mean(card: str) -> float:
    kind, v = CARDS[card]
    if kind == "fixed":
        return float(v)
    # E[max(两次 1dN)] = Σ k·((k/N)² − ((k−1)/N)²)
    return sum(k * ((k / v) ** 2 - ((k - 1) / v) ** 2) for k in range(1, v + 1))


def card_is_defense(card: str) -> bool:
    return card.startswith("defense_")


# ============================================================================================
# 2. 原版管线
# ============================================================================================

def vanilla_armor_absorb(dmg: float, armor: float, toughness: float) -> float:
    """
    CombatRules.getDamageAfterAbsorb L16-30

        f  = 2 + 韧性/4
        f1 = clamp(护甲 − 伤害/f, 护甲×0.2, 20)      ← 上下限都由**护甲**决定
        伤害 ×= 1 − f1/25

    注意：`20` 是原版护甲硬上限；`护甲×0.2` 使护甲 0 时完全无减免。
    """
    f = 2.0 + toughness / 4.0
    f1 = min(max(armor - dmg / f, armor * VANILLA_MIN_ARMOR_FRACTION), VANILLA_ARMOR_CAP)
    return dmg * (1.0 - f1 / 25.0)


def vanilla_magic_absorb(dmg: float, resistance_amp: int | None, prot_points: float) -> float:
    """LivingEntity.getDamageAfterMagicAbsorb —— 先抗性提升（整数 5%/级），再保护附魔（clamp 20）。"""
    if resistance_amp is not None:
        i = (resistance_amp + 1) * 5
        dmg = max(dmg * (25 - i) / 25.0, 0.0)
    if dmg <= 0.0:
        return 0.0
    p = min(max(prot_points, 0.0), 20.0)
    return dmg * (1.0 - p / 25.0)


def vanilla_player_hit(attack_attr: float, sharp_bonus: float, cooldown: float, is_crit: bool,
                       target_armor: float, target_tough: float,
                       target_prot: float = 0.0, target_resistance_amp: int | None = None,
                       target_absorption: float = 0.0) -> float:
    """原版玩家近战一次命中（Player.attack L1222 → LivingEntity.actuallyHurt L1785）。"""
    f = attack_attr * (0.2 + cooldown * cooldown * 0.8)   # 冷却缩放
    f1 = sharp_bonus * cooldown                            # 附魔加伤按同一冷却缩放
    dmg = f + f1
    if is_crit:
        dmg *= 1.5                                         # CriticalHitEvent 默认倍率
    dmg = vanilla_armor_absorb(dmg, target_armor, target_tough)
    dmg = vanilla_magic_absorb(dmg, target_resistance_amp, target_prot)
    return max(0.0, dmg - target_absorption)


def vanilla_mob_hit(mob_atk: float, player_armor: float, player_tough: float,
                    player_prot: float = 0.0, player_resistance_amp: int | None = None,
                    player_absorption: float = 0.0) -> float:
    """原版生物近战一次命中（Mob.doHurtTarget L1491 → LivingEntity.actuallyHurt）。"""
    dmg = vanilla_armor_absorb(mob_atk, player_armor, player_tough)
    dmg = vanilla_magic_absorb(dmg, player_resistance_amp, player_prot)
    return max(0.0, dmg - player_absorption)


def vanilla_post_armor_value(raw: float, armor: float, toughness: float,
                             resistance_amp: int | None = None, prot_points: float = 0.0) -> float:
    """返回 LivingDamageEvent.Pre 处的 getNewDamage()（= 护甲/抗性/保护之后、吸收之前）。"""
    return vanilla_magic_absorb(vanilla_armor_absorb(raw, armor, toughness), resistance_amp, prot_points)


# ============================================================================================
# 3. 骰战管线（1.21.1）
# ============================================================================================

def dice_player_to_mob(atk_attr: float, attack_mods: float, attack_cards: List[str],
                       mob_cat: str, mob_armor: float, mob_tough: float,
                       rng: random.Random,
                       curse_fourth: bool = False, full_power: bool = False,
                       mob_defense_roll: bool = True, victim_factor: float = 1.0,
                       attack_flat_extra: float = 0.0) -> Tuple[float, float]:
    """
    DiceCombatEvents.onLivingDamagePre —— 玩家→生物 骰战一次命中。
    返回 (最终伤害, 攻击点)；攻击点含骰点与卡牌（用于诊断）。
    """
    attack_power = atk_attr + attack_mods + attack_flat_extra
    base_dice = rng.randint(1, 6)                                   # rollCombatDie（均匀 d6）
    card_sum = sum(roll_card(c, rng) for c in attack_cards)
    bonus = float(base_dice + card_sum)
    if curse_fourth:
        bonus *= (1.0 - CURSE_MONSTER_DAMAGE_DEBUFF)                # applyCurseToDicePoints −40%
    attack_power += bonus
    if full_power:
        attack_power = math.ceil(attack_power * 1.5)                # 全力攻击 ×1.5（向上取整）
    if mob_cat is None:
        raise ValueError
    defense_dice = rng.randint(1, 6) if mob_defense_roll else 0     # 生物恒掷 d6（破绽时为 0）
    defense_power = mob_defense_int(mob_cat, mob_armor, mob_tough) + defense_dice
    dmg = dice_resolve(attack_power, defense_power)
    dmg *= victim_factor
    return dmg, attack_power


def dice_mob_to_player(mob_cat: str, mob_atk_attr: float, player_armor: float, player_tough: float,
                       rng: random.Random, player_has_dice: bool,
                       defense_cards: List[str] | None = None,
                       mob_weapon_bonus: float = 0.0,
                       player_prot: float = 0.0, player_resistance_amp: int | None = None,
                       victim_factor: float = 1.0) -> Tuple[float, float]:
    """
    DiceCombatEvents.resolveMobMeleeAttack —— 生物→玩家 骰战一次命中。
    返回 (最终伤害, 攻击点)。

    ⚠️ 关键结构：玩家侧的「原版护甲/抗性/保护」结果以 event.getNewDamage() 作为**加项**进入攻击点，
       同时 playerDefense 又含一次 护甲÷2 + 1.4×韧性 ⇒ 护甲被计入两次。
    """
    raw = mob_atk_attr + mob_weapon_bonus
    post = vanilla_post_armor_value(raw, player_armor, player_tough,
                                    player_resistance_amp, player_prot)
    defense_cards = defense_cards or []
    mob_roll = rng.randint(1, 6) if player_has_dice else 0
    mob_attack = BASE_ATTACK[mob_cat] + raw + post + mob_roll
    player_roll = rng.randint(1, 6) if player_has_dice else 0
    card_sum = sum(roll_card(c, rng) for c in defense_cards)
    player_def = player_defense(player_armor, player_tough) + player_roll + card_sum
    dmg = dice_resolve(mob_attack, player_def)
    dmg *= victim_factor
    return dmg, mob_attack


# ============================================================================================
# 4. 统计工具
# ============================================================================================

def mc_stats(sampler: Callable[[random.Random], float], n: int, rng: random.Random) -> Dict[str, float]:
    xs = [sampler(rng) for _ in range(n)]
    xs.sort()
    m = sum(xs) / n
    var = sum((x - m) ** 2 for x in xs) / n
    return {
        "mean": m,
        "sd": math.sqrt(var),
        "p05": xs[int(0.05 * n)],
        "p50": xs[int(0.50 * n)],
        "p95": xs[min(int(0.95 * n), n - 1)],
        "min": xs[0],
        "max": xs[-1],
    }


def ttk_mc(hp: float, sampler: Callable[[random.Random], float],
           rng: random.Random, trials: int = 4000, cap: int = 400) -> float:
    """击杀所需击数期望（不建模回血/无敌帧）。"""
    total = 0
    for _ in range(trials):
        acc = 0.0
        hits = 0
        while acc < hp and hits < cap:
            acc += sampler(rng)
            hits += 1
        total += hits
    return total / trials


def hits_to_die_mc(hp: float, sampler: Callable[[random.Random], float],
                   rng: random.Random, trials: int = 4000, cap: int = 400) -> float:
    """可承受击数期望（不建模回血）。"""
    return ttk_mc(hp, sampler, rng, trials, cap)


# ============================================================================================
# 5. 实验
# ============================================================================================

def exp1_player_output(n: int, ttk_trials: int, rng: random.Random) -> Dict[str, object]:
    """实验一：玩家→生物 每击期望伤害 与 击杀所需击数（原版 vs 骰战）。"""
    mobs = ["僵尸", "骷髅", "蜘蛛", "凋灵骷髅", "末影人", "猪灵蛮兵", "劫掠兽", "监守者", "铁傀儡", "牛"]
    rows = []
    for mob in mobs:
        m = MOBS[mob]
        hp, m_armor, m_tough, cat = m["hp"], m["armor"], m["tough"], m["cat"]
        for wname, atk in WEAPONS.items():
            # ---- 原版 ----
            van_plain = vanilla_player_hit(atk, 0.0, 1.0, False, m_armor, m_tough)
            van_sharp = vanilla_player_hit(atk, 3.0, 1.0, False, m_armor, m_tough)      # 锋利 V
            # ---- 骰战 ----
            dice_bare = mc_stats(lambda r: dice_player_to_mob(atk, 0.0, [], cat, m_armor, m_tough, r)[0], n, rng)
            dice_bare_ttk = ttk_mc(hp, lambda r: dice_player_to_mob(atk, 0.0, [], cat, m_armor, m_tough, r)[0],
                                   rng, ttk_trials)
            dice_epic3 = mc_stats(lambda r: dice_player_to_mob(atk, 0.0, ["epic", "epic", "epic"],
                                                               cat, m_armor, m_tough, r)[0], n, rng)
            dice_epic3_ttk = ttk_mc(hp, lambda r: dice_player_to_mob(atk, 0.0, ["epic", "epic", "epic"],
                                                                    cat, m_armor, m_tough, r)[0], rng, ttk_trials)
            rows.append({
                "mob": mob, "hp": hp, "mob_armor": m_armor, "mob_tough": m_tough, "cat": cat,
                "weapon": wname, "atk_attr": atk,
                "vanilla": van_plain, "vanilla_sharp5": van_sharp,
                "vanilla_ttk": math.ceil(hp / van_plain) if van_plain > 0 else None,
                "vanilla_sharp5_ttk": math.ceil(hp / van_sharp) if van_sharp > 0 else None,
                "dice_bare_mean": dice_bare["mean"], "dice_bare_p05": dice_bare["p05"],
                "dice_bare_p95": dice_bare["p95"], "dice_bare_ttk": dice_bare_ttk,
                "dice_epic3_mean": dice_epic3["mean"], "dice_epic3_ttk": dice_epic3_ttk,
            })
    return {"rows": rows}


def exp2_player_intake(n: int, ttk_trials: int, rng: random.Random) -> Dict[str, object]:
    """实验二：生物→玩家 每击期望伤害 与 可承受击数（20 生命、不建模回血）。"""
    mobs = ["僵尸", "骷髅", "末影人", "猪灵蛮兵", "劫掠兽", "监守者", "铁傀儡", "北极熊"]
    player_hp = 20.0
    rows = []
    for armor_name, (p_armor, p_tough) in ARMOR_SETS.items():
        for mob in mobs:
            m = MOBS[mob]
            cat, m_atk = m["cat"], m["atk"]
            van = vanilla_mob_hit(m_atk, p_armor, p_tough)
            d_no = mc_stats(lambda r: dice_mob_to_player(cat, m_atk, p_armor, p_tough, r, False)[0], n, rng)
            d_die = mc_stats(lambda r: dice_mob_to_player(cat, m_atk, p_armor, p_tough, r, True)[0], n, rng)
            d_die_card = mc_stats(
                lambda r: dice_mob_to_player(cat, m_atk, p_armor, p_tough, r, True,
                                             ["defense_epic", "defense_epic", "defense_epic"])[0], n, rng)
            rows.append({
                "armor_set": armor_name, "player_armor": p_armor, "player_tough": p_tough,
                "mob": mob, "cat": cat, "mob_atk": m_atk,
                "vanilla": van,
                "vanilla_hits": (player_hp / van) if van > 0 else float("inf"),
                "dice_nodice_mean": d_no["mean"],
                "dice_dice_mean": d_die["mean"],
                "dice_dice_p95": d_die["p95"],
                "dice_dice_cards_mean": d_die_card["mean"],
                "dice_nodice_hits": hits_to_die_mc(player_hp,
                                                   lambda r: dice_mob_to_player(cat, m_atk, p_armor, p_tough, r, False)[0],
                                                   rng, ttk_trials),
                "dice_dice_hits": hits_to_die_mc(player_hp,
                                                 lambda r: dice_mob_to_player(cat, m_atk, p_armor, p_tough, r, True)[0],
                                                 rng, ttk_trials),
            })
    return {"rows": rows, "player_hp": player_hp}


def exp3_modifier_matrix(n: int, rng: random.Random) -> Dict[str, object]:
    """
    实验三：原版伤害修饰器对骰战的传导矩阵。

    承伤取**两组基准**（避免只用一组时结果被骰战的 1 点下限"吃掉"而看不出差异）：
      · 强基准「监守者(攻30) → 钻石全套(甲20/韧8)」—— 不触底；
      · 弱基准「僵尸(攻3) → 铁全套(甲15/韧0)」—— 靠近下限。
    输出基准：「钻石剑玩家(攻7) → 僵尸」。
    """
    Z = MOBS["僵尸"]
    W = MOBS["监守者"]
    p_armor, p_tough = ARMOR_SETS["钻石全套"]
    w_armor, w_tough = ARMOR_SETS["铁全套"]
    base_atk = WEAPONS["钻石剑"]

    # ---------------- 承伤 ----------------
    def dice_in_seed(mob: Dict, armor: float, tough: float, **kw) -> float:
        r = random.Random(31415926)
        return sum(dice_mob_to_player(mob["cat"], mob["atk"], armor, tough, r, True, None,
                                      player_prot=kw.get("prot", 0.0),
                                      player_resistance_amp=kw.get("res", None))[0]
                   for _ in range(n)) / n

    weak_van0 = vanilla_mob_hit(Z["atk"], w_armor, w_tough)
    weak_dic0 = dice_in_seed(Z, w_armor, w_tough)
    strong_van0 = vanilla_mob_hit(W["atk"], p_armor, p_tough)
    strong_dic0 = dice_in_seed(W, p_armor, p_tough)

    rows = []

    def add(name: str, note: str, **kw) -> None:
        w_van = vanilla_mob_hit(Z["atk"], kw.get("w_armor", w_armor), kw.get("w_tough", w_tough),
                                player_prot=kw.get("prot", 0.0),
                                player_resistance_amp=kw.get("res", None),
                                player_absorption=kw.get("absorb", 0.0))
        w_dic = dice_in_seed(Z, kw.get("w_armor", w_armor), kw.get("w_tough", w_tough), **kw)
        if kw.get("absorb"):
            w_dic = max(0.0, w_dic - kw["absorb"])
        s_van = vanilla_mob_hit(W["atk"], kw.get("p_armor", p_armor), kw.get("p_tough", p_tough),
                                player_prot=kw.get("prot", 0.0),
                                player_resistance_amp=kw.get("res", None),
                                player_absorption=kw.get("absorb", 0.0))
        s_dic = dice_in_seed(W, kw.get("p_armor", p_armor), kw.get("p_tough", p_tough), **kw)
        if kw.get("absorb"):
            s_dic = max(0.0, s_dic - kw["absorb"])
        rows.append({
            "modifier": name, "note": note,
            "weak_vanilla": w_van, "weak_dice": w_dic,
            "weak_vanilla_delta_pct": (w_van / weak_van0 - 1) * 100 if weak_van0 else 0.0,
            "weak_dice_delta_pct": (w_dic / weak_dic0 - 1) * 100 if weak_dic0 else 0.0,
            "strong_vanilla": s_van, "strong_dice": s_dic,
            "strong_vanilla_delta_pct": (s_van / strong_van0 - 1) * 100 if strong_van0 else 0.0,
            "strong_dice_delta_pct": (s_dic / strong_dic0 - 1) * 100 if strong_dic0 else 0.0,
        })

    add("基准（无修饰器）", "僵尸→铁全套 / 监守者→钻石全套；骰战=玩家装备骰子、无防御牌")
    add("保护 IV 全套（+16 点）", "原版作用于魔法减免阶段；骰战仅经 getNewDamage() 泄漏",
        prot=16.0)
    add("抗性提升 II（40%）", "原版作用于魔法减免阶段；骰战仅经 getNewDamage() 泄漏",
        res=1)
    add("吸收 10（黄心）", "两者都在伤害落地后扣黄心（骰战 Pre 之后）", absorb=10.0)
    add("护甲 +5（去上限后 25）", "原版公式内 clamp 到 20；骰战无上限",
        w_armor=w_armor + 5.0, p_armor=p_armor + 5.0)
    add("盔甲韧性 +4（共 12）", "原版系数 1/4；骰战系数 1.4（生物侧 1.125）",
        w_tough=w_tough + 4.0, p_tough=p_tough + 4.0)

    # ---------------- 输出 ----------------
    def dice_out_seed(**kw) -> float:
        r = random.Random(2718281)
        return sum(dice_player_to_mob(kw.get("atk", base_atk), 0.0, kw.get("cards", []),
                                      Z["cat"], Z["armor"], Z["tough"], r,
                                      full_power=kw.get("fp", False))[0]
                   for _ in range(n)) / n

    Z_armor = Z["armor"]
    van_out0 = vanilla_player_hit(base_atk, 0.0, 1.0, False, Z_armor, Z["tough"])
    dice_out0 = dice_out_seed()

    rows_out = []

    def add_out(name: str, note: str, van: float, dic: float) -> None:
        rows_out.append({
            "modifier": name, "note": note, "vanilla": van, "dice": dic,
            "vanilla_delta_pct": (van / van_out0 - 1) * 100 if van_out0 else 0.0,
            "dice_delta_pct": (dic / dice_out0 - 1) * 100 if dice_out0 else 0.0,
        })

    add_out("基准（无修饰器）", "钻石剑玩家→僵尸；骰战=装备骰子、无卡牌", van_out0, dice_out0)
    add_out("锋利 V（+3 附魔加伤）", "骰战取 ATTACK_DAMAGE 属性值，不含附魔加伤 ⇒ 期望 0 影响",
            vanilla_player_hit(base_atk, 3.0, 1.0, False, Z_armor, Z["tough"]), dice_out_seed())
    add_out("攻击冷却 50%（0.2+0.5²×0.8）", "骰战完全不读 getAttackStrengthScale ⇒ 期望 0 影响",
            vanilla_player_hit(base_atk, 0.0, 0.5, False, Z_armor, Z["tough"]), dice_out_seed())
    add_out("暴击（×1.5）", "骰战不读暴击 ⇒ 期望 0 影响",
            vanilla_player_hit(base_atk, 0.0, 1.0, True, Z_armor, Z["tough"]), dice_out_seed())
    add_out("全力攻击（×1.5，向上取整）", "本模组自有卡牌；原版无对应项（该列沿用基准值）",
            van_out0, dice_out_seed(fp=True))

    return {
        "intake_rows": rows, "output_rows": rows_out,
        "baseline": {
            "weak_vanilla": weak_van0, "weak_dice": weak_dic0,
            "strong_vanilla": strong_van0, "strong_dice": strong_dic0,
            "output_vanilla": van_out0, "output_dice": dice_out0,
        },
    }


def exp4_curse(n: int, rng: random.Random) -> Dict[str, object]:
    """实验四：七咒之戒（第三诅咒 护甲/韧性 ×0.70；第四诅咒 骰点+卡牌 ×0.60）。"""
    k = 1.0 - CURSE_ARMOR_DEBUFF
    mobs = ["僵尸", "末影人", "劫掠兽", "监守者", "铁傀儡"]
    rows = []
    for armor_name, (a, t) in ARMOR_SETS.items():
        a2, t2 = a * k, t * k
        for mob in mobs:
            m = MOBS[mob]
            cat, m_atk = m["cat"], m["atk"]
            van_no = vanilla_mob_hit(m_atk, a, t)
            van_yes = vanilla_mob_hit(m_atk, a2, t2)
            # 配对采样：前后各起一条同种子序列，抽点顺序一致 ⇒ Δ 只反映参数变化
            d_no = mc_stats(lambda r: dice_mob_to_player(cat, m_atk, a, t, r, True)[0], n, random.Random(PAIR_SEED))
            d_yes = mc_stats(lambda r: dice_mob_to_player(cat, m_atk, a2, t2, r, True)[0], n, random.Random(PAIR_SEED))
            rows.append({
                "armor_set": armor_name, "mob": mob,
                "armor_before": a, "tough_before": t, "armor_after": a2, "tough_after": t2,
                "player_def_before": player_defense(a, t), "player_def_after": player_defense(a2, t2),
                "vanilla_before": van_no, "vanilla_after": van_yes,
                "vanilla_delta_pct": (van_yes / van_no - 1) * 100 if van_no > 0 else float("nan"),
                "dice_before": d_no["mean"], "dice_after": d_yes["mean"],
                "dice_delta_pct": (d_yes["mean"] / d_no["mean"] - 1) * 100 if d_no["mean"] > 0 else float("nan"),
            })

    # 输出方向：第四诅咒
    out_rows = []
    Z = MOBS["僵尸"]
    for wname in ("铁剑", "钻石剑", "下界合金剑"):
        atk = WEAPONS[wname]
        for cards, clabel in (([], "无卡牌"), (["epic"], "1×特大攻击牌"), (["epic", "epic", "epic"], "3×特大攻击牌")):
            base = mc_stats(lambda r: dice_player_to_mob(atk, 0.0, cards, Z["cat"], Z["armor"], Z["tough"], r)[0],
                            n, random.Random(PAIR_SEED))
            cursed = mc_stats(lambda r: dice_player_to_mob(atk, 0.0, cards, Z["cat"], Z["armor"], Z["tough"], r,
                                                          curse_fourth=True)[0], n, random.Random(PAIR_SEED))
            out_rows.append({
                "weapon": wname, "cards": clabel,
                "dice_before": base["mean"], "dice_after": cursed["mean"],
                "delta_pct": (cursed["mean"] / base["mean"] - 1) * 100 if base["mean"] > 0 else 0.0,
            })

    # 第一诅咒（×2 受伤）：分别看它在两条路径上的传导
    pain_rows = []
    for armor_name in ("裸装", "铁全套", "钻石全套"):
        a, t = ARMOR_SETS[armor_name]
        for mob in ("僵尸", "监守者"):
            m = MOBS[mob]
            cat, m_atk = m["cat"], m["atk"]
            raw2 = m_atk * CURSE_PAIN_MULTIPLIER
            van_no = vanilla_mob_hit(m_atk, a, t)
            van_yes = vanilla_mob_hit(raw2, a, t)
            d_no = mc_stats(lambda r: dice_mob_to_player(cat, m_atk, a, t, r, True)[0], n, random.Random(PAIR_SEED))
            # 骰战：×2 只经 getNewDamage()（post）进入攻击点；最终伤害不再乘 2
            r2 = random.Random(PAIR_SEED)
            vals = []
            for _ in range(n):
                post = vanilla_post_armor_value(raw2, a, t)
                mob_roll = r2.randint(1, 6)
                p_roll = r2.randint(1, 6)
                atk_p = BASE_ATTACK[cat] + raw2 + post + mob_roll
                def_p = player_defense(a, t) + p_roll
                vals.append(max(1.0, atk_p - def_p))
            d_yes = sum(vals) / len(vals)
            pain_rows.append({
                "armor_set": armor_name, "mob": mob,
                "vanilla_before": van_no, "vanilla_after": van_yes,
                "vanilla_ratio": van_yes / van_no if van_no > 0 else float("nan"),
                "dice_before": d_no["mean"], "dice_after": d_yes,
                "dice_ratio": d_yes / d_no["mean"] if d_no["mean"] > 0 else float("nan"),
            })

    return {"armor_rows": rows, "output_rows": out_rows, "pain_rows": pain_rows,
            "consts": {"armor_debuff": CURSE_ARMOR_DEBUFF, "multiplied_total": -CURSE_ARMOR_DEBUFF,
                       "monster_damage_debuff": CURSE_MONSTER_DAMAGE_DEBUFF,
                       "pain_multiplier": CURSE_PAIN_MULTIPLIER}}


def exp5_dice_roll_value(n: int, rng: random.Random) -> Dict[str, object]:
    """附：卡牌点数与骰点的统计指纹（用于校验均值口径）。"""
    out = {"cards": {}, "d6": {"mean": 3.5}}
    for c in CARDS:
        kind, v = CARDS[c]
        if kind == "rand2of":
            xs = [max(rng.randint(1, v), rng.randint(1, v)) for _ in range(n)]
        else:
            xs = [v] * n
        out["cards"][c] = {"type": kind, "param": v, "analytic_mean": card_mean(c),
                           "mc_mean": sum(xs) / len(xs), "max": max(xs), "min": min(xs)}
    return out


# ============================================================================================
# 6. 主入口
# ============================================================================================

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="dice-combat-simulation.json")
    ap.add_argument("--n", type=int, default=50000, help="每个统计单元的蒙特卡洛样本数")
    ap.add_argument("--ttk", type=int, default=4000, help="TTK/存活击数模拟的重复次数")
    ap.add_argument("--seed", type=int, default=20260926)
    args = ap.parse_args()

    rng = random.Random(args.seed)
    result = {
        "meta": {
            "mc_version": "1.21.1",
            "neoforge": "21.1.235",
            "n_samples": args.n,
            "ttk_trials": args.ttk,
            "seed": args.seed,
            "mod_constants": {
                "PLAYER_BASE_DEFENSE": PLAYER_BASE_DEFENSE,
                "PLAYER_TOUGHNESS_COEF": PLAYER_TOUGHNESS_COEF,
                "MOB_TOUGHNESS_COEF": MOB_TOUGHNESS_COEF,
                "BASE_ATTACK": BASE_ATTACK,
                "BASE_DEFENSE": BASE_DEFENSE,
                "MIN_DAMAGE": 1.0,
                "ENDER_DIE_RAIN_MULTIPLIER": ENDER_DIE_RAIN_MULTIPLIER,
            },
            "curse_constants": {
                "armor_debuff": CURSE_ARMOR_DEBUFF,
                "monster_damage_debuff": CURSE_MONSTER_DAMAGE_DEBUFF,
                "pain_multiplier": CURSE_PAIN_MULTIPLIER,
            },
        },
        "card_fingerprint": exp5_dice_roll_value(args.n, rng),
        "armor_sets": {k: {"armor": v[0], "toughness": v[1]} for k, v in ARMOR_SETS.items()},
        "weapons": WEAPONS,
        "mobs": MOBS,
        "exp1_player_output": exp1_player_output(args.n, args.ttk, rng),
        "exp2_player_intake": exp2_player_intake(args.n, args.ttk, rng),
        "exp3_modifier_matrix": exp3_modifier_matrix(args.n, rng),
        "exp4_curse": exp4_curse(args.n, rng),
    }
    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print(f"[sim] written {args.out}")
    print(f"[sim] exp1 rows={len(result['exp1_player_output']['rows'])} "
          f"exp2 rows={len(result['exp2_player_intake']['rows'])} "
          f"exp3 rows={len(result['exp3_modifier_matrix']['intake_rows']) + len(result['exp3_modifier_matrix']['output_rows'])} "
          f"exp4 rows={len(result['exp4_curse']['armor_rows']) + len(result['exp4_curse']['output_rows']) + len(result['exp4_curse']['pain_rows'])}")


if __name__ == "__main__":
    main()
