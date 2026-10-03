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

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUBPROJECT = "fabric-1.20.1"
# Loom 把重映射后的依赖放在**仓库根**的 .gradle/loom-cache（不是子项目里），实测于 2026-10-01。
LOOM_REMAPPED = os.path.join(ROOT, ".gradle", "loom-cache", "remapped_mods", "remapped")
# 本工具投放的清单（放在 run 目录隔壁，便于 --clean 精确回收，不误删他人手工投放的 jar）
MANIFEST_NAME = ".astral_embedded_jars.json"


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


def iter_loom_jars():
    if not os.path.isdir(LOOM_REMAPPED):
        return
    for dirpath, _dirs, files in os.walk(LOOM_REMAPPED):
        for f in files:
            if f.endswith(".jar") and not f.endswith("-sources.jar"):
                yield os.path.join(dirpath, f)


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


def collect_embedded():
    """[(宿主 jar 路径, 包内路径, mod id, version, 原始字节)]"""
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
                    except Exception:
                        mid, ver = None, None
                    out.append((p, n, mid, ver, data))
        except Exception:
            continue
    return out


def side_mods(side: str) -> str:
    return os.path.join(ROOT, SUBPROJECT, "run", side, "mods")


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
    ap = argparse.ArgumentParser(description="补齐 Loom dev 环境被剥离的 JiJ 内嵌库")
    ap.add_argument("--apply", action="store_true", help="真正投放（默认只报告）")
    ap.add_argument("--side", choices=["server", "client", "both"], default="both")
    ap.add_argument("--clean", action="store_true", help="按清单回收此前投放的文件")
    args = ap.parse_args()

    cp_ids = collect_classpath_ids()
    embedded = collect_embedded()

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
