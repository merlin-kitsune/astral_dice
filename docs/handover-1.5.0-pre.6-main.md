# 交接正本 — 1.5.0-pre.6（multi-main）

> **本文件自 `1.5.0-pre.6` 起为滚动交接正本**。pre.5 及更早的批次、实机读数、遗留与坑**不迁入**，一律回 `docs/handover-1.5.0-pre.5-main.md`（§0 速览 / §3 批次表 / §4 实机读数 / §5 遗留 / §6 坑 / §7 路径）。
> 数字均为 2026-10-24 快照；**开工先实读** `git log -1 --oneline` + `git status --short -uall` + 四线 `gradle.properties`。

## 0 速览（一屏）

1. 分支 `multi-main`，HEAD = 本批提交（`chore(release): bump 1.5.0-pre.6 …`，其父 `4f6140c5`）。**未 push、无 tag**（用户明确「暂不推送」）。
2. 四线 `mod_version = 1.5.0-pre.6+<加载器后缀>`；四线 `starengine_version = 1.1.0-pre.2`（未动）。
3. 本批收编 **Kimi 上一轮四批未提交改动**（member-detective / misaki 立牌 / 筹码商店刷新源 / 规则分派），并完成 **DSH 三语语言同步** + **地雷女备注区染色修复**。
4. 判据：P0 两线 `:classes` `BUILD SUCCESSFUL in 4s`；`check_lang_sync` EXIT=0；`audit_patchouli_keys` 悬空 0；`verify-split` EXIT=0；三道专项 python 判据 PASS 26/12/14；`verify_chip_shop_ui` `CHIPUI_TOTAL_FAIL: 0`。
5. **pre.6 产物已重建并投放**（最新一次 2026-10-25 14:22，含强化拒止物品形态；`:neoforge-1.21.1:build :forge-1.20.1:build` 触发 `pushToDevRun/pushToGame/pushToRootBuild`）——四个目标（`run/1.21.1/mods`、`run/1.20.1/mods`=devlibs named、两个 P0 整合包）逐处 sha256 **MATCH**，读数见 §2。
6. 本批实机：**星光兑换 5 组 + 筹码商店刷新源 / 文案（38 tooltip + 21 手册）/ `ame_note` 染色，双版本全 PASS**（2026-10-25，§4.1 / §4.1b）。`scripts/test/cases/CHIP-REGEN-*.json`（等 tick 自动 +1）随 ticker 删除失效，**已按用户裁决退役（两文件删除）**（§5-3）。
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

**产物现状（2026-10-25 14:22 重建，pre.6；含强化拒止物品形态）**：
neo `astral_dice-1.5.0-pre.6+neoforge_1.21.1.jar` 2,585,277 B / sha256 `7236957807d0fa6f528382be8c80984cfa1059e6a3d451aff264cdf7178ac95f`（`run/1.21.1/mods` 与「狐の航空学 Voxy Edition」同一份）；
forge `astral_dice-1.5.0-pre.6+forge_1.20.1.jar` 2,630,363 B / `d1bd35f0bffe488aa595f2ad58f07dc25925c0ed1ffdbbbc6cdb36375f85a0cb`（→ 1.20.1-Forge 模组测试）；
forge dev **named** 2,600,896 B / `421bbb4c8d6c90ae46057205f8ee31e0aa4f294c2b29b113cba48e166c6fb562`（`run/1.20.1/mods`）。
四目标 2026-10-25 14:22 逐处 sha256 MATCH；jar 内判据：`assets/astral_dice/models/item/repulsion.json` 与 `textures/item/repulsion.png` 均在、`lang/zh_cn.json` 含 `item.astral_dice.repulsion` / `msg.astral_dice.repulsion_hit` / 新卡面文案「回收成物品形式」。
> 上一轮（11:55，星光批次）读数：neo 2,577,145 B / `7995d714…5b5ff9e`；forge reobf 2,622,362 B / `2f14b079…c8f71e9`；forge dev 2,592,867 B / `40bfe716…6a32f274` —— 已被本批覆盖。
库 jar（成对投放，未随本批变动）：neo 98,737 B / forge 103,609 B（`1.1.0-pre.2`）。

## 3 本批批次表

| 提交 | 主题 | 关键落点 |
|---|---|---|
| `e2aed527` | bump pre.6 + 收编 Kimi 四批 + 三语语言同步（53 文件，+994/−981） | 见下 7 条 |
| `1ff14106` | feat(starlight)：星光兑换改为「装备时发放 + 每次兑换后按佩戴补星」（24 文件 +1380/−217） | `resource/ResourceConversion`（兑换后 `replenishOnExchange`）· `item/StarLightManager`（`grantedStarlight` 4bit 账本 / `grantStarlightOnEquip` / `revokeStarlightOnUnequip` / `replenishOnExchange`）· 四枚星光筹码 `onEquip`/`onChipUnequip` · 经商立牌 +50% 保留在 `CardShopMenu#tryExchange` · 新增 `tools/verify_starlight_exchange_refill.py`（`GATE: PASS (196 项)`） |
| `9321d624` | feat(repulsion)：强化拒止改为「近战 5 次回收成物品形态」（32 文件 +973/−172） | 新增 `item/RepulsionItem`（三份状态 + 耐久条）/ `item/RepulsionPlacement`（卡与物品共用落点解析）· `entity/RepulsionEntity`（`MAX_MELEE_HITS = 5` / `recoverIntoItem` / `restoreState` / NBT `RepulsionMeleeHits`）· `component/ModDataComponents` 三个 INT 键 · 两线三语 +11 键 · 贴图 + 生成模型 |
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

### 4.1 pre.6 星光兑换（2026-10-25）—— 双版本实机 5 组判据全 PASS

判据正本：`resource/ResourceConversion#starlightToStarCoins`（两线同文件 L29-48）+ `item/StarLightManager#grantStarlightOnEquip / revokeStarlightOnUnequip / replenishOnExchange`（neo L91/L106/L140；forge L94/L109/L143）+ `shop/CardShopMenu#tryExchange`（neo `:551`、forge `:553`，入口 = `clickMenuButton(player, 17)`）。
驱动面：`pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase env|launch` + AutoJS server `eval_js`（**两线都有 AutoJS**：1.21.1 由 `MT_ENV_AUTOJS` 投放；1.20.1 走 `run\1.20.1\mods\AutoJS-forge-1.20.1-1.0.0-dev.jar`，启动日志 `MT_LAUNCH: OK — AutoJS 端点已就绪`）+ 探针 `/astralshop {reset,star,openkeep,exchange,close}`（输出去 `run\<线>\logs\latest.log` 的 `[CHAT] AP_<TAG>_*`，**不是** `kubejs_log`）。

| # | 场景 | 1.21.1（neo）读数 | 1.20.1（forge）读数 |
|---|---|---|---|
| A | 下界之星骰（4 槽）装四枚星光筹码（锤 5 + 卡少 4 + 卡多 7 + 卡无限 3）后点兑换 | `AP_S2_EXCH:ret=1:star=19->20:bal=0->9`（19 扣 18 ⇒ 9 枚，余 1，再按佩戴补 19 回 20） | `AP_T1_EXCH:ret=1:star=19->20:bal=0->9` |
| B | 60 秒共享冷却：立刻再点一次 | `AP_S2_EXCH:star=20->20:bal=9->9`（`cdBefore=10068 / now=8868` ⇒ 1200t） | `AP_T1_EXCH:star=20->20:bal=9->9`（`cdBefore=2438 / now=1238`） |
| C | 无筹码时兑换 5 点 | `AP_S3_EXCH:ret=1:star=5->1:bal=0->2`（**无补星**） | `AP_T2_EXCH:ret=1:star=5->1:bal=0->2` |
| D | 经商立牌 `parunan_sign` +50%：20 点兑换 | `AP_S4_EXCH:ret=1:star=20->0:bal=1->16`（10 枚 + `max(1,(int)(10*0.5))`=5） | `AP_T3_EXCH:ret=1:star=20->0:bal=1->16` |
| E | 封顶边界 + 账本严格扣除（银行卡-余额多 7，cap=32） | `star=30` → 装备 → `32`；卸下 → `30`（只扣**实际** 2） | `star=30` → `32` → `30`（同左） |

补充实读：卸下四枚筹码后 `star` 由 20 回落到 **1**（按账本扣 19）；全程背包内 `astral_dice:star_coin` 计数 **0**（钱直接进钱包 `StarEngineEconomy.getBalance`）；两线 `CardShopMenu` 的兑换按钮都返回 `ret=1` 且 `star/bal` 前后对照相符。

### 4.1b 筹码商店刷新源 + 文案 + `ame_note` 染色（2026-10-25）—— 双版本实机全 PASS

判据正本：`shop/ChipShopCatalog.java:89-92`（`REFRESH_INITIAL=3` / `REFRESH_MAX=5` / `REFRESH_REGEN_TICKS=36000`，`:89` 注释「不得据此常量新增自动 +1」）+ `shop/ChipShopState.java:174-178`（`withRefreshGranted` = `min(MAX, left+1)`，唯一调用点 `event/AnvilUpgradeHandler.java:91`，日志 `CHIP_SHOP_REFRESH_GRANTED` 在 `:93`）+ `shop/ChipShopMenu.java:428-446`（`handleRefresh`）+ `shop/ChipShopOpener.java:39-62`（`open`）。
驱动面：`mt.ps1 --phase env|launch` + AutoJS server `eval_js` 直调 `ChipShopOpener.open(p)` / `ChipShopMenu#handleRefresh(p)` / `ChipShopState.get(p)`；铁砧走探针真实结算链 `/astralprobe anvilstar "astral_dice:golden_dice" ANV`；死亡用 `/kill @s`。

| # | 判据 | 1.21.1（neo） | 1.20.1（forge） |
|---|---|---|---|
| R0 | 初值 | `refreshes=3 anchor=0 offers="-"`（tick 1015） | `refreshes=3 anchor=0 offers="-"`（tick 1955） |
| R1 | 时间流逝不自增 | 1642 t（≈82 s）后仍 `3 / 0 / "-"` | 首次刷新前仍 `3 / 0`；`ChipShopRegenTicker` 两线 jar 内均 **0 条目**（neo 2002 / forge dev 2016、reobf 2016） |
| R2 | 刷新消耗 + anchor 首写 | `3→2→1`，`anchor=38865`（=2865+36000）首刷写入、二次不变；日志 `CHIP_SHOP_REFRESH refreshes=2/5`、`1/5` | `3→2→1`，`anchor=37955`（=1955+36000），同左 |
| R3 | 升星取走 +1 | `1→2`（同 tick）；日志 `CHIP_SHOP_REFRESH_GRANTED player=Dev star=0->1 coins=18 refreshes=2/5` | 同左（`1→2`、`refreshes=2/5`） |
| R4 | 铁砧真实结算链 | `AP_ANV_TAKE:clicked:30:20` / `AP_ANV_RESULT:astral_dice:golden_dice:1` / `AP_ANV_STAR:0:1` / `AP_ANV_DONE` | 同左（逐字相同） |
| R5 | 死亡不自愈 | `kill @s` → 4 s 后重生 `refreshes=2`、`anchor` 不变 | 同左（`refreshes=2`、`anchor=37955`） |

文案（本批 **60 键 = 38 `tooltip.*` + 21 `astral_dice.guide.*` + 1 `msg.astral_dice.card_shop_no_starlight`**，两线 lang 逐字相同；old/new 落盘 `temp/lang_changed_neo.json`）：

- tooltip：两线各 dump 全 **156** 个 `astral_dice` 物品的 `Screen.getTooltipFromItem` 渲染行（保留字面 § 码），与 lang 逐键比对 ⇒ **37/38 命中，两线一致**。唯一未显示 = `tooltip.astral_dice.sign.misaki_enigmatic`：`event/ModTooltipHandler.java:686-700` 以 `ModList.get().isLoaded("enigmaticlegacyplus")` 为条件，两线 dev 均未装 ⇒ **正确地不出现**。
- 换行口径：`komachi_passive` 的「␠␠缩进」渲染成 `- ` 项目符；`investigation_desc` 续行**去掉前导两空格且无项目符**（渲染原文 `§dI：自身与施加者获得 §9隐匿 (1:00)§r`，不是缺陷）；其余多行键逐行渲染。
- 两线 tooltip 全量对照：**11 个物品仅行序不同、行集完全相同**（`curios.modifiers.*` + 属性行在 neo 前置 1 行块、forge 后置），属平台差异、非本批回归（`moto_helmet ×3` / `sandwich ×3` / `speed_skates ×3` / `obsidian_dice` / `perpetual_motion_chip`）。
- 手册：21 键两线均 **21/21** 解析成功，17 条逐字一致、4 条只差 `%%`→`%`（`battle_system_changes.3` / `komachi_sign.2` / `rin_sign.1` / `extra_battery_chip.1`），**0 条含显式 `\n`**（折行由 Patchouli 自动）。
- `ame_note` 染色：两线逐字一致 —— `§d`（备注体）+ `§e`（数值）+ `§9`（时间），行首区段前缀由 `addSignNoteLines` 注入，符合 tooltip 染色规则；证据 = 上述渲染行原文（未取截图）。

### 4.1c 强化拒止物品形态（2026-10-25）—— 代码/资源就绪，**未实机**

改动（P0 两线同批）：玩家近战命中已放置力场第 5 次 ⇒ 回收成**新物品** `astral_dice:repulsion`（不是效果牌），携带耐久（= 关联血量）/ 已存活 tick / 已提供充能三份状态；再次放置按与效果牌**同一份**落点解析（`item/RepulsionPlacement.resolve`）恢复三份状态。

已成立的静态判据：

| 判据 | 命令 | 读数 |
|---|---|---|
| 两线编译 | `gradlew.bat :neoforge-1.21.1:classes` / `:forge-1.20.1:classes` | `BUILD SUCCESSFUL in 4s` / `in 11s`（EXIT 0，零新警告） |
| 两线打包 + 投放 | `gradlew.bat :<线>:build` | `BUILD SUCCESSFUL`，四目标 sha256 MATCH（§2） |
| 模型链 | `runData`（两线） | 生成 `src/generated/.../models/item/repulsion.json`（`parent: minecraft:item/generated`，`layer0: astral_dice:item/repulsion`） |
| 资源完整性 | `scripts/verify/verify_resource_integrity.ps1` | `RESULT: PASS`；两线注册物品 157，模型 157/157、贴图 157/157 |
| 语言同步 | `tools/check_lang_sync.ps1` | EXIT 0；P0 各 1058 键一致（3 条既有 WARN） |
| 稀有度一致性 | `tools/verify_rarity_consistency.py` | `GATE: PASS` |
| 规则分派 | `python -B docs/agent/verify-split.py` | 8 项全 `[OK]` |

**未做**：实机（放置 / 近战 5 次回收 / 重放恢复 / 非 owner 拒止 / 耐久条与 tooltip 三行读数）—— 按规则需用户明确下达。

### 4.2 历史读数

pre.5 及更早全部实机读数见 `docs/handover-1.5.0-pre.5-main.md` §4（含 4.8 pre.4 全量双线 10 跑 10 PASS、4.11–4.17 pre.5 各批、4.19 Jade 图标留白版）。

## 5 遗留与欠账

### Blocker

- 无（编译/守门全绿）。

### Major

1. ~~pre.6 产物未建未投~~ → **已完成**（2026-10-25 11:55）：`:neoforge-1.21.1:build :forge-1.20.1:build` 触发 `pushTo*`，四目标 sha256 全 MATCH（§2）；库 jar 成对在位（neo 98,737 B / forge 103,609 B，`1.1.0-pre.2`）。
2. ~~本批局部实机~~ → **已完成**（2026-10-25）：星光兑换 5 组（§4.1）+ 筹码商店刷新源 R0–R5 / 38 tooltip + 21 手册 / `ame_note` 染色（§4.1b），**双版本全 PASS**。
3. ~~`CHIP-REGEN-*.json` 已失效~~ → **已退役**（2026-10-25 用户裁决「旧用例直接退役」）：`scripts/test/cases/CHIP-REGEN-1.20.1.json` / `CHIP-REGEN-1.21.1.json` **两文件已删除**（它们断言「摆形态 → 等 tick → 自动 +1」，随 `ChipShopRegenTicker` 删除永久不成立）。新语义（刷新 3→2→1 + anchor 首写 + 升星取走 1→2 + 死亡不自愈）已在 §4.1b R2/R3/R5 双线实机取证；若日后要**自动化**回归再按这套判据重写，勿复活旧文件。探针 `/astralshop cregen` 仍保留（只摆状态、不再指望 ticker 推进）。
4. **`tooltip_color_audit` 存量 144 处**（9 键，2026-10-25 复跑）：`jill_active` 36 / `storm_approaching.description` 18 / `problem_delivery.description` 18 / `dorothy_active` 18 / `red_heat_warning.description` 16 / `fanny_active` 12 / `jill_passive` 8 / `card.barricade` 6 / `deepseek_passive` 6（+ 若干零散项）。原 `card.repulsion` 的 6 处 `§e20:00§7` 已按染色规则改为 `§920:00§7`（**时间值必须 §9**），复跑后 repulsion 相关 **0 处**；其余 9 键属既有基线债，仍未动。
5. **8 条「代码与文案都不符」**（已报用户，未改 tooltip）：bite 觉醒按撕咬张数 / huo_card = `CREATURE_OR_RIVAL` / magic_quiver 真闸门 = 目标已有标记 + 先用过伤害牌 / railgun 只 `bypasses_armor` / 三张卡 `NON_HOSTILE` / member_recommendation 只数主背包+副手 / repulsion 单次最多 5 点耐久 / airbag 掉虚空救不了。
6. **P1 两线欠账**（正式版前不移植，仅登记）：JEI 筹码 / `BeamFx` / `GlobalEventGate` / `RepulsionEntity` / `CardShopMenu#planPlacement` / `EventDrawBlock`；筹码效果图标仍带边框；史莱姆被动仍 20 tick；misaki 仍 `legendary()` + 黄金星盘 + 死亡清零剑气；pre.5 冷却指示器未移植。
7. **陈旧锁 `kimi.lock`**（`PID=26580` 已不存在）——不自行删，交用户裁决。
8. **强化拒止物品形态未实机**（2026-10-25）：代码/贴图/三语 lang/模型/CHANGELOG 全部就绪、两线编译与四道守门全绿（§4.1c），但**没有任何实机读数**。待用户下达后按 §4.1c 末段的六项判据跑。另有两个**待用户裁决**的小项：(a) 是否给新物品**加帕秋莉手册第 4 页**（现只把卡条目的文案改成「会被回收」）；(b) 玩家自写染色 `card.barricade` 的 6 处 `§e20:00§7` 是否也一并改 §9（本批只改了 repulsion）。

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
8. **`ICuriosItemHandler#setEquippedCurio` 在 Curios 9.5.1（1.21.1）不触发 `onEquip`**（字节码只做 `setStackInSlot`）⇒ 装备类效果（星光发放 / 筹码槽扩容）必须**等 1–2 个 tick** 再读，否则读出「什么都没发生」的假阴性；1.20.1 的 Curios 5.x 反而当场生效。另：**1.20.1 `CuriosApi.getCuriosInventory` 返回 Forge `LazyOptional`（`.orElse(null)`），1.21.1 返回 `Optional`（`.get()`）**；`getStacksHandler` 在 1.20.1 也要 `.isPresent()/.get()`。
9. **探针 `open` 会开店后立刻关**（否则 GUI 吞掉后续聊天命令）⇒ 要连点「星光兑换」必须在**同一次 AutoJS eval 内**顺序 `openkeep → exchange → close`（`performPrefixedCommand` 不走 GUI，不受影响）。
10. **探针输出通道 = 聊天** ⇒ `run\<线>\logs\latest.log` 的 `[CHAT] AP_...`；`mcp__autojs__kubejs_log` 返回 0 entries 属正常，别据此判失败。
11. **AutoJS/Rhino 口径**（1.21.1 rhino 2101 / 1.20.1 rhino 2001）：无 `server` 全局（用 `AutoJS.server()/AutoJS.player()`）；同一 eval 内不能 `function f(){}`（用 `var f = function(){}`）；`obj.getClass()` 不可用；`p.level` 是属性、`p.level.getGameTime()` 报错；返回值必须 `JSON.stringify((function(){...})())`。`StarLightManager` **没有** `getCap(player)`（实测 `Can't find method ... getCap(ServerPlayer)`）。
12. **`/astralshop reset` 只清 cd/余额/星光，不释放筹码的发放账本位** ⇒ 兑换前必须「卸下 → 等 tick → 装回」重新申领，否则读到 `star=0` 的空转结果（首轮 GUI 验证就栽在这里）。
14. **探针的 `StringArg.string()` 参数必须加引号**：`/astralprobe anvilstar "astral_dice:golden_dice" ANV`（`dice` = `StringArg.string()`、`tag` = `StringArg.word()`）；不加引号只在聊天里留一行被截断的 `...stral_dice:golden_dice ANV<--[此处]`，日志里几乎认不出是参数错。另 **`performPrefixedCommand` 在 Rhino 里返回 `undefined`**，不能当失败判据 —— 用 `say TESTPFC12` 能进日志即证明通道通。
15. **同一时刻只能一条线占用 AutoJS 的 25585**：1.21.1 未停就 launch 1.20.1 ⇒ 客户端日志 `[AutoJS]: Could not bind the MCP endpoint on 127.0.0.1:25585`（`java.net.BindException`），紧接着 preflight 报 `MT_INJECT_FOCUS: FAIL — 目标窗口无法置前台` 与「客户端未在运行」 ⇒ **先 `--phase stop --force` 另一线再 launch**。
16. **1.20.1 的 AutoJS 是 `run\1.20.1\mods\AutoJS-forge-1.20.1-1.0.0-dev.jar`（named）**：`MT_ENV_AUTOJS: 跳过 — 1.20.1 线未接入` 只表示**不再自动投放**，运行时照样有端点；判据看启动日志 `MT_LAUNCH: OK — AutoJS 端点已就绪`。
17. **`run/<线>/mods` 里的 astral_dice jar 会遮蔽 dev classes ⇒ datagen 静默不产出**（2026-10-25 实测）：`runData` 日志里 `Item Models: astral_dice finished` 与 `HashCache new count ... written: 0/1` 都正常，但新 `basicItem(...)` 对应的 `src/generated/.../models/item/xxx.json` **不存在**。根因：`run/1.21.1/mods/astral_dice-1.5.0-pre.6+neoforge_1.21.1.jar` 与 `neoforge-1.21.1/build/classes/java/main` 都声明 modid `astral_dice`，`UniqueModListBuilder` 选的是 **jar**（日志 `Selected file astral_dice-...jar for modid astral_dice`）⇒ 跑的是旧 jar 里的旧 `ModItemModelProvider`。修法：先把 `run/<线>/mods/astral_dice-*.jar` 移出（本轮移到 `temp/mods-bak-datagen/`），跑 `--rerun-tasks :<线>:runData`，再把 jar 拷回。
18. **`run_code` 自带 120s 程序级 deadline，`tools.pwsh` 的 `timeoutMs` 覆盖不到它** ⇒ 长任务（两线 `runData`、双线 `build`）必须同时给 `run_code` 的 `timeoutMs`，否则进程在中途被杀、日志看似「跑完了」。另：**bundled python 在本会话无法写文件**（PIL `im.save()` 对 `temp/` 与 `src/main/resources/` 均 `PermissionError: [Errno 13]`）⇒ 生成 png/二进制一律用 node（`fs` + `zlib` 手写 PNG 编码器），不要走 python 写盘。
13. pre.5 及更早的 32 条坑见 `docs/handover-1.5.0-pre.5-main.md` §6。

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
