#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tooltip 染色规则审计（规则见 docs/tooltip-color-rules.md）。

只读审计，不修改任何文件。检查项：

  R1  非时间数值未着黄（§e）
  R2  时间未着蓝（§9）      —— 形态 M:SS 与 N 秒 / Ns / N seconds
  R3  「<效果名> (<效果时间>)」未整段同色
  R1b 数值/时间的前后符号被留在染色区之外（+ - × ÷ ★ ~ 等）
  R5  行级 withStyle 与行内规则冲突的易错点（仅提示，不判失败）

例外（不报错）：§c 红色条目、行级语义色、§r 复位码、§f 按键提示行、
列表序号与标签序号（1. / 第一 / Curse 1 / T4）。

用法：
    python scripts/audit/tooltip_color_audit.py [--root .]

退出码：0 = 无违规；1 = 存在违规。
"""

from __future__ import annotations

import argparse
import io
import os
import re
import sys

SUBPROJECTS = ("neoforge-1.21.1", "forge-1.20.1")
LANGS = ("zh_cn.json", "en_us.json")

# 时间形态：M:SS（两位秒）、N 秒、Ns / N sec / N seconds
TIME_RE = re.compile(r"\d+:\d{2}|\d+\s*秒|\d+\s*(?:seconds?|secs?|s)\b")
# 取值符号（含在染色区内才算合规）
SIGN_CHARS = "+-×÷★~"

PAIR_RE = re.compile(r'^\s*"([^"]+)"\s*:\s*"((?:[^"\\]|\\.)*)"(,?)\s*$', re.M)


def read(path: str) -> str:
    with io.open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def parse_pairs(path: str):
    return [(m.group(1), m.group(2)) for m in PAIR_RE.finditer(read(path))]


def clean_with_codes(value: str):
    """返回 (去码文本, 与每个字符位置对应的颜色码)。"""
    value = value.replace("\\n", "\n")
    out, codes = [], []
    code = None
    i = 0
    while i < len(value):
        if value[i] == "§" and i + 1 < len(value):
            code = value[i:i + 2]
            i += 2
            continue
        out.append(value[i])
        codes.append(code)
        i += 1
    return "".join(out), codes


def is_exempt_run(text: str, code, pos: int) -> bool:
    """§c 红色条目、列表序号 / 标签序号等豁免内容。"""
    if code == "§c":
        return True
    after = text[pos:pos + 4]
    before = text[max(0, pos - 10):pos]
    if re.match(r"\d+\.\s", after):
        return True
    if before.rstrip().endswith("第"):
        return True
    if re.search(r"Curse\s+$", before):
        return True
    if re.search(r"(?:^|[\s（(])T$", before):
        return True
    return False


def audit_lang(path: str):
    problems = []
    for key, value in parse_pairs(path):
        if "tooltip" not in key and not key.startswith("effect."):
            continue
        text, codes = clean_with_codes(value)

        # R2：时间未着蓝
        for m in TIME_RE.finditer(text):
            code = codes[m.start()]
            if code == "§9" or is_exempt_run(text, code, m.start()):
                continue
            problems.append(("R2", key, code, m.group(0)))

        # R1：非时间数值未着黄
        for m in re.finditer(r"[+\-]?\d+(?:\.\d+)?\s*%?|★", text):
            if any(t.start() <= m.start() < t.end() for t in TIME_RE.finditer(text)):
                continue
            code = codes[m.start()]
            if code in ("§e", "§9") or is_exempt_run(text, code, m.start()):
                continue
            problems.append(("R1", key, code, m.group(0)))

        # R3：「名 (时间)」整段同色（排除 "For(2:00)" 这类时长范围前缀）
        for m in re.finditer(r"([^\s(（:：]+)\s*[(（](\d+:\d{2})[)）]", text):
            if m.group(1).lower() == "for":
                continue
            name_pos = m.start(1)
            if codes[name_pos] != codes[m.start(2)] or codes[name_pos] not in ("§9", "§c"):
                problems.append(("R3", key, codes[name_pos], m.group(0)))

        # R1b：取值符号被留在染色区之外（在原始文本上检查）
        for m in re.finditer(r"[%+×÷~]§[e9]|\-[%+×÷~]?§[e9]", value):
            problems.append(("R1b", key, "符号外置", value[m.start():m.end()]))
    return problems


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    args = ap.parse_args()

    total = 0
    for sub in SUBPROJECTS:
        for lang in LANGS:
            path = os.path.join(args.root, sub, "src/main/resources/assets/astral_dice/lang", lang)
            if not os.path.isfile(path):
                continue
            problems = audit_lang(path)
            if problems:
                print(f"--- {sub}/{lang}: {len(problems)} 处")
                for rule, key, code, tok in problems:
                    print(f"    [{rule}] {key}  code={code}  token={tok!r}")
            total += len(problems)

    print()
    print("tooltip 染色审计：", "PASS（无违规）" if total == 0 else f"FAIL（{total} 处违规）")
    return 0 if total == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
