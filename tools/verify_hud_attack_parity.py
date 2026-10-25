#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_hud_attack_parity.py —— HUD 攻击力「全量显示」口径守门
=============================================================

对应 2026-10-25 用户裁决:「HUD 口径统一 —— 所有攻击力都要加入显示, 除手持武器本身
与骰战中的随机攻击力数值加成」。

背景(为什么需要这个门)
----------------------
模组的真实战斗永远在服务端结算, 但攻击修饰器链上原本有 23(neo)/24(forge) 处
「if (ctx.attacker.level().isClientSide()) return ap;」模板守卫(逐批复制粘贴而来),
导致客户端 CombatPowerHud 求值时整条链被跳过 -> HUD 数字只含 10 块修饰器,
电流剑等 13 块被静默吞掉。修复不是逐处删守卫(会丢掉「未来非显示用途的客户端求值
仍应拦下」的语义), 而是引入显示口径标记 DiceCombatContext.displaySnapshot:
客户端只可能为了显示而求值 -> 标记放行; 真实战斗在服务端构建上下文, 标记恒 false。

检查项
------
H1 不得再有裸客户端守卫
   H1-a DiceCombatModifiers.java 中 isClientSide()) return ap; 计数为 0
H2 客户端守卫统一走 skipOnClient
   H2-a 存在 private static boolean skipOnClient(DiceCombatContext ctx)
   H2-b 方法体必须是 return ctx.attacker.level().isClientSide() && !ctx.displaySnapshot;
        (语义: 只有「客户端 且 非显示口径」才跳过)
H3 显示口径标记贯通
   H3-a DiceCombatContext 有 public boolean displaySnapshot;
   H3-b attackPowerBase 有 5 参重载, 末参为 boolean displaySnapshot
   H3-c 5 参版构造上下文后 ctx.displaySnapshot = displaySnapshot;
H4 骰战随机点数不进显示
   H4-a 攻击卡掷骰修饰器内有 if (ctx.displaySnapshot) return ap;
H5 手持武器仍被剔除
   H5-a CombatPowerHud 走 attackPowerDisplayOf(
   H5-b 链上仍存在 attackDamageExcludingHeldWeapon(
H6 两条线对等
   H6-a 每线 skipOnClient(ctx) 出现次数 >= 20
   H6-b 两线次数差 <= 2(neo 把蛟龙「真龙 +5」与「撕咬」合并成一块, 天然比 forge 少 1)

用法
----
    python tools/verify_hud_attack_parity.py

退出码: 0 = 全部满足; 1 = 有失败项; 2 = 有无法判定项。
本门只做静态取证, 不编译、不启动游戏 -> 不能证明运行时显示数值。
"""

import os
import re
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = ["neoforge-1.21.1", "forge-1.20.1"]   # P0;P1(26.1.2 / fabric)1.5.0 正式版前不移植
BASE = os.path.join("src", "main", "java", "com", "merlinkitsune", "astral_dice", "combat")
MODIFIERS = os.path.join(BASE, "DiceCombatModifiers.java")
CONTEXT = os.path.join(BASE, "DiceCombatContext.java")
HUD = os.path.join("src", "main", "java", "com", "merlinkitsune", "astral_dice",
                   "client", "CombatPowerHud.java")


class Gate(object):
    def __init__(self):
        self.checks = 0
        self.fails = []
        self.unknowns = []

    def run(self, ok, msg):
        self.checks += 1
        if ok is None:
            self.unknowns.append(msg)
        elif not ok:
            self.fails.append(msg)
        return ok


def read(path):
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        return fh.read()


def count(pattern, text):
    return len(re.findall(pattern, text))


def check_line(g, line):
    mod = read(os.path.join(ROOT, line, MODIFIERS))
    ctx = read(os.path.join(ROOT, line, CONTEXT))
    hud = read(os.path.join(ROOT, line, HUD))
    if mod is None or ctx is None:
        g.run(None, "%s: 缺少 DiceCombatModifiers.java 或 DiceCombatContext.java, 无法判定" % line)
        return None

    bare = count(r"isClientSide\(\)\)\s*return ap;", mod)
    g.run(bare == 0, "%s H1-a: 仍有 %d 处裸客户端守卫(应全部改走 skipOnClient)" % (line, bare))

    g.run("private static boolean skipOnClient(DiceCombatContext ctx)" in mod,
          "%s H2-a: 缺少 skipOnClient(DiceCombatContext) 定义" % line)
    body_ok = re.search(
        r"private static boolean skipOnClient\(DiceCombatContext ctx\)\s*\{\s*"
        r"return ctx\.attacker\.level\(\)\.isClientSide\(\)\s*&&\s*!ctx\.displaySnapshot;\s*\}",
        mod) is not None
    g.run(body_ok, "%s H2-b: skipOnClient 语义不是「客户端 且 非显示口径才跳过」" % line)

    g.run("public boolean displaySnapshot;" in ctx,
          "%s H3-a: DiceCombatContext 缺少 displaySnapshot 字段" % line)
    overload = re.search(
        r"double attackDamageStart,\s*(?:\r?\n\s*)?boolean displaySnapshot\)", mod) is not None
    g.run(overload, "%s H3-b: attackPowerBase 缺少 5 参(boolean displaySnapshot) 重载" % line)
    g.run("ctx.displaySnapshot = displaySnapshot;" in mod,
          "%s H3-c: 5 参 attackPowerBase 未把 displaySnapshot 写进上下文" % line)

    g.run("if (ctx.displaySnapshot) return ap;" in mod,
          "%s H4-a: 攻击卡掷骰修饰器未在显示口径下直接返回(随机点数会污染 HUD)" % line)

    if hud is None:
        g.run(None, "%s H5-a: 找不到 client/CombatPowerHud.java, 无法判定" % line)
    else:
        g.run("attackPowerDisplayOf(" in hud,
              "%s H5-a: CombatPowerHud 未走 attackPowerDisplayOf(" % line)
    g.run("attackDamageExcludingHeldWeapon(" in mod,
          "%s H5-b: 链上已无 attackDamageExcludingHeldWeapon(, 手持武器可能被重复计入" % line)

    return count(r"skipOnClient\(ctx\)", mod)


def check_parity(g, counts):
    for line, n in counts.items():
        g.run(n is not None and n >= 20,
              "%s H6-a: skipOnClient 命中 %s 处, 少于预期的 20 处" % (line, n))
    got = [v for v in counts.values() if v is not None]
    if len(got) == 2:
        g.run(abs(got[0] - got[1]) <= 2,
              "H6-b: 两线 skipOnClient 次数差 %d > 2(对等性破坏: %s)" % (abs(got[0] - got[1]), got))
    else:
        g.run(None, "H6-b: 两线次数不齐, 无法判定对等性")


def main():
    g = Gate()
    print("hud attack parity gate  ROOT=%s" % ROOT)
    print("P0 lines: %s" % ", ".join(LINES))
    print()
    counts = {}
    for line in LINES:
        counts[line] = check_line(g, line)
        print("%-20s skipOnClient=%s" % (line, counts[line]))
    print()
    check_parity(g, counts)

    print()
    print("NOTE: 本门只做静态取证, 不编译、不启动游戏 -> 不能证明 HUD 运行时显示数值。")
    if g.fails:
        print("GATE: FAIL  (%d 项不满足 / 共 %d 项)" % (len(g.fails), g.checks))
        for f in g.fails:
            print("  - " + f)
        return 1
    if g.unknowns:
        print("GATE: UNKNOWN  (%d 项无法判定 / 共 %d 项)" % (len(g.unknowns), g.checks))
        for u in g.unknowns:
            print("  - " + u)
        return 2
    print("GATE: PASS  (%d 项全部满足)" % g.checks)
    return 0


if __name__ == "__main__":
    sys.exit(main())
