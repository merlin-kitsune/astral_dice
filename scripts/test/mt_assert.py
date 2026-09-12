#!/usr/bin/env python3
"""mt_assert — 断言引擎（阶段 C 的判定臂）。

判定原则：所有日志断言只针对**快照点之后的增量区间**求值。
旧流程是对整个 latest.log 做 grep，历史运行留下的同名标记会让断言假通过；
本模块用字节游标把断言锚定到本次运行产生的日志，使结论可复算。

子命令:
  snapshot --version V                       记录基线（日志字节游标 / crash 基线 / run id）
  log      --version V --pattern RE          增量区间内是否存在标记
  crash    --version V                       增量区间内无崩溃报告
  kubejs   --version V                       KubeJS server.log 为 0 errors
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from mt_paths import Paths, active_run_id, run_start_ts  # noqa: E402

EXIT_FAIL = 1
EXIT_ERROR = 2

SNAPSHOT_FILE = Path(__file__).resolve().parent / "cases" / ".mt_snapshot.json"


# ── 快照 ──────────────────────────────────────────────────────────────────
def load_snapshot() -> dict:
    if not SNAPSHOT_FILE.is_file():
        return {}
    try:
        return json.loads(SNAPSHOT_FILE.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}


def cmd_snapshot(args) -> int:
    p = Paths(args.version)
    run_id = active_run_id()
    snap = load_snapshot()
    if snap.get("run_id") != run_id:
        snap = {"run_id": run_id, "versions": {}}
    offsets: dict[str, int] = {}
    for log in (p.latest_log, p.debug_log, p.kubejs_log, p.probe_log):
        try:
            offsets[log.name] = log.stat().st_size if log.is_file() else 0
        except OSError:
            offsets[log.name] = 0
    snap.setdefault("versions", {})[args.version] = {
        "offsets": offsets,
        "ts": time.time(),
        "crash_baseline": sorted(f.name for f in p.crash_dir.glob("*.txt"))
        if p.crash_dir.is_dir() else [],
        "mixin_errors": [],
    }
    SNAPSHOT_FILE.parent.mkdir(parents=True, exist_ok=True)
    SNAPSHOT_FILE.write_text(json.dumps(snap, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"MT_SNAPSHOT: OK — {args.version} run={run_id} "
          f"latest={offsets.get('latest.log', 0)}B debug={offsets.get('debug.log', 0)}B "
          f"probe={offsets.get('astral_probe.log', 0)}B")
    return 0


def _snap_for(version: str) -> dict:
    return load_snapshot().get("versions", {}).get(version, {})


def _read_delta(log: Path, offset: int) -> str:
    """读取 offset 之后的新增内容；文件被轮转（变小）则整读。"""
    if not log.is_file():
        return ""
    size = log.stat().st_size
    if offset > size:
        # 本次运行重建了日志（launch 阶段清零），从 0 读
        offset = 0
    with log.open("r", encoding="utf-8", errors="ignore") as f:
        f.seek(offset)
        return f.read()


# ── 断言通道解析 ──────────────────────────────────────────────────────────
# latest —— 客户端游戏日志。**探针标记的权威通道**:命令输出经 ctx.source.sendFailure、
#            tick 回调经 ServerPlayer#sendSystemMessage,两条路都由 ChatComponent 落到
#            `[CHAT] AP_...`;mt_launch.sh 每次运行前删除该文件,故天然是「本轮增量」。
# debug  —— 客户端调试日志(Mixin 应用 / 渲染栈加载证据)。
#
# ⚠️ 已移除的 `probe` 通道(run/<版本>/astral_probe.log):该文件从未生成 ——
#    KubeJS 的 Java 类过滤器拒绝 java.io,FileWriter 构造失败又被 try/catch 静默吞掉,
#    表现为「服务端权威通道不存在」,把测试链故障伪装成修复无效。不要再加回来。
LOG_SOURCES: dict[str, str] = {"latest": "latest_log", "debug": "debug_log"}


def _log_for(p: Paths, source: str) -> Path:
    return getattr(p, LOG_SOURCES[source])


def _missing(p: Paths, source: str, log: Path) -> bool:
    """日志文件不存在属**环境问题**(游戏没起来/跑错目录),不是断言失败。"""
    if log.is_file():
        return False
    print(f"MT_ASSERT_LOG: ERROR — 断言通道缺失({source}:{log});"
          "游戏未启动或日志目录不对", file=sys.stderr)
    return True


def _show_hits(pat: re.Pattern, text: str, limit: int = 3) -> None:
    """打印命中示例行。

    ⚠️ **必须复用已编译的 pat**,不要用 f-string 重新拼 `.*{pattern}.*` 再编译。
    早期实现正是后者:一旦 pattern 带内联标志(如用例里的 `(?i)oculus`),标志就
    不在新表达式开头 → Python 3.11+ 抛 `re.error: global flags not at the start`,
    该异常未捕获 → 进程带 traceback 退出(traceback 走 stderr、stdout 已打印 PASS)
    → 表现为「断言明明打印 PASS,却被上层判成 FAIL」(2026-09-12 真机 BUG4 实测)。
    """
    shown = 0
    for ln in text.splitlines():
        if pat.search(ln):
            print(f"    {ln.strip()[:160]}")
            shown += 1
            if shown >= limit:
                break


# ── 断言 ──────────────────────────────────────────────────────────────────
def cmd_log(args) -> int:
    p = Paths(args.version)
    if not args.since_snapshot:
        print("MT_WARN: 未指定 --since-snapshot，将对全文件求值（可能被历史标记污染）",
              file=sys.stderr)
    log = _log_for(p, args.source)
    if _missing(p, args.source, log):
        return EXIT_ERROR
    offset = _snap_for(args.version).get("offsets", {}).get(log.name, 0) \
        if args.since_snapshot else 0
    text = _read_delta(log, offset)
    try:
        pat = re.compile(args.pattern)
    except re.error as exc:
        print(f"MT_ASSERT: ERROR — 非法正则 {args.pattern}：{exc}", file=sys.stderr)
        return EXIT_ERROR
    hits = pat.findall(text)
    label = f"{args.source}:{log.name}@{offset}B"
    if hits:
        print(f"MT_ASSERT_LOG: PASS — /{args.pattern}/ 命中 {len(hits)} 次（{label}）")
        _show_hits(pat, text)
        return 0
    print(f"MT_ASSERT_LOG: FAIL — /{args.pattern}/ 未命中（{label}）")
    return EXIT_FAIL


def cmd_log_absent(args) -> int:
    """反向断言：增量区间内不得出现某标记（用于「旧机制无残留」类 TC）。

    通道缺失时返回 ERROR 而不是 PASS —— 否则「文件不存在」会被静默判成
    「标记未出现」，把环境故障伪装成通过。
    """
    p = Paths(args.version)
    log = _log_for(p, args.source)
    if _missing(p, args.source, log):
        return EXIT_ERROR
    offset = _snap_for(args.version).get("offsets", {}).get(log.name, 0)
    text = _read_delta(log, offset)
    try:
        pat = re.compile(args.pattern)
    except re.error as exc:
        print(f"MT_ASSERT: ERROR — 非法正则 {args.pattern}：{exc}", file=sys.stderr)
        return EXIT_ERROR
    if pat.search(text):
        print(f"MT_ASSERT_ABSENT: FAIL — 不应出现的 /{args.pattern}/ 出现了")
        _show_hits(pat, text)
        return EXIT_FAIL
    print(f"MT_ASSERT_ABSENT: PASS — /{args.pattern}/ 未出现"
          f"（{args.source}:{log.name}@{offset}B）")
    return 0


def cmd_crash(args) -> int:
    p = Paths(args.version)
    baseline = set(_snap_for(args.version).get("crash_baseline", []))
    if not p.crash_dir.is_dir():
        print("MT_ASSERT_CRASH: PASS — 无 crash-reports 目录")
        return 0
    new = sorted(f.name for f in p.crash_dir.glob("*.txt") if f.name not in baseline)
    if new:
        print(f"MT_ASSERT_CRASH: FAIL — 新增崩溃报告 {', '.join(new)}")
        return EXIT_FAIL
    print(f"MT_ASSERT_CRASH: PASS — 本次运行无新增崩溃报告（基线 {len(baseline)} 个）")
    return 0


def cmd_kubejs(args) -> int:
    p = Paths(args.version)
    if not p.kubejs_log.is_file():
        print("MT_ASSERT_KUBEJS: BLOCKED — 未找到 KubeJS server.log"
              "（KubeJS 未加载或尚未进入世界）")
        return 11
    text = p.kubejs_log.read_text(encoding="utf-8", errors="ignore")
    errs = [ln for ln in text.splitlines() if re.search(r"\bERROR\b|\bException\b", ln)]
    if errs:
        print(f"MT_ASSERT_KUBEJS: FAIL — server.log 含 {len(errs)} 条 error")
        for ln in errs[:5]:
            print(f"    {ln.strip()[:160]}")
        return EXIT_FAIL
    print("MT_ASSERT_KUBEJS: PASS — server.log 0 errors")
    return 0


def cmd_mixin(args) -> int:
    """增量区间内不得出现 Mixin 应用失败（渲染栈兼容性的硬信号）。"""
    p = Paths(args.version)
    log = p.debug_log if p.debug_log.is_file() else p.latest_log
    offset = _snap_for(args.version).get("offsets", {}).get(log.name, 0)
    text = _read_delta(log, offset)
    pat = re.compile(r"Mixin apply failed|Mixin apply error|Failed to apply mixin")
    if pat.search(text):
        print("MT_ASSERT_MIXIN: FAIL — 检测到 Mixin 应用失败（兼容模组栈异常）")
        return EXIT_FAIL
    print(f"MT_ASSERT_MIXIN: PASS — {log.name} 增量区间无 Mixin 失败")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="mt 断言引擎")
    sub = ap.add_subparsers(dest="cmd", required=True)

    def common(sp):
        sp.add_argument("--version", required=True, choices=["1.21.1", "1.20.1"])
        return sp

    common(sub.add_parser("snapshot"))
    common(sub.add_parser("crash"))
    common(sub.add_parser("kubejs"))
    common(sub.add_parser("mixin"))

    lg = common(sub.add_parser("log"))
    lg.add_argument("--pattern", required=True)
    lg.add_argument("--source", choices=["latest", "debug", "probe"], default="latest")
    lg.add_argument("--since-snapshot", action="store_true", default=True)
    lg.add_argument("--no-snapshot", dest="since_snapshot", action="store_false")

    ab = common(sub.add_parser("absent"))
    ab.add_argument("--pattern", required=True)
    ab.add_argument("--source", choices=["latest", "debug", "probe"], default="latest")

    args = ap.parse_args()
    if args.cmd == "snapshot":
        return cmd_snapshot(args)
    if args.cmd == "log":
        return cmd_log(args)
    if args.cmd == "absent":
        return cmd_log_absent(args)
    if args.cmd == "crash":
        return cmd_crash(args)
    if args.cmd == "kubejs":
        return cmd_kubejs(args)
    if args.cmd == "mixin":
        return cmd_mixin(args)
    return EXIT_ERROR


if __name__ == "__main__":
    raise SystemExit(main())
