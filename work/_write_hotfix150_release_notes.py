# -*- coding: utf-8 -*-
import os
os.chdir(r'D:/MCProject/astral_dice_multiloader')
NL = bytes([13, 10])

ZH = [
  '# 星之骰戏 (Astral Dice) 1.5.0-hotfix 玩家更新日志',
  '',
  '已发布：1.5.0-hotfix —— **只针对 1.20.1 / Fabric 一个版本**的热修，其余三个版本（NeoForge 1.21.1 / Forge 1.20.1 /',
  '  NeoForge 26.1.2）仍为 1.5.0，本次没有改动它们。',
  '',
  '## 修了什么',
  '',
  '- **配置里的「筹码配方」开关以前没有效果**：不管把它打开还是关掉，67 条筹码配方都照样能合出来，开关等于没起作用。',
  '  现在它真的管用了 —— 关掉之后，这些筹码配方会一起隐藏（配方书里对应的条目也一起隐藏）。',
  '',
  '## 怎么生效',
  '',
  '- 改完配置后**需要重启游戏**才会生效；游戏里直接改不会立刻起作用。',
  '- 本热修只影响 1.20.1 的 Fabric 版本。用另外三个版本（NeoForge 1.21.1 / Forge 1.20.1 / NeoForge 26.1.2）的玩家',
  '  **不需要更新**，那些版本不受影响。',
  '- 与另外三个 1.5.0 版本依然可以正常联机，互通没有变化。',
  '',
]

EN = [
  '# Astral Dice 1.5.0-hotfix — Player Changelog',
  '',
  'Released: 1.5.0-hotfix — a hotfix for **the 1.20.1 / Fabric build only**. The other three builds (NeoForge 1.21.1 /',
  '  Forge 1.20.1 / NeoForge 26.1.2) stay at 1.5.0 and are untouched by this hotfix.',
  '',
  '## What was fixed',
  '',
  '- **The “chip recipes” option in the config did nothing**: whether it was on or off, all 67 chip recipes could',
  '  still be crafted, so the switch had no effect at all. It works now — turning it off hides those chip recipes',
  '  (together with their recipe-book entries).',
  '',
  '## How it takes effect',
  '',
  '- After changing the config, **restart the game** for it to take effect; editing it while the game is running',
  '  will not apply immediately.',
  '- This hotfix only affects the 1.20.1 Fabric build. Players on the other three builds (NeoForge 1.21.1 /',
  '  Forge 1.20.1 / NeoForge 26.1.2) **do not need to update** — those builds are unaffected.',
  '- Multiplayer with the three 1.5.0 builds still works; compatibility is unchanged.',
  '',
]

def w(path, lines):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = NL.join(l.encode('utf-8') for l in lines) + NL
    open(path, 'wb').write(data)
    chk = open(path, 'rb').read()
    assert chk.count(bytes([13, 13, 10])) == 0
    assert chk.count(bytes([10])) == chk.count(NL), 'bare LF in ' + path
    print('wrote', path, len(chk), 'bytes,', chk.count(NL), 'lines CRLF')

w('release/1.5.0-hotfix/PLAYER_CHANGELOG_ZH.md', ZH)
w('release/1.5.0-hotfix/PLAYER_CHANGELOG.md', EN)
