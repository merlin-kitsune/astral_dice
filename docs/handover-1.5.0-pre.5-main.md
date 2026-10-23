# 交接正本 — 1.5.0-pre.5（multi-main）

## 0 速览（一屏）

1. 分支 `multi-main`；HEAD = **本批头提交**（本批提交后即此值，提交号见 `git log -1`）（pre.4 施工批 + 史莱姆被动 15 秒批 + 派子代理屏蔽人格注入规则批 + 强化拒止品质改奇特批 + 电击手套 FX 批 + 筹码商店品质外框恢复批（第 15 条）均已提交）。
2. 未推送提交 **1** 个（`git rev-list --count origin/multi-main..HEAD`：pre.4 与 353cfed4 已 push，**提交本批之前实测 0**，提交本批后为 1），**无** tag（`1.5.0-pre.3` / `1.5.0-pre.4` / `1.5.0-pre.5` 均无，需用户放行）。
3. 四线 `mod_version` 均为 `1.5.0-pre.5+<加载器后缀>`（2026-10-22 22:2x bump）；`starengine_version=1.1.0-pre.2`（四线一致，未动）。
4. jar 内硬前置：`modId="starengine"` / `versionRange="[1.1.0-pre.2,2.0.0)"`，`META-INF/jarjar/` 计数 **0**（pre.4 两 jar 已复核）。
5. 可交付范围：**只发 P0**（`neoforge-1.21.1` + `forge-1.20.1`）。P1 两线版本号同为 pre.5，但内容不含 1.5.0（本批也未移植）。
6. P0 双 jar（pre.5，**2026-10-23 13:40 重建，含「雷暴将至」修复 + 立即刷怪波**）已投放 **4 处**（两个整合包 + 两个 `run/<线>/mods`）+ 根 `build/libs`，逐处 sha256 见 §2。
7. 守门 8 跑 7 绿；唯一红 = `verify_probe_class_refs`（26.1.2 产物陈旧，P1 待重建，见 §5-1）。
8. 实机：1.20.1 本批 6/6 PASS、1.21.1 5 条 FIXBATCH 判据全命中、末条 49/49 PASS；**创造模式扣卡复验 PASS**（§4.4，2026-10-22 16:10，用例 `CARD-NOCOST-CREATIVE-1.20.1`）。
9. **pre.4 本批（2026-10-22 17:3x）无新增实机读数**：改动 = 实体拆除口径 + 死亡动画 + 两个管理命令 + 11 张效果图标 + 版本号，判据目前全为静态/字节码/资源 md5（§5-13），实机待用户下达。
10. **史莱姆立牌被动改为「每 15 秒最多触发一次」（2026-10-22，已提交 `7c51df63`）**：`HURT_COOLDOWN_TICKS` **20 → 300**（仅 P0 两线 `item/sign/LuluSignItem.java`），tooltip / 帕秋莉手册条目 / 两份 CHANGELOG 同步；判据见 §5-15，**无实机读数**。P1 两线未动（§5-16）。
11. **派子代理一律屏蔽人格注入（2026-10-22 用户裁决，本轮已完成）**：红线区〈子代理与测试环境硬规则〉新增第 8 条 —— brief 必带「不要使用任何人设 / 角色扮演 / 语气修饰 / 昵称自称」；复核 / 审查 / 取证类任务一律用全新上下文 `subagent`（`subagent_fork` 会继承带人设的对话）；子代理回报出现人格化表达即判不合规。判据见 §5-18。
12. 面向整合包会话的交接单见 `docs/handover-1.5.0-pre.5-modpack.md`（投放表 / 自检读数 / 坑 / 剩余事项）。
13. **强化拒止品质改为奇特（2026-10-22 用户裁决，已提交）**：`is_exclusive` 标签 7 项里只有它还是 `epic` ⇒ P0 两线各改一行 `.rarity(AstralRarities.epic())` → `.rarity(AstralRarities.bizarre())`；判据 = 字节码 `lambda$static$52`（neo）/ `lambda$static$56`（forge）内 `AstralRarities.bizarre()` 1 次、`epic()` 0 次；并新增「专属牌品质总则」+ CHANGELOG/需求文档订正 + 新产物投放 4 处（18:09）。详见 §5-19。
14. **电击手套命中 FX（2026-10-22 18:18，已提交）**：命中目标撒 Irons 粒子库电火花（`comet_azur_filament`×2 电弧 / `glintstone_spark`×7 火花芯 / `frost_spark`×4 细闪，rgb `0x9BE8FF`·`0xE8FBFF`），圆心一次 `SoundEvents.LIGHTNING_BOLT_IMPACT`（音量 0.5 / 音调 1.5）；实现 `combat/ElectricShockFx.java`（P0 两线）+ `audio/SoundPlayback` 新增 7 参 `playAt` 重载（旧 5 参委托它） + `item/chip/ElectricGloveChipItem#executeShock` 接线。判据见 §5-20，**无实机读数**。
15. **筹码商店品质外框恢复 + 卡牌购买钱包音效（2026-10-22 18:32）**：`screen/ChipShopScreen` 把 2026-10-21 误删的三色品质描边环（`chipshop_quality_1/2/3`，28x28）重新画回筹码格脚下、`client/ChipShopClientState`+`shop/ChipShopCatalog` javadoc 同步口径；`shop/CardShopMenu#handleBuy` 成功尾段补 `SoundPlayback.playTo(buyer, ModSounds.WALLET_DEPOSIT.get())`（拒绝分支不响）。守门 `verify_chip_shop_ui.ps1` 的 G2b 由反向改为正向断言、`mutate_chip_shop_ui.ps1` 的 M38/M39 同步反转（`MUT_TOTAL: 39，判红 39`）。判据与产物见 §5-21 / §2，**无实机读数**。

16. **pre.4 全量实机测试（2026-10-22 19:00–19:40，用户下达「pre.4 以上所有更改全部做一遍实机测试并输出报告」）**：P0 双线各 **10 条用例：9 条机器判据 PASS + 1 条截图取证**（4 条新用例 × 2 线 + 6 条回归）——`PRE4-CHIPSHOP-FRAME-*` 的 vision 断言在测试台里是 **DELEGATED**（不判红绿），本体只覆盖 crash/kubejs/mixin 健康项，外框的机器判据在静态守门 `verify_chip_shop_ui.ps1` 的 G2b；机器行原文见 §4.8。本轮为跑通判据改了**测试资产**（两份 fixbatch probe 新增 `hurtby`、bugfix probe 新增 `barplace` 与 `doLuluPrep` 补骰子、6 条用例重写/订正），**未改任何产品源码**；新增的探针能力同时让 §4.5–§4.7 三批「无实机读数」落地。17. **pre.5 指示器批（2026-10-22 22:3x，本批头提交 `git log -1`）**：① **移除电磁炮「就位」效果指示器**（效果 `railgun_ready` 连同注册 / 三语 lang / 图标 / 图标映射一并删除，「生效中」指示器 6 枚 → **5 枚**）；② **磨刀石 / 安全气囊保命触发后新增同名倒计时指示器**（新效果 `whetstone_guard_cd` / `airbag_guard_cd`，时长 = 剩余冷却，`event/PlayerTickEvents#updateChipCooldownIndicators` 每 tick 按附件里的冷却结束刻同步；与 ∞「生效中」指示器并存）；③ 四线 `mod_version` 升 **`1.5.0-pre.5`**；④ 需求「激光让客户端卡一下」**在真整合包内取证定性为「不是光束绘制」**（§4.10：光束可见帧 avg 6.05 ms vs 无光束 6.24 ms、绘制本体 4–66 µs），**产品源码零改动**。判据见 §5-24 / §5-25，产物与投放见 §2。

18. **玩家侧状态效果保护（2026-10-23 用户裁决，本批头提交）**：玩家身上的效果**除非被声明可清除（牛奶 / 蜂蜜瓶）否则不受任何外部影响**；mob 侧**保持原版**（不搬计时表 —— 女巫 / 诡术师会反复喝药清 mob 状态，改保活会叠加 tick 负载）。新增两线 `event/PlayerEffectProtection`，把此前散落的裸 `addEffect` 收口为统一入口并留下「结束 tick」快照，外部清除时 `astral_dice:` 直接取消、原版命名空间**下一 tick 按余量补回**；主动移除必须走 `removeNow`。独立复核 `dsh_code_review`（run `c20953f4`）三条 finding 已全部修复。详见 §5-26，**无实机读数**（补回时序 / 牛奶可清除 / mob 不受影响三项待用户下达）。

19. **Jade 生物护甲改为「防御力」（2026-10-23 用户裁决 A，本批头提交）**：审计发现 Jade 玩家分支显示 `defensePowerOf`（防御力）而生物分支回落原版 `getArmorValue()`，与骰战结算 `CombatFormula.mobDefenseInt` 是两个单位；两线各改 1 文件（`mixin/jade/ArmorValueProviderMixin.java`）把非玩家分支改走 `mobDefenseInt(TargetCategory.classify(entity), getArmorValue(), 韧性)`。产物 12:35 重建、4 处投放 sha256 全 MATCH（§2）。详见 §5-28，**无实机读数**。

20. **「雷暴将至」修复 + 立即刷怪波（2026-10-23 用户裁决，本批头提交）**：① 修掉审计查出的 4 处缺陷（×2 复制域无 `MobSpawnType` 白名单 / 复制体未 `finalizeSpawn` / 无视 `isCanceled`·`isSpawnCancelled` / 落点不校验）；② 新增「施放瞬间在玩家 8–16 格刷 5–7 只僵尸·骷髅·蜘蛛（僵尸与骷髅强制铁头盔、掉落率 0），15% 概率追加一只女巫；半径 32 格内只刷一波」；两线各改 1 文件 `event/EventEffects.java`。产物 13:40 重建、4 处投放 sha256 全 MATCH（§2）。详见 §5-29，**无实机读数**。

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
| neo `mod_version` | `1.5.0-pre.5+neoforge_1.21.1` | `neoforge-1.21.1/gradle.properties:37` |
| forge `mod_version` | `1.5.0-pre.5+forge_1.20.1` | `forge-1.20.1/gradle.properties:41` |
| 26.1.2 `mod_version` | `1.5.0-pre.5+neoforge_26.1.2` | `neoforge-26.1.2/gradle.properties:54` |
| fabric `mod_version` | `1.5.0-pre.5+fabric_1.20.1` | `fabric-1.20.1/gradle.properties:29` |
| `starengine_version` | `1.1.0-pre.2`（四线一致） | `fabric:40` / `forge:114` / `neo:43` 起的段落 |
| jar 内依赖区间 | `[1.1.0-pre.2,2.0.0)`，`type=required`（neo）/ `mandatory=true`（forge） | `META-INF/neoforge.mods.toml:141-144` / `META-INF/mods.toml:154-158` |
| 内嵌库 | `META-INF/jarjar/` 计数 **0**（两线） | `unzip -l <jar> \| grep -c 'META-INF/jarjar/'` |

P0 产物（`build/libs`，**2026-10-23 13:40 重建（含「雷暴将至」修复 + 立即刷怪波；诊断探针已剔除）**）：

> ✅ **2026-10-23 13:40 已重建并含本批「雷暴将至」修复 + 立即刷怪波（提交后 §5-29）**：两 jar 内 `event/EventEffects` 的 `javap` 命中 `spawnStormWave` / `createWaveMob` / `placeStormMob` / `findSpawnY` / `isSpawnFloor` / `equipStormHelmet` / `placeStormCopy` + 7 个 `WAVE_*` 常量，字节码含 `MobSpawnType.NATURAL` 门控、`isCanceled` / `isSpawnCancelled` 早退与 4 参（neo）/ 5 参（forge reobf SRG `Mob.m_6518_`）`finalizeSpawn`；`META-INF/jarjar/` 仍 0；4 处投放 + 根 `build/libs` 已刷新。上一批（12:35，Jade 生物护甲 `539c4698`）的三份 jar 已作废，仅作历史值（下行）。

- neo `astral_dice-1.5.0-pre.5+neoforge_1.21.1.jar` — **2,569,892 B** — `6c13fbe01e4e8eb6c9e2e1ccbb5e8e52a7103ef48b51983294e654e195d3e407`
- forge `astral_dice-1.5.0-pre.5+forge_1.20.1.jar` — **2,615,326 B** — `0c85cd3ec9b6b9947b98178ff19e6d728d18e671057bb98d576705d8b4eeb031`
- forge dev named jar `build/devlibs/astral_dice-1.5.0-pre.5+forge_1.20.1.jar` — **2,585,937 B** — `00151870f1a694af7df7feb3acdaa91abef035359e3fb7a3987ec7570588b403`
- 历史值（只作背景，均已被覆盖）：pre.5 13:29（复核前，已被上行覆盖）= neo 2,568,549 B `e99db150…` / forge 2,614,004 B `8f714e84…` / devlibs 2,584,622 B `7122d0c4…`；pre.5 12:35（**不含**「雷暴将至」批）= neo 2,565,536 B `a34cbc48…` / forge 2,611,060 B `aebca4d5…` / devlibs 2,581,605 B `0d961f7a…`；pre.5 12:05（**不含** Jade 生物护甲批）= neo 2,565,317 B `d9b3742d…` / forge 2,610,860 B `3a54d2bc…` / devlibs 2,581,395 B `284528e5…`；pre.5 22:32（**不含**效果保护批）= neo 2,559,837 B `f25546b6…` / forge 2,605,382 B `e30980b4…` / devlibs 2,575,899 B `86c6350b…`；pre.4 22:21（**带诊断探针的临时构建，勿投放，已删除**）= neo 2,561,853 B `bfc88d8e…`；pre.4 18:32（筹码商店品质外框批）= neo 2,558,631 B `742132dd…` / forge 2,604,187 B `4b984008…` / devlibs 2,574,721 B `8f81e4c3…`；pre.4 18:18（电击手套 FX 批）= neo 2,558,408 B `02719fe6…` / forge 2,603,959 B `42cce572…` / devlibs 2,574,494 B `67bc4d24…`；pre.4 18:09（强化拒止品质批）= neo 2,556,778 B `cab211ef…` / forge 2,602,334 B `5141780a…` / devlibs 2,572,866 B `544b61de…`；pre.4 17:53（史莱姆批）= neo 2,556,774 B `bdfbe5f9…` / forge 2,602,332 B `d9780bd0…` / devlibs 2,572,864 B `2849258d…`；pre.4 17:2x = neo 2,556,723 B `7198cc75…` / forge 2,602,280 B `fbb77627…` / devlibs 2,572,814 B `6a445c8f…`；pre.3 15:47 = 2,555,654 B `f063102b…` / 2,601,208 B `7bf787df…`。
- 同版本号（pre.5）的中间构建件**不残留**：Gradle 同名覆盖，`build/libs` 与 `build/devlibs` 里 pre.5 各只有上面那三份之一。
- ⚠️ **旧版本产物仍留在各子项目 build 目录**（pre.1–pre.4，勿投放）；**仓库根 `build/libs` 已于本批（2026-10-23 13:40）用 `pushToRootBuild` 刷新为 pre.5 一对**（坑 28；根目录同时保留 P1 旧件 `astral_dice-1.3.7-*` 与 `starengine-*-1.1.0-pre.2.jar`）——取件只认 §2 这三行。
- 投放（2026-10-23 13:40 实测，逐处 `Get-FileHash` 双向对账；四个目标里的 `astral_dice-1.5.0-pre.*` 每处只剩 pre.5 一份）：
  | 位置 | jar 形态 | 字节 | sha256 前 16 | 对账 |
  |---|---|---|---|---|
  | `run/1.21.1/mods` | reobf（`build/libs`） | 2,569,892 | `6C13FBE01E4E8EB6` | MATCH |
  | `run/1.20.1/mods` | **named dev jar**（`build/devlibs`） | 2,585,937 | `00151870F1A694AF` | MATCH（同源，§6-3） |
  | `D:\.minecraft\versions\狐の航空学 Voxy Edition\mods` | reobf（`build/libs`） | 2,569,892 | `6C13FBE01E4E8EB6` | MATCH |
  | `D:\.minecraft\versions\1.20.1-Forge 模组测试\mods` | reobf（`build/libs`） | 2,615,326 | `0C85CD3EC9B6B994` | MATCH |
- jar 内自检（`python zipfile` + `javap`）：neo version=`1.5.0-pre.5+neoforge_1.21.1` / forge=`1.5.0-pre.5+forge_1.20.1`；`META-INF/jarjar/` = **0**；依赖区间 `[1.1.0-pre.2,2.0.0)` 在位；**诊断探针 0 残留**（`client/ClientFrameProbe.class` 0 个、字节码含 `AP_FP_` 的 class 0 个）；新效果图标 `textures/mob_effect/{whetstone_guard_cd,airbag_guard_cd}.png`（410 / 483 B）两线俱在、`railgun_ready.png` **0** 个；`command/AstralPartyCommand.class` 含 `killbarricade` / `killrepulsion` / `AFTERM`。
- 投放后目录状态：两个 `run/<线>/mods` 与两个整合包的 `astral_dice-*.jar` 只剩 pre.5 一份；整合包侧 `starengine-*` 与 `AutoJS-forge-1.20.1-1.0.0.jar`（reobf，167,358 B）在位；**整合包里本轮注入的测试资产已全部清除**（`kubejs/server_scripts/` 的 `astral_bugfix_probe.js` / `astral_fixbatch_probe.js` / `zz_lpfx_drive2.js` 已删、世界副本 `saves/lp-fx-test` 已删，用户原世界 `saves/新しい世界` 未动）。

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
| `548b4405` | fix(rarity) 强化拒止品质改奇特 | 两线 `item/ModItems.java` 的 `EFFECT_CARD_REPULSION` 由 `AstralRarities.epic()` → `bizarre()`；判据 javap `lambda$static$52`（neo）/`lambda$static$56`（forge）`bizarre` 1 / `epic` 0；专属牌品质总则落 `AGENTS-full.md`（第 46 条）；CHANGELOG 两语 + `docs/features/repulsion-card-spec.md` 订正；产物 18:09 重投 4 处 |
| `fc913507` | release+ui(pre.5) 指示器批 | 删电磁炮「就位」指示器；磨刀石 / 安全气囊保命触发后新增**同名倒计时**指示器（`whetstone_guard_cd` / `airbag_guard_cd`）；四线升 pre.5；整合包内激光卡顿取证定性为「非光束绘制」（§4.9 / §5-25） |
| 本批头提交（`git log -1`） | feat(effect) 玩家侧状态效果保护 | 新增两线 `event/PlayerEffectProtection`（统一计时权威 + 抗外部清除 + `removeNow` 主动移除入口 + 死亡/登出/重生清场）；`ModEffectEvents` HIGH/LOWEST 双通道 + `PlayerEvent.Clone`；两线各 26 文件 `EffectTimerGuard.apply` 收口；审计正本 `docs/features/effect-client-server-split-audit.md`；**无实机读数**（§5-26） |

| 本批头提交（提交后 `git log -1`） | fix(jade) 生物护甲改显示防御力 | 两线 `mixin/jade/ArmorValueProviderMixin.java` 的非玩家分支改 `CombatFormula.mobDefenseInt(TargetCategory.classify(e), e.getArmorValue(), 韧性)`（玩家分支仍 `DiceCombatModifiers.defensePowerOf`）；判据 javap `classify`@18 / `mobDefenseInt`@33；产物 12:35 重建 + 4 处投放（§2） |
| 本批头提交（提交后 `git log -1`） | fix(event) 「雷暴将至」修复 + 立即刷怪波 | 两线 `event/EventEffects.java`：×2 钩子加 `MobSpawnType.NATURAL` 白名单 + 取消早退 + 复制体补 `finalizeSpawn`（4/5 参分叉）+ 7 点落位校验；新增 `spawnStormWave`（5–7 只僵尸/骷髅/蜘蛛 + 15% 女巫、8–16 格、`player.y ± 4` 纵向扫描、铁头盔 `setDropChance 0`、32 格内一波）；判据 javap 7 个新方法 + `WAVE_*` 常量；产物 13:40 重建 + 4 处投放（§2） |

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

### 4.8 pre.4 全量实机（2026-10-22 19:00–19:40，P0 双线，**各 9 条机器判据 PASS + 1 条截图取证**）

跑法：`pwsh -NoProfile -File scripts\test\mt.ps1 --version <线> --phase env` → `--phase launch`（脱离式、冷启动）→ 逐条 `--phase cases --case <case_id>`；两条线各一轮。1.21.1 那轮跑了两遍：首轮 3 条红，全部定性为**测试资产问题**（见 §5-22、坑 23–26），修完重跑全绿。

| 用例 | 1.21.1 | 1.20.1 | 覆盖的 pre.4 改动 |
|---|---|---|---|
| `PRE4-REP-KILL-*` | PASS | PASS | 强化拒止可被玩家拆掉 / 怪物伤害无效 / `/kill` / `/astralparty killrepulsion` |
| `PRE4-BARR-KILL-*` | PASS | PASS | 路障 `killbarricade`（all / radius）+ `/kill` |
| `PRE4-LULU-15S-*` | PASS | PASS | 史莱姆立牌被动 15 秒冷却（`HURT_COOLDOWN_TICKS` 300） |
| `PRE4-CHIPSHOP-FRAME-*` | 截图† | 截图† | 筹码商店三色品质外框 —— †vision 断言在 `mt_case.ps1:1623` 是 **DELEGATED**（`2439` 映射 `→VIS`，不判红绿）⇒ 该条 PASS 只代表健康断言通过；外框机器判据 = `verify_chip_shop_ui.ps1` G2b 静态正向 + 截图人工目视 |
| `FIXBATCH-REPULSION-CHARGE-*` | PASS | PASS | 回归：强化拒止充能 |
| `FIXBATCH-GLOVE-MAINTARGET-*` | PASS | PASS | 回归：电击手套主目标 + AOE（同日 FX 的触发面） |
| `FIXBATCH-EVENT-GATE-*` | PASS | PASS | 回归：事件闸门 |
| `FIXBATCH-SHOP-STACK-BLANK-*` | PASS | PASS | 回归：抽奖盒空白筹码 |
| `CHIP-CLIENT-BUY-*` | PASS | PASS | 回归：筹码购买（卡牌购买钱包音效走同一 `SoundPlayback` 通道） |
| `LULU-SIGN-*` | PASS | PASS | 回归：史莱姆立牌主动（顺手修好一条 2026-10-01 起就失效的陈旧用例） |

机器行原文（键读数，两线同形）：

- 强化拒止（1.21.1）：`AP_FX_RP1R_REP_READ:n=1:id=721:…` → 怪物近战 `RP2R:n=1`（**没被打掉**）→ 玩家 999 点 `RP3R:n=0`（**打掉了**）→ 重新放置后 `/kill @e[type=astral_dice:repulsion]` ⇒ `RP4R:n=0` → `/astralparty killrepulsion all` ⇒ `AFTERM|type=repulsion|killed=2|mode=all|radius=-1|dim=minecraft:overworld|x=1|y=-60|z=-9`，`8` 格档 ⇒ `AFTERM|type=repulsion|killed=1|mode=radius|radius=8|…`，随后 `RP7R:n=0`。
- 强化拒止（1.20.1）：同形，`AFTERM|type=repulsion|killed=2|mode=all`、`killed=1|mode=radius|radius=8`。
- 路障（两线）：`AP_B1_BARR:placed=1:n=1:pos=…` / `AP_B2_BARR:placed=1:n=2:pos=…`（证明**真的放下去了**）→ `AFTERM|type=barricade|killed=2|mode=all` → 重建两座后 `/kill @e[type=astral_dice:barricade]` ⇒ `AFTERM|…|killed=0`（**/kill 已被 `kill()` 覆写接管，实体在管理命令扫描前就摘掉了**）→ 单座 + `killbarricade 8` ⇒ `AFTERM|type=barricade|killed=1|mode=radius|radius=8`。
- 15 秒冷却（1.21.1，`api=player_damage`）：`AP_FX_LL0_LUHEAL:heal=0:last=0:now=3321:since=3321` → `LL1_HURT:ok=1:heal=1:last=3386:now=3386:since=0:hp=15` → 3 秒后 `LL2_HURT:ok=1:heal=1:last=3386:now=3518:since=132:hp=14`（**被 300 tick 闸门挡住，since 132 落在 40..299**）→ 17 秒后 `LL3_HURT:ok=1:heal=2:last=3928:now=3928:since=0:hp=14`（闸门到期）。
- 15 秒冷却（1.20.1，`api=player_attack_ds`；该线没有 `p.damage` 包装 ⇒ 走链式回退）：`LL1 … heal=1:since=0` / `LL2 … heal=1:since=131` / `LL3 … heal=2`。
- 收尾：`mt.ps1 --version 1.21.1 --phase stop --force --purge-saves` ⇒ `MT_STOP: OK (0s)` / `MT_CLEANUP_SAVES: PURGED 1` / `MT_CLEANUP_RESULT: OK`；`Get-Process java` 余 3 个**全是 Gradle 守护**（Zulu 21 PID 34464 + Adoptium 17 PID 36080 / 41468），无客户端残留。
- **补跑（2026-10-22 19:44–19:53，复核后）**：复核抓出两处用例文档/判据口径问题（见坑 27 与 §5-22 ⑤），据此改了 4 个用例文件（`LULU-SIGN-*` 的标题与两处 note 对齐「友方 6→6」；`PRE4-CHIPSHOP-FRAME-*` 标题/证据改标「截图取证」并加一条 `note` 说明 vision 是 DELEGATED）⇒ 把改过的 4 条在两条线上**重跑一遍**：`LULU-SIGN-1.21.1` / `PRE4-CHIPSHOP-FRAME-1.21.1` / `LULU-SIGN-1.20.1` / `PRE4-CHIPSHOP-FRAME-1.20.1` **全 PASS**；两次收尾同样 `MT_STOP: OK` + `MT_CLEANUP_SAVES: PURGED 1` + `MT_CLEANUP_RESULT: OK`，`Get-Process java` 余 3 个皆 Gradle 守护。

判据边界（如实登记，别当成已验）：

1. **观感类没有字节判据**：电击手套火花「像不像电」、落雷音的音量音调、图标美术、碎裂粒子观感 —— 机器行只能证明「伤害结算发生了 / 实体在同一 tick 消失」，**不能**证明观感符合预期。三色外框那条更弱一层：`assert/vision` 在测试台里被映射成 **DELEGATED**（`scripts/test/mt_case.ps1:1623` 直接 `return New-MtPair 'DELEGATED'`、`:2439` 标 `→VIS`），**不判红也不判绿** ⇒ 该用例的 PASS 只等于「没崩 + kubejs/mixin 健康」，外框本身靠 `verify_chip_shop_ui.ps1` 的 G2b 静态正向断言 + 截图人工目视（见坑 27）。
2. **卡牌购买钱包音效**只到「购买路径确实调了 `SoundPlayback`」这一层（`javap` 2 次 + `CHIP-CLIENT-BUY` 回归通过）；「耳朵里响起钱包声」仍需人工听。
3. **死亡动画**（同一 tick `remove(KILLED)`、无躺倒/变红）实机只能看到「砍掉即消失」，帧级动画须录屏目视。### 4.9 pre.5 指示器批（2026-10-22 22:3x）—— **无实机读数**

本批产品改动 = 删电磁炮「就位」指示器 + 磨刀石 / 安全气囊冷却倒计时指示器 + 四线版本号 pre.5。
判据目前全为**静态 / 字节码 / 资源 / 守门脚本**（§5-24）；实机观感（倒计时数字是否按秒跳动、图标是否沿用筹码无边框素材）**待用户下达**。

### 4.10 激光「卡一下」取证（**真整合包内**，2026-10-22 22:29–22:31；614 mod + Iris / Complementary + EuphoriaPatches + Voxy + Veil）

- 场景：`狐の航空学 Voxy Edition` 实例、自制世界副本 `lp-fx-test`；驱动 = 聊天框手打 `/astralprobe cardprep` + 三轮 `/astralfixbatch hostiles|cast`（包内**无 AutoJS**）；日志 = `D:\.minecraft\versions\狐の航空学 Voxy Edition\logs\latest.log`。
- 探针 = **全帧无条件**打印 `AP_FP_ALL:ms=…:beams=…:beamDrawUs=…`（临时 `client/ClientFrameProbe` + `BeamFxRenderer` 计时插桩；诊断后已全部删除，产物无残留、§2 已核）。
- 稳定期统计（22:29:30 之后）：
  - **有光束** n=366，avg **6.05 ms**，p50 5.35 / p90 9.57 / p99 12.39 / max 20.1；
  - **无光束** n=8774，avg **6.24 ms**，p50 5.45 / p90 9.78 / p99 14.15 / max 35.9；
  - `beamDrawUs` 取值集合 {4,5,6,7,8,9,21,29,31,38,66} µs ⇒ 光束绘制本体 **≤66 µs（≈一帧的 1%）**；
  - \>20 ms 的帧共 14 / 10677，**全部 `beams=0`**，集中在 22:29:47–55（进世界后的区块 / mob churn）。
- 两次 `AP_FP_BEAM_ADD:first=1:color=ff2626:width=0.05` 在 22:29:59.30 / 22:30:04.69，与 CAST 相隔恰好 10 tick（= `DamageCardFxScheduler.DELAY_TICKS`）。
- F3 同屏读数（无光束时）：`164 fps T: 180`、p50 180 / p98 88 / p99 56、`Display 2560x1441`、RTX 5090、`Mem 42% 13836/32768MB`、16x Ryzen 7 9800X3D。
- **结论：光束绘制不是「卡一下」的成因**；本轮日志无 `Can't keep up` / `ticks behind`。第一轮（22:18）看到的 66–103 FPS 是进世界 churn 撞上光束窗口，同口径平稳期是 6.05 ms ≈ 165 FPS。
- 判据边界：**单机单场景**（超平坦、朝地平线、顶配机型）；用户实际场景（远处 LOD 多、光影负载高、多人）未复现 ⇒ 若仍能感知卡顿，需要「卡的那一下」的场景 + F3 读数再定位。

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

22. **pre.4 全量实机测试（2026-10-22 19:00–19:40，用户下达）—— 双线 10/10 PASS**：
    ① 范围与读数见 §4.8；共 20 次用例执行（P0 两线 × 10 条）。1.21.1 首轮 3 条红，全部定性为**测试资产问题**（非产品缺陷），修完重跑全绿。
    ② 为跑通判据新增/修正的**测试资产**（唯一改动面，产品源码零改动）：
      - `scripts/test/resources/kubejs/<1.21.1|1.20.1>/server_scripts/astral_fixbatch_probe.js`：新增 `/astralfixbatch hurtby <tag>`（现场造一只 NoAI 掠夺者 → 施加一次怪物近战伤害 → 同 tick 读 `heal/last/now/since/hp` → 攻击者立刻 discard；伤害链全记在机器行 `api=` 与 `tried=` 里）；`fbxDoLuluHealRead` 的 `now` 从 `p.level.getGameTime()` 改为既有 `fbxNow(p)`（前者在 Rhino 下取到 -1）。
      - `scripts/test/resources/kubejs/<线>/server_scripts/astral_bugfix_probe.js`：新增 `/astralprobe barplace <tag> <dx> <dz>`（按 `item/card/BarricadeCardItem#applyEffect` 的真实路径落碰撞方块 + 建实体 + `setCollisionPos`）；`doLuluPrep` 补装金骰子（`putInSlot(p,"dice",…,0)`）。
      - 用例：`PRE4-BARR-KILL-*` 把裸 `/summon` 换成 `barplace` 并加放置断言；`PRE4-LULU-15S-*` 重排为 `hurtby` 三击（0s / 3s / 17s）+ 保命 regeneration + 收尾 `/effect clear @s`；`LULU-SIGN-*` 订正 2 条与 2026-09-28 规格冲突的陈旧断言（坑 25）。
    ③ 判据：两线各 10 条 `MT_CASE_RESULT: … = PASS`（其中 9 条有机器判据；`PRE4-CHIPSHOP-FRAME-*` 只有健康断言 + 截图，vision 在测试台是 DELEGATED，见坑 27）；收尾 `MT_STOP: OK` / `MT_CLEANUP_SAVES: PURGED 1` / `MT_CLEANUP_RESULT: OK`，`Get-Process java` 无客户端残留（§4.8）。
    ④ 未覆盖：观感类（粒子 / 音效 / 美术 / 死亡动画帧）见 §4.8「判据边界」；P1 两线按用户口径不测。
    ⑤ **复核（`dsh_code_review` run `5f2955ef-125c-420b-8f5b-498d03d206df`）**：R1 / R2 / R4 / R5 / R6 / R7 **SUPPORTED**，R3 **部分成立** —— 两处 finding 全部已修：(a) [P2] `PRE4-CHIPSHOP-FRAME-*` 的 `assert/vision` 在测试台是 **DELEGATED**（`mt_case.ps1:1623` / `:2439`），该条 PASS 不含视觉判定 ⇒ 已把两条用例改标「截图取证」、加 `note` 步与 `evidence.machine_judge_none`，并把验收口径从「10/10 PASS」改为「9 机器 + 1 截图」（坑 27）；(b) [P3] `LULU-SIGN-*` 的 title 与两处 note 仍写「友方瞬间治疗 6→10」，与已改断言自相矛盾 ⇒ 已按断言口径改为「友方 6→6 / 只剩敌对缓慢」。修完把 4 条改过的用例双线重跑，全 PASS（见 §4.8 补跑行）。
23. **素材目录口径（2026-10-22 用户裁决，`AGENTS-full.md` 第 50 条）**：`models/` / `images/` / `sound/` **随时更新、有新文件属正常**；`models/astral_anvil.bbmodel` 是用户下一步更新要用的素材，**不得动**。
    ⇒ 铁律：未跟踪 / 变动文件只要落在这三个目录下，**既不报 CONFLICT、也不得删除 / 移动 / 重命名 / 擅自纳入提交**（需要入库先问用户）；本正本此后不再把 `models/astral_anvil.bbmodel` 记成「意外 / 来历待查」的文件（`images/` / `sound/` 本就被 `.gitignore:63` / `:67` 忽略）。提交用显式路径，禁 `git add -A` / `git add .`。
24. **pre.5 指示器批（2026-10-22 22:3x，本批头提交 `git log -1`）**：
    ① 需求（用户 2026-10-22）：删电磁炮「就位」指示器；磨刀石 / 安全气囊触发后加「还要多久才能再触发」的**同名**倒计时指示器（把冷却时间关联到效果计时器）。
    ② 代码（P0 两线）：`effect/ModEffects.java` 删 `RAILGUN_READY`(`railgun_ready`,0x81D4FA)、增 `WHETSTONE_GUARD_CD`(`whetstone_guard_cd`,0xCFD8DC) 与 `AIRBAG_GUARD_CD`(`airbag_guard_cd`,0xFFB74D)（均 `ChipReadyEffect`）；`event/PlayerTickEvents#updateChipBonusIndicators` 由 6 枚降为 5 枚（import 由 `RailgunChipItem` 换 `AirbagChipItem`），tick 主流程新增 `updateChipCooldownIndicators(player)`，并新增 `REMAINING_TOLERANCE = 40` 与 `refreshIndicatorRemaining(Player, Holder<MobEffect>, boolean equipped, long endTick)`（`remaining = endTick - player.level().getGameTime()`；`!equipped || remaining <= 0` ⇒ 有则 `ModEffectRemoval.remove`；否则偏差 > 40 tick 才 `addEffect(new MobEffectInstance(effect, duration, 0, false, true, true))`）。**不能**放进 `!diceGated` 分支 —— 否则卸下骰子后没人负责移除图标。
    ③ 资源：两线新增 `textures/mob_effect/whetstone_guard_cd.png`(410 B) / `airbag_guard_cd.png`(483 B)、删除 `railgun_ready.png`；两线 × zh_cn / en_us / ja_jp 各 1 行增删（`effect.astral_dice.whetstone_guard_cd` = 磨刀石 / Whetstone / 砥石、`airbag_guard_cd` = 安全气囊 / Airbag / エアバッグ）；`tools/chip_effect_icon_map.json` 删 railgun 条目、加两条（现 **12** 条，仍是图标守门唯一事实源）。
    ④ 版本：四线 `mod_version` → `1.5.0-pre.5+<后缀>`（`neoforge-1.21.1/gradle.properties:37` / `forge-1.20.1:41` / `neoforge-26.1.2:54` / `fabric-1.20.1:29`）；`starengine_version=1.1.0-pre.2` 未动；`README.md` / `README_ZH.md` 各 5 处版本字样同步；两份 CHANGELOG 的 `#### 输入与界面` / `#### Input & UI` 各 +2 条。
    ⑤ 判据（全绿，静态）：`:neoforge-1.21.1:classes :forge-1.20.1:classes` BUILD SUCCESSFUL；`python tools/verify_effect_icons.py` 四线 66/66/53/53 PASS；`python tools/gen_chip_effect_icons.py --check` effects=24 misses=0；`pwsh -NoProfile -File tools/check_lang_sync.ps1` P0 三语各 **1046 keys** exit 0；`gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar` + `:pushToRootBuild` BUILD SUCCESSFUL；jar 内探针 0 残留、新图标在位、`railgun_ready.png` 0（§2）；四处投放 sha256 全 MATCH（§2 表）。
    ⑥ **无实机读数**（见 §4.9）：倒计时数字的实际刷新观感、图标观感待用户下达实机。
    ⑦ **P1 欠账**：`neoforge-26.1.2` / `fabric-1.20.1` 本批只 bump 了版本号，效果 / 图标 / lang **未移植**。

25. **激光「卡一下」的归因结论（2026-10-22，真整合包内取证）：不是光束渲染**。
    ① 证据见 §4.10（有光束帧 avg 6.05 ms vs 无光束 6.24 ms；绘制本体 4–66 µs；\>20 ms 的帧全在光束窗口之外）。
    ② 顺带查清：电磁炮（`item/chip/RailgunChipItem`）**不走** `combat/DamageCardFxScheduler` 的光束路径，它走 `event/RailgunStrikeScheduler`（延迟 1 秒的雷击）；光束渲染只有 `client/BeamFxRenderer`，覆盖 `Kind.LASER`（怪物激光卡）/ `Kind.ORBITAL`（轨道打击）等。
    ③ 前一轮 dev 复现同样失败：Iris + Sodium + Complementary 下首帧 60 µs、之后 5–6 µs；再加载 Voxy + particular 后首帧 58 µs、光束窗口帧间隔 2.5–15.8 ms。
    ④ 因此本批**产品源码零改动**；若用户仍能感知卡顿，需要真实场景（位置 / 朝向 / 光影 / 是否多人 / F3 读数）再定位。下一步候选嫌疑：cast 瞬间的效果音与粒子首播、或同一时刻其它 mod 的行为（与本模组无关）。

26. **玩家侧状态效果保护（2026-10-23 用户裁决，本批）**：玩家身上的效果**除非被声明可清除（牛奶 / 蜂蜜瓶）否则不受任何外部影响**；mob 侧**保持原版状态效果**（不搬计时表）。
    ① 审计正本 `docs/features/effect-client-server-split-audit.md`（只读审计 5 维 D1–D5，含 66 个效果计时口径矩阵 / 102 处 `new MobEffectInstance` 分类 / R1–R9 风险分级）。
    ② 裁决口径：R1（mob 窗口效果不设防）**不修** —— 女巫 / 诡厄巫法的术士会持续喝药或喝奶清 mob 状态，改造成保活计时会反复重施、叠加 tick 负载；R2（7 处玩家侧原版效果裸施）**已修**。
    ③ 新增两线 `event/PlayerEffectProtection.java`：`apply(LivingEntity, MobEffectInstance)` 统一入口（= `EffectTimerGuard.apply` + 玩家快照登记，跳过 ∞ / 近 ∞ / `DECLARED_CLEARABLE`）、`onExternalRemoval`（LOWEST 通道，延后到下一 tick 补回，避免 `removeAllEffects` 遍历期 CME）、`tick(Player)`（按 `endTick` 余量重施并重新登记，清 `endTick + 40 < now` 陈旧记录）、`removeNow(LivingEntity, effect)`（**本模组主动移除**的唯一合法入口）、`clear(Player)`（死亡 / 登出）。白名单当前 = `astral_dice:marked`（沿用 2026-09-30 裁决：标记可被牛奶清掉）。
    ④ 接线：`ModEffectEvents` HIGH（死亡 clear + 声明可清除放行）+ 新增 LOWEST 补回 + `PlayerLoggedOutEvent` 清理；`PlayerTickEvents` 先 `PlayerEffectProtection.tick` 后 `EffectTimerGuard.tick`；两线各 26 文件机械替换 `EffectTimerGuard.apply(` → `PlayerEffectProtection.apply(`（25 非 event 包文件补 import、18 删无用 import）；7 处原版裸施 + `item/card/BaseEffectCardItem` 默认入口一并收口。
    ⑤ 两个真 bug（已修，别重踩）：(a) 替换脚本用 `endsWith("event/PlayerEffectProtection.java")` 跳自身，Windows 反斜杠导致新类被自己改写 ⇒ `apply()` 自递归（**能编译**，运行栈溢出）；(b) 补回通道会把本模组主动移除当成外部清除而**复活效果**（`MarkManager` GLOWING / `RenShieldManager` 黄心 / `NancyLuSignItem` 隐身 ×3 / `FightPoisonWithPoisonCardItem` 共 6 处）⇒ 全部改 `removeNow(...)`。**新纪律：主动移除玩家效果必须走 `removeNow`。**（复核后又补第 7 处，见 ⑧）
    ⑥ 判据（静态全绿）：`:neoforge-1.21.1:classes :forge-1.20.1:classes` BUILD SUCCESSFUL；`PlayerEffectProtection.apply(` **52**（51 调用点 + 类内 restore 1 处；定义行不含该串）、`EffectTimerGuard.apply(` **1**（仅新类内部）、`removeNow(` **8**（7 调用 + 定义）、`new MobEffectInstance(` neo **103** / forge **104**。
    ⑦ **无实机读数**：补回时序、牛奶可清除（`marked`）、mob 侧不受影响三项需用户下达实机；P1 两线未移植。
    ⑧ **独立复核（2026-10-23，`dsh_code_review` run `c20953f4-1d9a-43b9-b323-500598e5f118`）**：R1（两线语义等价）/ R3（无自递归）/ R4（HIGH+LOWEST 配对与 tick 顺序）/ R5（无遗漏原版效果）SUPPORTED；R2 部分成立（前提被证伪）；R6 数字全对、仅 52 的分解写错（已改为 51 调用 + restore）。三条 finding 已全部修复：
        (a) **P2 真缺陷**：`event/EnderDiceHandler.java:194`（forge `:193`）保命清效果走裸 `ModEffectRemoval.remove` —— 内部标志让 HIGH 放行、效果真被移除，但**保护快照没丢**；陈旧 `endTick` 会让随后「第三方短时中毒（不经 apply 覆盖快照）→ 牛奶」被 LOWEST 按旧剩余时长补回 ⇒ 已改 `PlayerEffectProtection.removeNow` 并删无用 import。
        (b) **P3**：死亡 / 重生不清快照（原版死亡**不派发** `MobEffectEvent.Remove`，`PlayerLifecycleHandler` 只清本模组 11 个效果）⇒ `ModEffectEvents` 新增 `@SubscribeEvent onPlayerClone(PlayerEvent.Clone)` → `clear(getEntity())`（两线各 +10 行，文件 119 行）。
        (c) **P3 文档**：52 的分解与 `removeNow` 计数已按实测订正（见 ⑥），审计文档 §8 建议 6 / §10.3 / §10.4 / §10.5 同步。
27. ~~**源码领先于产物（2026-10-23 效果保护批，提交 `a344fc46`）**~~ ⇒ **12:05 已闭环**：`gradlew.bat :neoforge-1.21.1:jar :forge-1.20.1:jar`（BUILD SUCCESSFUL in 5s）+ 六个 push 任务（`:*:pushToDevRun` / `:pushToGame` / `:pushToRootBuild`，BUILD SUCCESSFUL）完成重建与重投，§2 三行 sha256 与投放表已重录、4 处 `Get-FileHash` 全部 MATCH。
    产物证据：三份 jar 内 `PlayerEffectProtection.class` + `PlayerEffectProtection$Observed.class` 各 1（`META-INF/jarjar/` 仍 0）；`javap -p -c` 命中 `ModEffectEvents.onPlayerClone` / `onExternalRemovalRestore`（两线）、`EnderDiceHandler` → `PlayerEffectProtection.removeNow`（neo `#352` / forge `#469`）、PEP 六个公开成员。历史：重建前 `build/libs` 是 2026-10-22 22:32 的 pre.5 件（neo 2,559,837 B `f25546b6…` / forge 2,605,382 B `e30980b4…`），不含本批。

28. **Jade 生物护甲改为「防御力」（2026-10-23 用户裁决 A，本批）**：起因 = 审计（用户 m04669「验证友好/被动/中立/敌对生物的护甲值计算是否准确，以及 Jade 上的护甲值呈现是否正确」）查出 Jade 两侧不同源。
    ① 审计结论（只读取证，源码 / 字节码 / MC 反编译三证）：**计算链准确、无双计** —— `DiceCombatEvents.java:759` 取 `target.getArmorValue() + modifierDefense * 2.0`，玩家走 `CombatFormula.playerDefense`（`2 + 护甲×0.30 + 0.85×韧性`）、生物走 `mobDefenseInt(classify, 护甲, 韧性)`（`floor(基数 0 + 护甲×0.40 + 1.0×韧性)`）；原版 `LivingEntity#actuallyHurt` 的护甲减免在 `CommonHooks.onLivingDamagePre` 之后才发生，被本模组 `setNewDamage` 覆盖后丢弃 ⇒ 生物护甲只计一次。四类生物**防御力基数全为 0** ⇒ 分类只影响攻击力（敌对 4 / 中立 3 / 被动 0 / 友好 0）。
    ② 缺口：Jade 15.10.6 `EntityHealthAndArmorProvider.appendTooltip` 里 `getArmorValue()` 出现 **2 次**（偏移 109-114 门控 `ifle`、121-127 作 `ArmorElement(float)` 入参），旧 mixin 只把**玩家**换成 `defensePowerOf`，生物回落到原版护甲 ⇒ 两个单位。对照读数：全套铁甲僵尸 armor **15** → 实际防御 **6** 而 Jade 画 **8** 图标；钻石甲 armor 20 + 韧性 8 → 实际防御 **16** 而 Jade 封顶 **10** 图标；铁头盔僵尸 armor 2 → 实际防御 floor(0.8)=**0** 而 Jade 有 **1** 图标；下界合金 armor 20/韧性 12 → 实际 20 与 Jade 恰好一致。
    ③ 代码（P0 两线各 1 文件）：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/mixin/jade/ArmorValueProviderMixin.java`（3,469 B / 61 行 → **4,788 B / 77 行**）与 `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/jade/ArmorValueProviderMixin.java`（7,400 B / 106 行 → **8,532 B / 119 行**）。处理器非玩家分支改为：
    ```java
    return CombatFormula.mobDefenseInt(
            TargetCategory.classify(entity),
            entity.getArmorValue(),
            entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
    ```
    新增 import `CombatFormula` / `TargetCategory` / `Attributes`；javadoc 小节「只对玩家生效」整段改为「玩家与生物：同一个单位（2026-10-23 用户裁决 A）」。**照旧不变的硬约束**：`@Redirect` 的 `method` 用完整描述符、`@At` 指原版 `LivingEntity#getArmorValue()I`；**forge 处理器必须非 static**（Mixin 0.8.5 严格匹配实例方法，写 static 会 `InvalidInjectionException` 且因 `required:false` 静默失效）；`Attributes.ARMOR_TOUGHNESS` 在 neo 是 `Holder`、forge 是 `Attribute`。
    ④ 行为变化：门控被重定向后判的是**防御力 > 0** ⇒ 护甲 <2.5 的生物（铁头盔僵尸之类）**整行不显示护甲**；玩家侧恒 ≥2 恒显示。`MC_ENTITY_ARMOR_MAX_FOR_RENDER` 默认 **20**（`VanillaPlugin` 字节码 `bipush 20`）⇒ ≤20 画图标（每图标 2 点）、>20 退化为数字，与 HUD 的 `POINTS_PER_ICON=2` 同口径。
    ⑤ 判据（静态全绿）：`gradlew.bat :neoforge-1.21.1:classes :forge-1.20.1:classes` BUILD SUCCESSFUL；两个产物 jar 内 `ArmorValueProviderMixin` 的 `javap -p -c` 命中 `TargetCategory.classify`@18 + `CombatFormula.mobDefenseInt`@33；`META-INF/jarjar/` 计数 **0**；产物 12:35 重建 + 6 个推送任务 + 4 处投放与根 `build/libs` 逐处 sha256 MATCH（§2）。
    ⑥ **未做的一半（有意）**：原方案 A 还含「tooltip 追加一行『防御力 X』」。统一单位后图标本身即防御力、玩家侧从不显示数字，再叠一行会造成两侧口径不对称；且需两线各写一条 `ITooltip` 注入，无实机放行无法验证（有启动崩溃风险）⇒ **暂不做**，待用户裁决是否另开一批。
    ⑦ **无实机读数**：Jade 面板实际图标数、门控（防御力 0 不显示）表现需用户下达实机；P1 两线按规则不测。
    ⑧ **P1 欠账**：`fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/mixin/jade/ArmorValueProviderMixin.java`（相关行 15/16/45/54/63）与 `neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/mixin/jade/ArmorValueProviderMixin.java`（15/16/24/37/46）**仍是旧行为 + 旧注释**（生物回落原版护甲值），随 P1 批次移植。
    ⑨ 顺带登记（**本轮未修，待裁决**）：(a) `DiceCombatModifiers.java` 的 `setDefenseArmorBonus(LivingEntity, String, int)` 重载（neo `:254` / forge `:256`）**零调用者**（死重载，注释所称「雷暴将至给怪加防御」实际没走它）；(b)「雷暴将至」走原版 `Attributes.ARMOR` +4.0（neo `event/EventEffects.java:121-122` / forge `:127-128`，到期归零 `:126-127` / `:132-133`）⇒ 等价 +2 防御力、生物防御实际只 +1（`floor(4×0.4)`）；(c) starengine 库侧 `TargetCategory.java:47-57` 的枚举 javadoc 仍写「HOSTILE 初始防御 2、攻击 5 / NEUTRAL 防御 0、攻击 4」，与 `TargetBattleStats`（0/4、0/3）及在用 jar 字节码（四类基数均 0）冲突 = **陈旧注释**，须在 starengine 仓改（不在本仓）。
29. **「雷暴将至」修复 + 立即刷怪波（2026-10-23 用户裁决，本批）**：起因 = 用户先要「雷暴降至事件的代码验证，判定其功能是否正常或者有缺陷」（只读审计），随后裁决「执行修复」，并追加功能：「玩家附近立即刷新 5-7 只怪，只能是僵尸、骷髅、蜘蛛（僵尸和骷髅必须戴头盔避免白天被烧死），小概率刷新一个女巫。如果玩家半径 32 格内有其他玩家，则该区域内只刷新一波」。
    ① 审计查出的 4 处缺陷（**全部已修**）：(a) P1 **×2 复制域过宽** —— 旧钩子只判 `Mob` + `Enemy`，不看 `getSpawnType()` ⇒ `/summon`（COMMAND）、刷怪笼（SPAWNER）、僵尸增援（REINFORCEMENT）、转化（CONVERSION）、袭击（EVENT）、结构（STRUCTURE）**夜间全会被 ×2**；(b) P1 **复制体未 `finalizeSpawn`** ⇒ 丢 `setCanPickUpLoot(0.55f)` / 幼年 / 鸡骑士 / 难度配装；(c) P2 **无视取消** —— 不查 `isCanceled()` / `isSpawnCancelled()`（cancel 只跳过 `finalizeSpawn`，实体照样生成）；(d) P2 **落点不校验** —— 固定 `+1/+1` 直接 `addFreshEntity`。
    ② 修复（`onFinalizeSpawn`）：`getSpawnType() != MobSpawnType.NATURAL` 早退 → `isCanceled() || isSpawnCancelled()` 早退 → 复制体改「`create` → 7 个候选偏移逐一 `noCollision` / `checkSpawnObstruction` → **直接** `finalizeSpawn(...NATURAL, null)` → `addFreshEntity`」，全失败 `discard()`；日志 `AP_EVENT_EFFECT: storm_night_spawn_x2:type={}` 未变。
    ③ 新功能（两线 `event/EventEffects.java`）：`spawnStormWave(level, target)` 在施放瞬间调用，**位置在取 `AABB` / 加攻防之前**（新波怪同批吃到 +2 攻 / +4 护甲并进到期清零列表）；常量 `WAVE_MIN=5` / `WAVE_MAX=7` / `WAVE_RING_MIN=8.0D` / `WAVE_RING_MAX=16.0D` / `WAVE_CLUSTER_RADIUS=32.0D` / `WAVE_WITCH_CHANCE=0.15F` / `WAVE_PLACE_TRIES=12`；种类 ZOMBIE / SKELETON / SPIDER 等概率、女巫**额外** 15%（不计入 5–7）；Y 由 `findSpawnY` 在 `player.y ± 4` 纵向扫描（`isSpawnFloor` = 脚下非空、上方空），扫不到再退 `Heightmap.MOTION_BLOCKING_NO_LEAVES`；头盔 = `Items.IRON_HELMET` + **不可破坏**（neo `DataComponents.UNBREAKABLE` / forge NBT `Unbreakable=true`）+ `setDropChance(HEAD, 0.0F)`（仅 Zombie / AbstractSkeleton 且 HEAD 槽为空）；机器行 `AP_EVENT_EFFECT: storm_wave:player={}:want={}:placed={}:witch={}`。
    ④ 「32 格一波」= 同 tick 静态 `WAVE_ORIGINS`（`List<WaveOrigin>` = **维度 + 坐标**；`waveTick != level.getGameTime()` 时清空；维度不同的原点不参与聚类），新原点距任一已刷原点 ≤32 格即打 `AP_EVENT_EFFECT: storm_wave_skip:player={}:reason=nearby_wave` 跳过；**只有 `placed + witch > 0` 才登记原点**（否则同区第二个玩家可补刷）。
    ⑤ **平台差异（坑 33）**：`Mob#finalizeSpawn` 1.21.1 = **4 参**、1.20.1 = **5 参**（末位 `@Nullable CompoundTag`）；**不得**走 `EventHooks.finalizeMobSpawn`（会再次派发 `FinalizeSpawn`，×2 钩子连锁翻倍）；forge reobf 里是 SRG `Mob.m_6518_`（5 参含 `CompoundTag`）。
    ⑥ 判据（静态全绿）：两线 `:classes` BUILD SUCCESSFUL；`javap -p` 两线均见 `spawnStormWave` / `createWaveMob` / `placeStormMob` / `findSpawnY` / `isSpawnFloor` / `equipStormHelmet` / `placeStormCopy` + 7 个 `WAVE_*`；字节码命中 `getSpawnType` / `isCanceled` / `isSpawnCancelled` 与 4 参（neo）/ 5 参（forge reobf）`finalizeSpawn`；jar 内 `META-INF/jarjar/` = 0；13:40 重建 + 6 个推送任务 + 4 处投放与根 `build/libs` 逐处 sha256 MATCH（§2）。
    ⑦ **已知限制**：头盔已标不可破坏（2026-10-23 复核 P3 收口）⇒ 不再掉耐久、也不会在白天被点燃，原「约 6.9 分钟碎」的限制作废；波怪在**任意维度 / 任意时刻**都会刷（用户未限时段）；`+2 攻 / +4 护甲` 仍走原版 `Attributes` 瞬态修饰符，`setDefenseArmorBonus(LivingEntity,…)` 死重载仍未启用（规格第 34 条 ④ 分歧未裁决）。
    ⑧ **无实机读数**：刷怪数量 / 头盔防烧 / 32 格一波三项需用户下达实机；P1 两线（26.1.2 / fabric）未移植。
    ⑨ **独立复核（2026-10-23，`dsh_code_review` run `e7302744-2c90-4f45-8ec2-b54ad3c40cec`）**：R1–R5 / R7 SUPPORTED、R6 部分成立，无 P0/P1；三条 finding 全部已修 —— (a) **P2** `docs/handover-new-session-context.md` 产物块只刷了两行（dev named jar 与标题停在旧批）⇒ 已按 §2 刷新；(b) **P3** `WAVE_ORIGINS` 只存裸 `Vec3`，同 tick 跨维度且坐标巧合接近时第二波被静默跳过 ⇒ 改 `record WaveOrigin(ResourceKey<Level> dim, Vec3 pos)` + 维度相等才聚类；(c) **P3** 铁头盔 165 耐久在直射日光下约 6.9 分钟碎、随后点燃（裁决「避免白天被烧死」只部分满足）⇒ 改不可破坏（1.21.1 `DataComponents.UNBREAKABLE` + `new Unbreakable(false)`；1.20.1 `getOrCreateTag().putBoolean("Unbreakable", true)`，两线 `ItemStack#isDamageableItem()` 遇它均为假）。复核同时硬证：两线 `coremods/finalize_spawn_targets.json` 只列 22 个原版类（无模组类）⇒ 直调 `finalizeSpawn` 不会重派事件。

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

23. **Rhino 下调不到 `p.hurt(...)`，且 1.20.1 没有 `/damage` 命令（2026-10-22 连踩两个）**：
    ① `ServerPlayer.hurt(DamageSource,float)` 在 KubeJS/Rhino 里报 `TypeError: Cannot call property hurt in object ServerPlayer[…] It is not a function, it is "boolean"`（`hurt` 声明于 `LivingEntity`，成员派发落到了别的同名 boolean 上）；
    ② `/damage` 是 **1.20.2** 才进原版的命令，1.20.1 侧不存在 ⇒ 任何「两线共用一条用例、用 `/damage` 打玩家」的写法在 forge 线必然红。
    ⇒ 统一走探针 `/astralfixbatch hurtby <tag>`：`p.damage(amount, src)`（KubeJS 包装器，**参数顺序 = 伤害在前**）→ 兜底 `p.attack(src, amount)` → `mob.doHurtTarget(p)` → `mob.attack(p)`，判据是**血量真的掉了**而不是「没抛异常」。实测 1.21.1 落在 `player_damage`、1.20.1 落在 `player_attack_ds`。
24. **裸 `/summon astral_dice:barricade` 必定自毁（2026-10-22 踩中，白跑一轮）**：`entity/BarricadeEntity#tick` 首段「没有碰撞方块的路障无法存在」⇒ `if (collisionPos == null) { discard(); return; }`；用裸 summon 摆靶再测 `killbarricade`，读数恒为 `killed=0`（看着像产品坏了）。正解 = 复刻真实放置路径（`ModBlocks.BARRICADE_COLLISION` 落方块 + `create` + `setCollisionPos`），即探针的 `barplace`。
25. **陈旧用例会把「规格已变」误报成回归（2026-10-22）**：`LULU-SIGN-*` 还要求「范围内友方 pig 被瞬间治疗 6→10」，而这段范围治疗已在 **2026-09-28 用户裁决**里整段删除（`LuluSignItem` 类 javadoc 与 `apply()` 逐字可查，范围能力只剩「敌对生物缓慢 1:00」）；该用例另自 **2026-10-01** 骰子闸门（`3c8f0d36`）起就开不出会话（`luluprep` 从不装骰子）。两条都是**测试资产陈旧**，不是产品缺陷。⇒ 断言里的期望值必须能在源码 javadoc/常量里找到**同一时点**的依据；找不到就先怀疑用例。

26. **用例之间会互相污染（2026-10-22 踩中）**：新用例为防止 17 秒窗口里被怪打死给了 `/effect give @s regeneration 300 3`，结果 5 分钟内下一条用例的「加血」断言被顶到满血（`hp=12->16` 实测成 `20->20`）。⇒ 任何给玩家上长效增益/减益的用例，**收尾必须 `/effect clear @s`**；跨用例的世界状态（时间 / 天气 / 血量 / 难度）一律在用例开头显式重置。

27. **`assert/vision` 在测试台里不判红绿（2026-10-22 被复核抓出）**：`scripts/test/mt_case.ps1:1623` 对 vision 一律 `return (New-MtPair 'DELEGATED' …)`、`:2439` 映射成 `→VIS` 步骤标记 ⇒ **含 vision 的用例 PASS 只等于「没崩 + 其余 log 断言过」**，视觉项永远不会让它红，也永远不会让它绿。⇒ ① 视觉项**不能算机器判据**，验收表里要单列「截图取证 / 人工目视」；② 想让它可机判，就得在客户端渲染路径上打机器行（`LOGGER.info("AP_…")`）再用 log 断言，否则别把它写进 PASS 数。本批 `PRE4-CHIPSHOP-FRAME-*` 已按此口径改写标题/证据并单列。

28. **根 `build/libs` 是「统一交付目录」，只跑 `:线:jar` 不会刷新它（2026-10-22 用户抓出）**：它由各子项目的 `pushToRootBuild` 填充，而该任务是 `tasks.named('build')` 的 `finalizedBy`（`neoforge-1.21.1/build.gradle:486`、`forge-1.20.1/build.gradle:622`）⇒ **只跑 `:neoforge-1.21.1:jar` / `:forge-1.20.1:jar` 时根目录一直停在上一次交付的版本**（本批实测：子项目 `build/libs` 已经是 `1.5.0-pre.4`，根 `build/libs` 还挂着 15:47 那批 `1.5.0-pre.3`，用户看到的就是这一份）。⇒ 交付/给别人拿 jar 之前，要么跑**全量** `gradlew.bat build`，要么显式跑 `gradlew.bat :neoforge-1.21.1:pushToRootBuild :forge-1.20.1:pushToRootBuild`（这两个任务**先删后拷、按各自完整后缀过滤**，不会动别线的 jar）。同一坑的第二个面：根 `build/libs` 里的 `starengine-*-1.1.0-pre.1.jar` 也是旧交付残留，而现行硬前置是 `[1.1.0-pre.2,2.0.0)` ⇒ 已换成 `1.1.0-pre.2`（来源 `F:\MCProject\starengine\build\libs`）。判据：根 `build/libs` 里 P0 两个 jar 的 sha256 必须与 `neoforge-1.21.1\build\libs` / `forge-1.20.1\build\libs` 同源一致（本批 `742132DD…` / `4B984008…`）。
29. **还原被临时插桩的文件不能用 `git show … | Set-Content`（2026-10-22 踩中）**：PowerShell 管道会把 `git show` 的多行输出塌成 1 行（实测 `client/BeamFxRenderer.java` 由 249 行变 1 行、11,718 B 且无换行）。正解 = 用 node 取字节：`child_process.execFileSync('git', ['show', 'HEAD:<path>'], { cwd: repo })` → `fs.writeFileSync(path, buf)`，写完核行数与 `bareLF`（应为 0）。

30. **整合包里不能用 kubejs tick 脚本驱动命令序列（2026-10-22 两次踩中）**：① 该包**没有 AutoJS**，`eval_js` 通道不可用；② 用 `ServerEvents.tick` + `runCommandSilent` 的脚本会出现「只打印 armed / finished、中间步骤一条都不执行、且无任何报错」（第一版 v4 能跑，加了 `for` + `const step` 的第二版就静默失效，原因未查明，Rhino 侧也不报 redeclaration）。⇒ 整合包内驱动一律**聊天框手打**：`press_keys t` → `type_text` → `press_keys enter`；玩家相关命令必须带 `execute as @a at @s run` 前缀（`runCommandSilent` 的源是控制台，`@s` 无效）。

31. **PCL2 启动器 + 双屏桌面自动化的读数坑（2026-10-22）**：① 冷启动到标题屏约 **3 分钟**（100 秒轮询会误判失败）；② 标题屏「退出游戏」单击**不一定生效**，常常要点第二次；③ 进程核对别只信 `Get-Process -Id`，要用 `Win32_Process` 的 `CommandLine` 匹配 `net.minecraft.client.main.Main`；④ 屏幕是 5120×1440 拼接、**左屏虚拟 x 为负**（-2560..0），MCP 截图坐标 = 虚拟坐标 + 2560，且 `take_screenshot(title_pattern)` 偶尔抓到前台别的窗口 ⇒ 关键取图用全屏截图再按矩形裁。

32. **判定「某特效是不是卡顿源」必须做全帧基线对比（2026-10-22）**：只在「特效可见期间」打印帧间隔，会把进世界后的区块 churn 全算到特效头上（第一轮因此误得「光束窗口只有 66 FPS」）。⇒ 探针要**全帧无条件**打印，再按「有/无特效」分组算 avg / p90 / p99 / max；判据是**两组分布一致**，而不是「窗口内偶发一帧 >40 ms」。
33. **`Mob#finalizeSpawn` 两线参数不一致，且 reobf 产物里改名叫 SRG（2026-10-23 踩过）**：1.21.1 = **4 参** `(ServerLevelAccessor, DifficultyInstance, MobSpawnType, SpawnGroupData)`、1.20.1 = **5 参**（末位 `@Nullable CompoundTag`）⇒ 同一段手工刷怪代码必须分叉；另：**不要**用 `EventHooks.finalizeMobSpawn` 手工补初始化 —— 它自己就是 `FinalizeSpawn` 的派发点，夜间 ×2 钩子会连锁翻倍，直接调 `Mob#finalizeSpawn`（方法体是纯原版初始化、内部不派发事件）即可；核 jar 字节码时 forge 侧是 SRG `Mob.m_6518_`，**按 `finalizeSpawn` 名字 grep 会假阴性**（要用 `SpawnGroupData;` 描述符锚）。

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
