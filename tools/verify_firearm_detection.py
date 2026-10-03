"""守卫:枪械/枪弹判定常量与证据表的一致性。

用法:
    python tools/verify_firearm_detection.py              # 0 = 通过, 1 = 有漂移
    python tools/verify_firearm_detection.py --jars temp/t04/mods
                                                          # 额外回查实物 jar(强烈建议)

四道判据:
  ① 四线(`neoforge-1.21.1`/`forge-1.20.1`/`neoforge-26.1.2`/`fabric-1.20.1`)常量**逐项同构**;
  ② Java 常量与 `tools/firearm-detection-evidence.json` **一一对应**(两个方向都比);
  ③ 近战判定的三条硬约束(不再排除工具 / 已调用 isFirearmItem / 仍排除 ProjectileWeaponItem);
  ④ **实物回查(默认强制)** —— 在真实 jar 里验证三件事:
     a. 每个伤害类型 key 都能找到 `data/<ns>/damage_type/<path>.json`;
     b. 每条**弹丸包名前缀**都真的存在对应的包(至少一个 `.class`);
     c. 证据 JSON 的 `projectileClasses` 的 FQCN 前缀落在上面某条包名前缀之内。
     ⚠️ 这条才拦得住「四线 Java 与证据表**一起**改错」的假绿(判据 ①②③ 只做三方自洽比对)。
     jar 目录缺失时**默认判 FAIL**;确实要跳过(如干净检出)必须显式 `--no-jars`。

为什么需要它:枪弹判定表是**逐条从第三方实物 jar 取证**得到的;一旦与证据表脱钩(或四线之间
不同步),就会静默退化成「部分模组漏判」。本脚本把这条约定机械化。
"""
import io
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2", "fabric-1.20.1"]
PKG = "src/main/java/com/merlinkitsune/astral_dice/"
EVIDENCE = os.path.join(ROOT, "tools", "firearm-detection-evidence.json")

fails = []
notes = []


def read(p):
    with open(p, "rb") as fh:
        return fh.read().decode("utf-8")


def grab(text, name, opener):
    m = re.search(re.escape(name) + r"\s*=\s*" + re.escape(opener) + r"\((.*?)\);", text, re.S)
    return m.group(1) if m else None


def strings(block):
    return re.findall(r'"([^"]*)"', block or "")


def keys(block):
    return ["%s:%s" % (a, b) for a, b in re.findall(r'key\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)', block or "")]


# ------------------------------------------------------------------ ① 解析四线
data = {}
for ln in LINES:
    dice = read(os.path.join(ROOT, ln, PKG, "combat", "DiceCombatEvents.java"))
    spell = read(os.path.join(ROOT, ln, PKG, "combat", "SpellDamageRegistry.java"))
    blk = {
        "namespaces": grab(dice, "FIREARM_ITEM_NAMESPACES", "java.util.Set.of"),
        "itemIds": grab(dice, "FIREARM_ITEM_IDS", "java.util.Set.of"),
        "damageTypes": grab(spell, "FIREARM_DAMAGE_TYPES", "List.of"),
        "packages": grab(spell, "FIREARM_PROJECTILE_PACKAGES", "List.of"),
    }
    for tag, raw in blk.items():
        if raw is None:
            fails.append("%s: 未找到常量 %s(grab 返回 None)" % (ln, tag))
    data[ln] = {
        "namespaces": sorted(strings(blk["namespaces"])),
        "itemIds": sorted(strings(blk["itemIds"])),
        "damageTypes": sorted(keys(blk["damageTypes"])),
        "packages": sorted(strings(blk["packages"])),
        "hasIsFirearmItem": "public static boolean isFirearmItem(ItemStack stack)" in dice,
        # ---- 2026-10-03 第二批:近战判定**回退为黑名单** + 两口「饕餮之锅」显式纳入 ----
        "meleeChecksEmptyHand": "if (held.isEmpty()) return false;" in dice,
        "meleeChecksShield": "held.is(Items.SHIELD)" in dice,
        "meleeChecksProjectileWeapon": "ProjectileWeaponItem) return false;" in dice,
        "meleeChecksBlock": "BlockItem) return false;" in dice,
        "meleeChecksFirearm": "if (isFirearmItem(held)) return false;" in dice,
        # 黑名单收尾:排除完毕后无条件放行(不再有任何「必须是武器/工具」的收口)
        "meleeReturnsTrue": "        return true;" in dice,
        # 显式纳入清单(两口锅)—— id 必须逐字来自实物 jar
        "meleeHasIncludeList": "MELEE_WEAPON_EXTRA_INCLUDES" in dice,
        "meleeHasIncludeHelper": "private static boolean isExplicitMeleeWeapon(ItemStack held)" in dice,
        "meleeChecksInclude": "if (isExplicitMeleeWeapon(held)) return true;" in dice,
        "meleeIncludesEldritchPan": '"enigmaticlegacy:eldritch_pan"' in dice,
        "meleeIncludesVoraciousPan": '"enigmaticdelicacy:voracious_pan"' in dice,
        # 非武器工具(剪刀/钓竿/打火石/刷子)—— 2026-10-03 同日二版**重新纳入黑名单**(用户裁决)
        "meleeChecksNonCombatTools": all(
            s in dice for s in ("held.is(Items.SHEARS)", "held.is(Items.FISHING_ROD)",
                                "held.is(Items.FLINT_AND_STEEL)", "held.is(Items.BRUSH)")),
        # 白名单形态必须彻底消失(否则宁可报错也不要静默留下半套逻辑)
        "meleeWhitelistHelperLeft": "isWeaponOrTool" in dice,
        "meleeNonCombatToolsConstantLeft": "VANILLA_NON_COMBAT_TOOLS" in dice,
        # 顺序判据要用原文(不参与上面按 key 的同构比对)
        "_dice": dice,
    }

base = data[LINES[0]]
for ln in LINES[1:]:
    for k in ("namespaces", "itemIds", "damageTypes", "packages"):
        if data[ln][k] != base[k]:
            fails.append("四线不同构: %s 的 %s 与 %s 不一致" % (ln, k, LINES[0]))

# --------------------------------------------------- ③ 近战判定的硬约束
# ⚠️ 移植线豁免（**仅限本批新增的「非武器工具」判据**）：按 AGENTS 第 221 行 ①，`fabric-1.20.1`
#    不参与三线的「同批实施」—— 这四件是否重新纳入黑名单由用户单独下达移植批次。
#    此处**记为 SKIP 并打印在摘要里**，不判 FAIL（判据不许模糊回退：SKIP 必须有存在感，
#    不能靠静默通过）。
TRANSPLANT_SKIPS = {"fabric-1.20.1": {"meleeChecksNonCombatTools"}}
skips = []

for ln in LINES:
    d = data[ln]
    if not d["hasIsFirearmItem"]:
        fails.append("%s: 缺少 isFirearmItem(ItemStack)" % ln)
    if d["meleeWhitelistHelperLeft"]:
        fails.append("%s: 近战判定仍残留 isWeaponOrTool(白名单形态未清理干净)" % ln)
    if d["meleeNonCombatToolsConstantLeft"]:
        fails.append("%s: 近战判定仍残留 VANILLA_NON_COMBAT_TOOLS(白名单批次的命名常量 —— "
                     "黑名单形态应改用 Items.SHEARS 等直接判定)" % ln)
    for flag, desc in (("meleeChecksEmptyHand", "未排除空手"),
                       ("meleeChecksShield", "未排除盾牌"),
                       ("meleeChecksProjectileWeapon", "未排除 ProjectileWeaponItem(弓/弩/弹弓)"),
                       ("meleeChecksBlock", "未排除方块"),
                       ("meleeChecksFirearm", "未调用 isFirearmItem(枪械本体应被排除)"),
                       ("meleeChecksNonCombatTools", "未排除非武器工具(剪刀 / 钓竿 / 打火石 / 刷子 —— "
                        "2026-10-03 同日二版按用户裁决重新纳入黑名单)"),
                       ("meleeReturnsTrue", "未以无条件 `return true;` 收尾(黑名单模式未生效)"),
                       ("meleeHasIncludeList", "缺少 MELEE_WEAPON_EXTRA_INCLUDES 显式纳入清单"),
                       ("meleeHasIncludeHelper", "缺少 isExplicitMeleeWeapon 辅助方法"),
                       ("meleeChecksInclude", "isMeleeWeaponAttack 未先查显式纳入清单"),
                       ("meleeIncludesEldritchPan", "显式纳入清单缺 enigmaticlegacy:eldritch_pan(1.20.1 饕餮之锅)"),
                       ("meleeIncludesVoraciousPan", "显式纳入清单缺 enigmaticdelicacy:voracious_pan(1.21.1 饕餮之锅)")):
        if not d[flag]:
            if flag in TRANSPLANT_SKIPS.get(ln, ()):
                skips.append("%s: %s（移植线滞后，待用户下达批次）" % (ln, desc))
                continue
            fails.append("%s: %s" % (ln, desc))
    # ★ 顺序判据:显式纳入必须**先于** BlockItem 排除 —— 1.21.1 的饕餮之锅正是 BlockItem,
    #   顺序写反 = 该锅被排除,而上面所有「存在性」断言仍会通过(典型的假绿)。
    try:
        i_inc = d["_dice"].index("if (isExplicitMeleeWeapon(held)) return true;")
        i_blk = d["_dice"].index("BlockItem) return false;")
        if i_inc > i_blk:
            fails.append("%s: 显式纳入判定写在 BlockItem 排除之后 ⇒ 方块化的饕餮之锅仍会被排除" % ln)
        # ★ 同理:显式纳入也必须先于「非武器工具」排除 —— 否则将来某模组的合法武器若恰好是
        #   剪刀/钓竿/打火石/刷子的同 id 或子类,会被这一条静默杀掉,而存在性断言照样全绿。
        if "meleeChecksNonCombatTools" in TRANSPLANT_SKIPS.get(ln, ()):
            pass                       # 该线未纳入本批 ⇒ 顺序判据随之豁免（已在 skips 里登记）
        else:
            i_tools = d["_dice"].index("held.is(Items.SHEARS)")
            if i_inc > i_tools:
                fails.append("%s: 「非武器工具」排除写在显式纳入之后面（顺序写反）⇒ 同名合法武器无法被清单救回" % ln)
    except (ValueError, KeyError) as e:
        fails.append("%s: 无法校验近战排除项的顺序判据: %s" % (ln, e))
    if not d["damageTypes"]:
        fails.append("%s: FIREARM_DAMAGE_TYPES 为空(解析失败?)" % ln)
    if not d["namespaces"]:
        fails.append("%s: FIREARM_ITEM_NAMESPACES 为空(解析失败?)" % ln)

# ------------------------------------------------------ ② 与证据表比对
with open(EVIDENCE, encoding="utf-8") as fh:
    ev = json.load(fh)

ev_keys = sorted({t["key"] for m in ev["mods"] for t in m["damageTypes"]})
if ev_keys != base["damageTypes"]:
    fails.append("伤害类型表与证据 JSON 不一致: Java 独有 %s / JSON 独有 %s"
                 % (sorted(set(base["damageTypes"]) - set(ev_keys)),
                    sorted(set(ev_keys) - set(base["damageTypes"]))))
if sorted(ev["namespaceWideExclusion"]) != base["namespaces"]:
    fails.append("命名空间排除表与证据 JSON 不一致: Java %s / JSON %s"
                 % (base["namespaces"], sorted(ev["namespaceWideExclusion"])))
if sorted(ev["itemIdExclusion"]) != base["itemIds"]:
    fails.append("物品 id 排除表与证据 JSON 不一致: Java %s / JSON %s"
                 % (base["itemIds"], sorted(ev["itemIdExclusion"])))
if sorted(ev["projectilePackagePrefixes"]) != base["packages"]:
    fails.append("弹丸包名前缀与证据 JSON 不一致: Java %s / JSON %s"
                 % (base["packages"], sorted(ev["projectilePackagePrefixes"])))

for m in ev["mods"]:
    if not m.get("artifact"):
        fails.append("证据表缺 artifact: %s" % m.get("label"))
    for t in m["damageTypes"]:
        if not t.get("messageId"):
            fails.append("证据表缺 messageId: %s" % t.get("key"))

# ------------------------------------------- ④ 实物回查(默认强制,拦假绿)
allow_no_jars = "--no-jars" in sys.argv
jar_dir = None
if "--jars" in sys.argv:
    jar_dir = sys.argv[sys.argv.index("--jars") + 1]
else:
    cand = os.path.join(ROOT, "temp", "t04", "mods")
    if os.path.isdir(cand):
        jar_dir = cand

if jar_dir and os.path.isdir(jar_dir):
    present = set()       # data/<ns>/damage_type/<path>
    class_paths = set()   # 全部 class 条目路径(含内嵌 jar)
    roots = [os.path.join(jar_dir, f) for f in sorted(os.listdir(jar_dir)) if f.endswith(".jar")]
    nested_dir = os.path.join(os.path.dirname(jar_dir), "_nested")
    if os.path.isdir(nested_dir):
        roots += [os.path.join(nested_dir, f)
                  for f in sorted(os.listdir(nested_dir)) if f.endswith(".jar")]

    def harvest(names, read):
        for n in names:
            if n.endswith(".class"):
                class_paths.add(n)
            m = re.match(r"data/([^/]+)/damage_type/(.+)\.json$", n)
            if m:
                present.add("%s:%s" % (m.group(1), m.group(2)))
            if n.endswith(".jar"):
                try:
                    with zipfile.ZipFile(io.BytesIO(read(n))) as nz:
                        harvest(nz.namelist(), nz.read)
                except Exception:
                    pass

    for jp in roots:
        try:
            with zipfile.ZipFile(jp) as z:
                harvest(z.namelist(), z.read)
        except Exception as e:
            notes.append("跳过 %s: %s" % (os.path.basename(jp), e))

    if not present:
        fails.append("实物 jar 目录 %s 存在却未解析出任何 damage_type —— 判据 ④ 不可评估，拒绝通过" % jar_dir)
    else:
        for k in base["damageTypes"]:
            if k not in present:
                fails.append("实物 jar 中找不到该伤害类型: %s" % k)
        # b. 包名前缀必须在实物里真实存在
        for pkg in base["packages"]:
            prefix = pkg.replace(".", "/")
            if not any(c.startswith(prefix) for c in class_paths):
                fails.append("实物 jar 中找不到该弹丸包: %s" % pkg)
        # c. 证据表里的弹丸类 FQCN 必须落在某条包名前缀内
        for m in ev["mods"]:
            for pc in m.get("projectileClasses", []):
                fqcn = pc.split(" (")[0].strip()
                if not any(fqcn.startswith(pk) for pk in base["packages"]):
                    fails.append("证据表 projectileClasses 不落在任何包名前缀内: %s" % fqcn)
        notes.append("实物回查: damage_type=%d, class 条目=%d;四线 %d 个 key 全命中=%s;%d 条包名前缀全存在=%s"
                     % (len(present), len(class_paths), len(base["damageTypes"]),
                        all(k in present for k in base["damageTypes"]),
                        len(base["packages"]),
                        all(any(c.startswith(pk.replace(".", "/")) for c in class_paths)
                            for pk in base["packages"])))
elif allow_no_jars:
    notes.append("已显式 --no-jars ⇒ **仅**执行判据 ①②③(三方自洽比对);"
                 "⚠️ 该模式**拦不住**「四线 Java 与证据表一起改错」的漂移,结论强度下降")
else:
    fails.append("未提供实物 jar(既无 --jars,默认目录 %s 也不存在)⇒ 判据 ④ 无法执行。"
                 "证据不足,拒绝通过。确实要跳过请显式加 --no-jars(会降低强度)"
                 % os.path.join(ROOT, "temp", "t04", "mods"))

# ------------------------------------------------------------------ 输出
print("FIREARM-DETECTION  verify")
print("  lines                : %s" % ", ".join(LINES))
print("  namespaces(%d)        : %s" % (len(base["namespaces"]), base["namespaces"]))
print("  itemIds(%d)           : %s" % (len(base["itemIds"]), base["itemIds"]))
print("  damageTypes(%d)       : %s" % (len(base["damageTypes"]), base["damageTypes"]))
print("  projectilePackages(%d): %s" % (len(base["packages"]), base["packages"]))
print("  evidence mods        : %d" % len(ev["mods"]))
for n in notes:
    print("  note: " + n)
for s in skips:
    print("  skip: " + s)
if fails:
    print("\nFAIL (%d)" % len(fails))
    for f in fails:
        print("  - " + f)
    sys.exit(1)
print("\nPASS — 四线同构,与 tools/firearm-detection-evidence.json 逐项一致")
