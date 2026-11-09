#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""筹码商店守门「变异测试」（judge-mutation）—— 证明 verify_chip_shop_ui 的 G9..G27 判据不是假绿。

本文件是 scripts/verify/旧版 mutate_chip_shop_ui 实现 的**等价迁移**（2026-11-02 批次 B）：
对源码/资源逐条注入一处语义变异（去重 / 概率表 / 已拥有过滤 / 双击 / 第二购买入口 / 隐藏槽 mayPickup /
价格步长 / 价格上限 / 刷新周期 / 刷新上限钳制 / 物品栏已满判定取反 / 筹码格命中条件 / 确认守卫被削 /
语言键集不对称 / 悬空语言键 / 两枚图标互换字节 / 品质外框被删掉或被改档），共 39 条 =
37 条文本变异 + 2 条图标字节互换；每条都要求守门脚本**判红**且失败信息命中预期关键字；
随后**字节级还原**并按 SHA256 复核文件已复原（交换型还原后直接比两枚图标哈希）。

用法（任意目录均可，仓库根按脚本位置上溯两级求）：
    python scripts/verify/mutate_chip_shop_ui.py
    python scripts/verify/mutate_chip_shop_ui.py --root F:/MCProject/astral_dice_multiloader-next
    python scripts/verify/mutate_chip_shop_ui.py --gate scripts/verify/verify_chip_shop_ui.py

退出码（与 旧实现 一致）：0 = 全部变异都被抓到（守门有效）；1 = 有变异漏判（守门有洞）；
2 = harness 自身失败（锚点没命中 / 目标文件缺失 / 还原后字节不一致）—— 该条判据根本没被注入，
    与「漏判」分开计数、分开报告，禁止混进判红桶里。

与 旧实现 的差别（仅两处，都不改输出契约）：
  1. 被调用的守门脚本由「同目录同名 .py」决定（旧实现 调 旧版 verify_chip_shop_ui 实现），
     默认即本文件的兄弟脚本，可用 --gate 覆盖 —— 这样 旧实现 资产删除后本 harness 仍可用；
  2. 写日志前显式创建 temp/t87/ 目录（旧实现 依赖该目录已存在）。

多行锚点：脚本内 NL 恒为 LF，而 forge 线源码/资源多为 CRLF ⇒ 锚点与替换串在比对前按
**目标文件自己的行尾**归一化（convert_to_file_eol）；不归一化时多行锚点会静默匹配不到（实测踩中过）。
"""

import hashlib
import os
import subprocess
import sys

OUT = []


def w(text=""):
    OUT.append(text + "\n")


def emit():
    sys.stdout.buffer.write("".join(OUT).encode("utf-8", "replace"))
    sys.stdout.buffer.flush()


NL = "\n"

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))


def parse_args(argv):
    root = ""
    gate = ""
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--root" and i + 1 < len(argv):
            root = argv[i + 1]
            i += 2
            continue
        if a == "--gate" and i + 1 < len(argv):
            gate = argv[i + 1]
            i += 2
            continue
        if a in ("-h", "--help"):
            w("用法: python scripts/verify/mutate_chip_shop_ui.py [--root <仓库根>] [--gate <守门脚本>]")
            emit()
            sys.exit(0)
        if not a.startswith("-") and root == "":
            root = a
            i += 1
            continue
        i += 1
    return root, gate


def sha256_hex(p):
    h = hashlib.sha256()
    with open(p, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest().upper()


def convert_to_file_eol(s, eol):
    """把锚点/替换串的行尾改成目标文件自己的行尾（脚本内 NL 恒为 LF；forge 线文件多为 CRLF）。"""
    t = s.replace("\r\n", "\n")
    if eol == "\r\n":
        t = t.replace("\n", "\r\n")
    return t


def as_text(b):
    return b.decode("utf-8-sig", "replace")


# M17 锚点（多行；逐字书写 pattern + key 整块，行尾由 convert_to_file_eol 按目标文件归一化）：
#   现状 = chip_shop 的 GPG/HKH/HHH + key{G,P,H,K}；变异 = 换成 card_shop 的 TTT/BBB/MMM + key{T,B,M}
#   => 两文件「图案 + 材料」签名全同 => 必被 G22 撞车组判红。
M17_FIND = NL.join([
    '  "pattern": [',
    '    "GPG",',
    '    "HKH",',
    '    "HHH"',
    '  ],',
    '  "key": {',
    '    "G": {',
    '      "item": "minecraft:gold_ingot"',
    '    },',
    '    "P": {',
    '      "item": "astral_dice:star_plate"',
    '    },',
    '    "H": {',
    '      "item": "minecraft:polished_blackstone_bricks"',
    '    },',
    '    "K": {',
    '      "item": "astral_dice:blank_chip"',
    '    }',
    '  },',
])
M17_REPL = NL.join([
    '  "pattern": [',
    '    "TTT",',
    '    "BBB",',
    '    "MMM"',
    '  ],',
    '  "key": {',
    '    "T": {',
    '      "item": "minecraft:smooth_stone_slab"',
    '    },',
    '    "B": {',
    '      "item": "astral_dice:star_coin"',
    '    },',
    '    "M": {',
    '      "tag": "minecraft:logs"',
    '    }',
    '  },',
])


def build_mutations(java, forge, root):
    def m(name, file, find, repl, expect, line=None):
        d = {"name": name, "file": file, "find": find, "repl": repl, "expect": expect}
        if line is not None:
            d["line"] = line
        return d

    return [
        m("M1 池去重被取消（pool.remove 注释掉）",
          java + "/shop/ChipShopRoller.java",
          "pool.remove(chosen);", "// pool.remove(chosen);", "去重/缺口"),
        m("M2 概率表改一位（0★ 的 60% → 61%）",
          java + "/shop/ChipShopCatalog.java",
          "{0.60, 0.37, 0.03}", "{0.61, 0.37, 0.03}", "概率/权重常量"),
        m("M3 已拥有过滤被去掉（needsReroll 不看已拥有）",
          java + "/shop/ChipShopState.java",
          "if (ChipShopRoller.isOwned(player, entry)) {", "if (false) {", "needsReroll 未按已拥有判定"),
        m("M4 确认按钮改双击（引入 DOUBLE_CLICK_MS）",
          java + "/screen/ChipShopScreen.java",
          "private static final int PRESS_TICKS",
          "private static final int DOUBLE_CLICK_MS = 250;" + NL + "    private static final int PRESS_TICKS",
          "被禁的拖拽/双击标识符"),
        m("M5 出现第二购买入口（mouseClicked 末尾也调确认）",
          java + "/screen/ChipShopScreen.java",
          "        // ── ④ 其余一律交回原版",
          "        onConfirmPressed();" + NL + "        // ── ④ 其余一律交回原版",
          "onConfirmPressed() 出现次数应为 2"),
        m("M6 隐藏槽 mayPickup 改 true（候选可被拿走）",
          java + "/shop/ChipShopMenu.java",
          "public boolean mayPickup(Player player) {",
          "public boolean mayPickup(Player player) { return true; } /*MUT*/ "
          "private boolean unusedMayPickup(Player player) {",
          "mayPickup=false"),
        m("M7 价格步长 5 → 1",
          java + "/shop/ChipShopCatalog.java",
          "PRICE_STEP = 5", "PRICE_STEP = 1", "价格公式/常量不符"),
        m("M8 价格上限 100 → 50",
          java + "/shop/ChipShopCatalog.java",
          "PRICE_MAX = 100", "PRICE_MAX = 50", "价格公式/常量不符"),
        m("M9 刷新恢复周期 36000 → 18000",
          java + "/shop/ChipShopCatalog.java",
          "REFRESH_REGEN_TICKS = 36000", "REFRESH_REGEN_TICKS = 18000", "刷新常量不符"),
        m("M10 刷新上限钳制被改（withRefreshes 的 Math.min → Math.max）",
          java + "/shop/ChipShopState.java",
          "Math.min(ChipShopCatalog.REFRESH_MAX, left)", "Math.max(ChipShopCatalog.REFRESH_MAX, left)",
          "刷新次数未夹在 0..REFRESH_MAX"),
        m("M11 物品栏已满判定被取反（hasNoRoom 的 isEmpty → !isEmpty）",
          java + "/shop/ChipShopOpener.java",
          "if (player.getInventory().getItem(i).isEmpty()) {",
          "if (!player.getInventory().getItem(i).isEmpty()) {",
          "物品栏已满判定被改"),
        m("M12 第 0 号筹码格永远选不中（chip >= 0 → chip > 0）",
          java + "/screen/ChipShopScreen.java",
          "if (chip >= 0) {", "if (chip > 0) {", "筹码格命中判定被改"),
        m("M13 空候选位也发购买包（确认守卫被削成 chip < 0）",
          java + "/screen/ChipShopScreen.java",
          "chip < 0 || ChipShopClientState.offer(chip).isEmpty()", "chip < 0", "确认按钮缺"),
        m("M14 语言键集不对称（仅 en_us 多加一条无关键）",
          root + "/neoforge-1.21.1/src/main/resources/assets/astral_dice/lang/en_us.json",
          '"gui.astral_dice.chip_shop.empty"',
          '"zzz_probe_key": "x",' + NL + '  "gui.astral_dice.chip_shop.empty"',
          "语言键集不对称"),
        m("M15 Java 侧语言键写错（悬空键：no_refresh → no_refresh_x）",
          java + "/shop/ChipShopMenu.java",
          "msg.astral_dice.chip_shop_no_refresh", "msg.astral_dice.chip_shop_no_refresh_x", "悬空语言键"),
        m("M17 配方撞车回归（chip_shop 改成与 card_shop 同图案同材料 TTT/BBB/MMM）",
          root + "/neoforge-1.21.1/src/main/resources/data/astral_dice/recipe/chip_shop.json",
          M17_FIND, M17_REPL, "shaped 配方撞车"),
        m("M18 关店被挪出 handleBuy（挪到同类新方法里）",
          java + "/shop/ChipShopMenu.java",
          "        buyer.closeContainer();" + NL + "    }",
          "    }" + NL + NL + "    private void mutatedClose(ServerPlayer buyer) { buyer.closeContainer(); }",
          ".closeContainer(); 必须落在 handleBuy 方法体内"),
        m("M19 标签框命中被删掉（chipIndexAt 只剩筹码格）",
          java + "/screen/ChipShopScreen.java",
          "isHoveringStrip(mouseX, mouseY, LABEL_X[cell], LABEL_Y, LABEL_W, LABEL_H)", "false",
          "命中面（筹码格 ∪ 标签框）"),
        m("M20 方块朝向丢掉 getOpposite()（朝向与放置者相反）",
          java + "/block/ChipShopBlock.java",
          "context.getHorizontalDirection().getOpposite()", "context.getHorizontalDirection()",
          "ChipShopBlock 朝向/交互语义缺失"),
        m("M21 拒绝码重复（DENY_NO_REFRESH 1 → 2，与物品栏已满撞码）",
          java + "/shop/ChipShopMenu.java",
          "DENY_NO_REFRESH = 1", "DENY_NO_REFRESH = 2", "拒绝码常量缺"),
        m("M22 星等来源被写死（不再读骰子星阶）",
          java + "/shop/ChipShopRoller.java",
          "CardShopShelf.currentStarLevel(player)", "3", "starLevel 未复用"),
        m("M23 注入折扣路径（§6 Q13：筹码商店无任何折扣）",
          java + "/shop/ChipShopCatalog.java",
          "public static final int OFFER_COUNT = 3;",
          "public static final int OFFER_COUNT = 3;" + NL + "    public static final double discount = 1.0;",
          "出现折扣路径标识符"),
        m("M24 概率表行序对调（2★ 与 3★ 两行互换）",
          java + "/shop/ChipShopCatalog.java",
          "            {0.40, 0.45, 0.15}," + NL + "            {0.25, 0.50, 0.25},",
          "            {0.25, 0.50, 0.25}," + NL + "            {0.40, 0.45, 0.15},",
          "概率表行序"),
        m("M38 品质外框绘制入口被挖掉（int frame = qualityCode(chip) → 0）",
          java + "/screen/ChipShopScreen.java",
          "int frame = ChipShopClientState.qualityCode(chip);", "int frame = 0;", "品质外框绘制锚点缺失"),
        m("M25 移植线方块塞入 1.21.1 专有字面（假 codec 常量蒙守门）",
          forge + "/block/ChipShopBlock.java",
          "    private static final VoxelShape SHAPE",
          '    static final String MUT = "simpleCodec(ChipShopBlock::new);";'
          + NL + "    private static final VoxelShape SHAPE",
          "本线不应有的平台专有字面", line="forge"),
        m("M26 移植线丢掉六参 use 入口（方块右键不再开店）",
          forge + "/block/ChipShopBlock.java",
          "    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,",
          "    public InteractionResult useMuted(BlockState state, Level level, BlockPos pos, Player player,",
          "ChipShopBlock 朝向/交互语义缺失", line="forge"),
        m("M27 移植线池去重被取消（pool.remove 注释掉）",
          forge + "/shop/ChipShopRoller.java",
          "pool.remove(chosen);", "// pool.remove(chosen);", "去重/缺口", line="forge"),
        m("M28 移植线概率表改一位（0★ 的 60% → 61%）",
          forge + "/shop/ChipShopCatalog.java",
          "{0.60, 0.37, 0.03}", "{0.61, 0.37, 0.03}", "概率/权重常量", line="forge"),
        m("M29 移植线已拥有过滤被去掉（needsReroll 不看已拥有）",
          forge + "/shop/ChipShopState.java",
          "if (ChipShopRoller.isOwned(player, entry)) {", "if (false) {",
          "needsReroll 未按已拥有判定", line="forge"),
        m("M30 移植线隐藏槽 mayPickup 改 true（候选可被拿走）",
          forge + "/shop/ChipShopMenu.java",
          "public boolean mayPickup(Player player) {",
          "public boolean mayPickup(Player player) { return true; } /*MUT*/ "
          "private boolean unusedMayPickup(Player player) {",
          "mayPickup=false", line="forge"),
        m("M31 移植线价格步长 5 → 1",
          forge + "/shop/ChipShopCatalog.java",
          "PRICE_STEP = 5", "PRICE_STEP = 1", "价格公式/常量不符", line="forge"),
        m("M32 移植线刷新恢复周期 36000 → 18000",
          forge + "/shop/ChipShopCatalog.java",
          "REFRESH_REGEN_TICKS = 36000", "REFRESH_REGEN_TICKS = 18000", "刷新常量不符", line="forge"),
        m("M33 移植线刷新上限钳制被改（withRefreshes 的 Math.min → Math.max）",
          forge + "/shop/ChipShopState.java",
          "Math.min(ChipShopCatalog.REFRESH_MAX, left)", "Math.max(ChipShopCatalog.REFRESH_MAX, left)",
          "刷新次数未夹在 0..REFRESH_MAX", line="forge"),
        m("M34 移植线关店被挪出 handleBuy（closeContainer 调用被注释掉）",
          forge + "/shop/ChipShopMenu.java",
          "buyer.closeContainer();", "// buyer.closeContainer();",
          ".closeContainer(); 必须落在 handleBuy 方法体内", line="forge"),
        m("M35 移植线第 0 号筹码格永远选不中（chip >= 0 → chip > 0）",
          forge + "/screen/ChipShopScreen.java",
          "if (chip >= 0) {", "if (chip > 0) {", "筹码格命中判定被改", line="forge"),
        m("M37 移植线关店被挪出 handleBuy（多行锚点，CRLF 文件）",
          forge + "/shop/ChipShopMenu.java",
          "        buyer.closeContainer();" + NL + "    }",
          "    }" + NL + NL + "    private void mutatedClose(ServerPlayer buyer) { buyer.closeContainer(); }",
          ".closeContainer(); 必须落在 handleBuy 方法体内", line="forge"),
        m("M39 移植线品质外框三档映射被改（qualityTex 的 frame == 2 分支失效）",
          forge + "/screen/ChipShopScreen.java",
          "if (frame == 2) {", "if (frame == 9) {", "qualityTex 三档映射不完整", line="forge"),
    ]


def build_swaps(root):
    icons = root + "/neoforge-1.21.1/src/main/resources/assets/astral_dice/textures/gui/chip_shop/chips"
    forge_icons = root + "/forge-1.20.1/src/main/resources/assets/astral_dice/textures/gui/chip_shop/chips"
    return [
        {"name": "M16 两枚筹码图标互换字节（adrenaline_high ↔ adrenaline_low）",
         "a": icons + "/adrenaline_high_chip.png",
         "b": icons + "/adrenaline_low_chip.png",
         "expect": "筹码图标不合格"},
        {"name": "M36 移植线两枚筹码图标互换字节（adrenaline_high ↔ adrenaline_low）",
         "a": forge_icons + "/adrenaline_high_chip.png",
         "b": forge_icons + "/adrenaline_low_chip.png",
         "expect": "筹码图标不合格", "line": "forge"},
    ]


def run_gate(gate, tgt, log):
    """等价于 旧实现 侧的「& 解释器(NoProfile, File, <gate>, <tgt>) 并 *> <log>」：
    子进程 stdout+stderr 一律重定向进日志文件，只把退出码带回来（日志随后原文读回做关键字判定）。"""
    d = os.path.dirname(log)
    if d and not os.path.isdir(d):
        os.makedirs(d, exist_ok=True)
    with open(log, "wb") as fh:
        proc = subprocess.run([sys.executable, "-B", gate, tgt], stdout=fh, stderr=subprocess.STDOUT)
    return proc.returncode


def read_log(log):
    try:
        with open(log, "rb") as fh:
            return fh.read().decode("utf-8", "replace")
    except OSError:
        return ""


def judge(name, expect, code, out, caught, escaped):
    """旧实现: if (code -ne 0 -and hit) { 判红 } else { 漏判！ }"""
    hit = expect in out
    if code != 0 and hit:
        caught[0] += 1
        w("MUT: " + name + " => RED(ok) expect=" + expect)
    else:
        escaped.append(name)
        w("MUT: " + name + " => 漏判！exit=" + str(code) + " expect=" + expect
          + " hit=" + ("True" if hit else "False"))


def main():
    root_arg, gate_arg = parse_args(sys.argv[1:])
    root = os.path.abspath(root_arg) if root_arg else ROOT
    java = root + "/neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice"
    forge = root + "/forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice"
    if gate_arg:
        gate = os.path.abspath(gate_arg)
    else:
        gate = os.path.join(HERE, "verify_chip_shop_ui.py")
    log = root + "/temp/t87/mutation.log"

    mutations = build_mutations(java, forge, root)
    swaps = build_swaps(root)

    w("MUT: 筹码商店守门变异测试（每条变异都必须被判红）")
    w("MUT_ROOT: " + root)

    caught = [0]
    escaped = []
    harness = []

    for mm in mutations:
        name = mm["name"]
        path = mm["file"]
        if not os.path.isfile(path):
            w("MUT_HARNESS: " + name + " —— 目标文件不存在 " + path)
            harness.append(name)
            continue
        with open(path, "rb") as fh:
            raw = fh.read()
        sha_before = sha256_hex(path)
        text = as_text(raw)
        eol = "\r\n" if "\r\n" in text else "\n"
        find = convert_to_file_eol(mm["find"], eol)
        repl = convert_to_file_eol(mm["repl"], eol)
        idx = text.find(find)
        if idx < 0:
            w("MUT_HARNESS: " + name + " —— 未找到变异锚点（按文件行尾归一化后仍无命中）")
            harness.append(name)
            continue
        if text.find(find, idx + 1) >= 0 and find != "{0.60, 0.37, 0.03}":
            # 锚点不唯一会让「变异」变得含糊，直接拒绝（概率表条目是唯一被允许出现两次的锚点，且只改第一处）
            w("MUT_HARNESS: " + name + " —— 变异锚点不唯一")
            harness.append(name)
            continue
        mutated = text[:idx] + repl + text[idx + len(find):]
        try:
            with open(path, "wb") as fh:
                fh.write(mutated.encode("utf-8"))
            code = run_gate(gate, mm.get("line", "neo"), log)
            judge(name, mm["expect"], code, read_log(log), caught, escaped)
        finally:
            with open(path, "wb") as fh:
                fh.write(raw)
            sha_after = sha256_hex(path)
            if sha_after != sha_before:
                w("MUT_HARNESS: " + name + " —— 还原后哈希不一致（" + sha_before + " vs " + sha_after + "）")
                harness.append(name)
            else:
                w("MUT_RESTORE: " + os.path.basename(path) + " 已字节还原 " + sha_after[0:12])

    for sw in swaps:
        name = sw["name"]
        pa = sw["a"]
        pb = sw["b"]
        if not os.path.isfile(pa) or not os.path.isfile(pb):
            w("MUT_HARNESS: " + name + " —— 目标图标不存在")
            harness.append(name)
            continue
        with open(pa, "rb") as fh:
            ba = fh.read()
        with open(pb, "rb") as fh:
            bb = fh.read()
        ha = sha256_hex(pa)
        hb = sha256_hex(pb)
        try:
            with open(pa, "wb") as fh:
                fh.write(bb)
            with open(pb, "wb") as fh:
                fh.write(ba)
            code = run_gate(gate, sw.get("line", "neo"), log)
            judge(name, sw["expect"], code, read_log(log), caught, escaped)
        finally:
            with open(pa, "wb") as fh:
                fh.write(ba)
            with open(pb, "wb") as fh:
                fh.write(bb)
            ha2 = sha256_hex(pa)
            hb2 = sha256_hex(pb)
            if ha2 != ha or hb2 != hb:
                w("MUT_HARNESS: " + name + " —— 还原后哈希不一致")
                harness.append(name)
            else:
                w("MUT_RESTORE: " + os.path.basename(pa) + " + " + os.path.basename(pb) + " 已字节还原")

    w("MUT_TOTAL: " + str(len(mutations) + len(swaps)) + "，判红 " + str(caught[0]))
    if escaped:
        w("MUT_ESCAPED: " + " | ".join(escaped))
    if harness:
        w("MUT_HARNESS_FAIL: " + " | ".join(harness))
        w("MUT_RESULT: FAIL(harness)")
        return 2
    if not escaped:
        w("MUT_RESULT: OK")
        return 0
    w("MUT_RESULT: FAIL")
    return 1


if __name__ == "__main__":
    try:
        rc = main()
    finally:
        emit()
    sys.exit(rc)
