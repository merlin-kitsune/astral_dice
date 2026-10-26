#!/usr/bin/env python3
"""从 Astral Party 官方 wiki 拉取立牌英日命名，校验 / 更新权威表。

权威来源（2026-10-26 用户裁决）：
  https://astralparty.miraheze.org/w/api.php?action=parse&page=<Title>&prop=wikitext&format=json&formatversion=2
  - 页面标题 = 英文名（EN 立牌名 = "<标题> Sign"）
  - {{CharPlayable|nameChinese = 多萝西|nameJapanese = ドロシー}} 里的 nameJapanese = 日文名（JA 立牌名 = "<nameJapanese>のスタンド"）
  - nameJapanese / nameChinese 为空（如 Moses）⇒ 不覆盖表里的手填值，标 NEEDS_REVIEW

用法：
  python tools/sign_names_from_wiki.py                 # 只校验（漂移则 exit 1）
  python tools/sign_names_from_wiki.py --write         # 有漂移则写回 tools/sign_display_names.json
  python tools/sign_names_from_wiki.py --proxy http://127.0.0.1:7897
  python tools/sign_names_from_wiki.py --only bonnie,jill
退出码：0 = 与 wiki 一致（或 --write 已对齐），1 = 有漂移/失败。
"""
import argparse, json, os, re, sys, time, urllib.parse, urllib.request

W = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TABLE = os.path.join(W, 'tools', 'sign_display_names.json')
API = 'https://astralparty.miraheze.org/w/api.php'
UA = 'astral-dice-naming-check/1.0 (local repo tool)'


def fetch(page, proxy=None, retries=3):
    q = {'action': 'parse', 'page': page, 'prop': 'wikitext', 'format': 'json', 'formatversion': '2'}
    url = API + '?' + urllib.parse.urlencode(q)
    handlers = [urllib.request.ProxyHandler({'http': proxy, 'https': proxy})] if proxy else []
    opener = urllib.request.build_opener(*handlers)
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    last = None
    for i in range(retries):
        try:
            with opener.open(req, timeout=30) as r:
                return json.loads(r.read().decode('utf-8'))
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(1.5 * (i + 1))
    raise RuntimeError('fetch %s failed: %s' % (page, last))


def grab(wikitext, field):
    m = re.search(field + r'\s*=\s*([^|}\n]*)', wikitext)
    return m.group(1).strip() if m else ''


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--write', action='store_true')
    ap.add_argument('--proxy', default=None)
    ap.add_argument('--only', default=None, help='逗号分隔的 id')
    a = ap.parse_args()
    table = json.load(open(TABLE, encoding='utf-8'))
    only = {s.strip() for s in a.only.split(',')} if a.only else None
    drift, review, ok, fail = [], [], 0, 0
    for s in table['signs']:
        if only and s['id'] not in only:
            continue
        page = (s.get('wiki_page') or '').strip()
        if not page:
            print('[skip] %-10s wiki 无条目（保留现名 EN=%s / JA=%s）' % (s['id'], s['en'], s['ja']))
            continue
        try:
            data = fetch(page, a.proxy)
        except Exception as e:  # noqa: BLE001
            print('[FAIL] %-10s %s' % (s['id'], e))
            fail += 1
            continue
        p = (data.get('parse') or {})
        wt = p.get('wikitext') or ''
        page_title = p.get('title') or page
        ja_name = grab(wt, 'nameJapanese')
        zh_name = grab(wt, 'nameChinese')
        want_en = page_title + ' Sign'
        want_ja = (ja_name + '\u306e\u30b9\u30bf\u30f3\u30c9') if ja_name else s['ja']
        diffs = []
        if s['en'] != want_en:
            diffs.append('en %r -> %r' % (s['en'], want_en))
        if s['ja'] != want_ja:
            diffs.append('ja %r -> %r' % (s['ja'], want_ja))
        if not ja_name:
            review.append('%s（wiki nameJapanese 为空，日文名需人工确认）' % s['id'])
        if diffs:
            drift.append((s, want_en, want_ja, diffs))
            print('[DRIFT] %-10s wiki=%s | %s' % (s['id'], page_title, '; '.join(diffs)))
        else:
            ok += 1
            print('[ok]    %-10s %s | %s' % (s['id'], s['en'], s['ja']))
    print('---- 与 wiki 一致 %d / 漂移 %d / 需人工 %d / 失败 %d' % (ok, len(drift), len(review), fail))
    for r in review:
        print('  [review] ' + r)
    if fail:
        print('WIKI: FAIL（%d 个页面取不到）' % fail)
        return 1
    if drift and a.write:
        for s, want_en, want_ja, _ in drift:
            s['en'], s['ja'] = want_en, want_ja
        table['updated'] = time.strftime('%Y-%m-%d')
        with open(TABLE, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(table, f, ensure_ascii=False, indent=2)
            f.write('\n')
        print('WIKI: WROTE（已写回 tools/sign_display_names.json，请重跑 python tools/verify_sign_display_names.py）')
        return 0
    if drift:
        print('WIKI: DRIFT（与 wiki 不一致，加 --write 写回权威表）')
        return 1
    print('WIKI: OK（权威表与 wiki 一致）')
    return 0


if __name__ == '__main__':
    sys.exit(main())