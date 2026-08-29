#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
语言文件同步检查(zh_cn.json <-> en_us.json)。

用法:
    python tools/check_lang_sync.py [--lang-dir <assets/astral_dice/lang>]

规则:
- zh_cn.json 与 en_us.json 的 key 集合必须完全一致(新增/删除 key 必须同步两侧)。
- 每个对应 key 的结构标记(%%/%s/%d 等占位符数量、换行数量)差异仅打印警告,
  不导致失败(中英措辞可不同,但结构应尽量一致)。
退出码:0 = 通过;1 = key 不一致或 JSON 解析失败。
"""
import argparse
import json
import re
import sys
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description="Check zh_cn/en_us lang key parity")
    parser.add_argument("--lang-dir", default="src/main/resources/assets/astral_dice/lang")
    args = parser.parse_args()

    lang_dir = Path(args.lang_dir)
    zh_path = lang_dir / "zh_cn.json"
    en_path = lang_dir / "en_us.json"
    try:
        zh = json.loads(zh_path.read_text(encoding="utf-8"))
        en = json.loads(en_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"[FAIL] 无法读取/解析语言文件: {exc}")
        return 1

    zh_keys = set(zh)
    en_keys = set(en)
    missing_in_en = sorted(zh_keys - en_keys)
    extra_in_en = sorted(en_keys - zh_keys)

    errors = 0
    if missing_in_en:
        errors += 1
        print("[FAIL] 以下 key 存在于 zh_cn.json 但缺失于 en_us.json(请在 en_us.json 补充对应英文):")
        for key in missing_in_en:
            print("  - " + key)
    if extra_in_en:
        errors += 1
        print("[FAIL] 以下 key 存在于 en_us.json 但缺失于 zh_cn.json(请同步删除或补回中文):")
        for key in extra_in_en:
            print("  - " + key)

    placeholder_re = re.compile(r"%%|%[sdbfxoeg]")
    for key in sorted(zh_keys & en_keys):
        zh_val = zh[key]
        en_val = en[key]
        if not isinstance(zh_val, str) or not isinstance(en_val, str):
            continue

        def markers(text: str):
            return (
                tuple(placeholder_re.findall(text)),
                text.count("\n"),
                len(re.findall(r"\u00a7[0-9a-fk-or]|\u00a7.", text)),
            )

        zm = markers(zh_val)
        em = markers(en_val)
        if zm != em:
            print(
                f"[WARN] {key}: 结构标记不一致(占位符/换行/颜色码) "
                f"zh={zm} en={em}"
            )

    if errors:
        print("\n语言文件未同步:请把 zh_cn.json 的手动修改同步至 en_us.json(同一 key 中英对应)后再提交。")
        return 1
    print(f"OK: zh_cn.json({len(zh_keys)} keys) 与 en_us.json({len(en_keys)} keys) key 完全一致。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
