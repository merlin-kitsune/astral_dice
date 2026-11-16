
import io, pathlib, subprocess, re
root = pathlib.Path('.')
d = root / 'work/_cfname_probe/report_data'
d.mkdir(parents=True, exist_ok=True)

def sh(cmd):
    p = subprocess.run(cmd, shell=True, capture_output=True)
    return p.stdout

(d / 'py.diff').write_bytes(sh('git diff -- tools/curseforge_upload.py'))
(d / 'md.diff').write_bytes(sh('git diff -- docs/agent/AGENTS-dsh.md'))

src = (root / 'tools/curseforge_upload.py').read_text(encoding='utf-8').splitlines()
def block(a, b):
    out = []
    for i in range(a, b + 1):
        out.append('%5d  %s' % (i, src[i - 1]))
    return '\n'.join(out)

(d / 'funcs.txt').write_text(
    '# :600-607  display_path（显示路径统一正斜杠）\n' + block(600, 607) +
    '\n\n# :684-694  loader_display_label\n' + block(684, 694) +
    '\n\n# :697-710  default_display_name\n' + block(697, 710) +
    '\n\n# :713-720  is_mainline_version\n' + block(713, 720) +
    '\n\n# :723-748  decide_changelog\n' + block(723, 748) + '\n',
    encoding='utf-8', newline='\n')

(d / 'meta.txt').write_text(
    '# :1057-1091  build_jobs（显示名与 changelog 的决策点）\n' + block(1057, 1091) +
    '\n\n# :1092-1106  print_jobs_plan\n' + block(1092, 1106) +
    '\n\n# :1107-1122  upload_metadata\n' + block(1107, 1122) + '\n',
    encoding='utf-8', newline='\n')

# 行尾统计
def eol(p):
    b = (root / p).read_bytes()
    crlf = b.count(b'\r\n')
    bare_lf = b.count(b'\n') - crlf
    bare_cr = b.count(b'\r') - crlf
    return p, crlf, bare_lf, bare_cr, len(b)

lines = []
for p in ['tools/curseforge_upload.py', 'docs/agent/AGENTS-dsh.md']:
    name, c, lf, cr, n = eol(p)
    lines.append('%s  CRLF=%d  bareLF=%d  bareCR=%d  bytes=%d' % (name, c, lf, cr, n))
(d / 'eol.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8', newline='\n')

pol = subprocess.run('python work/_cfname_probe/check_policy.py', shell=True, capture_output=True)
(d / 'policy.txt').write_bytes(pol.stdout + (pol.stderr or b''))
print('data ready')
print((d / 'eol.txt').read_text(encoding='utf-8'))
