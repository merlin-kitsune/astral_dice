
import hashlib, difflib
p = 'docs/agent/AGENTS-full.md'
raw = open(p, 'rb').read()
txt = raw.decode('utf-8')
print('original bytes', len(raw), 'CRLF-count', txt.count('\r\n'))
fl = txt.replace('\r\n', '\n').split('\n')
print('original elements', len(fl))

d = open('docs/agent/AGENTS-dsh.md', 'rb').read().decode('utf-8').replace('\r\n', '\n').split('\n')
ins = d[157:167]   # dsh 1-based L158..L167 = 10 lines (blockquote + 9 new lines), verbatim
assert len(ins) == 10, len(ins)
assert ins[0].startswith('> **'), ins[0][:40]
assert ins[1] == ''
assert ins[2].startswith('## '), ins[2][:40]
assert ins[3] == ''
assert all(ins[k].startswith('- **') for k in range(4, 10)), [ins[k][:20] for k in range(4,10)]

old2 = fl[2]
assert old2.startswith('> **'), old2[:40]
sm = difflib.SequenceMatcher(None, old2, ins[0], autojunk=False)
for tag, i1, i2, j1, j2 in sm.get_opcodes():
    if tag != 'equal':
        print('INLINE-DIFF', tag)
        print('  old:', repr(old2[i1:i2]))
        print('  new:', repr(ins[0][j1:j2]))

new_fl = fl[:2] + ins + fl[3:]
assert len(new_fl) == len(fl) + 9, (len(new_fl), len(fl))
open(p, 'wb').write('\n'.join(new_fl).encode('utf-8'))

b = open(p, 'rb').read()
print('after bytes', len(b), 'CRLF-count', b.count(b'\r\n'), 'bareLF', b.count(b'\n') - b.count(b'\r\n'))
nl = b.decode('utf-8').split('\n')
exp = '\n'.join(nl[1:129]).strip('\n')
act = '\n'.join(d[155:285]).strip('\n')
print('zone FULL L2..L129 == dsh red zone:', exp == act, 'chars', len(exp), len(act))
print('full md5', hashlib.md5(b).hexdigest())
