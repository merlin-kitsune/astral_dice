# -*- coding: utf-8 -*-
import os
os.chdir(r'D:/MCProject/astral_dice_multiloader')
NL = bytes([13, 10])
HOT = b'## 1.5.0-hotfix (2026-11-02)' + NL
ANCHOR = b'## 1.5.0 (2026-11-02)' + NL

ZH = [
  '## 1.5.0-hotfix (2026-11-02)',
  '',
  '### 已修复BUG',
  '',
  '- **修复 Fabric 1.20.1 的 `enable_chip_recipes` 开关此前无效**：该开关此前只被代码读取，筹码配方本身没有生成',
  '  条件块，因此无论开关怎么配置，67 条筹码配方始终全部注册；现改用 Fabric 官方资源条件',
  '  （`fabric:load_conditions`）门控筹码配方，67 条配方与其对应的 67 条进度（advancements）同步受控，',
  '  改配置后重启即生效。',
  '',
  '### 玩家可见变更',
  '',
  '- **Fabric 1.20.1 版本号定为 `1.5.0-hotfix`**：仅本线升为热修号，另三条线（NeoForge 1.21.1 / Forge 1.20.1 /',
  '  NeoForge 26.1.2）仍为 `1.5.0`；互通号仍是 `1.5`，与三条 1.5.0 线照旧可互相联机。',
  '',
]

EN = [
  '## 1.5.0-hotfix (2026-11-02)',
  '',
  '### Bug Fixes',
  '',
  '- **Fixed: the `enable_chip_recipes` switch on Fabric 1.20.1 had no effect** — the switch was read by the code,',
  '  but the chip recipes carried no condition block at all, so all 67 chip recipes were always registered no',
  '  matter how the option was set; the chip recipes are now gated by Fabric official resource condition',
  '  (`fabric:load_conditions`), with their 67 matching advancements gated the same way, and the option takes',
  '  effect after a restart.',
  '',
  '### Player-facing changes',
  '',
  '- **The Fabric 1.20.1 build is now versioned `1.5.0-hotfix`** — only this line moves to a hotfix number; the',
  '  other three lines (NeoForge 1.21.1 / Forge 1.20.1 / NeoForge 26.1.2) stay at `1.5.0`. The interop version is',
  '  still `1.5`, so it can still play together with the three 1.5.0 lines.',
  '',
]

def fix(path, lines, expect_before):
    raw = open(path, 'rb').read()
    if HOT in raw:
        i = raw.index(HOT)
        j = raw.index(ANCHOR, i)
        raw = raw[:i] + raw[j:]
        open(path, 'wb').write(raw)
        raw = open(path, 'rb').read()
    assert len(raw) == expect_before, (path, len(raw), expect_before)
    assert raw.count(HOT) == 0
    assert raw.count(ANCHOR) == 1
    i = raw.index(ANCHOR)
    payload = NL.join(l.encode('utf-8') for l in lines) + NL
    new = raw[:i] + payload + raw[i:]
    open(path, 'wb').write(new)
    chk = open(path, 'rb').read()
    assert chk.count(bytes([13, 13, 10])) == 0
    assert chk.count(bytes([10])) == chk.count(NL), 'bare LF appeared'
    print('patched', path, expect_before, '->', len(chk))

fix('CHANGELOG_ZH.md', ZH, 508368)
fix('CHANGELOG.md', EN, 567956)
