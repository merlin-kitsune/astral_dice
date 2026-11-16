#!/usr/bin/env python3
"""mt_cleanup —— 遗留进程 / 存档清理（python 复原版）。

职责边界（2026-11 起）：
  * 只**发现并打印**本流程的 dev 客户端 / Gradle 任务进程 PID（判据 = lib/mt_compat 的
    test_pipeline_process），以及 Gradle 守护进程（走 gradlew --stop）；
  * 本脚本**不终止任何进程**：终止由 Agent 通过 windows MCP 的 Process(mode:"kill") 执行；
  * 绝不按镜像名批量终止 gradle。

失败取证标记（cases/.mt_keep_alive-<版本>，以及历史遗留的 cases/.mt_keep_alive）存在时：
    run 不打印待收停 PID，只给提示；--force 会清掉**所有**匹配的标记再继续。

机器行：
    MT_CLEANUP: PENDING pid=<...>（本脚本不终止进程；由 Agent 用 windows MCP 终止）
    MT_CLEANUP: 待收停 <n> 个本流程进程<范围><守护>
    MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程
    MT_CLEANUP: 按 --keep-daemon 保留 <n> 个守护进程
    MT_CLEANUP: 警告 — 仍有 <n> 个本流程进程未退出：[…]
    MT_CLEANUP: 提示 — 仍有 <n> 个 Gradle 守护进程（可能属其它项目，未强杀）
    MT_CLEANUP: SKIP — 失败取证标记存在，保留游戏现场（<原因>）
    MT_CLEANUP: 已清理旧存档 <路径> / MT_CLEANUP_SAVES: PURGED <n> | KEPT（未指定 --purge-saves）
    MT_CLEANUP_STATUS: CLEAN — 无残留 | RESIDUAL — 仍有残留
    MT_CLEANUP_RESULT: OK | RESIDUAL | SKIP        （即使 --quiet 也打印）

退出码：0 = 无残留 / SKIP；1 = 仍有残留；11 = 进程枚举不可用（BLOCKED，绝不当作「干净」）；
        2 = 参数错误（MT_ERROR: …）。

两处**有意差异**：
  * 进程枚举优先 lib/mt_compat.list_java_processes()（JDK jps；不可用时退回 ctypes
    Toolhelp32 + NtQueryInformationProcess）。枚举 API 本身不可用（非 Windows / API 失败）
    时记 BLOCKED(11)，绝不静默当作 0 个进程。
  * 删存档树先 rename 再删：Windows 的批量删除保护可能在删到一半时把进程杀掉（本仓实测过），
    rename 是原子且快的，rename 之后的 rmtree 失败只会留下 <路径>.purge-<时间戳>，并打 MT_WARN。
  * 增加 -h/--help（本仓约定每个 python 入口 --help 退出 0）。

本文件不调用任何外部脚本资产：gradlew --stop 走 cmd.exe/gradlew.bat 或 gradlew。
"""

import os
import shutil
import subprocess
import sys
import time

TEST_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(TEST_DIR, "lib"))

import mt_compat as mc  # noqa: E402

EXIT_ERROR = mc.MT_EXIT_ERROR
EXIT_FAIL = mc.MT_EXIT_FAIL
EXIT_BLOCKED = mc.MT_EXIT_BLOCKED
EXIT_PASS = mc.MT_EXIT_PASS

CASES_DIR = os.path.join(TEST_DIR, "cases")
KEEP_ALIVE_LEGACY = os.path.join(CASES_DIR, ".mt_keep_alive")
GRADLE_DAEMON_MARK = "GradleDaemon"
SETTLE_SECONDS = 20.0
SETTLE_STEP = 0.5


def usage():
    return chr(10).join([
        "用法: python scripts/test/mt_cleanup.py (status|run) [选项]",
        "",
        "遗留进程 / 存档清理。",
        "只收停本流程的 dev 客户端 / Gradle 任务进程，另用 gradlew --stop 收 Gradle 守护。",
        "",
        "子命令:",
        "  status   只报告：本流程进程 + Gradle 守护进程（不改任何状态）",
        "  run      收停本流程进程（默认连同 Gradle 守护一起 stop）",
        "",
        "选项:",
        "  --version <v>          可重复；只处理这些版本（不给 = 全部 " + " ".join(mc.VERSIONS) + "）",
        "  --force                强制收停：先清掉所有失败取证标记，再照常收停",
        "  --keep-daemon          保留 Gradle 守护进程（只收停本流程进程）",
        "  --purge-saves          收停后清理 run/<版本>/ 下的测试存档（客户端 + 服务端 + 其它含 level.dat 的目录）",
        "  --daemon-timeout <秒>  gradlew --stop 的超时（默认 90）",
        "  --quiet                 只打机器行（MT_CLEANUP_RESULT 照旧打印）",
        "  -h, --help              显示本帮助",
        "",
        "退出码: 0 = 无残留 / SKIP | 1 = 仍有残留 | 11 = 进程枚举不可用（BLOCKED） | 2 = 参数错误",
        "",
        "能力缺失: 枚举不了 Java 进程（非 Windows / 工具失效）时记 BLOCKED(11)，",
        "          既不能判 CLEAN，也不能假装收停成功。",
    ])


# ── 标记 ─────────────────────────────────────────────────────────────────
def keep_alive_paths(version=""):
    """Get-MtKeepAlivePaths：版本标记优先，历史无后缀标记对所有版本生效。"""
    paths = []
    if version:
        paths.append(os.path.join(CASES_DIR, ".mt_keep_alive-" + version))
    paths.append(KEEP_ALIVE_LEGACY)
    return paths


def keep_alive_reason(version=""):
    """Get-MtKeepAliveReason：返回 '<路径> ：<内容>'，无标记返回 None。"""
    for path in keep_alive_paths(version):
        if not os.path.isfile(path):
            continue
        try:
            with open(path, "rb") as fh:
                text = fh.read().decode("utf-8", "replace").strip()
        except OSError:
            return path + " ：（读取标记失败）"
        return path + " ：" + (text or "（未记录原因）")
    return None


def all_keep_alive_paths(versions):
    """--force 要清掉**所有**匹配标记：目标版本标记 + 历史标记。"""
    seen = []
    for version in versions:
        for path in keep_alive_paths(version):
            if path not in seen and os.path.isfile(path):
                seen.append(path)
    if not seen and os.path.isfile(KEEP_ALIVE_LEGACY):
        seen.append(KEEP_ALIVE_LEGACY)
    return seen


# ── 进程 ─────────────────────────────────────────────────────────────────
def enumerate_processes():
    """返回 (procs, error)：procs 为 [{'pid','command_line'}]，error 为枚举失败原因。"""
    try:
        return mc.list_java_processes(), ""
    except mc.ProcessEnumerationUnavailable as exc:
        return [], str(exc)
    except OSError as exc:
        return [], str(exc)


def pipeline_procs(versions, procs):
    """Get-MtPipelineProcs：用与收停同一套谓词判「本流程进程」，不是裸子串匹配。"""
    found = []
    for item in procs:
        pid = item.get("pid")
        cmd = str(item.get("command_line") or "")
        for version in versions:
            try:
                paths = mc.get_paths(version)
            except ValueError:
                continue
            if mc.test_pipeline_process(paths, pid, cmd):
                found.append((version, pid, cmd))
                break
    return found


def daemon_procs(procs):
    return [(item.get("pid"), str(item.get("command_line") or ""))
            for item in procs if GRADLE_DAEMON_MARK in str(item.get("command_line") or "")]


def _gradlew_stop(timeout):
    """gradlew --stop：Windows 优先 gradlew.bat（cmd /c），否则 gradlew。返回 (rc, text)。"""
    root = mc.ROOT
    bat = os.path.join(root, "gradlew.bat")
    sh = os.path.join(root, "gradlew")
    if mc.IS_WINDOWS and os.path.isfile(bat):
        cmd = ["cmd.exe", "/c", bat, "--stop"]
    elif os.path.isfile(sh):
        cmd = ["sh", sh, "--stop"]
    else:
        return 1, "未找到 gradlew / gradlew.bat"
    try:
        proc = subprocess.run(cmd, cwd=root, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, timeout=timeout)
    except (OSError, subprocess.SubprocessError) as exc:
        return 1, str(exc)
    return proc.returncode, proc.stdout.decode("utf-8", "replace")


def settle(targets):
    """收停后等进程表真的空下来（TerminateProcess 返回 ≠ 已离开进程表）。"""
    deadline = time.time() + SETTLE_SECONDS
    while time.time() < deadline:
        procs, error = enumerate_processes()
        if error:
            return
        if not pipeline_procs(targets, procs):
            return
        time.sleep(SETTLE_STEP)


# ── 删存档 ───────────────────────────────────────────────────────────────
def remove_tree(path):
    """先 rename 再删：规避 Windows 批量删除保护（删到一半把进程杀掉）。"""
    if not os.path.exists(path):
        return False
    stamp = time.strftime("%Y%m%d-%H%M%S")
    trash = path + ".purge-" + stamp
    try:
        os.rename(path, trash)
    except OSError:
        trash = path
    try:
        if os.path.isdir(trash):
            shutil.rmtree(trash)
        else:
            os.remove(trash)
    except OSError as exc:
        mc.write_mt_warn("MT_CLEANUP: 无法彻底删除 " + trash + " — " + str(exc))
        return True
    return True


def purge_saves(versions, quiet):
    removed = 0
    for version in versions:
        try:
            paths = mc.get_paths(version)
        except ValueError:
            continue
        targets = [paths.client_world, paths.server_world]
        saves_dir = paths.saves_dir
        if os.path.isdir(saves_dir):
            for name in sorted(os.listdir(saves_dir)):
                full = os.path.join(saves_dir, name)
                if not os.path.isdir(full) or name == mc.WORLD_NAME:
                    continue
                if os.path.isfile(os.path.join(full, "level.dat")):
                    targets.append(full)
        for target in targets:
            if remove_tree(target):
                removed += 1
                mc.write_mt_line("MT_CLEANUP: 已清理旧存档 " + target)
    if quiet and removed == 0:
        pass
    mc.write_mt_line("MT_CLEANUP_SAVES: PURGED " + str(removed) if removed
                     else "MT_CLEANUP_SAVES: PURGED 0")
    return removed


# ── 子命令 ───────────────────────────────────────────────────────────────
def cmd_status(versions):
    procs, error = enumerate_processes()
    if error:
        mc.write_mt_err_line("MT_CLEANUP: ERROR — 无法枚举 Java 进程：" + error)
        mc.write_mt_line("MT_CLEANUP_STATUS: BLOCKED — 枚举不可用，不能判定是否有残留")
        return EXIT_BLOCKED
    ppl = pipeline_procs(versions, procs)
    dps = daemon_procs(procs)
    mc.write_mt_line("本流程进程 " + str(len(ppl)) + " 个：")
    for version, pid, cmd in ppl:
        mc.write_mt_line("    [" + version + "] PID=" + str(pid) + "  " + cmd[:160])
    mc.write_mt_line("Gradle 守护进程 " + str(len(dps)) + " 个：")
    for pid, cmd in dps:
        mc.write_mt_line("    PID=" + str(pid) + "  " + cmd[:160])
    reason = ""
    for version in versions:
        reason = keep_alive_reason(version)
        if reason:
            break
    if reason:
        mc.write_mt_line("")
        mc.write_mt_line("取证标记存在（退出清理会保留现场）：" + reason)
    mc.write_mt_line("")
    if ppl or dps:
        mc.write_mt_line("MT_CLEANUP_STATUS: RESIDUAL — 仍有残留")
        return EXIT_FAIL
    mc.write_mt_line("MT_CLEANUP_STATUS: CLEAN — 无残留")
    return EXIT_PASS


def cmd_run(versions, force, keep_daemon, purge, daemon_timeout, quiet):
    reason = ""
    for version in versions:
        reason = keep_alive_reason(version)
        if reason:
            break
    if reason and not force:
        mc.write_mt_line("MT_CLEANUP: SKIP — 失败取证标记存在，保留游戏现场（" + reason + "）")
        mc.write_mt_line("           取证完成后执行：bash scripts/test/mt.sh --version "
                         + versions[0] + " --phase stop --force")
        mc.write_mt_line("MT_CLEANUP_RESULT: SKIP")
        return EXIT_PASS

    pending = []
    for version in versions:
        try:
            paths = mc.get_paths(version)
        except ValueError:
            continue
        for pid in mc.stop_version_processes(paths, quiet=True):
            if pid not in pending:
                pending.append(pid)

    daemon_stopped = False
    if not keep_daemon:
        if versions != list(mc.VERSIONS):
            mc.write_mt_line("MT_CLEANUP: 指定了 --version，保留 Gradle 守护进程")
        else:
            rc, text = _gradlew_stop(daemon_timeout)
            daemon_stopped = rc == 0
            if not daemon_stopped and not quiet:
                tail = text.strip()[:160]
                mc.write_mt_warn("MT_CLEANUP: 警告 — gradlew --stop 未成功（rc=" + str(rc) + "）"
                                 + ("：" + tail if tail else ""))

    if reason and force:
        cleared = 0
        for path in all_keep_alive_paths(versions):
            try:
                os.remove(path)
                cleared += 1
                mc.write_mt_line("MT_CLEANUP: 已按 --force 清除失败取证标记 " + path)
            except OSError as exc:
                mc.write_mt_warn("MT_CLEANUP: 无法清除失败取证标记 " + path + " — " + str(exc))
        if cleared == 0:
            mc.write_mt_warn("MT_CLEANUP: 按 --force 未能清除失败取证标记（见上方告警）")

    if purge:
        purge_saves(versions, quiet)
    elif not quiet:
        mc.write_mt_line("MT_CLEANUP_SAVES: KEPT（未指定 --purge-saves）")

    if pending:
        settle(versions)

    procs, error = enumerate_processes()
    if error:
        mc.write_mt_err_line("MT_CLEANUP: ERROR — 无法枚举 Java 进程：" + error)
        mc.write_mt_line("MT_CLEANUP_RESULT: RESIDUAL（枚举不可用，不能判定已收停干净）")
        return EXIT_BLOCKED
    left = pipeline_procs(versions, procs)
    ldaemon = daemon_procs(procs)

    if not quiet:
        scope = ""
        if versions != list(mc.VERSIONS):
            scope = "（范围：" + ", ".join(versions) + "）"
        daemon_txt = "，Gradle 守护已停止" if daemon_stopped else ""
        if pending:
            mc.write_mt_line("MT_CLEANUP: PENDING pid=" + ",".join(str(pid) for pid in pending)
                             + "（本脚本不终止进程；由 Agent 用 mcp__windows__Process(mode:\"kill\") 终止）")
        mc.write_mt_line("MT_CLEANUP: 待收停 " + str(len(pending)) + " 个本流程进程" + scope + daemon_txt)
        if keep_daemon:
            mc.write_mt_line("MT_CLEANUP: 按 --keep-daemon 保留 " + str(len(ldaemon)) + " 个守护进程")
        if left:
            mc.write_mt_line("MT_CLEANUP: 警告 — 仍有 " + str(len(left)) + " 个本流程进程未退出："
                             + "[" + ", ".join(str(pid) for _v, pid, _c in left) + "]")
        if not keep_daemon and versions == list(mc.VERSIONS) and ldaemon:
            mc.write_mt_line("MT_CLEANUP: 提示 — 仍有 " + str(len(ldaemon))
                             + " 个 Gradle 守护进程（可能属其它项目，未强杀）")
    if left:
        mc.write_mt_line("MT_CLEANUP_RESULT: RESIDUAL")
        return EXIT_FAIL
    mc.write_mt_line("MT_CLEANUP_RESULT: OK")
    return EXIT_PASS


def main(argv):
    mc.init_console()
    cmd = ""
    versions = []
    force = False
    keep_daemon = False
    purge = False
    quiet = False
    daemon_timeout = 90.0
    index = 0
    while index < len(argv):
        token = argv[index]
        key = token.lstrip("-").lower()
        if token.startswith("-") and key in ("h", "help"):
            mc.write_mt_line(usage())
            return EXIT_PASS
        if token.startswith("-") and key == "version":
            index += 1
            if index >= len(argv):
                mc.write_mt_error_line("缺少 --version 的值")
                return EXIT_ERROR
            value = argv[index]
            if not mc.assert_version(value):
                return EXIT_ERROR
            versions.append(value)
            index += 1
            continue
        if token.startswith("-") and key == "daemon-timeout":
            index += 1
            if index >= len(argv):
                mc.write_mt_error_line("缺少 --daemon-timeout 的值")
                return EXIT_ERROR
            raw = argv[index]
            try:
                daemon_timeout = float(raw)
            except ValueError:
                mc.write_mt_error_line("参数 --daemon-timeout 需要数字，收到 " + raw)
                return EXIT_ERROR
            index += 1
            continue
        if token.startswith("-") and key == "force":
            force = True
            index += 1
            continue
        if token.startswith("-") and key == "keep-daemon":
            keep_daemon = True
            index += 1
            continue
        if token.startswith("-") and key == "purge-saves":
            purge = True
            index += 1
            continue
        if token.startswith("-") and key == "quiet":
            quiet = True
            index += 1
            continue
        if token.startswith("-"):
            mc.write_mt_error_line("未知参数 " + token)
            return EXIT_ERROR
        if cmd:
            mc.write_mt_error_line("未知参数 " + token)
            return EXIT_ERROR
        cmd = token.lower()
        index += 1

    if cmd not in ("status", "run"):
        mc.write_mt_error_line("必须指定子命令 status 或 run")
        return EXIT_ERROR
    targets = versions or list(mc.VERSIONS)
    if cmd == "status":
        return cmd_status(targets)
    return cmd_run(targets, force, keep_daemon, purge, daemon_timeout, quiet)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
