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

**判据只写一遍**: 01-07 全部落在纯函数 `evaluate_doc(tag, cfg, doc)` 里 —— 入参只有两线差异面
与一份源码 `Doc`,不读磁盘、不碰版本库;正向断言(01-07,跑工作树文件)与负向对照(08,跑内嵌
修复前夹具)都调同一个函数,不存在第二份判据。

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
  08 **负向对照(内嵌夹具,不读版本库)**: 把**同一组判据**(01-07)跑在**内嵌的修复前代码夹具**
      上必须判红 —— 夹具红不了就说明判据识别不出「未修复形态」(假绿)。另外要求红点里
      **必须包含 01**,否则「夹具本身残缺 ⇒ 任何判据都红」也会让本条空过。
      夹具 = `d91fa390` 两线 `DiceCombatEvents.java` 的最小区段(见 `OLD_NEO_SNIPPET` /
      `OLD_FABRIC_SNIPPET` 常量上方的来源与截取范围),模块级常量内嵌,无需外部输入。
      ⚠️ 旧实现用 `git show HEAD:<file>` 取「旧文本」,而修复与脚本同属一个提交 ⇒ HEAD 文本已
      无崩溃读点 ⇒ 该断言在任何干净检出(含 CI、浅克隆检出)上必红。本版已彻底删除对 git 的
      依赖(脚本内不再调用任何版本库命令)。
  09 行尾: 工作树必须是**纯 LF** —— CR 字节数为 0(本仓索引与工作树都以 LF 为准)。
      出现任何 CR(含 CRLF)即判 FAIL,计入 RESULT;不再有软检查通道。

退出码: 全绿 0,任一硬断言失败 1。

用法:
    python scripts/verify/verify_dice_combat_attrless_guard_p1.py
    python scripts/verify/verify_dice_combat_attrless_guard_p1.py --root <repo root>

纯标准库。
"""

from __future__ import annotations

import argparse
import re
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


# ─────────────────── 08 负向对照夹具(修复前的真实代码,内嵌) ───────────────────
# 来源: 提交 `d91fa390`(`fix(event-choice): 二选一界面退出强制触发首个事件 …`)——
#       即本批 attrless 修复的**父提交**,两线 `DiceCombatEvents.java` 仍是**未修复形态**。
# 只截取「够触发判据」的最小区段(逐字节照抄,未改一个字符):
#   neo    `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java`
#          L1033-L1084 `resolveMobMeleeAttack`(含 `magicAbsorbFactor` 减免段、对 `enchantedMobAttack`
#          的调用)+ L1117-L1124 `enchantedMobAttack`(**无守卫的** ATTACK_DAMAGE 读点)
#          + L610 玩家侧 `player.getAttributeValue(ATTACK_DAMAGE)` 单行(06 判据的反面基准)。
#   fabric `fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/combat/DiceCombatEvents.java`
#          L986-L1038 `resolveMobMeleeAttack`(**无守卫的** ATTACK_DAMAGE 读点就地内联在 mobAttack
#          的加项里)+ L566 玩家侧读点单行。
# 复核方式(只读,不参与判据):
#   git show d91fa390:<上面两处路径> | sed -n '1033,1084p;1117,1124p;610p'
#   git show d91fa390:<fabric 路径>       | sed -n '986,1038p;566p'
# 判据文本匹配前照旧先过 `strip_comments`,注释里的旧写法既骗不过也坑不了断言。
OLD_NEO_SNIPPET = """
    private static void resolveMobMeleeAttack(Mob mob, Player target, LivingDamageEvent.Pre event,
                                              DamageSource source, double victimFactor) {
        if (mob.level().isClientSide() || target.level().isClientSide()) return;
        // 仅近战:直接伤害实体必须就是生物本身(弓箭/三叉戟/药水等投掷物的 directEntity 不是 Mob,
        // 已在入口分流处被排除;此处再排除「生物发射但 directEntity 仍指向本身」的少数特殊攻击)。
        if (source.getDirectEntity() != mob) return;

        // 对骰成立条件:玩家装备骰子。未装备 ⇒ 双方均不掷,退化为按基础攻防值直接结算。
        boolean opposed = hasDice(target);
        int mobRoll = 0;
        int playerRoll = 0;
        int defenseCardSum = 0;
        if (opposed) {
            mobRoll = rollDice(6);
            playerRoll = rollCombatDie(target);
            var targetCurios = CuriosApi.getCuriosInventory(target);
            if (targetCurios.isPresent()) {
                var diceResult = targetCurios.get().findFirstCurio(DiceCurioItem::isDiceItem);
                if (diceResult.isPresent()) {
                    ItemStack dice = diceResult.get().stack();
                    WeaponEnhancement enh = dice.getOrDefault(ModDataComponents.WEAPON_ENHANCEMENT.get(), null);
                    if (enh != null) {
                        defenseCardSum = sumEquippedDefenseCardPoints(target, dice, enh);
                    }
                }
            }
        }

        // 生物武器附魔:按原版 Mob#doHurtTarget —— dmg = modifyDamage(level, weaponItem, target, damageSource, dmg)
        // (**替换式**:返回的已是「属性值 + 附魔」的合成值,而非增量)。
        double mobAttack = com.merlinkitsune.starengine.combat.TargetBattleStats.baseAttack(mob)
                + enchantedMobAttack(mob, target, source)
                + mobRoll;
        double playerDefense = com.merlinkitsune.starengine.combat.CombatFormula.playerDefense(
                target.getArmorValue(), target.getAttributeValue(Attributes.ARMOR_TOUGHNESS))
                + playerRoll + defenseCardSum;

        float finalDmg = com.merlinkitsune.starengine.combat.DiceBattleResolver
                .resolve(mobAttack, playerDefense);
        // 受击方(玩家)「抗性提升 + 保护附魔」乘算因子 —— 与「玩家→生物」路径**对称**应用
        // (2026-09-26 裁决①:两条路径必须用同一口径,不得一边丢弃、一边泄漏)。
        finalDmg *= com.merlinkitsune.starengine.combat.VanillaMitigation.magicAbsorbFactor(
                resistanceAmplifierOf(target), protectionPointsOf(target, source));
        finalDmg *= (float) victimFactor;
        event.setNewDamage(finalDmg);
        // 跳数字:与玩家攻击同一出口(登记组别,由 DamageNumberAggregator 在管线终值上取值)
        DamageNumberAggregator.tag(target, source, DamageNumberAggregator.Group.ATTACK);
        // 防御牌耐久:每次受到骰战攻击,防御方装备的所有防御牌各扣 1 点(仅在对骰成立时)
        if (opposed) {
            consumeAllDefenseCardDurability(target);
        }
    }

    private static double enchantedMobAttack(Mob mob, Entity target, DamageSource source) {
        float base = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return base;
        }
        return net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDamage(
                level, mob.getWeaponItem(), target, source, base);
    }

        double rawAttackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
"""

OLD_FABRIC_SNIPPET = """
    private static void resolveMobMeleeAttack(Mob mob, Player target, LivingDamageEvent event,
                                              DamageSource source, double victimFactor) {
        if (mob.level().isClientSide() || target.level().isClientSide()) return;
        // 仅近战:直接伤害实体必须就是生物本身(弓箭/三叉戟/药水等投掷物的 directEntity 不是 Mob,
        // 已在入口分流处被排除;此处再排除「生物发射但 directEntity 仍指向本身」的少数特殊攻击)。
        if (source.getDirectEntity() != mob) return;

        // 对骰成立条件:玩家装备骰子。未装备 ⇒ 双方均不掷,退化为按基础攻防值直接结算。
        boolean opposed = hasDice(target);
        int mobRoll = 0;
        int playerRoll = 0;
        int defenseCardSum = 0;
        if (opposed) {
            mobRoll = rollDice(6);
            playerRoll = rollCombatDie(target);
            var targetCurios = CuriosApi.getCuriosInventory(target);
            if (targetCurios.isPresent()) {
                var diceResult = targetCurios.get().findFirstCurio(DiceCurioItem::isDiceItem);
                if (diceResult.isPresent()) {
                    ItemStack dice = diceResult.get().stack();
                    WeaponEnhancement enh = ModDataComponents.WEAPON_ENHANCEMENT.getOrDefault(dice, null);
                    if (enh != null) {
                        defenseCardSum = sumEquippedDefenseCardPoints(target, dice, enh);
                    }
                }
            }
        }

        // 生物武器附魔:按原版 Mob#doHurtTarget —— f += getDamageBonus(getMainHandItem(), 目标 MobType)
        // (1.20.1 是**绝对量**加法,与本侧玩家路径同款)。
        double mobAttack = com.merlinkitsune.starengine.combat.TargetBattleStats.baseAttack(mob)
                + mob.getAttributeValue(Attributes.ATTACK_DAMAGE)
                + enchantDamageBonus(mob, target)
                + mobRoll;
        double playerDefense = com.merlinkitsune.starengine.combat.CombatFormula.playerDefense(
                target.getArmorValue(), target.getAttributeValue(Attributes.ARMOR_TOUGHNESS))
                + playerRoll + defenseCardSum;

        float finalDmg = com.merlinkitsune.starengine.combat.DiceBattleResolver
                .resolve(mobAttack, playerDefense);
        // 受击方(玩家)「抗性提升 + 保护附魔」乘算因子 —— 与「玩家→生物」路径**对称**应用
        // (2026-09-26 裁决①:两条路径必须用同一口径,不得一边丢弃、一边泄漏)。
        finalDmg *= com.merlinkitsune.starengine.combat.VanillaMitigation.magicAbsorbFactor(
                resistanceAmplifierOf(target), protectionPointsOf(target, source));
        finalDmg *= (float) victimFactor;
        event.setAmount(finalDmg);
        // 跳数字:与玩家攻击同一出口(登记组别,由 DamageNumberAggregator 在管线终值上取值)
        DamageNumberAggregator.tag(target, source, DamageNumberAggregator.Group.ATTACK);
        // 防御牌耐久:每次受到骰战攻击,防御方装备的所有防御牌各扣 1 点(仅在对骰成立时)
        if (opposed) {
            consumeAllDefenseCardDurability(target);
        }
    }

        double rawAttackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
"""

OLD_SNIPPETS: dict[str, str] = {
    "neo-26.1.2": OLD_NEO_SNIPPET,
    "fabric-1.20.1": OLD_FABRIC_SNIPPET,
}


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


# ────────────────────────── assertions (纯函数判据) ──────────────────────────
def evaluate_doc(
    tag: str, cfg: dict[str, str], doc: Doc
) -> tuple[list[tuple[str, bool, str]], list[tuple[str, str]]]:
    """跑 01-07 判据;入参只是一份源码 `Doc`(不读盘、不碰版本库)。

    返回值 `results` 中每条以 `f"{tag}-"` 为前缀,`ok=True` 即该条判据在**这份文本**上成立;
    01-07 全绿 ⇔ 「这段文本属于已修复形态」。08 负向对照把修复前的夹具喂进来,要求它不绿。
    """
    results: list[tuple[str, bool, str]] = []
    warnings: list[tuple[str, str]] = []

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

    return results, warnings


def evaluate(tag: str, cfg: dict[str, str], root: Path) -> tuple[list[tuple[str, bool, str]], list[tuple[str, str]]]:
    """正向断言: 读工作树文件,过 01-07 判据(纯函数),再补 09 行尾检查。"""
    pkg = root / cfg["dir"]
    line_root = pkg / SRC_PKG
    path = pkg / COMBAT_REL
    doc = Doc.from_path(path, line_root)

    results, warnings = evaluate_doc(tag, cfg, doc)

    # ── 09 行尾(硬断言): 本仓以 LF 为准,工作树不得出现任何 CR ──────────────
    crlf = doc.raw.count(b"\r\n")
    cr = doc.raw.count(b"\r")
    bare_cr = cr - crlf
    results.append(
        (
            f"{tag}-09 line endings: LF only (crlf=0, bare_cr=0)",
            cr == 0,
            f"LF-only baseline: crlf={crlf}, bare_cr={bare_cr}, cr_bytes={cr} "
            f"(expected cr_bytes=0; index and worktree are both LF in this repo)",
        )
    )

    # 软检查通道保留(当前没有软检查): 09 已从 WARN 升为硬断言。
    return results, warnings


def negative_control(results: list[tuple[str, bool, str]]) -> None:
    """08: 同一组判据(01-07)跑在**内嵌的修复前夹具**上,必须判红,且必须红在 01。

    不再读取任何版本库历史 ⇒ 在浅克隆 / 无 `.git` 历史的 CI 检出上结论一致。
    """
    for tag, cfg in LINES.items():
        fixture = OLD_SNIPPETS[tag]
        odoc = Doc(fixture, f"fixture@d91fa390:{cfg['dir']}")
        sub_results, _ = evaluate_doc(tag, cfg, odoc)
        prefix = f"{tag}-"
        failing = [name[len(prefix) :] if name.startswith(prefix) else name for name, ok, _ in sub_results if not ok]
        green = all(ok for _, ok, _ in sub_results)
        crash_detector_red = any(
            name.startswith(f"{tag}-01 ") and not ok for name, ok, _ in sub_results
        )
        ok = (not green) and crash_detector_red
        detail = (
            f"fixture=d91fa390:{cfg['dir']} ({len(odoc.lines)} lines); "
            f"verdict on fixture={'GREEN (gate cannot be contrasted)' if green else 'RED (expected)'}; "
            f"failing sub-assertions={failing or '[none]'}; "
            f"01 crash-read detector red={crash_detector_red}"
        )
        if green:
            detail += " -> fixture is the FIXED form: criterion 01 failed to detect the un-fixed form (false green)"
        elif not crash_detector_red:
            detail += " -> fixture is red for other reasons, criterion 01 unproven"
        results.append((f"{tag}-08 negative control (embedded pre-fix fixture must FAIL)", ok, detail))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="attrless guard gate for DiceCombatEvents (neoforge-26.1.2 + fabric-1.20.1)"
    )
    parser.add_argument(
        "--root",
        default=str(REPO_ROOT),
        help="repo root holding both neoforge-26.1.2/ and fabric-1.20.1/ (default: repo of this script)",
    )
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
    negative_control(results)

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
