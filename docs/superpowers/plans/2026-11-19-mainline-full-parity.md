# 主线全量对齐计划（2026-11-19）

> 状态：阶段 0–2 已完成；阶段 2 已本地提交（库 `0147fb3` / 主模组 `515be163`，未 push），阶段 3 待开始。
> 仓库：`F:/MCProject/astral_dice_multiloader`（`feature/mainline-full-parity`）与 `F:/MCProject/starengine`（`feature/mainline-full-parity`）。
> 基线 tag：两仓均为 `mainline-parity-baseline-2026-11-19`。

## 1. 目标与边界

把主线 `multi-main` / `main` 与当前 dev 线（`multi-dev-next` / `next`）在 API 归属、内容归属和去重上全量对齐，按“内容归属 → API 最后”的顺序执行。

本期做：

- P1 五类重复实现去重，并补两条 fabric 的 `EffectTimerGuard.setTimerSource(...)`。
- A/B：18 个 `starengine.effect.*`、`Rarity`、五线 `AstralRarities` 搬回主模组。
- C：`GameplayConstants` 的 25 个星骰内容字段与 `cardCostForStar(int)` 搬回主模组，通用字段留库。
- API：库补齐 `api.astral` 36 类、103 个门面方法 / 101 个 service 方法；主模组删旧 API 并新增 `apiimpl.AstralDiceServiceImpl`。
- 五线版本、依赖区间、CHANGELOG、README、审计记录收口。

本期不做：

- 不 push、不打远程 tag、不部署、不跑实机。
- 不写空壳实现；目标独有类若主线没有对应功能且无法从 dev 移植，标 `defer`。
- 不删 `models/`、`work/` 等历史未跟踪物。

## 2. 基线（阶段 0 实测）

| 项 | 值 |
|---|---|
| 主模组基线 | `multi-main` @ `ce6e85f4`，版本 `1.6.0-pre.1` |
| 库基线 | `main` @ `26ab1df`，版本 `1.2.0-pre.1` |
| 主模组工作树 | tracked 干净；仅有 `deploy.ps1`、`work/**`、`nul` 等历史未跟踪物 |
| 库工作树 | tracked 干净 |
| 基线 tag | 两仓 `mainline-parity-baseline-2026-11-19` |
| 基线构建 | `clean assemble --offline --no-daemon` 成功，13s，34 tasks（27 executed / 7 cached） |

基线五线 jar：

| 线 | jar 字节 |
|---|---:|
| fabric-1.20.1 | 3,356,149 |
| fabric-1.21.1 | 3,382,633 |
| forge-1.20.1 | 3,099,626 |
| neoforge-1.21.1 | 3,047,332 |
| neoforge-26.1.2 | 3,276,199 |

## 3. API 差距矩阵

### 3.1 目标库 `api.astral`：36 类

| 分组 | 数量 | 类 |
|---|---:|---|
| 根 | 2 | `AstralDiceApi`, `AstralDiceService` |
| combat | 20 | `AttackPowerModifier`, `CombatResolutionListener`, `CombatResolutionListeners`, `CombatResolutionView`, `CombatStat`, `CombatStatContext`, `CombatStatHandle`, `CombatStatHook`, `CombatStatHooks`, `CombatStatStage`, `DamageCategory`, `DamageCategoryResolver`, `DefensePowerModifier`, `DiceBattleView`, `DiceCombatFactor`, `ExtraDamageModifier`, `SpellDamageMatcher`, `SpellDamageModifier`, `SpellDamageView`, `VictimDamageModifier` |
| choice | 1 | `CardChoiceType` |
| client | 3 | `AstralHudLayer`, `AstralHudLayers`, `AstralKeyBindings` |
| data | 3 | `DiceLoadout`, `DiceTierView`, `InstalledCard` |
| event | 4 | `AstralEventEffect`, `AstralEventPolarity`, `AstralEventTier`, `AstralEventTrigger` |
| playerlike | 3 | `PlayerLikeCapability`, `PlayerLikeHandler`, `PlayerLikeWallet` |

### 3.2 主线现有 API：39 个物理文件 / 15 个唯一类

| 分组 | 物理文件 | 唯一类 |
|---|---:|---:|
| common `api` | 9 | 9（`AstralDiceApi` + 8 个 `api/combat`） |
| `event/api` | 25（5 线 × 5） | 5（`AstralEvent` / `Effect` / `Polarity` / `Tier` / `Trigger`） |
| `choice/CardChoiceType` | 5 | 1 |
| 合计 | 39 | 15 |

主线引用面：

| 符号 | 文件 | 处 |
|---|---:|---:|
| `AstralDiceApi` | 8 | 21 |
| `DiceTierRegistry` | 17 | 109 |
| `CombatStatHooks` | 4 | 23 |
| `AstralDicePlatform` | 8 | 21 |
| `AstralDicePlatforms` | 8 | 13 |
| `CardChoiceType` | 25 | 136 |
| `AstralEvent` | 45 | 245 |
| `AstralEventTrigger` | 47 | 251 |

### 3.3 重叠与独有

- 重叠 14 类：`AstralDiceApi`、8 个 `CombatStat*` / `DamageCategory*`、`CardChoiceType`、4 个 `AstralEvent*`（Effect/Polarity/Tier/Trigger）。
- 主线独有 1 类：`AstralEvent` 记录（目标库没有；阶段 4 留在主模组，作为主模组事件系统的记录类型）。
- 目标独有 22 类：见 3.4。

### 3.4 22 个目标独有类分类

分类口径：`port implementation` = 主线已有内部能力，补库 API + 主模组实现；`mainline feature absent` = 主线当前完全没有对应实现，需要从 dev 移植；`defer` = 本期不做。

| 类 | 分组 | 分类 | 主线依据 / 备注 |
|---|---|---|---|
| `AstralDiceService` | 根 | port implementation | 新 SPI；5 个入口安装，委托现有静态门面/内部实现 |
| `AttackPowerModifier` | combat | port implementation | 主线 `combat/AttackPowerModifier` 内部类型已存在 |
| `CombatResolutionListener` | combat | mainline feature absent | 主线无 `CombatResolution`；dev `DiceCombatEvents` 已每线两处 fire |
| `CombatResolutionListeners` | combat | mainline feature absent | 同上；`CopyOnWriteArrayList` + `ThreadLocal` 重入闸门 |
| `CombatResolutionView` | combat | mainline feature absent | 同上；结算快照记录 |
| `DefensePowerModifier` | combat | port implementation | 主线内部类型已存在 |
| `DiceBattleView` | combat | port implementation | 主线 `DiceCombatContext` 可投影 |
| `DiceCombatFactor` | combat | port implementation | 主线 `DiceCombatModifiers` 已有最终伤害因子链 |
| `ExtraDamageModifier` | combat | port implementation | 主线 `DiceCombatModifiers` 已有额外加伤 |
| `SpellDamageMatcher` | combat | port implementation | 主线 `SpellDamageRegistry` / `SpellDamageContext` |
| `SpellDamageModifier` | combat | port implementation | 主线内部类型已存在 |
| `SpellDamageView` | combat | port implementation | 主线 `SpellDamageContext` 可投影 |
| `VictimDamageModifier` | combat | port implementation | 主线 `DiceCombatModifiers` 已有受击侧倍率 |
| `AstralHudLayer` | client | port implementation | 主线 `CombatPowerHud` / 各线 HUD 注册可包一层 |
| `AstralHudLayers` | client | port implementation | 同上；版本中立注册表 |
| `AstralKeyBindings` | client | port implementation | 主线 `KeyBindingSetup` / 各线 `RegisterKeyMappingsEvent` |
| `DiceLoadout` | data | port implementation | 主线 `DiceTier` + `WeaponEnhancement#appliedStones` |
| `DiceTierView` | data | port implementation | 主线 `DiceTierRegistry` 可投影 |
| `InstalledCard` | data | port implementation | 主线卡牌栏组件可投影 |
| `PlayerLikeCapability` | playerlike | port implementation | 主线五线均有 `target/PlayerLikeTargets` |
| `PlayerLikeHandler` | playerlike | port implementation | 主线 `PlayerLikeTargets.Handler` 可适配 |
| `PlayerLikeWallet` | playerlike | port implementation | forge/neo 已有 `compat/playerlike/PlayerLikeWallet`；fabric 需补桥 |

统计：19 个 `port implementation`，3 个 `mainline feature absent`，0 个 `defer`。

> Ruling：`CombatResolution*` 不标 defer，放进阶段 4 从 dev 移植。理由：dev 已有完整实现，主线引用为 0，新增面只有 3 个类 + 每线两处 fire；若判断错误，代价是阶段 4 多 3 个类和 10 处调用点，不影响前三个阶段。

### 3.5 门面方法缺口

- 目标 `AstralDiceApi`：103 个 `public static` 方法（98 个唯一名，含重载）。
- 主线 `AstralDiceApi`：31 个（29 个唯一名，含重载）。
- 净差 72 个方法；按唯一名是 74 个缺口（`registerPlayerLike`、`startCardChoice`、`triggerAstralEvent` 等重载造成差值）。

按领域分组（74 个唯一名）：

| 领域 | 缺口数 | 代表方法 |
|---|---:|---|
| 生命周期 / service | 2 | `install`, `service` |
| 骰子阶层视图 | 3 | `registerDiceTierView`, `diceTierView`, `diceTierViews` |
| 类玩家 | 11 | `registerPlayerLike`, `isPlayerLike`, `matchesPlayerSelection`, `playerLikeWallet*` |
| 物品 / 卡牌 / 骰子 | 15 | `giveCardTo`, `diceLoadout`, `equipCard`, `openCardInventory` |
| 立牌 / 效果 | 7 | `isSignItem`, `activateSign`, `applyAstralEffect`, `effectLevel` |
| 骰战参与者 | 3 | `registerDiceCombatParticipant`, `isDiceCombatParticipant` |
| 敌对目标 | 3 | `registerHostileProbe`, `isHostileTarget` |
| 战斗修饰器 | 8 | `registerAttackModifier` … `registerCombatResolutionListener` |
| 目标选择 | 5 | `startTargetSelection`, `confirmTargetSelection` |
| 卡牌选择 | 3 | `startCardChoice`, `matchesCardChoice` |
| 星骰事件 | 4 | `registerAstralEvent`, `astralEvent`, `triggerAstralEvent` |
| 经济 | 7 | `isStarCoin`, `starCoinValue`, `depositCarriedStarCoins` |
| 配置读取 | 3 | `firearmDamageAllowed`, `actionBarDurationTicks`, `actionBarFadeTicks` |

## 4. 内容归属欠账（阶段 1–3 的范围）

### 4.1 P1 重复实现：13 个本地文件

- `PartyRelations`：`fabric-1.20.1`、`neoforge-26.1.2` 各 1。
- `EffectTimerGuard`：两条 fabric 各 1。
- `EliteTargets`：两条 fabric + `neoforge-26.1.2`，共 3。
- `HoldToSelect`：两条 fabric + `neoforge-26.1.2`，共 3。
- `SelfTargetable`：两条 fabric + `neoforge-26.1.2`，共 3。

另需：

- 两条 fabric 的 `AstralDiceMod` 在 `AstralDiceApi.install(...)` 后插入 `EffectTimerGuard.setTimerSource(new SignTimerSourceAdapter())`。
- `reportBackends` 的 `AP_FAB_PARTY` 改为 `AP_PARTY`；同步 `scripts/test/cases/PARTY-BACKENDS-fabric-1.20.1.json`、`scripts/test/fabric/cases/FAB-PARTY-BACKENDS.json` 和 `scripts/test/fabric/README.md`。

### 4.2 A/B：效果与稀有度

- 库删除 18 个 `com.merlinkitsune.starengine.effect.*`：`BerserkEffect`、`CounterEffect`、`CutterReadyEffect`、`DiceBlessingEffect`、`FateGuidanceEffect`、`HealingEffect`、`InvestigationBonusEffect`、`KingPowerEffect`、`LivingPageEffect`、`MarkedEffect`、`MisakiBurstEffect`、`MosesBrokenEffect`、`NancyLuHackEffect`、`PandamanTauntEffect`、`PaparaBiteEffect`、`RevengeHalberdEffect`、`UndercoverInvestigationEffect`、`WeakMarkEffect`。
- 库删除 `common/.../item/Rarity.java` 与五线 `item/AstralRarities.java`。
- 主模组接收，包名改为 `com.merlinkitsune.astral_dice.effect` / `com.merlinkitsune.astral_dice.item`。
- 引用改写：18 效果 15 文件 / 100 处；`Rarity` 25 / 25；`AstralRarities` 30 / 40。
- 两份 NeoForge `META-INF/enumextensions.json` 各 5 条改指 `com/merlinkitsune/astral_dice/item/AstralRarities`。

### 4.3 C：GameplayConstants 拆分

- 主模组新增 `common/src/main/java/com/merlinkitsune/astral_dice/component/AstralGameplayConstants.java`。
- 搬迁 25 个内容字段 + `cardCostForStar(int)`；库只留 8 个通用字段：`GIVE_GUIDE_BOOK_ON_FIRST_JOIN`、`EVENT_APPLY_MC_TEAM`、`EVENT_APPLY_FTB_TEAM`、`EVENT_APPLY_OPAC`、`ALLOW_FIREARM_DAMAGE`、`ACTIONBAR_DURATION_TICKS`、`ACTIONBAR_FADE_TICKS`、`TARGET_SELECT_RADIUS`。
- `GameplayConfigValues` 7 字段本批不动；`GIVE_GUIDE_BOOK_ON_FIRST_JOIN` 留库作为配置 seam。
- 引用面：312 文件 / 325 处；143 个 content-only、36 个 mixed（共 179 个 Java 文件，按 dev 线口径）。

### 4.4 留库项（已裁决）

- `StarCoinWalletState` 留库。
- `StarCoinCommand` 留库（命令构建共享，平台侧只注册）。
- `StarEngineEconomy` 留库（主模组 58 文件 + TLMDice 2 文件使用）。
- `StarEnginePlatform`、`api.astral` 的共享契约留库。

## 5. 分阶段执行

### 阶段 0：基线与矩阵（已完成）

- [x] 检查 `.agent-locks`，更新本轮 `dsh.lock`；无 `kimi.lock`。
- [x] 两仓打 `mainline-parity-baseline-2026-11-19`。
- [x] 两仓从基线建 `feature/mainline-full-parity`。
- [x] 主线五线 `clean assemble --offline --no-daemon` 通过（13s）。
- [x] 产出本文件与 36 vs 15 矩阵、22 类分类、72 方法缺口。
- [x] 矩阵确认后进入阶段 1（2026-11-19 用户确认 19/3/0 分类与 `CombatResolution*` 不 defer）。

### 阶段 1：P1 去重

1. 复用 dev 线 `work/p1_dedupe.py`，先 dry-run，确认 13 个删除、138 个文件改写、两条 fabric 的 `setTimerSource` 插入、`AP_FAB_PARTY → AP_PARTY`。
2. `--apply` 后逐文件检查 import / 同包引用；删除 13 个本地文件。
3. 版本：库 `1.2.0-pre.2`，主模组 `1.6.0-pre.2`；依赖区间同步。
4. 库 `clean publishToMavenLocal --offline --no-daemon`；主模组五线 `clean assemble --offline --no-daemon`。
5. 校验：13 个本地类不在主模组源码/jar；相关 import 全部指向 `starengine`；两条 fabric 的 `setTimerSource` 存在；`AP_PARTY` 断言生效。
6. 独立提交（库 / 主模组分开）。

**执行记录（2026-11-19）**

- dry-run：138 处改动、13 个删除；代码/版本/测试共 132 个文件（119 修改 / 13 删除），+213/−2176；加入 3 个文档/CHANGELOG 后提交共 135 文件，+246/−2179。
- 删除：fabric-1.20.1 5 个、fabric-1.21.1 4 个、neoforge-26.1.2 4 个本地副本。
- fabric：`SignTimerSourceAdapter` 改为库版 `TimerEntry` 防御性拷贝；`ModAttachments.EFFECT_TIMER_ENDS` 泛型切库版；两条入口在平台工厂后调用 `EffectTimerGuard.setTimerSource(...)`。
- `PartyRelations#reportBackends()` 改走库版，机器行 `AP_PARTY:`；两个测试夹具与 README 同步。
- 版本：库 `1.2.0-pre.2`（提交 7 文件：5 gradle + 2 CHANGELOG，+28/−10）；主模组 `1.6.0-pre.2`（五线依赖区间同步）。
- 构建：库 `clean compileJava publishToMavenLocal --offline --no-daemon` 11s；主模组 `clean assemble --offline --no-daemon` 25s。
- 产物：五份主模组 jar、五份库 jar；`work/verify_p1.sh` 输出 `P1_VERIFY_OK`。未跑实机。
- 提交：库 `0abae39`、主模组 `92c5cf73`；均未 push，tracked 工作树干净。

### 阶段 2：A/B 内容归属

1. 库删除 18 效果 + `Rarity` + 五线 `AstralRarities`；主模组接收并改包名。
2. 改写 15/100、25/25、30/40 引用；两份 `enumextensions.json` 改指新类。
3. 版本：库 `1.2.0-pre.3`，主模组 `1.6.0-pre.3`。
4. 库 `clean publishToMavenLocal`；主模组五线 `clean assemble`。
5. 校验：五份 jar 含新类、不含旧 `starengine` 路径；NeoForge 枚举扩展 JSON 指新类；库 jar 无旧类。
6. 独立提交。

**执行记录（2026-11-19）**

- 库删除 24 个源文件（18 效果 + `Rarity` + 五线 `AstralRarities`）；主模组新增 24 个源文件，改写 100 处 effect、25 处 `Rarity`、40 处 `AstralRarities`，清理 95 条同包 import。
- 两份 NeoForge `META-INF/enumextensions.json` 各 5 条改指 `com/merlinkitsune/astral_dice/item/AstralRarities`。
- 版本：库 `1.2.0-pre.3`（提交 32 文件，+46/−1218）；主模组 `1.6.0-pre.3`（提交 78 文件，+1305/−187）。
- 构建：库 `clean compileJava publishToMavenLocal --offline --no-daemon` 17s；主模组五线 `clean assemble --offline --no-daemon` 28s。
- 产物：五份主模组 jar、五份库 jar；`work/verify_ab_mainline.sh` 输出 `AB_VERIFY_OK`。未跑实机。
- 提交：库 `0147fb3`、主模组 `515be163`；均未 push，tracked 工作树干净。
### 阶段 3：C 常量拆分

1. 主模组新增 `AstralGameplayConstants`（25 字段 + `cardCostForStar`）；库 `GameplayConstants` 只留 8 个通用字段。
2. 改写 312 文件 / 325 处；mixed 文件同时保留库 import 与新类 import。
3. 版本：库 `1.2.0-pre.4`，主模组 `1.6.0-pre.4`。
4. 库 `clean publishToMavenLocal`；主模组五线 `clean assemble`。
5. 校验：五份主模组 jar 含 `AstralGameplayConstants`；库 jar 的 `GameplayConstants` 只有通用字段；旧内容字段残留 0。
6. 独立提交。

### 阶段 4：API 全量

1. 库补齐 `api.astral` 36 类；`AstralDiceApi` 103 方法、`AstralDiceService` 101 方法。
2. 主模组删除旧 9 个 API 文件（`common/.../api`），新增 `apiimpl.AstralDiceServiceImpl`；5 个入口安装。
3. 处理 7 个 mod seam：`registerDiceTier`、`isContentFrozen`、`freezeContent`、`platform`（主线 0 调用，可删）、`installCombatStatSupport`、`isCombatStatSupported`、`registerCombatStatHook`。
4. 事件：4 个 `AstralEvent*` 进库；`AstralEvent` 记录留主模组。`CardChoiceType` 5 份合成 1 个库类，先做物品标签 seam，处理 25 文件 / 136 处引用。
5. 目标独有 22 类按 3.4 落地；`CombatResolution*` 从 dev 移植，每线 `DiceCombatEvents` 两处 fire。
6. 版本：库 `1.2.1`，主模组 `1.6.0-pre.5`。
7. 库 `clean publishToMavenLocal`；主模组五线 `clean assemble`。
8. 校验：`api.astral` 36 类在库 jar；主模组无旧 API 包；5 个入口安装；方法数 103/101；事件与选择编译通过。
9. 独立提交。

### 阶段 5：收口

1. 五线 `gradle.properties`、`fabric.mod.json`、NeoForge/Forge 元数据、依赖区间统一到库 `1.2.1` / 主模组 `1.6.0-pre.5`。
2. 库五份 `publishToMavenLocal`；主模组五线 `clean assemble`。
3. 双语 CHANGELOG、`docs/design/09-mainline-full-parity.md`、审计记录、README。
4. 本地提交，不 push；不跑实机，不称可发布。

## 6. 验证与回滚

- 每个阶段：先 `git status --short` 确认 tracked 基线，构建前记录 HEAD；构建后核对 jar 类清单与版本。
- 失败回退：`git reset --hard <阶段提交前>` + 重新应用该阶段脚本；不 `git clean`、不删 `models/`/`work/`。
- 版本号只在该阶段产品代码/资源改动后 bump；仅文档改动不 bump。
- 远程 git 操作一律走本地代理 `http://127.0.0.1:7897`；本期不 push。

## 7. 门禁

- 阶段 1–3 完成前不进入阶段 4。
- 阶段 4 完成前不改 TLMDice；TLMDice 对 `AstralDiceApi` / `CardChoiceType` / `AstralEvent` 当前引用为 0。
- 矩阵确认是阶段 0 的出口：确认 19/3/0 分类、`CombatResolution*` 不 defer、72 方法缺口按阶段 4 处理后再开始阶段 1。

