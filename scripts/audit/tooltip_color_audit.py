#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Tooltip 染色规则审计（规则见 docs/tooltip-color-rules.md）。

python 等价移植 scripts/audit/tooltip_color_audit.ps1（原 .py 在 92fbeaf 删除，取回的是**旧版**
——那版只有 2 线 2 语、无 R0/R4；本文件按**现行 ps1** 移植，ps1 才是权威口径）。

只读审计，不修改任何文件。检查项：

  R1  非时间数值未着黄（§e）
  R2  时间未着蓝（§9）      —— 形态 M:SS 与 N 秒 / Ns / N seconds
  R3  「<效果名> (<效果时间>)」未整段同色
  R0  行内回落码 ≠ 本行底色码（§r 复位为无颜色=白、§7 复位为灰，都不是「恢复本行底色」）。
      行底色自 ModTooltipHandler.java 解析（tt("key"...).withStyle(ChatFormatting.X)）。
      仅当回落码后仍有文本（含换行之后的文本）时检查；值串末尾的回落码不判违规。
  R4  值内含 %%（字面百分号）却走 Component.translatable(...)：原版 TranslatableContents.
      decomposeTemplate 会把 %% 拆成独立的无样式 TEXT_PERCENT 片段，落在高亮区内的 % 会掉成
      行底色。必须走 tt(...)。
  R1b 数值/时间的前后符号被留在染色区之外（+ - × ÷ ★ ~ 等）

例外（不报错）：§c 红色条目、行级语义色、§f 按键提示行、列表序号与标签序号（1. / 第一 / Curse 1 / T4）。

用法：
    python scripts/audit/tooltip_color_audit.py            # 等价 ps1 的 -Root .
    python scripts/audit/tooltip_color_audit.py --root .   # 也接受 -Root / 位置参数

退出码（与 ps1 完全一致）：0 = 无违规；1 = 存在违规；2 = 无法判定（被审根下 lang 文件缺失 ⇒
覆盖度不足，不得当作通过）。2 由覆盖度守卫给出：missing > 0 的判定先于 checked == 0（两者都 2）。

与 ps1 的差异（仅措辞无关项）：
  · ps1 的 [CmdletBinding()] 对未知参数报 rc=1；本移植按移植纪律统一为 argparse 的 rc=2。
  · ps1 的 § 与正则走 .NET 引擎；这里用 python re 逐条等价重写（字符类、贪婪与 MULTILINE
    语义逐条对照过），不做「顺手优化」——例如 R3 仍取「紧邻左括号的末字符」色码，而非首字符。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from typing import Dict, List, Optional, Sequence, Tuple

SUBPROJECTS = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2", "fabric-1.20.1"]
# --- 覆盖矩阵声明（2026-10-06 校准；勿删）---
# 线：4 线全覆盖（原只 2 线，漏 neoforge-26.1.2 / fabric-1.20.1）
# 语：zh_cn / en_us / ja_jp 三语全覆盖（2026-10-06 Stage 2 纳入 ja_jp）
# ja_jp 口径（2026-10-06 逐条裁定；证据 temp/t62-stage2/）——
#   · R1 在日文上不具判别力：日文正文用「阿拉伯数字 + 量词/助词」计数
#     （1 回振り / 1 スタック / 2 倍 / ★レベル），与「要着黄的数值」同形。
#     ⇒ 仅当命中后 1 字为 CJK/假名，且（token 为 ★ 或 token 以空白结尾）时豁免；
#        数字紧贴 % 或紧贴 CJK 等真数值形态仍会判红（负控见 temp/t62-stage2/）。
#   · R3 原取「名字捕获串首字符」色码；CJK 无空格时捕获串会越过色码边界、吞到句内
#     （例 §e+2§7、§9移動速度上昇 (0:10)）⇒ 改取「紧邻 ( 的末字符」色码。
#   · R2/R0 无需豁免：ja_jp 的 R2 命中 36 处已由 Stage 1 修为 §9；
#     R0 命中 16 处（4 key × 4 线）为真违规，已同批修为 §r → **该键的行底色**（§6 / §6 / §c / §7，
#     逐键取自 ModTooltipHandler 的 withStyle；只有 GRAY 那键恰为 §7）。
#   · 残余已知局限：R1 对「未着色数字 + 空白 + CJK 词」形态不再报红（与日文散文计数不可区分）；
#     名字为混合色（首字符违规/末字符合规）时不再报 R3。
LANGS = ["zh_cn.json", "en_us.json", "ja_jp.json"]

# 时间形态：M:SS（两位秒）、N 秒、Ns / N sec / N seconds
TIME_RE = re.compile(r"(\d+:\d{2}|\d+\s*秒|\d+\s*(?:seconds?|secs?|s)\b)")
# 取值符号（含在染色区内才算合规）
SIGN_CHARS = "+-×÷★~"

PAIR_RE = re.compile(r'^\s*"([^"]+)"\s*:\s*"((?:[^"\\]|\\.)*)"(,?)\s*$', re.MULTILINE)

COLOR_CODE = {
    "BLACK": "§0", "DARK_BLUE": "§1", "DARK_GREEN": "§2", "DARK_AQUA": "§3",
    "DARK_RED": "§4", "DARK_PURPLE": "§5", "GOLD": "§6", "GRAY": "§7",
    "DARK_GRAY": "§8", "BLUE": "§9", "GREEN": "§a", "AQUA": "§b",
    "RED": "§c", "LIGHT_PURPLE": "§d", "YELLOW": "§e", "WHITE": "§f",
}

R1_RE = re.compile(r"[+\-]?\d+(?:\.\d+)?\s*%?|★")
R3_RE = re.compile(r"([^\s(（:：]+)\s*[(（](\d+:\d{2})[)）]")
R1B_RE = re.compile(r"[%+×÷~]§[e9]|\-[%+×÷~]?§[e9]")
FALLBACK_RE = re.compile(r"§[7r]")
CJK_AFTER_RE = re.compile("[\u3005\u3041-\u3096\u30A1-\u30FA\u4E00-\u9FFF\uFF66-\uFF9D]")
TOOLTIP_CALL_RE = re.compile(r'tt\(\s*"([^"]+)"')
TRANSLATABLE_CALL_RE = re.compile(r'Component\.translatable\(\s*"([^"]+)"')
WITH_STYLE_RE = re.compile(r"\.withStyle\(\s*ChatFormatting\.([A-Z_]+)")


def _enable_utf8_stdout() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(  # type: ignore[attr-defined]
                encoding="utf-8", errors="replace", newline="\n",
            )
        except (AttributeError, ValueError):
            pass


def write_out(text: str) -> None:
    sys.stdout.write(text + "\n")


def to_py_str(value) -> str:
    if value is None:
        return "None"
    if isinstance(value, bool):
        return "True" if value else "False"
    return str(value)


def to_py_repr(value) -> str:
    if value is None:
        return "None"
    if isinstance(value, bool):
        return "True" if value else "False"
    if isinstance(value, str):
        s = (value.replace("\\", "\\\\").replace("'", "\\'")
             .replace("\n", "\\n").replace("\r", "\\r"))
        return "'" + s + "'"
    if isinstance(value, dict):
        return "{" + ", ".join(to_py_repr(k) + ": " + to_py_repr(v) for k, v in value.items()) + "}"
    if isinstance(value, (list, tuple)):
        return "[" + ", ".join(to_py_repr(x) for x in value) + "]"
    return str(value)


def read_py_text(path: str) -> str:
    """等价 python 的 io.open(path, "r", encoding="utf-8").read()（含通用换行翻译）。"""
    with open(path, "rb") as fh:
        text = fh.read().decode("utf-8", errors="replace")
    return text.replace("\r\n", "\n").replace("\r", "\n")


def get_pairs(path: str) -> List[Tuple[str, str]]:
    return [(m.group(1), m.group(2)) for m in PAIR_RE.finditer(read_py_text(path))]


def clear_with_codes(value: str) -> Tuple[str, List[Optional[str]]]:
    """返回（去码文本, 与每个字符位置对应的颜色码）。"""
    value = value.replace("\\n", "\n")
    chars: List[str] = []
    codes: List[Optional[str]] = []
    code: Optional[str] = None
    i = 0
    n = len(value)
    while i < n:
        if value[i] == "§" and i + 1 < n:
            code = value[i:i + 2]
            i += 2
            continue
        chars.append(value[i])
        codes.append(code)
        i += 1
    return "".join(chars), codes


def get_tooltip_color_context(root: str) -> Tuple[Dict[str, Optional[str]], set]:
    """从两个子项目的 ModTooltipHandler.java 解析行底色与 translatable 调用面。"""
    codes: Dict[str, Optional[str]] = {}
    translatable = set()
    for sub in SUBPROJECTS:
        path = os.path.join(
            root, sub,
            "src/main/java/com/merlinkitsune/astral_dice/event/ModTooltipHandler.java")
        if not os.path.isfile(path):
            continue
        with open(path, "rb") as fh:
            src = fh.read().decode("utf-8", errors="replace")
        calls = [(TOOLTIP_CALL_RE, False), (TRANSLATABLE_CALL_RE, True)]
        for pattern, is_trans in calls:
            for m in pattern.finditer(src):
                key = m.group(1)
                if is_trans:
                    translatable.add(key)
                length = min(600, len(src) - m.end())
                if length <= 0:
                    continue
                tail = src[m.end():m.end() + length]
                cm = WITH_STYLE_RE.search(tail)
                if not cm:
                    continue
                code = COLOR_CODE.get(cm.group(1))
                if code is None:
                    continue
                if key not in codes:
                    codes[key] = code
                elif codes[key] != code:
                    codes[key] = None  # 同 key 多底色 → 不确定
    return codes, translatable


def test_fallback_code(value: str, base_code: str) -> List[str]:
    """规则 0：行内回落码（§7 / §r）必须等于本行底色码。

    §r 的实际渲染色是「无颜色 = 白」（StringDecomposer：defaultStyle = Style.EMPTY），
    §7 的实际渲染色是灰；两者都不等于本行底色时判违规。
    仅当回落码之后仍有文本（含换行之后的文本）时才检查。
    """
    out: List[str] = []
    for m in FALLBACK_RE.finditer(value):
        rest = value[m.end():]
        if len(rest.strip()) == 0:
            continue  # 值串末尾 → 不影响显示
        actual = m.group(0)
        if actual == "§r":
            actual = "§f"  # §r 渲染为白
        if actual != base_code:
            out.append(m.group(0))
    return out


def test_exempt_run(text: str, code: Optional[str], pos: int) -> bool:
    """§c 红色条目、列表序号 / 标签序号等豁免内容。"""
    if code == "§c":
        return True
    after = text[pos:pos + min(4, len(text) - pos)] if pos < len(text) else ""
    before = text[max(0, pos - 10):pos]
    if re.match(r"^\d+\.\s", after):
        return True
    if before.rstrip().endswith("第"):
        return True
    if re.search(r"Curse\s+$", before):
        return True
    if re.search(r"(?:^|[\s（(])T$", before):
        return True
    return False


def invoke_audit_lang(path: str, codes_by_key: Dict[str, Optional[str]],
                      translatable_keys: set) -> List[Tuple[str, str, object, object]]:
    problems: List[Tuple[str, str, object, object]] = []
    is_ja = path.endswith("ja_jp.json")
    for key, value in get_pairs(path):
        if ("tooltip" not in key) and (not key.startswith("effect.")):
            continue
        text, codes = clear_with_codes(value)
        time_matches = list(TIME_RE.finditer(text))

        # R2：时间未着蓝
        for m in time_matches:
            code = codes[m.start()] if m.start() < len(codes) else None
            if code == "§9":
                continue
            if test_exempt_run(text, code, m.start()):
                continue
            problems.append(("R2", key, code, m.group(0)))

        # R1：非时间数值未着黄
        for m in R1_RE.finditer(text):
            in_time = False
            for t in time_matches:
                if t.start() <= m.start() < t.end():
                    in_time = True
                    break
            if in_time:
                continue
            if is_ja:
                nxt = ""
                if m.end() < len(text):
                    nxt = text[m.end():m.end() + 1]
                is_cjk = bool(CJK_AFTER_RE.match(nxt)) and nxt != ""
                if is_cjk and ((m.group(0) == "★") or bool(re.search(r"\s$", m.group(0)))):
                    continue
            code = codes[m.start()] if m.start() < len(codes) else None
            if code in ("§e", "§9"):
                continue
            if test_exempt_run(text, code, m.start()):
                continue
            problems.append(("R1", key, code, m.group(0)))

        # R3：「名 (时间)」整段同色（排除 "For(2:00)" 这类时长范围前缀）
        for m in R3_RE.finditer(text):
            if m.group(1).lower() == "for":
                continue
            # 取「紧邻左括号的末字符」色码：CJK 无空格时捕获串会越过色码边界（原取首字符）
            name_pos = m.start(1) + len(m.group(1)) - 1
            time_pos = m.start(2)
            name_code = codes[name_pos] if 0 <= name_pos < len(codes) else None
            time_code = codes[time_pos] if 0 <= time_pos < len(codes) else None
            if (name_code != time_code) or (name_code not in ("§9", "§c")):
                problems.append(("R3", key, name_code, m.group(0)))

        # R0：行内回落码 ≠ 本行底色码
        if key in codes_by_key and codes_by_key[key] is not None:
            for bad in test_fallback_code(value, codes_by_key[key]):
                problems.append(("R0", key, bad, "base=" + codes_by_key[key]))

        # R4：值内含 %% 却走 Component.translatable(...)
        if "%%" in value and key in translatable_keys:
            problems.append(("R4", key, "translatable", "%% 需走 tt(...)"))

        # R1b：取值符号被留在染色区之外（在原始文本上检查）
        for m in R1B_RE.finditer(value):
            problems.append(("R1b", key, "符号外置", m.group(0)))

    return problems


def _normalize_dashes(argv: Sequence[str]) -> List[str]:
    """兼容 pwsh 风格单破折号（-Root → --root）；其它未知参数原样交给 argparse（rc=2）。"""
    out: List[str] = []
    for tok in argv:
        if tok in ("-Root", "-root"):
            out.append("--root")
        else:
            out.append(tok)
    return out


def main(argv: Optional[List[str]] = None) -> int:
    _enable_utf8_stdout()
    raw = list(sys.argv[1:] if argv is None else argv)
    ap = argparse.ArgumentParser(
        prog="tooltip_color_audit.py",
        description="只读审计 tooltip 染色规则（R0/R1/R1b/R2/R3/R4），等价 tooltip_color_audit.ps1。",
        epilog="退出码：0 无违规 / 1 有违规 / 2 无法判定（lang 文件缺失或未审到任何文件）",
    )
    ap.add_argument("root_pos", nargs="?", default=None, metavar="Root",
                    help="被审根目录（缺省 .，与 ps1 位置参数同义）")
    ap.add_argument("--root", "-Root", default=None, help="被审根目录（缺省 .）")
    args = ap.parse_args(_normalize_dashes(raw))

    root = args.root if args.root is not None else (args.root_pos if args.root_pos is not None else ".")

    total = 0
    checked = 0   # 实际审过的 lang 文件数
    missing = 0   # 不存在的 lang 文件数；>0 ⇒ 覆盖度不足（防「只审到 1/8 也判 PASS」的半坏假绿）
    codes_by_key, translatable_keys = get_tooltip_color_context(root)

    for sub in SUBPROJECTS:
        for lang in LANGS:
            path = os.path.join(root, sub, "src/main/resources/assets/astral_dice/lang", lang)
            if not os.path.isfile(path):
                missing += 1
                continue
            checked += 1
            problems = invoke_audit_lang(path, codes_by_key, translatable_keys)
            if problems:
                write_out("--- " + sub + "/" + lang + ": " + str(len(problems)) + " 处")
                for p in problems:
                    write_out("    [" + str(p[0]) + "] " + str(p[1])
                              + "  code=" + to_py_str(p[2])
                              + "  token=" + to_py_repr(p[3]))
            total += len(problems)

    write_out("")
    if missing > 0:
        write_out("tooltip 染色审计： ERROR（无法判定）—— 覆盖度不足：{0} 份 lang 文件不存在，"
                  "实审仅 {1} 份（期望 {2} 线 x {3} 语）".format(
                      missing, checked, len(SUBPROJECTS), len(LANGS)))
        sys.stdout.flush()
        return 2
    if checked == 0:
        write_out("tooltip 染色审计： ERROR（无法判定）—— 未审到任何 lang 文件（ROOT 错或路径约定变化）")
        sys.stdout.flush()
        return 2
    write_out("覆盖矩阵： " + " / ".join(SUBPROJECTS) + "  x  " + ", ".join(LANGS)
              + "  (实审 lang 文件 " + str(checked) + " 个)")
    verdict = "FAIL（" + str(total) + " 处违规）"
    if total == 0:
        verdict = "PASS（无违规）"
    write_out("tooltip 染色审计： " + verdict)
    sys.stdout.flush()
    return 0 if total == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
