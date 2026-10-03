#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_effect_icons.py —— 状态图标覆盖守门：**每个注册的 MOB_EFFECT 都必须有同名图标贴图**。

## 为什么需要它

MobEffect 的 HUD 图标是**按注册名查贴图**（`assets/<ns>/textures/mob_effect/<id>.png`）。
注册了效果却忘了放图，游戏不会报错、也不会崩 —— 玩家只会看到一个**紫黑方块**，
与「物品贴图缺失」同一类静默缺陷。2026-10-01 的「筹码没有状态图标」一轮里，
新增了 6 个指示器效果 + 6 张图标，正是这一对映射最容易漏掉一半的地方。

⇒ 判据：对四条线各自的 `effect/ModEffects.java`，抽取全部 `EFFECTS.register("<id>", …)` 的 id，
逐个断言 `<line>/src/main/resources/assets/astral_dice/textures/mob_effect/<id>.png` 存在且是合法 PNG。

⚠️ 平台差异：允许按线配置豁免（`ALLOW_MISSING`）。当前为空 —— 四条线均为 100% 覆盖；
将来若某线刻意复用原版图标（如 1.21 线的 `concealment` 沿用原版隐身图），
**必须显式登记在这里并写明理由**，不允许默默漏。

退出码：0 = 全覆盖；1 = 有缺口。
"""

from __future__ import annotations

import os
import re
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2", "fabric-1.20.1"]

# 线 -> 允许缺失的 effect id 集合（必须附理由；当前为空）
ALLOW_MISSING: dict[str, dict[str, str]] = {}

REGISTER_RE = re.compile(r'EFFECTS\.register\("([a-z0-9_]+)"')


def read_ids(line: str) -> list[str]:
    p = os.path.join(ROOT, line, "src/main/java/com/merlinkitsune/astral_dice/effect/ModEffects.java")
    with open(p, encoding="utf-8", errors="replace") as f:
        text = f.read()
    # 保持注册顺序、去重
    seen, out = set(), []
    for eid in REGISTER_RE.findall(text):
        if eid not in seen:
            seen.add(eid)
            out.append(eid)
    return out


def png_size(path: str):
    with open(path, "rb") as f:
        head = f.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    w, h = struct.unpack(">II", head[16:24])
    return w, h


def main() -> int:
    total_ids = 0
    problems: list[str] = []
    for line in LINES:
        ids = read_ids(line)
        total_ids += len(ids)
        texdir = os.path.join(ROOT, line, "src/main/resources/assets/astral_dice/textures/mob_effect")
        allow = ALLOW_MISSING.get(line, {})
        missing, bad, sizes = [], [], {}
        for eid in ids:
            if eid in allow:
                continue
            p = os.path.join(texdir, eid + ".png")
            if not os.path.isfile(p):
                missing.append(eid)
                continue
            sz = png_size(p)
            if sz is None:
                bad.append(eid)
            else:
                sizes[sz] = sizes.get(sz, 0) + 1
        covered = len(ids) - len(missing) - len(bad)
        size_txt = ", ".join("%dx%d×%d" % (w, h, n) for (w, h), n in sorted(sizes.items()))
        print("AP_EFFECT_ICONS: line=%-18s registered=%d covered=%d missing=%d bad=%d sizes=[%s]"
              % (line, len(ids), covered, len(missing), len(bad), size_txt))
        if missing:
            print("   MISSING: " + ", ".join(missing))
            problems.append("%s: 缺图标 %s" % (line, missing))
        if bad:
            print("   NOT_PNG: " + ", ".join(bad))
            problems.append("%s: 非法 PNG %s" % (line, bad))

    if problems:
        print()
        for p in problems:
            print("FAIL " + p)
        print("RESULT: FAIL —— 状态图标覆盖不完整（%d 处）" % len(problems))
        return 1
    print()
    print("RESULT: PASS —— 四线共 %d 个 MOD_EFFECT 注册，图标 100%% 覆盖" % total_ids)
    return 0


if __name__ == "__main__":
    sys.exit(main())
