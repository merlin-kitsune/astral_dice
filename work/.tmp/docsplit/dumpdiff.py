
import difflib, subprocess
head = subprocess.run(['git','show',':0:docs/agent/AGENTS-full.md'],capture_output=True).stdout.decode('utf-8').split('\n')
cur = open('docs/agent/AGENTS-full.md','rb').read().decode('utf-8').split('\n')
old, new = head[2], cur[2]
out = []
out.append('OLD L3 len=%d' % len(old))
out.append('NEW L3 len=%d' % len(new))
sm = difflib.SequenceMatcher(None, old, new, autojunk=False)
for tag,i1,i2,j1,j2 in sm.get_opcodes():
    if tag == 'insert':
        out.append('INSERT@%d (%d chars):\n%s' % (i1, j2-j1, new[j1:j2]))
    elif tag == 'delete':
        out.append('DELETE@%d (%d chars):\n%s' % (i1, i2-i1, old[i1:i2]))
    elif tag == 'replace':
        out.append('REPLACE old[%d:%d] -> new[%d:%d]:\n  OLD:%s\n  NEW:%s' % (i1,i2,j1,j2, old[i1:i2], new[j1:j2]))
out.append('')
out.append('NEW L3 head 60: %s' % new[:60])
out.append('NEW L3 tail 60: %s' % new[-60:])
out.append('')
out.append('--- inserted 9 lines (new L4..L12) ---')
for k in range(3, 12):
    out.append('%d| %s' % (k+1, cur[k]))
open('work/.tmp/docsplit/full_diff_detail.txt','w',encoding='utf-8').write('\n'.join(out))
print('written')
