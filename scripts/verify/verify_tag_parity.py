#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""verify_tag_parity.py -- 跨加载器物品标签对等守门（四条加载器线）。

判定口径
--------
* 事实源（P0）= neoforge-1.21.1（n21）为主，n21 缺该标签文件时退回
  forge-1.20.1（forge）。
* 判定面（P1）= neoforge-26.1.2（n26）、fabric-1.20.1（fabric）。
* 标签路径归一化：ns/tags/item 与 ns/tags/items 一律归一为
  ns/tags/item/<rest>（1.20.1 两线是 tags/items 复数，1.21+ 是 tags/item
  单数，二者视为同一标签）。
* 比对内容是 values 的集合（顺序不参与判定；条目可为字符串、
  {"id": ...} 对象或 #tag 引用，按字面值比较）。

致命项
------
  MISSING        P1 缺条：P0 有、该线没有（含整份文件缺失）。
  EXTRA          P1 多条：该线有、P0 没有（据此把对齐方向定死）。
  BASE_CONFLICT  P0 内部 n21 与 forge 的 values 集合互相不一致 ——
                 事实源自身冲突，必须人工裁决，不允许静默选边。

非致命项（不改变退出码）
------------------------
  TAGPARITY_WARN: FILE_ABSENT <line> <tagpath>
      fabric 整份文件缺失（该线平台不参与该标签）而 P0 两线都有。
      注意：文件在、条不在，仍然算致命 MISSING。
  TAGPARITY_NOTE: ...（--verbose 下打印）
      平台专用命名空间跳过、P0 文件存在性不对称、重复条目、replace 差异。

判定面（命名空间范围）
----------------------
默认只判定两条 P0 线都声明过的命名空间（本仓为 astral_dice、curios）。
平台自有命名空间（forge 线的 forge/tags、fabric 线的 c/tags 与
trinkets/tags）没有跨加载器对应物，纳入判定只会制造假阳性，因此默认跳过
并计数；需要强制纳入时用 --namespaces 显式列出，范围始终可审计。

契约（机器可读，勿改格式）
--------------------------
  TAGPARITY: MISSING        <line> <tagpath> <id,id,...>
  TAGPARITY: EXTRA          <line> <tagpath> <id,id,...>
  TAGPARITY: BASE_CONFLICT  <neoforge-1.21.1|forge-1.20.1> <tagpath> <id,id,...>
  末行 TAG_PARITY: OK checked=<n> files=<n>
  末行 TAG_PARITY: FAIL issues=<n> checked=<n> files=<n>

退出码
------
  0  全部对等
  1  存在致命项
  2  无法判定：根目录结构不符 / 扫到 0 个标签文件 / 判定面为空 / JSON 解析失败

checked = 实际判定过的标签数（判定面内的 key 数）；
files   = 四线扫描到的标签 JSON 文件数（已排除 generated 目录）。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

LINES = ("neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2", "fabric-1.20.1")
P0_LINES = ("neoforge-1.21.1", "forge-1.20.1")
P1_LINES = ("neoforge-26.1.2", "fabric-1.20.1")
PRIMARY_P0 = "neoforge-1.21.1"
SECOND_P0 = "forge-1.20.1"
FABRIC_LINE = "fabric-1.20.1"

REL_DATA = Path("src") / "main" / "resources" / "data"
SKIP_DIR_NAMES = frozenset({"generated"})
UNDECIDABLE = "无法判定"


def _seg_norm(seg):
    """item / items -> item；其它返回 None。"""
    if seg == "item" or seg == "items":
        return "item"
    return None


def discover_line(root, line):
    """返回 {tag_key: {"path": Path}}，只做发现，不解析。"""
    data_dir = Path(root) / line / REL_DATA
    out = {}
    if not data_dir.is_dir():
        return out
    data_dir_s = str(data_dir)
    for dirpath, dirnames, filenames in os.walk(data_dir_s):
        dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIR_NAMES)
        for fname in sorted(filenames):
            if not fname.endswith(".json"):
                continue
            full = os.path.join(dirpath, fname)
            rel = os.path.relpath(full, data_dir_s)
            parts = rel.replace(os.sep, "/").split("/")
            if len(parts) < 4:
                continue
            if parts[1] != "tags":
                continue
            seg = _seg_norm(parts[2])
            if seg is None:
                continue
            key = parts[0] + "/tags/" + seg + "/" + "/".join(parts[3:])
            out[key] = {"path": Path(full)}
    return out


def load_values(rec):
    """解析一份标签文件；返回 (values, replace, dups, error)。"""
    raw = rec["path"].read_bytes()
    try:
        doc = json.loads(raw.decode("utf-8-sig"))
    except Exception as exc:
        return None, None, [], "JSON parse error: {0}".format(exc)
    if not isinstance(doc, dict):
        return None, None, [], "top-level JSON is not an object"
    vals = doc.get("values", [])
    if not isinstance(vals, list):
        return None, None, [], "values is not a list"
    norm = []
    for item in vals:
        if isinstance(item, str):
            norm.append(item.strip())
        elif isinstance(item, dict):
            ident = item.get("id", item.get("tag"))
            if not isinstance(ident, str):
                return None, None, [], "entry object without string id"
            norm.append(ident.strip())
        else:
            return None, None, [], "entry is neither string nor object"
    seen = set()
    dups = []
    for v in norm:
        if v in seen:
            dups.append(v)
        seen.add(v)
    return norm, doc.get("replace", None), dups, None


def namespace_of(tag_key):
    return tag_key.split("/", 1)[0]


def resolve_default_root():
    return Path(__file__).resolve().parents[2]


def build_parser():
    parser = argparse.ArgumentParser(
        prog="verify_tag_parity.py",
        add_help=True,
        description="跨加载器物品标签对等守门：P1 相对 P0 的缺条 / 多条 / 事实源内部冲突。",
        epilog="退出码 0=OK 1=有致命项 2=无法判定。完整口径见文件头注释。",
    )
    parser.add_argument(
        "--root",
        default=None,
        help="仓库根（含四条加载器线目录）。缺省按脚本位置上溯两级推导，与 cwd 解耦。",
    )
    parser.add_argument(
        "--namespaces",
        default=None,
        help="强制指定判定面命名空间（逗号分隔）。缺省 = 两条 P0 线都声明过的命名空间。",
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="打印全部 NOTE（平台命名空间、P0 不对称、重复条目、replace 差异）。",
    )
    return parser


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)

    root = Path(args.root).resolve() if args.root else resolve_default_root()

    missing_dirs = []
    for line in LINES:
        d = root / line / REL_DATA
        if not d.is_dir():
            missing_dirs.append(str(d))
    if missing_dirs:
        print("TAGPARITY_ERROR: ROOT_STRUCTURE root={0}".format(root))
        for d in missing_dirs:
            print("TAGPARITY_ERROR: MISSING_DIR {0}".format(d))
        print("TAG_PARITY: {0} reason=root-structure".format(UNDECIDABLE))
        print("  {0}：给出的 --root 下找不到四线 data 目录。".format(UNDECIDABLE))
        return 2

    files = {}
    scan_errors = []
    total_files = 0
    for line in LINES:
        recs = discover_line(root, line)
        total_files += len(recs)
        good = {}
        for key in sorted(recs):
            vals, replace, dups, err = load_values(recs[key])
            if err is not None:
                scan_errors.append((line, key, err))
                continue
            good[key] = {
                "path": recs[key]["path"],
                "values": vals,
                "set": set(vals),
                "replace": replace,
                "dups": dups,
            }
        files[line] = good

    if scan_errors:
        for line, key, err in scan_errors:
            print("TAGPARITY_NOTE: PARSE_ERROR {0} {1} :: {2}".format(line, key, err))
        print("TAG_PARITY: {0} reason=parse-error".format(UNDECIDABLE))
        return 2

    if total_files == 0:
        print("TAGPARITY_ERROR: NO_TAG_FILES root={0}".format(root))
        print("TAG_PARITY: {0} reason=no-tag-file".format(UNDECIDABLE))
        print("  扫到 0 个标签文件，拒绝 vacuous PASS。")
        return 2

    p0_ns = [set(namespace_of(k) for k in files[l]) for l in P0_LINES]
    default_ns = p0_ns[0] & p0_ns[1]
    if args.namespaces:
        scope_ns = set(x.strip() for x in args.namespaces.split(",") if x.strip())
    else:
        scope_ns = set(default_ns)

    def in_scope(key):
        return namespace_of(key) in scope_ns

    in_scope_files = 0
    for line in LINES:
        for key in files[line]:
            if in_scope(key):
                in_scope_files += 1
    out_scope_files = total_files - in_scope_files

    print(
        "TAGPARITY_SCOPE: namespaces={0} explicit={1} in_scope_files={2} "
        "out_of_scope_files={3} total_files={4}".format(
            ",".join(sorted(scope_ns)) or "-",
            "yes" if args.namespaces else "no",
            in_scope_files,
            out_scope_files,
            total_files,
        )
    )
    if args.verbose:
        for line in LINES:
            for key in sorted(files[line]):
                if not in_scope(key):
                    print(
                        "TAGPARITY_NOTE: OUT_OF_SCOPE {0} {1} :: "
                        "namespace not declared by both P0 lines".format(line, key)
                    )

    keys = set()
    for line in LINES:
        for key in files[line]:
            if in_scope(key):
                keys.add(key)

    if not keys:
        print("TAGPARITY_ERROR: EMPTY_SCOPE root={0}".format(root))
        print("TAG_PARITY: {0} reason=empty-scope".format(UNDECIDABLE))
        return 2

    issues = []
    warns = []

    for key in sorted(keys):
        n21_rec = files[PRIMARY_P0].get(key)
        forge_rec = files[SECOND_P0].get(key)
        if n21_rec is not None:
            base_line = PRIMARY_P0
            base_set = n21_rec["set"]
        elif forge_rec is not None:
            base_line = SECOND_P0
            base_set = forge_rec["set"]
        else:
            base_line = None
            base_set = None

        if n21_rec is not None and forge_rec is not None:
            if n21_rec["set"] != forge_rec["set"]:
                diff = sorted(n21_rec["set"] ^ forge_rec["set"])
                issues.append(
                    (
                        "BASE_CONFLICT",
                        "{0}|{1}".format(PRIMARY_P0, SECOND_P0),
                        key,
                        diff,
                    )
                )
        elif args.verbose and (n21_rec is None) != (forge_rec is None):
            present = PRIMARY_P0 if n21_rec is not None else SECOND_P0
            print(
                "TAGPARITY_NOTE: BASE_FILE_ASYMMETRY {0} :: only {1} has it "
                "(baseline falls back)".format(key, present)
            )

        if base_set is None:
            for line in P1_LINES:
                if key in files[line]:
                    issues.append(("EXTRA", line, key, sorted(files[line][key]["set"])))
            continue

        for line in P1_LINES:
            rec = files[line].get(key)
            if rec is None:
                if line == FABRIC_LINE and n21_rec is not None and forge_rec is not None:
                    warns.append(("FILE_ABSENT", line, key, sorted(base_set)))
                else:
                    issues.append(("MISSING", line, key, sorted(base_set)))
                continue
            missing = sorted(base_set - rec["set"])
            extra = sorted(rec["set"] - base_set)
            if missing:
                issues.append(("MISSING", line, key, missing))
            if extra:
                issues.append(("EXTRA", line, key, extra))
            if args.verbose and rec["dups"]:
                print(
                    "TAGPARITY_NOTE: DUPLICATE_ENTRY {0} {1} :: {2}".format(
                        line, key, ",".join(sorted(set(rec["dups"])))
                    )
                )
            if args.verbose and base_line is not None:
                base_replace = files[base_line][key]["replace"]
                if (rec["replace"] is not None) != (base_replace is not None):
                    print("TAGPARITY_NOTE: REPLACE_KEY_DIFF {0} {1}".format(line, key))

    line_order = {name: i for i, name in enumerate(LINES)}
    kind_order = {"MISSING": 0, "EXTRA": 1, "BASE_CONFLICT": 2}
    issues.sort(
        key=lambda it: (
            line_order.get(it[1], 99),
            it[2],
            kind_order.get(it[0], 9),
        )
    )

    for kind, who, key, ids in issues:
        print("TAGPARITY: {0} {1} {2} {3}".format(kind, who, key, ",".join(ids)))
    for kind, who, key, ids in warns:
        print("TAGPARITY_WARN: {0} {1} {2} {3}".format(kind, who, key, ",".join(ids)))

    checked = len(keys)
    if issues:
        print("TAG_PARITY: FAIL issues={0} checked={1} files={2}".format(
            len(issues), checked, total_files
        ))
        return 1
    print("TAG_PARITY: OK checked={0} files={1}".format(checked, total_files))
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())

