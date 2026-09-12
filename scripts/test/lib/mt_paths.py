#!/usr/bin/env python3
"""mt — 路径与版本映射（python 侧唯一路径来源）。

与 bash 侧 lib/paths.sh 保持同一张版本表；任何一侧新增版本都必须同步另一侧。
所有路径从本文件位置向上推导，不依赖当前工作目录。
"""
from __future__ import annotations

import json
import os
import time
from pathlib import Path

# ── 版本表 ────────────────────────────────────────────────────────────────
# 顺序即测试顺序：1.21.1 优先，通过后才执行 1.20.1（见 AGENTS「测试顺序」）。
VERSIONS: tuple[str, ...] = ("1.21.1", "1.20.1")

_LOADER: dict[str, str] = {"1.21.1": "neoforge", "1.20.1": "forge"}
_SUBPROJECT: dict[str, str] = {"1.21.1": "neoforge-1.21.1", "1.20.1": "forge-1.20.1"}

# 机器本地配置（整合包 mods 源等）由 mt.conf 提供；缺失时回落到这些默认值。
_DEFAULT_PACK_MODS: dict[str, str] = {
    "1.21.1": r"D:\.minecraft\versions\狐の航空学 Voxy Edition\mods",
    "1.20.1": r"D:\.minecraft\versions\1.20.1 模组测试\mods",
}

PUBLISH_PORT = 25565
WORLD_NAME = "testworld"

# ── 目录推导 ──────────────────────────────────────────────────────────────
LIB_DIR = Path(__file__).resolve().parent
TEST_DIR = LIB_DIR.parent                     # scripts/test
ROOT = TEST_DIR.parent.parent                 # 仓库根
CONF_FILE = TEST_DIR / "mt.conf"
RUNS_FILE = TEST_DIR / "cases" / ".mt_active_run"
SHOTS_MANIFEST = ".mt_shots.json"


def _load_conf() -> dict[str, str]:
    """读取 mt.conf（KEY=VALUE，shell 可 source 的同一份文件）。"""
    conf: dict[str, str] = {}
    if CONF_FILE.is_file():
        for raw in CONF_FILE.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, val = line.partition("=")
            conf[key.strip()] = val.strip().strip('"').strip("'")
    return conf


CONF = _load_conf()


def _conf_key(version: str) -> str:
    return "MT_PACK_MODS_NEOFORGE" if version == "1.21.1" else "MT_PACK_MODS_FORGE"


class Paths:
    """单个版本的完整路径集。"""

    def __init__(self, version: str) -> None:
        if version not in VERSIONS:
            raise ValueError(f"未知版本 {version}（可选：{' / '.join(VERSIONS)}）")
        self.version = version
        self.loader = _LOADER[version]
        self.subproject = _SUBPROJECT[version]
        self.root = ROOT

        self.run_dir = ROOT / "run" / version
        self.mods_dir = self.run_dir / "mods"
        self.saves_dir = self.run_dir / "saves"
        self.logs_dir = self.run_dir / "logs"
        self.latest_log = self.logs_dir / "latest.log"
        self.debug_log = self.logs_dir / "debug.log"
        self.kubejs_log = self.logs_dir / "kubejs" / "server.log"
        # 服务端权威通道：由 run/<版本>/kubejs/server_scripts/astral_bugfix_probe.js
        # 追加写（工作目录 = run_dir）。独立于客户端渲染/聊天与 SLF4J 配置，
        # 因此「客户端卡死 / logger 被过滤」都不影响断言取证（source=probe）。
        self.probe_log = self.run_dir / "astral_probe.log"
        self.crash_dir = self.run_dir / "crash-reports"
        self.shot_dir = self.run_dir / "screenshots"
        self.client_world = self.saves_dir / WORLD_NAME
        self.server_world = self.run_dir / WORLD_NAME

        self.task_build = f":{self.subproject}:build"
        self.task_client = f":{self.subproject}:runClient"
        self.task_server = f":{self.subproject}:runServer"

        self.pack_mods_dir = Path(CONF.get(_conf_key(version), _DEFAULT_PACK_MODS[version]))

    # ── 进程识别（mt_env.kill / mt_preflight 共用，避免两侧判据漂移）────────
    @property
    def process_markers(self) -> tuple[str, ...]:
        """命令行中标识「该进程属于本版本的本流程」的标记。

        刻意**不使用裸子项目名**（如 `neoforge-1.21.1`）：那同时是仓库内的目录名，
        任何只是引用了该目录的进程（IDE 语言服务器、索引任务）都会被误命中，
        进而被 mt_stop 误杀。改为要求 gradle 任务选择器（两侧带冒号）或 run 目录。
        """
        sep = "\\" if os.name == "nt" else "/"
        return (f":{self.subproject}:", f"run{sep}{self.version}", str(self.run_dir))

    # ── 截图世代（AGENTS「截图识别」：仅认当前/最新世代）──────────────────
    @property
    def shots_manifest(self) -> Path:
        return self.shot_dir / SHOTS_MANIFEST

    def record_shot(self, path: Path, tag: str, run_id: str) -> None:
        """把一张截图登记进当前世代清单。"""
        data = self.read_shots()
        if data.get("run_id") != run_id:
            data = {"run_id": run_id, "shots": []}
        data["shots"].append({"file": Path(path).name, "tag": tag, "ts": time.time()})
        self.shot_dir.mkdir(parents=True, exist_ok=True)
        self.shots_manifest.write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    def read_shots(self) -> dict:
        if not self.shots_manifest.is_file():
            return {}
        try:
            return json.loads(self.shots_manifest.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            return {}

    def current_shots(self, run_id: str) -> list[Path]:
        """只返回当前世代的截图；无清单时回落到「比运行开始更新」的文件。

        这是「仅识别当前（最新）生成的截图」的唯一实现入口——任何断言都不得
        直接 glob 截图目录，否则会误认上一世代的残留证据。
        """
        data = self.read_shots()
        if data.get("run_id") == run_id:
            names = {s["file"] for s in data.get("shots", [])}
            out = [self.shot_dir / n for n in names if (self.shot_dir / n).is_file()]
            if out:
                return sorted(out, key=lambda p: p.stat().st_mtime)
        # 回落：以运行开始时间为界，取最新连续一批
        start = run_start_ts(run_id)
        if not self.shot_dir.is_dir():
            return []
        fresh = [p for p in self.shot_dir.glob("*.png") if p.stat().st_mtime >= start]
        return sorted(fresh, key=lambda p: p.stat().st_mtime)


# ── 运行标识 ──────────────────────────────────────────────────────────────
def new_run_id() -> str:
    return time.strftime("%Y%m%d-%H%M%S")


def active_run_id() -> str:
    """读取当前运行 id；不存在则新建并落盘（供 bash/python 两侧共享）。"""
    if RUNS_FILE.is_file():
        txt = RUNS_FILE.read_text(encoding="utf-8").strip()
        if txt:
            return txt
    rid = new_run_id()
    RUNS_FILE.parent.mkdir(parents=True, exist_ok=True)
    RUNS_FILE.write_text(rid, encoding="utf-8")
    return rid


def run_start_ts(run_id: str) -> float:
    """由 run id（yyyymmdd-HHMMSS）还原运行开始时间戳。

    ⚠️ 2026-09-12（pwsh 迁移期修正）：原实现只捕获 ValueError，但 Windows 上
    `time.mktime` 对**可解析却越界**的时间（如 19700101-000000，早于本地 epoch）
    抛的是 OverflowError，会直接冒泡成未捕获异常。按本函数自身文档「解析失败返回
    0.0」的契约，这里把 OverflowError / OSError 一并折叠为 0.0。
    """
    try:
        return time.mktime(time.strptime(run_id, "%Y%m%d-%H%M%S"))
    except (ValueError, OverflowError, OSError):
        return 0.0


def reports_dir(run_id: str) -> Path:
    d = TEST_DIR / "reports" / run_id
    d.mkdir(parents=True, exist_ok=True)
    return d


def all_versions() -> tuple[str, ...]:
    return VERSIONS
