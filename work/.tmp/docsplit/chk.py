
b = open('work/docs-split-gate-fix.md','rb').read()
print('bytes', len(b), 'CRLF', b.count(b'\r\n'), 'bareLF', b.count(b'\n')-b.count(b'\r\n'))
s = b.decode('utf-8')
for probe in ["split('\\n')[1:120]", "split('\\n')[1:129]", "want_red = ['默认不启动实机'", "结论：门禁全绿"]:
    print(probe[:30], '->', probe in s)
print('LASTLINE:', s.rstrip('\n').split('\n')[-1][:120])
