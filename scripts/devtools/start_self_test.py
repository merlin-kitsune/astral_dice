#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""Start-SelfTest —— dev 工作树（multi-dev-next）自测启用脚本的 Python 移植。

等价 scripts/devtools/Start-SelfTest.ps1（pwsh 版本）。一条命令完成「用户自行测试」所需的三件事：
  1) 构建所选平台（缺省 1.21.1）；
  2) 部署到目标 mods 目录（先删后拷；部署契约按「该线产物是否仍 JarJar 内嵌库」区分）；
  3) 可选启动 dev 客户端（--launch，缺省关闭；人工测试路径）。

参数面（同时接受 argparse 长选项与 pwsh 单破折号写法）：
  -Version / --version          1.21.1（缺省） | 1.20.1 | 26.1.2
  -TargetDir / --target-dir     目标 mods 目录（缺省 <repo>\run\<Version>\mods）
  -TargetKind / --target-kind   auto（缺省） | dev | prod
  -SkipBuild / --skip-build     跳过构建，直接用现有产物
  -Launch / --launch            后台启动 dev 客户端（人工测试路径，代理不得自动调用）
  -DryRun / --dry-run           只打印计划，零落盘
  -CleanOldJars / --clean-old-jars
                                把旧版 jar 移到 <repo>\temp\old-jars-<时间戳>\
  -BuildTimeoutSec / --build-timeout
                                Gradle 看门狗超时（秒，缺省 180）
  -LibRepoPath / --lib-repo-path
                                库仓库磁盘路径（缺省 = <repo 父目录>\starengine_next）

退出码：0 成功（含 --dry-run）；1 任一 Fail 断言失败（构建失败 / 产物缺失或形态不对 /
部署后计数不对）。未知参数或参数取值非法 ⇒ 2（argparse 行为，与 pwsh 参数绑定失败同义）。

与 ps1 的差异（逐条）：
  · 不打印终端颜色（Write-Host -ForegroundColor 的颜色码不是可解析语义；CI 重定向时 ps1 侧也会被
    去掉），机器行与人类文案逐字保留。
  · 时间戳按本机 zh-CN 习惯格式化为 %Y/%m/%d %H:%M:%S（ps1 用 DateTime 缺省 ToString()，同文化）。
  · 换行一律 LF（ps1 的 Write-Host 在 Windows 上是 CRLF）；内容判据用 diff --strip-trailing-cr。
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile
from datetime import datetime

VERSIONS = ("1.21.1", "1.20.1", "26.1.2")

LINE_MAP = {
    "1.21.1": {"Sub": "neoforge-1.21.1", "Kind": "neoforge",
               "Artifact": "starengine-neoforge-1.21.1", "GradlePath": ":neoforge-1.21.1"},
    "1.20.1": {"Sub": "forge-1.20.1", "Kind": "forge",
               "Artifact": "starengine-forge-1.20.1", "GradlePath": ":forge-1.20.1"},
    "26.1.2": {"Sub": "neoforge-26.1.2", "Kind": "neoforge",
               "Artifact": "starengine-neoforge-26.1.2", "GradlePath": ":neoforge-26.1.2"},
}

LIB_GROUP_PATH = os.path.join("com", "merlinkitsune", "starengine")

SRG_RE = re.compile(rb"\b[fm]_\d+_")


class Fail(Exception):
    """对应 ps1 的 Fail()：打印 [FAIL] + hints 后 throw ⇒ 退出码 1。"""


def _enable_utf8_stdout() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace", newline="\n")
        except (AttributeError, ValueError):
            pass


def ps_time(dt: datetime) -> str:
    return dt.strftime("%Y/%m/%d %H:%M:%S")


def stamp(dt: datetime | None = None) -> str:
    return (dt or datetime.now()).strftime("%Y%m%d-%H%M%S")


def collation_key(name: str) -> str:
    # Windows 资源管理器的 culture 排序把 '_' 排在 '.'/'-' 之前；ordinal 相反。
    return name.replace("_", "\x01").lower()


def sorted_names(names) -> list:
    return sorted(names, key=collation_key)


# ---------------------------------------------------------------------------
# 0. 路径与常量（全部由脚本位置推导，绝不依赖 cwd）
# ---------------------------------------------------------------------------
HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
REPO_PARENT = os.path.dirname(REPO_ROOT)
M2_REPO_ROOT = os.path.join(os.path.expanduser("~"), ".m2", "repository", LIB_GROUP_PATH)


def out(line: str) -> None:
    sys.stdout.write(line + "\n")
    sys.stdout.flush()


def write_step(msg: str) -> None:
    out("==> " + msg)


def write_info(msg: str) -> None:
    out("    " + msg)


def write_warn(msg: str) -> None:
    out("[WARN] " + msg)


def write_ok(msg: str) -> None:
    out("[ OK ] " + msg)


def fail(msg: str, hints=()) -> "Fail":
    out("[FAIL] " + msg)
    for h in hints:
        out("       · " + str(h))
    raise Fail(msg)


def read_gradle_properties(path: str) -> dict:
    m = {}
    with open(path, "r", encoding="utf-8", errors="replace", newline="") as fh:
        text = fh.read()
    for line in text.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        t = line.strip()
        if t == "" or t.startswith("#"):
            continue
        i = t.find("=")
        if i < 1:
            continue
        m[t[:i].strip()] = t[i + 1:].strip()
    return m


def get_sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def get_jar_form(jar_path: str) -> dict:
    """形态判据：jar 内 .class 字节里 SRG 形（f_/m_ + 数字 + 下划线）命中数。"""
    hits = 0
    with zipfile.ZipFile(jar_path, "r") as zf:
        for info in zf.infolist():
            if not info.filename.endswith(".class"):
                continue
            data = zf.read(info.filename)
            hits += len(SRG_RE.findall(data))
    if hits > 0:
        return {"Form": "reobf/srg", "Hits": hits}
    return {"Form": "dev/mojmap", "Hits": 0}


# ---------------------------------------------------------------------------
# 0.1 派生值（保留清单 / 库版本钉死值）—— 全部读各线 gradle.properties，禁止硬编码
# ---------------------------------------------------------------------------
def get_current_mod_jar_names() -> list:
    names = []
    for v in VERSIONS:
        p = os.path.join(REPO_ROOT, LINE_MAP[v]["Sub"], "gradle.properties")
        if not os.path.isfile(p):
            continue
        mv = read_gradle_properties(p).get("mod_version")
        if mv:
            names.append("astral_dice-%s.jar" % mv)
    return names


def get_pinned_lib_versions() -> dict:
    m = {}
    for v in VERSIONS:
        line = LINE_MAP[v]
        p = os.path.join(REPO_ROOT, line["Sub"], "gradle.properties")
        if not os.path.isfile(p):
            continue
        m[line["Artifact"]] = read_gradle_properties(p).get("starengine_version")
    return m


KEEP_MOD_JARS = get_current_mod_jar_names()
PINNED_LIB_VERSIONS = get_pinned_lib_versions()


def get_build_artifact_dirs() -> list:
    return [
        os.path.join(REPO_ROOT, "neoforge-1.21.1", "build", "libs"),
        os.path.join(REPO_ROOT, "forge-1.20.1", "build", "libs"),
        os.path.join(REPO_ROOT, "forge-1.20.1", "build", "devlibs"),
        os.path.join(REPO_ROOT, "neoforge-26.1.2", "build", "libs"),
        os.path.join(REPO_ROOT, "build", "libs"),
    ]


def _list_files(d: str, pattern: str) -> list:
    try:
        names = os.listdir(d)
    except OSError:
        return []
    import fnmatch
    return [os.path.join(d, n) for n in sorted_names(names)
            if fnmatch.fnmatch(n, pattern) and os.path.isfile(os.path.join(d, n))]


def _list_dirs(d: str) -> list:
    try:
        names = os.listdir(d)
    except OSError:
        return []
    return [n for n in sorted_names(names) if os.path.isdir(os.path.join(d, n))]


def get_cleanup_plan(quarantine: str) -> dict:
    """纯只读扫描（零落盘）：DryRun 预演与实际执行共用同一份判定，保证「计划 = 行为」。"""
    keep, move, m2_remain, notes = [], [], [], []

    # (a) 构建产物目录
    for d in get_build_artifact_dirs():
        if not os.path.isdir(d):
            continue
        for f in _list_files(d, "astral_dice-*.jar"):
            name = os.path.basename(f)
            if name in KEEP_MOD_JARS:
                keep.append(f)
                continue
            parts = os.path.normpath(d).split(os.sep)
            tag = "build-" + parts[-3] + "-" + parts[-1] + "-" + name
            move.append({"原路径": f, "去向": os.path.join(quarantine, tag)})

    # (b) run\<版本>\mods：旧本模组 jar + 与当前库版本不符的库 jar
    for v in VERSIONS:
        d = os.path.join(REPO_ROOT, "run", v, "mods")
        if not os.path.isdir(d):
            continue
        artifact = LINE_MAP[v]["Artifact"]
        pinned = PINNED_LIB_VERSIONS.get(artifact) or ""
        if not pinned:
            notes.append("run/%s/mods：%s 无 starengine_version ⇒ 库 jar 一律保留（不动）" % (v, artifact))
        for f in _list_files(d, "*.jar"):
            name = os.path.basename(f)
            if _like(name, "astral_dice-*.jar"):
                if name in KEEP_MOD_JARS:
                    keep.append(f)
                    continue
                move.append({"原路径": f, "去向": os.path.join(quarantine, "run-%s-mods-%s" % (v, name))})
                continue
            if _like(name, "starengine-*.jar"):
                if not pinned:
                    continue
                if name == "%s-%s.jar" % (artifact, pinned):
                    keep.append(f)
                    continue
                move.append({"原路径": f, "去向": os.path.join(quarantine, "run-%s-mods-%s" % (v, name))})

    # (c) mavenLocal：每个 artifact 下除「该线当前 starengine_version」外的全部版本目录一律隔离
    for artifactname in _list_dirs(M2_REPO_ROOT):
        artifact_dir = os.path.join(M2_REPO_ROOT, artifactname)
        pinned = PINNED_LIB_VERSIONS.get(artifactname) or ""
        remain = []
        if not pinned:
            notes.append("m2/%s：本仓三线均未声明它 ⇒ 整目录保留（不动）" % artifactname)
            remain.extend(_list_dirs(artifact_dir))
        else:
            if not os.path.isdir(os.path.join(artifact_dir, pinned)):
                notes.append("m2/%s：缺少当前版本目录 %s（该线 run 需要它，留意是否漏发布）"
                             % (artifactname, pinned))
            for vd in _list_dirs(artifact_dir):
                if vd == pinned:
                    remain.append(vd)
                    continue
                vd_full = os.path.join(artifact_dir, vd)
                for f in _walk_files(vd_full):
                    rel = os.path.relpath(f, vd_full).replace(os.sep, "\\")
                    move.append({
                        "原路径": f,
                        "去向": os.path.join(quarantine, "m2-%s-%s-%s" % (artifactname, vd, rel.replace("\\", "_"))),
                    })
        m2_remain.append("%s: %s" % (artifactname, ", ".join(remain)))

    return {"Keep": keep, "Move": move, "M2Remain": m2_remain, "Notes": notes}


def _like(name: str, pattern: str) -> bool:
    import fnmatch
    return fnmatch.fnmatch(name, pattern)


def _walk_files(root: str) -> list:
    result = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames.sort(key=collation_key)
        for n in sorted_names(filenames):
            result.append(os.path.join(dirpath, n))
    return result


def show_cleanup_plan(plan: dict, quarantine_label: str) -> None:
    write_info("保留清单（当前各线 mod_version 派生，共 %d 个文件）：" % len(plan["Keep"]))
    for k in plan["Keep"]:
        write_info("  · [保留] %s" % k)
    write_info("待隔离清单（移到 %s，共 %d 个文件）：" % (quarantine_label, len(plan["Move"])))
    for m in plan["Move"]:
        write_info("  · [隔离] %s" % m["原路径"])
    write_info("mavenLocal 清理后剩余版本（每个 artifact）：")
    for r in plan["M2Remain"]:
        write_info("  · %s" % r)
    for n in plan["Notes"]:
        write_warn(n)


# ---------------------------------------------------------------------------
# 3. 旧版 jar 清理（可选，移动到隔离目录 + 清单）
# ---------------------------------------------------------------------------
def invoke_old_jar_cleanup() -> str:
    st = stamp()
    quarantine = os.path.join(REPO_ROOT, "temp", "old-jars-%s" % st)
    os.makedirs(quarantine, exist_ok=True)
    moved = []

    write_step("清理旧版 jar -> %s" % quarantine)
    plan = get_cleanup_plan(quarantine)
    write_info("保留清单：%d 个文件（由各线 mod_version / starengine_version 派生，不动）" % len(plan["Keep"]))
    write_info("待隔离清单：%d 个文件" % len(plan["Move"]))

    for m in plan["Move"]:
        size = os.path.getsize(m["原路径"])
        entry = {"原路径": m["原路径"], "大小": size, "sha256": get_sha256(m["原路径"]), "去向": m["去向"]}
        os.makedirs(os.path.dirname(m["去向"]), exist_ok=True)
        shutil.move(m["原路径"], m["去向"])
        moved.append(entry)
        write_info("移动 %s  (%d B)" % (m["原路径"], size))
    for n in plan["Notes"]:
        write_warn(n)

    # mavenLocal：被搬空的版本目录一并移除（每个 artifact 只保留该线当前的 starengine_version）
    for artifactname in _list_dirs(M2_REPO_ROOT):
        pinned = PINNED_LIB_VERSIONS.get(artifactname) or ""
        if not pinned:
            continue
        for vd in _list_dirs(os.path.join(M2_REPO_ROOT, artifactname)):
            if vd == pinned:
                continue
            vd_full = os.path.join(M2_REPO_ROOT, artifactname, vd)
            left = _walk_files(vd_full)
            if len(left) == 0:
                shutil.rmtree(vd_full, ignore_errors=True)
                write_info("移除空版本目录：m2\\%s\\%s" % (artifactname, vd))

    manifest_csv = os.path.join(quarantine, "manifest.csv")
    _write_csv(manifest_csv, ["原路径", "大小", "sha256", "去向"],
               [[m["原路径"], m["大小"], m["sha256"], m["去向"]] for m in moved])

    lines = ["# 旧版 jar 隔离清单（Start-SelfTest -CleanOldJars）", "",
             "隔离目录：%s" % quarantine,
             "生成时间：%s" % ps_time(datetime.now()), "",
             "| # | 原路径 | 大小(B) | sha256 | 去向 |", "|---|---|---|---|---|"]
    i = 0
    for m in moved:
        i += 1
        lines.append("| %d | \x60%s\x60 | %d | \x60%s\x60 | \x60%s\x60 |"
                     % (i, m["原路径"], m["大小"], m["sha256"], m["去向"]))
    if len(moved) == 0:
        lines.append("| - | （无旧版 jar，无需移动） | - | - | - |")
    manifest_md = os.path.join(quarantine, "manifest.md")
    with open(manifest_md, "w", encoding="utf-8", newline="\r\n") as fh:
        fh.write("\n".join(lines) + "\n")

    write_ok("已移动 %d 个文件到隔离目录（不硬删）" % len(moved))
    write_info("CSV  : %s" % manifest_csv)
    write_info("MD   : %s" % manifest_md)
    after = get_cleanup_plan(quarantine)
    write_info("清理后 mavenLocal 各 artifact 剩余版本（每个 artifact 只应剩当前 starengine_version）：")
    for r in after["M2Remain"]:
        write_info("  · %s" % r)
    return quarantine


def _write_csv(path: str, header: list, rows: list) -> None:
    def field(v) -> str:
        s = str(v)
        if any(c in s for c in ("	", ",", '"', "\r", "\n")):
            s = '"' + s.replace('"', '""') + '"'
        return s
    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(",".join(field(h) for h in header) + "\r\n")
        for row in rows:
            fh.write(",".join(field(c) for c in row) + "\r\n")


# ---------------------------------------------------------------------------
# 参数解析（兼容 pwsh 单破折号与 -Name:value 写法）
# ---------------------------------------------------------------------------
def _split_colon_forms(argv: list) -> list:
    known = {
        "-version", "-targetdir", "-targetkind", "-skipbuild", "-launch", "-dryrun",
        "-cleanoldjars", "-buildtimeoutsec", "-librepopath",
    }
    result = []
    for tok in argv:
        if tok.startswith("-") and ":" in tok:
            head, _, tail = tok.partition(":")
            if head.lower() in known:
                result.extend([head, tail])
                continue
        result.append(tok)
    return result


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="start_self_test.py",
        add_help=True,
        description="dev 工作树自测启用：构建 -> 部署（先删后拷）-> 可选启动 dev 客户端。等价 Start-SelfTest.ps1。",
        epilog="参数可写成 --version 1.21.1 或 pwsh 风格 -Version 1.21.1；开关项同样接受 -DryRun 等写法。",
    )
    p.add_argument("--version", "-Version", dest="version", default="1.21.1",
                   choices=list(VERSIONS), help="平台（缺省 1.21.1）")
    p.add_argument("--target-dir", "-TargetDir", "--targetdir", dest="target_dir", default=None,
                   help="目标 mods 目录（缺省 <repo>\\run\\<Version>\\mods）")
    p.add_argument("--target-kind", "-TargetKind", "--targetkind", dest="target_kind", default="auto",
                   choices=["auto", "dev", "prod"], help="目标形态：auto（缺省）/ dev / prod")
    p.add_argument("--skip-build", "-SkipBuild", "--skipbuild", dest="skip_build",
                   action="store_true", help="跳过构建，直接用现有产物部署")
    p.add_argument("--launch", "-Launch", dest="launch", action="store_true",
                   help="构建+部署后后台启动 dev 客户端（人工测试路径，代理不得自动调用）")
    p.add_argument("--dry-run", "-DryRun", "--dryrun", dest="dry_run", action="store_true",
                   help="只打印计划，零落盘")
    p.add_argument("--clean-old-jars", "-CleanOldJars", "--cleanoldjars", dest="clean_old_jars",
                   action="store_true", help="把旧版 jar 移动到 <repo>\\temp\\old-jars-<时间戳>\\ 并生成清单")
    p.add_argument("--build-timeout", "-BuildTimeoutSec", "--buildtimeoutsec", dest="build_timeout_sec",
                   type=int, default=180, help="Gradle 看门狗超时（秒，缺省 180）")
    p.add_argument("--lib-repo-path", "-LibRepoPath", "--librepopath", dest="lib_repo_path", default=None,
                   help="库仓库磁盘路径（缺省 = <repo 父目录>\\starengine_next）")
    return p


def main(argv=None) -> int:
    _enable_utf8_stdout()
    argv = list(sys.argv[1:] if argv is None else argv)
    args = build_parser().parse_args(_split_colon_forms(argv))

    version = args.version
    skip_build = args.skip_build
    launch = args.launch
    dry_run = args.dry_run
    clean_old_jars = args.clean_old_jars
    build_timeout_sec = args.build_timeout_sec
    lib_repo_path = args.lib_repo_path

    repo_root = REPO_ROOT
    if not lib_repo_path:
        lib_repo_path = os.path.join(REPO_PARENT, "starengine_next")

    if len(KEEP_MOD_JARS) == 0:
        try:
            fail("保留清单为空：三线 gradle.properties 都读不到 mod_version", ["仓库根 = %s" % repo_root])
        except Fail:
            return 1

    # -----------------------------------------------------------------------
    # 1. 解析本次运行的参数与目标
    # -----------------------------------------------------------------------
    line = LINE_MAP[version]
    sub = line["Sub"]
    props_path = os.path.join(repo_root, sub, "gradle.properties")
    try:
        return _run(args, repo_root, line, sub, props_path, version, lib_repo_path,
                    skip_build, launch, dry_run, clean_old_jars, build_timeout_sec)
    except Fail as exc:
        sys.stderr.write("Start-SelfTest: %s\n" % exc)
        return 1


def _run(args, repo_root, line, sub, props_path, version, lib_repo_path,
         skip_build, launch, dry_run, clean_old_jars, build_timeout_sec) -> int:

    if not os.path.isfile(props_path):
        fail("找不到 %s（仓库根解析异常？）" % props_path, ["仓库根 = %s" % repo_root])
    props = read_gradle_properties(props_path)
    lib_version = props.get("starengine_version")
    mod_version = props.get("mod_version")
    if not lib_version:
        fail("%s\\gradle.properties 里没有 starengine_version" % sub,
             ["库版本必须由该键驱动，不允许脚本硬编码"])

    target_dir = args.target_dir
    if not target_dir:
        target_dir = os.path.join(repo_root, "run", version, "mods")
    target_dir = os.path.abspath(target_dir)

    # 目标形态（dev/prod）：--target-kind 显式优先；否则 <repo>\run\ 下 或 目录名以 dev 结尾 ⇒ dev
    run_prefix = os.path.abspath(os.path.join(repo_root, "run")) + os.sep
    target_leaf = os.path.basename(target_dir.rstrip("\\/"))
    if args.target_kind != "auto":
        is_dev_target = args.target_kind == "dev"
        kind_reason = "显式 -TargetKind %s" % args.target_kind
    elif target_dir.lower().startswith(run_prefix.lower()):
        is_dev_target = True
        kind_reason = "目标在 <repo>\\run\\ 下"
    elif re.search("dev$", target_leaf, re.IGNORECASE):
        is_dev_target = True
        kind_reason = "目标目录最末段以 dev 结尾（%s）" % target_leaf
    else:
        is_dev_target = False
        kind_reason = "目标在 <repo>\\run\\ 之外且目录名不以 dev 结尾（外部实例 ⇒ 生产形态）"
    form = "dev" if is_dev_target else "prod"

    # 本模组 jar：dev 目标 + forge 线只允许 build\devlibs；其余 -> build\libs
    mod_jar_name = "astral_dice-%s.jar" % mod_version
    if form == "dev" and line["Kind"] == "forge":
        mod_jar_candidates = [os.path.join(repo_root, sub, "build", "devlibs", mod_jar_name)]
    else:
        mod_jar_candidates = [os.path.join(repo_root, sub, "build", "libs", mod_jar_name)]
    mod_jar = next((p for p in mod_jar_candidates if os.path.isfile(p)), None)

    # 库 jar：dev 目标 + forge 线 -> 库仓 build\devlibs；其余 -> mavenLocal
    lib_jar_dev = os.path.join(lib_repo_path, "forge-1.20.1", "build", "devlibs",
                               "%s-%s.jar" % (line["Artifact"], lib_version))
    lib_jar_m2 = os.path.join(M2_REPO_ROOT, line["Artifact"], lib_version,
                              "%s-%s.jar" % (line["Artifact"], lib_version))
    if form == "dev" and line["Kind"] == "forge":
        lib_jar = lib_jar_dev
        lib_jar_form = "dev（库仓 devlibs）"
    else:
        lib_jar = lib_jar_m2
        lib_jar_form = "生产/常规（mavenLocal）"

    # 独立库 jar 是否随本次部署落地：dev 目标恒需要；prod 目标取决于该线产物是否仍 JarJar 内嵌。
    build_gradle_path = os.path.join(repo_root, sub, "build.gradle")
    embeds_lib_jar = False
    if os.path.isfile(build_gradle_path):
        with open(build_gradle_path, "r", encoding="utf-8", errors="replace", newline="") as fh:
            embeds_lib_jar = re.search(r"(?m)^\s*jarJar\s*\(", fh.read()) is not None
    deploy_lib_jar = is_dev_target or (not embeds_lib_jar)

    # -----------------------------------------------------------------------
    # 2. 打印计划
    # -----------------------------------------------------------------------
    out("")
    out("=========== Start-SelfTest（dev 工作树自测启用） ===========")
    write_info("仓库根（由 $PSScriptRoot 推导） : %s" % repo_root)
    write_info("平台 / 子项目                   : %s / %s" % (version, sub))
    write_info("目标 mods 目录                  : %s" % target_dir)
    write_info("目标形态                        : %s（dev ⇒ dev/Mojmap 形态 jar；prod ⇒ 生产 reobf 形态 jar）｜判定依据：%s（可用 -TargetKind dev|prod 覆盖）"
               % (form, kind_reason))
    write_info("本模组 mod_version              : %s" % mod_version)
    write_info("库坐标                          : %s:%s:%s"
               % (props.get("starengine_group"), line["Artifact"], lib_version))
    if deploy_lib_jar:
        write_info("独立库 jar 部署                 : 是（库为独立前置，必须成对）· 形态来源 %s" % lib_jar_form)
    else:
        write_info("独立库 jar 部署                 : 否（该线产物仍 JarJar 内嵌：%s\\build.gradle 有 jarJar 声明）" % sub)
    write_info("本模组 jar                      : %s" % (mod_jar if mod_jar else "<未找到: %s>" % " | ".join(mod_jar_candidates)))
    if not deploy_lib_jar:
        lib_desc = "<不部署（该线产物仍内嵌）>"
    elif os.path.isfile(lib_jar):
        lib_desc = lib_jar
    else:
        lib_desc = "<缺失: %s>" % lib_jar
    write_info("配套库 jar                      : %s" % lib_desc)
    write_info("构建                            : %s"
               % ("跳过（-SkipBuild）" if skip_build
                  else "会执行 %s:build（后台 + %ds 看门狗）" % (line["GradlePath"], build_timeout_sec)))
    write_info("启动客户端                      : %s"
               % ("【人工测试路径】会后台启动 runClient" if launch
                  else "否（缺省不启动；-Launch 为人工路径，代理不得自动调用）"))
    cleanup_desc = ("会移动到 %s" % repo_root + "\\temp\\old-jars-<时间戳>\\") if clean_old_jars else "否（-CleanOldJars 才执行）"
    write_info("旧版 jar 清理                   : %s" % cleanup_desc)
    if dry_run:
        out("    *** -DryRun：只打印计划，零落盘 ***")
    out("==========================================================")
    out("")

    if dry_run:
        write_step("DryRun 计划明细")
        write_info("1) %s" % ("(跳过构建)" if skip_build
                             else "后台构建：%s\\gradlew.bat %s:build" % (repo_root, line["GradlePath"])))
        write_info("2) %s：%s" % ("成对部署到" if deploy_lib_jar else "部署（仅本模组 jar）到", target_dir))
        write_info("   · 先删除：astral_dice-*.jar、starengine-*.jar")
        write_info("   · 再拷贝：%s" % os.path.basename(mod_jar_candidates[0]))
        write_info("   · 再拷贝：%s" % (os.path.basename(lib_jar) if deploy_lib_jar
                                      else "(不拷贝库 jar：该线产物仍内嵌)"))
        write_info("3) %s" % ("清理旧版 jar -> temp\\old-jars-<时间戳>\\" if clean_old_jars
                             else "(不清理旧版 jar)"))
        write_info("4) %s" % ("后台启动 dev 客户端（人工路径）" if launch else "(不启动客户端)"))
        dry_quarantine = os.path.join(repo_root, "temp", "old-jars-<时间戳>")
        write_step("DryRun 旧版 jar 清理计划（纯只读扫描；%s）"
                   % ("本次带 -CleanOldJars，实际执行时按此计划移动" if clean_old_jars
                      else "本次未带 -CleanOldJars，仅预演、不移动"))
        show_cleanup_plan(get_cleanup_plan(dry_quarantine), dry_quarantine)
        out("")
        write_ok("DryRun 结束：未构建、未拷贝、未移动任何文件。")
        return 0

    # -----------------------------------------------------------------------
    # 3. 旧版 jar 清理（可选）
    # -----------------------------------------------------------------------
    if clean_old_jars:
        invoke_old_jar_cleanup()

    # -----------------------------------------------------------------------
    # 4. 构建（看门狗：后台 + 超时 + 日志/产物双验证）
    # -----------------------------------------------------------------------
    build_log = os.path.join(repo_root, "temp", "self-test",
                             "build-%s-%s.log" % (version, stamp()))
    os.makedirs(os.path.dirname(build_log), exist_ok=True)

    log_text = ""
    started_at = datetime.now()
    if not skip_build:
        write_step("构建 %s:build（日志：%s）" % (line["GradlePath"], build_log))
        gradlew = os.path.join(repo_root, "gradlew.bat")
        if not os.path.isfile(gradlew):
            fail("找不到 %s" % gradlew, ["仓库根解析异常"])
        started_at = datetime.now()
        creationflags = 0
        if os.name == "nt":
            creationflags = 0x08000000  # CREATE_NO_WINDOW（Start-Process -WindowStyle Hidden）
        with open(build_log, "wb") as out_fh, open(build_log + ".err", "wb") as err_fh:
            proc = subprocess.Popen(
                [gradlew, "%s:build" % line["GradlePath"], "--console=plain"],
                cwd=repo_root, stdout=out_fh, stderr=err_fh,
                creationflags=creationflags,
            )
            try:
                proc.wait(timeout=build_timeout_sec)
                exited = True
            except subprocess.TimeoutExpired:
                exited = False
        if not exited:
            write_warn("超过 %ds 未退出（仓库已知的 Gradle 守护进程不退出问题）⇒ 强制结束进程树，随后按规则做日志+产物双验证"
                       % build_timeout_sec)
            try:
                subprocess.run(["taskkill", "/PID", str(proc.pid), "/T", "/F"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            except OSError:
                pass
        try:
            with open(build_log, "r", encoding="utf-8", errors="replace", newline="") as fh:
                log_text = fh.read()
        except OSError:
            log_text = ""
        if re.search(r"BUILD FAILED", log_text, re.IGNORECASE) or re.search(r"error:", log_text, re.IGNORECASE):
            fail("构建失败（日志见 %s）" % build_log,
                 ["先修构建错误再重试；本次**不会**部署任何 jar"])
        if not re.search(r"BUILD SUCCESSFUL", log_text, re.IGNORECASE):
            fail("构建未出现 BUILD SUCCESSFUL（超时或异常；日志见 %s）" % build_log,
                 ["看 %s 与 %s.err" % (build_log, build_log),
                  "确认 Gradle 守护进程状态：gradlew --stop"])
        write_ok("日志验证：BUILD SUCCESSFUL")
    else:
        write_step("跳过构建（-SkipBuild）")
    write_info(build_log)

    # -----------------------------------------------------------------------
    # 5. 部署（核心）
    # -----------------------------------------------------------------------
    write_step("成对部署（本模组 jar + 库 jar）" if deploy_lib_jar
               else "部署（仅本模组 jar；该线产物仍 JarJar 内嵌）")

    mod_jar = next((p for p in mod_jar_candidates if os.path.isfile(p)), None)
    if not mod_jar:
        if form == "dev" and line["Kind"] == "forge":
            third = ("forge 线 dev 目标**不回退** build\\libs 的 reobf(SRG) 产物（那会在 dev run 里崩）；"
                     "请确认 %s:build 已在 build\\devlibs 产出 dev(Mojmap) jar" % line["GradlePath"])
        else:
            third = "确认该线构建真的产出了 jar（或去掉 -SkipBuild 重新构建）"
        fail("找不到本次构建的本模组 jar：%s" % mod_jar_name, [
            "本目标形态（%s）只接受：%s" % (form, " ; ".join(mod_jar_candidates)),
            "先跑：%s\\gradlew.bat %s:build" % (repo_root, line["GradlePath"]),
            third,
        ])
    if not skip_build and mod_jar:
        jar_time = datetime.fromtimestamp(os.path.getmtime(mod_jar))
        if jar_time < datetime.fromtimestamp(started_at.timestamp() - 5):
            jar_task_re = re.escape("%s:jar" % line["GradlePath"]) + r"\s+UP-TO-DATE"
            if re.search(jar_task_re, log_text, re.IGNORECASE):
                write_warn("本模组 jar 时间戳未更新（%s）：构建日志显示 %s:jar UP-TO-DATE —— "
                           "Gradle 已按内容哈希确认该产物与本次输入一致，放行部署"
                           % (ps_time(jar_time), line["GradlePath"]))
            else:
                fail("本模组 jar 时间戳早于本次构建开始，且构建日志里没有 %s:jar UP-TO-DATE 证据（可能未真正重建）：%s (%s)"
                     % (line["GradlePath"], mod_jar, ps_time(jar_time)),
                     ["检查 build 日志：%s" % build_log,
                      "必要时先 clean 再构建（gradlew.bat clean %s:build）" % line["GradlePath"]])
        write_ok("产物验证：%s  (%s)" % (os.path.basename(mod_jar), ps_time(jar_time)))
    mod_form = get_jar_form(mod_jar)
    write_info("本模组 jar 形态：%s（SRG 命中 %d）" % (mod_form["Form"], mod_form["Hits"]))
    if form == "dev" and mod_form["Form"] != "dev/mojmap":
        fail("dev 目标下本模组 jar 不是 dev/Mojmap 形态：%s" % mod_jar, [
            "dev run 目录必须放 dev/Mojmap 形态 jar：reobf(SRG) 产物会以 NoSuchFieldError(f_xxxxx_) 崩",
            "forge 线正确来源：%s\\%s\\build\\devlibs\\%s（先跑 %s\\gradlew.bat %s:build）"
            % (repo_root, sub, mod_jar_name, repo_root, line["GradlePath"]),
            # ps1 第 537 行是 "'…正确来源：' + (Join-Path …)"，写在 @( ) 的逗号元素里时
            # PowerShell 会把 expr + (cmd) 拆成两个数组元素（实测 @("a","b","c"+(...)) 的 Count=4），
            # 所以 ps1 实际打出两条 hint（标题一条、路径一条）；此处按同一行为拆开，保证逐字节一致。
            "NeoForge 线（无 reobf 环节）正确来源：",
            os.path.join(repo_root, sub, "build", "libs", mod_jar_name),
        ])
    if form == "prod" and line["Kind"] == "forge" and mod_form["Form"] != "reobf/srg":
        fail("prod 目标(1.20.1)下本模组 jar 不是 reobf/SRG 形态：%s" % mod_jar, [
            "外部实例/整合包必须放发货形态（reobfJar 产物），dev/Mojmap 形态会被 Forge 以 SRG 名解析不到而报错",
            "正确来源：%s\\%s\\build\\libs\\%s" % (repo_root, sub, mod_jar_name),
            "需要 dev 形态请在 run 目录（或 -TargetKind dev）下测试",
        ])

    lib_jar_name = os.path.basename(lib_jar) if deploy_lib_jar else os.path.basename(lib_jar)
    if deploy_lib_jar:
        if not os.path.isfile(lib_jar):
            fail("配套库 jar 不存在：%s" % lib_jar, [
                "dev 目标（人手启动路径）只认 mods 目录：缺库会被 FML 拒绝（Currently, starengine is not installed）",
                "1.21.1/26.1.2：在库仓库执行 \x60./gradlew publishToMavenLocal\x60（或指定版本）后重试",
                "1.20.1 dev 目标需要库仓库的 dev jar：cd %s && ./gradlew :forge-1.20.1:build"
                "（产出 build\\devlibs\\starengine-forge-1.20.1-%s.jar）" % (lib_repo_path, lib_version),
                "1.20.1 prod 目标（整合包等）用 mavenLocal 的 reobf 产物：cd %s && ./gradlew publishToMavenLocal" % lib_repo_path,
                "库版本必须与 %s\\gradle.properties 的 starengine_version=%s 一致" % (sub, lib_version),
                "（P0 两线已取消 JarJar 内嵌：prod 目标同样必须放独立库 jar；仍保留 jarJar 的线（26.1.2）由产物承载）",
            ])
        lib_form = get_jar_form(lib_jar)
        write_info("库 jar 形态：%s（SRG 命中 %d）" % (lib_form["Form"], lib_form["Hits"]))
        if line["Kind"] == "forge" and form == "dev" and lib_form["Form"] != "dev/mojmap":
            fail("dev 目标(1.20.1)下库 jar 不是 dev/Mojmap 形态：%s" % lib_jar, [
                "SRG 形态的库 jar 放进 dev run 会以 NoSuchFieldError 崩",
                "请用库仓库 devlibs 的 dev jar（重新执行库的 :forge-1.20.1:build）",
            ])
        if line["Kind"] == "forge" and form == "prod" and lib_form["Form"] != "reobf/srg":
            fail("prod 目标(1.20.1)下库 jar 不是 reobf/SRG 形态：%s" % lib_jar, [
                "外部实例/整合包必须放发货形态（reobf/SRG）的库 jar；dev/Mojmap 形态会被 Forge 以 SRG 名解析不到而报错",
                "正确来源：%s\\%s\\%s\\%s-%s.jar（先 cd %s && ./gradlew publishToMavenLocal）"
                % (M2_REPO_ROOT, line["Artifact"], lib_version, line["Artifact"], lib_version, lib_repo_path),
                "需要 dev 形态请在 run 目录（或 -TargetKind dev）下测试",
            ])

    # 版本一致性（从文件名解析）
    if os.path.basename(mod_jar) != mod_jar_name:
        fail("本模组 jar 名与 gradle.properties 的 mod_version 不一致（期望 %s）" % mod_jar_name,
             ["实际：%s" % os.path.basename(mod_jar)])
    if deploy_lib_jar:
        expected_lib_name = "%s-%s.jar" % (line["Artifact"], lib_version)
        if lib_jar_name != expected_lib_name:
            fail("库 jar 名与 starengine_version=%s 不一致（期望 %s）：%s"
                 % (lib_version, expected_lib_name, lib_jar_name),
                 ["先同步消费方 gradle.properties 或重发库 jar"])

    # 先删后拷
    if not os.path.isdir(target_dir):
        os.makedirs(target_dir, exist_ok=True)
    for f in _list_files(target_dir, "*.jar"):
        name = os.path.basename(f)
        if _like(name, "astral_dice-*.jar") or _like(name, "starengine-*.jar"):
            write_info("删除旧 jar：%s" % name)
            os.remove(f)

    shutil.copyfile(mod_jar, os.path.join(target_dir, mod_jar_name))
    write_ok("已拷贝：%s" % mod_jar_name)
    if deploy_lib_jar:
        shutil.copyfile(lib_jar, os.path.join(target_dir, lib_jar_name))
        write_ok("已拷贝：%s" % lib_jar_name)

    # 部署后校验
    after = [f for f in _list_files(target_dir, "*.jar")]
    mods = [f for f in after if _like(os.path.basename(f), "astral_dice-*.jar")]
    libs = [f for f in after if _like(os.path.basename(f), "starengine-*.jar")]
    want_libs = 1 if deploy_lib_jar else 0
    if len(mods) != 1 or len(libs) != want_libs:
        expect = ("各恰一份" if deploy_lib_jar
                  else "astral_dice 恰 1 份、starengine 恰 0 份（该线产物仍 JarJar 内嵌，独立 jar 会盖掉内嵌副本）")
        fail("部署后目标目录内 astral_dice-*.jar=%d 份、starengine-*.jar=%d 份（应：%s）"
             % (len(mods), len(libs), expect),
             ["目录：%s" % target_dir,
              "库为独立前置的线（P0）prod 目标也必须成对部署；仍内嵌的线（26.1.2）prod 由产物承载；"
              "dev 目标（run 目录 / Start-<版本>.bat）恒须成对"])
    if deploy_lib_jar and len(libs) == 1 and os.path.basename(libs[0]) != lib_jar_name:
        fail("部署后库 jar 名与 starengine_version=%s 不一致：%s（期望 %s）"
             % (lib_version, os.path.basename(libs[0]), lib_jar_name),
             ["目录：%s" % target_dir,
              "先删后拷应已清掉旧库；若仍不符，检查库 jar 来源与 starengine_version"])
    if deploy_lib_jar:
        write_ok("成对校验：%s + %s（库为独立前置；sha256 前 12 位 %s / %s）"
                 % (os.path.basename(mods[0]), os.path.basename(libs[0]),
                    get_sha256(mods[0])[:12], get_sha256(libs[0])[:12]))
    else:
        write_ok("内嵌校验：%s 单独部署（该线产物仍经 META-INF/jarjar/ 内嵌库；sha256 前 12 位 %s）"
                 % (os.path.basename(mods[0]), get_sha256(mods[0])[:12]))

    # -----------------------------------------------------------------------
    # 6. 可选启动（人工测试路径）
    # -----------------------------------------------------------------------
    if launch:
        out("")
        out("===================================================================")
        out(" 【人工测试路径】-Launch：代理不得自动调用本分支；此处由用户显式触发。")
        out("===================================================================")
        client_log = os.path.join(repo_root, "temp", "self-test",
                                  "%s-client-%s.log" % (version, stamp()))
        os.makedirs(os.path.dirname(client_log), exist_ok=True)
        gradlew = os.path.join(repo_root, "gradlew.bat")
        creationflags = 0x08000000 if os.name == "nt" else 0
        with open(client_log, "wb") as out_fh, open(client_log + ".err", "wb") as err_fh:
            proc = subprocess.Popen(
                [gradlew, "%s:runClient" % line["GradlePath"], "--console=plain"],
                cwd=repo_root, stdout=out_fh, stderr=err_fh, creationflags=creationflags,
            )
        write_ok("已后台启动 dev 客户端：PID = %d" % proc.pid)
        write_info("日志：%s" % client_log)
        write_info("停止：taskkill /PID %d /T /F   或   工具链 mt.ps1 --phase stop --force" % proc.pid)
    else:
        write_info("未启动客户端（缺省）。需要人工测试时由用户显式加 -Launch。")

    out("")
    write_ok("完成：%s 已构建并部署到 %s（%s）"
             % (version, target_dir,
                "本模组 jar + 独立库 jar 成对（库为独立前置）" if deploy_lib_jar
                else "仅本模组 jar（该线产物仍 JarJar 内嵌）"))
    write_info('用户在游戏内自测时请确认：日志里 starengine 与 astral_dice 都已加载，'
               '且无 "Missing or unsupported mandatory dependencies"。')
    return 0


if __name__ == "__main__":
    sys.exit(main())
