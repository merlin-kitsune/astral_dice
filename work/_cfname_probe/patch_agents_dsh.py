# -*- coding: utf-8 -*-
P = 'docs/agent/AGENTS-dsh.md'
raw = open(P, 'r', encoding='utf-8', newline='').read()
assert '\r\n' in raw, 'expected CRLF'
norm = raw.replace('\r\n', '\n')
assert '\r' not in norm
lines = norm.split('\n')
anchor = '- **\u53d1\u5e03\u524d\u987b\u53d6\u5f97\u7528\u6237\u8bb8\u53ef\uff082026-11-02 \u8865\u5145\uff09**'
idx = [i for i, l in enumerate(lines) if l.startswith(anchor)]
assert len(idx) == 1, idx
i = idx[0]
bullet = ('- **CF \u7ad9\u70b9\u663e\u793a\u540d\u4e0e changelog \u53e3\u5f84\uff082026-11-02\uff09**\uff1a'
          '\u4e0a\u4f20\u4ef6\u7684\u663e\u793a\u540d\u4e00\u5f8b `Astral Dice <\u7248\u672c> (<\u52a0\u8f7d\u5668> <MC>)`'
          '\uff08\u52a0\u8f7d\u5668\u5199 `NeoForge`/`Forge`/`Fabric`\uff09\uff1b'
          '\u5c0f\u7248\u672c\u66f4\u65b0\uff08\u8865\u4e01 / hotfix / \u9884\u53d1\u5e03\uff09'
          '\u4e0d\u5728 CF \u5199 changelog\uff0c\u53ea\u6709\u4e3b\u7ebf\u7248\u672c `X.Y.0` \u624d\u9644\u5e26\u3002')
assert bullet not in norm
lines.insert(i + 1, bullet)
out = '\r\n'.join(lines)
with open(P, 'w', encoding='utf-8', newline='') as f:
    f.write(out)
chk = open(P, 'r', encoding='utf-8', newline='').read()
print('CRLF=%d bareLF=%d bareCR=%d chars=%d' % (
    chk.count('\r\n'),
    chk.count('\n') - chk.count('\r\n'),
    chk.count('\r') - chk.count('\r\n'),
    len(chk)))
print('inserted at line', i + 2)
