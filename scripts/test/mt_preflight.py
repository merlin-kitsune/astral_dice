#!/usr/bin/env python3
"""mt_preflight — 前置检查（阶段 P）。

在启动任何游戏进程之前把「环境不满足」与「功能缺陷」分开：
本模块失败一律 exit 10（PREFLIGHT），绝不触达游戏，避免环境问题被误判为测试失败。

用法:
  mt_preflight.py --version 1.21.1        # 单版本检查
  mt_preflight.py --all                   # 两个版本都检查
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
from mt_paths import CONF, VERSIONS, Paths  # noqa: E402
from mt_ps import java_procs, run_ps, run_tolerant  # noqa: E402

EXIT_PREFLIGHT = 10
REQUIRED_BRANCH = "multi-1.20.1-1.21.1"
TEST_DIR = Path(__file__).resolve().parent
KEEP_ALIVE = TEST_DIR / "cases" / ".mt_keep_alive"


def _run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    # encoding/errors 显式指定：中文环境下外部命令输出可能含非 UTF-8 字节，
    # 默认解码器会抛 UnicodeDecodeError 并让调用方「静默拿到空输出」。
    return subprocess.run(cmd, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", **kw)


def check_branch(root: Path) -> tuple[bool, str]:
    """当前分支必须是 multi-1.20.1-1.21.1。"""
    r = _run(["git", "-C", str(root), "rev-parse", "--abbrev-ref", "HEAD"])
    if r.returncode != 0:
        return False, f"无法读取分支：{r.stderr.strip()}"
    branch = r.stdout.strip()
    if branch != REQUIRED_BRANCH:
        return False, f"当前分支 {branch} ≠ {REQUIRED_BRANCH}"
    return True, branch


def check_ime() -> tuple[bool, str]:
    """输入语言前置。

    硬要求只有一条：**en-US 布局在本机可用**（已安装且能加载）。因为注入按美式扫描码
    表投递，且注入前会把**目标窗口所在线程**自动切到 en-US（见 mt_ime.py）。

    系统当前输入法**不再要求**是 en-US：那是旧的真实按键注入时代留下的人肉前置，
    现已由程序化切换替代 —— 用户开着中文输入法也能跑，不必手动切换。
    """
    if sys.platform != "win32":
        return True, "非 Windows（注入降级 MCP 通道，不校验输入语言）"
    try:
        import mt_ime
    except ImportError as exc:  # pragma: no cover
        return False, f"无法加载 mt_ime：{exc}"

    cur = mt_ime.langid_of_thread(0)
    if not mt_ime.en_us_available():
        return False, ("en-US 键盘布局不可用 —— 请在系统设置安装「英语(美国)」键盘"
                       "（注入按美式扫描码投递，并在注入前自动切换目标窗口）")
    return True, (f"en-US 布局可用（KLID {mt_ime.KLID_EN_US}）；当前系统输入法为 "
                  f"{mt_ime.describe(cur)} —— 无需手动切换，注入前自动切目标窗口")


def _own_process_markers() -> tuple[str, ...]:
    """本流程进程的命令行特征（判据与 mt_env.kill_version 共用同一来源）。"""
    marks: list[str] = []
    for v in VERSIONS:
        marks += list(Paths(v).process_markers)
    return tuple(marks)


def check_leftover_game() -> tuple[bool, str]:
    """遗留进程检查：本流程自己的进程**先自动清理**，清不掉才硬阻塞。

    刻意区分两类进程——早前的实现把两者混为一谈，会在用户只是开着自己的整合包
    客户端时误报阻塞：

      本流程遗留（dev 客户端 / Gradle 任务）→ **自动收停**（见下），
        否则独占 runClient 的前提不成立，且注入可能落到旧进程上；
      其它 Minecraft 客户端（用户自己开的整合包）→ 仅提示，不阻塞：注入通道已按
        版本锁定窗口（见 mt_inject._find_window），不会投错；只提示显存/焦点占用。

    为什么在这里兜底：退出钩子（`mt.sh` 的 EXIT/INT/TERM trap）在 MSYS/Git Bash 下
    **并不可靠**——实测 SIGTERM 会让 bash 直接终止、不执行钩子（Windows 上还有
    任务管理器强杀、控制台直接关窗等路径根本不经过 trap）。只靠 trap 就等于把
    「进程泄漏」交给运气。因此再加一道**下次启动时生效**的兜底：检出的本流程遗留
    直接收停，让泄漏最多影响一次运行的干净度，而不会累积。

    唯一例外：`.mt_keep_alive` 取证标记存在时**不自动清理**（那是失败时故意留的
    现场），改为 FAIL 并提示用 `--phase stop --force` 显式释放。
    """
    if sys.platform != "win32":
        _, out = run_tolerant(["pgrep", "-af", "gradle|minecraft"])
        if out.strip():
            return False, f"存在遗留进程：{out.strip()[:200]}"
        return True, "无遗留进程"

    marks = _own_process_markers()

    def scan() -> tuple[list[int], list[int]]:
        ours, others = [], []
        for pid, cmd in java_procs():
            if any(m and m in cmd for m in marks):
                ours.append(pid)
            elif "minecraft" in cmd.lower():
                others.append(pid)
        return ours, others

    ours, others = scan()

    _, gout = run_ps("Get-Process -Name gradle -ErrorAction SilentlyContinue | "
                     "ForEach-Object { $_.Id }", timeout=30)
    gradle_pids = [int(x) for x in gout.split() if x.strip().isdigit()]

    if ours:
        if KEEP_ALIVE.is_file():
            try:
                why = KEEP_ALIVE.read_text(encoding="utf-8").strip() or "（未记录原因）"
            except OSError:
                why = "（读取标记失败）"
            return False, (f"本流程遗留 PID={','.join(str(x) for x in ours)}，"
                           f"但存在失败取证标记（{why}）——按设计保留现场；"
                           "取证完成后执行 bash scripts/test/mt.sh --phase stop --force")
        stale = list(ours)
        import mt_cleanup  # 延迟导入：正常路径（无遗留）不需要付这个开销
        print(f"  [ .. ] 遗留进程: 检出 {len(stale)} 个本流程遗留进程"
              f"（PID={','.join(str(x) for x in stale)}），自动收停中…")
        mt_cleanup.run_cleanup(quiet=True)
        ours, others = scan()
        if ours:
            return False, (f"自动收停后仍有本流程遗留 PID={','.join(str(x) for x in ours)}，"
                           "请手工执行 bash scripts/test/mt.sh --phase stop 并检查权限")
        return True, (f"已自动收停 {len(stale)} 个本流程遗留进程"
                      f"（PID={','.join(str(x) for x in stale)}）——"
                      "退出钩子失效时的兜底，不影响本次结论")

    if gradle_pids:
        return False, (f"存在 gradle 包装器进程 PID={','.join(str(x) for x in gradle_pids)}，"
                       "请先 mt.sh --phase stop")

    if others:
        return True, (f"无本流程遗留；另有 {len(others)} 个其它 Minecraft 客户端在运行"
                      f"（PID={','.join(str(x) for x in others)}）——不阻塞，仅占用显存")
    return True, "无遗留进程"


def check_mcp_binary() -> tuple[bool, str]:
    """本地 MCP 二进制存在（连接状态由会话内校验，不在本脚本职责内）。"""
    exe = CONF.get("MT_MCP_BIN", "")
    if not exe:
        return True, "未配置 MT_MCP_BIN（跳过）"
    p = Path(exe)
    if not p.is_file():
        return False, f"MCP 二进制缺失：{p}"
    return True, p.name


def check_compat_stack(version: str) -> tuple[bool, str]:
    """兼容模组来源可用性。

    1.21.1：Sodium/Iris/ModernFix 从整合包复制到 dev run，因此必须存在源目录。
    1.20.1：dev run 不使用渲染模组（refmap 在 mojmap 下无法解析），只校验生产环境就绪。
    """
    p = Paths(version)
    if not p.pack_mods_dir.is_dir():
        if version == "1.20.1":
            return True, f"生产环境目录不存在（仅影响兼容性人工验证）：{p.pack_mods_dir}"
        return False, f"整合包 mods 目录不存在：{p.pack_mods_dir}"
    if version == "1.21.1":
        found = {pat: False for pat in ("sodium", "iris", "modernfix")}
        for f in p.pack_mods_dir.glob("*.jar"):
            low = f.name.lower()
            for pat in found:
                if pat in low:
                    found[pat] = True
        missing = [k for k, v in found.items() if not v]
        if missing:
            return False, f"整合包缺少兼容模组：{', '.join(missing)}"
    return True, str(p.pack_mods_dir)


def check_writable(version: str) -> tuple[bool, str]:
    """run 目录可写（世界重建与日志写入的前提）。"""
    p = Paths(version)
    try:
        p.run_dir.mkdir(parents=True, exist_ok=True)
        probe = p.run_dir / ".mt_write_probe"
        probe.write_text("ok", encoding="utf-8")
        probe.unlink()
    except OSError as exc:
        return False, f"run 目录不可写：{exc}"
    return True, str(p.run_dir)


def run_all(versions: list[str]) -> int:
    checks: list[tuple[str, tuple[bool, str]]] = [
        ("分支", check_branch(Paths(versions[0]).root)),
        ("输入法", check_ime()),
        ("遗留进程", check_leftover_game()),
        ("MCP 二进制", check_mcp_binary()),
        ("Gradle 包装器", (
            Paths(versions[0]).root.joinpath("gradlew").is_file(),
            "gradlew 存在" if Paths(versions[0]).root.joinpath("gradlew").is_file() else "缺少 gradlew",
        )),
    ]
    for v in versions:
        checks.append((f"兼容栈 {v}", check_compat_stack(v)))
        checks.append((f"run 可写 {v}", check_writable(v)))

    failed: list[str] = []
    for name, (ok, detail) in checks:
        mark = "OK  " if ok else "FAIL"
        print(f"  [{mark}] {name}: {detail}")
        if not ok:
            failed.append(f"{name}（{detail}）")

    if failed:
        print(f"\nMT_PREFLIGHT: FAIL — {len(failed)} 项不满足：{'; '.join(failed)}", file=sys.stderr)
        print("修复后重跑本阶段；前置失败不触达游戏，不作为功能缺陷。", file=sys.stderr)
        return EXIT_PREFLIGHT
    print(f"\nMT_PREFLIGHT: OK — {len(checks)} 项全部满足（{', '.join(versions)}）")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="mt 前置检查")
    ap.add_argument("--version", choices=list(VERSIONS))
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--json", action="store_true", help="以 JSON 输出（供上层消费）")
    args = ap.parse_args()

    versions = list(VERSIONS) if args.all or not args.version else [args.version]
    if args.json:
        # JSON 模式只做结构化汇总，供 mt.sh 汇总报告
        return run_all(versions)
    return run_all(versions)


if __name__ == "__main__":
    raise SystemExit(main())
