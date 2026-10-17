#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成「粉丝数」效果指示器图标（`kangel_fan_indicator`）。

## 为什么要有这个脚本

超天酱立牌的目标标记（`kangel_fan`）与佩戴者的**计数指示器**在 2026-10-17 被拆成两个注册 id
（拆前同 id 导致超天酱互殴误判 -1、被标记者被自己的镜像清掉标记）。
两者若用**同一张**图标，玩家在 HUD 上仍分不清「我是被标了粉丝」还是「我的粉丝数显示」
—— 故指示器图标 = `images/粉丝.png` 的**视觉变体**：把角色轮廓最外 2 像素描成金色
（原图是 2× 放大的像素画 ⇒ 2 像素 = 稿纸上的 1 个逻辑像素；保留原图配色与五官，形状不动）。

## 产物（P0 两线各一份，逐字节相同）

    neoforge-1.21.1/src/main/resources/assets/astral_dice/textures/mob_effect/kangel_fan_indicator.png
    forge-1.20.1/src/main/resources/assets/astral_dice/textures/mob_effect/kangel_fan_indicator.png

⚠️ P1 两线（26.1.2 / fabric）尚未注册本效果，故不产出（照 `kangel_fan.png` 的先例）。

## 用法

    python tools/gen_kangel_fan_indicator_texture.py            # 写入 P0 两线
    python tools/gen_kangel_fan_indicator_texture.py --check    # 只校验（幂等，rc=1 = 需重生成）

判据：`--check` 退出码 0 = 两线贴图与生成器结果逐字节一致。
实现只用标准库（zlib/struct）—— 与 `tools/gen_event_indicator_textures.py` 同口径，不依赖 PIL。
"""

from __future__ import annotations

import hashlib
import os
import struct
import sys
import zlib
from collections import deque

# --- stdout/stderr 编码自愈（统一口径：python 侧强制 UTF-8；勿删）---
# 背景：Windows 默认代码页 936 下 python 子进程的 sys.stdout.encoding 为 gbk，
# 本脚本打印的 ⇒/⚠/✅/↔ 等符号 GBK 不可表示 ⇒ 直接 UnicodeEncodeError
# 退出码 1（"门会红但红的原因是崩溃"）。此块使脚本不依赖调用者环境与宿主编码。
# ⚠ 块内引用 sys ⇒ 必须位于**所有 import 之后**；也**不得**加 except-pass（会静默失效）。
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
SOURCE = os.path.join(ROOT, "images", "粉丝.png")
OUTPUTS = (
    os.path.join("neoforge-1.21.1", "src", "main", "resources",
                 "assets", "astral_dice", "textures", "mob_effect", "kangel_fan_indicator.png"),
    os.path.join("forge-1.20.1", "src", "main", "resources",
                 "assets", "astral_dice", "textures", "mob_effect", "kangel_fan_indicator.png"),
)
# 金色描边（与 32×32 原图的紫色描边、粉色主体都能区分开）；厚度 = 2 像素（= 原稿 1 个逻辑像素）
GOLD = (255, 205, 60, 255)
RING_TEXELS = 2


def _chunks(data: bytes):
    pos, out = 8, []
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        out.append((typ, data[pos + 8:pos + 8 + ln]))
        pos += 12 + ln
    return out


def read_png(path: str):
    """读 8-bit RGBA PNG ⇒ (w, h, rows[bytes])；rows 已反滤波。"""
    with open(path, "rb") as f:
        chunks = _chunks(f.read())
    ck = dict(chunks)
    # IDAT 可能分多块 ⇒ 必须按顺序拼接（只取最后一块会 zlib 解压失败）
    idat = b"".join(payload for typ, payload in chunks if typ == b"IDAT")
    w, h, depth, ctype = struct.unpack(">IIBB", ck[b"IHDR"][:10])
    if depth != 8 or ctype != 6:
        raise SystemExit("仅支持 8-bit RGBA PNG: %s (depth=%d type=%d)" % (path, depth, ctype))
    raw = zlib.decompress(idat)
    stride, prev, rows = w * 4, bytearray(w * 4), []
    pos = 0
    for _ in range(h):
        ft = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride
        for i in range(stride):
            a = line[i - 4] if i >= 4 else 0
            b = prev[i]
            c = prev[i - 4] if i >= 4 else 0
            if ft == 1:
                line[i] = (line[i] + a) & 0xFF
            elif ft == 2:
                line[i] = (line[i] + b) & 0xFF
            elif ft == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 0xFF
            elif ft == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        rows.append(bytes(line))
        prev = line
    return w, h, rows


def write_png(path: str, w: int, h: int, rows) -> None:
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(typ: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + typ
            + payload
            + struct.pack(">I", zlib.crc32(typ + payload) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    data = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def build():
    """原图（32×32）+ 金色轮廓描边（最外 RING_TEXELS 圈不透明像素改金）。"""
    w, h, rows = read_png(SOURCE)
    if (w, h) != (32, 32):
        raise SystemExit("源图必须是 32×32: %s (%dx%d)" % (SOURCE, w, h))
    opaque = [[rows[y][x * 4 + 3] > 0 for x in range(w)] for y in range(h)]

    # 到最近「透明像素」的切比雪夫距离（8 邻域 BFS）：轮廓描边按等厚处理，不会在斜角处变细
    dist = [[-1] * w for _ in range(h)]
    queue = deque()
    for y in range(h):
        for x in range(w):
            if not opaque[y][x]:
                dist[y][x] = 0
                queue.append((x, y))
    while queue:
        x, y = queue.popleft()
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and dist[ny][nx] < 0:
                    dist[ny][nx] = dist[y][x] + 1
                    queue.append((nx, ny))

    out = [bytearray(r) for r in rows]
    for y in range(h):
        for x in range(w):
            if opaque[y][x] and 0 < dist[y][x] <= RING_TEXELS:
                out[y][x * 4:x * 4 + 4] = bytes(GOLD)
    return w, h, [bytes(r) for r in out]


def main() -> int:
    check = "--check" in sys.argv
    w, h, rows = build()
    payload = b"".join(rows)
    digest = hashlib.sha1(payload).hexdigest()

    if check:
        ok = True
        for rel in OUTPUTS:
            path = os.path.join(ROOT, rel)
            if not os.path.isfile(path):
                print("MISSING " + rel)
                ok = False
                continue
            cw, ch, crows = read_png(path)
            same = (cw, ch) == (w, h) and b"".join(crows) == payload
            print(("OK      " if same else "STALE   ") + rel)
            if not same:
                ok = False
        print("EXPECTED_SHA1 " + digest)
        return 0 if ok else 1

    for rel in OUTPUTS:
        write_png(os.path.join(ROOT, rel), w, h, rows)
        print("WROTE " + rel)
    print("SHA1 " + digest)
    return 0


if __name__ == "__main__":
    sys.exit(main())
