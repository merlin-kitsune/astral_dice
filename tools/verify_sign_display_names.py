#!/usr/bin/env python3
'''立牌英日命名门禁（五线：neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 / fabric-1.20.1 / fabric-1.21.1）。

规则（2026-10-26 用户裁决，见 docs/agent/AGENTS-full.md〈立牌 / 物品英日命名规则〉）：
  - EN 立牌名 = "<wiki 英文页名> Sign"，JA = "<wiki nameJapanese>のスタンド"；
  - 名字一律取角色人名，禁止把中文职称（秘密侦探 / 教主 / 调酒师 …）机翻成英文职称；
  - wiki 无条目 ⇒ 用 id 的人名，不机翻；
  - zh 侧保持游戏内职称式命名（本门禁只查 en_us / ja_jp，另附 zh 只读比对）。
权威表：tools/sign_display_names.json（拉取脚本 tools/sign_names_from_wiki.py）。

用法：python tools/verify_sign_display_names.py [--strict-zh]
退出码：0 = PASS，1 = FAIL。
'''
import json, os, re, sys

W = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TABLE = os.path.join(W, 'tools', 'sign_display_names.json')
LINES = ['neoforge-1.21.1', 'forge-1.20.1', 'neoforge-26.1.2', 'fabric-1.20.1', 'fabric-1.21.1']

ALL_LINES = list(LINES)


def lines_of(sign):
    """条目可带 lines 字段限定「已实装该立牌」的线；缺省 = 五线全有。

    用途：某一批立牌只在部分线实装时（例：2026-11-08 少主立牌首发 neoforge-1.21.1），
    其余线的 lang 不必补占位键，门禁也不会把它们判成「表与 lang 不一致」。
    """
    ls = sign.get('lines')
    return set(ls) if ls else set(ALL_LINES)

# 旧职称（英文）—— 只禁止它们紧接 "sign" 的形态，避免误伤 Boss 敌人 / Ninja Star / Healing Slime / Legendary Merchant 等既有专名
EN_LEGACY = ['Secret Detective', 'Great Detective', 'Boss', 'Astrologer', 'Sweeper', 'Ninja', 'Slime',
             'Kanban Musume', 'Guardian', 'Gunsmith', 'Vampire', 'Business', 'Merchant', 'Investigator',
             'Game Master', 'Fengshui Master', 'Cult Leader', 'Oasis Queen', 'Bartender', 'Sister']
# 旧职称（日文）—— 残留即 FAIL（这些词在日文文案里只可能指角色）
JA_LEGACY = ['\u8d85\u5929\u9171', '\u5730\u96f7\u5973', '\u602a\u529b\u4e71\u795e']  # 超天酱 / 地雷女 / 怪力乱神

fails, checks = [], 0

def fail(msg):
    fails.append(msg)

def load(path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)

def main():
    global checks
    if not os.path.exists(TABLE):
        print('FAIL: 缺权威表 ' + TABLE)
        return 1
    table = load(TABLE)
    signs = table['signs']
    ids = [s['id'] for s in signs]
    print('== 立牌英日命名门禁 ==')
    print('权威表: tools/sign_display_names.json（' + str(len(signs)) + ' 条，updated ' + table.get('updated', '?') + '）')

    for line in LINES:
        langs = {}
        for lang, fname in (('zh', 'zh_cn.json'), ('en', 'en_us.json'), ('ja', 'ja_jp.json')):
            p = os.path.join(W, line, 'src', 'main', 'resources', 'assets', 'astral_dice', 'lang', fname)
            if not os.path.exists(p):
                fail('[%s] 缺 %s' % (line, p))
                langs[lang] = {}
            else:
                langs[lang] = load(p)
        print('-- ' + line + ' -- zh %d keys / en %d keys / ja %d keys'
              % (len(langs['zh']), len(langs['en']), len(langs['ja'])))

        line_signs = [s for s in signs if line in lines_of(s)]
        # 1) 立牌显示名必须与权威表逐字一致（只校验本线已实装的条目）
        for s in line_signs:
            key = 'item.astral_dice.%s_sign' % s['id']
            for lang in ('en', 'ja'):
                checks += 1
                got = langs[lang].get(key)
                want = s[lang]
                if got != want:
                    fail('[%s] %s = %r，应为 %r' % (line, key, got, want))
            # zh 只读比对（--strict-zh 时才判失败）
            checks += 1
            if langs['zh'].get(key) != s['zh']:
                msg = '[%s] %s(zh) = %r，表中为 %r' % (line, key, langs['zh'].get(key), s['zh'])
                if '--strict-zh' in sys.argv[1:]:
                    fail(msg)
                else:
                    print('  [WARN] ' + msg)

        # 2) EN 里不许再出现 "<旧职称>(?:'s)? sign"
        pats = [(w, re.compile(r'\b' + re.escape(w) + r"(?:'s)? ?[Ss]ign\b")) for w in EN_LEGACY]
        for key, val in langs['en'].items():
            if not isinstance(val, str):
                continue
            for w, pat in pats:
                checks += 1
                if pat.search(val):
                    fail('[%s] en %s 仍含旧职称命名 %r :: %s' % (line, key, w, val[:90]))

        # 3) JA 里不许残留中文职称词
        for key, val in langs['ja'].items():
            if not isinstance(val, str):
                continue
            for w in JA_LEGACY:
                checks += 1
                if w in val:
                    fail('[%s] ja %s 仍含中文职称 %r :: %s' % (line, key, w, val[:90]))

    # 4) 表内 id 必须与 lang 里的立牌 item key 集合对齐
    for line in LINES:
        p = os.path.join(W, line, 'src', 'main', 'resources', 'assets', 'astral_dice', 'lang', 'en_us.json')
        if not os.path.exists(p):
            continue
        keys = {k[len('item.astral_dice.'):-len('_sign')] for k in load(p)
                if k.startswith('item.astral_dice.') and k.endswith('_sign')}
        checks += 1
        expected = {s['id'] for s in signs if line in lines_of(s)}
        missing = sorted(expected - keys)
        extra = sorted(keys - expected)
        if missing or extra:
            fail('[%s] 表与 lang 立牌集合不一致：表缺 %s / lang 多 %s' % (line, missing, extra))

    print('检查项: %d' % checks)
    if fails:
        print('FAIL: %d 处不符' % len(fails))
        for m in fails[:40]:
            print('  - ' + m)
        if len(fails) > 40:
            print('  ... 其余 %d 处省略' % (len(fails) - 40))
        return 1
    print('GATE: PASS（立牌英日命名与权威表逐字一致，无旧职称残留）')
    return 0

if __name__ == '__main__':
    sys.exit(main())
