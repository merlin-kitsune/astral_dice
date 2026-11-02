#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""test_mt_syntax —— 静态语法闸门（python 等价移植 scripts/devtools/Test-MtSyntax.ps1）。

对 scripts/ 与 tools/ 下的（默认）：
  · `.ps1` / `.psm1` → 结构化语法检查（词法级：字符串 / 块注释 / 括号配平）；
  · `.js`            → Node 语法自检（`node --check`）。
两个分支都**不执行**任何脚本内容。

为什么 .js 必须纳入（沿用 ps1 结论）：`scripts/test/resources/kubejs/**` 的探针是「用字符串拼
命令名 + 大量立即执行闭包」的代码，历史上出现过**函数尾闭合被吞**（少一个括号）但整文件仍能被
KubeJS 加载、命令静默不注册的事故 ⇒ `node --check` 是构建前唯一能拦住它的手段。

机器行（逐字保留，供 CI 解析）：
    PARSE OK    <相对路径>
    PARSE FAIL  <相对路径>          + 每条错误一行 `            L<行>:<列>  <消息>`
    PARSE SKIP  <相对路径>  (node 不在 PATH)
    MT_ERROR: 未找到任何 .ps1/.psm1/.js（已查：<目录列表>）      （写 stderr）
    RESULT: <总数> 个文件（ps1/psm1=<n> / js=<n>），解析失败 <n> 个，跳过 <n> 个

用法：
    python scripts/devtools/test_mt_syntax.py
    python scripts/devtools/test_mt_syntax.py --path scripts/test      # 也接受 -Path
    python scripts/devtools/test_mt_syntax.py -Path scripts tools /some/where

退出码：0 = 全部 0 错误；1 = 有语法错误；2 = 没找到任何文件 / 参数错误。
⚠️ `node` 不在 PATH 时，.js 一律记为 `PARSE SKIP`（不改变退出码）—— 避免把「环境缺 node」
   误判成「代码有错」。

与原 ps1 的差异（详见文件末「移植差异」注释块）：`.ps1/.psm1` 分支不再走 PowerShell 解析器
（本仓正在退役 .ps1，且移植纪律禁止调用 pwsh），改用纯 python 的结构化检查；它只覆盖
「字符串/块注释未闭合、括号不配平」这一类，覆盖不到 cmdlet 名、语法关键字等语义级错误。
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from typing import Iterable, List, Sequence, Tuple

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))

PS_EXTS = (".ps1", ".psm1")
JS_EXTS = (".js",)
_EXCLUDE_RE = re.compile(r"[\\/](__pycache__|node_modules)[\\/]")

# Get-ChildItem（无 -Force）默认跳过 Hidden+System 项
_FILE_ATTRIBUTE_HIDDEN = 0x2
_FILE_ATTRIBUTE_SYSTEM = 0x4


def _visible(path: str) -> bool:
    try:
        attrs = os.stat(path).st_file_attributes  # type: ignore[attr-defined]
    except (AttributeError, OSError):
        return True
    return not (attrs & (_FILE_ATTRIBUTE_HIDDEN | _FILE_ATTRIBUTE_SYSTEM))


def _collation_key(name: str) -> str:
    # Windows 文件名排序（Get-ChildItem 用的 culture-aware 比较）与 python 的码位序不同：
    # 下划线 `_` 的权重低于数字与 `-`/`.`，于是 `mt_watchdog.ps1` < `mt.ps1`、
    # `ft_assert.ps1` < `ft.ps1`（python 默认相反）。把 `_` 压到 \x01 可逐字复现（已用 pwsh
    # 在 9 个样本名上实测比对）。大小写不敏感同理（NTFS 本身不区分大小写）。
    return name.replace("_", "\x01").lower()


def _sorted_names(names: Iterable[str]) -> List[str]:
    return sorted(names, key=_collation_key)


def walk_files(dir_path: str) -> List[str]:
    """复刻 `Get-ChildItem -LiteralPath <dir> -Recurse -File` 的文件顺序。

    PowerShell 的 -Recurse 是「先列本目录文件，再按名称顺序钻进子目录」，故此处用同样的
    深度优先（前序）遍历，保证输出顺序与 ps1 一致。
    """
    out: List[str] = []

    def _visit(cur: str) -> None:
        try:
            entries = list(os.scandir(cur))
        except OSError:
            return
        files = [e for e in entries if e.is_file(follow_symlinks=False) and _visible(e.path)]
        dirs = [e for e in entries if e.is_dir(follow_symlinks=False) and _visible(e.path)]
        for e in _sorted_names([e.name for e in files]):
            out.append(os.path.join(cur, e))
        for name in _sorted_names([e.name for e in dirs]):
            _visit(os.path.join(cur, name))

    _visit(dir_path)
    return out


def rel_to_root(full: str) -> str:
    prefix = REPO_ROOT + os.sep
    if full.startswith(prefix):
        return full[len(prefix):]
    # ps1 用 Substring($root.Length + 1) 会切出垃圾串（仓库外路径）；这里退回绝对路径更安全
    return full


# ---------------------------------------------------------------------------
# .ps1 / .psm1：结构化（词法级）检查
#   覆盖：'…' / "…" / @'…'@ / @"…"@ 未闭合、<# … #> 未闭合、()[]{} 不配平
#   不覆盖：cmdlet 名、关键字、参数绑定等语义级错误（PowerShell 解析器才有）
# ---------------------------------------------------------------------------
_OPENERS = {"(": ")", "[": "]", "{": "}"}
_CLOSERS = {v: k for k, v in _OPENERS.items()}


def scan_powershell(src: str) -> List[Tuple[int, int, str]]:
    errors: List[Tuple[int, int, str]] = []
    line, col = 1, 1
    stack: List[Tuple[str, int, int]] = []
    i, n = 0, len(src)

    def advance(chunk: str) -> None:
        nonlocal line, col
        for ch in chunk:
            if ch == "\n":
                line += 1
                col = 1
            else:
                col += 1

    while i < n:
        ch = src[i]
        two = src[i:i + 2]

        # 块注释 <# … #>
        if two == "<#":
            end = src.find("#>", i + 2)
            if end < 0:
                errors.append((line, col, "缺少 #> 闭合的块注释 <# … #>"))
                return errors
            advance(src[i:end + 2])
            i = end + 2
            continue

        # 行注释
        if ch == "#":
            nl = src.find("\n", i)
            if nl < 0:
                return errors
            advance(src[i:nl])
            i = nl
            continue

        # here-string @' … '@ / @" … "@ （闭合符必须在行首）
        if two in ("@'", '@"'):
            quote = two[1]
            # 规范要求 @' / @" 之后必须紧跟换行
            nl = src.find("\n", i)
            if nl < 0:
                errors.append((line, col, "here-string 未闭合（缺少 %s@）" % quote))
                return errors
            closer = re.compile(r"(?m)^[ \t]*" + re.escape(quote + "@"))
            m = closer.search(src, nl + 1)
            if m is None:
                errors.append((line, col, "here-string 未闭合（缺少 %s@）" % quote))
                return errors
            advance(src[i:m.end()])
            i = m.end()
            continue

        # 单引号字符串：'' 表示一个引号
        if ch == "'":
            j = i + 1
            while True:
                if j >= n:
                    errors.append((line, col, "单引号字符串未闭合（缺少 '）"))
                    return errors
                if src[j] == "'":
                    if src[j:j + 2] == "''":
                        j += 2
                        continue
                    j += 1
                    break
                j += 1
            advance(src[i:j])
            i = j
            continue

        # 双引号字符串：反引号转义 / "" 表示一个引号
        if ch == '"':
            j = i + 1
            while True:
                if j >= n:
                    errors.append((line, col, "双引号字符串未闭合（缺少 \"）"))
                    return errors
                c = src[j]
                if c == "`":
                    j += 2
                    continue
                if c == '"':
                    if src[j:j + 2] == '""':
                        j += 2
                        continue
                    j += 1
                    break
                j += 1
            advance(src[i:j])
            i = j
            continue

        if ch in _OPENERS:
            stack.append((ch, line, col))
            advance(ch)
            i += 1
            continue
        if ch in _CLOSERS:
            if not stack:
                errors.append((line, col, "多余的 %s（没有配对的 %s）" % (ch, _CLOSERS[ch])))
                return errors
            opener, oline, ocol = stack.pop()
            if opener != _CLOSERS[ch]:
                errors.append((line, col, "%s 与第 %d 行第 %d 列的 %s 不配对" % (ch, oline, ocol, opener)))
                return errors
            advance(ch)
            i += 1
            continue

        advance(ch)
        i += 1

    for opener, oline, ocol in stack:
        errors.append((oline, ocol, "%s 未闭合（缺少 %s）" % (opener, _OPENERS[opener])))
    return errors


def describe_ps_line(rel: str, errors: Sequence[Tuple[int, int, str]]) -> None:
    print("PARSE FAIL  " + rel)
    for ln, cl, msg in errors:
        print("            L%d:%d  %s" % (ln, cl, msg))


# ---------------------------------------------------------------------------

def normalize_argv(argv: Sequence[str], known: Sequence[str]) -> List[str]:
    """兼容 pwsh 风格单破折号命名参数：`-Path val` / `-Path=val` → `--path val`。"""
    known_l = {k.lower() for k in known}
    out: List[str] = []
    for tok in argv:
        if len(tok) > 1 and tok[0] == "-" and tok[1] != "-":
            name, sep, val = tok[1:].partition("=")
            if name.lower() in known_l:
                out.append("--" + name.lower() + sep + val)
                continue
        out.append(tok)
    return out


def build_parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(
        prog="test_mt_syntax.py",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        description="静态语法闸门：.ps1/.psm1 结构化检查 + .js 的 node --check（不执行脚本内容）。",
        epilog=(
            "退出码：0 = 全部 0 错误；1 = 有语法错误；2 = 没找到任何文件 / 参数错误。\n"
            "缺省扫描 scripts 与 tools（等价 pwsh: Test-MtSyntax.ps1 的 -Path 缺省值）。"
        ),
    )
    ap.add_argument(
        "-p", "--path", action="append", nargs="+", metavar="DIR",
        help="要扫描的目录（相对仓库根或绝对路径；可重复给多次、一次给多个）。缺省：scripts tools",
    )
    return ap


def main(argv: Sequence[str]) -> int:
    ap = build_parser()
    args = ap.parse_args(normalize_argv(argv, ("path", "help")))

    raw = args.path if args.path else [["scripts", "tools"]]
    roots: List[str] = [x for group in raw for x in group]

    ps_files: List[str] = []
    js_files: List[str] = []
    for p in roots:
        abs_p = p if os.path.isabs(p) else os.path.join(REPO_ROOT, p)
        if not os.path.exists(abs_p):
            continue
        found = [f for f in walk_files(abs_p) if not _EXCLUDE_RE.search(f)]
        ps_files += [f for f in found if os.path.splitext(f)[1].lower() in PS_EXTS]
        js_files += [f for f in found if os.path.splitext(f)[1].lower() in JS_EXTS]

    if (len(ps_files) + len(js_files)) == 0:
        sys.stderr.write("MT_ERROR: 未找到任何 .ps1/.psm1/.js（已查：%s）\n" % ", ".join(roots))
        sys.stderr.flush()
        return 2

    node = None
    for name in ("node", "node.exe"):
        # Get-Command node -ErrorAction SilentlyContinue
        from shutil import which as _which
        node = _which(name)
        if node:
            break
    bad = 0
    skip = 0

    for f in ps_files:
        rel = rel_to_root(f)
        try:
            with open(f, "r", encoding="utf-8-sig", errors="replace", newline="") as fh:
                src = fh.read()
        except OSError as exc:
            print("PARSE FAIL  " + rel)
            print("            L1:1  无法读取文件：%s" % exc)
            bad += 1
            continue
        errors = scan_powershell(src)
        if errors:
            bad += 1
            describe_ps_line(rel, errors)
        else:
            print("PARSE OK    " + rel)

    for f in js_files:
        rel = rel_to_root(f)
        if not node:
            skip += 1
            print("PARSE SKIP  " + rel + "  (node 不在 PATH)")
            continue
        proc = subprocess.run(
            [node, "--check", f], capture_output=True, text=True,
            encoding="utf-8", errors="replace",
        )
        if proc.returncode != 0:
            bad += 1
            print("PARSE FAIL  " + rel)
            merged = (proc.stderr or "") + (proc.stdout or "")
            for line in merged.splitlines():
                print("            " + line)
        else:
            print("PARSE OK    " + rel)

    total = len(ps_files) + len(js_files)
    print("RESULT: %d 个文件（ps1/psm1=%d / js=%d），解析失败 %d 个，跳过 %d 个"
          % (total, len(ps_files), len(js_files), bad, skip))
    return 0 if bad == 0 else 1


def _enable_utf8_stdout() -> None:
    """显式 UTF-8 + 显式 LF。

    本机控制台缺省是 GBK（不重配会把中文写成乱码）；而 Windows 上 print() 会把换行按
    os.linesep 翻成 CRLF —— ps1 侧是 [Console]::Out.Write("\u0060n") 的纯 LF，故
    newline="\n" 关掉翻译，保证 stdout 逐字节对齐。
    """
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(  # type: ignore[attr-defined]
                encoding="utf-8", errors="replace", newline="\n",
            )
        except (AttributeError, ValueError):
            pass


if __name__ == "__main__":
    _enable_utf8_stdout()
    sys.exit(main(sys.argv[1:]))
