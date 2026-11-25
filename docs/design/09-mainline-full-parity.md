# 主线全量对齐（StarEngine 1.2.0-pre.2 / Astral Dice 1.6.0-pre.2）

> 状态：本地预发布候选，未 push、未发布、未跑实机。本文记录 2026-11-19 主线与 dev 线 API 全量对齐后的最终形态、接线方式与验收读数。
>
> 仓库：`D:/MCProject/astral_dice_multiloader`（主模组，分支 `feature/mainline-full-parity`）与 `D:/MCProject/starengine`（库，同名分支）。
>
> 基线 tag：`mainline-parity-baseline-2026-11-19`。主模组基线 `ce6e85f4`（1.6.0-pre.1），库基线 `26ab1df`（1.2.0-pre.1）。

## 1. 目标与边界

本阶段把主模组从「只保留 15 个唯一 API 类」推进到与 dev 线一致的完整 API 面：库侧提供 `com.merlinkitsune.starengine.api.astral`，主模组只保留实现与内容，不再各自维护 API 副本。范围包含 P1 去重、A/B 内容归属、C 常量拆分、22 个目标独有类和 72 个门面方法缺口。

不包含：TLMDice 六职业远程路线、实机客户端/服务端冒烟、远程发布、CurseForge 上传、tag。TLMDice 对 `AstralDiceApi` / `CardChoiceType` / `AstralEvent` 的引用仍为 0，未被本次改动波及。

## 2. 最终 API 面

### 2.1 库侧

`common/src/main/java/com/merlinkitsune/starengine/api/astral` 共 36 个源文件；另有 `common/src/main/java-rl/.../AstralEvent.java` 与 `neoforge-26.1.2/.../AstralEvent.java` 两个资源标识符变体。物理文件 38 个，逻辑类 37 个。

| 包 | 物理文件 | 逻辑类 | 说明 |
|---|---:|---:|---|
| 根 | 2 | 2 | `AstralDiceApi`、`AstralDiceService` |
| `combat` | 20 | 20 | 战斗修饰器、CombatStat、CombatResolution*、SpellDamageView 等 |
| `choice` | 1 | 1 | `CardChoiceType` |
| `client` | 3 | 3 | HUD、Layer、KeyBindings |
| `data` | 3 | 3 | `DiceLoadout`、`DiceTierView`、`InstalledCard` |
| `event` | 6 | 5 | 4 个事件元数据类 + `AstralEvent` 双变体 |
| `playerlike` | 3 | 3 | `PlayerLikeCapability`、`PlayerLikeHandler`、`PlayerLikeWallet` |

`AstralEvent` 记录最终进库，不再留在主模组：旧 `com.merlinkitsune.astral_dice.event.api.AstralEvent` 已删除，30 个主模组源文件改为 import 库版。1.20.1 / 1.21.1 / Forge 使用 `ResourceLocation` 变体，26.1.2 使用 `Identifier` 变体；两者字段与语义一致，仅标识符类型不同。

门面计数（`javap`，库 1.2.0-pre.2 jar）：`AstralDiceApi` 103 个 `public static` 方法，`AstralDiceService` 101 个 `abstract` 方法。主模组旧门面为 31 个 `public static`（29 个唯一名），净缺口 72；实际按唯一名补齐 74 个（含重载）。

### 2.2 主模组侧

旧 API 物理文件 39 个、唯一类 15 个，全部删除：

- `common/.../astral_dice/api` 9 个（旧 `AstralDiceApi` + combat 8）。
- 五线 `event/api` 各 5 个，共 25 个。
- 五线 `choice/CardChoiceType` 各 1 个，共 5 个。

新增主模组实现：

- `common/.../apiimpl/AstralDiceServiceImpl.java`（1184 行）与 `PlayerLikeTargetsAdapter.java`。
- `common/.../item/card/DiceCardLoadout.java`；五线 `item/dice/DiceLoadoutAccess.java`。
- 五线 `combat/CombatApiAdapters.java`；三线补 `item/card/CardUseService.java`（Fabric 1.20.1、Fabric 1.21.1、NeoForge 26.1.2）。
- 五线 `platform/AstralDicePlatform` 实现覆盖为 dev 的约 221 行版本；common 接口为 207 行。

`CardChoiceType` 五份合为库侧一份，五线 `CardChoiceManager` 仍各一份，但只 import 库枚举；`matchesCardChoice` 走 `AstralDiceApi`。`AstralRarities` 与 `Rarity` 已在阶段 2 搬到主模组，库不再拥有内容类。

## 3. 运行期接线

五个入口在 `AstralDicePlatforms.install(...)` 之后、任何内容注册之前安装服务：

```java
AstralDiceApi.install(new AstralDiceServiceImpl());
```

`AstralDiceServiceImpl` 把门面调用分派到主模组内部：`DiceTierRegistry`、`AstralEventRegistry`、`CardChoiceManager`、`DiceCombatParticipants`、`TargetSelectionManager`、`DiceCardLoadout`、`BaseSignItem`、`AstralDicePlatforms`、`PlayerLikeTargets`。库侧不反向依赖主模组，平台差异只通过 `AstralDicePlatform` 与 `PlayerLikeTargets` seam 进入。

`CombatResolution` 在五线 `DiceCombatEvents` 各接两处：玩家侧结算完成后 fire `CombatResolutionView(player, target, finalDmg, applied, extraSettled, diceStack, starLevel)`；参与者侧在 `mobLanding` 计算后 fire `CombatResolutionView(mob, target, finalDmg, mobLanding, g6ExtraSettled, attackerDie, starLevel)`。五线变量名不完全相同，按各线作用域逐线接入，未跨线照抄。

`PlayerLikeWallets.resolve` 在内部绑定未命中时回落 `AstralDiceApi.playerLikeWallet(holder)`，并用 `ApiWalletAdapter` 适配；`PlayerLikeTargets.giveItem` 对玩家走 `ResourceConversion`，不再直接返回 `false`。

## 4. 验收读数

库 `clean publishToMavenLocal --offline --no-daemon`：`BUILD SUCCESSFUL in 13s`。五份 m2 jar：

| 平台 | 大小 (B) | `api/astral` 条目 |
|---|---:|---:|
| fabric-1.20.1 | 692,160 | 47 |
| fabric-1.21.1 | 804,405 | 47 |
| forge-1.20.1 | 620,190 | 47 |
| neoforge-1.21.1 | 659,974 | 47 |
| neoforge-26.1.2 | 822,378 | 47 |

主模组五线 `clean assemble --no-daemon --no-configuration-cache`：最终从版本提交 `1d376ae0` 构建，`BUILD SUCCESSFUL in 11s`，34 个任务（27 执行 / 7 缓存）。五份 jar：

| 线 | 大小 (B) | SHA-256 |
|---|---:|---|
| fabric-1.20.1 | 3,362,853 | `b90eb47080de757a96b27b191def9305fc595a7ff21afc4a6c2e2a001314944b` |
| fabric-1.21.1 | 3,399,442 | `b4f71bee4acfed3aebc8e9ae5a39e8ecc50ad9b22ff9ab5482b620ce1a61fcf0` |
| forge-1.20.1 | 3,121,422 | `93c2580eb62e537c61e054188afbba38c1dfc27efa1622eb8f4505b5ef614b46` |
| neoforge-1.21.1 | 3,071,135 | `f63fee78050201ae2f991ec93c47e42c9861a934b34cd14b2f476e0f7b75b2d4` |
| neoforge-26.1.2 | 3,290,021 | `8f3adab0a47f92466b7775be6c78e9a6cae19a59cdf695f2fb5c2c2dc07c3910` |

五份 jar 均满足：`apiimpl/AstralDiceServiceImpl.class` 计数 1；旧 `astral_dice/api/AstralDiceApi.class`、旧 `astral_dice/event/api/AstralEvent.class`、旧 `astral_dice/choice/CardChoiceType.class` 计数均为 0。

`build-info.json`：Forge 1.20.1 为 `commit=1d376ae0ad9a453d62fb565702a918aacfd0a0df`、`classCount=681`；NeoForge 1.21.1 为同一 commit、`classCount=657`。两条 Fabric 线与 NeoForge 26.1.2 不内嵌 build-info，按大小 + SHA-256 识别。

## 5. 已知风险与未覆盖

- **未跑实机**：没有启动客户端或服务端，没有验证事件 GUI、CombatResolution 监听器、PlayerLike 转账/交付、CardChoiceType 物品标签匹配在运行期的行为。上述接线只经过编译与产物类清单校验。
- **预发布线包迁移**：`TargetType` 从 `com.merlinkitsune.starengine.target` 迁到 `api.target`，`HostileTargets` 从 `combat` 迁到 `api.combat`。这是 `1.2.0-pre.x` 线内的破坏性变更；稳定 `1.2.0` 尚未发布，唯一消费方主模组已在同一批更新。库 README 的 `1.x` 契约说明已标注该预发布例外。
- **`AstralEvent` 双变体**：库有 `ResourceLocation` 与 `Identifier` 两份源文件，逻辑上是同一类型；后续若增加第三方消费方，需要按 MC 版本选择对应 jar，不能把两份同时放进同一 classpath。
- **`CardChoiceManager` 未合并**：五线仍有五份 manager，只统一了枚举。它依赖各线物品注册与 GUI，合并需要先抽物品标签 seam，不在本次范围。
- **未 push / 未发布**：远程仓库、CurseForge、tag 均未动；README 中的版本与哈希只描述本地构建，不代表已发布产物。

## 6. 提交

| 阶段 | 库 | 主模组 |
|---|---|---|
| 0 基线与矩阵 | — | `173a57af` |
| 1 P1 去重 | `0abae39` | `92c5cf73` |
| 2 A/B 内容归属 | `0147fb3` | `515be163` |
| 3 C 常量拆分 | `447ba39` | `ed82ca32` |
| 4 API 全量 | `6a6ca4f` | `55ffdb6d` |
| 版本回退 pre.2 | `caf438c` | `1d376ae0` |
| 文档收口 | 本次提交 | 本次提交 |

所有提交均在本地 `feature/mainline-full-parity` 分支，未 push。
