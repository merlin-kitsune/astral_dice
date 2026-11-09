#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""模组依赖来源检查（统一口径）—— AGENTS.md「模组依赖添加规则（统一口径，1.20.1 + 1.21.1）」的执行体。

本文件是 tools/check_mod_sources.py 的**等价迁移**（旧 shell 测试资产退役后的承接实现）：
判定规则、每一行输出格式、状态词、退出码与 ps1 逐字一致；唯一差异是人类可读文案里出现的
**脚本自身文件名**（ps1 -> 本文件实际名），使被指向的文件在 ps1 退役后依然真实存在。

用法:
    python tools/check_mod_sources.py [-Root <仓库根>]

规则（与 AGENTS.md 同名章节一一对应）:
  R1  来源仓库统一：forge-1.20.1 与 neoforge-1.21.1 都必须声明同一组模组来源仓库 ——
      Curse Maven（https://www.cursemaven.com）与 Modrinth Maven（https://api.modrinth.com/maven）；
      同一文件内重复声明同一个仓库 URL 视为冗余（FAIL，Gradle 虽会去重，但重复声明会让「统一口径」失真）。
  R2  模组坐标统一：每个**模组**依赖坐标只能是 curse.maven: 或 maven.modrinth:；
      本地 jar 兜底（fileTree(...) / files(...)）实际命中 jar 时一律 FAIL（模组不得来自文件系统）；
      非模组库（mixin / gson / guava / asm / sponge-mixin 等）按 LIBRARY_GROUPS 白名单放行；
      已知官方 maven 的模组依赖按 MOD_EXCEPTIONS 登记为**例外**，每次运行都回显，
      便于逐个裁决「是否迁移到 Curse/Modrinth Maven」，不允许静默存在。
  R3  未被任何 build.gradle 引用的本地模组 jar 只作 INFO 回显（**不删文件**，留待用户裁决）。

退出码: 0 = 通过（R1/R1b/R2 全部满足）; 1 = 存在违规。

机器可读结论行: MOD_SOURCE_GATE: OK|FAIL violations=<n> exceptions=<n> infos=<n>
（scripts/test/mt_preflight* 的阶段 P 与 scripts/test/TESTING-SPEC.md 第 9 节静态守门共用本脚本。）
输出统一为 UTF-8 无 BOM + 显式 LF，与 tools/check_lang_sync.py 同一口径。

等价性说明（刻意保留的 ps1 语义，勿「顺手修正」）:
  * 大小写：ps1 的 -match / -like / -contains / 哈希表键查找都是**大小写不敏感**的，
    故 curse.maven / maven.modrinth 前缀、库白名单、例外表、gradle.properties 键一律按小写比较。
  * 仓库声明正则只认**同一行内**的 maven { url '...' }；跨行的 maven 块不参与判定（与 ps1 一致）。
  * 本地 jar 的 dir:/files(...) 两个正则沿用 ps1 的**大小写敏感**取值，
    而「是否属于本地 jar 兜底」的判定沿用 ps1 的**大小写不敏感**（-match）。
  * 目录里的 *.jar 按**大小写不敏感**后缀匹配（Windows 上 ps1 的 -Filter 即如此），便于 Linux CI 同口径。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

# ── 规则表 ────────────────────────────────────────────────────────────────
# R1：统一口径允许的两个模组来源仓库
REQUIRED_REPOS: tuple[str, ...] = (
    "https://www.cursemaven.com",
    "https://api.modrinth.com/maven",
)
# R1 豁免：非模组来源的仓库不受「必须声明」约束，仅参与重复声明检查
# （mavenCentral / neoforged / latvian / architectury / jitpack / blamejared / 本地 .oss-basemod-repo）

# R2：非模组库白名单（库 != 模组，不受「只能经 Curse/Modrinth Maven 获取模组」约束）
#     新增库时在此登记，并在 commit message 里说明它是库而不是模组。比较为大小写不敏感。
LIBRARY_GROUPS: frozenset[str] = frozenset(
    g.lower()
    for g in (
        "org.spongepowered",  # Sponge Mixin（1.20.1 注解处理器 + compileOnly）
        "net.fabricmc",  # sponge-mixin（26.1.2 侧的 mixin 实现）
        "io.github.llamalad7",  # MixinExtras（@WrapOperation/@Local 注入辅助注解库）
        "com.google.code.gson",  # mixin 注解处理器依赖
        "com.google.guava",  # mixin 注解处理器依赖
        "org.ow2.asm",  # mixin 注解处理器依赖
        # 本家前置库 StarEngine：三线共享类的下沉目标，**只发布到 mavenLocal**（无远程 maven）。
        # 它是**库而不是模组**（不注册任何注册表条目），故按「库」白名单放行。
        "com.merlinkitsune.starengine",
        # 旧写法一并登记（ps1 里是同一行重复两次，集合语义下等价）。
        "com.merlinkitsune.starengine",
    )
)

# R2：已知例外（**官方 maven 提供的模组依赖**）。每次运行都回显；是否迁移到 Curse/Modrinth Maven
#     由用户裁决（迁移需要目标 Maven 的版本 id/fileId，且必须重跑依赖解析验证）。
#     2026-09-17：原两条例外（mezz.jei / dev.architectury）已迁到 Curse Maven，故当前仅剩 AutoJS。
#     2026-10-18：cc.sighs.autojs —— AutoJS 实机测试库**只发布到 mavenLocal**（本地产物，
#     无 Curse/Modrinth 发布件），无法用 curse.maven: / maven.modrinth: 坐标表达。
MOD_EXCEPTIONS: dict[str, str] = {
    "cc.sighs.autojs": "AutoJS 实机测试库：本地 mavenLocal 分发，无 Curse/Modrinth 发布（GPL-3.0）",
}

# R3：可能残留本地模组 jar 的目录（只回显，不删）
LOCAL_JAR_DIRS: tuple[str, ...] = ("base-mod-libs", "base-mod-compile-libs")

# ── 目标 ──────────────────────────────────────────────────────────────────
TARGETS: tuple[str, ...] = ("forge-1.20.1", "neoforge-1.21.1")  # 统一口径约束的两条发布线
# 第三条线（26.1.2 由 1.21.1 整体迁移而来）：只回显 + 参与 R1b（坐标<->仓库一致性），不做 R1/R2 判定。
# 只对**实际存在**的子项目生效（避免对已移除/尚未进入本 worktree 的子项目持续报 INFO）。
INFO_TARGETS: tuple[str, ...] = ("neoforge-26.1.2",)

# ── 正则（逐条对齐 ps1）────────────────────────────────────────────────────
DEP_RE = re.compile(
    r"^\s*(modImplementation|modCompileOnly|modRuntimeOnly|modApi|implementation"
    r"|compileOnly|runtimeOnly|api|jarJar)\b(.*)$"
)
REPO_RE = re.compile(r"maven\s*\{\s*url\s*=?\s*['\"]([^'\"]+)['\"]")
# ps1 用 -match（大小写不敏感）判定「本地 jar 兜底」
LOCAL_JAR_RE = re.compile(r"fileTree\s*\(|files\s*\(", re.IGNORECASE)
QUOTED_RE = re.compile(r"['\"]([^'\"]+)['\"]")
PLACEHOLDER_RE = re.compile(r"\$\{([A-Za-z0-9_.]+)\}")
# 这两个沿用 ps1 的 [regex]::Matches（大小写敏感）
DIR_RE = re.compile(r"dir:\s*['\"]([^'\"]+)['\"]")
FILES_RE = re.compile(r"files\s*\(([^)]*)\)")
# 占位符左界（分开写，避免与外层模板/格式串的插值语法混淆）
PLACEHOLDER_OPEN = "$" + "{"

# ── 结果收集（对应 ps1 的三个 $script: 列表）─────────────────────────────────
VIOLATIONS: list[str] = []
EXCEPTIONS: list[str] = []
INFOS: list[str] = []

# 人类可读文案里出现的脚本自身标识（ps1 原文是 tools/check_mod_sources.py）
SCRIPT_REF = "tools/" + Path(__file__).name


# ── 输出 ──────────────────────────────────────────────────────────────────
class _Console:
    """UTF-8 无 BOM + 显式 LF 的输出通道（对应 ps1 的 [Console]::Out.Write）。"""

    def __init__(self) -> None:
        self._buffer = getattr(sys.stdout, "buffer", None)

    def line(self, text: str) -> None:
        data = text + "\n"
        if self._buffer is not None:
            self._buffer.write(data.encode("utf-8"))
        else:  # 退化路径：stdout 被替换成纯文本流时
            sys.stdout.write(data)

    def flush(self) -> None:
        try:
            if self._buffer is not None:
                self._buffer.flush()
            else:
                sys.stdout.flush()
        except (BrokenPipeError, ValueError):
            pass


# ── 解析 ──────────────────────────────────────────────────────────────────
def read_lines(path: str) -> list[str]:
    """等价于 原生 shell 的 Get-Content：按 CRLF/LF/CR 切行，去掉行尾，丢弃末尾空元素。"""
    try:
        with open(path, "rb") as fh:
            raw = fh.read()
    except OSError:
        return []
    text = raw.decode("utf-8-sig", errors="replace")
    if not text:
        return []
    lines = re.split(r"\r\n|\n|\r", text)
    if lines and lines[-1] == "":
        lines.pop()
    return lines


def read_gradle_properties(project_dir: str) -> dict[str, str]:
    """读取子项目 gradle.properties 的 key=value（用于还原坐标占位符）。键按小写归一（ps1 哈希表大小写不敏感）。"""
    props: dict[str, str] = {}
    path = os.path.join(project_dir, "gradle.properties")
    if not os.path.isfile(path):
        return props
    for line in read_lines(path):
        s = line.strip()
        if s == "" or s.startswith("#"):
            continue
        eq = s.find("=")
        if eq <= 0:
            continue
        props[s[:eq].strip().lower()] = s[eq + 1 :].strip()
    return props


def expand_placeholders(coord: str, props: dict[str, str]) -> str:
    """把坐标里的变量占位符按 gradle.properties 的键值还原；未知键保持原样。"""
    out = coord
    for match in PLACEHOLDER_RE.finditer(coord):
        key = match.group(1)
        value = props.get(key.lower())
        if value is not None:
            out = out.replace(PLACEHOLDER_OPEN + key + "}", str(value))
    return out


def is_path_rooted(path: str) -> bool:
    """按 Windows 语义判断是否绝对路径（与 IsPathRooted 同口径，兼顾 POSIX 前斜杠）。"""
    if not path:
        return False
    if path[0] in ("/", "\\"):
        return True
    return len(path) >= 2 and path[1] == ":"


def jar_names(directory: str) -> list[str]:
    """列出目录下的 *.jar 文件名（等价 Get-ChildItem -File -Filter '*.jar'，后缀大小写不敏感）。"""
    names: list[str] = []
    try:
        with os.scandir(directory) as entries:
            for entry in entries:
                try:
                    if entry.is_file() and entry.name.lower().endswith(".jar"):
                        names.append(entry.name)
                except OSError:
                    continue
    except OSError:
        return []
    return names


def dependency_coordinate(rest: str, props: dict[str, str]) -> tuple[str, str]:
    """从一条 build.gradle 行的「坐标部分」抽坐标；返回 (种类, 坐标)。

    种类: curse / modrinth / local-jar / library / exception / unknown / none
    """
    if LOCAL_JAR_RE.search(rest):
        return "local-jar", "fileTree/files(...)"
    match = QUOTED_RE.search(rest)
    if match is None:
        return "none", ""
    coord = match.group(1).strip()
    # Gradle 属性占位符（group / version）按该子项目的 gradle.properties 还原：
    # 本家前置库的坐标正是 star engine group:starengine-<平台>:star engine version，
    # 不还原的话坐标首段是字面量占位符，LIBRARY_GROUPS 白名单永远匹配不上。
    if PLACEHOLDER_OPEN in coord:
        coord = expand_placeholders(coord, props)
    lowered = coord.lower()
    if lowered.startswith("curse.maven:"):
        return "curse", coord
    if lowered.startswith("maven.modrinth:"):
        return "modrinth", coord
    group = coord.split(":")[0].lower()
    if group in MOD_EXCEPTIONS:
        return "exception", coord
    if group in LIBRARY_GROUPS:
        return "library", coord
    return "unknown", coord


def test_mod_source_file(project: str, path: str, enforce: bool) -> None:
    """按 R1 / R1b / R2 / R3 检查单个 build.gradle，结果追加到三个收集表。"""
    if not os.path.isfile(path):
        if enforce:
            VIOLATIONS.append(f"{project}: 缺少 {path}")
        else:
            INFOS.append(f"{project}: 缺少 {path}（跳过）")
        return

    lines = read_lines(path)
    props = read_gradle_properties(os.path.dirname(path))

    seen_repos: set[str] = set()
    used_curse = False
    used_modrinth = False

    for index, raw in enumerate(lines):
        trimmed = raw.strip()
        if trimmed.startswith("//"):
            continue
        line_no = index + 1

        # ── R1：仓库声明（含重复声明检查）──────────────────────────────
        for repo_match in REPO_RE.finditer(raw):
            url = repo_match.group(1).strip().rstrip("/").lower()
            if url in seen_repos:
                if enforce:
                    VIOLATIONS.append(
                        f"{project}:{line_no} R1 重复声明仓库 {url}（同一 build.gradle 内应只声明一次）"
                    )
            else:
                seen_repos.add(url)

        # ── R2：依赖坐标 ──────────────────────────────────────────────
        dep_match = DEP_RE.match(raw)
        if dep_match is None:
            continue
        kind, coord = dependency_coordinate(dep_match.group(2), props)
        if kind == "curse":
            used_curse = True
        elif kind == "modrinth":
            used_modrinth = True
        elif kind == "local-jar":
            # 只有「实际命中某个存在的 jar」才算违规；指向空目录/不存在的目录只是死配置（INFO）。
            rest = dep_match.group(2)
            refs: list[str] = [m.group(1) for m in DIR_RE.finditer(rest)]
            for files_match in FILES_RE.finditer(rest):
                refs.extend(q.group(1) for q in QUOTED_RE.finditer(files_match.group(1)))
            hits: list[str] = []
            base = os.path.dirname(path)
            for ref in refs:
                target = ref if is_path_rooted(ref) else os.path.join(base, ref)
                if os.path.isdir(target):
                    hits.extend(jar_names(target))
                elif os.path.isfile(target):
                    hits.append(ref)
            if hits:
                if enforce:
                    VIOLATIONS.append(
                        f"{project}:{line_no} R2 本地 jar 兜底不得作为模组来源"
                        f"（实际命中：{', '.join(hits)}）：{trimmed}"
                    )
            else:
                INFOS.append(
                    f"{project}:{line_no} 本地 jar 兜底指向的目录为空/不存在（不构成实际来源）：{trimmed}"
                )
        elif kind == "exception":
            group = coord.split(":")[0].lower()
            EXCEPTIONS.append(f"{project}:{line_no} 例外({MOD_EXCEPTIONS[group]})：{coord}")
        elif kind == "unknown":
            if enforce:
                VIOLATIONS.append(
                    f'{project}:{line_no} R2 未知来源坐标 "{coord}"'
                    "（模组必须用 curse.maven: / maven.modrinth:；"
                    f"确为库则在 {SCRIPT_REF} 的 $LibraryGroups 登记，"
                    "确为官方 maven 模组则在 $ModExceptions 登记）"
                )
        # none / library 无需处理

    if enforce:
        for req in REQUIRED_REPOS:
            if req.lower() not in seen_repos:
                VIOLATIONS.append(
                    f"{project} R1 缺少统一的模组来源仓库：{req}（两个发布线子项目必须声明同一组来源）"
                )

    # ── R1b：坐标 <-> 仓库一致性（**对所有线生效**，含第三条线）──────────
    # 用了某个来源的坐标却没声明对应仓库 -> 解析期必然失败（Gradle 只会报 "Could not find ..."）。
    if used_curse and REQUIRED_REPOS[0].lower() not in seen_repos:
        VIOLATIONS.append(
            f"{project} R1b 使用了 curse.maven 坐标但未声明 Curse Maven 仓库（{REQUIRED_REPOS[0]}）"
        )
    if used_modrinth and REQUIRED_REPOS[1].lower() not in seen_repos:
        VIOLATIONS.append(
            f"{project} R1b 使用了 maven.modrinth 坐标但未声明 Modrinth Maven 仓库（{REQUIRED_REPOS[1]}）"
        )

    # ── R3：本地模组 jar 残留（只回显）────────────────────────────────
    project_dir = os.path.dirname(path)
    for name in LOCAL_JAR_DIRS:
        directory = os.path.join(project_dir, name)
        if not os.path.isdir(directory):
            continue
        jars = jar_names(directory)
        if not jars:
            continue
        joined = ", ".join(jars)
        if enforce:
            INFOS.append(
                f"{project}/{name} 内有未被引用的本地 jar（{joined}）"
                "——模组获取已统一走 Curse/Modrinth Maven，"
                "该目录不再参与编译；是否删除由用户裁决（本脚本不删文件）"
            )
        else:
            INFOS.append(f"{project}/{name} 内有本地 jar（{joined}）")


# ── 命令行 ────────────────────────────────────────────────────────────────
def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog=Path(__file__).name,
        description="模组依赖来源检查（统一口径）——tools/check_mod_sources.py 的等价 Python 实现。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "规则:\n"
            "  R1  两条发布线（forge-1.20.1 / neoforge-1.21.1）必须声明同一组模组来源仓库\n"
            "      （https://www.cursemaven.com、https://api.modrinth.com/maven），重复声明即 FAIL。\n"
            "  R1b 使用了 curse.maven / maven.modrinth 坐标却没声明对应仓库即 FAIL（对所有线生效）。\n"
            "  R2  模组坐标只能是 curse.maven: 或 maven.modrinth:；本地 jar 兜底实际命中 jar 即 FAIL；\n"
            "      库白名单 LIBRARY_GROUPS 放行，官方 maven 模组依赖按 MOD_EXCEPTIONS 作为例外回显。\n"
            "  R3  未被引用的本地模组 jar 只作 INFO 回显（不删文件）。\n"
            "\n"
            "退出码: 0 = 通过; 1 = 存在违规。\n"
            "机器可读结论行: MOD_SOURCE_GATE: OK|FAIL violations=<n> exceptions=<n> infos=<n>\n"
        ),
    )
    parser.add_argument(
        "--root",
        "-Root",
        dest="root",
        default=None,
        metavar="ROOT",
        help="仓库根目录（默认：本脚本所在目录的上一级）",
    )
    parser.add_argument(
        "root_positional",
        nargs="?",
        default=None,
        metavar="ROOT",
        help="同 --root（兼容 旧 shell 的位置参数调用习惯）",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    root = args.root if args.root is not None else args.root_positional
    if root is None:
        root = os.path.realpath(
            os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir)
        )

    console = _Console()

    # ── 主流程 ────────────────────────────────────────────────────────
    console.line(f"模组来源统一口径检查（{SCRIPT_REF}）root={root}")
    console.line("")

    for project in TARGETS:
        console.line(f"[{project}]")
        test_mod_source_file(project, os.path.join(root, project, "build.gradle"), True)
    for project in INFO_TARGETS:
        info_build = os.path.join(root, project, "build.gradle")
        if not os.path.isfile(info_build):
            INFOS.append(f"{project}: 子项目不存在（已从 info 目标中收紧掉,不参与判定）")
            continue
        console.line(f"[{project}]（仅回显，不作为统一口径的判定对象）")
        test_mod_source_file(project, info_build, False)

    console.line("")
    for entry in EXCEPTIONS:
        console.line(f"  [EXC ] {entry}")
    for entry in INFOS:
        console.line(f"  [INFO] {entry}")
    for entry in VIOLATIONS:
        console.line(f"  [FAIL] {entry}")

    console.line("")
    verdict = "OK" if not VIOLATIONS else "FAIL"
    console.line(
        f"MOD_SOURCE_GATE: {verdict} violations={len(VIOLATIONS)} "
        f"exceptions={len(EXCEPTIONS)} infos={len(INFOS)}"
    )
    console.flush()

    return 1 if VIOLATIONS else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except BrokenPipeError:  # 下游提前关管道时不要抛栈
        sys.exit(1)
