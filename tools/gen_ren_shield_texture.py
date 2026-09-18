#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成「鼠鼠护盾」的护盾贴图(可平铺的六边晶格 + 等离子流纹)。

为什么要有这个脚本
------------------
护盾的「动态纹理」需要一张**可平铺**的图案贴图:渲染端按球面 UV 滚动它
(见 `client/RenShieldRenderer`),图案在球面上持续流动。图案必须是周期性的
(任意整数频率的正弦 / 周期晶格),否则 UV 环绕处会出现接缝。

产物(两线各一份,逐字节相同)
----------------------------
    <子项目>/src/main/resources/assets/astral_dice/textures/special/ren_shield.png

用法
----
    python tools/gen_ren_shield_texture.py            # 写入两个发布子项目
    python tools/gen_ren_shield_texture.py --check    # 只校验现有贴图与生成结果一致(不写文件)

判据:`--check` 退出码 0 = 两线贴图与生成器结果逐字节一致(可在静态闸门里调用)。
"""

from __future__ import annotations

import argparse
import hashlib
import math
import os
import sys

from PIL import Image

SIZE = 64
OUTPUTS = (
    os.path.join("neoforge-1.21.1", "src", "main", "resources",
                 "assets", "astral_dice", "textures", "special", "ren_shield.png"),
    os.path.join("forge-1.20.1", "src", "main", "resources",
                 "assets", "astral_dice", "textures", "special", "ren_shield.png"),
)


def frac(v: float) -> float:
    return v - math.floor(v)


def cell_distance(u: float, v: float, cell: float) -> float:
    """到最近「六边晶格」格心的距离。

    晶格 = 每行错半格的方格点阵(横竖周期都是 cell,行偏移每两行重复一次)——
    `cell` 整除 `SIZE` 且 `SIZE / cell` 为偶数 ⇒ 环绕处完全接得上,故贴图可平铺。
    """
    row = math.floor(v / cell)
    best = float("inf")
    for jj in (-1, 0, 1):
        j = row + jj
        offset = (cell * 0.5) if (j % 2) else 0.0
        col = math.floor((u - offset) / cell)
        for ii in (-1, 0, 1):
            i = col + ii
            dx = u - (i * cell + offset)
            dy = v - (j * cell)
            best = min(best, math.hypot(dx, dy))
    return best


def cell_glow(distance: float, radius: float, sharpness: float) -> float:
    """半径 radius 处的环状辉光(1 = 环心,0 = 远处):护盾的「能量单元」边线。"""
    d = (distance - radius) / sharpness
    return math.exp(-d * d)


def pattern(u: float, v: float) -> float:
    """0..1 的图案亮度:六边晶格辉光 + 两股斜向等离子流纹(全部整数频率 ⇒ 可平铺)。"""
    tau = 2.0 * math.pi
    distance = cell_distance(u, v, 16.0)
    lattice = cell_glow(distance, 7.2, 1.7)
    inner = math.exp(-((distance / 5.4) ** 2))
    wave_a = 0.5 + 0.5 * math.sin(tau * (3.0 * u + 2.0 * v) / SIZE)
    wave_b = 0.5 + 0.5 * math.sin(tau * (2.0 * u - 3.0 * v) / SIZE + 1.7)
    plasma = wave_a * wave_b
    # 细密的高频微光:让滚动时始终有细节在动
    sparkle = 0.5 + 0.5 * math.sin(tau * (5.0 * u - 4.0 * v) / SIZE + 0.6)
    value = 0.42 * lattice + 0.22 * inner * (0.35 + 0.65 * plasma) + 0.22 * plasma + 0.14 * sparkle
    return max(0.0, min(1.0, value))


def build() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE))
    pixels = image.load()
    for y in range(SIZE):
        for x in range(SIZE):
            p = pattern(float(x), float(y))
            # 灰度白 × 顶点色 = 最终颜色(顶点色负责青蓝与动态涌动),故贴图只承载亮度
            level = int(round(255.0 * max(0.0, min(1.0, 0.22 + 0.86 * p))))
            alpha = int(round(255.0 * max(0.0, min(1.0, 0.42 + 0.58 * p))))
            pixels[x, y] = (level, level, level, alpha)
    return image


def main() -> int:
    parser = argparse.ArgumentParser(description="生成鼠鼠护盾贴图")
    parser.add_argument("--check", action="store_true",
                        help="只校验(不写文件):两线贴图必须与生成结果逐字节一致")
    args = parser.parse_args()

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    image = build()

    if args.check:
        digest = hashlib.sha1(image.tobytes()).hexdigest()
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
