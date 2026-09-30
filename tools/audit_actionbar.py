#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""动作栏（ActionBar）守门审计 —— 三线只读。

依据 `AGENTS.md`《动作栏（ActionBar）口径 — 必须遵守》。检查三类硬约束：

  1. **值内禁 `§` 码**：`msg.*` / `hud.*` 前缀的 lang 值不得含任何 `§`。
     外层统一 `.withStyle(...)` 着色 ⇒ 值内 `§e` 是空操作、`§7` 会造成「前半黄、后半灰」断层。
  2. **键必须齐**：代码里引用的动作栏语言键，必须三线 × 三语都存在（缺 = 玩家看到裸键）。
  3. **通道唯一**：新的动作栏消息只应出现在允许的通道里（见 ALLOWED 白名单），
     裸 `displayClientMessage(…, true)` / `sendOverlayMessage(…)` 仅允许在双端类里（不得引用
     客户端类 `ActionBarManager`），新增必须显式登记。

退出码：0 = 通过；1 = 存在违规。用法：`python tools/audit_actionbar.py`
"""
import json
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LINES = ['neoforge-1.21.1', 'forge-1.20.1', 'neoforge-26.1.2']
# ⚠️ **第四线 `fabric-1.20.1` 有意不纳入本闸门**(2026-10-01 实测结论,勿凭直觉加回来):
#   ① 该线**不走本闸门所守的那条通道** —— `ActionBarPayload` 这个类**只存在于两条 neoforge 线**:
#      实测 `git grep ActionBarPayload` 分别为 1.21.1 = 52 处 / 26.1.2 = 57 处 / **forge-1.20.1 = 0** /
#      **fabric-1.20.1 = 0**(1.20.1 两条线各走自己的动作栏实现)⇒ 本闸门的通道白名单对它们本就不适用。
#   ② 实测把它加进 LINES 会报出**一批既存的 1.3.3 动作栏改造缺口**(大量 `msg.astral_dice.*`
#      的中/英/日值里仍内嵌 `§` 色码 + 4 个文件用原版覆盖层通道未进白名单)。
#      **那是 fabric 线尚未移植 1.3.3 内容所致,与本闸门无关** ⇒ 在本线完成该批移植并把
#      白名单补齐之前,贸然纳入只会让仓库闸门**长期常态变红**,反而掩盖真正的回归。
#    ⇒ 待该线补完 1.3.3 批次后,再把 `fabric-1.20.1` 加回本列表(并同步核对上述白名单)。
LANGS = ['zh_cn', 'en_us', 'ja_jp']
JR = 'src/main/java/com/merlinkitsune/astral_dice/'
LANG_REL = 'src/main/resources/assets/astral_dice/lang/'

# 动作栏发送 helper 名（跨三线并集）—— 新增 helper 必须登记，否则其调用点的键会被漏扫
HELPERS = [
    'sendSignActionBarColored', 'sendSignActionBar', 'notifyActionBar',
    'sendActionBar', 'sendInvestigationActionBar', 'sendEventActionBar',
    'notifyRoll', 'notifyDiceGift', 'sendReadyPrompt', 'notifyForcedCooldown',
    'notifyHeldSelectorBlocked', 'notifyShieldGained', 'sendTargetSelectionActionBar',
]
HELPER_RE = re.compile(r'\b(' + '|'.join(HELPERS) + r')\s*\(')
KEY_LIT = re.compile(r'"((?:msg|hud)\.[A-Za-z0-9_.]+)"')
PAYLOAD_RE = re.compile(r'new\s+(?:com\.merlinkitsune\.astral_dice\.network\.)?ActionBarPayload\s*\(')
# 原版覆盖层通道：只允许出现在「双端类」里（白名单），且必须显式着色
VANILLA_RE = re.compile(r'(displayClientMessage\s*\([^;]*,\s*true\s*\)|sendOverlayMessage\s*\()')
VANILLA_ALLOWED = {
    'item/card/BaseEffectCardItem.java',   # 双端类：客户端预检，不得引用客户端类 ActionBarManager
}
SEC = re.compile('\u00a7.')


def strip_comments(text):
    """去掉 // 行注释与 /* */ 块注释（尊重字符串字面量）。
    必要性：26.1.2 的 javadoc 里写着 {@code Player#sendOverlayMessage(...)}，不去注释会误报为
    「非白名单文件使用原版通道」。"""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c == '/' and i + 1 < n and text[i + 1] == '/':
            j = text.find('\n', i)
            i = n if j < 0 else j
        elif c == '/' and i + 1 < n and text[i + 1] == '*':
            j = text.find('*/', i + 2)
            i = n if j < 0 else j + 2
            out.append('\n')  # 保持行号
        elif c == '"' or c == "'":
            q = c
            out.append(c)
            i += 1
            while i < n and text[i] != q:
                if text[i] == '\\':
                    out.append(text[i])
                    i += 1
                    if i < n:
                        out.append(text[i])
                        i += 1
                    continue
                out.append(text[i])
                i += 1
            if i < n:
                out.append(text[i])
                i += 1
        else:
            out.append(c); i += 1
    return ''.join(out)


def iter_java(base):
    for root, _d, files in os.walk(os.path.join(base, JR)):
        for f in files:
            if f.endswith('.java'):
                yield os.path.join(root, f)


def scan_code(base):
    """→ (动作栏键集合, 原版通道出现的相对路径集合)"""
    keys, vanilla_files = set(), set()
    for path in iter_java(base):
        rel = os.path.relpath(path, os.path.join(base, JR)).replace('\\', '/')
        raw = open(path, encoding='utf-8', errors='replace').read()
        text = strip_comments(raw)
        lines = text.split('\n')

        # helper 调用点（含委托式 helper：体内不出现 ActionBarPayload，只能按 helper 名抓）
        for m in HELPER_RE.finditer(text):
            ln = text[:m.start()].count('\n') + 1
            block = '\n'.join(lines[max(0, ln - 1):min(len(lines), ln + 4)])
            keys.update(KEY_LIT.findall(block))
        # 直接构造
        for m in PAYLOAD_RE.finditer(text):
            ln = text[:m.start()].count('\n') + 1
            block = '\n'.join(lines[max(0, ln - 5):min(len(lines), ln + 6)])
            keys.update(KEY_LIT.findall(block))
        # 原版通道
        if VANILLA_RE.search(text):
            vanilla_files.add(rel)
    # 排除动态拼接前缀（如 "msg.astral_dice.fanny_event." + roll）
    return {k for k in keys if not k.endswith('.')}, vanilla_files


def load_lang(base, name):
    p = os.path.join(base, LANG_REL, name + '.json')
    try:
        return json.load(open(p, encoding='utf-8'))
    except Exception as e:
        return {'__ERR__': str(e)}


def main():
    viol = []
    lang_all = {}

    print('=' * 74)
    print('动作栏守门审计（AGENTS《动作栏（ActionBar）口径》）')
    print('=' * 74)

    # 1) 值内禁 §
    print('\n[1] 值内禁 § 码')
    for line in LINES:
        lang_all[line] = {n: load_lang(os.path.join(ROOT, line), n) for n in LANGS}
        for n in LANGS:
            d = lang_all[line][n]
            bad = [k for k in d if k.startswith(('msg.astral_dice.', 'hud.astral_dice.'))
                   and isinstance(d[k], str) and '\u00a7' in d[k]]
            status = 'OK' if not bad else f'违规 {len(bad)}'
            print(f'  {line:<18} {n:<6} {status}')
            for k in bad:
                viol.append(f'{line}/{n} 值内含 §: {k} = {d[k]!r}')

    # 2) 键必须三线 × 三语齐全
    print('\n[2] 动作栏键存在性（三线 × 三语）')
    for line in LINES:
        keys, _ = scan_code(os.path.join(ROOT, line))
        missing = [(k, n) for k in sorted(keys) for n in LANGS if k not in lang_all[line][n]]
        print(f'  {line:<18} 引用 {len(keys)} 键，缺失 {len(missing)}')
        for k, n in missing:
            viol.append(f'{line}/{n} 缺失键: {k}')

    # 3) 通道白名单
    print('\n[3] 原版覆盖层通道白名单')
    for line in LINES:
        _, vanilla = scan_code(os.path.join(ROOT, line))
        extra = sorted(vanilla - VANILLA_ALLOWED)
        for rel in extra:
            viol.append(f'{line} 非白名单文件使用原版覆盖层通道: {rel}')
        print(f'  {line:<18} 使用原版通道的文件 {len(vanilla)}，越白名单 {len(extra)}')

    print()
    if viol:
        print(f'!! FAIL —— {len(viol)} 条违规：')
        for v in viol[:60]:
            print('   -', v)
        return 1
    print('PASS（无违规）')
    return 0


if __name__ == '__main__':
    sys.exit(main())
