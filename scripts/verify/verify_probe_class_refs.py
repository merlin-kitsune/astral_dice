#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""探针类引用守门（由 旧版 verify_probe_class_refs 实现 等价迁移）

核对 KubeJS 探针里 Java.loadClass(...) 的目标是否真在运行时 classpath：
  1. build/libs/astral_dice-*.jar（取最新一份，排除 sources/javadoc）
  2. run/<版本>/mods/*.jar 全量
  3. <proj>/build/moddev/artifacts/<pattern>（MC 本体 + 加载器，取最新一份）
  4. 上述任意外层 jar 的 JarJar 内嵌 jar（META-INF/jarjar/*.jar，递归上限 3 层）
  5. mavenLocal 的 starengine 库 jar（gradle.properties:starengine_version 精确同名）
  另补 build.gradle 里 maven.modrinth:<slug>:<ver> 与 curse.maven:<slug>:<fileId> 的依赖
  （dev 运行期注入 classpath，不落 run/mods），从 gradle 缓存取最新 jar。

豁免：JDK 自带前缀 + com.mojang.* 运行期提供者 + org.lwjgl.（见 JDK_PREFIXES / WHITELIST）。

退出码（与 旧实现 一致）：0 = 全部可解析；1 = 有不可解析的类引用；2 = 无法判定 / 用法错误。

与 旧实现 的差异（逐条，均已在交付报告列明）：
  1) 默认 Root：旧实现 为 '.'（依赖 cwd ⇒ 在错误目录跑会假绿）；本脚本改为脚本位置上溯两级。
     显式 -Root 仍按传入值使用（与 旧实现 相同）。
  2) 反假绿新增：全部版本合计 "参与判据的 loadClass = 0 处" ⇒ 打印
     "RESULT: ERROR（无法判定）…" 并 exit 2（旧实现 该情形会落到 PASS）。文案沿用本脚本已有的
     [FATAL]/RESULT: ERROR 词汇。
  3) 输出换行/编码跟随平台（旧实现 用 Write-Output，同样是平台换行）；本脚本不做 JSON 类输出。
  4) gradle 缓存根：旧实现 用 $env:USERPROFILE（Linux 上为空会报错）；本脚本回落到 home 目录。

用法：
  python scripts/verify/verify_probe_class_refs.py [-Version 1.21.1|1.20.1|26.1.2|all] [-Root <仓库根>]
"""
from __future__ import annotations

import argparse
import fnmatch
import io
import os
import re
import sys
import zipfile

LINES = {
    "1.21.1": {
        "Proj": "neoforge-1.21.1",
        "LibArtifact": "starengine-neoforge-1.21.1",
        "ModsDir": "run/1.21.1/mods",
        "Artifacts": "neoforge-1.21.1/build/moddev/artifacts/neoforge-21.1.235*.jar",
    },
    "1.20.1": {
        "Proj": "forge-1.20.1",
        "LibArtifact": "starengine-forge-1.20.1",
        "ModsDir": "run/1.20.1/mods",
        "Artifacts": "forge-1.20.1/build/moddev/artifacts/forge-1.20.1-*.jar",
    },
    "26.1.2": {
        "Proj": "neoforge-26.1.2",
        "LibArtifact": "starengine-neoforge-26.1.2",
        "ModsDir": "run/26.1.2/mods",
        "Artifacts": "neoforge-26.1.2/build/moddev/artifacts/minecraft-patched-*.jar",
    },
}
VERSIONS = ("1.21.1", "1.20.1", "26.1.2")

JDK_PREFIXES = ("java.", "javax.", "jdk.", "sun.", "com.sun.", "org.w3c.", "org.xml.")
WHITELIST = (
    "com.mojang.brigadier.",
    "com.mojang.authlib.",
    "com.mojang.blaze3d.",
    "com.mojang.datafixers.",
    "com.mojang.serialization.",
    "org.lwjgl.",
)

CLASS_RE = re.compile(r"""Java\.loadClass\(\s*['"]([A-Za-z_$][A-Za-z0-9_$.]*)['"]\s*\)""")
MODRINTH_RE = re.compile(r"maven\.modrinth:([\w.\-]+):([\w.+\-]+)")
CURSE_RE = re.compile(r"curse\.maven:([\w.\-]+):(\d+)")
NOISE_RE = re.compile(r"sources|javadoc", re.IGNORECASE)
SOURCES_RE = re.compile(r"sources", re.IGNORECASE)
JARJAR_RE = re.compile(r"^META-INF/jarjar/.+\.jar$")
MAX_JAR_DEPTH = 3

ENTRIES = set()
BAD_REFS = []
NO_PROBE_DIR = False
CHECKED_TOTAL = 0


def norm_seps(p: str) -> str:
    """复刻 旧 shell Join-Path 在 Windows 上的分隔符归一（'/' -> '\\'）：
    旧实现 用 Join-Path 拼所有派生路径，Windows 上会把整条路径统一成反斜杠；
    posix 上 Join-Path 不做转换，故此处只在 os.sep 为 '\\' 时转换（Linux CI 无操作）。"""
    if os.sep == "\\":
        return p.replace("/", "\\")
    return p


def default_root() -> str:
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.normpath(os.path.join(here, "..", ".."))


def out(text: str = "") -> None:
    print(text)


# ---------------------------------------------------------------------------
# 按最后写入时间取最新一份（build/libs 常残留历史版本 jar）
# ---------------------------------------------------------------------------
def newest_file(pattern: str):
    hits = []
    for path in glob_paths(pattern):
        name = os.path.basename(path)
        if NOISE_RE.search(name):
            continue
        try:
            mtime = os.path.getmtime(path)
        except OSError:
            continue
        hits.append((mtime, len(hits), path))
    if not hits:
        return None
    hits.sort(key=lambda t: (t[0], t[1]), reverse=True)
    return hits[0][2]


def glob_paths(pattern: str):
    """非递归 glob（旧 shell Get-ChildItem -Path <pattern> -File 的等价）。"""
    import glob

    return sorted(glob.glob(pattern), key=lambda p: p)


# ---------------------------------------------------------------------------
# 单趟、字符串感知地剥掉 JS/Groovy 注释（只对代码里的引用判据）
# ---------------------------------------------------------------------------
def remove_js_comments(text: str) -> str:
    lf = 10
    bs = 92
    buf = []
    n = len(text)
    i = 0
    quote = ""
    while i < n:
        c = text[i]
        if quote:
            buf.append(c)
            if ord(c) == bs and (i + 1) < n:
                buf.append(text[i + 1])
                i += 2
                continue
            if c == quote:
                quote = ""
            i += 1
            continue
        if ord(c) == 34 or ord(c) == 39:
            quote = c
            buf.append(c)
            i += 1
            continue
        if c == "/" and (i + 1) < n:
            d = text[i + 1]
            if d == "*":
                end = text.find("*/", i + 2)
                if end < 0:
                    break
                block = text[i:end + 2]
                nl_count = sum(1 for ch in block if ord(ch) == lf)
                if nl_count > 0:
                    buf.append("\n" * nl_count)
                i = end + 2
                continue
            if d == "/":
                eol = text.find("\n", i)
                if eol < 0:
                    break
                i = eol
                continue
        buf.append(c)
        i += 1
    return "".join(buf)


# ---------------------------------------------------------------------------
# jar 条目收集（含 JarJar 内嵌 jar 递归展开）
# ---------------------------------------------------------------------------
def add_zip_entries(path: str) -> bool:
    try:
        with zipfile.ZipFile(path) as zf:
            names = zf.namelist()
            for name in names:
                ENTRIES.add(name)
            for name in names:
                if not JARJAR_RE.match(name):
                    continue
                try:
                    data = zf.read(name)
                except Exception:  # noqa: BLE001 - 内嵌读不了不影响外层结论
                    continue
                try:
                    add_nested_jar(io.BytesIO(data), 1)
                except Exception:  # noqa: BLE001
                    continue
        return True
    except Exception:  # noqa: BLE001 - 读不了返回 False（与 旧实现 的 catch 对齐）
        return False


def add_nested_jar(stream, depth: int) -> None:
    try:
        with zipfile.ZipFile(stream) as zf:
            names = zf.namelist()
            for name in names:
                ENTRIES.add(name)
            if depth >= MAX_JAR_DEPTH:
                return
            for name in names:
                if not JARJAR_RE.match(name):
                    continue
                try:
                    data = zf.read(name)
                except Exception:  # noqa: BLE001
                    continue
                try:
                    add_nested_jar(io.BytesIO(data), depth + 1)
                except Exception:  # noqa: BLE001
                    continue
    except Exception:  # noqa: BLE001
        return


# ---------------------------------------------------------------------------
# build.gradle 依赖（maven.modrinth / curse.maven）→ gradle 缓存 jar
# ---------------------------------------------------------------------------
def gradle_cache_root() -> str:
    home = os.environ.get("USERPROFILE") or os.path.expanduser("~")
    return os.path.join(home, ".gradle", "caches", "modules-2", "files-2.1")


def gradle_mod_jars(proj: str):
    jars = []
    unresolved = []
    g = os.path.join(proj, "build.gradle")
    if not os.path.isfile(g):
        unresolved.append("(缺 build.gradle: %s)" % g)
        return {"Jars": jars, "Unresolved": unresolved}

    with open(g, "r", encoding="utf-8-sig", errors="replace", newline="") as fh:
        text = fh.read()
    text = remove_js_comments(text)

    pairs = []
    curse_pairs = []
    for m in MODRINTH_RE.finditer(text):
        key = "%s|%s" % (m.group(1), m.group(2))
        if key not in pairs:
            pairs.append(key)
    for m in CURSE_RE.finditer(text):
        key = "%s|%s" % (m.group(1), m.group(2))
        if key not in curse_pairs:
            curse_pairs.append(key)

    cache_root = gradle_cache_root()
    for group, pairs_list, label in (
        ("maven.modrinth", pairs, "maven.modrinth:%s:%s"),
        ("curse.maven", curse_pairs, "curse.maven:%s:%s"),
    ):
        for key in pairs_list:
            parts = key.split("|")
            pat = os.path.join(cache_root, group, parts[0], parts[1], "*", "*.jar")
            hits = []
            for path in glob_paths(pat):
                name = os.path.basename(path)
                if SOURCES_RE.search(name):
                    continue
                try:
                    hits.append((os.path.getmtime(path), len(hits), path))
                except OSError:
                    continue
            if hits:
                hits.sort(key=lambda t: (t[0], t[1]), reverse=True)
                jars.append(hits[0][2])
            else:
                unresolved.append(label % (parts[0], parts[1]))
    return {"Jars": jars, "Unresolved": unresolved}


def gradle_property(path: str, key: str):
    if not os.path.isfile(path):
        return None
    with open(path, "r", encoding="utf-8-sig", errors="replace", newline="") as fh:
        text = fh.read()
    m = re.search(r"(?m)^\s*" + re.escape(key) + r"\s*=\s*(.+?)\s*$", text)
    if not m:
        return None
    return m.group(1).strip()


def m2_repository_roots():
    cands = []
    home = os.environ.get("HOME")
    if home:
        cands.append(os.path.join(home, ".m2", "repository"))
    profile = os.environ.get("USERPROFILE")
    if profile:
        cands.append(os.path.join(profile, ".m2", "repository"))
    unames = []
    if os.environ.get("USERNAME"):
        unames.append(os.environ["USERNAME"])
    if os.environ.get("USER") and os.environ.get("USER") != os.environ.get("USERNAME"):
        unames.append(os.environ["USER"])
    for uname in unames:
        if sys.platform.startswith("linux"):
            cands.append("/mnt/c/Users/%s/.m2/repository" % uname)
        else:
            cands.append("C:\\Users\\%s\\.m2\\repository" % uname)

    roots = []
    seen = set()
    for c in cands:
        if not c or not c.strip():
            continue
        try:
            full = os.path.abspath(c)
        except Exception:  # noqa: BLE001
            continue
        key = os.path.normcase(full)
        if key in seen:
            continue
        seen.add(key)
        roots.append(full)
    return roots


def maven_local_lib_jar(proj: str, artifact: str):
    ver = gradle_property(os.path.join(proj, "gradle.properties"), "starengine_version")
    tried = []
    if not ver:
        return {"Jar": None, "Version": None, "Tried": tried}
    rel = os.path.join("com", "merlinkitsune", "starengine", artifact, ver, "%s-%s.jar" % (artifact, ver))
    for root in m2_repository_roots():
        p = os.path.join(root, rel)
        tried.append(p)
        if os.path.isfile(p):
            return {"Jar": p, "Version": ver, "Tried": tried}
    return {"Jar": None, "Version": ver, "Tried": tried}


# ---------------------------------------------------------------------------
# 全限定名 → 可能的 class 条目名（含内嵌类折叠成 $Inner 的写法）
# ---------------------------------------------------------------------------
def class_variants(fqn: str):
    parts = fqn.split(".")
    result = ["/".join(parts) + ".class"]
    for i in range(len(parts) - 1, 2, -1):
        head = "/".join(parts[0:i])
        tail = "$".join(parts[i:])
        result.append("%s$%s.class" % (head, tail))
    return result


# ---------------------------------------------------------------------------
# 探针文件枚举：复刻 Get-ChildItem -Recurse -Filter *.js（本级文件优先，再 DFS 子目录）
# ---------------------------------------------------------------------------
def iter_js_files(root: str):
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
            elif e.name.lower().endswith(".js"):
                files.append(e.name)
        except OSError:
            continue
    for name in files:
        yield os.path.join(root, name)
    for name in dirs:
        yield from iter_js_files(os.path.join(root, name))


def probe_line(ver: str, root: str) -> None:
    global NO_PROBE_DIR, CHECKED_TOTAL

    cfg = LINES[ver]
    proj = os.path.join(root, cfg["Proj"])

    jars = []
    mod_jar = newest_file(os.path.join(proj, "build/libs/astral_dice-*.jar"))
    if mod_jar:
        jars.append(mod_jar)
    art = newest_file(os.path.join(root, cfg["Artifacts"]))
    if art:
        jars.append(art)
    mods_dir = os.path.join(root, cfg["ModsDir"])
    mods_jars = [p for p in glob_paths(os.path.join(mods_dir, "*.jar")) if os.path.isfile(p)]
    jars.extend(mods_jars)
    dep = gradle_mod_jars(proj)
    jars.extend(dep["Jars"])
    lib_info = maven_local_lib_jar(proj, cfg["LibArtifact"])
    if lib_info["Jar"]:
        jars.append(lib_info["Jar"])

    out("## %s   classpath jar = %d 个（run/mods %d + gradle 依赖 %d + mavenLocal 库 %d）"
        % (ver, len(jars), len(mods_jars), len(dep["Jars"]), 1 if lib_info["Jar"] else 0))
    if lib_info["Jar"]:
        out("   + 库 jar 来自 mavenLocal：%s" % lib_info["Jar"])
    else:
        if lib_info["Version"]:
            lib_why = "starengine_version=%s" % lib_info["Version"]
        else:
            lib_why = "gradle.properties 无 starengine_version"
        out("   ! mavenLocal 未找到库 jar（%s）：com.merlinkitsune.starengine.* 可能误报；"
            "请安装到 mavenLocal 或把库 jar 放进 run/<版本>/mods" % lib_why)
        for t in lib_info["Tried"]:
            out("     - 已尝试：%s" % t)
    if not mod_jar:
        out("   ! 未找到构建产物（%s/build/libs）—— 该线的模组类判据会大面积误报，先跑构建" % cfg["Proj"])
    for u in dep["Unresolved"]:
        out("   ! 依赖未在 gradle 缓存找到：%s" % u)

    ENTRIES.clear()
    for j in jars:
        if not add_zip_entries(j):
            out("   ! 无法读取：%s" % j)
    out("   条目合计 = %d" % len(ENTRIES))

    src_dir = os.path.join(root, "scripts/test/resources/kubejs", ver)
    if not os.path.isdir(src_dir):
        out("   [FATAL] 无探针目录 ⇒ 无法判定（ROOT 错或路径约定变化）；不得判 PASS")
        NO_PROBE_DIR = True
        out("")
        return
    files = list(iter_js_files(src_dir))
    if len(files) == 0:
        out("   [FATAL] 探针目录存在但 0 个 .js 脚本 ⇒ 无法判定（路径约定变化 / 资源缺失）；不得判 PASS")
        NO_PROBE_DIR = True
        out("")
        return

    checked = 0
    bad = []
    for f in files:
        with open(f, "r", encoding="utf-8-sig", errors="replace", newline="") as fh:
            text = remove_js_comments(fh.read())
        seen = set()
        for m in CLASS_RE.finditer(text):
            fqn = m.group(1)
            if fqn in seen:
                continue
            seen.add(fqn)
            skip = False
            for p in JDK_PREFIXES:
                if fqn.startswith(p):
                    skip = True
                    break
            if not skip:
                for p in WHITELIST:
                    if fqn.startswith(p):
                        skip = True
                        break
            if skip:
                continue
            checked += 1
            found = False
            for v in class_variants(fqn):
                if v in ENTRIES:
                    found = True
                    break
            if not found:
                bad.append({"File": os.path.basename(f), "Class": fqn})

    CHECKED_TOTAL += checked
    out("   脚本 %d 个 / 参与判据的 loadClass %d 处 / **不可解析 %d 处**" % (len(files), checked, len(bad)))
    for b in bad:
        out("     x %-40s %s" % (b["File"], b["Class"]))
        out("       ^ 该类不在运行时 classpath。若它已被下沉到 starengine，须改写为 com.merlinkitsune.starengine.*")
        BAD_REFS.append(b)
    out("")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="探针类引用守门（旧实现 等价迁移版）")
    ap.add_argument("-Version", "--version", dest="version", default="all",
                    choices=("1.21.1", "1.20.1", "26.1.2", "all"), help="要检查的版本线（默认 all）")
    ap.add_argument("root_pos", nargs="?", default=None, help="仓库根（位置参数，可选）")
    ap.add_argument("-Root", "--root", dest="root", default=None,
                    help="仓库根（默认脚本位置上溯两级；旧实现 原默认是 '.'）")
    args = ap.parse_args(argv)

    root = norm_seps(args.root if args.root is not None else (args.root_pos or default_root()))
    vers = VERSIONS if args.version == "all" else (args.version,)

    for v in vers:
        probe_line(v, root)

    if NO_PROBE_DIR:
        out("RESULT: ERROR（无法判定）—— 至少一条线无探针目录或目录内 0 个 .js 脚本，未核到任何类引用")
        return 2
    if CHECKED_TOTAL == 0:
        out("RESULT: ERROR（无法判定）—— 未扫到任何参与判据的 Java.loadClass 引用（0 处），拒绝 vacuous PASS")
        return 2
    if len(BAD_REFS) == 0:
        out("RESULT: PASS —— 三线探针的 Java.loadClass 目标全部可在运行时 classpath 解析")
        return 0
    out("RESULT: FAIL —— 存在 %d 处不可解析的类引用（脚本会加载失败 => 所有 type:kubejs 断言全红）"
        % len(BAD_REFS))
    return 1


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass
    sys.exit(main())
