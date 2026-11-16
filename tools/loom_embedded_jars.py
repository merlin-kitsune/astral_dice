#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
loom_embedded_jars.py —— 补齐 **Loom dev 环境**里被剥离的 jar-in-jar 内嵌库。

## 为什么需要它（实测根因，2026-10-01）

Loom 在重映射 `modImplementation` / `modRuntimeOnly` / `modCompileOnly` 的**第三方 mod**
时，会把产物 `fabric.mod.json` 里的 `jars` 声明**整条删掉**，而 `META-INF/jars/*.jar`
文件本体仍留在包里（且**未经重映射**，仍是 intermediary 名称）。

* 直接证据：`puzzles-lib-...jar` 原始产物 `jars=[{file: META-INF/jars/puzzlesaccessapi-fabric-20.1.1.jar}]`；
  经 Loom 重映射后（`.gradle/loom-cache/remapped_mods/...`）变成 `jars=None`，而
  `META-INF/jars/puzzlesaccessapi-fabric-20.1.1.jar` 还在（内含 `class_xxxx` → 未重映射）。
* 不是缓存陈旧：删掉整棵 Loom 重映射缓存后重新生成，结果**逐字相同**。
* 影响面：fabric dev 侧凡依赖内嵌库的 mod 全部起不来 ——
  实测命中 `puzzlesaccessapi`（Puzzles Lib，Loader 直接 HARD_DEP 拒绝）、
  `endec`/`endec_gson`/`endec_netty`（Accessories，`AccessoriesFabric.<clinit>` NoClassDefFoundError）、
  `fiber`（Patchouli，`FabricModInitializer` NoClassDefFoundError）、
  `cloth-basic-math`（Cloth Config）、`mixinextras`（KubeJS）。

## 为什么投放进 `run/<side>/mods/` 就能生效

Loom 的 dev 启动配置写了 `-Dfabric.remapClasspathFile=<subproject>/.gradle/loom-cache/remapClasspath.txt`
（见 `fabric-1.20.1/.gradle/loom-cache/launch.cfg`）⇒ **Fabric Loader 在 dev 模式会对
`mods/` 目录里的 jar 做运行时重映射**（intermediary → named）。故把**未重映射的原样内嵌 jar**
放进去即可，无需自行重映射。
⚠️ 该机制与「testbench 把第三方 jar 丢进 run 目录」的既有手法同源（见
`scripts/test/fabric/README.md` §7.2.5 的队伍后端 A/B/C/D 验证）。

## 去重规则（关键）

**只投放 classpath 上不存在的 mod id。** 内嵌 jar 里有一部分（Fabric API 子模块、
`cardinal-components-base/entity`）在 Loom classpath 上**已有独立条目**（版本还更新），
重复投放会被 Fabric Loader 判为 duplicate mod 而**拒绝启动**。故先枚举
`<loom-dir>/remapped_mods` 下全部 jar 的 `fabric.mod.json` id 作为「已在场」集合，
内嵌 jar 的 id 命中该集合即跳过。

## 用法

    python tools/loom_embedded_jars.py                       # 只报告（dry-run）
    python tools/loom_embedded_jars.py --apply               # 投放进 run/server/mods 与 run/client/mods
    python tools/loom_embedded_jars.py --apply --side server # 只投一侧
    python tools/loom_embedded_jars.py --apply --clean       # 先清掉本工具此前投放的（按清单文件）

退出码：0 = 无待补项 / 已补齐；1 = 报告出待补项且未 --apply。
"""

from __future__ import annotations

import argparse
import io
import json
import os
import sys
import zipfile
# --- stdout/stderr 编码自愈（统一口径：python 侧强制 UTF-8；勿删）---
# 背景：Windows 默认代码页 936 下 python 子进程的 sys.stdout.encoding 为 gbk，
# 本脚本打印的 ⇒/⚠/✅/↔ 等符号 GBK 不可表示 ⇒ 直接 UnicodeEncodeError
# 退出码 1（“门会红但红的原因是崩溃”）。此块使脚本不依赖调用者环境与宿主编码。
# ⚠ 块内引用 sys ⇒ 必须位于**所有 import 之后**；也**不得**加 except-pass（会静默失效）。
def _astral_heal_stdio() -> None:  # pragma: no cover - 环境适配
    for _stream_name in ("stdout", "stderr"):
        _stream = getattr(sys, _stream_name, None)
        _reconf = getattr(_stream, "reconfigure", None)
        if _reconf is None:
            continue
        try:
            _reconf(encoding="utf-8", errors="replace")
        except Exception as _exc:  # noqa: BLE001 - 明确告警，绝不静默
            sys.stderr.write(
                "[astral-gate] WARN: %s.reconfigure(utf-8) failed: %r\n"
                % (_stream_name, _exc)
            )


_astral_heal_stdio()

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUBPROJECT = "fabric-1.20.1"
# 运行目录根：默认 "run"（= <project>/run/<side>/mods），由 --run-root 覆盖。
RUN_ROOT = "run"
# Loom 把重映射后的依赖放在**仓库根**的 .gradle/loom-cache（不是子项目里），实测于 2026-10-01。
LOOM_REMAPPED = os.path.join(ROOT, ".gradle", "loom-cache", "remapped_mods", "remapped")
# 本工具投放的清单（放在 run 目录隔壁，便于 --clean 精确回收，不误删他人手工投放的 jar）
MANIFEST_NAME = ".astral_embedded_jars.json"
# 非 None 时只扫这些宿主 jar（由 --hosts-from 指定）
HOSTS = None


def fmj_id(zf: zipfile.ZipFile):
    """读 fabric.mod.json 的 id/version；非 mod 返回 (None, None)。"""
    try:
        data = zf.read("fabric.mod.json")
    except KeyError:
        return None, None
    try:
        j = json.loads(data.decode("utf-8"))
    except Exception:
        return None, None
    return j.get("id"), j.get("version")


def fmj_provides(zf: zipfile.ZipFile):
    """读 fabric.mod.json 的 provides（内嵌件常以 provides 冒充真实 mod，会造成 UNIQUE_ID 冲突）。"""
    try:
        j = json.loads(zf.read("fabric.mod.json").decode("utf-8"))
    except Exception:
        return ()
    p = j.get("provides") or ()
    return tuple(x for x in p if isinstance(x, str))


def iter_loom_jars():
    if not os.path.isdir(LOOM_REMAPPED):
        return
    root_nc = os.path.normcase(os.path.abspath(LOOM_REMAPPED))
    for dirpath, _dirs, files in os.walk(LOOM_REMAPPED):
        for f in files:
            if not (f.endswith(".jar") and not f.endswith("-sources.jar")):
                continue
            p = os.path.join(dirpath, f)
            if HOSTS is not None:
                # 只认「本线运行期真实 classpath」上的宿主 jar（两条 fabric 线共用
                # 一棵 loom-cache，不限定就会把别的线的宿主件一起扫进来）。
                if os.path.normcase(os.path.abspath(p)) not in HOSTS:
                    continue
            yield p


def load_hosts_from_argfile(path: str):
    """从 Loom 生成的 run 参数文件里取 -classpath，返回归一化后的 jar 路径集合。"""
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        text = f.read()
    lines = text.splitlines()
    cp = ""
    for i, ln in enumerate(lines):
        if ln.strip() == "-classpath" and i + 1 < len(lines):
            cp = lines[i + 1]
            break
    if not cp:
        raise SystemExit("HOSTS_FROM: 未在 %s 里找到 -classpath" % path)
    entries = cp.split(";") if ";" in cp else cp.split(os.pathsep)
    return {os.path.normcase(os.path.abspath(e)) for e in entries
            if e.strip().lower().endswith(".jar")}


def collect_classpath_ids():
    """Loom classpath 上已有的 mod id（= 重复投放的判据）。"""
    ids = {}
    for p in iter_loom_jars():
        try:
            with zipfile.ZipFile(p) as z:
                mid, ver = fmj_id(z)
        except Exception:
            continue
        if mid:
            ids.setdefault(mid, ver)
    return ids


def collect_embedded(cp_ids=None):
    """[(宿主 jar 路径, 包内路径, mod id, version, 原始字节)]

    跳过两类内嵌件：① 自身 id 已在 classpath 上（重复投放会撞 UNIQUE_ID）；
    ② provides 里有 id 已在 classpath 上（典型：accessories 内嵌 owo-sentinel 声明
    provides=[owo, owo-lib]，而 owo-lib 本体就在 classpath ⇒ Fabric Loader 0.20.1
    直接判 UNIQUE_ID owo 并拒绝启动）。这两种情形由平台侧已经提供的件接管。"""
    cp_ids = cp_ids or {}
    out = []
    for p in iter_loom_jars():
        try:
            with zipfile.ZipFile(p) as z:
                nested = [n for n in z.namelist()
                          if n.startswith("META-INF/jars/") and n.endswith(".jar")]
                if not nested:
                    continue
                for n in nested:
                    data = z.read(n)
                    try:
                        with zipfile.ZipFile(io.BytesIO(data)) as zz:
                            mid, ver = fmj_id(zz)
                            provs = fmj_provides(zz)
                    except Exception:
                        mid, ver, provs = None, None, ()
                    if mid in cp_ids or any(x in cp_ids for x in provs):
                        continue
                    out.append((p, n, mid, ver, data))
        except Exception:
            continue
    return out


def side_mods(side: str) -> str:
    return os.path.join(ROOT, SUBPROJECT, RUN_ROOT, side, "mods")


def load_manifest(mods_dir: str):
    p = os.path.join(mods_dir, MANIFEST_NAME)
    if os.path.isfile(p):
        try:
            with open(p, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}


def save_manifest(mods_dir: str, m: dict):
    with open(os.path.join(mods_dir, MANIFEST_NAME), "w", encoding="utf-8") as f:
        json.dump(m, f, ensure_ascii=False, indent=1)


def main() -> int:
    global SUBPROJECT, RUN_ROOT, HOSTS
    ap = argparse.ArgumentParser(description="补齐 Loom dev 环境被剥离的 JiJ 内嵌库")
    ap.add_argument("--apply", action="store_true", help="真正投放（默认只报告）")
    ap.add_argument("--side", choices=["server", "client", "both", "datagen"], default="both")
    ap.add_argument("--project", default=SUBPROJECT,
                    help="子项目目录名（默认 fabric-1.20.1）；如 fabric-1.21.1")
    ap.add_argument("--run-root", dest="run_root", default="run",
                    help="运行目录根（默认 run）")
    ap.add_argument("--clean", action="store_true", help="按清单回收此前投放的文件")
    ap.add_argument("--hosts-from", dest="hosts_from", default=None,
                    help="只从该 Loom run 参数文件（如 build/loom-cache/argFiles/runDatagen）"
                         "列出的宿主 jar 采集内嵌库；多线共用 cache 时必须指定")
    args = ap.parse_args()

    SUBPROJECT = args.project
    RUN_ROOT = args.run_root
    if args.hosts_from:
        HOSTS = load_hosts_from_argfile(args.hosts_from)
        print("AP_LOOM_HOSTS: from=%s jars=%d" % (args.hosts_from, len(HOSTS)))

    cp_ids = collect_classpath_ids()
    embedded = collect_embedded(cp_ids)

    need = []
    skipped_dup = []
    for host, inner, mid, ver, data in embedded:
        if mid is None:
            skipped_dup.append((inner, "<not-a-mod>", None))
            continue
        if mid in cp_ids:
            skipped_dup.append((inner, mid, cp_ids[mid]))
            continue
        need.append((host, inner, mid, ver, data))

    # 同 id 去重（不同宿主可能带同一内嵌库）
    by_id = {}
    for host, inner, mid, ver, data in need:
        by_id.setdefault(mid, (host, inner, mid, ver, data))
    need = list(by_id.values())
    need.sort(key=lambda r: r[2])

    print("AP_LOOM_EMBEDDED: classpath_ids=%d embedded_jars=%d need=%d skip_dup=%d"
          % (len(cp_ids), len(embedded), len(need), len(skipped_dup)))
    for inner, mid, cv in skipped_dup:
        print("   SKIP  %-46s id=%s (classpath=%s)" % (os.path.basename(inner), mid, cv))
    for host, inner, mid, ver, data in need:
        print("   NEED  %-46s id=%-34s ver=%-12s <- %s"
              % (os.path.basename(inner), mid, ver, os.path.basename(host)))

    sides = ["server", "client"] if args.side == "both" else [args.side]

    if args.clean:
        for s in sides:
            d = side_mods(s)
            m = load_manifest(d)
            removed = 0
            for name in list(m.get("files", [])):
                p = os.path.join(d, name)
                if os.path.isfile(p):
                    os.remove(p)
                    removed += 1
            if os.path.isfile(os.path.join(d, MANIFEST_NAME)):
                os.remove(os.path.join(d, MANIFEST_NAME))
            print("   CLEAN side=%s removed=%d" % (s, removed))
        return 0

    if not need:
        print("   => 无待补项（classpath 已自洽）")
        return 0

    if not args.apply:
        print("   => 待补 %d 项；加 --apply 投放（默认 dry-run）" % len(need))
        return 1

    for s in sides:
        d = side_mods(s)
        os.makedirs(d, exist_ok=True)
        m = load_manifest(d)
        files = set(m.get("files", []))
        written = 0
        for host, inner, mid, ver, data in need:
            out_name = "astral_embedded-%s.jar" % mid.replace(":", "_")
            with open(os.path.join(d, out_name), "wb") as f:
                f.write(data)
            files.add(out_name)
            written += 1
        m["files"] = sorted(files)
        m["note"] = ("由 tools/loom_embedded_jars.py 投放：Loom 重映射第三方 mod 时剥离了 "
                     "fabric.mod.json 的 jars 声明，dev 环境需把内嵌库放到 mods/ 才能被 "
                     "Fabric Loader 的运行时重映射接住。删除本清单中任一文件会重新触发启动失败。")
        save_manifest(d, m)
        print("   APPLY side=%-6s dir=%s written=%d" % (s, d, written))
    return 0


if __name__ == "__main__":
    sys.exit(main())
