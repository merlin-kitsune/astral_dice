#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Audit the Patchouli handbook for **dangling translation-key references**.

背景（2026-10-01）：`multi-main` 在取消「效果待定」机制时删掉了 lang 键
`astral_dice.guide.entry.special_effects.6`，但手册条目
`entries/getting_started/special_effects.json` 的第 6 页仍引用它 ⇒
游戏内该页显示**原始键名**（玩家看到 `astral_dice.guide.entry.special_effects.6`），
且在 lang 三语一致性守门里**查不出来**（`check_lang_sync` 只比三语之间的键集，
不负责「手册引用 ⊆ lang」）。本脚本补的就是这条判据。

判据
----
遍历三线 `assets/astral_dice/patchouli_books/**/*.json` 的全部字符串值，
取出形如 `astral_dice.<分段.分段...>` 的**翻译键**（用 `.` 分级；
物品/类别 id 用 `:`，两者可据此区分），逐一核对同线三语 lang 是否都有该键。

退出码
------
0 = 全部命中；1 = 存在悬空引用（逐条打印 文件:行/键）。

用法
----
    python tools/audit_patchouli_keys.py            # 只看结论
    python tools/audit_patchouli_keys.py -v         # 打印全部被引用的键
"""
import argparse
import json
import pathlib
import re
import sys
# --- stdout/stderr 编码自愈（统一口径：python 侧强制 UTF-8；勿删）---
# 背景：Windows 默认代码页 936 下 python 子进程的 sys.stdout.encoding 为 gbk，
# 本脚本打印的 ⇒/⚠/✅/↔ 等符号 GBK 不可表示 ⇒ 直接 UnicodeEncodeError
# 退出码 1（“门会红但红的原因是崩溃”）。此块使脚本不依赖调用者环境与宿主编码。
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

# 输出编码固定为 UTF-8：脚本会打印 ✓ / ✅ / ❌ / ⇒ / ⊆ 等非 GBK 字符，
# 而 Windows 中文控制台与「重定向到文件」默认都是 GBK ⇒ 会抛 UnicodeEncodeError 并
# 以 RC=1 退出（看着像门禁失败，实际结论是 PASS）⇒ 在这里显式重配一次。
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

REPO = pathlib.Path(__file__).resolve().parent.parent
LINES = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2"]
BOOK = "src/main/resources/assets/astral_dice/patchouli_books"
LANG_DIR = "src/main/resources/assets/astral_dice/lang"
LANGS = ["zh_cn", "en_us", "ja_jp"]
# 翻译键判定：`<前缀.>?astral_dice.<分段.分段...>`，全部由 `.` 分级、且**不含 `:`**
#   ✅ astral_dice.guide.entry.teru_sign.3 / item.astral_dice.teru_sign / block.astral_dice.xxx
#   ❌ astral_dice:teru_sign（物品 id，用 `:`）/ astral_dice:getting_started（类别 id）
KEY_RE = re.compile(r"^(?:[a-z0-9_]+\.)*astral_dice\.[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*$")


def walk_strings(obj, path="$"):
    """递归产出 (JSON 路径, 字符串值)。"""
    if isinstance(obj, dict):
        for k, v in obj.items():
            yield from walk_strings(v, f"{path}.{k}")
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from walk_strings(v, f"{path}[{i}]")
    elif isinstance(obj, str):
        yield path, obj


def audit_line(line, verbose):
    root = REPO / line / BOOK
    lang_ctx = REPO / line / LANG_DIR
    if not root.is_dir():
        return [], [], f"{line}: 无手册目录（跳过）"
    lang_keys = {}
    for lg in LANGS:
        p = lang_ctx / f"{lg}.json"
        if not p.is_file():
            return [], [], f"{line}: 缺 {p.relative_to(REPO)}"
        lang_keys[lg] = set(json.loads(p.read_text(encoding="utf-8")).keys())

    refs = []   # (文件相对路径, JSON 路径, 键)
    for f in sorted(root.rglob("*.json")):
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
        except Exception as e:  # 让 JSON 语法错误显式暴露
            raise SystemExit(f"[ERR] {f.relative_to(REPO)} 解析失败：{e}")
        for jpath, value in walk_strings(data):
            if KEY_RE.match(value):
                refs.append((f.relative_to(REPO).as_posix(), jpath, value))

    missing = []
    for rel, jpath, key in refs:
        for lg in LANGS:
            if key not in lang_keys[lg]:
                missing.append((rel, jpath, key, lg))
    if verbose:
        for rel, jpath, key in refs:
            print(f"    {key}   <- {rel} {jpath}")
    return refs, missing, None


def main():
    ap = argparse.ArgumentParser(description="审计 Patchouli 手册的翻译键引用悬空")
    ap.add_argument("-v", "--verbose", action="store_true", help="打印全部被引用的键")
    args = ap.parse_args()

    total_refs = total_missing = 0
    for line in LINES:
        print(f"=== {line} ===")
        refs, missing, err = audit_line(line, args.verbose)
        if err:
            print("   ", err)
            continue
        total_refs += len(refs)
        total_missing += len(missing)
        if missing:
            for rel, jpath, key, lg in missing:
                print(f"  [MISS] {rel} {jpath}")
                print(f"        键 {key} 在 lang/{lg}.json 中不存在")
        else:
            print(f"   手册引用 {len(refs)} 处，全部命中三语 lang ✓")

    print("-" * 68)
    print(f"合计：引用 {total_refs} 处，悬空 {total_missing} 处")
    if total_missing:
        print("❌ 存在悬空引用（游戏内会显示原始键名）。修法：删掉该页/改键，"
              "或补回 lang（三线 × 三语都要一致）。")
        return 1
    print("✅ 无悬空引用。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
