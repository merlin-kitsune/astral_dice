#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生产实例冒烟启动器（**通用**：NeoForge / Forge / Fabric 的 PCL 式版本目录都能起）。

## 为什么需要它

`scripts/test/fabric/ft_prod.ps1` 只按「纯 classpath」启动（Fabric 的 main class 就是普通
`KnotClient`）—— 对 **Forge 1.20.1** 不够：它的 main class 是
`cpw.mods.bootstraplauncher.BootstrapLauncher`，**必须**带上版本 JSON 里 `arguments.jvm` 的
`-p <模块路径>` / `--add-modules ALL-MODULE-PATH` / `-DignoreList` / `-DmergeModules` /
`-DlibraryDirectory`，否则启动即失败。

本脚本直接按**原版启动器语义**消费实例自己的 `<实例名>.json`（`inheritsFrom` 为空的独立实例）：
classpath = rules 过滤后的 `libraries` + 实例 jar；JVM/游戏参数 = `arguments.jvm` / `arguments.game`
（同样 rules 过滤 + `${...}` 变量替换）。⇒ 任何加载器的版本目录都能起。

## 判据（都是**机器行**，不靠肉眼）

  * `--expect <regex>`（可重复）：日志中必须命中的行（如本模组的 `AP_PARTY: ... back_ftb=on`）。
  * `--expect-count <regex>=<n>`：命中次数必须 ≥ n（如 `AP_PARTY: `=1，防「一次都没打」）。
  * 新增崩溃报告 / `--forbid <regex>` 命中 ⇒ FAIL。
  * 就绪基线：`Sound engine started`（原版到主菜单的标志行，与 ft_prod.ps1 同口径）。

⚠️ 判定基线 = **本次启动**：启动前删除 `logs/latest.log`，并快照 `crash-reports/` 的既有文件。

## 用法

    python scripts/test/prod_smoke.py \
        --instance "D:\\.minecraft\\versions\\1.20.1-Forge 模组测试" \
        --java "C:\\Program Files\\Zulu\\zulu-21\\bin\\java.exe" \
        --expect "AP_PARTY: sw_mc=.* back_ftb=on back_opac=on" \
        --timeout 300

    python scripts/test/prod_smoke.py --instance <dir> --java <exe> --dry-run   # 只构造 classpath

退出码：0 = 全部 `--expect` 命中且无崩溃/无 `--forbid`；1 = 断言失败；2 = 环境缺失。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time

READY_PATTERNS = [
    ("Sound engine started", "READY"),
]
FAIL_PATTERNS = [
    (re.compile(r"Loading errors encountered", re.I), "LOADING_ERRORS"),
    (re.compile(r"Mod loading has failed", re.I), "MOD_LOAD_FAILED"),
    (re.compile(r"Failed to start|Exception in thread \"main\"", re.I), "START_FAILED"),
    (re.compile(r"FATAL", re.I), "FATAL"),
]


def rules_allow(entry) -> bool:
    """照原版启动器语义：顺序应用 rules，最后一条匹配的生效；无 rules ⇒ 允许。"""
    rules = entry.get("rules")
    if not rules:
        return True
    allow = False
    for r in rules:
        ok = True
        osr = r.get("os") or {}
        if osr.get("name") and osr["name"] != "windows":
            ok = False
        if osr.get("arch") and osr["arch"] != "x86_64":
            ok = False
        if r.get("features"):
            # demo / quickPlay / 自定义分辨率等特性规则：本环境一律不满足
            ok = False
        if ok:
            allow = (r.get("action") == "allow")
    return allow


def values_of(entry):
    v = entry["value"] if isinstance(entry, dict) else entry
    return v if isinstance(v, list) else [v]


def maven_rel(name: str):
    p = name.split(":")
    if len(p) < 3:
        return None
    g, a, v = p[0], p[1], p[2]
    c = p[3] if len(p) >= 4 else None
    f = "%s-%s-%s.jar" % (a, v, c) if c else "%s-%s.jar" % (a, v)
    return os.path.join(*(g.split(".")), a, v, f)


def main() -> int:
    ap = argparse.ArgumentParser(description="生产实例冒烟（通用加载器）")
    ap.add_argument("--instance", required=True, help="版本目录（目录名即实例 id）")
    ap.add_argument("--java", required=True, help="java.exe 绝对路径")
    ap.add_argument("--mc-root", default=None, help=".minecraft 根（默认从 instance 上溯找 libraries/）")
    ap.add_argument("--timeout", type=int, default=300)
    ap.add_argument("--expect", action="append", default=[], help="日志必须命中的正则（可重复）")
    ap.add_argument("--expect-count", action="append", default=[],
                    help="形如 `<正则>=<n>`：命中次数须 ≥ n（可重复）")
    ap.add_argument("--forbid", action="append", default=[], help="日志不得命中的正则（可重复）")
    ap.add_argument("--jvm-arg", action="append", default=[])
    ap.add_argument("--game-arg", action="append", default=[])
    ap.add_argument("--log", default=None, help="把进程 stdout/stderr 也落到该文件（默认 instance/logs/prod_smoke_stdout.log）")
    ap.add_argument("--dry-run", action="store_true", help="只构造 classpath 与参数，不启动")
    args = ap.parse_args()

    inst = os.path.abspath(args.instance)
    if not os.path.isdir(inst):
        print("FAIL 实例目录不存在: %s" % inst)
        return 2
    inst_id = os.path.basename(inst)
    json_path = os.path.join(inst, inst_id + ".json")
    if not os.path.isfile(json_path):
        print("FAIL 找不到版本 JSON: %s" % json_path)
        return 2

    mc_root = args.mc_root
    if not mc_root:
        probe = inst
        for _ in range(4):
            probe = os.path.dirname(probe)
            if os.path.isdir(os.path.join(probe, "libraries")):
                mc_root = probe
                break
    if not mc_root:
        print("FAIL 无法推断 .minecraft 根（未找到 libraries/），请用 --mc-root 指定")
        return 2
    print("instance  : %s" % inst)
    print("mc root   : %s" % mc_root)

    with open(json_path, encoding="utf-8") as fh:
        j = json.load(fh)
    if j.get("inheritsFrom"):
        print("FAIL 该实例 JSON 带 inheritsFrom=%s —— 本脚本只支持自包含实例" % j["inheritsFrom"])
        return 2

    client_jar = os.path.join(inst, inst_id + ".jar")
    natives = os.path.join(inst, inst_id + "-natives")
    has_natives = os.path.isdir(natives)

    # ── ① classpath ─────────────────────────────────────────────────────────
    cp, missing = [], []
    libs = j.get("libraries", [])
    for lib in libs:
        if not rules_allow(lib):
            continue
        dl = (lib.get("downloads") or {}).get("artifact") or {}
        rel = dl.get("path") or maven_rel(lib.get("name", ""))
        if not rel:
            missing.append("<unresolvable> " + str(lib.get("name")))
            continue
        full = os.path.join(mc_root, "libraries", rel.replace("/", os.sep))
        (cp if os.path.isfile(full) else missing).append(full)
    cp.append(client_jar)
    cp_str = ";".join(cp)
    print("classpath : libraries=%d inCP=%d missing=%d natives=%s" % (len(libs), len(cp), len(missing), has_natives))
    for m in missing[:5]:
        print("   缺: %s" % m)

    # ── ② 变量替换表（原版启动器语义）────────────────────────────────────────
    var = {
        "natives_directory": natives,
        "classpath": cp_str,
        "classpath_separator": ";",
        "library_directory": os.path.join(mc_root, "libraries"),
        "version_name": inst_id,
        "game_directory": inst,
        "assets_root": os.path.join(mc_root, "assets"),
        "assets_index_name": str(j.get("assets", "")),
        "launcher_name": "prod-smoke",
        "launcher_version": "1",
        "auth_player_name": "ProdSmoke",
        "auth_uuid": "00000000000000000000000000000001",
        "auth_access_token": "0",
        "auth_xuid": "0",
        "clientid": "0",
        "user_type": "legacy",
        "version_type": "release",
        "resolution_width": "1280",
        "resolution_height": "720",
    }

    def expand(s: str) -> str:
        return re.sub(r"\$\{(\w+)\}", lambda m: var.get(m.group(1), m.group(0)), s)

    argv = [args.java, "-Xmx2G"]
    if has_natives:
        argv += ["-Djava.library.path=" + natives, "-Djna.tmpdir=" + natives,
                 "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + natives,
                 "-Dio.netty.native.workdir=" + natives]
    a = j.get("arguments") or {}
    for entry in a.get("jvm", []):
        if isinstance(entry, dict) and not rules_allow(entry):
            continue
        for v in values_of(entry):
            s = expand(v)
            # 版本 JSON 里已含 -cp ${classpath}；ours 已在 classpath 里，保留 JSON 原样即可
            argv.append(s)
    argv += args.jvm_arg
    argv.append(j["mainClass"])
    for entry in a.get("game", []):
        if isinstance(entry, dict) and not rules_allow(entry):
            continue
        for v in values_of(entry):
            argv.append(expand(v))
    argv += args.game_arg

    print("main      : %s" % j["mainClass"])
    print("argv 项数 : %d（cp_len=%d）" % (len(argv), len(cp_str)))
    if args.dry_run:
        print("DRY-RUN —— 不启动")
        print("argv(head) : %s" % " ".join(argv[:6]))
        return 0

    # ── ③ 启动前基线 ────────────────────────────────────────────────────────
    log_path = os.path.join(inst, "logs", "latest.log")
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    if os.path.isfile(log_path):
        try:
            os.remove(log_path)
        except OSError as exc:
            print("  注: latest.log 未能删除（可能被占用）：%s" % exc)
    crash_dir = os.path.join(inst, "crash-reports")
    pre = set(os.listdir(crash_dir)) if os.path.isdir(crash_dir) else set()

    out_log = args.log or os.path.join(inst, "logs", "prod_smoke_stdout.log")
    out_fh = open(out_log, "w", encoding="utf-8", errors="replace")
    proc = subprocess.Popen(argv, cwd=inst, stdout=out_fh, stderr=subprocess.STDOUT,
                            stdin=subprocess.DEVNULL)
    print("LAUNCH    : pid=%d stdout=%s" % (proc.pid, out_log))

    def read_parts():
        # 两个通道都要收：游戏自身 log4j 写 latest.log，我们另外也把 stdout/stderr 落了一份。
        # 任一为空的场景真实存在（有的加载器只往 stdout 打）=> 不能「先命中就 return」，
        # 读到空串后不再回退，判定会永远停在 TIMEOUT（假红）。
        out = []
        for p in (log_path, out_log):
            try:
                with open(p, encoding="utf-8", errors="replace") as fh:
                    out.append(fh.read())
            except OSError:
                continue
        return out

    def read_log() -> str:
        return chr(10).join(read_parts())
    verdict, ready = "TIMEOUT", False
    t0 = time.time()
    try:
        while time.time() - t0 < args.timeout:
            txt = read_log()
            if any(p in txt for p in ("Sound engine started",)):
                ready = True
                verdict = "READY"
                break
            hit = next(((n, p) for p, n in FAIL_PATTERNS if p.search(txt)), None)
            if hit:
                verdict = hit[0]
                break
            if os.path.isdir(crash_dir):
                new = set(os.listdir(crash_dir)) - pre
                if new:
                    verdict = "CRASH_REPORT:" + sorted(new)[-1]
                    break
            if proc.poll() is not None:
                verdict = "EXITED(%s)" % proc.returncode
                break
            time.sleep(2)
    finally:
        try:
            subprocess.run(["taskkill", "/F", "/T", "/PID", str(proc.pid)],
                           capture_output=True, text=True, timeout=60)
        except Exception:
            pass
        out_fh.close()

    chan = read_parts()
    txt = read_log()
    print("\nVERDICT   : %s  (%.0fs, ready=%s)" % (verdict, time.time() - t0, ready))
    if os.path.isdir(crash_dir):
        new = sorted(set(os.listdir(crash_dir)) - pre)
        print("新增崩溃报告: %s" % (new or "无 ✓"))

    ok = True
    print("\n---- --expect 断言 ----")
    for pat in args.expect:
        hit = re.search(pat, txt)
        print("  %s  %s" % ("PASS" if hit else "FAIL", pat))
        if hit:
            print("        %s" % hit.group(0)[:200])
        ok = ok and bool(hit)
    for spec in args.expect_count:
        pat, _, n = spec.rpartition("=")
        n = int(n)
        cnt = max([len(re.findall(pat, c)) for c in chan] or [0])
        good = cnt >= n
        print("  %s  命中 %d 次（>= %d）  %s" % ("PASS" if good else "FAIL", cnt, n, pat))
        ok = ok and good
    for pat in args.forbid:
        hit = re.search(pat, txt)
        print("  %s  不得命中: %s" % ("FAIL" if hit else "PASS", pat))
        if hit:
            print("        %s" % hit.group(0)[:200])
        ok = ok and not hit

    print("\n---- 相关日志行 ----")
    interesting = re.compile(r"AP_PARTY|AP_FAB_PARTY|starengine|FTB|ftb|opac|OPAC|Loading \d+ mods|Done \(|"
                             r"Sound engine|crash|Exception|FAIL|ERROR", re.I)
    shown = 0
    for line in txt.splitlines():
        if interesting.search(line):
            print("   " + line[:220])
            shown += 1
            if shown >= 60:
                print("   …（省略）")
                break
    if not ok:
        print("\n结论: FAIL —— 断言未全通过")
        return 1
    if verdict != "READY":
        print("\n结论: FAIL —— 未到主菜单（verdict=%s）" % verdict)
        return 1
    print("\n结论: PASS —— 到达主菜单且断言全通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
