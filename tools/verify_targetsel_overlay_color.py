#!/usr/bin/env python3
"""目标选择器中央提示文字（overlay）的像素判据 —— 26.1.2 显式 ARGB 修复的机器判据。

背景：26.1.2 的 net/minecraft/client/gui/GuiGraphicsExtractor.java:250-255 的
text(...) 第一句是 if (ARGB.alpha(color) != 0)；alpha 为 0 的 color 被静默丢弃、
一个字都不画，且不报错。1.21.1 的 Font#adjustColor（alpha 为 0 视为不透明）兜底在
本版本已移除。修复把 client/TargetSelectionClient 的 COLOR_FRIENDLY / COLOR_HOSTILE /
COLOR_NEUTRAL 由无 alpha 的 RGB 改为显式 ARGB。

判据：在 overlay 文本所在的屏幕条带内统计目标色的字形像素数。
  friendly -> (85, 255, 85)   = 0xFF55FF55
  hostile  -> (255, 85, 85)   = 0xFFFF5555
  neutral  -> (255, 255, 85)  = 0xFFFFFF55
修复版：目标色计数 >= --min-pixels；负控（SNAPSHOT.30 未修复）同场景应为 0。

退出码：0 = 判据成立，1 = 判据不成立，2 = 无法判定。
"""

import argparse
import json
import os
import sys

try:
    from PIL import Image
except Exception as exc:
    print("verify_targetsel_overlay_color: 无法导入 Pillow: " + str(exc))
    sys.exit(2)

COLOR_TABLE = {
    "friendly": (85, 255, 85),
    "hostile": (255, 85, 85),
    "neutral": (255, 255, 85),
}

DEFAULT_BOX = (640, 552, 1280, 604)
DEFAULT_RUN_REL = os.path.join("run", "26.1.2")


def repo_root():
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.dirname(here)


def resolve_from_registry(run_dir, tag):
    reg = os.path.join(run_dir, "screenshots", ".mt_shots.json")
    if not os.path.isfile(reg):
        return None
    with open(reg, "r", encoding="utf-8") as handle:
        data = json.load(handle)
    hits = [item for item in data.get("shots", []) if item.get("tag") == tag]
    if not hits:
        return None
    name = hits[-1].get("file")
    return os.path.join(run_dir, "screenshots", name) if name else None


def parse_box(text):
    parts = [int(piece.strip()) for piece in text.split(",")]
    if len(parts) != 4:
        raise ValueError("--box 需要 x0,y0,x1,y1 四个整数")
    return (parts[0], parts[1], parts[2], parts[3])


def parse_rgb(text):
    parts = [int(piece.strip()) for piece in text.split(",")]
    if len(parts) != 3:
        raise ValueError("--expect 需要 r,g,b 三个整数")
    return (parts[0], parts[1], parts[2])


def count_color(image, box, rgb, tol):
    region = image.crop(box)
    pixels = region.load()
    total = 0
    for y in range(region.height):
        for x in range(region.width):
            r, g, b = pixels[x, y][:3]
            if abs(r - rgb[0]) <= tol and abs(g - rgb[1]) <= tol and abs(b - rgb[2]) <= tol:
                total += 1
    return total


def main():
    parser = argparse.ArgumentParser(description="目标选择器 overlay 文字色值像素判据")
    parser.add_argument("--image", default=None, help="显式 PNG 路径（优先）")
    parser.add_argument("--tag", default=None, help="截图 tag（经 .mt_shots.json 取最后一张）")
    parser.add_argument("--run-dir", default=None, help="run 目录，缺省 <repo>/run/26.1.2")
    parser.add_argument("--color", default="hostile", choices=sorted(COLOR_TABLE.keys()))
    parser.add_argument("--expect", default=None, help="显式期望色 r,g,b（优先于 --color）")
    parser.add_argument("--min-pixels", type=int, default=20)
    parser.add_argument("--tol", type=int, default=0)
    parser.add_argument("--box", default=None)
    args = parser.parse_args()

    run_dir = args.run_dir or os.path.join(repo_root(), DEFAULT_RUN_REL)
    image_path = args.image
    if not image_path and args.tag:
        image_path = resolve_from_registry(run_dir, args.tag)
    if not image_path:
        print("verify_targetsel_overlay_color: 无法定位截图（需要 --image 或 --tag）")
        return 2
    if not os.path.isfile(image_path):
        print("verify_targetsel_overlay_color: 截图不存在: " + image_path)
        return 2

    expect = parse_rgb(args.expect) if args.expect else COLOR_TABLE[args.color]
    box = parse_box(args.box) if args.box else DEFAULT_BOX

    image = Image.open(image_path).convert("RGB")
    print("image=" + image_path)
    print("size=" + str(image.size) + " box=" + str(box) + " tol=" + str(args.tol))
    print("expect=" + str(expect) + " min_pixels=" + str(args.min_pixels))

    counts = {}
    for name in sorted(COLOR_TABLE.keys()):
        counts[name] = count_color(image, box, COLOR_TABLE[name], args.tol)
    for name in sorted(counts.keys()):
        print("count[" + name + "]=" + str(counts[name]))

    hit = count_color(image, box, expect, args.tol)
    if hit >= args.min_pixels:
        print("verify_targetsel_overlay_color: PASS — 目标色像素 " + str(hit) + " >= " + str(args.min_pixels))
        return 0
    print("verify_targetsel_overlay_color: FAIL — 目标色像素 " + str(hit) + " < " + str(args.min_pixels))
    return 1


if __name__ == "__main__":
    sys.exit(main())
