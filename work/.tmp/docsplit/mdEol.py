
import glob
for p in sorted(glob.glob('work/*.md'))[:6]:
    b = open(p, 'rb').read()
    crlf = b.count(b'\r\n')
    print(p, 'CRLF=%d bareLF=%d' % (crlf, b.count(b'\n') - crlf))
