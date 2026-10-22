# 交接正本 — 1.5.0-pre.4（multi-main）

## 0 速览（一屏）

1. 分支 `multi-main`；HEAD = **本批头提交**（提交号见 `git log -1`）（pre.4 施工批 + 史莱姆被动 15 秒批 + 派子代理屏蔽人格注入规则批 + 强化拒止品质改奇特批 + 电击手套 FX 批 + 筹码商店品质外框恢复批（第 15 条）均已提交）。
2. 未推送提交 **32** 个（`git rev-list --count origin/multi-main..HEAD`，2026-10-22 提交本批后实测），**无** tag（`1.5.0-pre.3` / `1.5.0-pre.4` 均无，需用户放行）。
3. 四线 `mod_version` 均为 `1.5.0-pre.4+<加载器后缀>`（2026-10-22 bump）；`starengine_version=1.1.0-pre.2`（四线一致，未动）。
4. jar 内硬前置：`modId="starengine"` / `versionRange="[1.1.0-pre.2,2.0.0)"`，`META-INF/jarjar/` 计数 **0**（pre.4 两 jar 已复核）。
5. 可交付范围：**只发 P0**（`neoforge-1.21.1` + `forge-1.20.1`）。P1 两线版本号同为 pre.4，但内容不含 1.5.0。
6. P0 双 jar（pre.4）已重建并投放 **4 处**（两个整合包 + 两个 `run/<线>/mods`），逐处 sha256 见 §2。
7. 守门 8 跑 7 绿；唯一红 = `verify_probe_class_refs`（26.1.2 产物陈旧，P1 待重建，见 §5-1）。
8. 实机：1.20.1 本批 6/6 PASS、1.21.1 5 条 FIXBATCH 判据全命中、末条 49/49 PASS；**创造模式扣卡复验 PASS**（§4.4，2026-10-22 16:10，用例 `CARD-NOCOST-CREATIVE-1.20.1`）。
9. **pre.4 本批（2026-10-22 17:3x）无新增实机读数**：改动 = 实体拆除口径 + 死亡动画 + 两个管理命令 + 11 张效果图标 + 版本号，判据目前全为静态/字节码/资源 md5（§5-13），实机待用户下达。
10. **史莱姆立牌被动改为「每 15 秒最多触发一次」（2026-10-22，已提交 `7c51df63`）**：`HURT_COOLDOWN_TICKS` **20 → 300**（仅 P0 两线 `item/sign/LuluSignItem.java`），tooltip / 帕秋莉手册条目 / 两份 CHANGELOG 同步；判据见 §5-15，**无实机读数**。P1 两线未动（§5-16）。
11. **派子代理一律屏蔽人格注入（2026-10-22 用户裁决，本轮已完成）**：红线区〈子代理与测试环境硬规则〉新增第 8 条 —— brief 必带「不要使用任何人设 / 角色扮演 / 语气修饰 / 昵称自称」；复核 / 审查 / 取证类任务一律用全新上下文 `subagent`（`subagent_fork` 会继承带人设的对话）；子代理回报出现人格化表达即判不合规。判据见 §5-18。
12. 面向整合包会话的交接单见 `docs/handover-1.5.0-pre.4-modpack.md`（投放表 / 自检读数 / 坑 / 剩余事项）。
13. **强化拒止品质改为奇特（2026-10-22 用户裁决，已提交）**：`is_exclusive` 标签 7 项里只有它还是 `epic` ⇒ P0 两线各改一行 `.rarity(AstralRarities.epic())` → `.rarity(AstralRarities.bizarre())`；判据 = 字节码 `lambda$static$52`（neo）/ `lambda$static$56`（forge）内 `AstralRarities.bizarre()` 1 次、`epic()` 0 次；并新增「专属牌品质总则」+ CHANGELOG/需求文档订正 + 新产物投放 4 处（18:09）。详见 §5-19。
14. **电击手套命中 FX（2026-10-22 18:18，已提交）**：命中目标撒 Irons 粒子库电火花（`comet_azur_filament`×2 电弧 / `glintstone_spark`×7 火花芯 / `frost_spark`×4 细闪，rgb `0x9BE8FF`·`0xE8FBFF`），圆心一次 `SoundEvents.LIGHTNING_BOLT_IMPACT`（音量 0.5 / 音调 1.5）；实现 `combat/ElectricShockFx.java`（P0 两线）+ `audio/SoundPlayback` 新增 7 参 `playAt` 重载（旧 5 参委托它） + `item/chip/ElectricGloveChipItem#executeShock` 接线。判据见 §5-20，**无实机读数**。
15. **筹码商店品质外框恢复 + 卡牌购买钱包音效（2026-10-22 18:32）**：`screen/ChipShopScreen` 把 2026-10-21 误删的三色品质描边环（`chipshop_quality_1/2/3`，28x28）重新画回筹码格脚下、`client/ChipShopClientState`+`shop/ChipShopCatalog` javadoc 同步口径；`shop/CardShopMenu#handleBuy` 成功尾段补 `SoundPlayback.playTo(buyer, ModSounds.WALLET_DEPOSIT.get())`（拒绝分支不响）。守门 `verify_chip_shop_ui.ps1` 的 G2b 由反向改为正向断言、`mutate_chip_shop_ui.ps1` 的 M38/M39 同步反转（`MUT_TOTAL: 39，判红 39`）。判据与产物见 §5-21 / §2，**无实机读数**。

## 1 环境与路径

- 仓库（唯一工作树）：`F:\MCProject\astral_dice_multiloader`，分支 `multi-main`；git 一律用 **git bash 的 `git`** ——
  写全 `git -C F:\MCProject\astral_dice_multiloader <子命令>`，或先 `cd` 进工作树；**不需要** `--git-dir=`，**不需要** `cmd.exe` 中转（原「WSL 报 `not a git repository`」问题不存在，2026-10-22 起失效）。
- 四子项目同树：`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1`。
- 编译（**Windows 原生**，原「WSL 只编译」口径 2026-10-22 起作废）：在工作树根执行 `gradlew.bat :<线>:classes|jar`；
  JDK = `C:\Program Files\Zulu\zulu-21`（21.0.12），`forge-1.20.1` 的 `toolchain 17` 由 Gradle 从 `%USERPROFILE%\.gradle\jdks` 取
  （已缓存 Adoptium 17.0.20），**不要手改**。
- 实机与脚本：一律 `pwsh -NoProfile -File <绝对路径>.ps1`（**禁** `bash` 跑测试、**禁** `.sh`、**禁** Windows PowerShell 5.1、
  **禁** `wsl.exe` 与 `/mnt/...` 路径）；bash 只承担 git。
- 测试台：`scripts/test/mt.ps1`（`--version <线> --phase env|run|stop`）。
- 整合包投放：
  - neo → `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods`
  - forge → `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods`
- 库 jar 与之同目录：`starengine-neoforge-1.21.1-1.1.0-pre.2.jar`（98,733 B）/ `starengine-forge-1.20.1-1.1.0-pre.2.jar`（103,604 B）。
- 委派（**2026-10-22 用户裁决**：DSH 只用自带子智能体，**禁止**用 Codex 的 `delegate_task`）：一次性活 ⇒ `subagent`（全新 context，brief 必须完全自包含；结果由子代理 `send_message` 回报，运行时另有完成通知）；接续上下文 ⇒ `subagent_fork`；多人并行 ⇒ `spawn_teammate` + `team_task_*`（`write_scopes` 互不重叠）；复核 ⇒ `dsh_code_review({ input })`，不为复核另派 subagent。主 Agent 只做识别需求 → 分配工作 → 验收成果（只看产物，禁轮询）。手册：`docs/agent/delegation-runbook.md`。
  Codex 侧 `C:\Users\xmace\.codex\mcp\delegation\server.mjs` 的 `delegate_task` 属**另一个宿主**，DSH 会话**不得引用**（日志 `…\delegation\server.log` 仅作历史背景）。
- 环境注入：**无** `/etc/profile.d` 注入脚本（`99-wsl-proxy.sh` / `99-wsl-github.sh`）、**无** `wsl-env-restore.sh`、**无** `/opt/zulu`+`use-jdk`
  （2026-10-22 起全部失效）；Windows 侧代理走系统环境变量（Clash `http://127.0.0.1:7897`），仓库级 `http.proxy` / `https.proxy` 已配好。

## 2 版本与坐标

| 项 | 实测值 | 证据 |
|---|---|---|
| neo `mod_version` | `1.5.0-pre.4+neoforge_1.21.1` | `neoforge-1.21.1/gradle.properties:37` |
| forge `mod_version` | `1.5.0-pre.4+forge_1.20.1` | `forge-1.20.1/gradle.properties:41` |
| 26.1.2 `mod_version` | `1.5.0-pre.4+neoforge_26.1.2` | `neoforge-26.1.2/gradle.properties:54` |
| fabric `mod_version` | `1.5.0-pre.4+fabric_1.20.1` | `fabric-1.20.1/gradle.properties:29` |
| `starengine_version` | `1.1.0-pre.2`（四线一致） | `fabric:40` / `forge:114` / `neo:43` 起的段落 |
| jar 内依赖区间 | `[1.1.0-pre.2,2.0.0)`，`type=required`（neo）/ `mandatory=true`（forge） | `META-INF/neoforge.mods.toml:141-144` / `META-INF/mods.toml:154-158` |
| 内嵌库 | `META-INF/jarjar/` 计数 **0**（两线） | `unzip -l <jar> \| grep -c 'META-INF/jarjar/'` |

P0 产物（`build/libs`，**2026-10-22 18:32 重建（含筹码商店品质外框恢复批）**）：

- neo `astral_dice-1.5.0-pre.4+neoforge_1.21.1.jar` — **2,558,631 B** — `742132dd929754c84f6df362dacdd36453f5b7e6ce6feee15f780d231a8da59a`
- forge `astral_dice-1.5.0-pre.4+forge_1.20.1.jar` — **2,604,187 B** — `4b984008a03f4bf8b236144eacdbc5814b821105fd4141f1b90125c521ff269a`
- forge dev named jar `build/devlibs/astral_dice-1.5.0-pre.4+forge_1.20.1.jar` — **2,574,721 B** — `8f81e4c3ba6710b4a544e8c5df262c1adbb38690dd88dd1c30709ed0784e6e7f`
- 历史值（只作背景，均已被覆盖）：pre.4 18:18（电击手套 FX 批）= neo 2,558,408 B `02719fe6…` / forge 2,603,959 B `42cce572…` / devlibs 2,574,494 B `67bc4d24…`；pre.4 18:09（强化拒止品质批）= neo 2,556,778 B `cab211ef…` / forge 2,602,334 B `5141780a…` / devlibs 2,572,866 B `544b61de…`；pre.4 17:53（史莱姆批）= neo 2,556,774 B `bdfbe5f9…` / forge 2,602,332 B `d9780bd0…` / devlibs 2,572,864 B `2849258d…`；pre.4 17:2x = neo 2,556,723 B `7198cc75…` / forge 2,602,280 B `fbb77627…` / devlibs 2,572,814 B `6a445c8f…`；pre.3 15:47 = 2,555,654 B `f063102b…` / 2,601,208 B `7bf787df…`。
- 同版本号（pre.4）的中间构建件**不残留**：Gradle 同名覆盖，`build/libs` 与 `build/devlibs` 里 pre.4 各只有上面那三份之一（2026-10-22 18:0x 复核：`Get-ChildItem -Filter 'astral_dice-1.5.0-pre.*.jar'` 全树只剩 pre.1/pre.2/pre.3/pre.4 各线一份）。
- ⚠️ 但**旧版本产物仍留在各子项目 build 目录**（勿投放）：`neoforge-1.21.1/build/libs` pre.1 2,522,270 / pre.2 2,522,595 / pre.3 2,555,654；`forge-1.20.1/build/libs` pre.1 2,567,077 / pre.2 2,567,445 / pre.3 2,601,208；`forge-1.20.1/build/devlibs` pre.1 2,627,085 / pre.3 2,571,732；**仓库根 `build/libs` 另有一对陈旧 pre.3**（neo 2,555,455 @ 00:22、forge 2,600,940 @ 11:59）——取件只认 §2 这三行。
- 投放（2026-10-22 18:32 实测，逐处 `Copy-Item` + `Get-FileHash` 双向对账；**投放目标**（`run/<线>/mods` 与两个整合包）里的旧 pre.3 已删除，每处只剩 pre.4 一份）：
  | 位置 | jar 形态 | 字节 | sha256 前 16 | 对账 |
  |---|---|---|---|---|
  | `run/1.21.1/mods` | reobf（`build/libs`） | 2,558,631 | `742132DD929754C8` | MATCH |
  | `run/1.20.1/mods` | **named dev jar**（`build/devlibs`） | 2,574,721 | `8F81E4C3BA6710B4` | MATCH（同源，§6-3） |
  | `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods` | reobf（`build/libs`） | 2,558,631 | `742132DD929754C8` | MATCH |
  | `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods` | reobf（`build/libs`） | 2,604,187 | `4B984008A03F4BF8` | MATCH |
- jar 内自检：neo version=`1.5.0-pre.4+neoforge_1.21.1` / forge=`1.5.0-pre.4+forge_1.20.1`；`META-INF/jarjar/` = **0**；`AstralPartyCommand.class` 含 `killbarricade` / `killrepulsion` / `AFTERM`；11 张 `mob_effect/*.png` 与 `images/shop/unbord_chips/` 源文件 md5 全等（两线各 11，mismatch 0）；`javap` 读数见 §5-13。
- 投放后目录状态：两个 `run/<线>/mods` 与两个整合包的 `astral_dice-*.jar` 只剩 pre.4 一份；整合包侧 `starengine-*` 与 `AutoJS-forge-1.20.1-1.0.0.jar`（reobf，167,358 B）在位。
- 成对库：两个整合包各有 `starengine-neoforge-1.21.1-1.1.0-pre.2.jar`（98,733 B）/ `starengine-forge-1.20.1-1.1.0-pre.2.jar`（103,604 B）；
  两个 `run/<线>/mods` **无** starengine jar（dev 由 Gradle classpath 提供，历史如此）。

## 3 本批批次表（一行一批：提交号 / 主题 / 关键落点）

| 提交 | 主题 | 关键落点 |
|---|---|---|
| `d11690d5` | release(main): 1.5.0-pre.2 → pre.3 | 四线 `mod_version` 升 pre.3 |
| `44a8fde3` | balance(batch-A) | 修女/多萝西配方、双立牌降稀有、摩托头盔数值 |
| `fe4aa05f` | feat(balance+ui) | 路障/拒止 20:00 寿命、电击手套含主目标、JEI 筹码商店、筹码无边框图标 |
| `9cf48dc6` | feat(fx+fix) | 激光/轨道炮改客户端光线渲染、转账音效、青之诅咒豁免 |
| `ab360c70` | fix(card) | 手牌抹杀只丢 1 张 |
| `5c80ede9` | feat(sign+event) | 强化拒止充能计入放置者、拉怪逐 tick 收敛、全员事件 1 秒硬间隔 |
| `a924dfee` | fix(meta) | StarEngine 改硬前置（required/mandatory + 版本下限） |
| `b2887776` | fix(shop) | 买卡优先并入同类栈，不另起一格 |
| `f2054fa5` | refactor(chip) | 额外电池冷却折算限定为立牌主动技能 |
| `0938a60d` | feat(scope+shop+meta) | 电池折算限立牌主动冷却、商店并栈收口、抽奖盒投入冷却、硬前置补强 |
| `36d3c43c` | test(cases) | 修正筹码商店用例口径 + 新增 5 条批次行为用例（两线实机全绿） |
| `3e200a9b` | fix(card+docs) | 创造模式两线扣卡对齐、CHANGELOG 库版本订正、补 `docs/compat-1.5.0-porting.md` |
| `afdcd453` | docs(modpack) | 新增整合包交接单 `docs/handover-1.5.0-pre.4-modpack.md`（投放表 / 自检 / 坑） |
| `15c81424` | release+fix(pre.4) | 四线升 pre.4；强化拒止可被玩家拆除 + 两实体 `kill()` 覆写；`/astralparty killbarricade\|killrepulsion`；11 个筹码效果图标换无边框 |
| `7c51df63` | fix(sign+lang) 史莱姆被动 15 秒 | `item/sign/LuluSignItem.java` 的 `HURT_COOLDOWN_TICKS` **20 → 300**（P0 两线）；tooltip / 帕秋莉手册 / 两份 CHANGELOG 同步 |
| `d4f58586` | docs(rules) 派子代理屏蔽人格注入 | 红线区〈子代理与测试环境硬规则〉第 8 条 + `AGENTS.md` §B 免人设句 + `AGENTS-full.md` 第 45 条 + 索引末行 `L3603` + `AGENTS-TOC.md:4` 行数 |
| 本批头提交（`git log -1`） | fix(rarity) 强化拒止品质改奇特 | 两线 `item/ModItems.java` 的 `EFFECT_CARD_REPULSION` 由 `AstralRarities.epic()` → `bizarre()`；判据 javap `lambda$static$52`（neo）/`lambda$static$56`（forge）`bizarre` 1 / `epic` 0；专属牌品质总则落 `AGENTS-full.md`（第 46 条）；CHANGELOG 两语 + `docs/features/repulsion-card-spec.md` 订正；产物 18:09 重投 4 处 |

## 4 实机读数（原文摘录）

### 4.1 MC 1.20.1（本轮，forge 线）

用例：`FIXBATCH-EVENT-GATE` / `FIXBATCH-REPULSION-ENDER` / `FIXBATCH-REPULSION-CHARGE` /
`FIXBATCH-GLOVE-MAINTARGET` / `FIXBATCH-SHOP-STACK-BLANK` + `CHIP-LOGIC` 回归 ⇒ **6/6 PASS**。

关键读数原文：

```
G4Z_GATE0:a1=1:a2=0:remain2=38:a3=1:t3=88
END_WATCH:max=1.19:samples=200:jumps=0
cg/charge 2→4→5→5
G1_GREAD:dmg=6,2
CARDSLOT:slots=1:count=3     blank_insert denied:remain=2400
```

### 4.2 MC 1.21.1（本轮，neo 线）

5 条 FIXBATCH 判据全命中：`G4A_TICKET2:after1=3:after2=3`、`END_WATCH:jumps=0`、`charge 2→4→5`、
`equip=1:cd=1:charge=2:dmg=6,2`、`slots=1:count=3`；末条 **49/49 PASS**。

### 4.3 创造模式扣卡（改前实测，改后见 §4.4）

- 1.21.1 创造：扣 **0** 张（`live.consume(1, player)` 按 instabuild 豁免，`BaseEffectCardItem:338`/`:423`、`CardChoiceMenu:130`）。
- 1.20.1 创造：扣 **1** 张（裸 `shrink`）⇒ 已改三处为 `if (!player.isCreative())`。

### 4.4 创造模式扣卡复验（2026-10-22 16:10，1.20.1，**PASS**）

用例：`scripts/test/cases/CARD-NOCOST-CREATIVE-1.20.1.json`（46 steps / 10 asserts，`channel=kubejs`）
命令：`pwsh -NoProfile -File scripts/test/mt.ps1 --version 1.20.1 --phase cases --case cases/CARD-NOCOST-CREATIVE-1.20.1.json --case-timeout 300`
⇒ `MT_CASE_RESULT: CARD-NOCOST-CREATIVE-1.20.1 = PASS`（exit 0，10/10 assert PASS，无 crash-reports）。

关键读数原文（探针日志，非截图）：

```
AP_NC1_PREP:hand=astral_dice:effect_card_monster_laser:handn=1:cards=1:...:plays=0:max=1:blocked=0      # 创造段基线
AP_NC1A_READ:hand=astral_dice:effect_card_monster_laser:handn=3:cards=3:...:plays=0:max=1:blocked=0      # 发 3 张后
AP_FX_NC1_CAST:called=1:tok=597076215:sel0=1:tid=1426:hand=...:cd=0:charge=0
AP_NC1B_READ:hand=astral_dice:effect_card_monster_laser:handn=3:cards=3:sel=0:token=-1:...:plays=1:max=1:blocked=1   # 出牌成功、卡数不变
AP_NC2A_READ:hand=astral_dice:effect_card_monster_laser:handn=3:cards=3:...:plays=0:max=1:blocked=0      # 生存对照段
AP_FX_NC2_CAST:called=1:tok=147078057:sel0=1:tid=1450:hand=...:cd=0:charge=0
AP_NC2B_READ:hand=astral_dice:effect_card_monster_laser:handn=2:cards=2:sel=0:token=-1:...:plays=1:max=1:blocked=1   # 生存扣 1 张
```

- 创造段：`plays=1`（**出牌确实发生**）且 `handn/cards` 3→3 ⇒ **扣 0 张** ⇒ `BaseEffectCardItem.java:337` 的 `if (!player.isCreative())` 实机成立。
- 生存对照段：同一流程 `handn/cards` 3→2 ⇒ 量具本身能测出扣除（**反假绿**）。
- 两段 `blocked=1` 都在出牌成功（`plays=1`）之后出现，属 `EffectCardPeriod` 正常锁定；出牌成功后会话结束（`sel=0:token=-1`），故断言**不锚** `sel/token`。
- 前置两处闸门（第一轮假绿的真因，见 §6 坑 10）：① 未佩骰子时 `TargetSelectionManager.java:287` 直接 return；② `cardprep` 的 item 参数必须加引号。
- 摘录的那一轮（16:07:32 / 16:07:57 两次 cast）与最终 PASS 轮（16:08:47–16:09:33，`.mt_progress.json` 记 `step_index=56/56`）读数一致，仅 token 值不同（后轮为 `884704855` / `1965359188`），结论不变。
- 收尾：`mt.ps1 --version 1.20.1 --phase stop --force --purge-saves` ⇒ `MT_STOP: OK (1s)` / `MT_CLEANUP_SAVES: PURGED 1` / `MT_CLEANUP_RESULT: OK`；
  `Get-Process java` 余 2 个，均为 Gradle 守护进程（Zulu 21 PID 34464 + Adoptium 17 PID 41468），无客户端残留。

### 4.5 pre.4 本批（2026-10-22 17:3x）—— **无新增实机读数**

本批两条判据本质上要实机：① 「强化拒止可被玩家打掉」需要真实玩家攻击与伤害结算；② 「没有生物式死亡动画」是目视观感。
已备齐的静态判据见 §5-13（`kill()` / `hurt` / `getHurtSound` 字节码、`AFTERM` 机器行字符串、图标 md5、lang 三语 1045 键一致、两份 CHANGELOG 措辞）。
⇒ 实机待用户明确下达；建议最小验证面：装 pre.4 → `/astralparty killrepulsion all` 清场 → 放一个新强化拒止 → 玩家用任意武器砍一刀（观察是否逐步变透明并按实际伤害扣耐久）→ 砍到归零（观察是否同一 tick 消失、无躺倒/变红）。

### 4.6 史莱姆被动 15 秒批（2026-10-22 17:5x）—— **无实机读数**

判据全为静态：`javap -p -c -constants` 命中 `private static final int HURT_COOLDOWN_TICKS = 300;` 且 `onHurt` 存在（两线）；jar 内 `zh_cn/en_us/ja_jp` 的 `tooltip.astral_dice.sign.lulu_passive` 末行分别为
`每 §915 秒§7 触发一次§r` / `§7at most once per §915s§r` / `§915 秒§7 に 1 回のみ発動§r`，`astral_dice.guide.entry.lulu_sign.2` 句末各带「每 15 秒最多触发一次」口径；
`check_lang_sync.ps1` exit 0（三语 §/换行/占位符计数一致）；`tooltip_color_audit.ps1` 违规数 **174 = 基线 174**（`git archive HEAD` 导出副本复跑同数，且命中项里无 `lulu_passive`）。
⇒ 「15 秒内只吃一次受击」属计时行为，真要取证得实机连续挨两刀看治愈层数；实机待用户下达。

### 4.7 强化拒止品质改奇特批（2026-10-22 18:0x）—— **无实机读数**

改动只影响物品稀有度（tooltip 边框色 / 名称色 / 稀有度文案），不触碰功能与掉落池；判据全为静态 + 字节码：
① `is_exclusive.json` 7 项与两线 `ModItems` 的 `rarity(...)` 逐项对账 ⇒ 7/7 全 `bizarre`（改前 6/7，仅 `effect_card_repulsion` 为 `epic`）；
② 打包后 `javap -p -c` 按方法块定位：neo `lambda$static$52` / forge `lambda$static$56` 内 `AstralRarities.bizarre()` 1 次、`.epic()` 0 次；
③ `check_lang_sync.ps1` exit 0（本批未动 lang）、`verify_effect_icons.py` PASS（未动图标）。
⇒ 实机只需目视确认 tooltip 边框不再是金色（史诗色）⇒ 待用户下达。

## 5 遗留与欠账

### Blocker

（无）P0 双 jar 与整合包已就位，判据见 §2。

### Major

1. **`verify_probe_class_refs` 6 处红点已定性（2026-10-22），守门自身三处缺陷已修，只剩 1 条产物陈旧项**：
   原 6 处**无一是源码真缺口**，逐条取证 ——
   - `mezz.jei.common.Internal`：真类在，JEI 走 **Curse Maven**（`neoforge-1.21.1/build.gradle:187`
     `curse.maven:jei-238222:8512040` / `forge-1.20.1/build.gradle:272` `curse.maven:jei-238222:8778011`），
     缓存 jar 内含 `mezz/jei/common/Internal.class`；脚本原只解析 `maven.modrinth` ⇒ 盲区。
   - `org.lwjgl.glfw.GLFW`：真类在（gradle 缓存 `lwjgl-glfw-3.3.x.jar`），LWJGL 由加载器/启动器运行期提供、不进 `build.gradle`。
   - `net.minecraftforge.registries.ForgeRegistries`：**被判红的 2 处**都在 `kubejs/1.21.1/server_scripts/astral_bugfix_probe.js:16817`/`:16833`
     的 **JSDoc 反例说明**里（该线跑 NeoForge，本无此类）⇒ 正则把注释当真引用 = 假红。
     （另：`kubejs/1.20.1/server_scripts/astral_bugfix_probe.js:2789` 有**真代码** `Java.loadClass` 同名类，1.20.1 本来就是 Forge、可解析，不在红点内。）
   - `client.DamageNumberStore`：类在源码（三条线都有；该文件最早由 `46986687`（2026-09-26）加入，10-20 的 `15d5ff25` 只是最近一次触及），
     红因 **26.1.2 产物与源码不同源**：`neoforge-26.1.2/build/libs` 最新仍是 `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`（2026-10-05 11:55），
     该 jar 内 `DamageNumberStore` 条目 **0**（30 个 `client/*` 类可正常枚举）⇒ P1 待重建，非代码缺失。
   **守门修正**（`scripts/verify/verify_probe_class_refs.ps1`）：① 新增 `$script:CurseRe` 并解析
   `~/.gradle/caches/modules-2/files-2.1/curse.maven/<group>/<fileId>/**/*.jar`；② 白名单加 `org.lwjgl.`；
   ③ 新增 `Remove-JsComments`（**单趟、字符串感知**扫描：字符串字面量原样保留 → `/*…*/` 换成等量换行保行号 → `//` 丢到行尾；
    并对 `build.gradle` 也先剥注释，避免把**被注释掉的依赖**当成未解析）；④ 依赖解析补 `curse.maven` 与 `org.lwjgl.` 之后
    该门对 1.20.1 的 `maven.modrinth:immediatelyfast:NJ17fqEK` 误报也消失（该坐标在 `forge-1.20.1/build.gradle:325` 本是注释行）。
   **修正后实跑**：1.21.1 = 21 jar / 34,081 条目 / **150 处** / **不可解析 0**；1.20.1 = 21 jar / 31,270 / **142 处** / **0**；
   26.1.2 = 19 jar / 46,346 / **108 处** / **1**（仅 `DamageNumberStore`）⇒ 末行仍 `RESULT: FAIL（1 处）`。
   （处数比修前多 2/1/1：旧版剥注释实现会**误删真代码**，被删掉的 `Java.loadClass` 不参与判据。）
   ⚠️ 该脚本判 FAIL 时 **`exit 1`**（2026-10-22 实测，`scripts/verify/verify_probe_class_refs.ps1:527-529`），判据以末行 `RESULT` 为准。
2. ~~**创造模式扣卡修复未实机复验**~~ ⇒ **2026-10-22 已闭环（§4.4）**：1.20.1 创造实测扣 **0** 张、生存对照扣 **1** 张，
   用例 `scripts/test/cases/CARD-NOCOST-CREATIVE-1.20.1.json` PASS。三处改动（`BaseEffectCardItem.java:337`/`:423`、
   `CardChoiceMenu.java:134`）中，`:337`（选择器确认路径）已实机取证；`:423`（自身牌 `use()`）与 `:134`（`CardChoiceMenu#consumeSelection`）
   仍只有静态判据 ⇒ 登记为**剩余轻度欠账**（同一用例未覆盖这两条入口）。
3. **P1 两线整体欠账**（26.1.2 / fabric 均 0 命中，1.21.1 为基准）：JEI 筹码（`compat/jei` 9 文件）、
   `BeamFx`（4）、`GlobalEventGate`（3）、`RepulsionEntity`（10）、`CardShopMenu#planPlacement`（1）、
   `EventDrawBlock`（6）。⇒ 1.5.0 正式版前不验证、不移植（用户裁决）；映射表见 `docs/compat-1.5.0-porting.md`。
4. **提交未推 + 无 tag**：`origin/multi-main..HEAD` = **26**（pre.4 提交前实测；本次提交后 27），`git tag --list '*1.5.0-pre.*'` 为空。按用户口径默认不 push、不打 tag。

### Minor

0. **WSL 口径已全面失效（2026-10-22，本轮已完成）**：`AGENTS.md`「回滚裁决」1–6 条改写为 Windows 原生口径（含第 7 条收尾关闭游戏），
   `docs/agent/AGENTS-full.md` 末尾新增〈WSL 口径失效对照〉（5 行口径表）+ 就地标注 L2861 / 第 37 条 ⑥ 失效；
   本文档 §1 / §7 改 Windows 口径（`F:\` 路径、`gradlew.bat`、`pwsh`、PowerShell 判据命令）。
   判据：`python docs/agent/verify-split.py` ⇒ 四项全 `[OK]`（快照 3,522 行 / 红线区 11,520 字符逐字一致 / 索引覆盖 L121–L3522 / AGENTS.md 21,874 字符）。

5. **立牌冷却口径：9 个「起冷却」子类已补 `setSignActiveMaxCooldown`（2026-10-22 完成，两线对等）**。
   补齐清单（`item/sign/`，四条线里 P0 两条同改）：`BonnieSignItem`、`DorothySignItem`、`HaiqingSignItem`、
   `JillSignItem`、`MegasSignItem`、`MosesSignItem`、`RenSignItem`、`TeruSignItem`、`ZhaoSignItem`
   （每个文件 +2 行：一行注释 + 一行 `setSignActiveMaxCooldown(player, (long) (<与 CooldownEnd 同一表达式>))`）。
   ⚠️ **不是 11 个**：原清单里的 `JasmineSignItem:121` 与 `KomachiSignItem:150` 只**缩短已有冷却**
   （`setSignActiveCooldownEnd(now + 剩余×比例)`），它们**是 max 的消费者而非生产者**（Jasmine 还读 `maxCooldown`
   算减半量）⇒ 在那里写 max 会把基准改错，**故意不改**。
   判据：`:neoforge-1.21.1:classes` + `:forge-1.20.1:classes` 均 BUILD SUCCESSFUL；两线各 9 个立牌的匿名内部类
   （`BonnieSignItem$1.class` 等）字节码内含 `setSignActiveMaxCooldown` 引用（外层类不含，调用在 action 内部类里）；
   `git diff` 中 **18 个 java 文件各 +2 行**（整份 diff 是 23 文件：18 java + 4 文档 + 1 脚本）。
6. **26.1.2 线与测试环境都停在旧 jar**：`run/26.1.2/mods/astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`（10-06），
   且 `neoforge-26.1.2/build/libs` 最新也只到 `astral_dice-1.3.7-beta.2+neoforge_26.1.2.jar`（2026-10-05 11:55）
   ⇒ 该线守门第 4 条红点（`client.DamageNumberStore`）就是这个陈旧产物造成的；P1 不重建（用户裁决），重建后自动转绿。
7. **AutoJS 两个本地补丁未上游化**：根级 `pack.mcmeta`（`pack_format 15`）+ `status` 反射兜底。
8. **`testworld` 会被 `--purge-saves` 删除**：现存仅 `run/1.21.1/saves/testworld`；下轮实机前必须先 `--phase env`。
9. **`run/1.21.1/mods` 未装 JEI**：该目录实测 **13 个 jar / 14 个条目**（architectury / AstralDice / AutoJS×2 / collective /
   ferritecore / ImmediatelyFast / iris / kubejs / modernfix / carpet / rhino / sodium / superflat…），**没有** JEI 与 LWJGL；
   这正是脚本必须再补 gradle/Maven 依赖 classpath 的原因（§5-1）。

10. ~~**源码领先于产物（2026-10-22 立牌冷却补丁）**~~ ⇒ **15:47 已闭环**：`gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar` 重建
    （BUILD SUCCESSFUL），四处投放 sha256 全部 MATCH，§2 的 sha256 已重录。历史记录：14:25 的 jar 不含
    `setSignActiveMaxCooldown` 补丁，`run/<线>/mods` 与整合包当时同源同理。
11. **代码审查工具链已换插件（2026-10-22 用户裁决，本轮已完成）**：旧 DSH `code_review_*` 六件套已不在工具面上，
    现行 = 插件 `@michengai/dsh-code-review`（v1.0.1）的单一工具 `dsh_code_review({ input })`：`input` 空 ⇒ 原生范围选择，
    非空 ⇒ **整段原样**作为自定义审查要求（不解析 CLI 选项，范围须写自然语言）；报告以 GFM 回本会话，`/review-status` 查进度、`/review-cancel` 取消。
    同步位置：`AGENTS.md` 红线区〈二次验证规范〉+〈子代理与测试环境硬规则〉第 4/5/7 条、契约 §C 第 6 条、索引末行；
    `docs/agent/AGENTS-full.md`（红线区同文 + 新增〈代码审查工具链更新〉节 + §5-1/§5-2 规则行）；`docs/agent/AGENT-SPLIT.md:19`；
    `docs/handover-new-session-context.md:57`/`:85`；`scripts/test/TESTING-SPEC.md` 附录 A 续 55；本文档 §6 坑 9。
    判据：`python docs/agent/verify-split.py` 四项 `[OK]`；`grep -c 'code_review_'` 在 AGENTS.md 与 AGENTS-full 红线区 = 0。
    复核（`dsh_code_review` 首跑 `fb01cfcf-ffce-4b0c-80a5-feb1c4016ef0`，patch is correct）提 4 条文档精度问题，**已全部就地修**：① AGENTS-full 新节「历史条目读法」清单误列第 37 条、漏第 33/38/39 条 ⇒ 实测命中为第 25 / 27 / 30 / 33 / 38 / 39 / 41 条（`git grep -n 'code_review' -- docs/agent/AGENTS-full.md` 对回条目号）；② 新节「本文档 §6 坑 9」指向 AGENTS-full 内不存在的章节 ⇒ 改为 `docs/handover-1.5.0-pre.4-main.md` §6 坑 9；③ 新节未写报告落盘位置 ⇒ 表内补 `reportDirectory`（默认 `$DSH_HOME/code-review` 即 `C:\Users\xmace\.dsh\code-review`，文件名 `sha256(会话).json`，`lib/journal.js` 临时文件 + rename 原子写）与「`input` 为空需宿主交互问答器，非交互环境必须给非空 input」；④ `scripts/test/TESTING-SPEC.md:1524` 自标「现行」却仍用 `code_review` 且写作「由主 Agent 亲自编写」 ⇒ 改 `dsh_code_review` 复核并订正为「派子代理编写」。改完复跑 verify-split 四项 `[OK]`，full 行数不变（3,541），索引末行无需再动。
 12. **派活口径已改为 DSH 自带子智能体（2026-10-22 用户裁决，本轮已完成）**：DSH 会话的派活唯一通道 = `subagent`（一次性、全新 context、brief 自包含）/
     `subagent_fork`（接续已完成回合）/ `spawn_teammate` + `team_task_*`（多人并行，`write_scopes` 互不重叠）；**Codex 的 `delegate_task` 不得再引用**
     （`~/.codex/mcp/delegation/server.mjs` 属另一宿主，日志字段只作历史背景）；复核仍走 `dsh_code_review({ input })`，不为复核另派 subagent。
     主 Agent 分工不变：识别需求 → 分配工作 → 验收成果；子代理只干活与回报，**禁轮询**（完成靠运行时通知）。
     同步位置：`AGENTS.md` 契约 §A 第 3/6 条 + §B 首句与③ + 新增 **§B-2**；`docs/agent/delegation-runbook.md` **整篇重写**（82 行，工具选择表 / 四条收敛规则 / 标准流程 / 中断三查 / brief 模板 / 失败模式 + 末尾只留 Codex 历史段）；
     `docs/agent/AGENTS-full.md` 末尾新增〈派活口径〉节；`docs/agent/AGENTS-TOC.md` **全表重算**（190 行标题，行号与偏移按当前文件）；`docs/handover-new-session-context.md:85`；本文档 §1 委派两行。
     判据：`python docs/agent/verify-split.py` 四项 `[OK]`；`grep -rn 'delegate_task' AGENTS.md docs/agent/delegation-runbook.md` 只应命中「禁止 / 历史」语境；
     `grep -n 'delegate_task' docs/handover-new-session-context.md` 仅 1 处且位于「禁止」句；`grep -c 'delegate_task' docs/agent/delegation-runbook.md` = 1（首页口径句）。
     复核（`dsh_code_review` run `326a3779-dd78-44dc-8c0a-2b9e29f0a22e`，结论「应视为正确（不阻断）」）提 5 项，**已全部就地修**：① 红线区仍有「只读查证用 `Explore` / 跑命令用 `general-purpose`」属 Codex 侧派活类型 ⇒ 两文件同文改为「按 §B 职业声明派 `subagent`」（红线区 12,070→12,129 字符，逐字仍一致）；② 工具表 `subagent_fork` 行场景含「复核」与下一行 `dsh_code_review` 冲突 ⇒ 改为「续写 / 二次核查；复核不算」；③ Minor-12 数字虚报（runbook 83→82 行、`delegate_task` 计数 2→1）；④ 新节「历史条目读法」指认位置 0 命中 ⇒ 改为旧版 runbook 与 `handover-new-session-context.md:85` 旧文；⑤ `AGENTS-TOC.md` 未随新节更新 ⇒ 全表重算（190 标题）。
13. **pre.4 本批已完成（2026-10-22，尚未提交）**：① 强化拒止改为**只吃玩家近战伤害**（`RepulsionEntity#hurt` 里非 `Player` 来源恒 `false`，`dealt = max(1.0F, amount)`，归零 `shatter()`），`getHurtSound` → `SoundEvents.GLASS_HIT`；② 两者死亡观感改船/盔甲架口径（`die()` 内 `release(false)` + 同 tick `remove(RemovalReason.KILLED)`，无躺倒/变红）；③ 新增 **`kill()` 覆写**（`RepulsionEntity#kill → shatter()`、`BarricadeEntity#kill → die(genericKill())`）—— 原版 `/kill` 走 `Entity#kill() → hurt(genericKill, MAX)`，而 `genericKill` 无实体 ⇒ 旧实现下 `/kill` 恒无效（§6-11）；④ `/astralparty killbarricade|killrepulsion [<radius>|all]`（两线 `command/AstralPartyCommand.java`，权限 2，默认 64 格，`all` 全维度，落 `AFTERM|type=…|killed=…|mode=…|radius=…|dim=…|x=…|y=…|z=…`）；⑤ 11 个筹码效果图标换无边框素材（`tools/gen_chip_effect_icons.py` + `tools/chip_effect_icon_map.json`，P0 两线各 11 张）；⑥ 四线版本 1.5.0-pre.4 + 两份 README 版本表/示例同步 + 两份 CHANGELOG 各 3 处改写 + 2 条新条目。
    判据（全绿）：`gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar` BUILD SUCCESSFUL；`python tools/gen_chip_effect_icons.py --check` ⇒ `effects=22 misses=0 unaligned=0`（exit 0）；`python tools/verify_effect_icons.py` ⇒ `RESULT: PASS`（四线 65/65/53/53）；`pwsh -NoProfile -File tools/check_lang_sync.ps1` ⇒ exit 0（P0 两线 zh/en/ja 各 **1045** 键一致）；`javap`：neo `RepulsionEntity` 含 `hurt`/`getHurtSound`/`die`/**`kill()`**、`BarricadeEntity` 含 `die`/**`kill()`**/`remove(RemovalReason)`；forge reobf 对应 SRG = `m_6469_`（hurt）/ `m_7975_`（getHurtSound）/ `m_6667_`（die）/ **`m_6074_`**（kill）/ `m_142687_`（remove）；sha256 见 §2。
     复核（`dsh_code_review` run `44d3003c-d99a-4c0b-8cce-6e686b1e0e11`，R1–R6 SUPPORTED / R7·R8 部分成立，**无 P1 阻断缺陷**）：提 6 项，**已全部就地修** —— ① 两份 CHANGELOG 把强化拒止写成「100 点耐久 / 半径 8 格 / 超过 8 格拉回 7.5 格」，而实现是 `MAX_HEALTH = 50.0D`（两线 `event/ModEntityEvents.java:41` / forge `:46`）+ `AFFECT_RADIUS = 12.0D` / `CLAMP_RADIUS = 11.5D`（`entity/RepulsionEntity.java:167`/`:171`，forge `:191`/`:195`）⇒ 已改为 **50 / 12 / 11.5**；② `command/AstralPartyCommand.java:141`/`:231`（forge `:143`/`:233`）注释写「球体」而实现是 `new AABB(±radius)` + `getEntitiesOfClass`（`:272-274`）⇒ 两线注释改「立方体范围 AABB（对角最远 半径×√3）」；③ `docs/agent/AGENT-SPLIT.md:3` 版本号 → pre.4；④ `docs/handover-new-session-context.md:31` 时间戳 14:25 → 17:36；⑤ `docs/agent/AGENTS-TOC.md:4` 表头行数 3,576 → **3,591**；⑥ 删掉工作树根 6 个临时脚本（`temp_check_lang.py` / `temp_kill2.py` / `temp_kill3.py` / `temp_mcsrc.py` / `temp_mcsrc2.py` / `temp_md5.py`）。复核自身限制：其沙箱对 `~/.gradle` 只读（`gradle-9.2.1-bin.zip.lck` 拒绝访问）⇒ 用独立 `javac` 替代 `./gradlew`（exit 0），且未跑实机。
14. **P1 两线的筹码效果图标仍是带边框版**（本轮只改 P0：`neoforge-1.21.1` / `forge-1.20.1` 各 11 张）。`neoforge-26.1.2` / `fabric-1.20.1` 的 `textures/mob_effect/*.png` 保持原样，等 1.5.0 正式版前的 P1 批次一起换；`tools/chip_effect_icon_map.json` 已是通用唯一事实源，届时 `--apply` 一次即可。
15. **史莱姆立牌被动改为每 15 秒最多触发一次（2026-10-22 用户裁决，未提交）**：起因是用户要求 tooltip 补「每 15 秒触发一次」，
    实测实现为 **1 秒**（`HURT_COOLDOWN_TICKS = 20`），经用户裁决（选项 A）**实现与文案一起改成 15 秒**。落点：
    ① 代码 `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/sign/LuluSignItem.java:68` 与 `forge-1.20.1/.../LuluSignItem.java:70` 的 `HURT_COOLDOWN_TICKS` **20 → 300**（`onHurt` 判据 `nowTick - getLuluLastHurtTick(player) < HURT_COOLDOWN_TICKS` 未动，`COOLDOWN_REDUCTION_TICKS = 200L` 未动）；
    ② 语言表 P0 两线 × `zh_cn/en_us/ja_jp`：`tooltip.astral_dice.sign.lulu_passive` 末尾追加一行、`astral_dice.guide.entry.lulu_sign.2` 句末补括注；
    ③ `docs/agent/AGENTS-full.md:986` 立牌技能一览 lulu 行被动列订正为「受到**敌对目标攻击** +1 治愈…**每 15 秒最多触发一次**（300 tick；原 20 tick）」；
    ④ 两份 CHANGELOG 各 1 条（写在 `#### 平台与兼容性` 之前）。
    判据：`gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar` BUILD SUCCESSFUL；打包后 javap 命中 `= 300`；`check_lang_sync.ps1` exit 0（P0 三语各 **1045** 键）；`tooltip_color_audit.ps1` **174 = 基线 174**（无 `lulu_passive` 命中）；四处投放 sha256 见 §2。
16. **P1 两线的史莱姆被动仍是 1 秒（20 tick）且文案未补**（`neoforge-26.1.2/.../LuluSignItem.java:170`、`fabric-1.20.1/.../LuluSignItem.java:161` 均内联 20）：按「1.5.0 正式版前 P1 不验证不移植」未动，随 P1 批次一起改（与 §5-14 的筹码图标同批）。
17. **史莱姆批复核（2026-10-22 18:0x，`dsh_code_review` run `27408cf2-5919-461f-8a7d-d948bc50256a`）**：R1–R5 / R7 / R8 **SUPPORTED**、R6 **部分成立**；**无 P1 / P2 代码或产物缺陷**，2 项文档问题已就地修 —— ① `docs/agent/AGENTS-TOC.md:4` 表头「当前 3,591 行」未随第 44 条同步 ⇒ 改 **3,599**；② §2 原写「同目录残留中间构建件 2,556,723 / 2,556,771 / 2,602,329 / 2,572,861 勿投放」与磁盘不符（**同版本号被 Gradle 覆盖，实测不存在**，且与相邻两条自相矛盾）⇒ 改写为「pre.4 中间件不残留 + 旧版本产物仍在各子项目 `build/` 下（列清单）+ 仓库根 `build/libs` 有一对陈旧 pre.3，取件只认 §2 三行」。复核自述限制：沙箱对 `~/.gradle` 只读（`gradle-9.2.1-bin.zip.lck` 拒绝访问）未跑 gradlew，改用 `javap -cp <jar>` + python 常量池解析取证（两 jar 常量池 `ConstantValue=300`、`onHurt` 走 `ldc2_w 300l`）；未实机。它另附一条**非缺陷观察**：`lulu_last_hurt_tick` 默认 `0L`（`component/ModAttachments.java:116`）⇒ 新世界 `gameTime < 300` 时第一次受击被吞（20 tick 时代同样存在，本批只是把窗口从 1 秒放大到 15 秒，未计入缺陷）。
18. **派子代理一律屏蔽人格注入（2026-10-22 用户裁决，本轮已完成）**：① 红线区〈子代理与测试环境硬规则〉新增**第 8 条**（`AGENTS.md` 与 `docs/agent/AGENTS-full.md` 同批逐字，两文件红线区仍字节一致）；② `AGENTS.md` 契约 §B 增补「免人设句」段；③ `AGENTS-full.md` 末尾追加**第 45 条**；④ 索引末行 `| L3047–L3603 |` + 主题追加「派子代理屏蔽人格注入（第 45 条）」，`AGENTS-TOC.md:4` 行数 3,599 → **3,603**。口径：brief 除职业声明外**必须原文带**「不要使用任何人设 / 角色扮演 / 语气修饰 / 昵称自称，按你的职业身份以中性技术口吻输出」；**审查 / 取证类任务必须用全新上下文 `subagent`**（`subagent_fork` 会继承主会话带人设的对话；**复核不在此列** —— 复核一律走 `dsh_code_review`，见硬规则第 4 条）；子代理回报出现自称 / 卖萌 / 情绪表演即**不合规**，要求重发中性版，且**不得把带情绪的段落当证据**；主 Agent 对用户的回复不受限（人设只在表达层）。判据：`python docs/agent/verify-split.py` 四项全 [OK]（红线区 **12,567** 字符逐字一致（P1 措辞订正后实测）、索引覆盖 L121–L3603、`AGENTS-TOC.md` 190 行行号/偏移已全表重算）；`grep -c '屏蔽人格注入' AGENTS.md docs/agent/AGENTS-full.md` 均 ≥1。本批**未实派子代理**（只落规则）。⑤ 复核（`dsh_code_review` run `b96a6a0e-a85e-49dc-8cfd-24b84c8da74f`，报告 `temp\review-persona-rule.md`）的 4 项已全部闭环：P1 = 第 8 条「复核与审查类任务」与第 4 条「复核不派 subagent」冲突 ⇒ 两文件红线区 + §B + `docs/handover-new-session-context.md` 同改为「**审查 / 取证类**（复核不在此列）」；P2 = `docs/agent/AGENTS-TOC.md` 行号列整体漂移（实测错 182 行）⇒ 按 `AGENTS-full.md` 实际标题重算 190 行「行号 + LF 字符偏移」两列；P2 = `docs/agent/delegation-runbook.md` 模板缺免人设句 ⇒ 已补（§4 模板 + §2 派活步骤注明「复核本身不派子代理、走 `dsh_code_review`」）；P3 = §0 编号乱序 ⇒ persona 条改 11、整合包交接单指针改 12。

19. **强化拒止品质改奇特（2026-10-22 用户裁决，已提交 本批头提交（`git log -1`））**：
    ① 代码（P0 两线各 1 行）：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java` 与 `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/item/ModItems.java` 的 `EFFECT_CARD_REPULSION`：`.rarity(AstralRarities.epic())` → `.rarity(AstralRarities.bizarre())`，注释同步「品质:奇特(bizarre;2026-10-22 用户裁决:专属牌一律奇特,原为史诗)」；
    ② 依据：`neoforge-1.21.1/src/main/resources/data/astral_dice/tags/item/is_exclusive.json` 共 7 项，其中 6 项早已 `bizarre()`，只有 `effect_card_repulsion` 是 `epic()` ⇒ 唯一越轨项；
    ③ 规格：`docs/agent/AGENTS-full.md` 效果牌表处新增「专属牌品质总则」（所有 `is_exclusive.json` 登记物品一律 `AstralRarities.bizarre()`；新增专属牌先定奇特档；改档须同步效果牌表 / CHANGELOG / P1 移植清单）；
    ④ 文案：`CHANGELOG_ZH.md:16` 「（史诗；」→「（奇特（bizarre）；」、`CHANGELOG.md:16` 「(Epic;」→「(Bizarre;」；`docs/features/repulsion-card-spec.md` 引用块后追加 2 行裁决说明（用户原文按逐字引用保留）；
    ⑤ 判据：`gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar` BUILD SUCCESSFUL；javap 方法块命中 `bizarre` 1 / `epic` 0（`lambda$static$52` neo、`lambda$static$56` forge）；4 处投放 sha256 全 MATCH（§2）；
    ⑥ **P1 欠账**：P1 两线**整张牌尚未移植**（实测 `grep -rni repulsion neoforge-26.1.2/src` / `fabric-1.20.1/src` 各 0 命中，两线 `is_exclusive` 标签各 6 项、无 `effect_card_repulsion`）⇒ 随 P1 批次整体移植（item / entity / lang / patchouli / tag 全套），**不是**「一行 rarity 待改」；按「1.5.0 正式版前 P1 不验证不移植」未动。
20. **电击手套命中 FX：粒子库电火花 + 落雷冲击音（2026-10-22 18:18，已提交 本批头提交（`git log -1`））**：
    ① 起因（用户原话）：为「电击手套」命中的目标创建 FX，优先用 Irons 粒子库的电火花，并配一个合适音效。
    ② 实现（P0 两线，`src/main/java/com/merlinkitsune/astral_dice/`）：新类 `combat/ElectricShockFx.java` —— `victimCenter(LivingEntity)` 取「脚底 + 半身高」；`sparksAt(ServerLevel, Vec3)` 每目标三批 `sendParticles(new LibraryParticleOptions(...))`（`COMET_AZUR_FILAMENT`×2 弧光 scale 1.7 / `GLINTSTONE_SPARK`×7 火花芯 scale 0.9 / `FROST_SPARK`×4 细闪 scale 0.7）；`shockSound(ServerLevel, Vec3)` 播 `SoundEvents.LIGHTNING_BOLT_IMPACT`（0.5 / 1.5）。
    ③ 接线：`item/chip/ElectricGloveChipItem#executeShock` —— `if (!dealt) continue;` 之后逐目标放火花（**只有真正吃到伤害的目标有电光**），循环外 `if (hitAny)` 只在中心播**一次**音效（半径 3 格多目标时避免叠成轰鸣）；`audio/SoundPlayback` 新增 `playAt(Level, double, double, double, SoundEvent, float, float)` 重载（旧 5 参委托它，`volume<=0 || pitch<=0` 直接返回）。
    ④ 选型依据：粒子库里没有「球形电爆」题材，`comet_azur_filament` 的锯齿蓝丝是唯一像电弧的；音效侧本机**无 ffmpeg** ⇒ 不新增 `.ogg`，改用原版 `LIGHTNING_BOLT_IMPACT`（短促电击冲击，非 `LIGHTNING_BOLT_THUNDER` 的雷鸣尾音），降音量 0.5 + 提音调 1.5 贴合手套小范围电击。
    ⑤ 判据（全绿，静态）：`gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar` BUILD SUCCESSFUL；两 jar 均含 `combat/ElectricShockFx.class`，`javap` 三方法签名齐全；neo `SoundPlayback` 两个 `playAt` 重载（5 参 / 7 参）齐全；4 处投放 `Get-FileHash` 全 MATCH（§2 表）。
    ⑥ **无实机读数**：火花观感 / 音效音量音调需用户明确下达实机（§6-1 的老规矩：重建后必须重新投放，已投放）。
    ⑦ **P1 欠账**：`neoforge-26.1.2` / `fabric-1.20.1` 未移植本 FX（整批随 P1 批次）。
    ⑧ **复核**（`dsh_code_review` run `cdda4ad6-b3c6-4ca1-bda8-692d4a87b9fb`）：R1–R7 **全部 SUPPORTED**，无代码 / 产物缺陷；提 3 项文档订正**已就地修** —— `handover-new-session-context.md` 的 forge devlibs 仍写旧哈希（2,572,866 / `544b61de…` ⇒ 2,574,494 / `67bc4d24…`）与 18:09 时间戳；`playAt` 重载参数数写成「6 参 / 8 参」（实为 **5 参 / 7 参**）；未推送数写 31（实况 30，FX 批未提交）⇒ 顺带把正本 §0 第 1/2 条与第 14 条「未提交」对齐。

21. **筹码商店品质外框恢复 + 卡牌购买钱包音效（2026-10-22 18:32，用户报障驱动）**：
    ① 起因（用户原话）：「筹码商店表示品质的外框（3种颜色）莫名其妙丢失了，需要补回来。然后购买卡牌也没有 wallet 音效，补上。」
    ② 丢框根因：提交 `fe4aa05f`（2026-10-21「筹码无边框图标」批）连 `screen/ChipShopScreen` 的 `TEX_QUALITY_1/2/3` + `qualityTex(int)` + `renderOffers` 里那一段 blit 一起删了（本意只是换**图标**素材）；三张 28×28 描边环贴图 `assets/astral_dice/textures/gui/shop/chipshop_quality_{1,2,3}.png` 一直都在 ⇒ 纯代码回归，无需补美术。
    ③ 代码（P0 两线各 4 文件）：`screen/ChipShopScreen` 恢复三档贴图常量与 `renderOffers` 外框绘制（`qualityCode(chip)` 优先 → 退 `qualityFrameIndexOf(stack)` → `QUALITY_FRAME_NONE` 跳过），并在「⑤b 悬停覆盖层」前恢复 `private static String qualityTex(int frame)`（1/2/其它 → `TEX_QUALITY_1/2/3`）；`client/ChipShopClientState:24` 与 `shop/ChipShopCatalog` javadoc 同步「2026-10-22 起屏幕重新绘制品质外框」；`shop/CardShopMenu#handleBuy` 成功尾段补 `SoundPlayback.playTo(buyer, ModSounds.WALLET_DEPOSIT.get())`（拒绝/失败分支不响）。
    ④ 守门口径反转：`scripts/verify/verify_chip_shop_ui.ps1` 的 G2b 由「断言**不**画外框」改成**正向**断言（`$frameNeed` 5 条 + `qualityTex` 方法体正则三档映射 + 输出行 `CHIPUI_FRAME`）；`$drawnTexNames` 恢复为全部 13 张；`scripts/verify/mutate_chip_shop_ui.ps1` 的 M38（neo，挖掉绘制入口）/ M39（forge，`frame == 2` → `frame == 9`）同步反转。
    ⑤ 判据（全绿，静态）：`pwsh -NoProfile -File scripts/verify/verify_chip_shop_ui.ps1` ⇒ 两线 `CHIPUI_ASSETS: 13/13`、`CHIPUI_LAYOUT: 31/31`、`CHIPUI_TOTAL_FAIL: 0`、`CHIPUI_RESULT: OK`；`mutate_chip_shop_ui.ps1` ⇒ `MUT_TOTAL: 39，判红 39`、`MUT_RESULT: OK`；`gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar` BUILD SUCCESSFUL；`javap -p -c` 两线 `shop/CardShopMenu` 的 `SoundPlayback.playTo` 与 `WALLET_DEPOSIT` 各 **2** 次（原转账 + 新购买），`screen/ChipShopScreen` 的 `qualityTex` 2 / `qualityCode` 4 / `blitStrip` 7；两 jar `META-INF/jarjar/` = 0；4 处投放 sha256 全 MATCH（§2 表）。
    ⑥ **无实机读数**：三色外框观感与卡牌购买音效需用户明确下达实机（§6-1：重建后必须重新投放，已投放）。
    ⑦ **P1 欠账**：`neoforge-26.1.2` / `fabric-1.20.1` 未移植本批（外观/音效随 P1 批次）。
    ⑧ **复核**（`dsh_code_review` run `e6b42a13-bf68-47be-8682-709c3b390c48`）：R1–R4 / R6 / R7 **SUPPORTED**、R5 **部分成立**；**无代码 / 产物缺陷**。它独立取证的关键读数与本批自述一致 —— 外框段与 `fe4aa05f^` 逐字相同（图标在外框之后绘制）、`javap` 计数 `qualityTex=2 / qualityCode=4 / blitStrip=7 / qualityFrameIndexOf=1`、三张 28×28 贴图两线俱在（162/160/158 B）、两 jar `META-INF/jarjar/` = 0、四处投放同源 MATCH、`verify_chip_shop_ui.ps1` 实跑 `CHIPUI_RESULT: OK`、`AGENTS-full.md` **3,621 行** 且 `verify-split.py` 四项 `[OK]`；mutate 套件它按「会改源码再还原」的口径只做静态核对（M38/M39 的 find 串各命中 1 行）。唯一 finding = **本文档 §0 第 2 条与 `handover-new-session-context.md` 的未推送数口径**（提审时实况 31、我按「提交后 32」提前写了 32）⇒ 提交后实况确为 **32**，两处已统一为 32 并注明实测时点。

## 6 坑（踩过的，后续别重踩）

1. **`run/<线>/mods` 的 jar 曾 ≠ `build/libs`（neo 于 2026-10-22 15:47 对齐；forge 按 §6-3 永远放 devlibs named jar）**：当时 neo 是 00:22 的旧构建（同名、2 B 差异），
   forge 是 `build/devlibs` 的 named jar（dev 必需，reobf 的 SRG 调用在 dev 会抛 `NoSuchMethodError`，且 `Error` 不被 MCP 的
   `catch(Exception)` 兜住）。⇒ **每次重建 jar 后一律重新投放并核 sha256**，别拿 `ls` 到同名就当成新构建。
2. **`--purge-saves` 会删 `testworld`** ⇒ 下轮实机前先 `pwsh -NoProfile -File scripts/test/mt.ps1 --version <线> --phase env`。
3. **1.20.1 dev 必须用 `build/devlibs` 的 named jar**；整合包仍用 reobf jar（两者不可互换）。
4. **AutoJS 已按上游 `7138d72` 重编**（包根 `cc.sighs` → `com.sighs`）；仓库内 Maven 坐标仍是
   `cc.sighs.autojs:*`（groupId），别把 groupId 当成包根去改。
5. **StarEngine 不再内嵌**：漏装库 jar 会以 `Missing or unsupported mandatory dependencies` 直接启动失败；
   区间上界固定 `2.0.0`，且**不能**写成 `[1.1.0,2.0.0)`（Maven 把 `pre` 排在正式版之后）。
6. **抽奖盒「投入空白筹码」只清购买冷却**（无刷新机制），与筹码商店「刷新批次 +1」语义不同且**有意**（用户裁决）。
7. **探针 `Java.loadClass` 的类被搬走 ⇒ 该线所有 kubejs 断言全红**（报「KubeJS 有错误」，极易误判为模组 bug）。
8. **`verify_probe_class_refs` 的假红有三个来源**（2026-10-22 全部实测）：① JS **注释**里的类名被当引用；
   ② 依赖只写 `curse.maven` 而脚本只解析 `maven.modrinth`；③ 加载器运行期提供的库（LWJGL）不在 `build.gradle`。
   修完仍剩红点时，先查**该线产物是否陈旧**（jar 早于源码 `mtime`），别急着往源码里补类。
9. **旧 `code_review_*` 接口已作废（2026-10-22 用户裁决）**：`code_review_execute` 撞 cell 600s 墙钟、`preview` 有时效、
   `code_review_start` 后还会卡在 `protocol-correction` —— **一律不要再调用**。现行 = 插件 `@michengai/dsh-code-review` 的
   `dsh_code_review({ input })`（`input` 空 ⇒ 原生范围选择；非空 ⇒ 整段原样作为审查要求，范围要写自然语言）。

10. **实机用例的两处假绿闸门（2026-10-22 实测，1.20.1 扣卡用例连吃 5 轮假绿）**：
    ① **骰子门控**：`TargetSelectionManager.java:287` `if (!CurioSlotUtil.hasDiceEquipped(player)) return;` —— 未佩骰子时
    `confirm` 仍成功、会话被移除、token 换新，但 `action.apply` **根本不执行** ⇒ 既不扣卡也不出牌（`plays=0`）。
    用例必须先 `/astralshop curioreset <tag>` → `/give @s astral_dice:golden_dice 1` → `/astralprobe equipslot dice "astral_dice:golden_dice" <tag>`。
    ② **`/astralprobe cardprep <tag> <item>` 的引号口径**：注册处 `tag` = `StringArg.word()`（**不能加引号**）、
    `item` = `StringArg.string()`（**必须加引号**）；两种错写法都**静默失败**（全不加引号报 `参数后应有空格分隔…`，全加引号在末尾报错）。
    正形：`/astralprobe cardprep NC1 "astral_dice:effect_card_monster_laser"`。定性手段 = `/astralprobe cmdcheck <tag> <原始命令>`（只解析不执行）。
    ③ 判据只锚 `handn/cards/plays/max/blocked`：出牌成功后会话结束为 `sel=0:token=-1`，锚 `sel=\d+` 必假红。
11. **原版 `/kill` 对「`hurt` 全覆写」的自定义实体无效（2026-10-22 实测踩中）**：`/kill` 的路径是 `Entity#kill()` → `hurt(damageSources().genericKill(), Float.MAX_VALUE)`，
    而 `genericKill` 的 `getEntity()` 是 `null`。自定义实体若把 `hurt` 收窄成「只认玩家/生物来源」（强化拒止、路障），该 `hurt` 恒返回 `false` ⇒ 实体**杀不掉、且不给任何提示**。
    ⇒ 非生物实体要能被 `/kill` 杀死，**必须覆写 `public void kill()`**（本批两线都补了），或在 `hurt` 里对 `genericKill` 特判。
12. **新增 lang key 时 `ja_jp.json` 也要补（2026-10-22 踩中）**：`tools/check_lang_sync.ps1` 核的是 P0 两线 **zh_cn / en_us / ja_jp 三份**键集，只补 zh/en 会直接 FAIL。
    另：脚本化插入 key 时若把两条 key 塞在**同一行**（逗号后两个空格），JSON 合法、游戏也能读，但 diff 与 grep 都会漏 —— 本批用 `,[ ]{2,}"command\.` 正则规范成独立行（4 份各 1 处）。
13. **效果图标守门必须有清单（2026-10-22 定案）**：`gen_chip_effect_icons.py --check` 若只靠「图标像素 == 筹码物品图」启发式，一旦有边框素材被覆盖成无边框，之后**永远空集、永远绿**（假绿）。
    ⇒ 唯一事实源 = `tools/chip_effect_icon_map.json`（11 条），启发式只负责首次发现；新增筹码效果必须同时补清单，`misses>0` 才是真信号。
14. **`dsh_code_review` 必须给 `run_code` 设 `timeoutMs ≥ 600000`（2026-10-22 踩中）**：默认 120s 直接把 cell 打死，插件只留
    `{"state":"finished","result":{"status":"interrupted","detail":"run_code settled"}}`（139 B，`$DSH_HOME/code-review/<sha256>.json`），报告 0 字；
    它**不会**在后台续跑 ⇒ 直接重跑一次（同一批改动重复审查无害）。
15. **多行 tooltip 追加一句会打乱三语「结构标记」（2026-10-22 踩中）**：`tools/check_lang_sync.ps1` 的告警口径是
    `(占位符元组, 换行数, 颜色码数)` 三者**跨语言必须相等**（`§.` 每个 § 都计 1）。只给 zh 加 `每 §915 秒§7 触发一次§r`（3 个 §）而 en 加
    `once per §915s§r`（2 个 §）⇒ 立刻 `[WARN] tooltip.astral_dice.sign.lulu_passive: 结构标记不一致 zh=((), 3, 6) en=((), 3, 5)`。
    ⇒ 追加带颜色的句子时**先算三种语言各自的 § 数**，不够的一侧补一个语义无害的回落码（本批 en 补成 `§7at most once per §915s§r`）。
    （该告警**不阻断** exit code，但会在交接报告里留下真假难辨的红字，仍应清零。）
16. **`AGENTS-full.md` 行数变了必须同步两处（2026-10-22 连续两批都漏）**：① `AGENTS.md` 索引末行的 `| L3047–L<行数> |`（`verify-split.py` 第 3 项会抓）；② `docs/agent/AGENTS-TOC.md:4` 表头的「当前 **N** 行」—— **这一处没有任何守门**，只能靠人肉或复核发现，别忘。

17. **改物品稀有度后，判据不能只看源码 diff（2026-10-22，强化拒止批）**：`ModItems` 的稀有度写在**静态注册 lambda** 里（`registerItem(...)` 的 `Supplier`），`javap -p -c` 的普通方法体里看不到；正解是按**方法块**切分 javap 输出后，找**同时含目标类名**（`RepulsionCardItem`）的那一块，再数 `AstralRarities.bizarre()` / `.epic()` 的 `invokestatic` 次数（本批命中 `lambda$static$52` / `lambda$static$56`）。用「固定字符窗口 ±6000」数关键词会被邻近其它卡片的调用污染（首次数出 `epic=2`，纯属窗口越界）。
18. **改 `epic()` → `bizarre()` 时别看 `AstralRarities` 在 forge 线被 reobf**：库 jar（starengine）不参与本仓 reobf，`bizarre` 方法名在 forge 产物里原样保留（已实测）。真正需要 SRG 名的是**本仓自己的**方法（如 §5-13 的 `m_6074_` = `kill`）。

19. **粒子库没有「电」题材的现成贴图（2026-10-22 电击手套 FX 批）**：179 张里最接近电弧的是 `comet_azur_filament`（锯齿蓝丝），`glintstone_spark` / `frost_spark` 只是点火花；剩下的火花系（`holy/flame/ember_spark`）是暖色十字，染色后会脏。⇒ 做电类 FX 就「细长弧光 + 冷色点花」两件套，别硬找球形电爆。另：`LibraryParticleOptions` 的 `rgb` 是**逐通道相乘**（`LibraryParticle` 里 `setColor`），给 0x9BE8FF 这类冷白会明显压暗原贴图，想保亮要用接近白色的值。
20. **改动只落在 chip 类里也会漏判「命中」语义（2026-10-22）**：`ElectricGloveChipItem#executeShock` 的 `dealt` 才是「这目标真的吃到伤害」的判据（`victim.hurt(...)` 的返回值），FX 必须挂在 `if (!dealt) continue;` **之后**；挂在循环头会让未受伤目标也冒电光（假象）。同理音效要放循环外 + `hitAny` 守卫。

21. **反向断言守门在功能回滚后会变成「假绿」（2026-10-22 踩中）**：`verify_chip_shop_ui.ps1` 的 G2b 原本断言「不画品质外框」（2026-10-21 无边框口径），用户报障时它**照样全绿** —— 因为「外框被误删」正好满足那条反向断言。⇒ 一旦某个特性是**用户可见的既有行为**，守门必须是**正向**断言（存在性 + 逐字锚点 + 方法体完整性），反向断言只用于「明确禁止某写法」的场景；特性被回滚时，反向断言必须同批反转（本批 M38/M39 已同步）。
22. **变异套件跑不完就中断会把变异留在源码里（2026-10-22 踩中）**：`mutate_chip_shop_ui.ps1` 单跑 2–4 分钟，`run_code` 默认 `timeoutMs` 120s 会把它打断，当场把第 35 条变异（`if (chip >= 0) {` → `if (chip > 0) {`）留在 `forge-1.20.1/.../screen/ChipShopScreen.java`，随后守门报 `筹码格命中判定被改` —— 极易被误判成「我刚写的代码有问题」。⇒ 跑变异套件一律 `tools.pwsh({ run_in_background: true })` + `job_output({ wait: true })` 并给 `run_code` 足够 `timeoutMs`；**中断后先 `git status` 看有没有残留变异痕**（它会自动还原，但被杀时来不及），另需预建 `temp\t87`（脚本不建目录，缺目录只报 `Could not find a part of the path ...\temp\t87\mutation.log`）。

## 7 关键路径（命令与文件）

```powershell
# 现状（git 一律 git bash 的 git；-C 显式指定工作树）
git -C F:\MCProject\astral_dice_multiloader status --short -uall
git -C F:\MCProject\astral_dice_multiloader rev-parse --short HEAD
git -C F:\MCProject\astral_dice_multiloader rev-list --count origin/multi-main..HEAD

# 版本 / 依赖
Get-ChildItem -Recurse -Filter gradle.properties | Select-String -Pattern 'mod_version'
tar -xOf 'neoforge-1.21.1\build\libs\astral_dice-1.5.0-pre.4+neoforge_1.21.1.jar' 'META-INF/neoforge.mods.toml' | Select-String -Pattern 'modId="starengine"' -Context 0,4
(tar -tf <jar> | Select-String -Pattern 'META-INF/jarjar/').Count

# 产物一致性（三处：build/libs / run 副本 / 整合包）
Get-FileHash -Algorithm SHA256 'neoforge-1.21.1\build\libs\astral_dice-1.5.0-pre.4+neoforge_1.21.1.jar',
                            'forge-1.20.1\build\libs\astral_dice-1.5.0-pre.4+forge_1.20.1.jar',
                            'forge-1.20.1\build\devlibs\astral_dice-1.5.0-pre.4+forge_1.20.1.jar' | Format-List
Get-ChildItem 'D:\.minecraft\versions\狐の航空学 Voxy Edition\mods' | Where-Object Name -match 'astral_dice|starengine' | Select-Object Name,Length,LastWriteTime
Get-ChildItem 'D:\.minecraft\versions\1.20.1-Forge 模组测试\mods'     | Where-Object Name -match 'astral_dice|starengine' | Select-Object Name,Length,LastWriteTime

# P1 缺口
foreach ($f in 'BeamFx','GlobalEventGate','RepulsionEntity','EventDrawBlock','planPlacement') {
  "$f = " + (Get-ChildItem neoforge-26.1.2/src,fabric-1.20.1/src -Recurse -File | Select-String -Pattern $f -List | Measure-Object).Count }

# 立牌冷却口径
Get-ChildItem neoforge-1.21.1/src/main/java -Recurse -Filter *SignItem.java | Select-String -Pattern 'setSignActiveCooldownEnd|setSignActiveMaxCooldown'

# 效果图标 / 语言表守门
python tools/gen_chip_effect_icons.py --check     # 期望 effects=22 misses=0 unaligned=0
python tools/verify_effect_icons.py              # 期望 RESULT: PASS（四线 65/65/53/53）
pwsh -NoProfile -File tools/check_lang_sync.ps1   # 期望 exit 0（P0 三语各 1045 键）

# 电击手套命中 FX（ElectricShockFx）判据
(tar -tf 'neoforge-1.21.1\build\libs\astral_dice-1.5.0-pre.4+neoforge_1.21.1.jar' | Select-String 'combat/ElectricShockFx').Count   # 期望 1
javap -p -cp 'neoforge-1.21.1\build\libs\astral_dice-1.5.0-pre.4+neoforge_1.21.1.jar' com.merlinkitsune.astral_dice.combat.ElectricShockFx
javap -p -cp 'neoforge-1.21.1\build\libs\astral_dice-1.5.0-pre.4+neoforge_1.21.1.jar' com.merlinkitsune.astral_dice.audio.SoundPlayback   # 期望 5 参 + 7 参两个 playAt

# 清场（实体管理命令，权限 2）
#   /astralparty killbarricade [<radius>|all]
#   /astralparty killrepulsion [<radius>|all]
#   日志机器行：AFTERM|type=…|killed=…|mode=…|radius=…|dim=…|x=…|y=…|z=…

# 收尾
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version <线> --phase stop --force
pwsh -NoProfile -File F:\MCProject\astral_dice_multiloader\scripts\test\mt.ps1 --version <线> --phase stop --purge-saves
python F:\MCProject\astral_dice_multiloader\docs\agent\verify-split.py
```

关键文件：`docs/compat-1.5.0-porting.md`（P1 映射）、`docs/compat-1.20.1-forge.md`、
`scripts/test/TESTING-SPEC.md`、`scripts/test/cases/FIXBATCH-*.json`、
`scripts/verify/verify_probe_class_refs.ps1`、`docs/audit/1.3.2-release-audit-2026-09-27.md:50`。
