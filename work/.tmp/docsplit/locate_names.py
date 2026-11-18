
import re
t = open('docs/agent/AGENTS-full.md','rb').read().decode('utf-8').split('\n')
for i, l in enumerate(t, 1):
    if '立牌' in l and '英日命名规则' in l and l.startswith('#'):
        print('HEADING line', i, l[:80])
    if '5178' in l: pass
print('total lines', len(t))
# find last line of that section: next heading after it
idx = next(i for i,l in enumerate(t) if l.startswith('#') and '英日命名规则' in l)
print('section start (1-based)', idx+1)
nxt = next((i for i in range(idx+1, len(t)) if t[i].startswith('## ')), None)
print('next ## heading at', (nxt+1) if nxt else None)
