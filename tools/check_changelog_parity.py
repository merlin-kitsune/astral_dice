#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CHANGELOG（英文）<-> CHANGELOG_ZH（中文）逐段一致性闸门。

判据：
  P1  两份文件的 `## ` 标题集合相同（顺序不要求一致，差异逐条报出）；
  P2  每个 `## ` 段的**行首 `- ` 顶层条目**数相同（`  - ` 子条目不计）；
  P3  每个段内 `### ` / `#### ` 计数相同；
  P4  不得出现 `## Unreleased…` / `## 未发布…`；
  P5  检查面非空：文件缺失 / 版本段数为 0 / 任一侧总条目数为 0 ⇒ 退出码 2。

退出码：0 = 全部通过，1 = 有判据不满足，2 = 无法判定（文件缺失 / 检查面为空 / 参数错误）。
只读、幂等、零第三方依赖、Python 3.8+。
"""

import argparse
import os
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_EN = os.path.join(REPO_ROOT, "CHANGELOG.md")
DEFAULT_ZH = os.path.join(REPO_ROOT, "CHANGELOG_ZH.md")

# P4：二级标题不得以这些前缀开头（先去掉可能存在的方括号包裹）
UNRELEASED_PREFIXES = ("unreleased", "未发布")


class Undecidable(Exception):
    """检查面读不到 / 为空：必须退出码 2，绝不能判 PASS。"""

    def __init__(self, code, detail):
        Exception.__init__(self, detail)
        self.code = code
        self.detail = detail


class Section(object):
    """一个 `## ` 版本段及其段内计数。"""

    __slots__ = ("title", "line", "entries", "bold", "h3", "h4")

    def __init__(self, title, line):
        self.title = title
        self.line = line
        self.entries = 0  # 行首 `- `（不含 `  - ` 子条目）
        self.bold = 0  # 行首 `- **`（仅用于 WARN，不参与判定）
        self.h3 = 0  # 行首 `### `
        self.h4 = 0  # 行首 `#### `


def read_text(path):
    """按字节读入再解码；任何读取/解码失败一律视为「无法判定」。"""
    try:
        with open(path, "rb") as fh:
            raw = fh.read()
    except OSError as exc:
        raise Undecidable("unreadable", "无法读取检查面：%s（%s）" % (path, exc))
    try:
        return raw.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise Undecidable("bad-encoding", "不是合法 UTF-8：%s（%s）" % (path, exc))


def parse_sections(path):
    """解析出全部 `## ` 段；围栏代码块（``` / ~~~）内的行一律不计。"""
    sections = []
    current = None
    fence = None
    for lineno, line in enumerate(read_text(path).splitlines(), 1):
        probe = line.strip()
        if probe.startswith("```") or probe.startswith("~~~"):
            marker = probe[:3]
            if fence is None:
                fence = marker
            elif fence == marker:
                fence = None
            continue
        if fence is not None:
            continue
        if line.startswith("## "):
            current = Section(line[3:].strip(), lineno)
            sections.append(current)
            continue
        if current is None:
            continue
        if line.startswith("- "):
            current.entries += 1
            if line.startswith("- **"):
                current.bold += 1
        elif line.startswith("### "):  # `#### ` 不满足（第 4 字符是 #）
            current.h3 += 1
        elif line.startswith("#### "):
            current.h4 += 1
    return sections


def find_duplicates(titles):
    seen = set()
    dups = []
    for title in titles:
        if title in seen and title not in dups:
            dups.append(title)
        seen.add(title)
    return dups


def unreleased_title(title):
    core = title.strip().strip("[]").strip()
    low = core.lower()
    for prefix in UNRELEASED_PREFIXES:
        if low.startswith(prefix):
            return True
    return False


def compare(en, zh):
    """返回 (fails, warns)，两者均为待打印的行。"""
    fails = []
    warns = []

    en_titles = [s.title for s in en]
    zh_titles = [s.title for s in zh]
    en_set = set(en_titles)
    zh_set = set(zh_titles)

    # ---- P1：段集合一致（含重复段判定：重复会让集合比对失效，属结构性缺陷）----
    for title in find_duplicates(en_titles):
        fails.append(
            "FAIL P1 ## %s: duplicate heading in EN (%dx)"
            % (title, en_titles.count(title))
        )
    for title in find_duplicates(zh_titles):
        fails.append(
            "FAIL P1 ## %s: duplicate heading in ZH (%dx)"
            % (title, zh_titles.count(title))
        )
    reported = set()
    for title in en_titles:
        if title not in zh_set and title not in reported:
            reported.add(title)
            fails.append("FAIL P1 ## %s: missing in ZH" % title)
    for title in zh_titles:
        if title not in en_set and title not in reported:
            reported.add(title)
            fails.append("FAIL P1 ## %s: missing in EN" % title)

    # 顺序不属于硬判据（版本段可乱序），但两侧顺序不同要打印出来，便于人工定位
    if en_set == zh_set and en_titles != zh_titles:
        for index, (left, right) in enumerate(zip(en_titles, zh_titles), 1):
            if left != right:
                warns.append(
                    "WARN P1: section order differs: first divergence at #%d "
                    "EN='%s' ZH='%s'" % (index, left, right)
                )
                break

    # ---- P2 / P3：逐段计数（只比对两侧都存在的段；缺失段已由 P1 记账）----
    zh_by_title = {}
    for section in zh:
        zh_by_title.setdefault(section.title, section)

    only_en = [t for t in en_titles if t not in zh_set]
    only_zh = [t for t in zh_titles if t not in en_set]
    if only_en or only_zh:
        skipped = [t for t in en_titles if t in only_en]
        skipped += [t for t in zh_titles if t in only_zh and t not in en_set]
        if skipped:
            warns.append(
                "WARN P2/P3: skipped %d unmatched section(s): %s"
                % (len(skipped), ", ".join(skipped))
            )

    for section in en:
        other = zh_by_title.get(section.title)
        if other is None:
            continue
        if section.entries != other.entries:
            fails.append(
                "FAIL P2 ## %s: EN=%d ZH=%d"
                % (section.title, section.entries, other.entries)
            )
        if section.h3 != other.h3 or section.h4 != other.h4:
            fails.append(
                "FAIL P3 ## %s: headings EN=###%d/####%d ZH=###%d/####%d"
                % (section.title, section.h3, section.h4, other.h3, other.h4)
            )
        elif section.entries == other.entries and section.bold != other.bold:
            warns.append(
                "WARN P2 ## %s: entries equal (%d/%d) but bold-start counts "
                "differ EN=%d ZH=%d (historical style difference; not a failure)"
                % (
                    section.title,
                    section.entries,
                    other.entries,
                    section.bold,
                    other.bold,
                )
            )

    # ---- P4：不得出现未发布标记 ----
    for label, sections in (("EN", en), ("ZH", zh)):
        for section in sections:
            if unreleased_title(section.title):
                fails.append(
                    "FAIL P4 ## %s: forbidden heading in %s (unreleased marker)"
                    % (section.title, label)
                )

    return fails, warns


def _configure_stdout():
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError, OSError):
        pass


def write_stdout(text):
    """UTF-8 直写；控制台编码兜底不通过时用 buffer 再试一次。"""
    try:
        sys.stdout.write(text)
        sys.stdout.flush()
    except UnicodeEncodeError:
        buffer = getattr(sys.stdout, "buffer", None)
        if buffer is None:
            raise
        buffer.write(text.encode("utf-8", "replace"))
        buffer.flush()


class _Parser(argparse.ArgumentParser):
    def error(self, message):
        sys.stderr.write("CHANGELOG_PARITY: UNDECIDABLE bad-args\n")
        sys.stderr.write("无法判定：参数错误：%s\n" % message)
        sys.exit(2)


def build_parser():
    parser = _Parser(
        prog="check_changelog_parity.py",
        description="CHANGELOG.md 与 CHANGELOG_ZH.md 的逐段一致性闸门（只读）。",
        epilog=(
            "退出码：0=通过  1=有判据不满足  2=无法判定（文件缺失/检查面为空/参数错误）。"
            " 默认路径按本脚本所在仓库根解析，与当前工作目录无关。"
        ),
    )
    parser.add_argument("--en", default=DEFAULT_EN, help="英文 CHANGELOG 路径")
    parser.add_argument("--zh", default=DEFAULT_ZH, help="中文 CHANGELOG 路径")
    parser.add_argument(
        "--quiet", "-q", action="store_true", help="只打印最后一行机器行"
    )
    return parser


def emit(lines, machine, quiet):
    payload = [machine] if quiet else list(lines) + [machine]
    write_stdout("\n".join(payload) + "\n")


def guard_inputs(en_path, zh_path):
    """P5：任一输入缺失 / 不是普通文件 / 两侧同一文件 ⇒ 无法判定。"""
    for label, path in (("EN", en_path), ("ZH", zh_path)):
        if not os.path.exists(path):
            raise Undecidable("missing-%s" % label.lower(), "文件不存在：%s" % path)
        if not os.path.isfile(path):
            raise Undecidable("not-a-file-%s" % label.lower(), "不是普通文件：%s" % path)
    if os.path.realpath(en_path) == os.path.realpath(zh_path):
        raise Undecidable(
            "same-file", "两侧指向同一文件，不构成对照：%s" % en_path
        )


def main(argv=None):
    _configure_stdout()
    args = build_parser().parse_args(argv)
    en_path = os.path.abspath(args.en)
    zh_path = os.path.abspath(args.zh)

    try:
        guard_inputs(en_path, zh_path)
        en = parse_sections(en_path)
        zh = parse_sections(zh_path)
        if not en or not zh:
            raise Undecidable(
                "empty-sections",
                "解析出的版本段数为 0（EN=%d ZH=%d），检查面为空" % (len(en), len(zh)),
            )
        total_en = sum(s.entries for s in en)
        total_zh = sum(s.entries for s in zh)
        if total_en == 0 or total_zh == 0:
            raise Undecidable(
                "empty-entries",
                "任一侧总条目数为 0（EN=%d ZH=%d），检查面为空" % (total_en, total_zh),
            )
    except Undecidable as exc:
        emit(
            ["无法判定：%s" % exc.detail],
            "CHANGELOG_PARITY: UNDECIDABLE %s" % exc.code,
            args.quiet,
        )
        return 2

    fails, warns = compare(en, zh)
    if fails:
        emit(
            fails + warns,
            "CHANGELOG_PARITY: FAIL mismatches=%d" % len(fails),
            args.quiet,
        )
        return 1

    emit(
        warns,
        "CHANGELOG_PARITY: OK sections=%d entries=%d/%d"
        % (len(en), total_en, total_zh),
        args.quiet,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
