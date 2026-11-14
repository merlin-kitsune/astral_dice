# 欠账登记（2026-11-09）

> 口径：只登记**可复现、会影响行为或可维护性**的欠账；已修项必须带当时证据（提交 + 读数）。
> 本表与本仓 `docs/features/player-like-entities-api.md`、`docs/features/tlmdice-participant-support.md` 互为索引：
> 接口契约在后者，欠账状态在本表。

## 一、本轮已修（含实机/静态证据）

| ID | 欠账 | 修法 | 证据 |
|---|---|---|---|
| D1 | `neoforge-26.1.2` 的「白泽赐福溢出攻击力」与「绿洲女王威压」各注册了**两份**攻击修饰器 ⇒ 该线玩家攻击力每击双计 | 删掉后置的两份重复块（结构相同、注释相同、仅 `skipOnClient` 写法略异） | `83e601ce`；实机 `attackModifiers=32`（此前 34），`zhaoOverflowBonus` 读写正常 |
| D2 | `fabric-1.20.1` 的 `RepulsionEntity#hasOwnerSign` 仍是「本线无立牌系统 ⇒ 恒 true」的过期垫片 ⇒ 卸下/失去立牌后拒止力场不会销毁 | 改回 `LinglingSignManager.hasSign(player)`（本线立牌批早已落线），并改写注释 | `ed46bf8b`；实机：无立牌生成力场 2.2s 后 count=0；装 `lingling_sign` 再生成 ⇒ count=1 |
| D3 | 测试台 env 阶段不检查 `run/<版本>/mods` 里的 `astral_dice*.jar`：它与 dev classes 同 modId 双源，会让 datagen 不生成、改动"看似不生效"（本周期两次踩坑） | env 新增 `mod_shadow_<ver>` 检查（有影子 jar ⇒ WARN + 隔离指引） | `047cfce4`；正向 `mod_shadow=OK`，负向（放一个空 jar）`mod_shadow=WARN` |
| D4 | `.gitattributes` 未固定文档行尾 ⇒ 每次 checkout `docs/**/*.md` 被 autocrlf 转 CRLF、整篇显改动 | 增加 `docs/**/*.md text eol=lf`、`CHANGELOG*.md text eol=crlf`、`release/**/*.md text eol=crlf` | `047cfce4` |
| D5 | `neoforge-26.1.2` 缺 `data/astral_dice/curios/entities/player_like_maid.json`（P0/forge 有）⇒ 同版本对注册生物饰品槽不一致 | 补齐 `entities: ["touhou_little_maid:maid"]`、`slots: ["stand","chip"]` | 数据件，JSON 解析通过；该线无 TLM，运行期不可达（见 D6） |

> 2026-11-09 口径变更：进程 / 窗口 / 桌面操作改由 `dsh-windows-mcp`（`mcp__windows__*`）承担，
> 本表提到的 `work/audit/stop_tlmdice.sh`、windows MCP 的 `Process(mode:"kill", pid=…)`、`ps -W` 一类做法一律**降级为历史取证记录**，
> 不再作为执行口径（规则正本见 `docs/agent/AGENTS-dsh.md`〈⚙️ 环境与工具链口径〉第 10 条）。

## 二、已登记、未修（含原因与下一步）

| ID | 级别 | 欠账 | 现状 | 下一步 |
|---|---|---|---|---|
| D6 | P0 | `neoforge-26.1.2`（2026-11-11 起 P0）的 G6「注册参与者」端到端在本仓不可达：`run/mods` 里没有参与者提供方（TLMDice 只构建 forge-1.20.1 / neoforge-1.21.1）；fabric 两线的同类缺口仍按 P1 | 已做：静态同形 + 五线编译 + 玩家路径回归（fabric-1.21.1 三条用例 PASS、fabric-1.20.1 三条 OK、26.1.2 启动正常） | 等 TLMDice 出 26.1.2 构建后补端到端；或加 dev 测试参与者注册器 |
| D7 | P0 | `neoforge-26.1.2` 战斗类用例资产未接线：`DEF-CARD` / `MAMUSHI-COMBAT` / `HOSTILE-TARGETS` 全 `hit=False`，日志无 `AP_D0_/AP_CB_/AP_H1_` 探针行 | 不是崩溃，是该线缺探针脚本；2026-11-11 起 26.1.2 归 P0 ⇒ 按 P0 发布阻塞处理 | 按 `scripts/test/resources/kubejs/26.1.2/` 补三份探针或以「该线不做这三例」显式登记 |
| D8 | P2（26.1.2 现 P0 线） | 守卫颗粒度未逐行同形：`26.1.2` 13 处、`fabric-1.20.1` 10 处仍是「整段 lambda 提前返回」，P0 基准两线是逐调用点 | 数值口径与基准一致（都是玩家专属账户/背包 API 跳过），但结构不同；26.1.2 的该差异按 P0 线质量欠账跟踪 | 下一轮把这些块拆到语句级；改动需重跑五线编译与实机回归 |
| D9 | P2 | `fabric-*` 的七咒（第一诅咒 -50%）整链未移植：`enigmaticlegacyplus` 无 fabric 构建 | 已按用户裁决「不移植」，并在卡片 javadoc 与 CHANGELOG 写明 | 保持不移植；若将来有 fabric 构建再评估 |
| D10 | P2 | 生成物 `sykes_sign` 配方/模型：datagen 已不再产出，仓库里仍保留（靠 `git checkout` 维持） | 属既有欠账，非本周期引入 | 发布前定夺：删除并加迁移说明，或恢复生成源码 |
| D11 | P2 | forge dev 环境必须用「重映射件」：rhino / collective / superflat 的生产件（SRG refmap）在 Mojmap dev 必崩 | 已在 `run/1.20.1/mods` 用 MDG/FG 转换缓存件跑通（`rhino-dev.jar`、`collective-dev.jar`、`superflat-dev.jar`） | 把这些件纳入测试台自举（存在性 + sha256 + 缺失时明确报错），见 TLMDice 侧 prompt |
| D12 | P0 | `neoforge-26.1.2` 缺 `PlayerLikeWallet` / `ChipShopService`（`PlayerLikeTargets` 已有）；这两个类型只在 `neoforge-1.21.1` / `forge-1.20.1` | 源码实读：26.1.2 无 `PlayerLikeWallet.java` / `ChipShopService.java`；fabric 两线同样缺，仍 P1 | 26.1.2 现 P0，发布前必须补或显式裁决降级 |
| D13 | P1 | 目标选择器「女仆」类别未接线：本仓已提供 `TargetPromptCategories.registerActionCategory(actionId, "maid")`，TLMDice 侧尚未注册任何动作 | P0 三线 .35 与 fabric 两线 .36 均已改完并实机（读数见 `docs/reports/2026-11-14-target-selector-actionbar-35.md` 与 `docs/reports/2026-11-14-target-selector-actionbar-36-fabric.md`）；女仆选择动作在 TLMDice 仓（`MaidActiveSkillIntent` 等） | TLMDice 客户端初始化时对其选择动作注册 `maid`，并补实机 |
| D15 | P1 | **Fabric 两线缺反击状态机**：P0 三线（`neoforge-1.21.1` / `forge-1.20.1` / `neoforge-26.1.2`）的反击是一段 10 秒状态（`item/CounterStateManager.java`），Fabric 两线 grep 该名为 0 命中，只有「受击一次反击一次」的一次性语义 | 判据：`grep -rn CounterStateManager fabric-1.20.1/src fabric-1.21.1/src` → 0；同命令在 P0 三线有命中 | 影响：金鲤与 Z3000 在 Fabric 的强度低于 P0，`.38` 的 guide 文案已按 Fabric 实际行为（一次性）改写，不再是缺陷文案；但两线语义仍不一致 | 修法：把状态机移植到两线并同时改 `DiceCombatEvents` 的反击分支与 `RenShieldManager` 的保留条件，再各跑一轮实机 |
| D14 | 已解决（2026-11-14） | 垃圾桶立牌 `z3000_sign` 原先只在 P0 三线落地；现已在 `fabric-1.20.1` / `fabric-1.21.1` 落地（26.4-SNAPSHOT.37，提交 `a04ddd5d`） | 两线实机通过：4.5 格拉取 1000→995、17 格拒绝、冷却闸门、拖拽延迟击杀 +1 反击并 -33% 冷却、两个 20 血击杀把攻击叠层 0→1（`ATTACK_DAMAGE` 1.0→2.0）；读数见 `docs/reports/2026-11-14-z3000-fabric-37.md` | 未覆盖：反击层数消耗路径、视觉、1200 tick 节流在连续击杀下的表现（需要时补测） |

## 三、跨仓（TLMDice）

TLMDice 侧的欠账不直接改对方仓库，统一出 prompt：`docs/handoff/2026-11-09-tlmdice-debt-prompt.md`。
其中与本仓强相关的一条：**TD-14 已过期** —— 上游 `26.4-SNAPSHOT.7` 起 forge-1.20.1 已有 `PlayerLikeTargets` / `PlayerLikeWallets` / `ChipShopService`，TLMDice 的 `MaidShopIntent` / `MaidActiveSkillIntent` / `MaidTransferIntent` 三个 `BLOCKED_BY` 桩可以开始接线。
