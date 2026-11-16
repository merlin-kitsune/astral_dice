# -*- coding: utf-8 -*-
"""Bountiful 赏金联动一致性校验（只读守门）。

独立重算「按规则应进入赏金池的物品集合」，与两条 Bountiful 线池文件逐项比对：
  1. 各线 ModItems 注册物品 id 与品质完全一致（以 neoforge-1.21.1 为基线逐条对构造比对）；
  2. astral_objs = 非「不进池档」骰子 + 货币(star_coin / star_coin_bag / star_plate / golden_star_plate)；
  3. astral_rews = astral_objs ∪ 卡牌(专属牌除外) ∪ 非「不进池档」筹码 ∪ 非「不进池档」立牌
     （不进池 = 巅峰 PINNACLE + 奇特 BIZARRE；传奇 LEGENDARY 已按 2026-09-25 裁决放开进池）；
     卡牌判据 = id 前缀(attack_card_/defense_card_/effect_card_) ∪ 本模组卡牌标签
     (data/<ns>/tags/{item|items}/{combat_cards,effect_cards}.json)；
  4. 集合相等（0 缺失 / 0 多余），且数据层 rarity 与物品品质映射一致；
  5. 各线池文件逐字节一致（md5）；
  6. 文件格式：纯 CRLF / Tab 缩进 / 末尾换行；
  7. 价值平衡式：objs 顶值 ≥ rews 顶值(2 条之和) × 0.9。

退出码：0 = 全部通过；1 = 存在致命偏差；2 = 无法判定（根目录错/结构变化，不得判 PASS）。只读。

用法:
  python scripts/verify/verify_bountiful_pools.py [--root <repo-root>]

—— Python 版:1:1 对应 scripts/verify/旧版 verify_bountiful_pools 实现。
   ⚠️ 与 92fbeaf 之前的旧 .py 的差异（旧版已过时，不得回退）：
      两线（neoforge-1.21.1 / forge-1.20.1；26.1.2 无 Bountiful，不参与）/ AstralRarities 稀有度口径 / 传奇进池 / 卡牌标签判据 /
      专属牌剔除 / astral_currency.json 跨线一致 / 无 cwd 依赖（默认根 = 脚本上溯 3 层）。
"""
import hashlib
import json
import math
import os
import re
import sys

NS = 'astral_dice'
VERSIONS = ['neoforge-1.21.1', 'forge-1.20.1']
BASELINE = VERSIONS[0]
MODITEMS_REL = 'src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java'
POOL_REL = 'src/main/resources/data/bountiful/bounty_pools/bountiful/'
DECREE_REL = 'src/main/resources/data/bountiful/bounty_decrees/bountiful/astral.json'

REG = re.compile(r'(?:registerItem|ITEMS\.register)\("([a-z0-9_]+)"')
RAR = re.compile(r'rarity\((?:[A-Za-z_][A-Za-z0-9_]*\.)*AstralRarities\.([a-z]+)\(\)\)')

DICE = ['dice', 'golden_dice', 'glass_dice', 'netherrack_dice', 'diamond_dice',
        'emerald_dice', 'obsidian_dice', 'weird_dice', 'amethyst_dice',
        'netherite_dice', 'crimson_dice', 'ender_dice', 'nether_star_dice']
MONEY = ['star_coin', 'star_coin_bag', 'star_plate', 'golden_star_plate']
EXCLUDED_TIERS = ('PINNACLE', 'BIZARRE')
RARITY_MAP = {'COMMON': 'COMMON', 'RARE': 'RARE', 'EPIC': 'EPIC', 'LEGENDARY': 'LEGENDARY'}

TAG_DIR = {'neoforge-1.21.1': 'item', 'forge-1.20.1': 'items'}
CARD_TAGS = ['combat_cards.json', 'effect_cards.json']
EXCLUSIVE_TAG = 'is_exclusive.json'
LEGACY_POOL_EXCEPTIONS = []

errors = []
warnings = []


# ---------------------------------------------------------------------------
# 输出:统一显式 LF（不得 CRLF）
# ---------------------------------------------------------------------------
def _setup_stdout():
    try:
        sys.stdout.reconfigure(encoding='utf-8', newline='\n')
    except Exception:
        pass


def out(text):
    sys.stdout.write(text + '\n')


def pyrepr(value):
    if value is None:
        return 'None'
    if isinstance(value, bool):
        return 'True' if value else 'False'
    if isinstance(value, str):
        return "'" + value.replace('\\', '\\\\').replace("'", "\\'").replace('\n', '\\n').replace('\r', '\\r') + "'"
    if isinstance(value, tuple):
        parts = [pyrepr(x) for x in value]
        if len(parts) == 1:
            return '(' + parts[0] + ',)'
        return '(' + ', '.join(parts) + ')'
    if isinstance(value, dict):
        return '{' + ', '.join(pyrepr(k) + ': ' + pyrepr(v) for k, v in value.items()) + '}'
    if isinstance(value, (list, set, frozenset)):
        return '[' + ', '.join(pyrepr(x) for x in value) + ']'
    return str(value)


def add_err(msg):
    errors.append(msg)


def add_warn(msg):
    warnings.append(msg)


def read_raw_text(path):
    with open(path, 'r', encoding='utf-8', newline='') as fh:
        return fh.read()


def md5_hex(path):
    with open(path, 'rb') as fh:
        return hashlib.md5(fh.read()).hexdigest()


# ---------------------------------------------------------------------------
def parsed_items(path):
    text = read_raw_text(path)
    hits = list(REG.finditer(text))
    items = {}
    for i, m in enumerate(hits):
        start = m.end()
        end = hits[i + 1].start() if i + 1 < len(hits) else len(text)
        seg = text[start:end]
        r = RAR.search(seg)
        items[m.group(1)] = r.group(1).upper() if r else 'COMMON'
    return items


def tagged_cards(pre, ver, files=None):
    """读取本模组卡牌标签里的物品 id（裸 id）。缺失只 WARN 并退回前缀判据。"""
    if not files:
        files = CARD_TAGS
    tag_set = set()
    sub = TAG_DIR.get(ver, 'item')
    for f in files:
        p = pre + ver + '/src/main/resources/data/' + NS + '/tags/' + sub + '/' + f
        if not os.path.isfile(p):
            add_warn('标签缺失(该线相关集合退回 id 前缀判据): ' + ver + '/tags/' + sub + '/' + f)
            continue
        d = json.loads(read_raw_text(p))
        if not isinstance(d, dict) or 'values' not in d:
            continue
        for v in d['values']:
            if not isinstance(v, str):
                continue
            i = v.find(':')
            tag_set.add(v[i + 1:] if i >= 0 else v)
    return tag_set


def classified(items, tag_cards):
    c = {'dice': set(), 'money': set(), 'cards': set(), 'signs': set(), 'chips': set(), 'materials': set()}
    for k in items:
        is_card = k.startswith('attack_card_') or k.startswith('defense_card_') or k.startswith('effect_card_')
        if not is_card and tag_cards is not None:
            is_card = k in tag_cards
        if k in DICE:
            c['dice'].add(k)
        elif k in MONEY:
            c['money'].add(k)
        elif is_card:
            c['cards'].add(k)
        elif k.endswith('_sign') and k != 'blank_sign':
            c['signs'].add(k)
        elif k.endswith('_chip') and k != 'blank_chip':
            c['chips'].add(k)
        else:
            c['materials'].add(k)
    return c


def expected_sets(items, c, exclusive):
    objs = set()
    for k in c['dice']:
        if items[k] not in EXCLUDED_TIERS:
            objs.add(k)
    for k in c['money']:
        objs.add(k)
    rews = set(objs)
    for group in ('cards', 'signs', 'chips'):
        for k in c[group]:
            if items[k] not in EXCLUDED_TIERS:
                rews.add(k)
    if exclusive is not None:
        for k in exclusive:
            objs.discard(k)
            rews.discard(k)
    return objs, rews


def get_pool(path):
    raw = read_raw_text(path)
    d = json.loads(raw)
    content = {}
    for v in d['content'].values():
        parts = str(v['content']).split(':', 1)
        key = parts[1] if len(parts) > 1 else ''
        content[key] = v
    return content, raw


def disp_round_even(x):
    """等价 .NET Math.Round(x, 0, MidpointRounding.ToEven) 的整数化（x >= 0）。"""
    f = math.floor(x)
    r = x - f
    if r > 0.5:
        return int(f + 1)
    if r < 0.5:
        return int(f)
    return int(f) if int(f) % 2 == 0 else int(f + 1)


def default_root():
    return os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def parse_root(argv):
    root = None
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == '--root':
            if i + 1 >= len(argv):
                sys.stderr.write('--root 需要参数\n')
                sys.exit(2)
            root = argv[i + 1]
            i += 2
            continue
        if a.startswith('--root='):
            root = a[len('--root='):]
            i += 1
            continue
        if root is None:
            root = a
            i += 1
            continue
        sys.stderr.write('无法识别的参数: ' + a + '\n')
        sys.exit(2)
    if root is None:
        root = default_root()
    return root


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
def main(argv):
    _setup_stdout()
    root = parse_root(argv)
    root = root.rstrip('/\\')
    pre = ''
    if root not in ('.', ''):
        pre = root + '/'

    # 1) 各线物品清单一致（以 BASELINE 为基线，逐条与其余线比对）
    items_by_ver = {}
    for ver in VERSIONS:
        p = pre + ver + '/' + MODITEMS_REL
        if not os.path.isfile(p):
            out('FAIL 找不到物品注册清单: ' + p)
            out('')
            out('RESULT: 无法判定（根目录错或目录结构变化）')
            return 2
        items_by_ver[ver] = parsed_items(p)
    a = items_by_ver[BASELINE]
    if not a:
        out('FAIL 未解析到任何 registerItem(...) 注册项（' + pre + BASELINE + '/' + MODITEMS_REL + '）⇒ 无法判定；不得判 ALL OK')
        out('')
        out('RESULT: 无法判定（根目录错或 ModItems 结构变化）')
        return 2

    for ver in VERSIONS:
        if ver == BASELINE:
            continue
        b = items_by_ver[ver]
        same_items = True
        if len(a) != len(b):
            same_items = False
        else:
            for k in a:
                if k not in b:
                    same_items = False
                    break
                if a[k] != b[k]:
                    same_items = False
                    break
        if not same_items:
            only_a = sorted(set(a) - set(b))
            only_b = sorted(set(b) - set(a))
            diff_rarity = sorted([k for k in a if k in b and a[k] != b[k]])
            add_err('跨线 ModItems 不一致(' + BASELINE + ' vs ' + ver + ')：仅 ' + BASELINE + '=' + pyrepr(only_a) + '；品质差异=' + pyrepr(diff_rarity))
            add_err('跨线 ModItems 不一致(' + BASELINE + ' vs ' + ver + ')：仅 ' + ver + '=' + pyrepr(only_b))

    items = a
    tag_cards = tagged_cards(pre, BASELINE)
    exclusive = tagged_cards(pre, BASELINE, [EXCLUSIVE_TAG])
    for ver in VERSIONS:
        if ver == BASELINE:
            continue
        tag_cards_other = tagged_cards(pre, ver)
        tag_only_a = sorted(tag_cards - tag_cards_other)
        tag_only_b = sorted(tag_cards_other - tag_cards)
        if tag_only_a or tag_only_b:
            add_warn('跨线卡牌标签不一致(' + BASELINE + ' vs ' + ver + ')：仅 ' + BASELINE + '=' + pyrepr(tag_only_a) + '；仅 ' + ver + '=' + pyrepr(tag_only_b))
        exclusive_other = tagged_cards(pre, ver, [EXCLUSIVE_TAG])
        excl_only_a = sorted(exclusive - exclusive_other)
        excl_only_b = sorted(exclusive_other - exclusive)
        if excl_only_a or excl_only_b:
            add_warn('跨线专属牌标签不一致(' + BASELINE + ' vs ' + ver + ')：仅 ' + BASELINE + '=' + pyrepr(excl_only_a) + '；仅 ' + ver + '=' + pyrepr(excl_only_b))

    c = classified(items, tag_cards)
    out('专属效果牌(禁止进入任何赏金池): ' + str(len(exclusive)) + ' 项')
    eo, er = expected_sets(items, c, exclusive)
    counts = {k: len(c[k]) for k in ('dice', 'money', 'cards', 'signs', 'chips', 'materials')}
    out('物品分类: ' + pyrepr(counts) + ' 总 ' + str(len(items)))
    out('规则应含: objs ' + str(len(eo)) + ' / rews ' + str(len(er)))
    excl = []
    for group in ('chips', 'signs', 'dice', 'cards'):
        for k in c[group]:
            if items[k] in EXCLUDED_TIERS:
                excl.append(k)
    excl = sorted(excl)
    out('按规则排除(巅峰/奇特档 筹码/立牌/骰子/卡牌): ' + str(len(excl)) + ' 项')

    # 2/3/4) 逐线逐池比对
    for ver in VERSIONS:
        for pool, exp in (('astral_objs.json', eo), ('astral_rews.json', er)):
            path = pre + ver + '/' + POOL_REL + pool
            content, _raw = get_pool(path)
            actual = set(content.keys())
            miss = sorted(exp - actual)
            extra_all = sorted(actual - exp)
            extra_legacy = [k for k in extra_all if k in LEGACY_POOL_EXCEPTIONS]
            extra = [k for k in extra_all if k not in LEGACY_POOL_EXCEPTIONS]
            tag = ver + '/' + pool
            if miss:
                add_err(tag + ' 缺失条目(' + str(len(miss)) + '): ' + pyrepr(miss))
            if extra:
                add_err(tag + ' 多余/应排除条目(' + str(len(extra)) + '): ' + pyrepr(extra))
            if extra_legacy:
                add_warn(tag + ' 专属效果牌仍在池内(既有状态,待用户裁决)(' + str(len(extra_legacy)) + '): ' + pyrepr(extra_legacy))
            if not miss and not extra:
                out('OK  ' + tag.ljust(40) + ' 条目 ' + str(len(actual)) + ' 与规则一致')

            # 数据层 rarity 映射（缺省字段 = COMMON，Bountiful 默认）
            bad_r = []
            for k, v in content.items():
                if k not in items:
                    continue
                raw_rarity = None if 'rarity' not in v else v['rarity']
                vr = 'COMMON' if raw_rarity is None else raw_rarity
                want = RARITY_MAP.get(items[k])
                if vr != want:
                    bad_r.append((k, raw_rarity, want))
            if bad_r:
                add_err(tag + ' rarity 映射错误: ' + pyrepr(bad_r[:8]))

            # 格式
            with open(path, 'rb') as fh:
                s = fh.read().decode('latin-1')
            if s.count('\r\n') != s.count('\n'):
                add_err(tag + ' 换行符非纯 CRLF')
            if not s.endswith('\r\n'):
                add_err(tag + ' 末尾缺换行')
            if '\r\n    ' in s or '\r\n  ' in s:
                add_err(tag + ' 存在空格缩进(应为 Tab)')

    # 5) 各线逐字节一致
    for pool in ('astral_objs.json', 'astral_rews.json', 'astral_currency.json', 'astral.json'):
        paths = []
        for v in VERSIONS:
            if pool == 'astral.json':
                paths.append(pre + v + '/' + DECREE_REL)
            else:
                paths.append(pre + v + '/' + POOL_REL + pool)
        digests = []
        missing = False
        for p in paths:
            if not os.path.isfile(p):
                add_err("跨线文件缺失: [Errno 2] No such file or directory: '" + p + "'")
                missing = True
                break
            digests.append(md5_hex(p))
        if missing:
            continue
        if len(set(digests)) != 1:
            add_err('跨线不一致: ' + pool + ' → ' + pyrepr(digests))
        else:
            out('OK  跨线一致 ' + pool.ljust(24) + ' md5 ' + digests[0])

    # 6) 价值平衡式
    for ver in VERSIONS:
        objs = get_pool(pre + ver + '/' + POOL_REL + 'astral_objs.json')[0]
        rews = get_pool(pre + ver + '/' + POOL_REL + 'astral_rews.json')[0]
        objs_top = 0.0
        for v in objs.values():
            x = float(v['amount']['max']) * float(v['unitWorth'])
            if x > objs_top:
                objs_top = x
        vals = []
        for v in rews.values():
            vals.append(float(v['amount']['max']) * float(v['unitWorth']))
        vals.sort()
        top2 = []
        if len(vals) >= 1:
            top2.append(vals[-1])
        if len(vals) >= 2:
            top2.append(vals[-2])
        sum_top2 = 0.0
        for x in top2:
            sum_top2 += x
        need = sum_top2 * 0.9
        ok = objs_top >= need
        head = 'OK  '
        verdict = 'PASS'
        if not ok:
            head = 'FAIL'
            verdict = 'FAIL'
        out(head + ' 价值平衡: objs 顶值 ' + str(int(objs_top)) + ' ≥ rews 顶值2和 ' + str(int(sum_top2)) + ' × 0.9 = ' + str(disp_round_even(need)) + ' → ' + verdict)
        if not ok:
            add_err(ver + ' 价值平衡式不成立')

    # 7) decree 引用
    for ver in VERSIONS:
        p = pre + ver + '/' + DECREE_REL
        try:
            d = json.loads(read_raw_text(p))
        except FileNotFoundError:
            add_err(ver + " decree 缺失: [Errno 2] No such file or directory: '" + p + "'")
            continue
        obj_ok = isinstance(d, dict) and isinstance(d.get('objectives'), list) and len(d['objectives']) == 1 and d['objectives'][0] == 'astral_objs'
        rew_ok = isinstance(d, dict) and isinstance(d.get('rewards'), list) and len(d['rewards']) == 1 and d['rewards'][0] == 'astral_rews'
        if (not obj_ok) or (not rew_ok):
            add_err(ver + ' decree 引用异常: ' + pyrepr(d))
        else:
            out('OK  ' + ver + ' decree 引用 astral_objs/astral_rews')

    out('')
    for w in warnings:
        out('WARN ' + w)
    if errors:
        for e in errors:
            out('FAIL ' + e)
        out('')
        out('结果: ' + str(len(errors)) + ' 项致命偏差')
        sys.stdout.flush()
        return 1
    suffix = ''
    if warnings:
        suffix = '（' + str(len(warnings)) + ' 条提示）'
    out('结果: ALL OK' + suffix)
    sys.stdout.flush()
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
