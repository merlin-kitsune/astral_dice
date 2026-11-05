#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""tools/gen_agents_toc.py - AGENTS-full.md 标题总表生成器 / 校验器.

背景：docs/agent/AGENTS-TOC.md 历史上是手算的（仓库内没有生成器），每次正文变动都会
让行号与字符偏移整体漂移。本脚本把它变成可重复生成的产物：

    行号      1 起
    字符偏移  按原始文件字符计，含 CRLF 的两个字符（sum(len(line)+2)），与旧表口径一致
    标题      行首 1-4 个 # + 空格，且不在 fenced code block 内

用法：
    python -B tools/gen_agents_toc.py --check    # 只比对，不一致 exit 1 并打印首个漂移项
    python -B tools/gen_agents_toc.py --write    # 重写文件（保留「生成日志」段并追加一行）
    python -B tools/gen_agents_toc.py            # 缺省 = --check
"""
import io
import os
import re
import sys
import time

BT = chr(96)          # 反引号，避免源码里出现裸的反引号
FENCE = BT * 3
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "docs", "agent", "AGENTS-full.md")
TOC = os.path.join(ROOT, "docs", "agent", "AGENTS-TOC.md")

HEADING_RE = re.compile(r"^#{1,4} \S")
ROW_RE = re.compile(r"^\| L(\d+) \| (\d+) \| (.*) \|$")
LOG_HEADER = "## 生成日志"


def headings(path):
    """返回 [(行号, 字符偏移, 标题文本)]；偏移按 CRLF 口径累计。"""
    with io.open(path, "rb") as fh:
        raw = fh.read()
    lines = raw.split(b"\n")
    out = []
    offset = 0
    in_fence = False
    for idx, rawline in enumerate(lines, start=1):
        text = rawline[:-1] if rawline.endswith(b"\r") else rawline
        decoded = text.decode("utf-8", "replace")
        stripped = decoded.strip()
        if stripped.startswith(FENCE) or stripped.startswith("~~~"):
            in_fence = not in_fence
        elif not in_fence and HEADING_RE.match(decoded):
            out.append((idx, offset, decoded.rstrip()))
        offset += len(decoded) + 2
    return out


def render(rows, total_lines):
    stamp = time.strftime("%Y-%m-%d %H:%M")
    c = BT
    head = [
        "# AGENTS-full.md 标题总表（由 tools/gen_agents_toc.py 生成，勿手改）",
        "",
        "- 生成：%s（%spython -B tools/gen_agents_toc.py --write%s）；来源 %sdocs/agent/AGENTS-full.md%s：**%d 行 / %d 个标题**。"
        % (stamp, c, c, c, c, total_lines, len(rows)),
        "- 口径：行号 1 起；**字符偏移按原始文件字符计、含 CRLF 的两个字符**（%ssum(len(line)+2)%s）；"
        "标题 = 行首 1-4 个 %s#%s + 空格，且不计 fenced code block 内的行。" % (c, c, c, c),
        "- 用法：查表拿行号后 %ssed -n 'a,bp' docs/agent/AGENTS-full.md%s；"
        "漂移判据 %spython -B tools/gen_agents_toc.py --check%s（exit 0 = 一致）。" % (c, c, c, c),
        "",
        "| 行号 | 字符偏移 | 标题 |",
        "|---|---|---|",
    ]
    body = ["| L%d | %d | %s |" % (ln, off, title) for ln, off, title in rows]
    return "\n".join(head + body) + "\n"


def split_log(text):
    """拆成 (表部分, 生成日志部分)；日志段不存在时返回 (text, None)。"""
    pos = text.find("\n" + LOG_HEADER)
    if pos < 0:
        return text, None
    return text[: pos + 1], text[pos + 1 :]


def main(argv):
    mode = "check"
    for arg in argv[1:]:
        if arg in ("--check", "--write"):
            mode = arg[2:]
        elif arg in ("-h", "--help"):
            print(__doc__)
            return 0
        else:
            print("TOC: ERROR 未知参数 %s" % arg)
            return 2

    if not os.path.isfile(SRC):
        print("TOC: FAIL 找不到 %s" % SRC)
        return 1
    with io.open(SRC, "rb") as fh:
        total_lines = fh.read().count(b"\n")
    rows = headings(SRC)
    table = render(rows, total_lines)

    if not os.path.isfile(TOC):
        if mode == "write":
            with io.open(TOC, "w", encoding="utf-8", newline="\n") as fh:
                fh.write(table)
            print("TOC: OK written=%s headings=%d lines=%d" % (TOC, len(rows), total_lines))
            return 0
        print("TOC: FAIL 目标不存在（先 --write）")
        return 1

    with io.open(TOC, "r", encoding="utf-8", newline="") as fh:
        current = fh.read().replace("\r\n", "\n")

    if mode == "check":
        cur_table, _ = split_log(current)
        cur_rows = [m for m in (ROW_RE.match(l) for l in cur_table.split("\n")) if m]
        gen_rows = [m for m in (ROW_RE.match(l) for l in table.split("\n")) if m]
        if len(cur_rows) == len(gen_rows) and all(
            (a.group(1), a.group(2), a.group(3)) == (b.group(1), b.group(2), b.group(3))
            for a, b in zip(cur_rows, gen_rows)
        ):
            print("TOC: OK headings=%d lines=%d" % (len(rows), total_lines))
            return 0
        print("TOC: DRIFT 表内 %d 行 / 实际 %d 个标题" % (len(cur_rows), len(gen_rows)))
        for idx, (a, b) in enumerate(zip(cur_rows, gen_rows)):
            if (a.group(1), a.group(2), a.group(3)) != (b.group(1), b.group(2), b.group(3)):
                print("TOC: DRIFT#%d 表内 L%s @%s %s -> 实际 L%s @%s %s"
                      % (idx + 1, a.group(1), a.group(2), a.group(3), b.group(1), b.group(2), b.group(3)))
                break
        if len(gen_rows) > len(cur_rows):
            extra = gen_rows[len(cur_rows)]
            print("TOC: DRIFT 首个缺失行 -> L%s %s" % (extra.group(1), extra.group(3)))
        print("TOC: 修复 = python -B tools/gen_agents_toc.py --write")
        return 1

    _, log = split_log(current)
    if log is None:
        log = LOG_HEADER + "\n\n"
    entry = "- %s：%s--write%s 重算（%d 行 / %d 个标题）。" % (time.strftime("%Y-%m-%d %H:%M"), BT, BT, total_lines, len(rows))
    log = log.rstrip("\n") + "\n" + entry + "\n"
    with io.open(TOC, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(table + "\n" + log)
    print("TOC: OK written=%s headings=%d lines=%d" % (TOC, len(rows), total_lines))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
