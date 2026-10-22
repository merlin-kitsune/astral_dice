#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_chip_effect_icons.py —— 把「筹码状态效果图标」换成「无边框筹码」素材（幂等 + 可守门）。

## 背景（为什么会需要这个脚本）

MobEffect 的 HUD 图标是按注册名查贴图：assets/<ns>/textures/mob_effect/<id>.png。
历史上这批图标是**直接拷筹码物品图**（textures/item/<chip>_chip.png）的 —— 那张图带背包格子
边框，放大到 HUD 上就是「效果图标 = 筹码本身图标」，2026-10-22 用户判为不合格。

无边框素材早已在仓库里：images/shop/unbord_chips/<中文名>.png（32×32，与 mob_effect 同尺寸），
只是 2026-10 那轮「筹码无边框图标」只改了商店 UI（docs/chip-shop-ui-spec.md），没回写
textures/mob_effect/  ⇒ 效果 HUD 仍是带边框的旧图。本脚本补上这一步。

## 映射怎么来

**发现**（--apply / 首次运行）：
1. 遍历 <line>/.../textures/mob_effect/*.png，算 md5；
2. 与同线 textures/item/*.png 比 md5，**只接受** stem 以 _chip 结尾的命中
   （判据就是「效果图标直接抄了筹码物品图」这一事实，不靠人工清单）；
3. 该筹码的中文名取同线 lang/zh_cn.json 的 item.astral_dice.<stem>，
   源文件 = images/shop/unbord_chips/<中文名>.png；
4. 源图必须存在且为 32×32，否则计入 MISS ⇒ 退出码 1（不允许静默跳过）。

发现结果**落盘**到 tools/chip_effect_icon_map.json（人工可读、可 diff、可手改补充）。

**守门**（--check）：只读这份清单，逐个断言「目标图标 md5 == 无边框素材 md5」。
⚠️ 不能靠发现的启发式做守门：覆盖一次之后目标图就不再等于筹码物品图，启发式会「发现 0 个」
⇒ 守门变成永远绿的空跑。清单是守门的唯一事实源。

## 用法

    python tools/gen_chip_effect_icons.py                 # dry-run：列清单 + 差异
    python tools/gen_chip_effect_icons.py --apply         # 覆盖（幂等）+ 刷新清单
    python tools/gen_chip_effect_icons.py --check         # 守门：全部对齐才 0
    python tools/gen_chip_effect_icons.py --line fabric-1.20.1 --line neoforge-26.1.2

默认只处理 P0 两条线（neoforge-1.21.1 / forge-1.20.1）。P1（neoforge-26.1.2 /
fabric-1.20.1）自 2026-10 起在 1.5.0 正式版前冻结：需要时显式传 --line。

退出码：0 = 无差异 / 已全部对齐；1 = 有 MISS 或（--check 下）有偏差。
"""

from __future__ import annotations

import hashlib
import json
import os
import struct
import sys


# --- stdout/stderr 编码自愈（统一口径：python 侧强制 UTF-8；勿删）---
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
MANIFEST_PATH = os.path.join(ROOT, "tools", "chip_effect_icon_map.json")

P0_LINES = ["neoforge-1.21.1", "forge-1.20.1"]
P1_LINES = ["neoforge-26.1.2", "fabric-1.20.1"]

UNBORD_DIR = os.path.join(ROOT, "images", "shop", "unbord_chips")
CHIP_SUFFIX = "_chip"
ICON_SIZE = (32, 32)

MANIFEST_COMMENT = (
    "筹码状态效果图标 -> 无边框筹码素材的映射清单（守门唯一事实源）。"
    "effect = textures/mob_effect/<effect>.png；chip = 它原先抄的那个筹码物品 id；"
    "zh = 该筹码的中文名，对应 images/shop/unbord_chips/<zh>.png。"
    "由 tools/gen_chip_effect_icons.py --apply 生成；新增/修改请手改后重跑 --check。"
)


def assets_dir(line: str) -> str:
    return os.path.join(ROOT, line, "src", "main", "resources", "assets", "astral_dice")


def md5_of(path: str) -> str:
    h = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def png_size(path: str):
    with open(path, "rb") as f:
        head = f.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


def load_zh_lang(line: str) -> dict:
    """本仓库 lang 文件是扁平 key -> string（无嵌套、无注释）。"""
    p = os.path.join(assets_dir(line), "lang", "zh_cn.json")
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def pngs(dir_path: str) -> list:
    if not os.path.isdir(dir_path):
        return []
    return sorted(n for n in os.listdir(dir_path) if n.lower().endswith(".png"))


def load_manifest() -> list:
    if not os.path.isfile(MANIFEST_PATH):
        return []
    with open(MANIFEST_PATH, encoding="utf-8") as f:
        data = json.load(f)
    return list(data.get("entries") or [])


def save_manifest(entries: list) -> None:
    payload = {
        "_comment": MANIFEST_COMMENT,
        "entries": [
            {"effect": e["effect"], "chip": e["chip"], "zh": e["zh"]}
            for e in sorted(entries, key=lambda r: r["effect"])
        ],
    }
    with open(MANIFEST_PATH, "w", encoding="utf-8", newline="\n") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
        f.write("\n")


def discover(line: str, lang: dict) -> list:
    """启发式发现：效果图标与某个 *_chip 物品图字节相同 ⇒ 就是「抄了筹码图」。"""
    tex = os.path.join(assets_dir(line), "textures")
    eff_dir, item_dir = os.path.join(tex, "mob_effect"), os.path.join(tex, "item")

    item_by_md5 = {}
    for name in pngs(item_dir):
        item_by_md5.setdefault(md5_of(os.path.join(item_dir, name)), name[: -len(".png")])

    found = []
    for name in pngs(eff_dir):
        chip_stem = item_by_md5.get(md5_of(os.path.join(eff_dir, name)))
        if not chip_stem or not chip_stem.endswith(CHIP_SUFFIX):
            continue  # 立牌 / 卡面 / 独立美术的效果图标不在本脚本范围
        zh = lang.get("item.astral_dice." + chip_stem)
        if not zh:
            continue
        found.append({"effect": name[: -len(".png")], "chip": chip_stem, "zh": zh})
    return found


def resolve(line: str, entries: list):
    """把清单 + 发现结果落成 (rows, misses)。rows = [(effect, chip, zh, src, target_eff_path)]"""
    lang = load_zh_lang(line)
    tex = os.path.join(assets_dir(line), "textures", "mob_effect")
    merged = {e["effect"]: dict(e) for e in entries}
    # 只并入「清单里还没有」的发现项（清单是权威，手改过的 zh 不被覆盖）
    for e in discover(line, lang):
        merged.setdefault(e["effect"], e)

    rows, misses = [], []
    for effect in sorted(merged):
        e = merged[effect]
        zh = e.get("zh") or lang.get("item.astral_dice." + str(e.get("chip")))
        if not zh:
            misses.append((line, effect, e.get("chip", "-"), "无法确定中文名（lang 缺键）"))
            continue
        src = os.path.join(UNBORD_DIR, str(zh) + ".png")
        if not os.path.isfile(src):
            misses.append((line, effect, e.get("chip", "-"),
                           "无边框素材缺失 images/shop/unbord_chips/%s.png" % zh))
            continue
        size = png_size(src)
        if size != ICON_SIZE:
            misses.append((line, effect, e.get("chip", "-"), "素材尺寸 %r != %r" % (size, ICON_SIZE)))
            continue
        target = os.path.join(tex, effect + ".png")
        if not os.path.isfile(target):
            misses.append((line, effect, e.get("chip", "-"), "目标图标不存在 %s" % target))
            continue
        rows.append((effect, str(e.get("chip", "-")), str(zh), src, target))
    return rows, misses


def main(argv: list) -> int:
    lines = list(P0_LINES)
    apply_changes = False
    check_only = False
    explicit_lines = False

    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == "--apply":
            apply_changes = True
        elif arg == "--check":
            check_only = True
        elif arg == "--line":
            if i + 1 >= len(argv):
                sys.stderr.write("--line 需要一个值\n")
                return 1
            if not explicit_lines:
                lines, explicit_lines = [], True
            lines.append(argv[i + 1])
            i += 1
        elif arg in ("-h", "--help"):
            sys.stdout.write(__doc__ or "")
            return 0
        else:
            sys.stderr.write("未知参数：%s（用 --help 看用法）\n" % arg)
            return 1
        i += 1

    if apply_changes and check_only:
        sys.stderr.write("--apply 与 --check 互斥\n")
        return 1

    mode = "check" if check_only else ("apply" if apply_changes else "dry-run")
    print("== gen_chip_effect_icons: mode=%s lines=%s" % (mode, ",".join(lines)))
    print("   manifest=%s entries=%d" % (os.path.relpath(MANIFEST_PATH, ROOT), len(load_manifest())))
    if not explicit_lines:
        print("   注：P1 线（%s）自 2026-10 起冻结，未处理；需要时显式 --line" % " / ".join(P1_LINES))

    entries = load_manifest()
    effects = written = unaligned = 0
    all_misses = []
    seen = {e["effect"]: e for e in entries}

    for line in lines:
        if not os.path.isdir(assets_dir(line)):
            all_misses.append((line, "-", "-", "线目录不存在：%s" % assets_dir(line)))
            continue
        rows, misses = resolve(line, entries)
        all_misses.extend(misses)
        print("\n-- %s: %d 个筹码类效果图标" % (line, len(rows)))
        for effect, chip, zh, src, target in rows:
            effects += 1
            src_md5, eff_md5 = md5_of(src), md5_of(target)
            same = src_md5 == eff_md5
            print("   [%s] %-26s <= %-24s (%s)" % ("OK  " if same else "DIFF", effect, chip, zh))
            for e in discover(line, load_zh_lang(line)) if apply_changes else []:
                seen.setdefault(e["effect"], e)
            if same:
                continue
            if apply_changes:
                with open(src, "rb") as f:
                    data = f.read()
                with open(target, "wb") as f:
                    f.write(data)
                written += 1
            else:
                unaligned += 1

    if apply_changes:
        # 发现结果全部并入清单（含「本轮已经对齐、启发式再也发现不到」的项）
        for line in lines:
            if not os.path.isdir(assets_dir(line)):
                continue
            for e in discover(line, load_zh_lang(line)):
                seen.setdefault(e["effect"], e)
        save_manifest(list(seen.values()))
        print("\n   manifest written: %d entries" % len(seen))

    if all_misses:
        print("\n!! MISS %d 处：" % len(all_misses))
        for line, eff, chip, why in all_misses:
            print("   %s | %s | %s | %s" % (line, eff, chip, why))

    print("\n== summary: effects=%d misses=%d %s=%d" % (
        effects, len(all_misses),
        "written" if apply_changes else "unaligned",
        written if apply_changes else unaligned))
    if all_misses:
        return 1
    if check_only and unaligned:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
