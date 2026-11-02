#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""立牌注册 / 标签一致性守门（verify_sign_registration.ps1 的 Python 等价迁移，2026-10-21 起）。

1.21.1 起立牌要能装备，必须同时出现在三处：
  - item/ModItems.java 的 registerItem("..._sign")
  - data/astral_dice/tags/item(s)/signs.json
  - data/curios/tags/item(s)/stand.json（blank_sign 按设计不入此表）

检查四条线：neoforge-1.21.1(item) / forge-1.20.1(items) / neoforge-26.1.2(item) / fabric-1.20.1(items)。

输出与退出码完全对齐 ps1：
  'SIGN_REG: OK|FAIL [proj] registered=N signs=M stand=K'
  '   缺 signs.json : a, b'  /  '   缺 stand.json : …'  /  '   标签内未注册: …'
  'SIGN_REG: UNDECIDABLE [proj] 缺少 <path>'  /  'SIGN_REG: UNDECIDABLE（检查面缺失）'
  'SIGN_REG: FAIL（N 条线不一致）'  /  'SIGN_REG: ALL OK'
  退出码：0 = 一致；1 = 有缺失/多余；2 = 无法判定（检查面缺失）。

与 ps1 的两点差别（都是反假绿要求，2026-11-02 批次 B）：
  1. 仓库根仍按「脚本位置上溯两级」求（ps1 同口径），但 ps1 找不到 settings.gradle 时回落到
     **当前目录**；本实现显式提供 --root 覆盖，且回落分支保留 ps1 语义仅作为最后手段。
  2. 追加守卫：四条线累计 **registered 数为 0** 时判『无法判定』(退出码 2)，
     复用 ps1 已有的 'SIGN_REG: UNDECIDABLE（检查面缺失）' 机器行 —— 不新增任何状态词/机器行，
     只堵住「注册正则一条都没扫到却报 ALL OK」的 vacuous PASS。

用法：
    python scripts/verify/verify_sign_registration.py
    python scripts/verify/verify_sign_registration.py --root /path/to/repo
"""

import argparse
import json
import os
import re
import sys

LINES = [
    {"key": "neoforge-1.21.1", "tags": "item"},
    {"key": "forge-1.20.1", "tags": "items"},
    {"key": "neoforge-26.1.2", "tags": "item"},
    {"key": "fabric-1.20.1", "tags": "items"},
]
EXEMPT_STAND = ("blank_sign",)   # 空白立牌是合成基底，不进可装备表

_OUT = []


def w(text=""):
    _OUT.append(text + "\n")


def emit():
    sys.stdout.buffer.write("".join(_OUT).encode("utf-8", "replace"))
    sys.stdout.buffer.flush()


def read_text(path):
    try:
        with open(path, "rb") as fh:
            data = fh.read()
    except OSError:
        return None
    return data.decode("utf-8-sig", "replace")


def read_json(path):
    txt = read_text(path)
    if txt is None:
        raise OSError(path)
    return json.loads(txt)


def unstripped_ns(values):
    """等价 .values | ForEach-Object { ($_ -replace '^astral_dice:', '') }。"""
    return [re.sub(r"^astral_dice:", "", str(v), flags=re.IGNORECASE) for v in (values or [])]


def sort_unique(seq):
    """等价 Sort-Object -Unique（大小写不敏感排序，去重）。"""
    seen = set()
    out = []
    for s in sorted(seq, key=lambda x: (x.lower(), x)):
        k = s.lower()
        if k in seen:
            continue
        seen.add(k)
        out.append(s)
    return out


def repo_root_from_script():
    return os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def main(argv):
    parser = argparse.ArgumentParser(
        description="立牌注册 / 标签一致性守门（verify_sign_registration.ps1 的 Python 等价实现）")
    parser.add_argument("--root", "-r", default=None,
                        help="仓库根（缺省 = 脚本位置上溯两级；ps1 在缺 settings.gradle 时回落当前目录）")
    args = parser.parse_args(argv)

    if args.root:
        root = args.root
    else:
        root = repo_root_from_script()
        if not os.path.exists(os.path.join(root, "settings.gradle")):
            root = os.getcwd()

    fail = 0
    undecidable = 0
    total_registered = 0

    for line in LINES:
        proj = line["key"]
        tags = line["tags"]
        items = os.path.join(root, "%s/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java" % proj)
        signs = os.path.join(root, "%s/src/main/resources/data/astral_dice/tags/%s/signs.json" % (proj, tags))
        stand = os.path.join(root, "%s/src/main/resources/data/curios/tags/%s/stand.json" % (proj, tags))

        for p in (items, signs, stand):
            if not os.path.exists(p):
                w("SIGN_REG: UNDECIDABLE [%s] 缺少 %s" % (proj, p))
                undecidable += 1
        if undecidable > 0:
            continue

        txt = read_text(items) or ""
        registered = sort_unique(re.findall(r'registerItem\("([a-z0-9_]+_sign)"', txt))
        total_registered += len(registered)

        try:
            sign_tag = unstripped_ns(read_json(signs).get("values"))
            stand_tag = unstripped_ns(read_json(stand).get("values"))
        except (OSError, ValueError) as exc:
            sys.stderr.write("SIGN_REG: 读取标签 JSON 失败 %s\n" % exc)
            return 1

        missing_signs = [x for x in registered if x.lower() not in {v.lower() for v in sign_tag}]
        missing_stand = [x for x in registered
                         if x.lower() not in {v.lower() for v in EXEMPT_STAND}
                         and x.lower() not in {v.lower() for v in stand_tag}]
        extra = [x for x in sort_unique(sign_tag + stand_tag)
                 if x.lower() not in {v.lower() for v in registered}]

        if missing_signs or missing_stand or extra:
            fail += 1
            w("SIGN_REG: FAIL [%s] registered=%d signs=%d stand=%d"
              % (proj, len(registered), len(sign_tag), len(stand_tag)))
            if missing_signs:
                w("   缺 signs.json : " + ", ".join(missing_signs))
            if missing_stand:
                w("   缺 stand.json : " + ", ".join(missing_stand))
            if extra:
                w("   标签内未注册: " + ", ".join(extra))
        else:
            w("SIGN_REG: OK [%s] registered=%d signs=%d stand=%d"
              % (proj, len(registered), len(sign_tag), len(stand_tag)))

    if undecidable > 0:
        w("SIGN_REG: UNDECIDABLE（检查面缺失）")
        emit()
        return 2
    if total_registered == 0:
        # 反假绿守卫：一条立牌注册都没扫到 ⇒ 检查面失效，绝不允许 ALL OK
        w("SIGN_REG: UNDECIDABLE（检查面缺失）")
        emit()
        return 2
    if fail > 0:
        w("SIGN_REG: FAIL（%d 条线不一致）" % fail)
        emit()
        return 1
    w("SIGN_REG: ALL OK")
    emit()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
