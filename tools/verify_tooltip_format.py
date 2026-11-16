#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tooltip / 手册文案书写格式守门（docs/tooltip-color-rules.md 规则 5 的机器判据）。

检查项（跨 5 线 × zh_cn / en_us / ja_jp）：
  B1  专有名禁用【】，应改用「」（角色 / 物品 / 机制 / 状态效果 / 区域）
  B2  时长后缀必须半角括号，且括号前留一个空格：攻击力 §e+2§7 (§92:00§7)
  B3  半角 (M:SS) 前必须有空格（禁止 缓慢 II(0:30)）
  B4  罗马数字等级与中日文之间留一个空格：缓慢 II / 移動速度上昇 II

用法：
  python tools/verify_tooltip_format.py                 # 五线全查
  python tools/verify_tooltip_format.py --line 1.21.1   # 单线（可重复）
退出码：0 = 无违规；1 = 有违规；2 = 参数 / 文件错误。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LINES = [
    "neoforge-1.21.1",
    "forge-1.20.1",
    "neoforge-26.1.2",
    "fabric-1.20.1",
    "fabric-1.21.1",
]
LANGS = [("zh_cn", ""), ("en_us", "_"), ("ja_jp", "_")]  # 仅用于报告

SCOPES = ("tooltip.", "effect.", "astral_dice.guide.entry.")

RE_FAN = re.compile(r"[【】]")
RE_FULLWIDTH_TIME = re.compile(r"（\s*(?:§[0-9a-f])?[0-9]{1,2}:[0-9]{2}(?:§[0-9a-f])?\s*）")
RE_ASCII_TIME = re.compile(r"(?P<pre>.)\((?:§[0-9a-f])?[0-9]{1,2}:[0-9]{2}(?:§[0-9a-f])?\)")
RE_ROMAN = re.compile(r"(?<=[\u3040-\u9fff])(?:II|III|IV)(?![A-Za-z])")


def lang_files(line: str):
    base = ROOT / line / "src/main/resources/assets/astral_dice/lang"
    return sorted(base.glob("*.json"))


def scan_value(text: str):
    """返回 [(rule, detail), ...]。"""
    hits = []
    for m in RE_FAN.finditer(text):
        hits.append(("B1", "全角方头括号 %r（应改用「」）" % m.group(0)))
    for m in RE_FULLWIDTH_TIME.finditer(text):
        hits.append(("B2", "全角括号时长 %s（应写成半角且括号前留空格）" % m.group(0)))
    for m in RE_ASCII_TIME.finditer(text):
        pre = m.group("pre")
        if pre not in (" ", "(", "（", '"'):
            hits.append(("B3", "半角括号时长前缺空格：%s" % text[max(0, m.start() - 12):m.end() + 1]))
    for m in RE_ROMAN.finditer(text):
        hits.append(("B4", "罗马数字等级前缺空格：%s" % text[max(0, m.start() - 8):m.end() + 1]))
    return hits


def main() -> int:
    ap = argparse.ArgumentParser(description="tooltip / 手册文案书写格式守门")
    ap.add_argument("--line", action="append", default=None, help="只查指定线（可重复，支持 1.21.1 这类短名）")
    ap.add_argument("--json", action="store_true", help="以 JSON 输出")
    args = ap.parse_args()

    def norm(name: str):
        name = name.strip()
        if name in LINES:
            return name
        for ln in LINES:
            if ln.endswith(name) or name in ln:
                return ln
        return None

    lines = LINES
    if args.line:
        lines = []
        for raw in args.line:
            resolved = norm(raw)
            if resolved is None:
                print("TOOLTIP_FORMAT: ERROR — 未知线 %r（可选：%s）" % (raw, " ".join(LINES)))
                return 2
            lines.append(resolved)

    violations = []
    checked_files = 0
    checked_values = 0
    for line in lines:
        files = lang_files(line)
        if not files:
            print("TOOLTIP_FORMAT: ERROR — 找不到 lang 目录：%s" % line)
            return 2
        for path in files:
            checked_files += 1
            data = json.loads(path.read_text(encoding="utf-8"))
            for key, value in data.items():
                if not isinstance(value, str) or not key.startswith(SCOPES):
                    continue
                checked_values += 1
                for rule, detail in scan_value(value):
                    violations.append({
                        "line": line,
                        "file": str(path.relative_to(ROOT)).replace("\\", "/"),
                        "key": key,
                        "rule": rule,
                        "detail": detail,
                    })

    if args.json:
        print(json.dumps({"violations": violations, "checked": checked_values}, ensure_ascii=False, indent=2))
    else:
        for v in violations:
            print("TOOLTIP_FORMAT_VIOLATION: %s [%s] %s — %s" % (v["file"], v["rule"], v["key"], v["detail"]))
        if violations:
            print("TOOLTIP_FORMAT: FAIL violations=%d files=%d %s" % (len(violations), checked_files, " ".join(lines)))
            return 1
        print("TOOLTIP_FORMAT: OK checked=%d files=%d %s" % (checked_values, checked_files, " ".join(lines)))
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main())
