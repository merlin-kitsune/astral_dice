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
| D6 | P1 | P1 三线的 G6「注册参与者」端到端在本仓不可达：`run/mods` 里没有参与者提供方（TLMDice 只构建 forge-1.20.1 / neoforge-1.21.1） | 已做：静态同形 + 五线编译 + 玩家路径回归（fabric-1.21.1 三条用例 PASS、fabric-1.20.1 三条 OK、26.1.2 启动正常） | 等 TLMDice 出 P1 构建后补端到端；或在上游加一个不依赖第三方模组的测试参与者注册器（仅 dev） |
| D7 | P1 | `neoforge-26.1.2` 战斗类用例资产未接线：`DEF-CARD` / `MAMUSHI-COMBAT` / `HOSTILE-TARGETS` 全 `hit=False`，日志无 `AP_D0_/AP_CB_/AP_H1_` 探针行 | 不是崩溃，是同名用例在这条线上缺探针脚本 | 按 `scripts/test/resources/kubejs/26.1.2/` 补三份探针或以「该线不做这三例」显式登记 |
| D8 | P2 | P1 守卫颗粒度未逐行同形：`26.1.2` 13 处、`fabric-1.20.1` 10 处仍是「整段 lambda 提前返回」，P0 是逐调用点 | 数值口径与 P0 一致（都是玩家专属账户/背包 API 跳过），但结构不同 | 下一轮把这些块拆到语句级；改动需重跑五线编译与实机回归 |
| D9 | P2 | `fabric-*` 的七咒（第一诅咒 -50%）整链未移植：`enigmaticlegacyplus` 无 fabric 构建 | 已按用户裁决「不移植」，并在卡片 javadoc 与 CHANGELOG 写明 | 保持不移植；若将来有 fabric 构建再评估 |
| D10 | P2 | 生成物 `sykes_sign` 配方/模型：datagen 已不再产出，仓库里仍保留（靠 `git checkout` 维持） | 属既有欠账，非本周期引入 | 发布前定夺：删除并加迁移说明，或恢复生成源码 |
| D11 | P2 | forge dev 环境必须用「重映射件」：rhino / collective / superflat 的生产件（SRG refmap）在 Mojmap dev 必崩 | 已在 `run/1.20.1/mods` 用 MDG/FG 转换缓存件跑通（`rhino-dev.jar`、`collective-dev.jar`、`superflat-dev.jar`） | 把这些件纳入测试台自举（存在性 + sha256 + 缺失时明确报错），见 TLMDice 侧 prompt |

## 三、跨仓（TLMDice）

TLMDice 侧的欠账不直接改对方仓库，统一出 prompt：`docs/handoff/2026-11-09-tlmdice-debt-prompt.md`。
其中与本仓强相关的一条：**TD-14 已过期** —— 上游 `26.4-SNAPSHOT.7` 起 forge-1.20.1 已有 `PlayerLikeTargets` / `PlayerLikeWallets` / `ChipShopService`，TLMDice 的 `MaidShopIntent` / `MaidActiveSkillIntent` / `MaidTransferIntent` 三个 `BLOCKED_BY` 桩可以开始接线。
