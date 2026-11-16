
import hashlib, sys
p = 'docs/agent/verify-split.py'
raw = open(p, 'rb').read()
txt = raw.decode('utf-8')
print('orig bytes', len(raw), 'CRLF', txt.count('\r\n'), 'bareLF', txt.count('\n') - txt.count('\r\n'))
t = txt.replace('\r\n', '\n')

def rep(old, new):
    global t
    c = t.count(old)
    if c != 1:
        print('ABORT count=%d for %r' % (c, old[:90]))
        sys.exit(2)
    t = t.replace(old, new)
    print('OK  ', old[:70].replace('\n', '\\n'))

rep('共同安全红线 1–6 条 / 按需文档索引节；',
    '共同安全红线 1–7 条 / 按需文档索引节；')

rep('与快照 L2–L120 仍逐字一致；',
    '与快照 L2–L129 仍逐字一致；')

rep('# 老脚本第 2 项守的「红线区 == 快照 L2–L120 逐字」不能丢，这里按新落点原样续守。\n',
    '# 老脚本第 2 项守的「红线区 == 快照 L2–L120 逐字」不能丢，这里按新落点原样续守。\n'
    '# 2026-11-02：dsh 侧红线区内新增整节「## 发布流程硬规则（2026-11-02 用户裁决，强制）」（+9 行）并同步进\n'
    '# AGENTS-full.md，故比较区间的下界随之后移 L2–L120 → L2–L129（判据仍是逐字节相等，未放宽）。\n')

rep("norm(FULL).split('\\n')[1:120]",
    "norm(FULL).split('\\n')[1:129]")

rep("'AGENTS-dsh.md 生效红线区块与 AGENTS-full.md L2–L120 逐字一致'",
    "'AGENTS-dsh.md 生效红线区块与 AGENTS-full.md L2–L129 逐字一致'")

rep("want_red = ['默认不启动实机', '默认不部署', '默认不 commit', '保护素材与他人改动', '互斥锁', '不代改他人职责']",
    "want_red = ['默认不启动实机', '默认不部署', '默认不 commit', '保护素材与他人改动', '互斥锁', '不代改他人职责',\n            '没有详细测试就没有发布版本']")

rep("'共同安全红线 1–6 条齐全且编号连续'",
    "'共同安全红线 1–7 条齐全且编号连续'")

rep('nums == [1, 2, 3, 4, 5, 6] and not miss_red',
    'nums == [1, 2, 3, 4, 5, 6, 7] and not miss_red')

out = t.replace('\n', '\r\n')
open(p, 'wb').write(out.encode('utf-8'))
b = open(p, 'rb').read()
print('after bytes', len(b), 'CRLF', b.count(b'\r\n'), 'bareLF', b.count(b'\n') - b.count(b'\r\n'))
print('py md5', hashlib.md5(b).hexdigest())
