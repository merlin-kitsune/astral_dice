
import hashlib
for p in ['docs/agent/AGENTS-full.md', 'docs/agent/AGENTS-dsh.md', 'docs/agent/verify-split.py']:
    b = open(p, 'rb').read()
    crlf = b.count(b'\r\n')
    lf = b.count(b'\n')
    cr = b.count(b'\r')
    print('%s bytes=%d CRLF=%d bare_LF=%d CR=%d md5=%s' % (p, len(b), crlf, lf - crlf, cr - crlf, hashlib.md5(b).hexdigest()))
