#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""离线断言复算：按 mt_assert.py 的语义（log / absent + source）对已落盘日志重跑用例断言。

用途：在不重启游戏的前提下反复校准断言正则。

本脚本是 scripts/test/tools/lp_offline_assert.ps1（41 行，随 pwsh 资产退役删除）的等价实现。
判据差异：ps1 用 .NET [regex]，本脚本用 python re —— 两者都是「Search（任意位置匹配）」语义；
极端正则（lookbehind 变长、.NET 专属语法）可能表现不同，本脚本不做语法转译，报错即 FAIL 并原样打印。

用法:
    python scripts/test/tools/lp_offline_assert.py --case <用例名或路径> --version <线>
    python scripts/test/tools/lp_offline_assert.py -Case <用例> -Version <线>      # pwsh 风格

输出:
    MT_OFFLINE_ASSERT_INPUT: case=<名> asserts=<n> latest=<字节>B debug=<字节>B
      [PASS] #<i> [log|absent/<latest|debug>] <pattern>
      [FAIL] ...
    MT_OFFLINE_ASSERT: case=<名> log=<n> absent=<n> FAIL=<n>
退出码：0 = 无 FAIL；1 = 有 FAIL。
"""
import argparse
import json
import os
import re
import sys


def repo_root():
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.abspath(os.path.join(here, '..', '..', '..'))


def read_text(path):
    if not os.path.isfile(path):
        return ''
    with open(path, 'r', encoding='utf-8', errors='replace', newline='') as f:
        return f.read()


def main():
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace', newline='\n')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace', newline='\n')
    except Exception:
        pass
    ap = argparse.ArgumentParser(add_help=True, description='离线断言复算')
    ap.add_argument('-Case', '--case', dest='case', required=True)
    ap.add_argument('-Version', '--version', dest='version', required=True)
    a = ap.parse_args()

    root = repo_root()
    case_path = a.case if os.path.isabs(a.case) else os.path.join(root, 'scripts', 'test', 'cases', a.case)
    if not os.path.isfile(case_path):
        sys.stderr.write('MT_OFFLINE_ASSERT: ERROR — 用例不存在: ' + case_path + '\n')
        return 1
    with open(case_path, 'r', encoding='utf-8', errors='replace') as f:
        doc = json.load(f)

    logs = os.path.join(root, 'run', a.version, 'logs')
    latest = read_text(os.path.join(logs, 'latest.log'))
    debug = read_text(os.path.join(logs, 'debug.log'))
    case_name = os.path.basename(case_path)

    asserts = doc.get('asserts') or []
    print('MT_OFFLINE_ASSERT_INPUT: case=' + case_name + ' asserts=' + str(len(asserts))
          + ' latest=' + str(len(latest.encode('utf-8'))) + 'B debug=' + str(len(debug.encode('utf-8'))) + 'B')

    i = 0
    fail = 0
    nlog = 0
    nabs = 0
    for item in asserts:
        i += 1
        t = str(item.get('type') or '')
        if t not in ('log', 'absent'):
            continue
        src = str(item.get('source') or 'latest')
        text = debug if src == 'debug' else latest
        pattern = item.get('pattern')
        pattern = '' if pattern is None else str(pattern)
        try:
            hit = re.search(pattern, text) is not None
            err = None
        except re.error as e:
            hit = False
            err = str(e)
        if t == 'log':
            nlog += 1
            ok = hit
        else:
            nabs += 1
            ok = not hit
        if ok:
            print('  [PASS] #' + str(i) + ' [' + t + '/' + src + '] ' + pattern)
        else:
            fail += 1
            extra = '' if err is None else '  (正则错误: ' + err + ')'
            print('  [FAIL] #' + str(i) + ' [' + t + '/' + src + '] ' + pattern + extra)

    print('MT_OFFLINE_ASSERT: case=' + case_name + ' log=' + str(nlog) + ' absent=' + str(nabs)
          + ' FAIL=' + str(fail))
    return 1 if fail > 0 else 0


if __name__ == '__main__':
    sys.exit(main())
