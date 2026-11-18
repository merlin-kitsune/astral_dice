
import hashlib, subprocess
head = subprocess.run(['git','show',':0:docs/agent/AGENTS-full.md'],capture_output=True).stdout.decode('utf-8')
hl = head.split('\n')
old_bq = hl[2]
print('HEAD line3 len', len(old_bq), old_bq[:50])

cur = open('docs/agent/AGENTS-full.md','rb').read().decode('utf-8').split('\n')
new_bq = cur[2]
print('CUR line3 len', len(new_bq))

# reconstruct pre-patch file
restored = cur[:2] + [old_bq] + cur[12:]
txt = '\n'.join(restored)
h = hashlib.md5(txt.encode('utf-8')).hexdigest()
print('reconstructed md5', h)
print('pre-patch md5 expected 54bbf530a7fb787c49ba07bafda134c5')
print('ROUNDTRIP MATCH:', h == '54bbf530a7fb787c49ba07bafda134c5')
# confirm the only added chars vs old blockquote
import difflib
sm = difflib.SequenceMatcher(None, old_bq, new_bq, autojunk=False)
for tag,i1,i2,j1,j2 in sm.get_opcodes():
    if tag!='equal':
        print(tag, 'pos', i1, 'inserted len', j2-j1, 'deleted len', i2-i1)
