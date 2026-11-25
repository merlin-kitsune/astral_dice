# Astral Dice multi-dev-next 交接文档（2026-11-14，26.4-SNAPSHOT.32）

> 交接时间：2026-11-14 01:2x（Asia/Hong_Kong）
> 工作树：D:/MCProject/astral_dice_multiloader-next
> 本文件取代 docs/handover-2026-11-12-new-session-prompt.md 的 .13 快照；旧文只作历史参考。
> 当前任务来源：用户 m09378「总结工作，纪录所有欠账项，并写入交接文档和创建交接 prompt」。

## 0. 先读这一条：工作树没有提交

本次会话从 43f821ad 接手，到本文写入时没有产生任何 commit / push / tag / CurseForge 上传。所有产品代码、资源、报告、脚本改动仍在工作树里。

实读（2026-11-14）：

~~~text
branch   multi-dev-next
HEAD     43f821adf0779320e2efe752aefa76cfd5711eb7
origin   43f821adf0779320e2efe752aefa76cfd5711eb7
status   165 个 tracked 修改；63 个未跟踪条目
diff     +2024 / -1197（tracked）
~~~

未跟踪条目里有 39 个产品文件（Java / json / png），包括：

- 三线 GrapplingHookEntity.java、GrapplingHookRenderer.java、CounterStateManager.java、Z3000SignItem.java
- 1.21.1 GrapplingHookCommand.java
- 三线 Z3000 / Vacuum 模型、贴图、生成配方 / 进度
- 6 份新验收报告与 1 份 .13 旧交接文档

不要执行 git clean -fd、git reset --hard 或任何批量删除。这些未跟踪 Java 不是临时文件，是当前可构建状态的一部分。

## 1. 实读现状

### 1.1 版本与产物

五线 mod_version 均为 26.4-SNAPSHOT.32+<loader>：

| 线 | 级别 | 当前版本 | .32 产物 |
|---|---|---|---|
| neoforge-1.21.1 | P0 | 26.4-SNAPSHOT.32+neoforge_1.21.1 | 有 jar，SHA-256 c4ff8df0e6348d039e86d83670f3548f59d6a18ea7c11b467fcb111d0c3130ca |
| forge-1.20.1 | P0 | 26.4-SNAPSHOT.32+forge_1.20.1 | 有 jar，SHA-256 9aa7b89dc525ce2b33c58ce411344afcfec469ba04f3271b46ec669e34818cfc |
| neoforge-26.1.2 | P0 | 26.4-SNAPSHOT.32+neoforge_26.1.2 | 有 jar，SHA-256 b1646981ca216380104ef0bbc048dcba8996708f3b3fbe49ecc1439380fee28a |
| fabric-1.20.1 | P1 | 26.4-SNAPSHOT.32+fabric_1.20.1 | 没有 .32 产物；build/libs 最后一个是 .30 |
| fabric-1.21.1 | P1 | 26.4-SNAPSHOT.32+fabric_1.21.1 | 没有 .32 产物；build/libs 最后一个是 .30 |

注意事项：

- 根 build/libs 当前混有 .24 / .30 / .32 三代产物；P0 的 .32 也没有生成 .sha256。不要把当前 build/libs 当成“五线同版发布集”。
- 五线最后一次完整构建是 .30；.31 / .32 只构建了 P0 三线。Fabric 两线只是版本号到了 .32，没有重新编译。
- 生产发布链路（tag / Release / CurseForge）没有动过；当前不能称为可发布版本。

### 1.2 运行态

2026-11-14 01:2x 实读：

- 25565 / 25575 / 25585 均无 LISTENING。
- D:/MCProject/.agent-locks/ 不存在。
- 只剩 3 个 Gradle daemon java：PID 22368 / 22392 / 31780。
- 没有游戏客户端、AutoJS、RCON 或 Fabric 服务端在跑。

### 1.3 证据与磁盘

- 当前可复核的实机日志：
  - run/neoforge-1.21.1/logs/latest.log（约 5.3 MB，最后停在 .31 Z3000）
  - run/forge-1.20.1/logs/latest.log（约 1.6 MB，.31 Z3000）
  - run/neoforge-26.1.2/logs/latest.log（约 0.6 MB，.32 Z3000）
- run/*/screenshots 合计约 10.9 GB（1.21.1 约 9.6 GB、forge 约 639 MB、26.1.2 约 1.3 GB）。这些都在 .gitignore 的 run/ 下，不随仓库交付；清理前先确认归属。
- 报告里的 latest.log:NNNN 行号是当时快照。后续实机已把 1.21.1 / 1.20.1 / 26.1.2 的 latest.log 覆盖成 Z3000 运行日志，旧 A/B/反击报告的行号不能按当前文件复核。报告本身是唯一留档。
- 新实机启动前如果要保留证据，先把当前 latest.log 复制到 docs/reports/evidence/ 或独立目录；否则下一次启动会重置。

### 1.4 旧后台 job 说明（2026-11-14 04:24 通知）

- 通知：bash-9b9ca06924f84830（label Compile 26.1.2 line）exit 1，job 输出为空。
- 查 Gradle daemon 日志（C:/Users/xmace/.gradle/daemon/9.5.1/daemon-22368.out.log:45403）：00:48:58 `:neoforge-26.1.2:runClientData` 因 `java.lang.IllegalStateException: Missing item model definitions for: [astral_dice:vacuum_piece]` 失败。
- 这不是 .32 编译失败：00:49:45 已在 neoforge-26.1.2/src/main/java/com/merlinkitsune/astral_dice/datagen/ModItemModelProvider.java:43 补 `declareCustomModelItem(ModItems.VACUUM_PIECE.get())`，00:49:56 生成 `src/generated/clientResources/assets/astral_dice/items/vacuum_piece.json`。
- 同一 daemon 日志 01:24 的 `runClient` 失败是客户端断开导致的 Build cancelled，不是代码错误。
- 仍欠：.32 没有重跑 `:neoforge-26.1.2:runClientData` 的完整日志；发布前应补一次 datagen 证据。

## 2. 2026-11-12 → 11-14 完成的工作

### 2.1 版本总表

| 版本 | 主题 | 代码范围 | 验证状态 |
|---|---|---|---|
| .14 | 修复 ChipShopService.grantRefresh 封顶判断 | P0 三线 | 三线编译 / 构建 PASS；未实机 |
| .15 | 随机发牌收口到 RandomCardHandler | 五线 | 五线构建 PASS；未实机 |
| .16 | 蛟龙→金鲤联动、金鲤主动重写、两侦探配方、tooltip / 手册清理、仙酿图标 | 五线 | 1.21.1 实机；其余四线未跑；无独立报告，日志已被覆盖 |
| .17 | 肾上腺素筹码闪避（20% / 40%，仅怪物近战） | 五线 | 五线构建 PASS；未实机 |
| .18 | 反击改 10 秒近战状态、金鲤 4 层修复、安全气囊纳入 | P0 三线 | P0 三线实机；女仆路径仅 1.21.1 PASS；fabric 仍旧语义 |
| .19–.23 | 垃圾桶动态吸尘管道原型（后被废弃） | 1.21.1 产品代码；五线版本同步 | 1.21.1 实机；视觉反复调整；代码已在 .24 删除 |
| .24 | 从 Confluence 移植抓钩并改造 | 1.21.1 产品代码；五线版本同步 | 五线构建 PASS；未实机；后续被 .30 清理 |
| .25–.29 | 抓钩拖怪原型：去物品、抓怪、吸附、吸尘器头 / 白色管道渲染 | 1.21.1 产品代码；其余四线版本同步 | 1.21.1 实机；用户已目视确认；截图与日志见抓钩报告 |
| .30 | 删除 Confluence 派生声音 / 模型 / 许可，重写为自有实现 | 五线构建；产品代码实际只有 1.21.1 | 五线构建 PASS；未实机 |
| .31 | Z3000 垃圾桶立牌落地 | P0 三线 | 三线构建 PASS；1.21.1 / 1.20.1 实机 |
| .32 | 修 26.1.2 渲染器构造期 NPE | P0 三线 | 三线构建 PASS；26.1.2 实机全套 PASS；1.21.1 / 1.20.1 未用 .32 重跑；fabric 未构建 |

### 2.2 关键完成项

#### .14 grantRefresh

- 三线 ChipShopService.grantRefresh 从引用比较改为值比较：
  - 26.1.2 ChipShopService.java:106
  - neo21 ChipShopService.java:104
  - forge ChipShopService.java:72
- 封顶 REFRESH_MAX 时返回 false，不再误报“授予成功”。
- 只有编译 / 构建证据，没有实机刷新到上限的回归。

#### .15 随机发牌

- RandomCardHandler 新增 battleCards()（8 张：攻击中 / 大 / 特大、防御中 / 大 / 特大、名刀、蓄力）、healingCards()、CardCategory.HEALING、giveFixedCardTo。
- Bonnie 被动改用 BATTLE；Pandaman 用 HEALING；Satellite / EventEffects 改固定入口。
- 全仓扫描结论：除专属牌返还外，无其他私有随机卡牌池。
- 未实机验证掉率或具体池分布。

#### .16 联动 / 配方 / 文案

- 蛟龙主动给装备金鲤的玩家发牌后，对方 +1 金鳞；金鲤主动只放饮品并 +1 反击，不再给金鳞。
- 大侦探配方 RRR / GCG / BTB，秘密侦探望远镜改标记涂料；tooltip、手册去染色 / 制表符化。
- 1.21.1 实机过；其余四线没有实机。该版本没有独立报告，且当时的 latest.log 已被覆盖，证据可复核性弱。

#### .17 肾上腺素

- 一般 / 高效在低血加成期间各 +20% 闪避，两枚叠加 40%，每次受击只掷一次；只对 Mob 的近战直接伤害生效。
- 五线构建 PASS；未实机。

#### .18 10 秒反击（P0 三线）

- 新增 CounterStateManager：激活后 200 tick 状态；状态内符合近战条件的命中免费反击；不刷新时长；只有成功激活才扣 1 层并 +1 金鳞。
- 排除弹射物、爆炸、魔法、技能 / 卡牌、荆棘与自定义伤害；玩家与车万女仆为友方，不激活也不被反击。
- 金鲤 4 层不再被 grantShield 压回 1；护盾消失不摘 REN_COUNTER；安全气囊近战致命伤激活并反击，魔法致命伤只无效化。
- 报告：docs/reports/2026-11-12-counter-p0-acceptance.md。
- 1.21.1 全套含女仆 PASS；1.20.1 / 26.1.2 除女仆外 PASS（测试环境无 TLM）。
- fabric 两线仍是旧反击语义，P1 待做。

#### .19–.23 吸尘管道原型（已废弃）

- 采用 Display.ItemDisplay 阵列 + vacuum_piece / vacuum_head 模型，/astralvacuum 触发；曾实机验证生成、牵引、5 点技能伤害、回收。
- 视觉问题反复：头扁、管道像鞭子、头部不可见、UV 越界。最终用户放弃该方案，.24 删除 VacuumPipeManager 与 VacuumCommand。
- 保留 VACUUM_PIECE 隐藏物品和两套模型 / 贴图，后来被抓钩渲染复用。
- 报告：docs/reports/2026-11-13-vacuum-prototype-live.md（其中“实际 28 伤害”是未隔离伤害折叠效果牌的结果，不是管道固有伤害）。

#### .24–.30 抓钩拖怪

- .24 从 Confluence 移植抓钩；.25 改为只抓怪、拖到玩家面前；.26 吸附到 1.5 格。
- .27 把钩头换成吸尘器模型、铁链换成白色管道；.28 同步 owner id；.29 修“渲染器叠加世界坐标”导致的双倍偏移。
- .29 实机：9.5 格 → 1.5 格，AP_GRAPPLE_END reason=arrived age=53，用户已目视确认。截图见 docs/reports/2026-11-13-grappling-hook-live.md。
- .30 按要求删除 Confluence 派生声音 / 模型 / 许可与 THIRD_PARTY_NOTICES / licenses/，重写实体与渲染器；五线构建 PASS，未实机。
- 目前产品 jar 内没有 Confluence 许可文件或抓钩音效；work/confluence 仍是未跟踪的 84 MB 取证克隆，不参与构建。

#### .31–.32 Z3000

- .31：P0 三线落地 Z3000（主动拖拽、5 点技能伤害、拖拽击杀 +1 反击、被动 -33% 冷却、攻击叠层 +12、冷却门控、配方 / 资源 / 三语文案）。
- .32：26.1.2 启动崩溃修复。根因是 GrapplingHookRenderer 在实体渲染器构造阶段创建 ItemStack，此时组件未绑定；改为首次 extractRenderState 惰性创建。
- 报告：docs/reports/2026-11-14-z3000-acceptance.md。

## 3. Z3000 当前口径（以代码为准）

代码：三线 .../item/sign/Z3000SignItem.java，关键常量在 1.21.1 版本 :42-47。

| 项 | 当前实现 |
|---|---|
| 主动 | 16 格内选敌对目标；生成抓钩；拖到玩家面前 1.5 格；造成 5 点技能伤害 |
| 拖拽击杀 | 若这次技能伤害直接击杀，玩家 +1 层“反击” |
| 被动冷却 | 每击杀一个敌对目标，主动剩余冷却 -33%；每 1200 tick 最多一次 |
| 被动攻击 | 每击杀 2 个最大生命 >=20 的敌对目标，攻击 +1，最多 +12 |
| 反击层 | 与金鲤共享 SouriSignManager.COUNTER_MAX=4；护盾清理不会再抹掉 Z3000 层 |
| 配方 | HRH / ISI / TDT（漏斗 / 红石 / 铁锭 / 空白立牌 / 铁粒 / 普通骰子） |
| 注册 | 1.21.1 ModItems.java:1194；1.20.1 :1174；26.1.2 :1053 |
| 持久状态 | ModAttachments.java:2642-2679 三个附件：攻击叠层、>=20 血击杀计数、上次冷却缩减时间 |
| tooltip | ModTooltipHandler：1.21.1 :1765、1.20.1 :1730、26.1.2 :1826 起 |

重要口径冲突：docs/features/sign-roadmap.md §2.2 仍写“>=30 血 / 上限 +10”，这是旧计划表口径；当前代码按用户后续要求是 >=20 血 / 上限 +12。接手时不要按 roadmap 数字“修正”代码；应先向用户确认哪一版是目标。

Fabric 两线没有 Z3000：只有版本号同步，没有代码、资源、语言键或实机。

## 4. 实机验证矩阵

| 线 | 最后实机版本 | Z3000 | 10 秒反击 | .16 联动 | 备注 |
|---|---|---|---|---|---|
| neoforge-1.21.1 | .31 | 主动 / 被动主链路、反击、冷却、上限链路（上限 / 低血数值未留档） | .18 全套含女仆 PASS | 1.21.1 PASS（无报告） | .32 未重跑 |
| forge-1.20.1 | .31 | 全套 PASS（含攻击 12/13、低血不计） | .18 除女仆外 PASS | 未跑 | .32 未重跑 |
| neoforge-26.1.2 | .32 | 全套 PASS | .18 除女仆外 PASS | 未跑 | .31 启动崩溃，.32 修复后 PASS |
| fabric-1.20.1 | .13（B 阶段实体级） | 未实现 | 旧语义 | 未跑 | 最后构建 .30，.32 未构建 |
| fabric-1.21.1 | .13（B 阶段实体级） | 未实现 | 旧语义 | 未跑 | 最后构建 .30，.32 未构建 |

## 5. 欠账清单

### 5.1 发布 / 证据阻塞（P0）

1. 全部改动未提交：165 tracked 修改 + 63 未跟踪条目；39 个未跟踪产品文件。没有 checkpoint，误删即丢失。接手第一件事应请求用户授权做选择性本地提交，不要直接 git add -A（会带入 models/、work/audit、work/souri、立牌计划表.txt 等非本项目文件）。
2. .32 产物不完整：Fabric 两线没有 .32 构建；P0 三线没有 .32 的 .sha256；1.21.1 / 1.20.1 没有用 .32 重跑。当前 build/libs 混有 .24 / .30 / .32，不能作为发布集。
3. 命名门禁 FAIL 19 条（python tools/verify_sign_display_names.py，exit 1）：
   - 三线 item.astral_dice.z3000_sign 的 en / ja 名未登记到 tools/sign_display_names.json（权威表无 z3000）；
   - 五线 tooltip.astral_dice.sign.mamushi_note 的 en / ja 片段校验失败；
   - 三线 lang 多出 z3000 键。
   处理时机：发布前必须绿；修法应先按 astral-dice-sign-i18n-naming 取 wiki 权威名，再更新权威表与五线 lang。
4. Z3000 文档债：没有 docs/features/z3000-sign-spec.md；docs/features/sign-roadmap.md 仍写“未实现”，且 §2.2 的 30 血 / +10 与代码 20 血 / +12 冲突；AGENTS-full.md / AGENTS 的效果 / 立牌清单没有 z3000；tools/sign_display_names.json 没有 z3000。不要把这些文档当成现状。
5. A 阶段非玩家商店仍有未验证分支：测试 handler 的 giveItem 是 default 方法，JavaAdapter 无法覆盖，实际走 spawnAtLocation；handler.giveItem=true 与 delivery_failed + refunded 未验证。玩家方块右键 UI 派发也未走通（用 ChipShopOpener.open 同入口替代）。报告：docs/reports/2026-11-12-26.1.2-acceptance.md。
6. B 阶段 fabric 非玩家 kill / hurt 是 no-op：两线 BaseSignItem 只在 killer / entity 为 Player 时委派；全仓没有立牌覆写 onKillEntity / onHurtEntity。只能证明基类管线，不能声称某立牌的非玩家击杀效果生效。Misaki 的名刀交付也因无 PlayerLikeTargets handler 未验证。报告：docs/reports/2026-11-12-fabric-entity-acceptance.md。
7. 26.1.2 datagen 未复跑：00:48 的 `runClientData` 曾因 `Missing item model definitions for: [astral_dice:vacuum_piece]` 失败；00:49 已补 `ModItemModelProvider.java:43 declareCustomModelItem(VACUUM_PIECE)` 并生成 `items/vacuum_piece.json`，但 .32 没有新的完整 datagen 日志。发布前补 `:neoforge-26.1.2:runClientData` 证据。

### 5.2 功能 / 验证欠账（P1）

1. Fabric 10 秒反击 P1 未做：两线仍是旧反击语义；需要按 .18 的 P0 方案移植，并补安全气囊、金鲤 4 层、护盾消失不摘状态。
2. Fabric Z3000 未落地：没有代码、资源、lang、配方、实机；不能对外声称 Fabric 可用。
3. .16 只有 1.21.1 实机：1.20.1 / 26.1.2 / fabric 两线未跑；且没有独立报告，日志已被覆盖。
4. .14 / .15 / .17 未实机：分别是商店刷新封顶、随机发牌池、肾上腺素闪避；只有构建证据。
5. .18 女仆路径：1.20.1 / 26.1.2 测试环境没有车万女仆，未验证；不要把“玩家友方”当作“女仆友方”。
6. Z3000 1.21.1 数值取证：攻击叠层 12/13 与低血排除只有链路日志，没有像 1.20.1 / 26.1.2 那样的读回；当前只能算“链路通过、数值未完整取证”。
7. .30 未实机：五线构建 PASS，但抓钩清理后的版本没有启动过；当前 .29 的截图不能自动等同于 .30 / .32。
8. 抓钩头视觉：源 vacuum_head 模型 Y 方向只有 1.8 px，当前 HEAD_SCALE_Y=2.6 仍偏扁；用户尚未裁决是否加厚模型。
9. /astralhook 只有 1.21.1：GrapplingHookCommand 只在 1.21.1 存在；forge / 26.1.2 没有同名命令。Z3000 走实体入口不依赖它，但该调试入口是否保留 / 移植 / 删除需要裁决。
10. 抓钩原型残留：VACUUM_PIECE 隐藏物品、vacuum_piece / vacuum_head 模型与贴图仍在；work/vacuum 是临时生成脚本。作为 Z3000 渲染依赖保留，但不是完整立牌资产。

### 5.3 文档 / 工具 / 清理欠账（P2）

1. scripts/verify/verify_content_library.py 与 1.2.0 冻结快照漂移：2026-11-14 实跑 exit 1；闭集 git-only 35 项，四组标签计数不一致。脚本自述“非常规发布门禁”；是否重建快照 / 基线需用户裁决。
2. 8 条“代码与文案不符”：旧交接挂账，未获裁决；未动。
3. Luka（摩托车手）未实现：docs/features/sign-roadmap.md §2.3 仍是需求底稿；没有代码 / 资源 / 规格。
4. Fabric 附属 / 玩家类目标 API 不完整：用户已明确“当前优先添加新功能”，不要先做 API 大改。
5. 文档指针过期：docs/handover-2026-11-12-new-session-prompt.md 仍是 .13；docs/handover-new-session-context.md 仍指向 2026-11-11 主线口径；CHANGELOG_ZH.md / CHANGELOG.md 的 .16 段仍写“未实机”，但 1.21.1 已实机。本文是新的交接正本；旧文只作历史。
6. work/confluence 84 MB：未跟踪的 Confluence 取证克隆，不参与构建、不进入 jar；若要彻底清掉第三方源码，需先确认后再删。
7. run/*/screenshots 约 10.9 GB：全是忽略目录；清理前确认归属，不要提交。
8. 测试世界状态：1.21.1 用 新的世界（FlatLevelSource）；1.20.1 用 testworld（FlatLevelSource）；26.1.2 用 flatworld（从服务端 testworld 复制，FlatLevelSource，服务端显示名仍是 testworld）；1.21.1 的旧 testworld 是 NoiseBasedChunkGenerator，不要拿它当超平坦判据。世界内可能残留测试实体，重启后先清 item_display / armor_stand / 命名目标。
9. 旧报告行号不可复核：A/B/反击报告引用的 latest.log 行号已被后续运行覆盖；报告本身是历史留档，不要把行号当当前证据。

## 6. 硬约束与操作口径

- 不自行开实机：必须用户明确下达；同一时刻只允许一条实机线；发现别的实机在跑立即停手并告知用户。
- 不 commit / 不 push / 不打 tag / 不上传 CurseForge，除非用户明确要求。远程 git 走本地代理 http://127.0.0.1:7897。
- 不碰 models/、images/、sound/ 与他人未跟踪文件；不删任何非自己创建的文件。
- 单写者：一个文件同一时刻只允许一个写者；并行任务切不重叠的写作用域。
- 任何“完成 / PASS / 已修”的结论都要附可复核命令、日志行或文件行号；没有证据就写“未验证”。
- 改产品代码并重新编译后，先把五线 mod_version bump 到下一个 26.4-SNAPSHOT.N，并在双 CHANGELOG 记录，再启动实机；不得在旧版本上跑新回归。
- 没有详细测试与实机取证，不得称为“可发布”。当前 .32 只是开发构建。
- DSH 在本机压缩走 billion-context 代理，不调用原生 compress（详见 docs/agent/AGENTS-dsh.md）。

## 7. 建议接手顺序

### 第 0 步（只读，不启动游戏）

1. 读根 AGENTS.md、docs/agent/AGENTS-dsh.md、本文。
2. 实读 git status --short、git rev-parse HEAD、五线 gradle.properties。
3. 复核 25565 / 25575 / 25585 与 D:/MCProject/.agent-locks/。
4. 确认工作树里 39 个未跟踪产品文件、models/ 与 work/audit / work/souri 的边界。

### 第 1 步（需用户授权）：先做 checkpoint

- 建议按版本 / 主题拆本地提交，而不是一个巨型提交。
- 至少先把当前可构建状态打成 checkpoint，避免未跟踪 Java 丢失。
- 不要 git add -A；逐目录 / 逐文件选择：
  - 产品：五线 src/main、src/generated、gradle.properties、双 CHANGELOG
  - 文档：6 份报告、本文、新 prompt、AGENTS / 测试台文档
  - 排除：models/、work/audit/、work/souri/、立牌计划表.txt、work/confluence/、run/
- 提交信息应写清版本、验证状态和未实机项。

### 第 2 步：文档 / 门禁的非产品收口

1. 用 astral-dice-sign-i18n-naming 补 z3000 的权威 en / ja 名，更新 tools/sign_display_names.json 与三线 lang。
2. 查清 mamushi_note en / ja 片段失败的实际字节 / 措辞。
3. 更新 docs/features/sign-roadmap.md：Z3000 已实现、Luka 未实现、30/10 与 20/12 的口径冲突以用户裁决为准。
4. 更新 AGENTS 的立牌 / 效果清单；补 docs/features/z3000-sign-spec.md。
5. 修正双 CHANGELOG .16 的“未实机”表述，或在本文 / 后续交接中明确“1.21.1 实机、其他未跑”。

### 第 3 步：补齐 .32 产物证据

- 构建 Fabric 两线 .32，生成五线 .sha256。
- 1.21.1 / 1.20.1 用 .32 重跑 Z3000 核心用例（至少 9 格拖拽、17 格拒绝、拖拽击杀 +1 反击、冷却缩减、攻击封顶）。
- 若需要改产品代码，先 bump .33，不要直接改 .32。

### 第 4 步：P1

- Fabric 10 秒反击移植 + 实机。
- Fabric Z3000 落地 + 实机。
- 补 .14 / .15 / .17 的实机（若用户认为必要）。

### 第 5 步：历史 / 功能债

- Luka 立牌。
- Fabric PlayerLike API。
- verify_content_library.py 快照与 8 条文案不符，需用户裁决。

## 8. 复现命令与证据索引

### 8.1 只读核对

~~~bash
git rev-parse --abbrev-ref HEAD
git rev-parse HEAD
git status --short
grep -H '^mod_version' */gradle.properties
netstat -ano | grep -E ':(25565|25575|25585) .*LISTENING'
ls -la D:/MCProject/.agent-locks 2>&1
~~~

### 8.2 构建

~~~bash
# P0 三线
./gradlew.bat :neoforge-1.21.1:build :forge-1.20.1:build :neoforge-26.1.2:build --console=plain

# 五线（当前 Fabric 只到 .30 产物）
./gradlew.bat :neoforge-1.21.1:build :forge-1.20.1:build :neoforge-26.1.2:build :fabric-1.20.1:build :fabric-1.21.1:build --console=plain
~~~

### 8.3 实机启动

~~~bash
bash scripts/test/win/launch.sh --version 1.21.1 --quickplay 新的世界
bash scripts/test/win/launch.sh --version 1.20.1 --quickplay testworld
bash scripts/test/win/launch.sh --version 26.1.2 --quickplay flatworld --timeout 300
# fabric：
bash scripts/test/win/launch.sh --version fabric-1.20.1 --side client --enable-autojs --quickplay testworld
~~~

### 8.4 证据索引

- docs/reports/2026-11-12-26.1.2-acceptance.md：A 阶段 26.1.2 玩家 / 非玩家商店、Bonnie、会员推荐信；限制见 §5。
- docs/reports/2026-11-12-fabric-entity-acceptance.md：fabric 两线 Misaki / Bonnie 实体级；非玩家 kill / hurt 代码层 no-op。
- docs/reports/2026-11-12-counter-p0-acceptance.md：P0 三线 10 秒反击；1.21.1 含女仆。
- docs/reports/2026-11-13-vacuum-prototype-live.md：已废弃的吸尘管道原型。
- docs/reports/2026-11-13-grappling-hook-live.md：抓钩拖怪、截图哈希、.30 清理。
- docs/reports/2026-11-14-z3000-acceptance.md：Z3000 P0 三线验收与全部日志行。
- 当前 latest.log 与截图目录见 §1.3；下一次实机会覆盖它们。

---

交接结论：功能主线已到 .32（P0 三线），但工作树未提交、Fabric 未跟上、证据 / 文档 / 门禁仍有欠账。不要把这个状态当成发布候选；先 checkpoint，再补产物与证据。
