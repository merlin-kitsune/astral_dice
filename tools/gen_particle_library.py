#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成「铁-艾尔登法环像素粒子库」的四线贴图 / 粒子清单 / 变体常量。

素材：https://github.com/shuimo0413/Iron-Elden-Ring-Particle-library
      MIT License, Copyright (c) 2026 shuimo0413（全文见 docs/third-party/）。

产物（四条线各一套、内容一致 —— 本仓没有共享 resources 机制，故每线各拷一份）：
  <线>/src/main/resources/assets/astral_dice/textures/particle/ier/*.png
  <线>/src/main/resources/assets/astral_dice/particles/particle_library.json
  <线>/src/main/java/com/merlinkitsune/astral_dice/particle/LibraryVariants.java

用法：python tools/gen_particle_library.py --source temp/ier-particles [--check]
幂等：重复运行无改动；ier/ 里源目录已删掉的旧贴图会被清理。
"""
from __future__ import annotations

import argparse
import re
import shutil
from pathlib import Path
REPO = Path(__file__).resolve().parents[1]
LINES = ("neoforge-1.21.1", "forge-1.20.1", "fabric-1.20.1", "neoforge-26.1.2")
ASSETS = "src/main/resources/assets/astral_dice"
PACKAGE = "src/main/java/com/merlinkitsune/astral_dice/particle"
SUBDIR = "ier"
JSON_NAME = "particle_library.json"
SKIP = ("粒子库预览", "preview")


def scan(source):
    """源目录根下的粒子贴图（按文件名排序，排除展示图）。"""
    pngs = [p for p in source.glob("*.png") if not any(s in p.name for s in SKIP)]
    return sorted(pngs, key=lambda p: p.name)


def const_name(stem):
    return re.sub(r"[^0-9A-Za-z]+", "_", stem).upper()


def particle_json(names):
    rows = ["{", '  "textures": [']
    for index, name in enumerate(names):
        tail = "" if index == len(names) - 1 else ","
        rows.append('    "astral_dice:%s/%s"%s' % (SUBDIR, name, tail))
    rows += ["  ]", "}"]
    return "\n".join(rows) + "\n"
def variants_java(names):
    rows = [
        "package com.merlinkitsune.astral_dice.particle;",
        "",
        "/**",
        " * 「铁-艾尔登法环像素粒子库」变体索引 —— **由 {@code tools/gen_particle_library.py} 生成，请勿手改**。",
        " *",
        " * <p>每个常量对应 {@code assets/astral_dice/textures/particle/ier/<名字>.png} 一张 32×32 贴图；",
        " * 客户端按同一下标从 {@code assets/astral_dice/particles/particle_library.json} 的清单里取图。",
        " *",
        " * <p>素材：https://github.com/shuimo0413/Iron-Elden-Ring-Particle-library",
        " * （MIT License, Copyright (c) 2026 shuimo0413）。",
        " */",
        "public final class LibraryVariants {",
        "    /** 贴图张数（= {@link #NAMES} 长度 = json 条目数）。 */",
        "    public static final int COUNT = %d;" % len(names),
        "",
    ]
    for index, name in enumerate(names):
        rows.append("    /** {@code %s.png} */" % name)
        rows.append("    public static final int %s = %d;" % (const_name(name), index))
    rows += ["", "    /** 下标 → 贴图名（与常量、json 清单同序）。 */", "    public static final String[] NAMES = {"]
    buffer = "            "
    for name in names:
        piece = '"%s", ' % name
        if len(buffer) + len(piece) > 118:
            rows.append(buffer.rstrip())
            buffer = "            "
        buffer += piece
    if buffer.strip():
        rows.append(buffer.rstrip())
    rows += [
        "    };",
        "",
        "    private LibraryVariants() {",
        "    }",
        "",
        "    /** 越界自动回绕的下标 → 贴图名（日志/诊断用）。 */",
        "    public static String name(int variant) {",
        "        return NAMES[Math.floorMod(variant, NAMES.length)];",
        "    }",
        "}",
    ]
    return "\n".join(rows) + "\n"
def write_if_changed(path, text, check):
    """内容不同才写盘（保持生成器幂等，不制造无意义 diff）。"""
    old = path.read_text(encoding="utf-8") if path.exists() else None
    if old == text:
        return False
    if check:
        print("  [check] 需更新 " + str(path.relative_to(REPO)))
        return True
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("  [write] " + str(path.relative_to(REPO)) + " (%d 字符)" % len(text))
    return True
def main():
    parser = argparse.ArgumentParser(description="Astral Dice 粒子库资源与常量生成器")
    parser.add_argument("--source", default="temp/ier-particles", help="含 *.png 的素材目录")
    parser.add_argument("--check", action="store_true", help="只校验、不写盘")
    args = parser.parse_args()

    source = (REPO / args.source).resolve()
    if not source.is_dir():
        print("ERROR 素材目录不存在：" + str(source))
        return 2
    pngs = scan(source)
    if not pngs:
        print("ERROR 素材目录里没有 PNG：" + str(source))
        return 2
    names = [p.stem for p in pngs]
    print("素材 %s：%d 张贴图" % (source.name, len(names)))

    copied = removed = written = 0
    for line in LINES:
        root = REPO / line
        if not root.is_dir():
            print("ERROR 缺失线目录：" + line)
            return 2
        print("[" + line + "]")
        texture_dir = root / ASSETS / "textures/particle" / SUBDIR
        if not args.check:
            texture_dir.mkdir(parents=True, exist_ok=True)
        wanted = set()
        for png in pngs:
            wanted.add(png.name)
            target = texture_dir / png.name
            if target.exists() and target.read_bytes() == png.read_bytes():
                continue
            if args.check:
                print("  [check] 需拷贝 " + png.name)
            else:
                shutil.copyfile(png, target)
            copied += 1
        if texture_dir.is_dir():
            for old in sorted(texture_dir.glob("*.png")):
                if old.name not in wanted:
                    print(("  [check] 需删除 " if args.check else "  [del] ") + old.name)
                    if not args.check:
                        old.unlink()
                    removed += 1
        if write_if_changed(root / ASSETS / "particles" / JSON_NAME, particle_json(names), args.check):
            written += 1
        if write_if_changed(root / PACKAGE / "LibraryVariants.java", variants_java(names), args.check):
            written += 1

    tail = "（--check：未写盘）" if args.check else ""
    print("总览：贴图 %d 张 / 拷贝 %d / 清理 %d / 文本写入 %d%s" % (len(names), copied, removed, written, tail))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
