#!/usr/bin/env python3
"""mt_paths.py — 单仓路径事实源（bash/python 侧）。

与 scripts/test/lib/Mt.Paths.psm1、scripts/test/win/lib/common.sh 的版本表 / 子项目映射**同口径**，
供 mt_case.py 在**不启动游戏**的前提下定位产物 jar（op=jar_stats 与 jar/bytecode 断言的数据源）。

映射（三处必须一致，改一处就要改三处）：
  1.21.1        -> neoforge-1.21.1   (neoforge)
  1.20.1        -> forge-1.20.1      (forge)
  26.1.2        -> neoforge-26.1.2   (neoforge)
  fabric-1.20.1 -> fabric-1.20.1     (fabric)

产物定位口径（与 pwsh Get-MtCaseJarEntries 逐条对齐）：
  · 只在 <repo>/<subproject>/build/libs 下找，不做版本目录拼接
  · 候选 = astral_dice-*.jar，排除 -(sources|dev|slim).jar$
  · 取 mtime 最新的一只；找不到 / 打不开 ⇒ 抛 JarMissing（调用方决定 BLOCKED/FAIL），
    **绝不返回空列表冒充「0 命中」** —— 那会让 bytecode_absent 恒 PASS（假绿）。
"""
from __future__ import annotations

import glob
import os
import re
import zipfile

VERSIONS = ("1.21.1", "1.20.1", "26.1.2", "fabric-1.20.1")
SUBPROJECT = {
    "1.21.1": "neoforge-1.21.1",
    "1.20.1": "forge-1.20.1",
    "26.1.2": "neoforge-26.1.2",
    "fabric-1.20.1": "fabric-1.20.1",
}
LOADER = {"1.21.1": "neoforge", "1.20.1": "forge", "26.1.2": "neoforge", "fabric-1.20.1": "fabric"}
JAR_GLOB = "astral_dice-*.jar"
JAR_EXCLUDE = re.compile(r"-(sources|dev|slim)\.jar$")

# jar_stats 口径（pwsh Get-MtCaseJarStats + bash fabric.sh mt_fab_cases_run 的 recipes 修正）
MODELS_PREFIX = "assets/astral_dice/models/item/"
RECIPES_PREFIXES = ("data/astral_dice/recipes/", "data/astral_dice/recipe/")
TRINKETS_PREFIX = "data/trinkets/"
BOUNTIFUL_PREFIX = "data/bountiful/"
EMBED_PREFIX = "META-INF/jars/"
SIGN_RE = re.compile(r"item/sign/[A-Za-z]+SignItem\.class$")


class JarMissing(RuntimeError):
    """产物 jar 不存在 / 不是合法 zip。调用方据此记 BLOCKED（不是 FAIL：工具链事实）。"""


def repo_root(start: str | None = None) -> str:
    """仓库根：默认从本文件位置推（scripts/test/lib -> 上三级）。"""
    here = os.path.abspath(start or os.path.dirname(os.path.abspath(__file__)))
    return os.path.abspath(os.path.join(here, "..", "..", ".."))


def subproject(version: str) -> str:
    v = str(version or "").strip()
    if v not in SUBPROJECT:
        raise KeyError(f"未知版本 {v!r}（可选：{' / '.join(VERSIONS)}）")
    return SUBPROJECT[v]


def libs_dir(version: str, root: str | None = None) -> str:
    return os.path.join(root or repo_root(), subproject(version), "build", "libs")


def newest_jar(version: str, root: str | None = None) -> str | None:
    d = libs_dir(version, root)
    if not os.path.isdir(d):
        return None
    cands = [p for p in glob.glob(os.path.join(d, JAR_GLOB))
             if not JAR_EXCLUDE.search(os.path.basename(p))]
    if not cands:
        return None
    return max(cands, key=os.path.getmtime)


def jar_entries(version: str, root: str | None = None) -> tuple[str | None, list[str]]:
    """返回 (jar 路径, 条目名列表)。没有 jar 或不是合法 zip ⇒ (None, [])。"""
    path = newest_jar(version, root)
    if path is None:
        return None, []
    try:
        with zipfile.ZipFile(path) as zf:
            return path, zf.namelist()
    except (OSError, zipfile.BadZipFile):
        return None, []


def _count(names: list[str], prefixes, suffix: str) -> int:
    if isinstance(prefixes, str):
        prefixes = (prefixes,)
    return sum(1 for n in names if n.endswith(suffix) and n.startswith(tuple(prefixes)))


def jar_stats(version: str, root: str | None = None) -> dict:
    """离线开包统计。字段与 pwsh / bash 两侧名字完全相同（下游断言按名取）。"""
    path, names = jar_entries(version, root)
    if path is None:
        raise JarMissing(f"找不到产物 jar（先跑 --phase build）：{libs_dir(version, root)}")
    return {
        "jar": os.path.basename(path),
        "entries": len(names),
        "models": _count(names, MODELS_PREFIX, ".json"),
        # 1.20.1(Forge) 用 recipes/，1.21+(NeoForge/Fabric) 用 recipe/ —— 两个都数，否则 neo 线恒为 0
        "recipes": _count(names, RECIPES_PREFIXES, ".json"),
        "trinkets": _count(names, TRINKETS_PREFIX, ".json"),
        "bountiful": _count(names, BOUNTIFUL_PREFIX, ".json"),
        "embed": _count(names, EMBED_PREFIX, ".jar"),
        "signs": sum(1 for n in names if SIGN_RE.search(n)),
    }


def run_dir(version: str, side: str | None = None, root: str | None = None) -> str:
    """run 根：fabric 双 run 目录 <sub>/run/<side>；另三线 run/<版本>。"""
    base = root or repo_root()
    sub = subproject(version)
    if version == "fabric-1.20.1":
        return os.path.join(base, sub, "run", (side or "server").strip() or "server")
    return os.path.join(base, "run", version)


def mods_dir(version: str, side: str | None = None, root: str | None = None) -> str:
    return os.path.join(run_dir(version, side, root), "mods")


def fabric_injected_flag(version: str, side: str | None = None, root: str | None = None) -> str:
    """fabric 线「本次启动是否注入了 AutoJS」的标记文件（AutoJS jar 永不进 mods/）。"""
    return os.path.join(run_dir(version, side, root), ".mt_autojs_injected")
