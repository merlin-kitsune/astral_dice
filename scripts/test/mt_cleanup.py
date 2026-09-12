#!/usr/bin/env python3
"""mt_cleanup — 流程退出时的进程收停（唯一实现）。

被三个入口共用，避免出现两套逻辑：
  - `mt.sh` 的退出钩子（正常结束 / 失败 / Ctrl-C 中断三条路径都覆盖）；
  - `mt.sh --phase stop`（显式收停）；
  - `mt_stop.sh`（薄封装）。

## 收停范围（只碰本流程自己的东西）

1. **本流程的 dev 客户端 / Gradle 任务进程** —— 判据为 `Paths.process_markers`
   （gradle 任务选择器 + run 目录），不会误杀 IDE 语言服务器或用户自己的客户端；
2. **Gradle 守护进程** —— 用 `gradlew --stop` 优雅停止（而非 `taskkill /IM gradle.exe`，
   后者会连用户其它项目的 Gradle 一起杀）。可用 `--keep-daemon` 跳过以保留构建缓存热态。

## 与失败取证的配合

条目 `on_fail=keep_game_running` 失败时会落 `.mt_keep_alive` 标记。该标记存在时，
本模块**不杀客户端也不停守护进程**（守护进程可能是客户端的父进程），只打印提示 ——
否则「自动清理」会在失败瞬间销毁现场，让取证变得不可能。
取证完成后用 `--force` 或先清掉标记再 `--phase stop`。

## 用法

  mt_cleanup.py status                    # 列出残留（退出码 0=干净 / 1=有残留）
  mt_cleanup.py run                       # 收停（尊重 keep_alive）
  mt_cleanup.py run --version 1.21.1      # 只收停指定版本（可重复）
  mt_cleanup.py run --force               # 忽略 keep_alive，强制收停
  mt_cleanup.py run --keep-daemon         # 只收停客户端，保留 Gradle 守护
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from mt_paths import ROOT, VERSIONS, Paths  # noqa: E402
from mt_ps import java_procs, run_tolerant  # noqa: E402

TEST_DIR = Path(__file__).resolve().parent
KEEP_ALIVE = TEST_DIR / "cases" / ".mt_keep_alive"

GRADLE_DAEMON_MARK = "GradleDaemon"


def pipeline_procs(versions: list[str] | None = None) -> list[tuple[str, int, str]]:
    """本流程自己的 java 进程：[(版本, pid, 命令行)]。

    `versions` 为 None 时扫描全部版本；否则只保留指定版本，
    用于 `mt_stop.sh --version X` 这类「只收停某版本」的调用。
    """
    wanted = list(versions) if versions else list(VERSIONS)
    markers = {v: Paths(v).process_markers for v in wanted}
    out: list[tuple[str, int, str]] = []
    for pid, cmd in java_procs():
        for v, ms in markers.items():
            if any(m and m in cmd for m in ms):
                out.append((v, pid, cmd))
                break
    return out


def daemon_procs() -> list[tuple[int, str]]:
    """Gradle 守护进程（不属于任何单版本，故单独处理）。"""
    return [(pid, cmd) for pid, cmd in java_procs() if GRADLE_DAEMON_MARK in cmd]


def keep_alive_reason() -> str | None:
    if KEEP_ALIVE.is_file():
        try:
            return KEEP_ALIVE.read_text(encoding="utf-8").strip() or "（未记录原因）"
        except OSError:
            return "（读取标记失败）"
    return None


# ══ status ════════════════════════════════════════════════════════════════
def cmd_status(args) -> int:
    ppl = pipeline_procs()
    dps = daemon_procs()
    print(f"本流程进程 {len(ppl)} 个：")
    for v, pid, cmd in ppl:
        print(f"    [{v}] PID={pid}  {cmd[:160]}")
    print(f"Gradle 守护进程 {len(dps)} 个：")
    for pid, cmd in dps:
        print(f"    PID={pid}  {cmd[:160]}")
    reason = keep_alive_reason()
    if reason:
        print(f"\n取证标记存在（退出清理会保留现场）：{reason}")
    clean = not ppl and not dps
    print(f"\nMT_CLEANUP_STATUS: {'CLEAN — 无残留' if clean else 'RESIDUAL — 仍有残留'}")
    return 0 if clean else 1


# ══ run ═══════════════════════════════════════════════════════════════════
def run_cleanup(*, versions: list[str] | None = None, force: bool = False,
                keep_daemon: bool = False, daemon_timeout: float = 90.0,
                quiet: bool = False) -> int:
    """收停本流程进程（可复用入口）。

    `mt_preflight` 也调它做「启动前兜底清理」——signal trap 在 MSYS/Git Bash 下并不可靠
    （实测 SIGTERM 直接终止 bash、不执行钩子），所以「避免进程泄漏」不能只靠退出钩子，
    必须有一道在**下次启动时**生效的兜底。
    """
    reason = keep_alive_reason()
    if reason and not force:
        print(f"MT_CLEANUP: SKIP — 失败取证标记存在，保留游戏现场（{reason}）")
        print("           取证完成后执行：bash scripts/test/mt.sh --phase stop --force")
        print("MT_CLEANUP_RESULT: SKIP")
        return 0

    killed = 0
    targets = list(versions) if versions else list(VERSIONS)
    for v in targets:
        from mt_env import kill_version  # 延迟导入，避免无谓的模块级耦合
        killed += kill_version(v, quiet=True)

    stopped_daemon = False
    if not keep_daemon:
        # 守护进程不归属单版本：只收停部分版本时不该动它，否则会破坏其它版本的构建热态
        if versions:
            if not quiet:
                print("MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程")
        else:
            gradlew = ROOT / "gradlew"
            if gradlew.is_file():
                rc, out = run_tolerant(
                    ["bash", str(gradlew), "--stop"], timeout=daemon_timeout
                )
                stopped_daemon = rc == 0
                if not stopped_daemon and not quiet:
                    print(f"MT_CLEANUP: 警告 — gradlew --stop 未成功（rc={rc}）"
                          f"{('：' + out.strip()[:160]) if out.strip() else ''}")

    if reason and force:
        try:
            KEEP_ALIVE.unlink()
            print("MT_CLEANUP: 已按 --force 清除失败取证标记")
        except OSError:
            pass

    left = pipeline_procs(targets)
    ldaemon = daemon_procs()
    if not quiet:
        print(f"MT_CLEANUP: 已收停 {killed} 个本流程进程"
              f"{'（范围：' + ', '.join(targets) + '）' if versions else ''}"
              f"{'，Gradle 守护已停止' if stopped_daemon else ''}")
        if keep_daemon:
            print(f"MT_CLEANUP: 按 --keep-daemon 保留 {len(ldaemon)} 个守护进程")
        if left:
            print(f"MT_CLEANUP: 警告 — 仍有 {len(left)} 个本流程进程未退出："
                  f"{[p for _, p, _ in left]}")
        if not keep_daemon and not versions and ldaemon:
            print(f"MT_CLEANUP: 提示 — 仍有 {len(ldaemon)} 个 Gradle 守护进程"
                  f"（可能属其它项目，未强杀）")

    # 机器可读结论（`--quiet` 也输出）：供 mt.sh 决定提示语，避免「跳过」被说成「完成」
    print(f"MT_CLEANUP_RESULT: {'RESIDUAL' if left else 'OK'}")
    return 0 if not left else 1


def cmd_run(args) -> int:
    return run_cleanup(versions=list(args.version) if args.version else None,
                       force=args.force, keep_daemon=args.keep_daemon,
                       daemon_timeout=args.daemon_timeout, quiet=args.quiet)


def main() -> int:
    ap = argparse.ArgumentParser(description="mt 流程退出清理")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("status")

    r = sub.add_parser("run")
    r.add_argument("--version", action="append", default=None,
                   help="只收停指定版本（可重复；缺省=全部）")
    r.add_argument("--force", action="store_true",
                   help="忽略失败取证标记，强制收停")
    r.add_argument("--keep-daemon", action="store_true",
                   help="保留 Gradle 守护进程（构建热态）")
    r.add_argument("--daemon-timeout", type=float, default=90.0)
    r.add_argument("--quiet", action="store_true")

    args = ap.parse_args()
    if args.cmd == "status":
        return cmd_status(args)
    return cmd_run(args)


if __name__ == "__main__":
    raise SystemExit(main())
