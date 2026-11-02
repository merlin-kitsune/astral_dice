#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""资源完整性守门（verify_resource_integrity.ps1 的 Python 等价迁移）。

核对「物品注册表 → 模型 → 物品模型定义」三步链是否逐条闭合，逐线（版本线）独立检查：
  1. ModItems.java 里 registerItem("id", …) / registerSimpleBlockItem("id", …) / register("id", …) 的全部 id；
  2. jar 内 assets/astral_dice/models/item/<id>.json 覆盖全部 id（三线都要求，FATAL）；
  3. jar 内 assets/astral_dice/items/<id>.json 覆盖全部 id
     （仅 26.1.2：1.21.4+ 的物品模型定义，缺失同样表现为无模型，FATAL）；
  4. jar 内 assets/astral_dice/textures/item/<id>.png（方块物品允许 textures/block/<id>.png）
     覆盖全部 id（缺失只报 WARN，不判失败）。

只读，不修改任何工程文件。

输出与退出码完全对齐 ps1：
  - 机器行前缀：'='*92 分隔、'资源完整性 [ver]  <dir>'、'  jar   = …'、'  注册物品 id = N'、
    '  [OK]/[MISS]/[WARN] <label> …'、'RESULT: PASS / FAIL / ERROR（无法判定）'；
  - 退出码：0 = 全部通过；1 = 存在致命偏差（模型/物品模型定义缺失）；2 = 无法判定
    （未核到任何一条线，例如没有 jar 或路径约定变了 —— 反假绿守卫，绝不允许 vacuous PASS）。

与 ps1 的唯一差别（反假绿要求，2026-11-02 批次 B）：
  - `--root` 缺省值 '.' 的含义改为「脚本所在仓库根」而不是「当前目录」，因此**在错误目录里跑
    也会扫到真仓库**（ps1 的 Root='.' 依赖 cwd）；显式传 --root 时按传入值走。
    打印的路径前缀仍与 ps1 相同（缺省时就是 '.'）。

用法：
    python scripts/verify/verify_resource_integrity.py                 # 三线全跑
    python scripts/verify/verify_resource_integrity.py --version 26.1.2
    python scripts/verify/verify_resource_integrity.py --root /path/to/repo --version all
"""

import argparse
import fnmatch
import hashlib
import os
import re
import sys
import zipfile

MODID = "astral_dice"

LINE_DIRS = {
    "1.21.1": "neoforge-1.21.1",
    "1.20.1": "forge-1.20.1",
    "26.1.2": "neoforge-26.1.2",
}

SEP = "=" * 92

_OUT = []


def w(text=""):
    _OUT.append(text + "\n")


def emit():
    sys.stdout.buffer.write("".join(_OUT).encode("utf-8", "replace"))
    sys.stdout.buffer.flush()


def read_text(path):
    """等价 Get-Content -Raw / [IO.File]::ReadAllText：UTF-8（去 BOM），非法字节按 U+FFFD。"""
    try:
        with open(path, "rb") as fh:
            data = fh.read()
    except OSError:
        return None
    return data.decode("utf-8-sig", "replace")


def file_exists(path):
    return os.path.exists(path)


def sha256_hex(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest().upper()


def get_registered_ids(line_dir):
    """等价 Get-RegisteredIds：ModItems.java 里三种注册入口的字符串 id，去重升序。"""
    f = os.path.join(line_dir, "src", "main", "java", "com", "merlinkitsune",
                     MODID, "item", "ModItems.java")
    if not file_exists(f):
        return None
    txt = read_text(f)
    if txt is None:
        return None
    ids = re.findall(r'(?:registerSimpleBlockItem|registerItem|register)\(\s*"([a-z0-9_]+)"', txt)
    return sorted(set(ids))


def get_jar(line_dir):
    """等价 Get-Jar：build/libs 下最新的 astral_dice-*.jar（排除 sources / javadoc）。"""
    libs = os.path.join(line_dir, "build", "libs")
    if not os.path.isdir(libs):
        return None
    cand = []
    for name in sorted(os.listdir(libs)):
        if not fnmatch.fnmatch(name, "%s-*.jar" % MODID):
            continue
        if not os.path.isfile(os.path.join(libs, name)):
            continue
        if re.search(r"sources|javadoc", name, re.IGNORECASE):
            continue
        cand.append(os.path.join(libs, name))
    if not cand:
        return None
    cand.sort(key=lambda p: os.stat(p).st_mtime)
    return cand[-1]


def test_line(root_display, root_base, ver, state):
    dir_display = os.path.join(root_display, LINE_DIRS[ver])
    dir_base = os.path.join(root_base, LINE_DIRS[ver])
    w("")
    w(SEP)
    w("资源完整性 [%s]  %s" % (ver, dir_display))
    w(SEP)

    ids = get_registered_ids(dir_base)
    if not ids:
        w("  [FATAL] 找不到 ModItems.java（路径约定变了？）")
        state["fatal"] = 1
        return

    jar = get_jar(dir_base)
    if jar is None:
        w("  [FATAL] 无 jar（先构建）；注册 id 数 = %d" % len(ids))
        state["fatal"] = 1
        return

    state["checked"] += 1
    w("  jar   = %s" % os.path.basename(jar))
    w("  注册物品 id = %d" % len(ids))

    n = len(ids)
    checks = [
        {"pref": "assets/%s/models/item/" % MODID, "ext": ".json", "label": "物品模型",
         "fatal": True, "alts": []},
        {"pref": "assets/%s/textures/item/" % MODID, "ext": ".png", "label": "物品贴图",
         "fatal": False, "alts": ["assets/%s/textures/block/" % MODID]},
    ]
    if ver == "26.1.2":
        checks = [
            {"pref": "assets/%s/models/item/" % MODID, "ext": ".json", "label": "物品模型",
             "fatal": True, "alts": []},
            {"pref": "assets/%s/items/" % MODID, "ext": ".json", "label": "物品模型定义(1.21.4+)",
             "fatal": True, "alts": []},
            {"pref": "assets/%s/textures/item/" % MODID, "ext": ".png", "label": "物品贴图",
             "fatal": False, "alts": ["assets/%s/textures/block/" % MODID]},
        ]

    try:
        with zipfile.ZipFile(jar) as zf:
            names = set(zf.namelist())
    except (zipfile.BadZipFile, OSError):
        names = set()

    for c in checks:
        miss = []
        for cid in ids:
            if (c["pref"] + cid + c["ext"]) in names:
                continue
            alt_hit = False
            for alt in c["alts"]:
                if alt and (alt + cid + c["ext"]) in names:
                    alt_hit = True
                    break
            if not alt_hit:
                miss.append(cid)
        if not miss:
            w("  [OK]   %-24s 覆盖 %d/%d" % (c["label"], n, n))
        else:
            tag = "[MISS]" if c["fatal"] else "[WARN]"
            w("  %s %-24s 缺 %d/%d：" % (tag, c["label"], len(miss), n))
            for m in miss:
                w("           - %s" % m)
            w("           ↑ 新增物品时须同步：datagen 的 ItemModelProvider 登记"
              "（26.1.2 还需重跑两段式 datagen）")
            if c["fatal"]:
                state["fatal"] = 1


def repo_root_from_script():
    return os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def main(argv):
    parser = argparse.ArgumentParser(
        description="资源完整性守门（verify_resource_integrity.ps1 的 Python 等价实现）")
    parser.add_argument("--version", "-v", default="all",
                        choices=["1.21.1", "1.20.1", "26.1.2", "all"],
                        help="要检查的版本线（默认 all）")
    parser.add_argument("--root", "-r", default=".",
                        help="仓库根（缺省 '.' = 脚本所在仓库根，不依赖 cwd）")
    args = parser.parse_args(argv)

    root_arg = args.root
    if root_arg in ("", "."):
        root_base = repo_root_from_script()
        root_display = "."
    else:
        root_base = root_arg
        root_display = root_arg

    state = {"fatal": 0, "checked": 0}
    vers = ["1.21.1", "1.20.1", "26.1.2"] if args.version == "all" else [args.version]
    for v in vers:
        test_line(root_display, root_base, v, state)

    w("")
    if state["checked"] == 0:
        w("RESULT: ERROR（无法判定）—— 未核到任何一条线（缺 jar 或路径约定变化）；请先构建再跑")
        emit()
        return 2
    if state["fatal"] == 0:
        w("RESULT: PASS —— 模型链逐条闭合（紫黑格子成因②已排除）")
        emit()
        return 0
    w("RESULT: FAIL —— 存在无模型物品（实机会渲染成紫黑格子）")
    emit()
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
