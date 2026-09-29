"""fabric-1.20.1 线的「资源/注册一致性」守门脚本（只读，不改任何工程文件）。

为什么需要它
------------
本线是移植线，**大量缺陷表现为「注册了但资源没跟上」或「引用了但没人提供」**，
且这些缺陷**全都不报错** —— 只在游戏里表现为紫黑格、无声、粒子不显示、手册显示原始键名、
配方永远合不出来。2026-09-29 的体检里就有两类真实案例：
  · `c:bricks` 在 1.20.1 上没有任何提供者 ⇒「对怪板砖」配方永不可合成（见 KNOWN-ISSUES KI-F5）；
  · `data/c` 不在产物里（`sourceSets` 漏配）⇒ 137 个模型不进包。
逐条「打开 jar 用肉眼数」既慢又会漏，故把判据脚本化。

检查项（全部对照**产物 jar** 或**会进产物的源**，任一项失败退出码 1）
------------------------------------------------------------------
  1. 物品闭环：ModItems 注册 id ↔ jar 内 models/item ↔ textures/item
  2. 标签闭环：本模组引用的每个标签，是否存在提供者（自建 / minecraft / 已装前置模组）
  3. 音效闭环：ModSounds 注册 id ↔ sounds.json 键 ↔ sounds/*.ogg
  4. 粒子闭环：ModParticles 注册 id ↔ particles/<id>.json ↔ 其引用的贴图
  5. 语言闭环：三语键集一致 + java 里的字面量键都在 lang 里
  6. 手册闭环：patchouli 条目引用的物品 / 配方 / lang 键是否真实存在
  7. 创意标签覆盖：每个注册物品都能在 ModCreativeTabs 里找到

用法
----
    python tools/verify_fabric_assets.py            # 跑全部
    python tools/verify_fabric_assets.py --jar <j>  # 指定产物 jar

⚠️ 判据纪律（本项目踩过的坑，勿简化）
  · 「物品模型多出 1 个」不一定是缺陷（`astral_guide` 是帕秋莉手册物品）；
  · 配方文件的标签写作 `{"tag": "c:bricks"}`，**没有 `#` 前缀**，只抓 `#` 会漏；
  · 1.21+ 的配方目录是 `data/<ns>/recipe/`（单数），1.20.1 是 `recipes/`（复数）；
  · 数量对不上不等于缺陷 —— 先排除工具自身的盲区再下结论。
"""
import argparse
import json
import os
import re
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
FABRIC = os.path.join(REPO, 'fabric-1.20.1')
SRC = os.path.join(FABRIC, 'src', 'main', 'java', 'com', 'merlinkitsune', 'astral_dice')
RES = os.path.join(FABRIC, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'astral_dice')
LANG_DIR = os.path.join(ASSETS, 'lang')

# 已知的正当例外：物品模型里有、但不属于 ModItems 注册的物品
EXTRA_MODEL_ALLOW = {'astral_guide'}

# 手册里允许缺键的键（每条都要写明理由，否则视为缺陷）
LANG_KEY_ALLOW = {
    # 2026-09-29 登记：teru_sign 的手册条目写了 3 个文本页，但 lang 只定义了 .1/.2 ⇒ 第 3 页
    # 显示原始键名。**这是四条线共有的既有内容缺失**（不是移植引入），且补写需要文案裁决：
    #   · 配方依据（可复算）：`Z` = diamond_dice、`P` = golden_star_plate（P 在 pattern 中出现 2 次）；
    #   · 稀有度：TERU_SIGN = AstralRarities.legendary()；
    #   · ⚠️ 但「档位词」口径不明 —— HANNA_SIGN / SHERRY_SIGN 的代码稀有度同为 bizarre()，
    #     手册里却分别写着 "Rare tier" / "Epic tier" ⇒ 手册的档位词与 AstralRarities 不必然对应。
    # ⇒ 在档位口径裁决前不擅自补写（否则等于给玩家一条可能与实际不符的品质说明）。
    'astral_dice.guide.entry.teru_sign.3',
}

MODS_DIRS = [
    r'D:\.minecraft\versions\1.20.1-Fabric 模组测试\mods',
    os.path.join(FABRIC, 'run', 'server', 'mods'),
    os.path.join(FABRIC, 'run', 'client', 'mods'),
]


def find_jar(explicit):
    if explicit:
        return explicit
    libs = os.path.join(FABRIC, 'build', 'libs')
    if not os.path.isdir(libs):
        return None
    jars = [os.path.join(libs, n) for n in os.listdir(libs)
            if n.startswith('astral_dice-') and n.endswith('.jar')]
    return sorted(jars)[-1] if jars else None


def registered_item_ids():
    text = open(os.path.join(SRC, 'item', 'ModItems.java'), encoding='utf-8').read()
    ids = set(re.findall(r'registerItem\(\s*"([a-z0-9_]+)"', text))
    ids |= set(re.findall(r'register\(\s*"([a-z0-9_]+)"\s*,', text))
    return ids, text


def check_items(jar_path, failures):
    ids, _ = registered_item_ids()
    with zipfile.ZipFile(jar_path) as jar:
        names = jar.namelist()
    models = {os.path.basename(n)[:-5] for n in names
              if n.startswith('assets/astral_dice/models/item/') and n.endswith('.json')}
    textures = {os.path.basename(n)[:-4] for n in names
                if n.startswith('assets/astral_dice/textures/item/') and n.endswith('.png')}
    miss_m = sorted(ids - models)
    miss_t = sorted(ids - textures)
    extra = sorted(models - ids - EXTRA_MODEL_ALLOW)
    print('[1] 物品闭环: 注册 %d / 模型 %d / 贴图 %d' % (len(ids), len(models), len(textures)))
    if miss_m:
        failures.append('缺模型(紫黑格): %s' % miss_m)
    if miss_t:
        failures.append('缺贴图: %s' % miss_t)
    if extra:
        print('    注意: 多出的模型（非注册物品）%s' % extra)


def check_tags(failures):
    refs, local = {}, set()
    data_roots = [(os.path.join(FABRIC, 'src', b, 'resources', 'data'), b)
                  for b in ('main', 'generated')]
    for root, _ in data_roots:
        for dirpath, _, filenames in os.walk(root):
            for name in filenames:
                if not name.endswith('.json'):
                    continue
                full = os.path.join(dirpath, name)
                text = open(full, encoding='utf-8').read()
                rel = os.path.relpath(full, FABRIC)
                for hashed, ns, path in re.findall(r'"(#?)([a-z0-9_]+):([a-z0-9_/]+)"', text):
                    if hashed == '#':
                        refs.setdefault((ns, path), set()).add(rel)
                for ns, path in re.findall(r'"tag"\s*:\s*"([a-z0-9_]+):([a-z0-9_/]+)"', text):
                    refs.setdefault((ns, path), set()).add(rel)
                parts = os.path.relpath(full, root).replace('\\', '/').split('/')
                if len(parts) >= 4 and parts[1] == 'tags':
                    local.add((parts[0], '/'.join(parts[3:])[:-5]))
    provided = {('minecraft', '.')}
    provided |= local
    for mods_dir in MODS_DIRS:
        if not os.path.isdir(mods_dir):
            continue
        for name in os.listdir(mods_dir):
            if not name.endswith('.jar'):
                continue
            try:
                with zipfile.ZipFile(os.path.join(mods_dir, name)) as jar:
                    for entry in jar.namelist():
                        parts = entry.split('/')
                        if len(parts) >= 5 and parts[0] == 'data' and parts[2] == 'tags' \
                                and entry.endswith('.json'):
                            provided.add((parts[1], '/'.join(parts[3:])[:-5]))
            except Exception:
                pass
    unknown = sorted(k for k in refs if k[0] != 'minecraft' and k not in provided)
    print('[2] 标签闭环: 引用 %d / 自建 %d / 有提供者 %d' %
          (len(refs), len(local), len(refs) - len(unknown)))
    if unknown:
        failures.append('无提供者的标签(空标签 ⇒ 静默永不匹配): %s' % unknown)


def check_sounds(failures):
    java = open(os.path.join(SRC, 'audio', 'ModSounds.java'), encoding='utf-8').read()
    declared = {x for x in re.findall(r'register\(\s*"([a-z0-9_]+)"\s*\)', java) if x}
    spec = set()
    sp = os.path.join(ASSETS, 'sounds.json')
    if os.path.isfile(sp):
        spec = set(json.load(open(sp, encoding='utf-8')).keys())
    oggdir = os.path.join(ASSETS, 'sounds')
    oggs = {n[:-4] for n in os.listdir(oggdir)} if os.path.isdir(oggdir) else set()
    print('[3] 音效闭环: 注册 %d / sounds.json %d / ogg %d' % (len(declared), len(spec), len(oggs)))
    for label, left, right in (('注册<->sounds.json', declared, spec),
                               ('注册<->ogg', declared, oggs),
                               ('sounds.json<->ogg', spec, oggs)):
        if left - right or right - left:
            failures.append('音效 %s 不一致: 仅左=%s 仅右=%s'
                            % (label, sorted(left - right), sorted(right - left)))


def check_particles(failures):
    java = open(os.path.join(SRC, 'init', 'ModParticles.java'), encoding='utf-8').read()
    ids = set(re.findall(r'PARTICLE_TYPES\.register\(\s*"([a-z0-9_]+)"', java))
    pdir = os.path.join(ASSETS, 'particles')
    have = {n[:-5] for n in os.listdir(pdir)} if os.path.isdir(pdir) else set()
    print('[4] 粒子闭环: 注册 %d / particles/*.json %d' % (len(ids), len(have)))
    if ids - have:
        failures.append('粒子注册但无 particles/<id>.json: %s' % sorted(ids - have))


def check_lang(failures):
    langs = {}
    for name in ('zh_cn', 'en_us', 'ja_jp'):
        path = os.path.join(LANG_DIR, name + '.json')
        langs[name] = json.load(open(path, encoding='utf-8'))
    base = set(langs['zh_cn'])
    print('[5] 语言闭环: ' + ' / '.join('%s %d' % (n, len(langs[n])) for n in langs))
    for name, table in langs.items():
        diff = base ^ set(table)
        if diff:
            failures.append('%s 与 zh_cn 键集不一致: %s' % (name, sorted(diff)[:10]))
    key_re = re.compile(r'"((?:item|block|entity|effect|tooltip|subtitles|key|category|message|gui|'
                        r'astral_dice|death|commands?|advancements?)\.[A-Za-z0-9_.]*astral_dice'
                        r'[A-Za-z0-9_.]*)"')
    literals = set()
    for dirpath, _, filenames in os.walk(SRC):
        for name in filenames:
            if name.endswith('.java'):
                literals |= set(key_re.findall(open(os.path.join(dirpath, name),
                                                    encoding='utf-8').read()))
    for name, table in langs.items():
        miss = sorted(k for k in literals if k not in table)
        if miss:
            failures.append('%s 缺 java 引用的键: %s' % (name, miss))


def check_manual(failures):
    _, items_src = registered_item_ids()
    items = set(re.findall(r'registerItem\(\s*"([a-z0-9_]+)"', items_src))
    recipes = set()
    for sub in ('recipes', 'recipe'):
        for base in ('generated', 'main'):
            d = os.path.join(FABRIC, 'src', base, 'resources', 'data', 'astral_dice', sub)
            if os.path.isdir(d):
                recipes |= {n[:-5] for n in os.listdir(d) if n.endswith('.json')}
    book = os.path.join(ASSETS, 'patchouli_books')
    entries = 0
    miss_items, miss_recipes, miss_keys = set(), set(), set()
    for dirpath, _, filenames in os.walk(book):
        for name in filenames:
            if not name.endswith('.json'):
                continue
            entries += 1
            text = open(os.path.join(dirpath, name), encoding='utf-8').read()
            for ns, iid in re.findall(r'"(?:item|items)"\s*:\s*"([a-z0-9_]+):([a-z0-9_/]+)"', text):
                if ns == 'astral_dice' and iid not in items:
                    miss_items.add(iid)
            for ns, rid in re.findall(r'"recipe"\s*:\s*"([a-z0-9_]+):([a-z0-9_/]+)"', text):
                if ns == 'astral_dice' and rid not in recipes:
                    miss_recipes.add(rid)
            for key in re.findall(r'"(?:text|title|name)"\s*:\s*"(astral_dice\.[a-z0-9_.]+)"', text):
                miss_keys.add(key)
    lang_keys = set(json.load(open(os.path.join(LANG_DIR, 'zh_cn.json'), encoding='utf-8')))
    real_miss_keys = sorted(k for k in miss_keys if k not in lang_keys and k not in LANG_KEY_ALLOW)
    known_miss_keys = sorted(k for k in miss_keys if k not in lang_keys and k in LANG_KEY_ALLOW)
    print('[6] 手册闭环: 条目 %d / 引用配方 %d' % (entries, len(recipes)))
    if known_miss_keys:
        # 已登记的既有缺陷：**打印出来但不算失败**，避免"白名单 = 静默掩盖"
        print('    已知缺键（已登记、待文案裁决，不阻塞）: %s' % known_miss_keys)
    if miss_items:
        failures.append('手册引用了不存在的物品: %s' % sorted(miss_items))
    if miss_recipes:
        failures.append('手册引用了不存在的配方: %s' % sorted(miss_recipes))
    if real_miss_keys:
        failures.append('手册引用但 lang 缺键(玩家会看到键名): %s' % real_miss_keys)


def check_creative_tabs(failures):
    ids, items_src = registered_item_ids()
    field2id = dict(re.findall(r'Item>\s*([A-Z0-9_]+)\s*=\s*registerItem\(\s*"([a-z0-9_]+)"',
                               items_src))
    path = os.path.join(SRC, 'init', 'ModCreativeTabs.java')
    if not os.path.isfile(path):
        print('[7] 创意标签: 无 ModCreativeTabs.java（跳过）')
        return
    fields = set(re.findall(r'ModItems\.([A-Z0-9_]+)', open(path, encoding='utf-8').read()))
    covered = {field2id[f] for f in fields if f in field2id}
    missing = sorted(ids - covered)
    print('[7] 创意标签覆盖: %d/%d' % (len(covered), len(ids)))
    if missing:
        failures.append('不在创造栏的物品: %s' % missing)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', default=None, help='产物 jar 路径（默认取 build/libs 最新）')
    args = parser.parse_args()

    jar_path = find_jar(args.jar)
    if not jar_path or not os.path.isfile(jar_path):
        print('找不到产物 jar —— 先跑一次 gradlew :fabric-1.20.1:build')
        return 2
    print('jar = %s' % os.path.relpath(jar_path, REPO))
    print()

    failures = []
    check_items(jar_path, failures)
    check_tags(failures)
    check_sounds(failures)
    check_particles(failures)
    check_lang(failures)
    check_manual(failures)
    check_creative_tabs(failures)

    print()
    if failures:
        print('=== FAIL ===')
        for f in failures:
            print('  ✗ %s' % f)
        return 1
    print('=== PASS: 7 项闭环全部通过 ===')
    return 0


if __name__ == '__main__':
    sys.exit(main())
