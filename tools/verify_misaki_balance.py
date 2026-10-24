#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""护法立牌（misaki_sign）品质/配方/死亡规则的静态守门（P0 两线）。

## 要拦的回归
用户 2026-10-24 裁决（P0 两线 neoforge-1.21.1 / forge-1.20.1）：
1. 品质 legendary → epic；
2. 合成配方 P 位 golden_star_plate → star_plate（datagen 与已入库生成 JSON 必须同步，
   只改一边 ⇒ 下次 runData 会回滚，属典型假绿）；
3. 死亡不再清零「剑气」：死亡处理方法体里不得再出现 MISAKI_SIGN_STACKS，
   而**卸下立牌**的清零路径 `MisakiSignItem#clearSignData` 必须保留；
4. Bountiful 赏金池 astral_rews 的 misaki rarity 必须与 Java 品质一致（EPIC）。
以上任一被改回旧形态或半改，本守门 exit 1。

## 判据（逐线，Java 一律先剥注释再匹配，不靠注释文本）
- M1 `ModItems.java` 的 `registerItem("misaki_sign", …)` 段内为 `.rarity(AstralRarities.epic())`，
  且同段内没有 `legendary()`。
- R1 datagen `ModRecipeProvider.java` 的 `shaped(…, ModItems.MISAKI_SIGN.get())` 块内
  `.define('P', ModItems.STAR_PLATE.get())`，且块内无 `GOLDEN_STAR_PLATE`。
- R2 已入库生成 JSON（neo `recipe/`，forge `recipes/`）的 `key.P.item` =
  `astral_dice:star_plate`，`result` 仍为 `astral_dice:misaki_sign` 且 count=1。
- D1 `PlayerLifecycleHandler.onPlayerDeathClearEffects` 方法体内不含 `MISAKI_SIGN_STACKS`；
  同时 padman 清零与 Curios 包装仍在（防止"整段删掉"式过度修改）。
- D2 `MisakiSignItem.clearSignData` 方法体仍把 MISAKI_SIGN_STACKS 置 0（卸下清零保留）。
- B1 `astral_rews.json` 中 misaki 条目 `rarity` = `EPIC`、`unitWorth` = 10000，
  文件仍为 UTF-8 / 纯 CRLF / Tab 缩进 / 末尾换行。

## 范围
只查 P0 两线；P1（neoforge-26.1.2 / fabric-1.20.1）**不在本任务范围**，明确不检查
（P0/P1 分歧由 scripts/verify/verify_bountiful_pools.ps1 报出，属已知既有红）。

退出码：0 全部通过 / 1 存在违规 / 2 无法判定（文件或方法无法定位）。

## 本守门证明不了什么
静态结构启发式：能拦"改配方的同时忘记同步 datagen/生成 JSON""死亡清零被改回来"这类
半改，不能证明游戏内品质显示、合成结果或死亡后剑气真的保留（需实机/单测）。

用法：python tools/verify_misaki_balance.py [-v]
"""

import json
import os
import re
import sys


# --- stdout/stderr 编码自愈（与 tools/verify_bonnie_active_order.py 同口径）---
def _astral_heal_stdio() -> None:  # pragma: no cover - 环境适配
    for _stream_name in ("stdout", "stderr"):
        _stream = getattr(sys, _stream_name, None)
        _reconf = getattr(_stream, "reconfigure", None)
        if _reconf is None:
            continue
        try:
            _reconf(encoding="utf-8", errors="replace")
        except Exception as _exc:  # noqa: BLE001 - 明确告警，绝不静默
            sys.stderr.write(
                "[astral-gate] WARN: %s.reconfigure(utf-8) failed: %r\n"
                % (_stream_name, _exc)
            )


_astral_heal_stdio()

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# P0 对等两线；P1 明确不在本任务范围。
LINES = ("neoforge-1.21.1", "forge-1.20.1")

JAVA_REL = os.path.join(
    "src", "main", "java", "com", "merlinkitsune", "astral_dice")
MODITEMS_REL = os.path.join(JAVA_REL, "item", "ModItems.java")
DATAGEN_REL = os.path.join(JAVA_REL, "datagen", "ModRecipeProvider.java")
LIFECYCLE_REL = os.path.join(JAVA_REL, "event", "PlayerLifecycleHandler.java")
SIGN_REL = os.path.join(JAVA_REL, "item", "sign", "MisakiSignItem.java")
REWS_REL = os.path.join(
    "src", "main", "resources", "data", "bountiful", "bounty_pools",
    "bountiful", "astral_rews.json")

# 1.20.1 线的配方目录是复数 `recipes`（1.21.1 起为单数 `recipe`），由数据驱动差异表决定。
RECIPE_REL = {
    "neoforge-1.21.1": os.path.join(
        "src", "generated", "resources", "data", "astral_dice",
        "recipe", "misaki_sign.json"),
    "forge-1.20.1": os.path.join(
        "src", "generated", "resources", "data", "astral_dice",
        "recipes", "misaki_sign.json"),
}

MISAKI_REG_RX = re.compile(r'registerItem\s*\(\s*"misaki_sign"')
NEXT_REG_RX = re.compile(r'registerItem\s*\(')
RARITY_EPIC_RX = re.compile(r'\.rarity\(\s*AstralRarities\.epic\(\)\s*\)')
RARITY_LEGENDARY_RX = re.compile(r'\.rarity\(\s*AstralRarities\.legendary\(\)\s*\)')

MISAKI_RECIPE_RX = re.compile(
    r'ShapedRecipeBuilder\s*\.\s*shaped\(\s*RecipeCategory\s*\.\s*MISC\s*,\s*'
    r'ModItems\s*\.\s*MISAKI_SIGN\s*\.\s*get\(\)\s*\)')
NEXT_RECIPE_RX = re.compile(r'ShapedRecipeBuilder\s*\.\s*shaped\s*\(')
DEFINE_P_STAR_RX = re.compile(
    r"\.define\(\s*'P'\s*,\s*ModItems\s*\.\s*STAR_PLATE\s*\.\s*get\(\)\s*\)")
GOLDEN_STAR_PLATE_RX = re.compile(r'GOLDEN_STAR_PLATE')

DEATH_SIG_RX = re.compile(
    r'onPlayerDeathClearEffects\s*\(\s*LivingDeathEvent\s+\w+\s*\)')
CLEAR_SIG_RX = re.compile(
    r'protected\s+void\s+clearSignData\s*\(\s*Player\s+\w+\s*,\s*'
    r'ItemStack\s+\w+\s*\)')
CLEAR_ZERO_RX = re.compile(r'MISAKI_SIGN_STACKS[^;]{0,160}?,\s*0\s*\)')

MISAKI_ID = "astral_dice:misaki_sign"
STAR_PLATE_ID = "astral_dice:star_plate"
EXPECTED_UNIT_WORTH = 10000


def read(path):
    with open(path, "rb") as fh:
        return fh.read().decode("utf-8", "replace")


def read_bytes(path):
    with open(path, "rb") as fh:
        return fh.read()


def strip_comments(src):
    """去掉行/块注释，保留字符串字面量（判据统一在剥注释后的文本上跑）。"""
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == '"' or c == "'":
            q = c
            out.append(c)
            i += 1
            while i < n:
                if src[i] == "\\":
                    out.append(src[i:i + 2])
                    i += 2
                    continue
                out.append(src[i])
                if src[i] == q:
                    i += 1
                    break
                i += 1
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            if j < 0:
                break
            i = j
            continue
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i)
            if j < 0:
                break
            i = j + 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def matching_close(src, open_idx):
    """open_idx 指向 `{`，返回配对的闭括号下标；失败返回 None。"""
    depth = 0
    j = open_idx
    n = len(src)
    while j < n:
        c = src[j]
        if c == '"' or c == "'":
            q = c
            j += 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == q:
                    break
                j += 1
        elif c == "/" and j + 1 < n and src[j + 1] == "/":
            j = src.find("\n", j)
            if j < 0:
                return None
            continue
        elif c == "/" and j + 1 < n and src[j + 1] == "*":
            j = src.find("*/", j)
            if j < 0:
                return None
            j += 1
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return j
        j += 1
    return None


def extract_method_body(src, sig_rx):
    """按签名定位方法，返回其 `{…}` 方法体文本；未找到返回 None。"""
    m = sig_rx.search(src)
    if not m:
        return None
    i = src.find("{", m.end())
    if i < 0:
        return None
    j = matching_close(src, i)
    if j is None:
        return None
    return src[i:j + 1]


def check_items_rarity(line, run, verbose):
    path = os.path.join(ROOT, line, MODITEMS_REL)
    if not os.path.isfile(path):
        run(None, "M1 %s: 文件不存在 %s" % (line, MODITEMS_REL))
        return
    src = strip_comments(read(path))
    m = MISAKI_REG_RX.search(src)
    if not m:
        run(None, "M1 %s: ModItems 里找不到 registerItem(\"misaki_sign\", …)" % line)
        return
    nxt = NEXT_REG_RX.search(src, m.end())
    seg = src[m.start():nxt.start() if nxt else len(src)]
    if RARITY_EPIC_RX.search(seg):
        run(True, "")
        if verbose:
            print("    ok M1 %s: MISAKI_SIGN 品质 = epic()" % line)
    else:
        run(False, "M1 %s: MISAKI_SIGN 品质不是 epic()" % line)
    if RARITY_LEGENDARY_RX.search(seg):
        run(False, "M1 %s: MISAKI_SIGN 段内仍残留 legendary()" % line)
    else:
        run(True, "")
        if verbose:
            print("    ok M1 %s: MISAKI_SIGN 段内无 legendary()" % line)


def check_datagen_recipe(line, run, verbose):
    path = os.path.join(ROOT, line, DATAGEN_REL)
    if not os.path.isfile(path):
        run(None, "R1 %s: 文件不存在 %s" % (line, DATAGEN_REL))
        return
    src = strip_comments(read(path))
    m = MISAKI_RECIPE_RX.search(src)
    if not m:
        run(None, "R1 %s: datagen 里找不到 MISAKI_SIGN 的 shaped 配方" % line)
        return
    nxt = NEXT_RECIPE_RX.search(src, m.end())
    block = src[m.start():nxt.start() if nxt else len(src)]
    if DEFINE_P_STAR_RX.search(block):
        run(True, "")
        if verbose:
            print("    ok R1 %s: datagen P 位 = ModItems.STAR_PLATE" % line)
    else:
        run(False, "R1 %s: datagen P 位不是 ModItems.STAR_PLATE.get()（配方未同步改材料）" % line)
    if GOLDEN_STAR_PLATE_RX.search(block):
        run(False, "R1 %s: datagen 该配方块内仍有 GOLDEN_STAR_PLATE" % line)
    else:
        run(True, "")
        if verbose:
            print("    ok R1 %s: datagen 该配方块内无 GOLDEN_STAR_PLATE" % line)


def check_generated_recipe(line, run, verbose):
    rel = RECIPE_REL[line]
    path = os.path.join(ROOT, line, rel)
    if not os.path.isfile(path):
        run(None, "R2 %s: 生成配方不存在 %s" % (line, rel))
        return
    try:
        data = json.loads(read_bytes(path).decode("utf-8-sig"))
    except Exception as exc:  # noqa: BLE001 - 解析失败按无法判定处理
        run(None, "R2 %s: 生成配方 JSON 解析失败: %r" % (line, exc))
        return
    item = (((data.get("key") or {}).get("P") or {}).get("item"))
    if item == STAR_PLATE_ID:
        run(True, "")
        if verbose:
            print("    ok R2 %s: 生成配方 key.P.item = %s" % (line, item))
    else:
        run(False, "R2 %s: 生成配方 key.P.item = %r（应为 %s）"
                   % (line, item, STAR_PLATE_ID))
    res = data.get("result") or {}
    res_id = res.get("id") or res.get("item")
    count = res.get("count", 1)
    if res_id == MISAKI_ID and count == 1:
        run(True, "")
        if verbose:
            print("    ok R2 %s: 生成配方产物 = %s × 1" % (line, res_id))
    else:
        run(False, "R2 %s: 生成配方产物 = %r × %r（应为 %s × 1）"
                   % (line, res_id, count, MISAKI_ID))


def check_death_cleanup(line, run, verbose):
    path = os.path.join(ROOT, line, LIFECYCLE_REL)
    if not os.path.isfile(path):
        run(None, "D1 %s: 文件不存在 %s" % (line, LIFECYCLE_REL))
        return
    src = strip_comments(read(path))
    body = extract_method_body(src, DEATH_SIG_RX)
    if body is None:
        run(None, "D1 %s: 找不到 onPlayerDeathClearEffects(LivingDeathEvent) 方法体" % line)
        return
    if "MISAKI_SIGN_STACKS" in body:
        run(False, "D1 %s: 死亡处理方法体仍在清 MISAKI_SIGN_STACKS ⇒ 死亡会丢剑气" % line)
    else:
        run(True, "")
        if verbose:
            print("    ok D1 %s: 死亡处理方法体不再触碰 MISAKI_SIGN_STACKS" % line)
    if "PADMAN_ATK_BONUS" in body and "findFirstCurio" in body:
        run(True, "")
        if verbose:
            print("    ok D1 %s: padman 清零与 Curios 包装仍在（未过度删除）" % line)
    else:
        run(False, "D1 %s: padman 清零/Curios 包装被连带删除 ⇒ 修改越界" % line)


def check_clear_sign_data(line, run, verbose):
    path = os.path.join(ROOT, line, SIGN_REL)
    if not os.path.isfile(path):
        run(None, "D2 %s: 文件不存在 %s" % (line, SIGN_REL))
        return
    src = strip_comments(read(path))
    body = extract_method_body(src, CLEAR_SIG_RX)
    if body is None:
        run(None, "D2 %s: 找不到 clearSignData(Player, ItemStack) 方法体" % line)
        return
    if CLEAR_ZERO_RX.search(body):
        run(True, "")
        if verbose:
            print("    ok D2 %s: clearSignData 仍把 MISAKI_SIGN_STACKS 置 0（卸下清零保留）" % line)
    else:
        run(False, "D2 %s: clearSignData 不再清零 MISAKI_SIGN_STACKS ⇒ 卸下也不清剑气" % line)


def check_bountiful_pool(line, run, verbose):
    path = os.path.join(ROOT, line, REWS_REL)
    if not os.path.isfile(path):
        run(None, "B1 %s: 文件不存在 %s" % (line, REWS_REL))
        return
    raw = read_bytes(path)
    try:
        data = json.loads(raw.decode("utf-8"))
    except Exception as exc:  # noqa: BLE001 - 解析失败按无法判定处理
        run(None, "B1 %s: astral_rews.json 解析失败: %r" % (line, exc))
        return
    entries = []
    for entry in (data.get("content") or {}).values():
        if entry.get("content") == MISAKI_ID:
            entries.append(entry)
    if len(entries) != 1:
        run(None, "B1 %s: astral_rews 里 %s 条目数 = %d（应为 1）"
                  % (line, MISAKI_ID, len(entries)))
        return
    entry = entries[0]
    rarity = entry.get("rarity", "COMMON")  # 缺省字段 = COMMON（Bountiful 默认）
    if rarity == "EPIC":
        run(True, "")
        if verbose:
            print("    ok B1 %s: astral_rews misaki rarity = EPIC" % line)
    else:
        run(False, "B1 %s: astral_rews misaki rarity = %r（应与 Java 品质同步为 EPIC）"
                   % (line, rarity))
    worth = entry.get("unitWorth")
    if worth == EXPECTED_UNIT_WORTH:
        run(True, "")
        if verbose:
            print("    ok B1 %s: astral_rews misaki unitWorth = %d（未改价值）"
                  % (line, EXPECTED_UNIT_WORTH))
    else:
        run(False, "B1 %s: astral_rews misaki unitWorth = %r（应为 %d，本任务不改价值）"
                   % (line, worth, EXPECTED_UNIT_WORTH))

    # 格式：UTF-8 / 纯 CRLF / Tab 缩进 / 末尾换行
    crlf = raw.count(b"\r\n")
    lf = raw.count(b"\n")
    if crlf == lf and raw.endswith(b"\r\n"):
        run(True, "")
        if verbose:
            print("    ok B1 %s: astral_rews 纯 CRLF 且末尾有换行" % line)
    else:
        run(False, "B1 %s: astral_rews 换行格式异常（CRLF=%d, LF=%d, 末尾换行=%s）"
                   % (line, crlf, lf, raw.endswith(b"\r\n")))
    text = raw.decode("utf-8", "replace")
    if b"\r\n    " in raw or b"\r\n  " in raw:
        run(False, "B1 %s: astral_rews 出现空格缩进（应为 Tab）" % line)
    else:
        run(True, "")
        if verbose:
            print("    ok B1 %s: astral_rews 仍为 Tab 缩进" % line)


def main():
    verbose = "-v" in sys.argv
    fails = []
    unresolved = []
    checks = 0

    def run(ok, msg):
        nonlocal checks
        checks += 1
        if ok is None:
            unresolved.append(msg)
            print("  ?? " + msg)
        elif ok:
            if verbose and msg:
                print("  ok " + msg)
        else:
            fails.append(msg)
            print("  FAIL " + msg)

    print("== 护法立牌品质/配方/死亡规则（P0 两线：neoforge-1.21.1 / forge-1.20.1；"
          "P1 neoforge-26.1.2 / fabric-1.20.1 明确不在检查范围）==")
    for line in LINES:
        print("[%s]" % line)
        check_items_rarity(line, run, verbose)
        check_datagen_recipe(line, run, verbose)
        check_generated_recipe(line, run, verbose)
        check_death_cleanup(line, run, verbose)
        check_clear_sign_data(line, run, verbose)
        check_bountiful_pool(line, run, verbose)

    print()
    if fails:
        print("GATE: FAIL  (%d 项不满足 / 共 %d 项)" % (len(fails), checks))
        for f in fails:
            print("  - " + f)
        return 1
    if unresolved:
        print("GATE: UNKNOWN  (%d 项无法判定 / 共 %d 项)" % (len(unresolved), checks))
        for u in unresolved:
            print("  - " + u)
        return 2
    print("GATE: PASS  (%d 项全部满足)" % checks)
    return 0


if __name__ == "__main__":
    sys.exit(main())
