
import re, subprocess
d = subprocess.run(['git','--no-pager','diff','docs/agent/AGENTS-full.md'],capture_output=True).stdout.decode('utf-8')
cur = None
stats = []
for line in d.split('\n'):
    if line.startswith('@@'):
        cur = {'hdr': line, 'add': 0, 'del': 0}
        stats.append(cur)
    elif cur is not None:
        if line.startswith('+++') or line.startswith('---'):
            continue
        if line.startswith('+'): cur['add'] += 1
        elif line.startswith('-'): cur['del'] += 1
for s in stats:
    print(s['hdr'], ' => +%d -%d' % (s['add'], s['del']))
print('total +%d -%d' % (sum(s['add'] for s in stats), sum(s['del'] for s in stats)))
