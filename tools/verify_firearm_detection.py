"""守卫:枪械/枪弹判定常量与证据表的一致性。

用法:
    python tools/verify_firearm_detection.py              # 0 = 通过, 1 = 有漂移
    python tools/verify_firearm_detection.py --jars temp/t04/mods
                                                          # 额外回查实物 jar(强烈建议)

四道判据:
  ① 四线(`neoforge-1.21.1`/`forge-1.20.1`/`neoforge-26.1.2`/`fabric-1.20.1`)常量**逐项同构**;
  ② Java 常量与 `tools/firearm-detection-evidence.json` **一一对应**(两个方向都比);
  ③ 近战判定的三条硬约束(不再排除工具 / 已调用 isFirearmItem / 仍排除 ProjectileWeaponItem);
  ④ (可选)每个伤害类型 key 都能在**实物 jar** 里找到对应的
     `data/<ns>/damage_type/<path>.json` —— 这条才拦得住「四线 Java 与证据表一起改错」的假绿。

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
        # 工具排除必须已删除:PICKAXES / SHOVELS / HOES 曾是被排除的三项(axes 从未排除,勿误判)
        "meleeExcludesTools": any(t in dice for t in
                                  ("ItemTags.PICKAXES", "ItemTags.SHOVELS", "ItemTags.HOES")),
        "meleeChecksFirearm": "if (isFirearmItem(held)) return false;" in dice,
        "meleeChecksProjectileWeapon": "ProjectileWeaponItem) return false;" in dice,
        "meleeChecksShield": "held.is(Items.SHIELD)" in dice,
        "meleeChecksBlock": "BlockItem) return false;" in dice,
        "meleeChecksEmptyHand": "if (held.isEmpty()) return false;" in dice,
    }

base = data[LINES[0]]
for ln in LINES[1:]:
    for k in ("namespaces", "itemIds", "damageTypes", "packages"):
        if data[ln][k] != base[k]:
            fails.append("四线不同构: %s 的 %s 与 %s 不一致" % (ln, k, LINES[0]))

# --------------------------------------------------- ③ 近战判定的硬约束
for ln in LINES:
    d = data[ln]
    if not d["hasIsFirearmItem"]:
        fails.append("%s: 缺少 isFirearmItem(ItemStack)" % ln)
    if d["meleeExcludesTools"]:
        fails.append("%s: 近战判定仍在排除工具(ItemTags.PICKAXES/SHOVELS/HOES) —— 与 2026-10-03 裁决相反" % ln)
    for flag, desc in (("meleeChecksFirearm", "未调用 isFirearmItem(枪械本体应被排除)"),
                       ("meleeChecksProjectileWeapon", "未排除 ProjectileWeaponItem(弓/弩/弹弓)"),
                       ("meleeChecksShield", "未排除盾牌"),
                       ("meleeChecksBlock", "未排除方块"),
                       ("meleeChecksEmptyHand", "未排除空手")):
        if not d[flag]:
            fails.append("%s: %s" % (ln, desc))
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

# ------------------------------------------- ④ 可选:回查实物 jar(拦假绿)
jar_dir = None
if "--jars" in sys.argv:
    jar_dir = sys.argv[sys.argv.index("--jars") + 1]
else:
    cand = os.path.join(ROOT, "temp", "t04", "mods")
    if os.path.isdir(cand):
        jar_dir = cand

if jar_dir and os.path.isdir(jar_dir):
    present = set()
    nested = os.path.join(os.path.dirname(jar_dir), "_nested")
    roots = [os.path.join(jar_dir, f) for f in sorted(os.listdir(jar_dir)) if f.endswith(".jar")]
    if os.path.isdir(nested):
        roots += [os.path.join(nested, f) for f in sorted(os.listdir(nested)) if f.endswith(".jar")]
    for jp in roots:
        try:
            with zipfile.ZipFile(jp) as z:
                for n in z.namelist():
                    m = re.match(r"data/([^/]+)/damage_type/(.+)\.json$", n)
                    if m:
                        present.add("%s:%s" % (m.group(1), m.group(2)))
                    if n.endswith(".jar"):  # 内嵌 jar(jar-in-jar)
                        try:
                            with zipfile.ZipFile(io.BytesIO(z.read(n))) as nz:
                                for nn in nz.namelist():
                                    m2 = re.match(r"data/([^/]+)/damage_type/(.+)\.json$", nn)
                                    if m2:
                                        present.add("%s:%s" % (m2.group(1), m2.group(2)))
                        except Exception:
                            pass
        except Exception as e:
            notes.append("跳过 %s: %s" % (os.path.basename(jp), e))
    if not present:
        notes.append("实物 jar 目录存在但未解析出任何 damage_type —— 跳过 jars 回查")
    else:
        missing = [k for k in base["damageTypes"] if k not in present]
        for k in missing:
            fails.append("实物 jar 中找不到该伤害类型: %s" % k)
        notes.append("实物 jar 回查: 已解析 %d 个 damage_type,四线 %d 个 key 全部命中=%s"
                     % (len(present), len(base["damageTypes"]), not missing))
else:
    notes.append("未提供 --jars 且 temp/t04/mods 不存在 ⇒ **未**回查实物 jar(判据 ④ 跳过)")

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
if fails:
    print("\nFAIL (%d)" % len(fails))
    for f in fails:
        print("  - " + f)
    sys.exit(1)
print("\nPASS — 四线同构,与 tools/firearm-detection-evidence.json 逐项一致")
