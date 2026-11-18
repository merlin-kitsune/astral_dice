
d_open = open('docs/agent/AGENTS-full.md', 'rb').read().decode('utf-8')
fl = d_open.split('\n')
for i in range(0, 10):
    print(i+1, repr(fl[i])[:160])
print('...')
for i in range(115, 126):
    print(i+1, repr(fl[i])[:160])
print('elems', len(fl))
