#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成「书页射程」状态图标(调查员立牌 rin 主动追加的 +50% 射程 buff)。

为什么要有这个脚本
------------------
MobEffect 的 HUD 图标是**按注册名查贴图**(``assets/<ns>/textures/mob_effect/<id>.png``)
—— 注册了效果却忘放图 ⇒ 玩家看到紫黑方块(守门 ``tools/verify_effect_icons.py`` 专门堵这个)。
本图标与「活体书页」效果图标同族(同款书页 + 中央符号),但中央符号由**红色准星**换成
**蓝色双向箭头**(表示「射程」),便于在 HUD 上区分「书页本体」与「书页射程」。

产物(三条生产线各一份,逐字节相同;fabric 移植线按 AGENTS 边界不参与同批)
------------------------------------------------------------------
    <子项目>/src/main/resources/assets/astral_dice/textures/mob_effect/rin_page_range.png

画法:以 16×16 逻辑网格手写像素图,再整数 2 倍放大到 32×32(与其它 mob_effect 图标同规格)。

用法
----
    python tools/gen_rin_page_range_texture.py            # 写入三个生产子项目
    python tools/gen_rin_page_range_texture.py --check    # 只校验现有贴图与生成结果一致(不写文件)

判据:``--check`` 退出码 0 = 三线贴图与生成器结果逐字节一致(可在静态闸门里调用)。
"""

from __future__ import annotations

import argparse
import hashlib
import os
import sys

from PIL import Image

SCALE = 2
GRID = 16

OUTPUTS = (
    os.path.join("neoforge-1.21.1", "src", "main", "resources",
                 "assets", "astral_dice", "textures", "mob_effect", "rin_page_range.png"),
    os.path.join("forge-1.20.1", "src", "main", "resources",
                 "assets", "astral_dice", "textures", "mob_effect", "rin_page_range.png"),
    os.path.join("neoforge-26.1.2", "src", "main", "resources",
                 "assets", "astral_dice", "textures", "mob_effect", "rin_page_range.png"),
)

TRANSPARENT = (0, 0, 0, 0)
PAGE_BORDER = (200, 200, 200, 255)   # 书页描边(浅灰,与 living_page 同族)
PAGE_WHITE = (245, 245, 245, 255)    # 书页纸面
PAGE_SHADE = (225, 225, 225, 255)    # 书页右下角的翻页阴影
ARROW = (42, 127, 212, 255)          # 射程箭头(蓝)
ARROW_HI = (120, 190, 245, 255)      # 箭头高光(浅蓝)


def build() -> Image.Image:
    """16×16 逻辑像素:书页(x2..11, y2..13) + 中央双向箭头(y7 中线)。"""
    img = Image.new("RGBA", (GRID, GRID), TRANSPARENT)
    px = img.load()

    # ── 书页:描边 + 纸面 ──
    for y in range(2, 14):
        for x in range(2, 12):
            px[x, y] = PAGE_BORDER if (x in (2, 11) or y in (2, 13)) else PAGE_WHITE

    # ── 右下角翻页阴影(与 living_page 同款观感)──
    for x in range(7, 12):
        px[x, 13] = PAGE_SHADE
    for x in range(3, 11):
        px[x, 14] = PAGE_SHADE

    # ── 中央双向箭头 ◄─►(左尖 x4 / 右尖 x9,轴 x5..x8,斜肩 y6/y8)──
    px[4, 7] = ARROW
    px[9, 7] = ARROW
    for x in range(5, 9):
        px[x, 7] = ARROW
    for x in (5, 8):
        px[x, 6] = ARROW
        px[x, 8] = ARROW
    px[6, 7] = ARROW_HI
    px[7, 7] = ARROW_HI

    return img.resize((GRID * SCALE, GRID * SCALE), Image.NEAREST)


def main() -> int:
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    image = build()
    digest = hashlib.sha1(image.tobytes()).hexdigest()[:12]

    if "--check" in sys.argv:
        ok = True
        for rel in OUTPUTS:
            path = os.path.join(root, rel)
            if not os.path.isfile(path):
                print("MISSING " + rel)
                ok = False
                continue
            with Image.open(path) as existing:
                same = existing.convert("RGBA").tobytes() == image.tobytes()
            print(("OK      " if same else "STALE   ") + rel)
            if not same:
                ok = False
        print("EXPECTED_SHA1 " + digest)
        return 0 if ok else 1

    for rel in OUTPUTS:
        path = os.path.join(root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        image.save(path, "PNG", optimize=True)
        print("WROTE " + rel)
    return 0


if __name__ == "__main__":
    sys.exit(main())
