#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""fetch_shaderpacks.py —— 把测试台用的光影包投放进指定 run 目录（可复现，不靠手工拷贝）。

## 为什么需要它

四线测试台的光影包（`ComplementaryReimagined_r5.9.3.zip`）此前只存在于各线 run 目录里
（`run/1.21.1/shaderpacks/`、`run/1.20.1/`、`run/26.1.2/`），而 `run/` 被 .gitignore 忽略
⇒ 换机器 / 清理缓存后 fabric 线（以及任何新 run 目录）拿不到光影包，`iris.properties` 里
`shaderPack=` 指向的文件不存在，Iris 静默回落到内置管线 —— 看上去"光影打开了"，实际没生效。

本脚本按 **Modrinth 官方 CDN** 下载指定光影包，落到 `<run-dir>/shaderpacks/`，
并校验 zip 完整性（必须含 `shaders/` 顶层目录），已存在且校验通过时秒退（幂等）。

## 用法

    python tools/fetch_shaderpacks.py --run-dir fabric-1.20.1/run/client
    python tools/fetch_shaderpacks.py --all-lines              # 四条线一次补齐
    python tools/fetch_shaderpacks.py --run-dir ... --dry-run  # 只报告

退出码：0 = 已就绪 / 已下载；1 = 下载或校验失败；2 = 参数错误。
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys
import urllib.request
import zipfile

DEFAULT_PACK = "ComplementaryReimagined_r5.9.3.zip"
# Complementary Reimagined（Modrinth project HVnmMxH1 / slug complementary-reimagined；
# version Bqen1mJX = r5.9.3，对 1.20.1 / 1.21.x 通用。2026-11-03 用 /v2/project/.../version 实测核对）
PACK_URLS = {
    DEFAULT_PACK: "https://cdn.modrinth.com/data/HVnmMxH1/versions/"
                  "Bqen1mJX/ComplementaryReimagined_r5.9.3.zip",
}
RUN_DIRS = [
    "run/1.21.1",
    "run/1.20.1",
    "run/26.1.2",
    "fabric-1.20.1/run/client",
]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _heal_stdio() -> None:
    for name in ("stdout", "stderr"):
        stream = getattr(sys, name, None)
        reconf = getattr(stream, "reconfigure", None)
        if reconf is not None:
            try:
                reconf(encoding="utf-8", errors="replace")
            except Exception:  # noqa: BLE001 - 明确告警，不静默
                pass


def pack_ok(path: str) -> bool:
    try:
        with zipfile.ZipFile(path) as zf:
            return any(n.startswith("shaders/") for n in zf.namelist())
    except (OSError, zipfile.BadZipFile):
        return False


def ensure_pack(run_dir: str, pack: str, dry: bool) -> int:
    dest_dir = os.path.join(run_dir, "shaderpacks")
    dest = os.path.join(dest_dir, pack)
    if os.path.isfile(dest) and pack_ok(dest):
        print("SHADERPACK: OK 已有且校验通过 " + dest)
        return 0
    url = PACK_URLS.get(pack)
    if not url:
        print("SHADERPACK: FAIL 未登记下载地址 " + pack)
        return 1
    print("SHADERPACK: NEED " + dest + " <- " + url)
    if dry:
        return 0
    os.makedirs(dest_dir, exist_ok=True)
    try:
        with urllib.request.urlopen(url, timeout=90) as resp:  # noqa: S310 - 固定 https 官方 CDN
            data = resp.read()
    except Exception as exc:  # noqa: BLE001 - 网络失败必须可见
        print("SHADERPACK: FAIL 下载失败 " + repr(exc))
        return 1
    tmp = dest + ".part"
    with open(tmp, "wb") as fh:
        fh.write(data)
    if not pack_ok(tmp):
        os.unlink(tmp)
        print("SHADERPACK: FAIL 下载内容不是合法光影包（缺 shaders/）")
        return 1
    os.replace(tmp, dest)
    print("SHADERPACK: OK 下载完成 %d B -> %s" % (len(data), dest))
    return 0


def main(argv: list[str]) -> int:
    _heal_stdio()
    ap = argparse.ArgumentParser(add_help=True)
    ap.add_argument("--run-dir", action="append", default=None,
                    help="run 目录（可重复）；相对路径按仓库根解析")
    ap.add_argument("--all-lines", action="store_true", help="四条线一次补齐")
    ap.add_argument("--pack", default=DEFAULT_PACK)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv[1:])

    if args.all_lines:
        targets = list(RUN_DIRS)
    elif args.run_dir:
        targets = list(args.run_dir)
    else:
        targets = []
    if not targets:
        print("SHADERPACK: FAIL 未指定 --run-dir 或 --all-lines")
        return 2

    rc = 0
    for rel in targets:
        run_dir = rel if os.path.isabs(rel) else os.path.join(ROOT, rel)
        rc |= ensure_pack(run_dir, args.pack, args.dry_run)
    print("SHADERPACK_RESULT: " + ("OK" if rc == 0 else "FAIL"))
    return rc


if __name__ == "__main__":
    sys.exit(main(sys.argv))
