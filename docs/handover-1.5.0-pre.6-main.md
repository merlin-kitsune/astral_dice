# 交接正本 — 1.5.0-pre.6（multi-main）

> **本文件自 `1.5.0-pre.6` 起为滚动交接正本**。pre.5 及更早的批次、实机读数、遗留与坑**不迁入**，一律回 `docs/handover-1.5.0-pre.5-main.md`（§0 速览 / §3 批次表 / §4 实机读数 / §5 遗留 / §6 坑 / §7 路径）。
> 数字均为 2026-10-24 快照；**开工先实读** `git log -1 --oneline` + `git status --short -uall` + 四线 `gradle.properties`。

## 0 速览（一屏）

1. 分支 `multi-main`，HEAD = 本批提交（`chore(release): bump 1.5.0-pre.6 …`，其父 `4f6140c5`）。**未 push、无 tag**（用户明确「暂不推送」）。
2. 四线 `mod_version = 1.5.0-pre.6+<加载器后缀>`；四线 `starengine_version = 1.1.0-pre.2`（未动）。
3. 本批收编 **Kimi 上一轮四批未提交改动**（member-detective / misaki 立牌 / 筹码商店刷新源 / 规则分派），并完成 **DSH 三语语言同步** + **地雷女备注区染色修复**。
4. 判据：P0 两线 `:classes` `BUILD SUCCESSFUL in 4s`；`check_lang_sync` EXIT=0；`audit_patchouli_keys` 悬空 0；`verify-split` EXIT=0；三道专项 python 判据 PASS 26/12/14；`verify_chip_shop_ui` `CHIPUI_TOTAL_FAIL: 0`。
5. ⚠️ **`build/libs` 与投放仍是 pre.5 产物**（本批只跑 `classes`）⇒ 实机 / 交付前必须 `:jar` 重建 pre.6 并重投四个目标。
6. ⚠️ 本批**零实机**；`scripts/test/cases/CHIP-REGEN-*.json`（等 tick 自动 +1）随 ticker 删除**已失效**。
7. 规则分派已落地：根 `AGENTS.md` 为 41 行入口，Kimi → `docs/agent/AGENTS-kimi.md`，DSH → `docs/agent/AGENTS-dsh.md`，Codex → `docs/agent/AGENT-SPLIT.md`（只读）。
8. 未处置：陈旧锁 `F:\MCProject\.agent-locks\kimi.lock`（`PID=26580` / `TASK=misaki-chip-shop-balance-p0`，进程已不存在）—— 按规则不自行删，交用户裁决。

## 1 环境与路径

路径与命令**沿用 pre.5 正本 §1**（Windows 原生：git bash 的 `git`、`pwsh -NoProfile -File`、`JAVA_HOME=C:\Program Files\Zulu\zulu-21`、工作树直接 `gradlew.bat`、测试台 `scripts\test\mt.ps1`）。两条补充：

- 仓库多了一整套**规则分派**文件：根 `AGENTS.md`（入口/红线/索引）· `docs/agent/AGENTS-kimi.md` · `docs/agent/AGENTS-dsh.md`（旧根逐字节留存 + 开头 DSH 段）· `docs/agent/PROJECT-REFERENCE.md`（工程拓扑）· `docs/agent/AGENT-SPLIT.md`（Codex/DSH 分工 + §六 Kimi 兼容）· `AGENTS-full.md`（逐批历史，未变）。
- 锁：`F:\MCProject\.agent-locks\` 现同时存在 `kimi.lock`（陈旧）与 `dsh.lock`（DSH 若在建）；动工作树前两侧都要看。

## 2 版本与坐标

| 项 | 值 | 落点 |
|---|---|---|
| neo `mod_version` | `1.5.0-pre.6+neoforge_1.21.1` | `neoforge-1.21.1/gradle.properties` |
| forge `mod_version` | `1.5.0-pre.6+forge_1.20.1` | `forge-1.20.1/gradle.properties` |
| 26.1.2 `mod_version` | `1.5.0-pre.6+neoforge_26.1.2` | `neoforge-26.1.2/gradle.properties` |
| fabric `mod_version` | `1.5.0-pre.6+fabric_1.20.1` | `fabric-1.20.1/gradle.properties` |
| `starengine_version` | `1.1.0-pre.2`（四线）；fabric 另有 `starengine_version_range=>=1.1.0-pre.2` | 各线 `gradle.properties` |
| 硬前置区间 | `[1.1.0-pre.2,2.0.0)`；`META-INF/jarjar/` 计数 **0** | `neoforge-1.21.1/.../neoforge.mods.toml` · `forge-1.20.1/.../mods.toml` |

**产物现状（未重建）**：`build/libs/` 仍是 pre.5（2026-10-23 17:10）——
neo `astral_dice-1.5.0-pre.5+neoforge_1.21.1.jar` 2,570,968 B / sha256 `8796e6cf4db317b4f35fc295eb0a968ff58cdeb9b941678cb576d0ff5c57078b`；
forge `astral_dice-1.5.0-pre.5+forge_1.20.1.jar` 2,616,502 B / `1519f22142bfd911e39ad7e1934d2051d08b85ba7753961e7663b15f15b61284`；
forge dev named 2,587,015 B / `5eac2ac6e637ca88c79c678759611e429a55385067d38b3798fb4c17c82e46bf`。
库 jar：neo 98,737 B / forge 103,609 B（`1.1.0-pre.2`，2026-10-23 17:09–17:10）。

## 3 本批批次表

| 提交 | 主题 | 关键落点 |
|---|---|---|
| `e2aed527` | bump pre.6 + 收编 Kimi 四批 + 三语语言同步（53 文件，+994/−981） | 见下 7 条 |
| 本文件所在提交 | docs：Kimi 交接单 + 工程参考 + pre.6 正本 + 新会话上下文 | `docs/handover-2026-10-24-kimi-takeover.md` · `docs/agent/PROJECT-REFERENCE.md` · 本文件 · `docs/handover-new-session-context.md` |

`e2aed527` 的七条内容：

1. **规则分派（Kimi）**：根 `AGENTS.md` 收为 41 行入口（2,452 字符）；`docs/agent/AGENTS-dsh.md`（旧根逐字节留存，原 sha256 `c018c8b6e116ef05f2c24106c97a5f3622ff5a30ab7b41cfa97810b6f4bb3e79`）开头加 DSH〈using-superpowers〉段；新建 `docs/agent/AGENTS-kimi.md`；`docs/agent/verify-split.py` 重写四项；`AGENT-SPLIT.md` 追加 §六（Kimi 锁兼容）；`docs/agent/README.md` 一行同步。
2. **member-detective（Kimi）**：`MemberRecommendationChipItem` `HAND_CARD_LIMIT = 5`（主背包 items + 副手计数，不含 Curios）；`BonnieSignItem` 改为先 `MarkManager.apply(target)` 再按 `markLevel * 2` 返币；新增 `tools/verify_member_recommendation_hand_gate.py`(14) / `tools/verify_bonnie_active_order.py`(12)。
3. **misaki 立牌（Kimi）**：两线 `MISAKI_SIGN` `epic()`；配方 `STAR_PLATE`（datagen + 生成 JSON）；死亡不清剑气（`clearSignData` 卸下清零保留）；`astral_rews.json` `EPIC`；新增 `tools/verify_misaki_balance.py`(26)。
4. **筹码商店刷新源（Kimi）**：两线删 `shop/ChipShopRegenTicker.java`；`needsReroll` 去 `diceKey`；`withRefreshGranted()`（不重置、封顶 5）；唯一授予点 `event/AnvilUpgradeHandler`（取走升星成品 +1；升星花费 0/1/2★=15/20/25 与 preview 共用）；死亡保留 neo `.copyOnDeath()` + forge `AstralData` 白名单 `ModAttachments.CHIP_SHOP_STATE.name()`；`verify_chip_shop_ui.ps1` 加 G18c..G18h。
5. **三语语言同步（DSH，用户 m07951/m08212 下达）**：用户手改 39 条（38 `tooltip.*` + `msg.astral_dice.card_shop_no_starlight`）为权威；按新语义改写 21 条 `astral_dice.guide.entry.*` 手册正文；`zh_cn.json` 同步到 forge 线（按 forge 自身键序只换 60 个值，**禁止整文件复制**）；`en_us.json`/`ja_jp.json` 两线各 60 键重译，格式保真 234/234 §码 · `%%` 10/10 · `%s` 6/6 · \n 39/39 · `" | "` 3/3。
6. **`tooltip.astral_dice.sign.ame_note` 染色（DSH）**：备注区底色 `§d`（`event/ModTooltipHandler#addSignNoteLines`），补 7 对染色（`§e` 数值 / `§9` 时间 / `§d` 回落），正文未改（仅删一多余空格）；6 文件（两线 × 三语）。`tooltip_color_audit` 中 `ame_note` 40 → 0。
7. **版本 bump（DSH）**：四线 `mod_version` → `1.5.0-pre.6`。

## 4 实机读数

### 4.1 本批（pre.6）—— **零实机**

本批全部判据为静态 + 编译，未开客户端。新增/变更行为**均无实机读数**，尤其：

- 筹码商店「刷新次数只来自升星」改造（删 ticker 后不再自动回满）——需一轮实机确认「摆形态不再自动 +1 / 升星取走 +1 / 死亡后次数不自愈」。
- 语言改动（39 tooltip + 21 手册 + 三语译文）——需实机看 tooltip 换行/染色与手册页文字。
- `ame_note` 染色——静态规则已判，仍需肉眼确认备注区观感（`§d` 底色 + `§e`/`§9` 对比）。

### 4.2 历史读数

pre.5 及更早全部实机读数见 `docs/handover-1.5.0-pre.5-main.md` §4（含 4.8 pre.4 全量双线 10 跑 10 PASS、4.11–4.17 pre.5 各批、4.19 Jade 图标留白版）。

## 5 遗留与欠账

### Blocker

- 无（编译/守门全绿）。

### Major

1. **pre.6 产物未建未投**：`:jar` 重建 → 记 sha256 → 投 `run/1.21.1/mods`、`run/1.20.1/mods`（forge 用 named）、两个 P0 整合包，**成对**带 `starengine-*-1.1.0-pre.2.jar`；投放后逐份核 sha256。
2. **本批零实机**（见 §4.1），需用户下达后补。
3. **`CHIP-REGEN-1.20.1.json` / `CHIP-REGEN-1.21.1.json` 已失效**（仍断言「等 tick 自动 +1」）——退役或按新语义改写（升星取走 +1）。
4. **`tooltip_color_audit` 存量 130 处**（10 键）：`jill_active` 22 / `storm_approaching.description` 18 / `problem_delivery.description` 18 / `dorothy_active` 18 / `red_heat_warning.description` 16 / `fanny_active` 12 / `jill_passive` 8 / `card.barricade` 6 / `card.repulsion` 6 / `deepseek_passive` 6。其中 `card.barricade`/`card.repulsion` 是**用户自写 `§e20:00§7`**（规则应为 §9），按「tooltip 以用户为准」未动。
5. **8 条「代码与文案都不符」**（已报用户，未改 tooltip）：bite 觉醒按撕咬张数 / huo_card = `CREATURE_OR_RIVAL` / magic_quiver 真闸门 = 目标已有标记 + 先用过伤害牌 / railgun 只 `bypasses_armor` / 三张卡 `NON_HOSTILE` / member_recommendation 只数主背包+副手 / repulsion 单次最多 5 点耐久 / airbag 掉虚空救不了。
6. **P1 两线欠账**（正式版前不移植，仅登记）：JEI 筹码 / `BeamFx` / `GlobalEventGate` / `RepulsionEntity` / `CardShopMenu#planPlacement` / `EventDrawBlock`；筹码效果图标仍带边框；史莱姆被动仍 20 tick；misaki 仍 `legendary()` + 黄金星盘 + 死亡清零剑气；pre.5 冷却指示器未移植。
7. **陈旧锁 `kimi.lock`**（`PID=26580` 已不存在）——不自行删，交用户裁决。

### Minor

1. `docs/handover-2026-10-24-member-detective.md` 等 Kimi 批次文档已入库，措辞按 2026-10-24 事实；后续改动若改变其中数字需回填。
2. 仓库根 `undefined/temp/`（2026-10-23 历史临时脚本残留，被 `temp/` 规则忽略）未清理。
3. `misaki_sign.json` 是手改的（与 datagen 是否逐字节一致未验）；下次 `runData` 后须 diff。
4. **子代理模型口径已收紧为「唯一」**（2026-10-22 用户裁决）：一律 `provider: "deepseek-official"` + `model: "deepseek-flash"`（DeepSeek-V4.1-Flash），不得换更强 / 其它 provider 的 model；原「确需深推理才换更强 model」「其它模型仅作备用」两种口径**作废**；`list_subagent_models` 只核注册表、不换模型；该路由失效 ⇒ 停下报用户。同步文件 4 个：`docs/agent/AGENTS-dsh.md`（§A 第 6 条 / §B ③ / 红线区第 7 条⑧）、`docs/agent/AGENTS-full.md`（红线区第 7 条⑧同文 + 子项目修改默认规则实施分工 + 第 3 节纪律第 6 条）、`docs/agent/delegation-runbook.md`（§2 派活）、`docs/agent/AGENTS-kimi.md`（§0 第 2 条，使用 Kimi 路由 `DeepSeek/deepseek-flash`）。

## 6 坑（本批新增，后续继续追加）

1. **`git show HEAD:<file>` 是 LF**（仓库存 LF、工作树 CRLF 由 autocrlf 转换）⇒ 行级改写先 `replace(/\r\n/g,"\n").split("\n")` 再 `join("\r\n")`。
2. **forge 与 neo 的 lang 键序在 index 955 起分叉**（`block.astral_dice.barricade_collision` vs `block.astral_dice.chip_shop`；forge 另有 6 行缺 2 空格缩进、保留用户删掉的空行）⇒ **禁止整文件复制**，只按目标文件键序换值。
3. **`tools.edit` 插多行会把整文件转 LF** ⇒ 大文件改写用 node `fs` 全量读改写。
4. **子代理对 `temp/` 无写/删权限**，残留须主会话用 `pwsh` 清；`.gitignore` 的 `temp/` 是任意层级匹配。
5. **`withRefreshGranted` 不可是「重置」**（`min(MAX, left+1)`），且**死亡必须保留**（否则重生 = 变相补满）；forge 侧走 `AstralData` 白名单而非 `copyOnDeath`。
6. **`MemberRecommendationChipItem` 只数主背包 + 副手**，不含 Curios 卡牌栏（与用户文案一致）。
7. **备注区（`*_note` 键）回落码必须 `§d`**（`addSignNoteLines` 会把行内 `§7` 替换成 `§d`），否则踩 R0。
8. pre.5 及更早的 32 条坑见 `docs/handover-1.5.0-pre.5-main.md` §6。

## 7 关键路径（命令）

```powershell
# 状态 / 版本
git -C F:\MCProject\astral_dice_multiloader log -1 --oneline
git -C F:\MCProject\astral_dice_multiloader status --short -uall
git -C F:\MCProject\astral_dice_multiloader rev-list --count origin/multi-main..HEAD
Select-String -Path F:\MCProject\astral_dice_multiloader\*-1.*\gradle.properties -Pattern '^mod_version|^starengine_version'

# 编译 / 产物
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
Set-Location F:\MCProject\astral_dice_multiloader
.\gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes --console=plain
.\gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar --console=plain

# 守门
pwsh -NoProfile -File tools\check_lang_sync.ps1
python tools\audit_patchouli_keys.py
python docs\agent\verify-split.py
pwsh -NoProfile -File scripts\audit\tooltip_color_audit.ps1
pwsh -NoProfile -File scripts\verify\verify_chip_shop_ui.ps1
python tools\verify_misaki_balance.py
python tools\verify_bonnie_active_order.py
python tools\verify_member_recommendation_hand_gate.py

# 测试台（实机须用户下达）
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.20.1 --phase env
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version 1.20.1 --phase stop --force --purge-saves
```
