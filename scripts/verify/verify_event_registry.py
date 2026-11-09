#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""事件注册表静态守门（由 旧版 verify_event_registry 实现 等价迁移而来）

判据（与 旧实现 完全一致）：
  ① 注册的 id 都在 AstralEventIds 里有常量；
  ② id 路径唯一；
  ③ 三语 lang 均有 event.astral_dice.<path>.name；
  ④ 图标 assets/astral_dice/textures/gui/event/<path>.png 存在
     （investigation_stage 另查 4 张阶级图）；
  ⑤ 事件键字面量不得散落在注册表之外（WARN，不影响退出码）。

输出约定（对齐 旧实现）：
  - 全部人类可读行写 stdout，平台换行（Windows CRLF / Linux LF）——旧实现 用 Write-Host，
    同样是平台换行；UTF-8。
  - 退出码：0 = 全绿；1 = 有 FAIL（含"注册表中未找到任何 AstralEventIds 引用"
    "缺少源文件"两种提前退出分支）。

反假绿：
  - 默认仓库根 = 脚本位置上溯两级（不依赖 cwd），可显式覆盖；
  - 扫到 0 条注册引用时 旧实现 本身就 FAIL 且退出 1（不是假绿），此处保持同一判据与文案。

与 旧实现 的唯一已知差异：旧实现 末尾"常量已定义但未注册"的 WARN 走 $constMap.Keys
（.NET Hashtable，字符串哈希随机化，跨进程顺序不稳定），本脚本按 ids 文件出现顺序输出；
真实仓库当前该分支为 0 行，故 stdout 逐字节一致。

用法：
  python scripts/verify/verify_event_registry.py [-Root <仓库根>] [-Line <子项目>]
"""
from __future__ import annotations

import argparse
import os
import re
import sys

LANGS = ("zh_cn", "en_us", "ja_jp")
STAGE_ICONS = (
    "investigation_stage_1",
    "investigation_stage_2",
    "investigation_stage_3",
    "investigation_stage_revelation",
)
DEFAULT_LINE = "neoforge-1.21.1"

CONST_PATTERN = re.compile(r'public static final ResourceLocation (\w+) = id\("([^"]+)"\)')
REF_PATTERN = re.compile(r"AstralEventIds\.(\w+)")
STRAY_PATTERN = re.compile(r"event\.astral_dice\.")
REGISTRY_EXCLUDE = re.compile(r"AstralEventRegistry\.java$", re.IGNORECASE)


def norm_seps(p: str) -> str:
    """复刻 旧 shell Join-Path 在 Windows 上的分隔符归一（'/' -> '\\'）：
    旧实现 用 Join-Path 拼所有派生路径，Windows 上会把整条路径统一成反斜杠；
    posix 上 Join-Path 不做转换，故此处只在 os.sep 为 '\\' 时转换（Linux CI 无操作）。"""
    if os.sep == "\\":
        return p.replace("/", "\\")
    return p


def default_root() -> str:
    """脚本位置上溯两级 = 仓库根（scripts/verify/<file> -> repo）。"""
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.normpath(os.path.join(here, "..", ".."))


def iter_java_files(root: str):
    """复刻 Get-ChildItem -Recurse -Filter *.java 的枚举顺序：
    先输出本目录下的 *.java（文件系统枚举顺序），再按枚举顺序递归子目录
    （已用 /tmp 夹具实测 旧 shell 7.6.9 为「本级文件全部优先，然后 DFS 子目录」）。"""
    try:
        entries = list(os.scandir(root))
    except OSError:
        return
    files = []
    dirs = []
    for e in entries:
        try:
            if e.is_dir(follow_symlinks=False):
                dirs.append(e.name)
            elif e.name.lower().endswith(".java"):
                files.append(e.name)
        except OSError:
            continue
    for name in files:
        yield os.path.join(root, name)
    for name in dirs:
        yield from iter_java_files(os.path.join(root, name))


def read_text(path: str) -> str:
    with open(path, "r", encoding="utf-8", errors="replace", newline="") as fh:
        return fh.read()


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(
        description="事件注册表静态守门（旧实现 等价迁移版）",
        add_help=True,
    )
    ap.add_argument("root_pos", nargs="?", default=None, help="仓库根（位置参数，可选）")
    ap.add_argument("-Root", "--root", dest="root", default=None, help="仓库根（默认脚本位置上溯两级）")
    ap.add_argument("-Line", "--line", dest="line", default=DEFAULT_LINE, help="子项目目录（默认 neoforge-1.21.1）")
    args = ap.parse_args(argv)

    root = norm_seps(args.root or args.root_pos or default_root())
    line = args.line

    src = os.path.join(root, line, "src", "main", "java", "com", "merlinkitsune", "astral_dice")
    res = os.path.join(root, line, "src", "main", "resources")
    ids_file = os.path.join(src, "event", "AstralEventIds.java")
    reg_file = os.path.join(src, "event", "AstralEventRegistry.java")
    fail = 0

    if not (os.path.exists(ids_file) and os.path.exists(reg_file)):
        print("EVENT_REG: FAIL 缺少源文件（%s / %s）" % (ids_file, reg_file))
        return 1

    # 常量表：旧 shell @{} 是忽略大小写的 Hashtable
    const_map = {}       # lower(name) -> path
    const_order = []     # 按 ids 文件出现顺序保留原始名
    for m in CONST_PATTERN.finditer(read_text(ids_file)):
        name, path = m.group(1), m.group(2)
        key = name.lower()
        if key not in const_map:
            const_order.append(name)
        const_map[key] = path

    registered = []
    seen = set()
    for m in REF_PATTERN.finditer(read_text(reg_file)):
        name = m.group(1)
        if name.lower() == "id":
            continue
        if name.lower() in seen:
            continue
        seen.add(name.lower())
        registered.append(name)

    if len(registered) == 0:
        print("EVENT_REG: FAIL 注册表中未找到任何 AstralEventIds 引用")
        return 1

    paths = []  # 已见路径（-contains 忽略大小写）
    seen_paths = set()
    for c in registered:
        key = c.lower()
        if key not in const_map:
            print("EVENT_REG: FAIL 常量 %s 未在 AstralEventIds 中定义" % c)
            fail += 1
            continue
        path = const_map[key]
        if path.lower() in seen_paths:
            print("EVENT_REG: FAIL id 路径重复: %s" % path)
            fail += 1
        seen_paths.add(path.lower())
        paths.append(path)
        for lang in LANGS:
            lang_file = os.path.join(res, "assets", "astral_dice", "lang", lang + ".json")
            key_pattern = re.compile(r"event\.astral_dice\." + re.escape(path) + r"\.name", re.IGNORECASE)
            if not select_string_quiet(lang_file, key_pattern):
                print("EVENT_REG: FAIL 缺少三语键 event.astral_dice.%s.name（%s）" % (path, lang))
                fail += 1
        png = os.path.join(res, "assets", "astral_dice", "textures", "gui", "event", path + ".png")
        if not os.path.exists(png):
            print("EVENT_REG: FAIL 缺少图标 textures/gui/event/%s.png" % path)
            fail += 1

    for t in STAGE_ICONS:
        png = os.path.join(res, "assets", "astral_dice", "textures", "gui", "event", t + ".png")
        if not os.path.exists(png):
            print("EVENT_REG: FAIL 缺少阶级图标 textures/gui/event/%s.png" % t)
            fail += 1

    for f in iter_java_files(src):
        if REGISTRY_EXCLUDE.search(f):
            continue
        text = read_text(f)
        for idx, ln in enumerate(text.splitlines(), start=1):
            if STRAY_PATTERN.search(ln):
                print("EVENT_REG: WARN 事件键字面量散落 " + f.replace(root, "") + ":" + str(idx))

    for k in const_order:
        if k.lower() not in seen:
            print("EVENT_REG: WARN 常量 %s（%s）已定义但未注册" % (k, const_map[k.lower()]))

    if fail > 0:
        print("EVENT_REG: FAIL failures=%d" % fail)
        return 1
    print("EVENT_REG: OK line=%s events=%d ids=%d icons=ok langs=3" % (line, len(registered), len(const_map)))
    return 0


def select_string_quiet(path: str, pattern: re.Pattern) -> bool:
    """复刻 Select-String -Quiet：逐行（忽略大小写）正则命中即 True。
    文件不存在时 旧实现 在 $ErrorActionPreference=Stop 下会终止；此处按"缺键"处理（仍 FAIL）。"""
    try:
        text = read_text(path)
    except OSError:
        return False
    for ln in text.splitlines():
        if pattern.search(ln):
            return True
    return False


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass
    sys.exit(main())
