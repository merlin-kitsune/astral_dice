#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""合成配方「网格唯一性」静态守门（由 旧版 verify_crafting_recipe_uniqueness 实现 等价迁移）

用途：防止两条配方对同一个 3×3 摆放同时成立（原版 RecipeManager#getRecipeFor 取
byType(CRAFTING) 里第一条 matches() 命中的配方，后者永远产不出来）。

判据（与 旧实现 逐条对齐，按原版 matches() 语义推导）：
  * shaped ↕ shaped：pattern 展开成 3×3 单元矩阵，裁掉**四周全空**的行/列，
    内部空格仍参与（该格必须为空）⇒ 裁剪后逐格相同即重合。
  * 任意两条配方：非空材料**多重集**相同即重合（shaped 与 shapeless 跨类型也查）。
  * 材料等价只按字面 id：{"item":X}→item:X / {"tag":T}→tag:T / 数组→oneof:[...] /
    其它→json:<紧凑序列化>。

输出约定（对齐 旧实现）：
  - 旧实现 显式把 [Console]::OutputEncoding 设为 UTF8(no BOM)，并用 [Console]::Out.Write(文本 + LF)
    ⇒ stdout/stderr 一律 UTF-8 + LF（跨平台恒定）。本脚本 reconfigure newline='' 复刻。
  - 退出码：0 = 全绿；1 = 存在重合 / root 不存在 / 无子项目；
            2 = 无法判定（**迁移新增反假绿分支**：扫到 0 个配方文件时拒绝 vacuous PASS）。

与 旧实现 的已知差异（逐条）：
  1) 反假绿新增：配方文件数为 0 ⇒ stderr "RECIPE_UNIQ: ERROR 未扫到任何配方文件…" + exit 2
     （旧实现 在该情形下会打印 shaped=0|shapeless=0 然后 "RECIPE_UNIQ: OK" 退出 0，属假绿）。
  2) JSON 解析失败的报错文案沿用 python 的 json 异常文本，与 .NET ConvertFrom-Json 不同
     （仅出现在畸形 JSON 的情形，行前缀与判据一致）。
  3) 排序：旧实现 用 Sort-Object（.NET 文化敏感比较），本脚本用 python 字典序；真实数据与
     迁移夹具上两者结果一致（见交付报告），仅在材料 id 含 '_' 等标点与字母交错且相对
     顺序本就不确定时才可能不同。

用法：
  python scripts/verify/verify_crafting_recipe_uniqueness.py [-Root <仓库根>] [-LogDir <相对目录>]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time

SUB_PROJECTS = ("neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2")
RECIPE_DIRS = (
    "src/generated/resources/data/astral_dice/recipe",
    "src/generated/resources/data/astral_dice/recipes",
    "src/main/resources/data/astral_dice/recipe",
    "src/main/resources/data/astral_dice/recipes",
)


def default_root() -> str:
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.normpath(os.path.join(here, "..", ".."))


def write_out(text: str = "") -> None:
    sys.stdout.write(text + "\n")


def write_err(text: str = "") -> None:
    sys.stderr.write(text + "\n")


# ---------------------------------------------------------------------------
# 材料归一化：{"item":X} -> item:X ; {"tag":T} -> tag:T ; 数组 -> oneof:[...] ;
# 其它形态(药水等) -> json:<紧凑序列化>
# ---------------------------------------------------------------------------
def _ci_get(obj: dict, name: str):
    """旧 shell 的 PSObject 属性访问/对 -contains 均忽略大小写。"""
    if name in obj:
        return obj[name]
    low = name.lower()
    for k, v in obj.items():
        if isinstance(k, str) and k.lower() == low:
            return v
    return _MISSING


_MISSING = object()


def _compact_json(value) -> str:
    text = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    return re.sub(r"\s+", "", text)


def ingredient_key(value) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        inner = []
        for one in value:
            k = ingredient_key(one)
            if k != "":
                inner.append(k)
        inner = sorted(set(inner))
        return "oneof:[" + "+".join(inner) + "]"
    if isinstance(value, str):
        return "raw:" + value
    if isinstance(value, dict):
        for name in ("item", "tag"):
            v = _ci_get(value, name)
            if v is not _MISSING:
                return name + ":" + ("" if v is None else _ps_str(v))
        return "json:" + _compact_json(value)
    return "json:" + _compact_json(value)


def _ps_str(v) -> str:
    """[string]$v 的近似：旧 shell 把 $null 变 ''、true/false 小写。"""
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float, str)):
        return str(v)
    return _compact_json(v)


# ---------------------------------------------------------------------------
# shaped 展开为 3×3 单元矩阵，再裁剪四周全空的行/列
# ---------------------------------------------------------------------------
def shaped_cells(js: dict) -> dict:
    pat = _ci_get(js, "pattern")
    if pat is _MISSING or pat is None:
        pat = []
    elif not isinstance(pat, list):
        pat = [pat]
    h = len(pat)
    w = 0
    for row in pat:
        r = "" if row is None else _ps_str(row)
        if len(r) > w:
            w = len(r)
    grid = [["." for _ in range(3)] for _ in range(3)]
    key_obj = _ci_get(js, "key")
    if key_obj is _MISSING or key_obj is None:
        key_obj = {}
    for r in range(h):
        if r >= 3:
            break
        row = "" if pat[r] is None else _ps_str(pat[r])
        for c in range(len(row)):
            if c >= 3:
                break
            ch = row[c]
            if ch == " ":
                continue
            found = _MISSING
            for name, val in (key_obj.items() if isinstance(key_obj, dict) else []):
                if isinstance(name, str) and name == ch:  # -ceq 大小写敏感
                    found = val
                    break
            if found is _MISSING:
                grid[r][c] = "missing:" + ch
            else:
                grid[r][c] = ingredient_key(found)
    rows_with_content = [r for r in range(3) if any(grid[r][c] != "." for c in range(3))]
    cols_with_content = [c for c in range(3) if any(grid[r][c] != "." for r in range(3))]
    if not rows_with_content or not cols_with_content:
        return {"Cells": [], "W": 0, "H": 0, "Multi": []}
    r0, r1 = min(rows_with_content), max(rows_with_content)
    c0, c1 = min(cols_with_content), max(cols_with_content)
    cells = []
    multi = []
    for r in range(r0, r1 + 1):
        for c in range(c0, c1 + 1):
            cells.append(grid[r][c])
            if grid[r][c] != ".":
                multi.append(grid[r][c])
    return {"Cells": cells, "W": c1 - c0 + 1, "H": r1 - r0 + 1, "Multi": multi}


def multiset_key(items) -> str:
    return "MULTI|" + ";".join(sorted(items))


# ---------------------------------------------------------------------------
# 读配方：四个资源目录，按 basename 去重（先加载的那份生效）
# ---------------------------------------------------------------------------
def read_recipes(project_dir: str):
    found = {}
    found_lower = {}
    warn = []
    for rel in RECIPE_DIRS:
        d = os.path.join(project_dir, rel)
        if not os.path.isdir(d):
            continue
        try:
            entries = list(os.scandir(d))
        except OSError:
            continue
        for e in entries:
            try:
                if not e.is_file():
                    continue
            except OSError:
                continue
            if not e.name.lower().endswith(".json"):
                continue
            rid = os.path.splitext(e.name)[0]
            low = rid.lower()
            if low in found_lower:
                warn.append(
                    "duplicate id '%s' in '%s' and '%s' (资源管理器只保留先加载的一份)"
                    % (rid, found_lower[low]["Source"], rel)
                )
                continue
            try:
                with open(e.path, "r", encoding="utf-8-sig") as fh:
                    js = json.load(fh)
            except Exception as exc:  # noqa: BLE001 - 与 旧实现 的 catch 对齐
                warn.append("JSON parse failed: %s/%s — %s" % (rel, e.name, exc))
                continue
            if not isinstance(js, dict):
                js = {}
            rtype = _ci_get(js, "type")
            rec = {
                "Id": rid,
                "Type": "" if rtype is _MISSING or rtype is None else _ps_str(rtype),
                "Json": js,
                "Source": rel,
            }
            found[rid] = rec
            found_lower[low] = rec
    return {"Recipes": found, "Warn": warn}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="合成配方网格唯一性守门（旧实现 等价迁移版）")
    ap.add_argument("root_pos", nargs="?", default=None, help="仓库根（位置参数，可选）")
    ap.add_argument("-Root", "--root", dest="root", default="", help="仓库根（默认脚本位置上溯两级）")
    ap.add_argument("-LogDir", "--log-dir", dest="log_dir", default="", help="额外落盘目录（相对仓库根）")
    args = ap.parse_args(argv)

    root = args.root if args.root is not None else ""
    if root is None:
        root = ""
    if not root.strip():
        root = args.root_pos or default_root()
    if not os.path.exists(root):
        write_err("RECIPE_UNIQ: ERROR root not found: %s" % root)
        return 1
    root = os.path.abspath(root)

    lines = []
    for name in SUB_PROJECTS:
        proj = os.path.join(root, name)
        if os.path.exists(proj):
            lines.append({"Name": name, "Dir": proj})
    if not lines:
        write_err("RECIPE_UNIQ: ERROR no subproject found under %s" % root)
        return 1

    total_problems = 0
    report = []
    scanned_recipes = 0

    for line in lines:
        read = read_recipes(line["Dir"])
        scanned_recipes += len(read["Recipes"])
        for w in read["Warn"]:
            report.append("  [WARN] %s: %s" % (line["Name"], w))

        shaped = []
        shapeless = []
        for rec in read["Recipes"].values():
            rtype = rec["Type"]
            if re.search(r"crafting_shaped$", rtype, re.IGNORECASE):
                cells = shaped_cells(rec["Json"])
                if cells["W"] == 0:
                    report.append("  [WARN] %s: %s 是空网格(无任何材料),已跳过" % (line["Name"], rec["Id"]))
                    continue
                shaped.append(
                    {
                        "Id": rec["Id"],
                        "GridKey": "SHAPED|%dx%d|" % (cells["W"], cells["H"]) + ",".join(cells["Cells"]),
                        "MultiKey": multiset_key(cells["Multi"]),
                        "Multi": "+".join(sorted(cells["Multi"])),
                    }
                )
            elif re.search(r"crafting_shapeless$", rtype, re.IGNORECASE):
                multi = []
                ing = _ci_get(rec["Json"], "ingredients")
                if ing is not _MISSING and ing is not None:
                    for one in (ing if isinstance(ing, list) else [ing]):
                        k = ingredient_key(one)
                        if k != "":
                            multi.append(k)
                if len(multi) == 0:
                    report.append("  [WARN] %s: %s 是 shapeless 但 ingredients 为空,已跳过" % (line["Name"], rec["Id"]))
                    continue
                shapeless.append(
                    {
                        "Id": rec["Id"],
                        "GridKey": multiset_key(multi),
                        "MultiKey": multiset_key(multi),
                        "Multi": "+".join(sorted(multi)),
                    }
                )

        problems = []
        for items, kind in ((shaped, "shaped/shaped"), (shapeless, "shapeless/shapeless")):
            groups = {}
            order = []
            for it in items:
                k = it["GridKey"]
                if k not in groups:
                    groups[k] = []
                    order.append(k)
                groups[k].append(it)
            for k in order:
                grp = groups[k]
                if len(grp) <= 1:
                    continue
                problems.append(
                    {
                        "Kind": kind,
                        "Ids": " | ".join(sorted(g["Id"] for g in grp)),
                        "Key": grp[0]["Multi"],
                    }
                )
        for s in shaped:
            for l in shapeless:
                if s["MultiKey"] == l["MultiKey"]:
                    problems.append(
                        {
                            "Kind": "shaped/shapeless",
                            "Ids": " | ".join(sorted([s["Id"], l["Id"]])),
                            "Key": s["Multi"],
                        }
                    )

        if problems:
            total_problems += len(problems)
            report.append("  [FAIL] %s: 重合 %d 组" % (line["Name"], len(problems)))
            for p in problems:
                report.append("         - %s: %s  材料={%s}" % (p["Kind"], p["Ids"], p["Key"]))
        report.append(
            "RECIPE_UNIQ|%s|shaped=%d|shapeless=%d|collisions=%d"
            % (line["Name"], len(shaped), len(shapeless), len(problems))
        )

    # 反假绿：一个配方文件都没扫到 ⇒ 无法判定，绝不给 OK
    if scanned_recipes == 0:
        write_err(
            "RECIPE_UNIQ: ERROR 未扫到任何配方文件（root=%s）——无法判定，拒绝 vacuous PASS" % root
        )
        return 2

    report.append("RECIPE_UNIQ: TOTAL collisions=%d lines=%d" % (total_problems, len(lines)))

    for l in report:
        write_out(l)

    if args.log_dir is not None and args.log_dir.strip():
        abs_dir = os.path.join(root, args.log_dir)
        os.makedirs(abs_dir, exist_ok=True)
        stamp = time.strftime("%Y%m%d-%H%M%S")
        out_file = os.path.join(abs_dir, "recipe_uniqueness_%s.txt" % stamp)
        with open(out_file, "w", encoding="utf-8", newline="") as fh:
            fh.write("\n".join(report) + "\n")
        write_out("RECIPE_UNIQ: log=%s" % out_file)

    if total_problems > 0:
        write_err("RECIPE_UNIQ: FAIL collisions=%d" % total_problems)
        return 1
    write_out("RECIPE_UNIQ: OK")
    return 0


if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8", newline="")
        sys.stderr.reconfigure(encoding="utf-8", newline="")
    except Exception:
        pass
    sys.exit(main())
