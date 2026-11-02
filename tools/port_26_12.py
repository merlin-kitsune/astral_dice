#!/usr/bin/env python3
"""26.1.2 线移植分析器（只读分析；codemod 需显式 --apply）。

用途：把 `neoforge-1.21.1`（1.5.0-rc.7 基线）与 `neoforge-26.1.2` 的
java / 资源 / lang / 注册表四类差异算清楚，供
`docs/migration-26.1.2-rc7-mapping.md` 与分波移植使用。

子命令
  plan     文件三分类（A 缺失 / B 需增量合并 / C 纯适配）+ 资源差集 + lang 键差集
  regs     注册表对账（effect / item / attachment / payload 的 id 集合）
  codemod  按 26.1.2 API 字典做机械替换（默认 dry-run，--apply 才写盘）

分类判据（plan）
  C 纯适配：两线都有该文件且内容不同，但自「26.1.2 侧最后一次改动该文件」的提交以来，
            1.21.1 侧该文件从未再变 ⇒ 差异全部来自版本适配，不需迁移。
  B 需合并：同上，但 1.21.1 侧在该提交之后又改过 ⇒ 需要三路合并，base 取该提交时的 1.21.1 版本。
  A 缺失  ：1.21.1 有、26.1.2 没有 ⇒ 整文件移植。

用法
  python3 tools/port_26_12.py plan --json temp/p1-plan.json
  python3 tools/port_26_12.py regs
  python3 tools/port_26_12.py codemod --dry-run
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import tempfile
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OLD = "neoforge-1.21.1"
NEW = "neoforge-26.1.2"
SRC = "src/main/java"
RES = "src/main/resources"
LANG = "assets/astral_dice/lang"


def git(*args, check=False):
    p = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True,
                       errors="replace")
    if check and p.returncode != 0:
        raise RuntimeError(p.stderr.strip() or "git failed")
    return p


def walk(base_dir, suffix=None):
    out = []
    for dirpath, _dirs, files in os.walk(base_dir):
        for f in files:
            if suffix and not f.endswith(suffix):
                continue
            out.append(os.path.relpath(os.path.join(dirpath, f), base_dir).replace(os.sep, "/"))
    return sorted(out)


def read(path):
    with open(os.path.join(ROOT, path), "rb") as fh:
        return fh.read()


def show(rev, path):
    p = git("show", f"{rev}:{path}")
    return p.stdout if p.returncode == 0 else None


def last_touch(path):
    p = git("log", "-1", "--format=%H", "--", path)
    return p.stdout.strip() or None


def count_lines(text):
    return text.count("\n") + (0 if text.endswith("\n") else 1)


def classify_java():
    old_root = os.path.join(ROOT, OLD, SRC)
    new_root = os.path.join(ROOT, NEW, SRC)
    old_files = walk(old_root, ".java")
    new_set = set(walk(new_root, ".java"))
    rows = []
    for rel in old_files:
        old_path = f"{OLD}/{SRC}/{rel}"
        new_path = f"{NEW}/{SRC}/{rel}"
        if rel not in new_set:
            old_text = read(old_path).decode("utf-8", "replace")
            rows.append({"kind": "A", "rel": rel, "lines": count_lines(old_text)})
            continue
        a = read(old_path)
        b = read(new_path)
        if a == b:
            rows.append({"kind": "C0", "rel": rel, "lines": count_lines(a.decode("utf-8", "replace"))})
            continue
        a_n = a.replace(b"\r\n", b"\n")
        b_n = b.replace(b"\r\n", b"\n")
        if a_n == b_n:
            rows.append({"kind": "C1", "rel": rel, "base": "crlf-only",
                         "lines": count_lines(a_n.decode("utf-8", "replace"))})
            continue
        commit = last_touch(new_path)
        base = show(commit, old_path) if commit else None
        if base is None:
            rows.append({"kind": "B", "rel": rel, "base": None,
                         "diff_lines": None, "lines": count_lines(a.decode("utf-8", "replace"))})
        elif base.replace("\r\n", "\n") == a.decode("utf-8", "replace").replace("\r\n", "\n"):
            rows.append({"kind": "C1", "rel": rel, "base": commit,
                         "lines": count_lines(a.decode("utf-8", "replace"))})
        else:
            import difflib
            d = list(difflib.unified_diff(base.splitlines(), a.decode("utf-8", "replace").splitlines(), n=0))
            changed = sum(1 for line in d if line[:1] in "+-" and not line.startswith(("+++", "---")))
            rows.append({"kind": "B", "rel": rel, "base": commit, "diff_lines": changed,
                         "lines": count_lines(a.decode("utf-8", "replace"))})
    unique = sorted(set(walk(new_root, ".java")) - set(old_files))
    return rows, unique


def res_diff():
    old_root = os.path.join(ROOT, OLD, RES)
    new_root = os.path.join(ROOT, NEW, RES)
    old_files = set(walk(old_root))
    new_files = set(walk(new_root))
    missing = sorted(old_files - new_files)
    extra = sorted(new_files - old_files)
    groups = defaultdict(lambda: [0, 0])
    for rel in missing:
        parts = rel.split("/")
        key = "/".join(parts[:3]) if len(parts) > 3 else "/".join(parts[:2])
        groups[key][0] += 1
    for rel in extra:
        parts = rel.split("/")
        key = "/".join(parts[:3]) if len(parts) > 3 else "/".join(parts[:2])
        groups[key][1] += 1
    return missing, extra, groups


def lang_diff():
    out = {}
    for name in ("zh_cn", "en_us", "ja_jp"):
        rel = f"{RES}/{LANG}/{name}.json"
        po = os.path.join(ROOT, OLD, rel)
        pn = os.path.join(ROOT, NEW, rel)
        if not (os.path.exists(po) and os.path.exists(pn)):
            out[name] = None
            continue
        with open(po, encoding="utf-8") as fh:
            o = json.load(fh)
        with open(pn, encoding="utf-8") as fh:
            n = json.load(fh)
        out[name] = {
            "old_keys": len(o), "new_keys": len(n),
            "missing": sorted(set(o) - set(n)),
            "extra": sorted(set(n) - set(o)),
        }
    return out


def parse_ids(path, pattern):
    if not os.path.exists(os.path.join(ROOT, path)):
        return None
    text = open(os.path.join(ROOT, path), encoding="utf-8", errors="replace").read()
    return sorted(set(re.findall(pattern, text)))


def regs():
    spec = {
        "effects": (f"{NEW}/{SRC}/com/merlinkitsune/astral_dice/effect/ModEffects.java",
                    r'register\(\s*"([a-z0-9_]+)"'),
        "items": (f"{NEW}/{SRC}/com/merlinkitsune/astral_dice/item/ModItems.java",
                  r'registerItem\(\s*"([a-z0-9_]+)"'),
        "attachments": (f"{NEW}/{SRC}/com/merlinkitsune/astral_dice/component/ModAttachments.java",
                        r'register\(\s*"([a-z0-9_]+)"'),
    }
    spec_old = {k: (v[0].replace(NEW, OLD, 1), v[1]) for k, v in spec.items()}
    result = {}
    for key in spec:
        new_ids = parse_ids(*spec[key])
        old_ids = parse_ids(*spec_old[key])
        result[key] = {
            "old": old_ids, "new": new_ids,
            "missing": sorted(set(old_ids or []) - set(new_ids or [])),
            "extra": sorted(set(new_ids or []) - set(old_ids or [])),
        }
    return result


CODEMOD_RULES = [
    ("resourcelocation-import", r"import net\.minecraft\.resources\.ResourceLocation;",
     "import net.minecraft.resources.Identifier;"),
    ("resourcelocation-new", r"new ResourceLocation\(([^,()]+),\s*([^()]+)\)",
     r"Identifier.fromNamespaceAndPath(\1, \2)"),
    ("resourcelocation-type", r"\bResourceLocation\b", "Identifier"),
    ("irh-type", r"InteractionResultHolder<ItemStack>", "InteractionResult"),
    ("isclientSide-var", r"\.isClientSide\b(?!\()", ".isClientSide()"),
    ("inv-items", r"\.getInventory\(\)\.items\b", ".getNonEquipmentItems()"),
    ("displayClientMessage", r"\.displayClientMessage\(", ".sendOverlayMessage("),
    ("getServer", r"\.getServer\(\)", ".getServer()"),
]


def codemod(apply=False, only=None):
    old_root = os.path.join(ROOT, OLD, SRC)
    new_root = os.path.join(ROOT, NEW, SRC)
    old_files = set(walk(old_root, ".java"))
    new_files = walk(new_root, ".java")
    targets = [rel for rel in new_files if rel in old_files]
    report = []
    for rel in targets:
        path = os.path.join(new_root, rel)
        with open(path, encoding="utf-8", errors="replace") as fh:
            text = fh.read()
        original = text
        hits = []
        for name, pat, repl in CODEMOD_RULES:
            if only and name not in only:
                continue
            new_text, n = re.subn(pat, repl, text)
            if n:
                hits.append((name, n))
                text = new_text
        if hits:
            report.append({"rel": rel, "hits": hits})
            if apply:
                with open(path, "w", encoding="utf-8", newline="") as fh:
                    fh.write(text)
    return report


WAVE_RULES = [
    ("P5", ("item/",)),
    ("P4", ("shop/", "screen/", "client/", "block/", "entity/", "mixin/", "compat/", "choice/")),
    ("P3", ("event/", "network/", "combat/", "world/", "trade/", "command/", "recipe/", "choicemenu/")),
    ("P2", ("component/", "effect/", "init/", "datagen/", "core/", "util/", "audio/", "particle/")),
]


def wave_of(rel):
    for wave, prefixes in WAVE_RULES:
        if rel.startswith(tuple(prefixes)):
            return wave
    return "P2"


API_DICT = """| 类别 | 1.21.1 写法 | 26.1.2 写法 | 取证 |
|---|---|---|---|
| 标识符类型 | `ResourceLocation` | `Identifier` | `26.1.2/.../audio/ModSounds.java:5` |
| 构造标识符 | `new ResourceLocation(ns, path)` | `Identifier.fromNamespaceAndPath(ns, path)` | `ModSounds.java:107` |
| 物品交互返回 | `InteractionResultHolder<ItemStack>` + `.success(x)` | `InteractionResult` + `.SUCCESS` | `26.1.2/.../item/card/BaseEffectCardItem.java:81` |
| 客户端判定 | `level.isClientSide`（字段） | `isClientSide()`（方法） | `26.1.2/.../client/CombatPowerHud.java` |
| 背包遍历 | `player.getInventory().items` | `getInventory().getNonEquipmentItems()` | 本次 `plan` 的 96 个纯适配对 |
| 消息发送 | `player.displayClientMessage(text, overlay)` | `sendOverlayMessage` / `sendSystemMessage` | 26.1.2 侧 13 处改写 |
| 服务端取用 | `entity.getServer()` | `entity.level().getServer()` | 26.1.2 侧 17 处改写 |
| 注册物品 | `new Item.Properties()` 直传 | `registerItem(name, props -> new XxxItem(...))` | `26.1.2/.../item/ModItems.java:157` |
| 容器点击 | `ClickType` | `ContainerInput` | `docs/compat-26.1.2-neoforge.md` §2 |
| 药水/组件 | `stack.get(DataComponents.FOOD)` 语义 | 26.1.2 data components 调整 | 同上 §2 |
| mixin 级别 | `JAVA_21` | `compatibilityLevel JAVA_25` | `docs/compat-26.1.2-neoforge.md` §3 |
| 事件注解 | `@EventBusSubscriber` + 任意 `@SubscribeEvent` | 无监听方法的类不得带注解（26.1.2 抛异常） | 同上 §4 |
| datagen 入口 | 单 `GatherDataEvent` | `.Client` / `.Server` 双入口 + 双输出根 | 同上 §5 |
"""


def cmd_doc(args):
    plan = json.load(open(os.path.join(ROOT, args.plan), encoding="utf-8"))
    reg = regs()
    t = plan["totals"]

    def table(rows, head):
        out = ["| " + " | ".join(head) + " |", "|" + "---|" * len(head)]
        out += ["| " + " | ".join(str(c) for c in r) + " |" for r in rows]
        return "\n".join(out)

    a_rows = sorted(plan["java"]["A_missing"], key=lambda r: r["rel"])
    b_rows = sorted(plan["java"]["B_merge"], key=lambda r: -(r["diff_lines"] or 0))
    lines = []
    lines.append("# 26.1.2 线移植 mapping（1.5.0-rc.7 → `neoforge-26.1.2`）\n")
    lines.append("> 本文件由 `python3 tools/port_26_12.py doc --plan temp/p1/plan.json` 生成，"
                 "`plan` 的数字来自实读；**状态列手工回填**，每波完成后更新。\n")
    lines.append("## 1. 版本矩阵\n")
    lines.append("| 项 | neoforge-1.21.1（源） | neoforge-26.1.2（目标） |\n|---|---|---|")
    import re as _re
    def prop(line, key):
        path = os.path.join(ROOT, line, "gradle.properties")
        if not os.path.exists(path):
            return "?"
        m = _re.search(rf"^{key}\s*=\s*(.+)$", open(path, encoding="utf-8").read(), _re.M)
        return m.group(1).strip() if m else "?"
    for key, alt in (("mod_version", None), ("starengine_version", None),
                     ("minecraft_version", None), ("neo_version", "neoforge_version")):
        a = prop(OLD, key) if prop(OLD, key) != "?" else prop(OLD, alt or key)
        b = prop(NEW, key) if prop(NEW, key) != "?" else prop(NEW, alt or key)
        lines.append(f"| `{key}` | `{a}` | `{b}` |")
    lines.append(f"| java 文件 | 411（A {t['A_files']} 缺 / B {t['B_files']} 需并 / "
                 f"C1 {t['C1_files']} 纯适配 / C0 {t['C0_files']} 相同） | "
                 f"{len(walk(os.path.join(ROOT, NEW, SRC), '.java'))} |")
    lines.append("\n## 2. 文件分类总账\n")
    lines.append(table([
        ["A 缺失（整文件移植）", t["A_files"], t["A_lines"], "P2–P5"],
        ["B 需增量合并", t["B_files"], t["B_diff_lines"], "P2–P5"],
        ["C1 纯适配（不迁移）", t["C1_files"], "-", "-"],
        ["C0 完全一致（不动）", t["C0_files"], "-", "-"],
        ["26.1.2 独有", len(plan["java"]["unique_new"]), "-", "-"],
        ["资源缺失", t["res_missing"], "-", "P6"],
    ], ["类别", "文件数", "行数", "波次"]))
    lines.append("\n## 3. API 差异字典\n")
    lines.append("机械规则来自 96 组「同文件两侧仅因适配而不同」的对拍，`tools/port_26_12.py codemod` "
                 "按此执行；未覆盖的高危符号进人工清单。\n")
    lines.append(API_DICT)
    lines.append("\n## 4. A 类：缺失文件（整文件移植）\n")
    lines.append(table([[wave_of(r["rel"]), r["rel"], r["lines"], ""] for r in a_rows],
                       ["波次", "相对 `src/main/java` 路径", "行数", "状态"]))
    lines.append("\n## 5. B 类：需增量合并（按待并行数排序）\n")
    lines.append("合并方式：`base` = 该文件在 26.1.2 侧最后一次被改动那次提交里的 **1.21.1 版本**；"
                 "`git show <base>:neoforge-1.21.1/src/main/java/<rel>` 取 base，"
                 "与两侧当前内容做三路合并，冲突人工确认。\n")
    lines.append(table([[wave_of(r["rel"]), r["rel"], r["diff_lines"] or "?",
                         (r["base"] or "-")[:8], ""] for r in b_rows],
                       ["波次", "相对路径", "待并行数", "base", "状态"]))
    lines.append("\n## 6. 26.1.2 独有文件（不迁移，逐条确认保留理由）\n")
    lines.append(table([[r, ""] for r in plan["java"]["unique_new"]], ["相对路径", "保留理由"]))
    lines.append("\n## 7. 注册表对账\n")
    for key, val in reg.items():
        if val["old"] is None or val["new"] is None:
            lines.append(f"- **{key}**：文件缺失（old/new 至少一侧不存在）")
            continue
        lines.append(f"- **{key}**：1.21.1 {len(val['old'])} → 26.1.2 {len(val['new'])}；"
                     f"缺 {len(val['missing'])}，多 {len(val['extra'])}")
        if val["missing"]:
            lines.append(f"  - 缺：{', '.join('`' + x + '`' for x in val['missing'])}")
        if val["extra"]:
            lines.append(f"  - 多：{', '.join('`' + x + '`' for x in val['extra'])}")
    lines.append("\n## 8. 资源与 lang\n")
    groups = plan["resources"]["missing_groups"]
    lines.append(table([[k, v] for k, v in sorted(groups.items(), key=lambda kv: -kv[1])],
                       ["资源目录", "缺失数"]))
    for name, val in plan["lang"].items():
        if val:
            lines.append(f"\n- `{name}.json`：1.21.1 {val['old_keys']} 键 → 26.1.2 {val['new_keys']} 键，"
                         f"缺 {len(val['missing'])}，多 {len(val['extra'])}")
    lines.append("\n## 9. 波次与门禁\n")
    lines.append(table([
        ["P0", "基线快照（本文件 §1/§2）", "0", "已完成"],
        ["P1", "`tools/port_26_12.py` + `tools/check_lang_sync.py` + 本文件", "2 新文件", "已完成"],
        ["P2", "地基：ModAttachments/ModDataComponents/ModEffects/ModItems/ModPayloads/ModCreativeTabs + 本地 PartyRelations·EliteTargets 回沉", "~3k 行", "待做"],
        ["P3", "系统层：event/network/combat/world/trade/command/recipe", "~5k 行", "待做"],
        ["P4", "商店与界面：shop/screen/client/block/entity/mixin/compat/choice", "~13k 行", "待做"],
        ["P5", "物品层：item/sign、item/chip、item/card、item", "~4.5k 行", "待做"],
        ["P6", "资源与 datagen（需授权）", "279 + 253×3", "待做"],
        ["P7", "门禁：编译 + 静态门禁 + 实机（需授权）+ 文档", "-", "待做"],
    ], ["波", "内容", "规模", "状态"]))
    lines.append("\n每波出口：`JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 "
                 "./gradlew :neoforge-26.1.2:classes` → `BUILD SUCCESSFUL`，"
                 "且 `git status` 只出现本波预期文件。\n")
    lines.append("\n## 10. 回滚点\n")
    lines.append("每波一个提交（若获授权）；失败 `git reset --hard <上一波>`。回滚点登记：\n")
    lines.append("| 波 | 提交 | 备注 |\n|---|---|---|\n| P1 | （待提交） | |")
    lines.append("")
    out = "\n".join(lines)
    with open(os.path.join(ROOT, args.out), "w", encoding="utf-8") as fh:
        fh.write(out)
    print(f"== 写出 {args.out}（{len(out.splitlines())} 行 / {len(out)} 字符）")


def apply_codemod_text(text):
    hits = []
    for name, pat, repl in CODEMOD_RULES:
        text, n = re.subn(pat, repl, text)
        if n:
            hits.append((name, n))
    return text, hits


def cmd_merge(args):
    rows, _ = classify_java()
    want = set(args.rel or [])
    picked = []
    for r in rows:
        if r["kind"] not in ("A", "B"):
            continue
        if want and r["rel"] not in want:
            continue
        if not want and args.wave and wave_of(r["rel"]) != args.wave:
            continue
        picked.append(r)
    conflicts_total = 0
    for r in picked:
        rel = r["rel"]
        old_path = os.path.join(ROOT, OLD, SRC, rel)
        new_path = os.path.join(ROOT, NEW, SRC, rel)
        os.makedirs(os.path.dirname(new_path), exist_ok=True)
        theirs = open(old_path, encoding="utf-8", errors="replace").read()
        if r["kind"] == "A":
            merged, src = theirs, "copied"
        else:
            base = show(r["base"], f"{OLD}/{SRC}/{rel}") or ""
            ours = open(new_path, encoding="utf-8", errors="replace").read()
            tmp = tempfile.mkdtemp(prefix="mrgsrc-")
            po = os.path.join(tmp, "ours.java")
            pb = os.path.join(tmp, "base.java")
            pt = os.path.join(tmp, "theirs.java")
            for path, text in ((po, ours), (pb, base), (pt, theirs)):
                with open(path, "w", encoding="utf-8", newline="") as fh:
                    fh.write(text)
            p = subprocess.run(["git", "merge-file", "-p", "--diff3",
                                "-L", "ours(26.1.2)", "-L", "base", "-L", "theirs(1.21.1)",
                                po, pb, pt], capture_output=True, text=True)
            merged = p.stdout
            src = f"merged(conflicts={merged.count('<<<<<<<')})"
            conflicts_total += merged.count("<<<<<<<")
            shutil.rmtree(tmp, ignore_errors=True)
        merged, hits = apply_codemod_text(merged)
        if args.apply:
            with open(new_path, "w", encoding="utf-8", newline="") as fh:
                fh.write(merged)
        print(f"   {src:28s} {rel}  codemod={hits if hits else '-'}")
    print(f"== {len(picked)} 个文件，冲突段 {conflicts_total}，{'已写入' if args.apply else 'dry-run'}")


def cmd_plan(args):
    rows, unique = classify_java()
    missing, extra, groups = res_diff()
    langs = lang_diff()
    by_kind = defaultdict(list)
    for r in rows:
        by_kind[r["kind"]].append(r)

    payload = {
        "old": OLD, "new": NEW,
        "java": {
            "A_missing": by_kind["A"],
            "B_merge": by_kind["B"],
            "C1_adapt": by_kind["C1"],
            "C0_identical": by_kind["C0"],
            "unique_new": unique,
        },
        "resources": {"missing": missing, "extra": extra,
                      "missing_groups": {k: v[0] for k, v in groups.items()}},
        "lang": langs,
        "totals": {
            "A_files": len(by_kind["A"]), "A_lines": sum(r["lines"] for r in by_kind["A"]),
            "B_files": len(by_kind["B"]),
            "B_diff_lines": sum(r["diff_lines"] or 0 for r in by_kind["B"]),
            "C1_files": len(by_kind["C1"]), "C0_files": len(by_kind["C0"]),
            "res_missing": len(missing), "res_extra": len(extra),
        },
    }
    if args.json:
        os.makedirs(os.path.dirname(os.path.join(ROOT, args.json)), exist_ok=True)
        with open(os.path.join(ROOT, args.json), "w", encoding="utf-8") as fh:
            json.dump(payload, fh, ensure_ascii=False, indent=1)

    t = payload["totals"]
    print(f"== java: A(缺失)={t['A_files']} 文件 / {t['A_lines']} 行; "
          f"B(需合并)={t['B_files']} 文件 / {t['B_diff_lines']} 行待并; "
          f"C1(纯适配)={t['C1_files']}; C0(完全一致)={t['C0_files']}; 26.1.2 独有={len(unique)}")
    print(f"== resources: 缺 {t['res_missing']} / 多 {t['res_extra']}")
    for key, val in sorted(groups.items(), key=lambda kv: -kv[1][0])[:12]:
        print(f"   {val[0]:5d}  {key}")
    for name, val in langs.items():
        if val:
            print(f"== lang {name}: old={val['old_keys']} new={val['new_keys']} "
                  f"缺 {len(val['missing'])} 多 {len(val['extra'])}")

    print("\n== A 类按包（前 25）")
    pk = defaultdict(lambda: [0, 0])
    for r in by_kind["A"]:
        pkg = "/".join(r["rel"].split("/")[:-1]) or "(root)"
        pk[pkg][0] += 1
        pk[pkg][1] += r["lines"]
    for pkg, (n, lines) in sorted(pk.items(), key=lambda kv: -kv[1][1])[:25]:
        print(f"   {n:3d} 文件 {lines:6d} 行  {pkg}")

    print("\n== B 类（按待并行数排序，前 30）")
    for r in sorted(by_kind["B"], key=lambda r: -(r["diff_lines"] or 0))[:30]:
        print(f"   {r['diff_lines'] or -1:5d} 行待并  {r['rel']}  base={(r['base'] or '-')[:8]}")


def cmd_regs(args):
    data = regs()
    for key, val in data.items():
        if val["old"] is None or val["new"] is None:
            print(f"== {key}: 文件缺失 old={val['old'] is not None} new={val['new'] is not None}")
            continue
        print(f"== {key}: old={len(val['old'])} new={len(val['new'])} "
              f"缺 {len(val['missing'])} 多 {len(val['extra'])}")
        if val["missing"]:
            print("   缺: " + ", ".join(val["missing"]))
        if val["extra"]:
            print("   多: " + ", ".join(val["extra"]))


def cmd_codemod(args):
    only = set(args.only.split(",")) if args.only else None
    rep = codemod(apply=args.apply, only=only)
    total = 0
    for r in rep:
        hits = ", ".join(f"{n}×{c}" for n, c in r["hits"])
        total += sum(c for _, c in r["hits"])
        print(f"   {r['rel']}: {hits}")
    print(f"== {len(rep)} 个文件 / {total} 处替换 {'已写入' if args.apply else '(dry-run，未写盘)'}")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("plan", help="文件三分类 + 资源/lang 差集")
    p.add_argument("--json", default="")
    p.set_defaults(func=cmd_plan)

    p = sub.add_parser("regs", help="注册表对账")
    p.set_defaults(func=cmd_regs)

    p = sub.add_parser("merge", help="A 复制 / B 三路合并（+codemod）")
    p.add_argument("--wave", default="")
    p.add_argument("--rel", action="append", default=None)
    p.add_argument("--apply", action="store_true")
    p.set_defaults(func=cmd_merge)

    p = sub.add_parser("doc", help="生成 mapping 文档")
    p.add_argument("--plan", default="temp/p1/plan.json")
    p.add_argument("--out", default="docs/migration-26.1.2-rc7-mapping.md")
    p.set_defaults(func=cmd_doc)

    p = sub.add_parser("codemod", help="API 机械替换")
    p.add_argument("--apply", action="store_true")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--only", default="")
    p.set_defaults(func=cmd_codemod)

    args = ap.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
