# -*- coding: utf-8 -*-
"""Bountiful 赏金板「外部模组物品清除」校验（只读守门）。

背景
----
Bountiful 8.x 自带一批**兼容池**，位于 bountiful 自己的 jar 内：
  data/bountiful/bounty_pools/<modid>/<pool>.json
（本机实例里 <modid> = supplementaries，14 文件 / 31 条目，全部 content 指向
 `supplementaries:` 命名空间）。这些条目不是 Supplementaries 注册的，
而是 Bountiful 主动生成的，会挤占赏金板。

本脚本独立复算「实例里还有哪些赏金池条目引用了目标模组命名空间」，并核验
两种清除机制是否把每一条都覆盖住：

  机制 A —— config/bountiful/bountiful.json 的 general.dataPathsToExclude
             每项把 `*` 替换为 `([A-Za-z_/]+)` 构成正则，对
             ResourceLocation.getPath() 去掉 ".json" 后的字符串做**全串匹配**
             （反编译 io.ejekta.bountiful.config.ResourceLoadStrategy#getResources 证实）。
             → "bounty_pools/<modid>/*" 即排除该目录下所有池文件。
             ⚠️ 该字段由 BountifulIO.reloadConfig() = saveConfig() + loadConfig()
                先写后读 → **必须在游戏关闭时改文件**，否则运行中改会被内存旧值回写覆盖。

  机制 B —— config/bountiful/bounty_pools/<与 jar 内同名的池>.json
             把条目键的值设为 null。池身份 = **文件名**（不含目录），config 层与
             jar 层同名池会合并；反编译 io.ejekta.bountiful.data.Pool#merged 证实：
               键在 base 中不存在 → 原样放入（null 也放）
               值 == null          → 覆盖为 null
               否则                → KudzuVine.graft 深合并
             随后 Pool#setup 里 `if (json == null) return@forEach` → null 条目被跳过。
             → 条目被真正移除。config 层文件 Bountiful 从不回写，/reload 即生效。

扫描来源（每个实例）
  - <root>/mods/*.jar            → data/*/bounty_pools/**.json
  - <root>/config/bountiful/bounty_pools/*.json
  - <root>/saves/*/datapacks/*/data/*/bounty_pools/**.json
  - <root>/datapacks/*/data/*/bounty_pools/**.json

退出码：0 = 目标命名空间条目 0 泄漏且覆盖完整；1 = 有泄漏/覆盖不全。只读。

用法:
  python scripts/verify/verify_bountiful_instance_exclusions.py
  python scripts/verify/verify_bountiful_instance_exclusions.py --target supplementaries
  python scripts/verify/verify_bountiful_instance_exclusions.py --instances "D:\\.minecraft\\versions\\狐の航空学"
"""
import argparse
import glob
import json
import os
import re
import sys
import zipfile

DEFAULT_BASES = [r"D:\.minecraft\versions",
                 os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "run")]

JAR_POOL_RE = re.compile(r"^data/([^/]+)/bounty_pools/(.+?)\.json$")

errors = []


def err(msg):
    errors.append(msg)


def wildcard_regex(pattern):
    """复刻 Kotlin 侧 Regex(pattern.replace(Regex("[*]"), "([A-Za-z_/]+)"))"""
    return re.compile(pattern.replace("*", "([A-Za-z_/]+)"))


def is_excluded(path_no_ext, patterns):
    """path_no_ext 例：bounty_pools/supplementaries/_all_objs"""
    return any(wildcard_regex(p).fullmatch(path_no_ext) for p in patterns)


def find_bountiful_jars(root):
    out = []
    for pat in ("mods/*.jar", "*/mods/*.jar"):
        out += glob.glob(os.path.join(root, pat))
    return [p for p in out if "bountiful" in os.path.basename(p).lower()]


def load_excluded_patterns(root):
    cfg = os.path.join(root, "config", "bountiful", "bountiful.json")
    if not os.path.isfile(cfg):
        return None  # 无 bountiful 配置文件
    try:
        with open(cfg, "r", encoding="utf-8") as fh:
            data = json.load(fh)
    except Exception as exc:  # noqa: BLE001
        err("%s 解析失败: %s" % (cfg, exc))
        return []
    return data.get("general", {}).get("dataPathsToExclude") or []


def collect_sources(root):
    """返回 [(source_label, path_no_ext, raw_text)]，path_no_ext 已剥掉 .json 与 data/<ns>/ 前缀"""
    out = []
    for jar in sorted(find_bountiful_jars(root)):
        try:
            zf = zipfile.ZipFile(jar)
        except Exception as exc:  # noqa: BLE001
            err("jar 无法打开: %s (%s)" % (jar, exc))
            continue
        for name in zf.namelist():
            m = JAR_POOL_RE.match(name)
            if not m:
                continue
            try:
                raw = zf.read(name).decode("utf-8")
            except Exception:  # noqa: BLE001
                continue
            # path relative to data/<ns>/ -> "bounty_pools/<...>"，与 dataPathsToExclude 口径一致
            out.append(("jar:" + os.path.basename(jar),
                        "bounty_pools/" + m.group(2), raw))
    for f in sorted(glob.glob(os.path.join(root, "config", "bountiful",
                                           "bounty_pools", "*.json"))):
        out.append(("config:" + os.path.basename(f),
                    "bounty_pools/" + os.path.basename(f)[:-5], _read(f)))
    for pat in ("saves/*/datapacks/*/data/*/bounty_pools/**/*.json",
                "saves/*/datapacks/*/data/*/bounty_pools/*.json",
                "datapacks/*/data/*/bounty_pools/**/*.json",
                "datapacks/*/data/*/bounty_pools/*.json"):
        for f in glob.glob(os.path.join(root, pat), recursive=True):
            m = re.search(r"[/\\]data[/\\][^/\\]+[/\\](bounty_pools[/\\].+)\.json$", f)
            rel = m.group(1).replace("\\", "/") if m else os.path.relpath(f, root)
            out.append(("datapack:" + os.path.relpath(f, root), rel, _read(f)))
    return out


def _read(fp):
    try:
        with open(fp, "r", encoding="utf-8") as fh:
            return fh.read()
    except Exception:  # noqa: BLE001
        return ""


def audit(root, target):
    print("=" * 86)
    print("INSTANCE :", root)
    if not find_bountiful_jars(root):
        print("  (无 bountiful jar，跳过)")
        return 0, 0, 0

    patterns = load_excluded_patterns(root)
    if patterns is None:
        print("  ⚠ 未找到 config/bountiful/bountiful.json（首次运行游戏后才会生成）")
        patterns = []
    print("  jar          :", [os.path.basename(j) for j in find_bountiful_jars(root)])
    print("  exclude 列表 :", patterns)

    srcs = collect_sources(root)
    # config 覆盖层：池名 -> 被 null 的键集合
    cfg_null = {}
    for label, rel, raw in srcs:
        if not label.startswith("config:"):
            continue
        try:
            d = json.loads(raw)
        except Exception:  # noqa: BLE001
            continue
        pool = os.path.basename(rel)
        cfg_null.setdefault(pool, set()).update(
            k for k, v in (d.get("content") or {}).items() if v is None)

    total = leaked = covered = 0
    leak_rows = []
    for label, rel, raw in srcs:
        if target + ":" not in raw:
            continue
        try:
            d = json.loads(raw)
        except Exception:  # noqa: BLE001
            err("%s 的 %s JSON 解析失败" % (label, rel))
            continue
        pool = os.path.basename(rel)
        excl = is_excluded(rel, patterns)
        for key, val in sorted((d.get("content") or {}).items()):
            if val is None or target + ":" not in json.dumps(val):
                continue
            total += 1
            nulled = key in cfg_null.get(pool, set())
            ok = excl or nulled
            if ok:
                covered += 1
            else:
                leaked += 1
                leak_rows.append((rel, key, label))
        if not excl and pool not in cfg_null:
            err("池 %s 既未被 dataPathsToExclude 排除，也没有 config 覆盖文件（可能泄漏）" % rel)

    for rel, key, label in leak_rows:
        print("  ✗ 泄漏: %s :: %s    (来源 %s)" % (rel, key, label))
    print("  目标命名空间('%s:')条目总数 : %d" % (target, total))
    print("  已被机制 A/B 覆盖          : %d" % covered)
    print("  仍会出现在赏金板           : %d" % leaked)
    print("  →", "CLEAR" if leaked == 0 else "**LEAK**")
    return total, covered, leaked


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--target", default="supplementaries",
                    help="要清除的模组命名空间（默认 supplementaries）")
    ap.add_argument("--instances", nargs="*", default=None,
                    help="实例根目录；默认扫描 D:\\.minecraft\\versions\\* 与仓库 run/")
    args = ap.parse_args()

    roots = []
    if args.instances:
        roots = args.instances
    else:
        for base in DEFAULT_BASES:
            base = os.path.normpath(base)
            if os.path.isdir(base):
                roots += [os.path.join(base, d) for d in sorted(os.listdir(base))
                          if os.path.isdir(os.path.join(base, d))]

    grand = [0, 0, 0]
    for r in roots:
        if not os.path.isdir(r):
            err("实例目录不存在: %s" % r)
            continue
        t, c, l = audit(r, args.target)
        grand = [grand[0] + t, grand[1] + c, grand[2] + l]

    print("=" * 86)
    print("汇总: 目标条目 %d | 已覆盖 %d | 泄漏 %d | 错误 %d"
          % (grand[0], grand[1], grand[2], len(errors)))
    for e in errors:
        print("  !", e)
    print("RESULT:", "ALL CLEAR" if grand[2] == 0 and not errors else "PROBLEM")
    return 0 if grand[2] == 0 and not errors else 1


if __name__ == "__main__":
    sys.exit(main())
