#!/usr/bin/env python3
"""lang 键集同步检查（Linux 无 pwsh，`tools/check_lang_sync.ps1` 在本机不可用）。

对每条线比较 `assets/astral_dice/lang/{zh_cn,en_us,ja_jp}.json` 的键集：
三者必须完全相等，否则游戏内会显示未翻译键。

用法：python3 tools/check_lang_sync.py [--line neoforge-1.21.1 ...]
退出码：0 = 全部一致；1 = 有差异。
"""

import argparse
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2", "fabric-1.20.1"]
LANGS = ["zh_cn", "en_us", "ja_jp"]


def load(line, lang):
    path = os.path.join(ROOT, line, "src/main/resources/assets/astral_dice/lang", f"{lang}.json")
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--line", action="append", default=None)
    args = ap.parse_args()
    lines = args.line or LINES
    failed = False
    for line in lines:
        table = {lang: load(line, lang) for lang in LANGS}
        present = {lang: data for lang, data in table.items() if data is not None}
        if len(present) < 2:
            print(f"[SKIP] {line}: lang 文件不足两份")
            continue
        base_lang = "en_us" if "en_us" in present else next(iter(present))
        base = set(present[base_lang])
        ok = True
        for lang, data in present.items():
            keys = set(data)
            missing = sorted(base - keys)
            extra = sorted(keys - base)
            if missing or extra:
                ok = False
                failed = True
                print(f"[FAIL] {line}/{lang}.json 相对 {base_lang}: 缺 {len(missing)} 多 {len(extra)}")
                for key in missing[:5]:
                    print(f"        - {key}")
                for key in extra[:5]:
                    print(f"        + {key}")
        if ok:
            print(f"[OK]   {line}: {base_lang}={len(base)} 键，{len(present)} 份语言键集一致")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
