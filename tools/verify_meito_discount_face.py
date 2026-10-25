#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""名刀·嘎呜切「减费卡面」守门（2026-10-25 用户裁决）。

背景：护法立牌（misaki_sign）被动把「名刀·嘎呜切」的战斗费用 4 → 3 点（服务端
`MisakiSignItem#getMeitoCost`），而卡面 64×64 素材右下角画着费用点 ⇒ 佩戴时货架卡面
必须换成减费素材，否则玩家看到的费用点与真实结算不一致。

判据（P0 两线 neoforge-1.21.1 / forge-1.20.1，逐条独立断言）：

D1 源素材 images/shop/card+event/嘎呜切减费.png 存在，且 PNG 头解析为 64×64（卡面渲染按 64 缩放）。
D2 两线 resources 里的 attack_card_meito_discount.png 与源素材**逐字节一致**（素材搬运不许改字节）。
D3 两线 CardShopScreen 定义 cardFaceTexture(ItemStack) 且 drawCardFace 通过它取贴图（不再就地拼路径）。
D4 cardFaceTexture 内四道要素齐全：只对 ATTACK_CARD_MEITO 生效、读 MisakiSignItem#hasMisakiEquipped、
   指向 _discount.png、素材缺失时回退 base（faceExists 三元）。
D5 服务端减费口径仍在（MisakiSignItem 里 hasMisakiEquipped(player) ? 3 : AppliedStone.cost("meito")），
   保证「换图」与「实际费用」同源；该口径只允许 1 处。
D6 减费贴图名只被 CardShopScreen 引用（单一事实源），其它 java 文件不得硬编码。

用法：python -B tools/verify_meito_discount_face.py [--quiet]
退出码：0 = 全通过；1 = 有失败项。
"""
import os
import re
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = ["neoforge-1.21.1", "forge-1.20.1"]

SRC = os.path.join("images", "shop", "card+event", "嘎呜切减费.png")
TEX = os.path.join("src", "main", "resources", "assets", "astral_dice", "textures", "gui", "shop", "card",
                   "attack_card_meito_discount.png")
SCREEN = os.path.join("src", "main", "java", "com", "merlinkitsune", "astral_dice", "screen", "CardShopScreen.java")
MISAKI = os.path.join("src", "main", "java", "com", "merlinkitsune", "astral_dice", "item", "sign", "MisakiSignItem.java")

failures = []
notes = []


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def read_bytes(p):
    with open(p, "rb") as f:
        return f.read()


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    w, h = struct.unpack(">II", data[16:24])
    return (w, h)


# ---- D1 源素材 ----
src_path = os.path.join(ROOT, SRC)
if not os.path.isfile(src_path):
    failures.append("D1 源素材缺失: %s" % SRC)
    src_bytes = b""
else:
    src_bytes = read_bytes(src_path)
    size = png_size(src_bytes)
    if size != (64, 64):
        failures.append("D1 源素材不是 64x64 的 PNG: %s" % (size,))
    else:
        notes.append("D1 源素材 %d B / 64x64 OK" % len(src_bytes))

# ---- D2 两线贴图与源素材逐字节一致 ----
for line in LINES:
    p = os.path.join(ROOT, line, TEX)
    if not os.path.isfile(p):
        failures.append("D2 %s: 减费贴图缺失 %s" % (line, TEX))
        continue
    data = read_bytes(p)
    if src_bytes and data != src_bytes:
        failures.append("D2 %s: 减费贴图与源素材不一致（%d B vs %d B）" % (line, len(data), len(src_bytes)))
    else:
        notes.append("D2 %s: %d B 与源素材一致" % (line, len(data)))

# ---- D3 / D4 两线 CardShopScreen ----
for line in LINES:
    p = os.path.join(ROOT, line, SCREEN)
    if not os.path.isfile(p):
        failures.append("D3 %s: CardShopScreen 缺失" % line)
        continue
    src = read(p)
    m = re.search(r"private ResourceLocation cardFaceTexture\(ItemStack stack\) \{(.*?)\n    \}", src, re.S)
    if not m:
        failures.append("D3 %s: 未定义 cardFaceTexture(ItemStack)" % line)
        body = ""
    else:
        body = m.group(1)
    if "ResourceLocation face = cardFaceTexture(stack);" not in src:
        failures.append("D3 %s: drawCardFace 未改用 cardFaceTexture(stack)" % line)
    need = {
        "D4 只对名刀生效": "stack.is(ModItems.ATTACK_CARD_MEITO.get())",
        "D4 读护法佩戴": "MisakiSignItem.hasMisakiEquipped(this.minecraft.player)",
        "D4 指向减费贴图": '"_discount.png"',
        "D4 缺素材回退": "faceExists(discount) ? discount : base",
    }
    for label, token in need.items():
        if token not in body:
            failures.append("%s %s: 缺少 %s" % (label, line, token))
    if all(token in body for token in need.values()):
        notes.append("D3/D4 %s: cardFaceTexture 四要素齐全" % line)

# ---- D5 服务端减费口径 ----
for line in LINES:
    p = os.path.join(ROOT, line, MISAKI)
    if not os.path.isfile(p):
        failures.append("D5 %s: MisakiSignItem 缺失" % line)
        continue
    src = read(p)
    pat = 'hasMisakiEquipped(player) ? 3 : AppliedStone.cost("meito")'
    if src.count(pat) != 1:
        failures.append("D5 %s: 减费口径命中 %d 处（应为 1）" % (line, src.count(pat)))
    else:
        notes.append("D5 %s: 4 -> 3 減费口径单点存在" % line)

# ---- D6 贴图名单一引用点 ----
hits = []
for line in LINES:
    base = os.path.join(ROOT, line, "src", "main", "java")
    for dirpath, _dirnames, filenames in os.walk(base):
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            fp = os.path.join(dirpath, fn)
            try:
                text = read(fp)
            except UnicodeDecodeError:
                continue
            if "attack_card_meito_discount" in text:
                hits.append(os.path.relpath(fp, os.path.join(ROOT, line)))
    stray = [h for h in hits if not h.replace("\\", "/").endswith("screen/CardShopScreen.java")]
if len(hits) < 2 or stray:
    failures.append("D6 减费贴图名必须只出现在两线 CardShopScreen（命中 %d 处，越界 %s）"
                    % (len(hits), ", ".join(sorted(stray)) or "无"))
else:
    notes.append("D6 减费贴图名只在两线 CardShopScreen 出现（%d 处）" % len(hits))

quiet = "--quiet" in sys.argv
if not quiet:
    for n in notes:
        print("  " + n)
if failures:
    print("GATE: FAIL (%d 项)" % len(failures))
    for f in failures:
        print("  - " + f)
    sys.exit(1)
print("GATE: PASS (D1-D6 全部满足，两线)")
