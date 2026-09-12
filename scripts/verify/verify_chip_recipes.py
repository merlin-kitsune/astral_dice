# -*- coding: utf-8 -*-
"""筹码配方统一规范 — 三档校验(源码 / 生成资源 / jar)。

用法(仓库根目录执行):
    python scripts/verify/verify_chip_recipes.py              # 三档 × 双版本全跑
    python scripts/verify/verify_chip_recipes.py java neo      # 只校验 neo 源码
    python scripts/verify/verify_chip_recipes.py gen           # 双版本生成资源
    python scripts/verify/verify_chip_recipes.py jar forge     # 只校验 forge jar

三档各管什么(必须都跑过才算真正验证,不能只看 dataGen 日志的 written: N):
  java  — 解析 ModRecipeProvider.java,与目标配方逐格比对(及时发现漏改/写错)
  gen   — 解析 dataGen 产物 json,确认「Provider 改对了」且「生成真的落到了文件」
  jar   — 开 jar 读 data/.../recipe{,s}/*.json,确认打包产物里就是新配方

退出码 0 = 全绿;1 = 有不一致。

要点(踩过的坑):
  * 符号->材料 item id 必须独立构造,不能从 chips 表取(材料不是筹码)。
  * 药水原料:neo 为无 item 键的 components ingredient;forge 为带 type+nbt 的
    partial_nbt 且含 item=potion —— 两者都归约为 POTION_EQUIV 再比对。
  * 1.20.1 无 1.21 新增物品须走 chip_common.PLATFORM_OVERRIDE(如 MACE->ANVIL),
    并同步登记到 docs/compat-1.20.1-forge.md,不得强行统一成 1.21 物品。
"""
import io
import json
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chip_common as CC  # noqa: E402

ROOT = CC.ROOT
SUB = {"neo": "neoforge-1.21.1", "forge": "forge-1.20.1"}
RECDIR = {"neo": "recipe", "forge": "recipes"}
ADVDIR = {"neo": "advancement", "forge": "advancements"}
VER = "1.2.0"
JAR = {"neo": "astral_dice-%s+neoforge_1.21.1.jar" % VER,
       "forge": "astral_dice-%s+forge_1.20.1.jar" % VER}

# 非筹码的模组常量 -> 物品 id
EXTRA_MOD = {"DICE": "dice", "STAR_COIN_BAG": "star_coin_bag",
             "ORBITAL_STRIKE_CARD": "effect_card_orbital_strike"}
# 符号 -> 材料物品 id(材料不在 chips 表内,直接由 ModItems 常量名推导)
SYM2ITEM = {s: "astral_dice:" + c.lower() for s, c in CC.SYM_CONST.items()}


def expected_cell(tok):
    if tok.startswith("MC:"):
        return "minecraft:" + tok[3:].lower()
    if tok.startswith("MOD:"):
        name = tok[4:]
        if name in EXTRA_MOD:
            return "astral_dice:" + EXTRA_MOD[name]
        info = CC.chips.get(name)
        return "astral_dice:" + info["id"] if info else None
    if tok.startswith("POTION"):
        return "POTION_EQUIV"
    return SYM2ITEM.get(tok)


def expected(dialect, const):
    return [[expected_cell(t) for t in row]
            for row in CC._plat(CC.target(const), dialect)]


def diff3(exp, got):
    bad = []
    if len(got) != 3 or any(len(r) != 3 for r in got):
        return [("shape", "非 3x3: %r" % (got,))]
    for r in range(3):
        for c in range(3):
            if exp[r][c] != got[r][c]:
                bad.append((r, c, exp[r][c], got[r][c]))
    return bad


# ---- 档 1:源码 ----
def check_java(dialect):
    path = os.path.join(ROOT, SUB[dialect], "src", "main", "java", "com",
                        "merlinkitsune", "astral_dice", "datagen", "ModRecipeProvider.java")
    parsed = CC.parse_file(path)
    return parsed, path


# ---- 档 2:生成资源 ----
def jar_json(dialect):
    """返回 {relative_name: raw} 供 gen / jar 两档共用"""
    d = os.path.join(ROOT, SUB[dialect], "src", "generated", "resources", "data",
                     "astral_dice", RECDIR[dialect])
    out = {}
    for f in os.listdir(d):
        if f.endswith(".json"):
            out[f] = io.open(os.path.join(d, f), encoding="utf-8").read()
    return out


def check_jar(dialect):
    p = os.path.join(ROOT, "build", "libs", JAR[dialect])
    z = zipfile.ZipFile(p)
    pre = "data/astral_dice/%s/" % RECDIR[dialect]
    return {n[len(pre):]: z.read(n).decode("utf-8")
            for n in z.namelist() if n.startswith(pre) and n.endswith(".json")}


def cells_from_json(raw):
    j = json.loads(raw)
    pat, key = j.get("pattern", []), j.get("key", {})
    out = []
    for row in pat:
        line = []
        for ch in row:
            if ch == " ":
                line.append(None)
                continue
            cell = key.get(ch, {})
            # 药水:neo 无 item 键;forge 带 type+nbt 且 item=potion
            if "item" in cell and not ("type" in cell and cell["item"] == "minecraft:potion"):
                line.append(cell["item"])
            else:
                line.append("POTION_EQUIV")
        out.append(line)
    return out


def run(dialect, modes):
    print("== %s (%s) ==" % (dialect, SUB[dialect]))
    total = len(CC.chips)
    failures = 0
    if "java" in modes:
        parsed, path = check_java(dialect)
        ok, bad, miss = 0, [], []
        for const in CC.chips:
            if const not in parsed:
                miss.append(const)
                continue
            # 源码档在 token 空间比对(parse_file 还原出的就是目标表的 token 形式)
            exp = CC._plat(CC.target(const), dialect)
            d = diff3(exp, parsed[const])
            bad.append((const, d)) if d else None
            ok += 0 if d else 1
        failures += len(bad) + len(miss)
        print("  [java] %s" % os.path.relpath(path, ROOT))
        print("  [java] 筹码 %d | 一致 %d | 不一致 %d | 缺配方 %d" % (total, ok, len(bad), len(miss)))
        for c, d in bad[:10]:
            print("        ✗ %s %s" % (c, d))
        if miss:
            print("        缺: %s" % miss)
    for mode, loader in (("gen", jar_json), ("jar", check_jar)):
        if mode not in modes:
            continue
        src = loader(dialect)
        ok, bad, miss = 0, [], []
        for const, info in CC.chips.items():
            name = info["id"] + ".json"
            if name not in src:
                miss.append(const)
                continue
            d = diff3(expected(dialect, const), cells_from_json(src[name]))
            bad.append((const, d)) if d else None
            ok += 0 if d else 1
        failures += len(bad) + len(miss)
        print("  [%s] 配方文件 %d | 筹码 %d | 一致 %d | 不一致 %d | 缺文件 %d"
              % (mode, len(src), total, ok, len(bad), len(miss)))
        for c, d in bad[:10]:
            print("        ✗ %s %s" % (c, d))
        if miss:
            print("        缺: %s" % miss)
    print("")
    return failures


def main():
    args = [a for a in sys.argv[1:]]
    modes = [a for a in args if a in ("java", "gen", "jar")] or ["java", "gen", "jar"]
    dialects = [a for a in args if a in ("neo", "forge")] or ["neo", "forge"]
    fails = 0
    for d in dialects:
        fails += run(d, modes)
    if fails:
        print("RESULT: %d 处不一致" % fails)
    else:
        print("RESULT: ALL OK")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
