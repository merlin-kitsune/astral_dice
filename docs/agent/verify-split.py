#!/usr/bin/env python3
"""AGENTS.md 拆分自检（只读）。校验：全文快照哈希 / 红线区逐字一致 / 索引无空洞。
用法：python3 docs/agent/verify-split.py
"""
import bisect, difflib, glob, hashlib, os, re, sys

W = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FULL = os.path.join(W, 'docs/agent/AGENTS-full.md')
AG = os.path.join(W, 'AGENTS.md')
bak = sorted(glob.glob(os.path.join(W, 'AGENTS.md.bak-a-plan-*')))

def md5(p):
    return hashlib.md5(open(p, 'rb').read()).hexdigest()

def norm(p):
    return open(p, 'rb').read().decode('utf-8').replace('\r\n', '\n')

def seq_contains(sub_lines, sup_lines):
    """保序包含判定：sub 的每一行都要按原顺序在 sup 中有交代
    （允许 sup 中段插入 / 末尾追加 / 原地改写；不允许丢行与乱序）。
    返回 (缺失行数, 乱序行数, 原地改写行数, 新增行数)：
      · 乱序 = 该行存在，但只出现在已消费位置之前（顺序被打乱）；
      · 缺失 = 精确比对未命中、且无法归因为「原地改写」的行（= 快照真丢了备份内容）；
      · 改写 = 精确比对未命中、但落在 difflib 的 replace 块内（旧行被新文本原地替换）——
                仅作报告，不参与通过判定（合规订正会更新行内数字/说明）；
      · 新增 = 仅出现在 sup 中的行（中段插入 / 末尾追加；恒有 快照行数 − 备份行数 = 新增 − 缺失）。
    归类依据 = difflib 的 opcodes：replace 块按「旧行数 ≤ 新行数 ⇒ 全部算改写，
    多出来的行数算缺失」保守处理；delete 块（快照无对应）一律算缺失。"""
    pos = {}
    for i, l in enumerate(sup_lines):
        pos.setdefault(l, []).append(i)
    unmatched, disorder, cursor = [], 0, 0
    for i, l in enumerate(sub_lines):
        lst = pos.get(l)
        if not lst:
            unmatched.append(i)      # 精确比对未命中 ⇒ 待归类（原地改写 vs 真删除）
            continue
        j = bisect.bisect_left(lst, cursor)
        if j == len(lst):
            disorder += 1            # 该行存在，但只出现在已消费位置之前 ⇒ 顺序被打乱
        else:
            cursor = lst[j] + 1

    rewritten_idx, added = set(), 0
    for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(
            None, sub_lines, sup_lines, autojunk=False).get_opcodes():
        if tag == 'insert':
            added += j2 - j1                      # 仅存在于快照的新行
        elif tag == 'replace':
            excess = (i2 - i1) - (j2 - j1)        # 旧行多于新行 ⇒ 多出的部分是真删除
            rewritten_idx.update(range(i1 + max(excess, 0), i2))
    missing = sum(1 for i in unmatched if i not in rewritten_idx)
    rewritten = sum(1 for i in unmatched if i in rewritten_idx)
    return missing, disorder, rewritten, added

ok = True
def check(name, cond, extra=''):
    global ok
    print(('  [OK]  ' if cond else '  [FAIL]') + f' {name}' + (f'  {extra}' if extra else ''))
    ok = ok and bool(cond)

print('== 1. 全文快照 ==')
if bak:
    # 2026-10-16：快照允许中段插入与末尾追加（slim AGENTS.md「维护约定 2」明确允许的演进方式），
    # 「逐字节恒等」只在拆分当日成立、首次合规订正后必然永假 ⇒ 改为「保序包含」判定：
    # 拆分前备份的每一行按序在 AGENTS-full.md 中有交代（缺失行数 / 乱序行数 均为 0 才 OK；
    # 原地改写行数只作报告 —— 合规订正会更新行内数字/说明，不构成「丢行」）。
    # ⚠️ AGENTS.md.bak-a-plan-* 与 AGENTS.md.bak-slim-* 是回滚工件，字节不许变。
    n_missing, n_disorder, n_rewritten, n_added = seq_contains(norm(bak[-1]).split('\n'),
                                                              norm(FULL).split('\n'))
    check(f'docs/agent/AGENTS-full.md 保序包含拆分前备份（{os.path.basename(bak[-1])}；缺失/乱序 = 0）',
          n_missing == 0 and n_disorder == 0,
          f'缺失行数={n_missing} / 乱序行数={n_disorder}'
          f' / 原地改写行数={n_rewritten} / 新增行数={n_added}'
          f' / 行数 快照 {norm(FULL).count(chr(10)):,} vs 备份 {norm(bak[-1]).count(chr(10)):,}'
          f' / md5 快照={md5(FULL)[:12]} 备份={md5(bak[-1])[:12]}')
else:
    print('  [WARN] 未找到 AGENTS.md.bak-a-plan-* 备份，跳过哈希比对')
check('快照行数 = 3480', norm(FULL).count('\n') >= 3480, f'lines={norm(FULL).count(chr(10))}')

print('== 2. 红线区逐字一致 ==')
ag = norm(AG)
full = norm(FULL)
body = ag.split('## 📍 生效红线区', 1)[1].split('<!-- END 生效红线区', 1)[0]
body = body.split('\n', 2)[2].strip('\n')          # 去掉小标题行与其后空行
orig_2_120 = '\n'.join(full.split('\n')[1:120]).strip('\n')
check('AGENTS.md 红线区 == AGENTS-full.md L2–L120（逐字）', body == orig_2_120,
      f'红线区 {len(body):,} 字符 vs 原文 {len(orig_2_120):,} 字符')

print('== 3. 索引覆盖 ==')
rows = re.findall(r'\|\s*L(\d+)–L(\d+)\s*\|', ag)
check('索引行数 ≥ 15', len(rows) >= 15, f'rows={len(rows)}')
# 2026-10-16：期望末端不得硬编码 —— 全文快照按 slim AGENTS.md「维护约定 2」会持续追加
# （中段插入 / 末尾追加），末端须随快照实际行数走 ⇒ cov == 实际行数 + 1。
n_full = norm(FULL).count('\n')
cov, gaps = 121, []
for a, b in [(int(a), int(b)) for a, b in rows]:
    if a != cov:
        gaps.append((cov, a))
    cov = b + 1
check(f'索引连续覆盖 L121–L{n_full}（无空洞、无重叠）', not gaps and cov == n_full + 1,
      f'覆盖到 L{cov-1}，空洞={gaps}，期望末端 L{n_full}（快照实际行数）')

print('== 4. 体量与结构 ==')
heads = [l for l in full.split('\n') if l.startswith('#')]
inred = sum(1 for l in orig_2_120.split('\n') if l.startswith('#'))
print(f'  原文：{len(full):,} 字符 / {full.count(chr(10)):,} 行 / {len(heads)} 个标题')
print(f'  AGENTS.md：{len(ag):,} 字符（红线区 {len(body):,} + 契约 {ag.count("代理协作契约") and len(ag.split("## 📍")[0]):,} + 索引说明）')
print(f'  标题分布：红线区内 {inred} 个，索引指向的区间内 {len(heads)-inred} 个（按行号区间可达）')
check('AGENTS.md 未超 32K 字符', len(ag) < 32000, f'{len(ag):,} 字符')
print('\n结果：' + ('全部通过' if ok else '有失败项'))
sys.exit(0 if ok else 1)
