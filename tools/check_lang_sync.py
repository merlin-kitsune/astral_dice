#!/usr/bin/env python3
"""lang 同步检查（Linux 无 旧 shell，tools/check_lang_sync.py 在本机不可用）。

与 tools/check_lang_sync.py **同判定能力、同退出码**，逐条对齐 ps1 语义：

1. **键集**：en_us.json 与 zh_cn.json 双向一致；ja_jp.json（存在即校验）
   以 **zh_cn 为基线**一致（ps1 :456-470）。
2. **未转义字面百分号**：值里出现单个 %（既不是 %%，也不是 %[sdbfxoeg]
   或 %<数字>$[sdbfxoeg]）⇒ [FAIL] + 退出码 1（ps1 :490-519）。
   该值经 I18n.get/String.format 会抛 UnknownFormatConversionException ⇒ 客户端崩溃；
   字面 % 必须写成 %%。
3. **结构标记**（占位符元组 / 换行数 / 颜色码数，ps1 :113-120）在 zh vs en、
   zh vs ja 之间不一致 ⇒ [WARN]，**不改变退出码**（ps1 :521-534）。

用法：python3 tools/check_lang_sync.py [--line neoforge-1.21.1 ...]
退出码：0 = 全部一致（WARN 不计入）；1 = 键集不一致或存在未转义字面百分号。
"""

import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = ["neoforge-1.21.1", "forge-1.20.1", "neoforge-26.1.2", "fabric-1.20.1",
         "fabric-1.21.1"]
LANGS = ["zh_cn", "en_us", "ja_jp"]
LANG_REL = os.path.join("src", "main", "resources", "assets", "astral_dice", "lang")

# ps1 :116 —— 结构标记用的占位符抽取（刻意不含 %<数字>$x 形式，与 ps1 逐字一致）
PLACEHOLDER_RE = re.compile(r"%%|%[sdbfxoeg]")
# ps1 :118 —— 颜色码：§[0-9a-fk-or]，其余 §. 兜底（. 不跨换行，与 .NET 默认同口径）
COLOR_RE = re.compile("\u00a7[0-9a-fk-or]|\u00a7.")
# ps1 :512 —— 未转义百分号的合法单元：%% / %[sdbfxoeg] / %<数字>$[sdbfxoeg]
PERCENT_UNIT_RE = re.compile(r"%(?:%|\d+\$[sdbfxoeg]|[sdbfxoeg])")


def setup_streams():
    """显式 UTF-8（不依赖 locale）与显式 LF（不做 os.linesep 转换），对齐 ps1 的 Write-Stdout。"""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="strict", newline="\n")
        except (AttributeError, ValueError, OSError):
            pass


setup_streams()


def out(text):
    sys.stdout.write(text + "\n")


def lang_dir_for(line):
    return os.path.join(ROOT, line, LANG_REL)


def load(lang_dir, lang):
    """读一份 lang。缺失 => (None, None)；存在但不可读/不可解析 => (None, 错误串)。"""
    path = os.path.join(lang_dir, lang + ".json")
    if not os.path.exists(path):
        return None, None
    try:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
    except (OSError, ValueError) as exc:
        return None, str(exc)
    if not isinstance(data, dict):
        return None, "顶层不是 JSON 对象"
    return data, None


def structure_marker(text):
    """ps1 :113-120 Get-StructureMarkerRepr 的等值实现。

    返回 (占位符元组, 换行数, 颜色码数) 的 Python repr，例如 ((), 1, 10) 或
    (('%s',), 0, 1)，与 ps1 逐字符同形 —— 占位符只可能由 %/s/d/b/f/x/o/e/g 组成，
    不会触发 Python repr 的双引号形态。
    """
    placeholders = tuple(PLACEHOLDER_RE.findall(text))
    return repr((placeholders, text.count("\n"), len(COLOR_RE.findall(text))))


def find_percent_violations(text):
    """ps1 :507-519 —— 从左到右消费，返回每个「非法单个 %」的码位下标。

    合法单元 %% / %<数字>$[sdbfxoeg] / %[sdbfxoeg] 整体跳过；其余 % 记为违规。
    """
    bad = []
    i = 0
    while i < len(text):
        if text[i] != "%":
            i += 1
            continue
        match = PERCENT_UNIT_RE.match(text, i)
        if match is not None:
            i = match.end()
            continue
        bad.append(i)
        i += 1
    return bad


def codepoint_window(text, index, back=12, forward=12):
    """ps1 :66-82 Get-PyCodePointWindow —— Python 切片天然按码位计数，等价于 max(0,i-12)..i+12。"""
    return text[max(0, index - back):index + forward]


def check_lang_dir(lang_dir, label):
    """检查单个 lang 目录，按 ps1 顺序打印。返回 (errors, percent_errors, warn_count)。"""
    table, broken = {}, []
    for lang in LANGS:
        data, err = load(lang_dir, lang)
        table[lang] = data
        if err is not None:
            broken.append((lang, err))
    # ps1 :404-419 —— 读失败即 FAIL + 返回 1（ja_jp 存在才读，但读到坏文件同样判 1）
    if broken:
        for lang, err in broken:
            out("[FAIL] %s/%s.json 无法读取/解析: %s" % (label, lang, err))
        return len(broken), 0, 0
    present = {lang: data for lang, data in table.items() if data is not None}
    # ps1 :397-399 —— zh_cn.json 与 en_us.json 是必需语言（缺失即 FAIL，不是跳过）
    missing_files = [lang for lang in ("zh_cn", "en_us") if lang not in present]
    if missing_files:
        for lang in missing_files:
            out("[FAIL] %s/%s.json 缺失（zh_cn.json / en_us.json 为必需语言）" % (label, lang))
        return len(missing_files), 0, 0

    # ps1 :456-470 —— ja_jp（存在即校验）一律以 zh_cn 为基线
    base_lang = "zh_cn"
    base_keys = set(present[base_lang])

    errors = 0
    for lang in LANGS:
        if lang not in present or lang == base_lang:
            continue
        keys = set(present[lang])
        missing = sorted(base_keys - keys)
        extra = sorted(keys - base_keys)
        if not missing and not extra:
            continue
        errors += 1
        out("[FAIL] %s/%s.json 相对 %s: 缺 %d 多 %d" % (label, lang, base_lang, len(missing), len(extra)))
        for key in missing[:5]:
            out("        - " + key)
        for key in extra[:5]:
            out("        + " + key)

    percent_errors = 0
    warn_count = 0
    zh = present.get("zh_cn")
    en = present.get("en_us")
    ja = present.get("ja_jp")
    if isinstance(zh, dict) and isinstance(en, dict):
        # ps1 :472-535 —— 逐键：先百分号（zh/en；ja 若存在且含该键），再结构标记（zh vs en、zh vs ja）
        for key in sorted(set(zh) & set(en)):
            zh_val = zh[key]
            en_val = en[key]
            # ps1 :486-488 —— 任一侧非字符串则整键跳过（百分号与结构标记都不判）
            if not isinstance(zh_val, str) or not isinstance(en_val, str):
                continue
            ja_val = ja.get(key) if isinstance(ja, dict) else None
            pairs = [("zh", "zh_cn", zh_val), ("en", "en_us", en_val)]
            if isinstance(ja_val, str):
                pairs.append(("ja", "ja_jp", ja_val))
            for name, lang_name, text in pairs:
                for index in find_percent_violations(text):
                    percent_errors += 1
                    window = codepoint_window(text, index)
                    out(
                        "[FAIL] %s/%s.json %s(%s): 含未转义字面百分号"
                        "(必须写成 %%%%,否则 I18n.get/String.format 抛 "
                        "UnknownFormatConversionException 并崩溃客户端): ...%s..."
                        % (label, lang_name, key, name, window)
                    )
            zh_marker = structure_marker(zh_val)
            en_marker = structure_marker(en_val)
            if zh_marker != en_marker:
                warn_count += 1
                out("[WARN] %s: 结构标记不一致(占位符/换行/颜色码) zh=%s en=%s" % (key, zh_marker, en_marker))
            if isinstance(ja_val, str):
                ja_marker = structure_marker(ja_val)
                if zh_marker != ja_marker:
                    warn_count += 1
                    out("[WARN] %s: 结构标记不一致(占位符/换行/颜色码) zh=%s ja=%s" % (key, zh_marker, ja_marker))

    if errors:
        out(
            "\n%s: 语言文件未同步:请把 zh_cn.json 的手动修改同步至 en_us.json"
            "(同一 key 中英对应)后再提交。" % label
        )
    if percent_errors:
        out(
            "\n%s: 共 %d 处未转义字面百分号:lang 值里的字面 %% 必须写成 %%%%"
            "(见 AGENTS.md「lang 值中的字面百分号必须写成 %%%%」)。" % (label, percent_errors)
        )
    if errors or percent_errors:
        return errors + percent_errors, percent_errors, warn_count
    counts = " ".join("%s=%d" % (lang, len(present[lang])) for lang in LANGS if lang in present)
    out(
        "[OK]   %s: %s 键集一致；未转义字面百分号 0 处；结构标记告警 %d 条（WARN 不改变退出码）"
        % (label, counts, warn_count)
    )
    return 0, 0, warn_count


def main(argv=None):
    parser = argparse.ArgumentParser(description="lang 键集 / 未转义字面百分号 / 结构标记 同步检查")
    parser.add_argument(
        "--line",
        action="append",
        default=None,
        metavar="LINE",
        help="只检查指定子项目线（可重复）；缺省检查四条线全部",
    )
    args = parser.parse_args(argv)
    failed = False
    for line in args.line or LINES:
        errors, _percent_errors, _warn_count = check_lang_dir(lang_dir_for(line), line)
        if errors:
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
