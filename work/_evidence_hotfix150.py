# -*- coding: utf-8 -*-
import os, subprocess, sys
os.chdir(r'D:/MCProject/astral_dice_multiloader')
def run(cmd):
    p = subprocess.run(cmd, shell=True, capture_output=True)
    return p.returncode, p.stdout.decode('utf-8', 'replace'), p.stderr.decode('utf-8', 'replace')
print('=== lang sync (utf-8) ===')
rc, out, err = run('python -X utf8 tools/check_lang_sync.py')
print('exit', rc)
print(out)
print('=== gradle.properties 20-31 ===')
lines = open('fabric-1.20.1/gradle.properties', 'rb').read().split(b'\n')
for i in range(19, 31):
    print('%3d| %s' % (i+1, lines[i].decode('utf-8')))
print('=== line endings ===')
for p in ['fabric-1.20.1/gradle.properties','CHANGELOG.md','CHANGELOG_ZH.md','release/1.5.0-hotfix/PLAYER_CHANGELOG.md','release/1.5.0-hotfix/PLAYER_CHANGELOG_ZH.md']:
    b = open(p, 'rb').read()
    crlf = b.count(bytes([13,10])); lf = b.count(bytes([10])); crcrlf = b.count(bytes([13,13,10]))
    print('  %-52s bytes=%7d LF=%5d CRLF=%5d bareLF=%5d CRCRLF=%d' % (p, len(b), lf, crlf, lf-crlf, crcrlf))
print('=== git status (my files) ===')
rc, out, err = run('git -C D:\\MCProject\\astral_dice_multiloader status --porcelain -- fabric-1.20.1/gradle.properties CHANGELOG.md CHANGELOG_ZH.md release/1.5.0-hotfix work')
print(out or '(clean)')
print('=== git diff --stat (mine) ===')
rc, out, err = run('git -C D:\\MCProject\\astral_dice_multiloader diff --stat -- fabric-1.20.1/gradle.properties CHANGELOG.md CHANGELOG_ZH.md')
print(out)
print('=== git diff gradle.properties ===')
rc, out, err = run('git -C D:\\MCProject\\astral_dice_multiloader diff -U2 -- fabric-1.20.1/gradle.properties')
print(out)
print('=== other three lines versions ===')
for p in ['forge-1.20.1/gradle.properties','neoforge-1.21.1/gradle.properties','neoforge-26.1.2/gradle.properties']:
    for i, l in enumerate(open(p, 'rb').read().split(b'\n')):
        if l.startswith(b'mod_version='):
            print('  %-38s line %d: %s' % (p, i+1, l.decode()))
