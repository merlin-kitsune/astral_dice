#!/usr/bin/env python3
'''AGENTS.md 规则库自检（只读）。

2026-10-24 重写：AGENTS.md 已瘦身为「宿主分派 + 共同红线 + 按需索引」，旧的两项断言
（按「📍 生效红线区」小节与 AGENTS-full.md L2–L120 逐字比对；按「| L121–L… |」行号索引判连续覆盖）
在新结构下必然 IndexError / 恒 FAIL ⇒ 换成与结构无关的四项：
  1. 全文快照：AGENTS-full.md 保序包含拆分前备份（不丢行、不乱序）；
     + 红线区的**新落点**（docs/agent/AGENTS-dsh.md 标记块）与快照 L2–L120 仍逐字一致；
  2. 结构完整：宿主分派表 4 个宿主 / 共同安全红线 1–6 条 / 按需文档索引节；
  3. 引用有效：AGENTS.md 里引用的 docs/**.md 必须真实存在（默认只 WARN，--strict 才 FAIL）；
  4. 体量 + 驻留规则：AGENTS.md < 32K 字符；AGENTS-dsh.md 仍含用户裁决的 using-superpowers 段。

用法：python docs/agent/verify-split.py [--strict]
'''
import bisect, difflib, glob, hashlib, os, re, sys

W = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FULL = os.path.join(W, 'docs/agent/AGENTS-full.md')
AG = os.path.join(W, 'AGENTS.md')
DSH = os.path.join(W, 'docs/agent/AGENTS-dsh.md')
bak = sorted(glob.glob(os.path.join(W, 'AGENTS.md.bak-a-plan-*')))
STRICT = '--strict' in sys.argv[1:]

def md5(p):
    return hashlib.md5(open(p, 'rb').read()).hexdigest()

def norm(p):
    return open(p, 'rb').read().decode('utf-8').replace('\r\n', '\n')

def seq_contains(sub_lines, sup_lines):
    '''保序包含判定：sub 的每一行都要按原顺序在 sup 中有交代
    （允许 sup 中段插入 / 末尾追加 / 原地改写；不允许丢行与乱序）。
    返回 (缺失行数, 乱序行数, 原地改写行数, 新增行数)：
      · 乱序 = 该行存在，但只出现在已消费位置之前（顺序被打乱）；
      · 缺失 = 精确比对未命中、且无法归因为「原地改写」的行（= 快照真丢了备份内容）；
      · 改写 = 精确比对未命中、但落在 difflib 的 replace 块内（旧行被新文本原地替换）——
                仅作报告，不参与通过判定（合规订正会更新行内数字/说明）；
      · 新增 = 仅出现在 sup 中的行（中段插入 / 末尾追加；恒有 快照行数 − 备份行数 = 新增 − 缺失）。
    归类依据 = difflib 的 opcodes：replace 块按「旧行数 ≤ 新行数 ⇒ 全部算改写，
    多出来的行数算缺失」保守处理；delete 块（快照无对应）一律算缺失。'''
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
warns = 0
def check(name, cond, extra=''):
    global ok
    print(('  [OK]  ' if cond else '  [FAIL]') + f' {name}' + (f'  {extra}' if extra else ''))
    ok = ok and bool(cond)

def warn(name, extra=''):
    global warns
    warns += 1
    print('  [WARN]' + f' {name}' + (f'  {extra}' if extra else ''))

print('== 1. 全文快照 ==')
if bak:
    # 2026-10-16：快照允许中段插入与末尾追加（slim AGENTS.md「维护约定 2」明确允许的演进方式），
    # 「逐字节恒等」只在拆分当日成立、首次合规订正后必然永假 ⇒ 改为「保序包含」判定：
    # 拆分前备份的每一行按序在 AGENTS-full.md 中有交代（缺失 / 乱序 均为 0 才 OK；
    # 原地改写行数只作报告 —— 合规订正会更新行内数字/说明，不构成「丢行」）。
    # ⚠ AGENTS.md.bak-a-plan-* 与 AGENTS.md.bak-slim-* 是回滚工件，字节不许变。
    n_missing, n_disorder, n_rewritten, n_added = seq_contains(norm(bak[-1]).split('\n'),
                                                              norm(FULL).split('\n'))
    check(f'docs/agent/AGENTS-full.md 保序包含拆分前备份（{os.path.basename(bak[-1])}；缺失/乱序 = 0）',
          n_missing == 0 and n_disorder == 0,
          f'缺失行数={n_missing} / 乱序行数={n_disorder}'
          f' / 原地改写行数={n_rewritten} / 新增行数={n_added}'
          f' / 行数 快照 {norm(FULL).count(chr(10)):,} vs 备份 {norm(bak[-1]).count(chr(10)):,}'
          f' / md5 快照={md5(FULL)[:12]} 备份={md5(bak[-1])[:12]}')
else:
    warn('未找到 AGENTS.md.bak-a-plan-* 备份，跳过快照比对')
check('快照行数 ≥ 3480', norm(FULL).count('\n') >= 3480, f'lines={norm(FULL).count(chr(10))}')

# 2026-10-24：根 AGENTS.md 已瘦身为「宿主分派」文件，原「生效红线区（快照 L1–L120 逐字）」整块
# 已迁到 docs/agent/AGENTS-dsh.md（起始标记：## 📍 生效红线区；结束标记：<!-- END 生效红线区）。
# 老脚本第 2 项守的「红线区 == 快照 L2–L120 逐字」不能丢，这里按新落点原样续守。
_dsh = norm(DSH)
_dl = _dsh.split('\n')
_exp = '\n'.join(norm(FULL).split('\n')[1:120]).strip('\n')
_s = next((i + 1 for i, l in enumerate(_dl) if l.startswith('## 📍 生效红线区')), None)
_e = next((i for i, l in enumerate(_dl) if l.startswith('<!-- END 生效红线区')), None)
_act = '\n'.join(_dl[_s:_e]).strip('\n') if (_s is not None and _e is not None and _e > _s) else None
check('AGENTS-dsh.md 生效红线区块与 AGENTS-full.md L2–L120 逐字一致', _act == _exp,
      f'红线区 {len(_act):,} 字符' if _act is not None else '红线区标记缺失（## 📍 生效红线区 / <!-- END 生效红线区）')

print('== 2. AGENTS.md 结构完整（宿主分派 / 共同红线 / 按需索引）==')
ag = norm(AG)
hosts = re.findall(r'^\|\s*\*\*(.+?)\*\*\s*\|', ag, re.M)
want_hosts = ['Kimi Code', 'DSH', 'Codex', '其他宿主']
missing_hosts = [h for h in want_hosts if h not in hosts]
check('宿主分派表含 4 个宿主（' + ' / '.join(want_hosts) + '）',
      not missing_hosts, f'hosts={hosts}' + (f' / 缺={missing_hosts}' if missing_hosts else ''))

bolds = re.findall(r'^(\d+)\.\s+\*\*(.+?)\*\*', ag, re.M)
nums = [int(n) for n, _ in bolds]
want_red = ['默认不启动实机', '默认不部署', '默认不 commit', '保护素材与他人改动', '互斥锁', '不代改他人职责']
hit = [w for w in want_red if any(w in t for _, t in bolds)]
miss_red = [w for w in want_red if w not in hit]
check('共同安全红线 1–6 条齐全且编号连续', nums == [1, 2, 3, 4, 5, 6] and not miss_red,
      f'编号={nums} / 命中 {len(hit)}/{len(want_red)} 条关键词' + (f' / 缺={miss_red}' if miss_red else ''))

HEAD_IDX = '## 三、按需文档索引'
idx_rows = 0
if HEAD_IDX in ag:
    tail = ag.split(HEAD_IDX, 1)[1].split('\n## ', 1)[0]
    idx_rows = sum(1 for l in tail.split('\n') if l.startswith('|') and 'docs/' in l)
check('「按需文档索引」节存在且 ≥ 6 行有效引用', idx_rows >= 6, f'rows={idx_rows}')

print('== 3. 规则库引用有效 ==')
refs = sorted(set(re.findall(r'`(docs/[^`\s]+\.md)`', ag)))
dead = [p for p in refs if '*' not in p and not os.path.exists(os.path.join(W, p))]
empty_globs = [p for p in refs if '*' in p and not glob.glob(os.path.join(W, p))]
bad = dead + empty_globs
if bad:
    msg = f'{len(bad)} 个引用指向不存在的文件：' + ' / '.join(bad)
    if STRICT:
        check(f'{len(refs)} 条 docs 引用全部存在', False, msg)
    else:
        warn(f'{len(refs)} 条 docs 引用全部存在（默认不致命；--strict 才 FAIL）', msg)
else:
    check(f'{len(refs)} 条 docs 引用全部存在', True)

print('== 4. 体量与驻留规则 ==')
check('AGENTS.md 未超 32K 字符', len(ag) < 32000, f'{len(ag):,} 字符')
dsh = norm(DSH)
check('AGENTS-dsh.md 保留用户裁决段〈会话启动第一步：加载 using-superpowers〉',
      '会话启动第一步' in dsh and 'using-superpowers' in dsh,
      f'AGENTS-dsh.md {len(dsh):,} 字符')

print()
print('结果：' + (('全部通过' + (f'（{warns} 个警告）' if warns else '')) if ok else '有失败项'))
sys.exit(0 if ok else 1)
