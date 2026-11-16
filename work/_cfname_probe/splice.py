
import io, pathlib, re, subprocess, sys

root = pathlib.Path('.')
body = (root / 'work/_cfname_probe/report_body.md').read_text(encoding='utf-8')
old_src = subprocess.run(['git', 'show', 'HEAD:tools/curseforge_upload.py'], capture_output=True).stdout.decode('utf-8').splitlines()
new_src = (root / 'tools/curseforge_upload.py').read_text(encoding='utf-8').splitlines()
md_src = (root / 'docs/agent/AGENTS-dsh.md').read_text(encoding='utf-8').splitlines()
outdir = root / 'work/_cfname_probe/out'

def numbered(lines, a, b, tag):
    out = [tag]
    for i in range(a, b + 1):
        out.append('%5d  %s' % (i, lines[i - 1]))
    return '\n'.join(out)

FENCE = chr(96) * 4


def fenced(text):
    return '\n' + FENCE + '\n' + text.rstrip('\n') + '\n' + FENCE + '\n'


def rng(arg):
    parts = [x for x in arg.split('-') if x != '']
    if len(parts) == 1:
        return int(parts[0]), int(parts[0])
    return int(parts[0]), int(parts[1])


def repl(m):
    kind = m.group(1)
    arg = m.group(2)
    if kind == 'SRC':
        a, b = rng(arg)
        return fenced(numbered(new_src, a, b, '# tools/curseforge_upload.py:%d-%d' % (a, b)))
    if kind == 'OLD':
        a, b = rng(arg)
        return fenced(numbered(old_src, a, b, '# git show HEAD:tools/curseforge_upload.py  :%d-%d' % (a, b)))
    if kind == 'MD':
        a, b = rng(arg)
        return fenced(numbered(md_src, a, b, '# docs/agent/AGENTS-dsh.md:%d-%d' % (a, b)))
    if kind == 'FILE':
        return fenced((root / arg).read_text(encoding='utf-8'))
    if kind == 'OUT':
        return fenced((outdir / (arg + '.txt')).read_text(encoding='utf-8', errors='replace'))
    raise SystemExit('unknown marker ' + kind)

res = re.sub(r'\{\{(SRC|OLD|MD|FILE|OUT):([^}]+)\}\}', repl, body)
res = res.replace('\r\n', '\n')
res = res.replace('⟦', chr(96)).replace('⟧', chr(96))
target = root / 'work/cf-displayname-changelog-policy.md'
target.write_text(res, encoding='utf-8', newline='\n')
print('WROTE %s  %d B  %d lines' % (target, len(res.encode('utf-8')), res.count('\n') + 1))
