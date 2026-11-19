# P2 归属分类：库内“共享实现” vs 星骰内容

- 日期：2026-11-19
- 范围：dev-next 线（库 `starengine_next` / 主模组 `astral_dice_multiloader-next`），不含 main 线跟随迁移
- 状态：A/B 已执行（库 26.4-SNAPSHOT.17 / 主模组 26.4-SNAPSHOT.96）；C（GameplayConstants 拆分）已执行（库 26.4-SNAPSHOT.18 / 主模组 26.4-SNAPSHOT.97）；`StarCoinWalletState` 按用户裁决留库
- 前序：`docs/design/07-api-ownership.md`、`docs/features/api-audit/00-master-audit.md` §11–§12；P0/P1 已完成

## 0. 结论摘要

| 类别 | 对象 | 建议 | 影响面 |
|---|---|---|---|
| 真共享实现 | `StarEngineEconomy`、`StarCoinCommand`、`StarCoinWalletState` | 留在库 | 中（跨模组钱包） |
| 平台/契约 | `StarEnginePlatform(s)`、`api.astral` 37 类 | 留在库 | 无（P0 已归位） |
| 星骰内容 | `effect` 18 个 MobEffect | **已搬回**（.96/.17） | 低（15 文件） |
| 星骰内容 | `Rarity` + 五线 `AstralRarities` | **已搬回**（.96/.17） | 中（25 + 30 import、10 条 json） |
| 混合 | `GameplayConstants` | 已拆分：库通用 + 主模组内容常量（`.97` / `.18`） | 高（179 文件）；已完成 |
| 边界 | `StarCoinWalletState` | **留库**（m02749 裁决） | 低（15 文件） |

**判据**：一个类若被两个以上模组消费，或属于库的通用子系统（经济、目标、actionbar、平台 seam），且不含星骰角色/命名空间/内容 ID，算“共享实现”；若类名、效果名、稀有度 ID 或数值明显属于星骰内容，且只有主模组使用，算“应搬回主模组”。API 契约按 m01848 一律留库。

## 1. 建议留在库的对象

### 1.1 StarEngineEconomy（真共享）

- 文件：`starengine_next/common/src/main/java/com/merlinkitsune/starengine/economy/StarEngineEconomy.java`（101 行）
- 消费：主模组 58 个 Java 文件、240 处引用；TLMDice 2 个文件（两线 `wallet/MaidWalletService.java:5`）
- 库内：`economy/*EconomyStorage` 与 `api/economy/PlayerWallet.java:3` 使用
- 结论：跨模组钱包账本，保留。

### 1.2 StarCoinCommand（共享命令构建器）

- 文件：`starengine_next/common/src/main/java/com/merlinkitsune/starengine/economy/StarCoinCommand.java`（189 行）
- 库内注册：5 条平台线各自的 `*EconomyStorage` 调 `StarCoinCommand.register(...)`：
  - `fabric-1.20.1/.../FabricEconomyStorage.java:172-174`
  - `fabric-1.21.1/.../FabricEconomyStorage.java:181-183`
  - `forge-1.20.1/.../ForgeEconomyStorage.java:121`
  - `neoforge-1.21.1/.../NeoForgeEconomyStorage.java:126`
  - `neoforge-26.1.2/.../NeoForgeEconomyStorage.java:147`
- 主模组直接引用：0。
- 文件头自述（`StarCoinCommand.java:26-39`）：命令构建放在共享源码，平台侧只负责挂到 `RegisterCommandsEvent`，消息文本刻意用字面英文。
- 结论：这是库经济子系统的一部分，不是星骰内容，保留。若把库改成“纯契约库”，才需要搬到主模组；代价是 5 条线各自注册命令，且 TLMDice 不再自动获得 `/starcoin`。

### 1.3 StarCoinWalletState（边界，倾向留库）

- 文件：`starengine_next/common/src/main/java/com/merlinkitsune/starengine/economy/StarCoinWalletState.java`（35 行）
- 主模组 15 个文件、49 处引用；TLMDice 0；库内其它代码 0（只有自身）。
- 内容：`private static volatile long balance` + `balance()` / `setBalance(long)`，无任何客户端专有 import。
- 文件头自述（`:3-16`）：这是 `StarEngineEconomy` 服务端账本的**客户端显示缓存**，2026-09-22 从主模组下沉，口径逐字未变。
- 结论：名字与语义都属于库的星币经济，不含星骰内容，**建议留库**。若坚持“库只放 API”，可搬回主模组（1 个库文件 + 15 个 import），TLMDice 不受影响。

### 1.4 平台与 API

- `StarEnginePlatform` / `StarEnginePlatforms`：库平台 seam（`id` / `economyStorage` / `install`），保留。
- `api.astral` 37 类：门面 `AstralDiceApi`（100 个静态方法）+ `AstralDiceService` SPI + 36 契约；实现在主模组 `apiimpl.AstralDiceServiceImpl`。符合 m01848，保留。

## 2. 建议搬回主模组

### 2.1 effect：18 个 MobEffect 子类（低风险）

- 位置：`starengine_next/common/src/main/java/com/merlinkitsune/starengine/effect/`
- 类：`BerserkEffect`、`CounterEffect`、`CutterReadyEffect`、`DiceBlessingEffect`、`FateGuidanceEffect`、`HealingEffect`、`InvestigationBonusEffect`、`KingPowerEffect`、`LivingPageEffect`、`MarkedEffect`、`MisakiBurstEffect`、`MosesBrokenEffect`、`NancyLuHackEffect`、`PandamanTauntEffect`、`PaparaBiteEffect`、`RevengeHalberdEffect`、`UndercoverInvestigationEffect`、`WeakMarkEffect`
- 依赖：全部只 import `net.minecraft.world.effect.MobEffect` / `MobEffectCategory`（无 `starengine` 内部依赖、无加载器 API）。
- 库内代码引用：0（只有这些类自己的 package 行）。
- 主模组引用：15 个 Java 文件、200 处；每线 5 个文件，主要是 `effect/ModEffects.java` 的 18 条注册 import，加少量立牌文件。
- 迁移：把这 18 个文件移到主模组 `common/src/main/java/com/merlinkitsune/astral_dice/effect/`，package 改 `com.merlinkitsune.astral_dice.effect`；15 个文件把 `com.merlinkitsune.starengine.effect.*` 改成本包/新包 import。无资源、无 loader 差异、无 TLMDice 引用。
- 影响：低；五线 assemble 即可验证。

### 2.2 Rarity + AstralRarities（中低风险）

**Rarity（内容定义）**

- 文件：`starengine_next/common/src/main/java/com/merlinkitsune/starengine/item/Rarity.java`（223 行）
- 5 档：`RARE("ASTRAL_DICE_RARE", "astral_dice:rare", 0x55FFFF)`、`EPIC`、`LEGENDARY`、`PINNACLE`、`BIZARRE`（`:43-57`）。
- 这是星骰专用命名空间/常量名，不是通用库枚举；主模组 25 个文件 import。
- 库内代码引用：0（`AstralRarities.java` 里只有 javadoc `@link Rarity`）。

**AstralRarities（平台接线）**

- 五条线各 1 个文件：`starengine_next/{neoforge-1.21.1,forge-1.20.1,neoforge-26.1.2,fabric-1.20.1,fabric-1.21.1}/src/main/java/com/merlinkitsune/starengine/item/AstralRarities.java`
- 做法：NeoForge 用 `EnumProxy` + `enumextensions.json`；Forge 用 `Rarity.create`；Fabric 用 Unsafe 合成。
- 主模组 30 个文件 import；TLMDice 0；库内其它代码 0。
- 资源引用：`neoforge-1.21.1/src/main/resources/META-INF/enumextensions.json:8,17,26,35,44` 与 `neoforge-26.1.2/.../enumextensions.json:8,17,26,35,44` 的 `"class"` 都是 `com/merlinkitsune/starengine/item/AstralRarities`。
- 迁移：`Rarity` 放主模组 common `item` 包；五线 `AstralRarities` 放各线 `item` 包（package 改 `com.merlinkitsune.astral_dice.item`）；同步 10 条 json；主模组 25 + 30 个 import 改写（有重叠，按文件去重）。无 TLMDice 影响。
- 影响：中低；需要同时改 NeoForge 枚举扩展资源，启动期验证比 effect 多一步（至少 jar 内 json 路径与类名一致；实机未跑）。

### 2.3 GameplayConstants：拆分（高风险，建议单独一批）

- 文件：`starengine_next/common/src/main/java/com/merlinkitsune/starengine/component/GameplayConstants.java`（130 行）
- 主模组总引用：283 个 Java 文件、583 处；其中：
  - 174 个文件使用至少一个“星骰内容字段”；
  - 135 个文件使用至少一个“库通用字段”；
  - 26 个文件两者都用（按文件去重；174 + 135 − 26 = 283）。
- 库内使用：8 个文件，且**只用通用字段**：
  - `target/TargetSelectionAction.java:29` → `TARGET_SELECT_RADIUS`
  - `event/EventTargetCollector.java:105-107,164-169` → `EVENT_APPLY_MC_TEAM/FTB_TEAM/OPAC`
  - `combat/PartyRelations.java:124-126,207-209` → 同上
  - 五线 `client/ActionBarManager.java`（`neoforge-1.21.1:23,44`、`forge-1.20.1:22,56`、`neoforge-26.1.2:28,49`、`fabric-1.20.1:22,27,56`、`fabric-1.21.1:29,34,63`）→ `ACTIONBAR_DURATION_TICKS/FADE_TICKS`
- 建议保留在库的通用字段（7 个）：
  - `ACTIONBAR_DURATION_TICKS`、`ACTIONBAR_FADE_TICKS`
  - `EVENT_APPLY_MC_TEAM`、`EVENT_APPLY_FTB_TEAM`、`EVENT_APPLY_OPAC`
  - `TARGET_SELECT_RADIUS`
  - `ALLOW_FIREARM_DAMAGE`（库内当前无代码引用，但属于通用战斗配置 seam；可保留）
- 建议搬到主模组的内容字段（26 个 + 方法）：
  - 星光/标记/效果牌：`MAX_STARLIGHT`、`MAX_MARKER`、`EFFECT_CARD_COOLDOWN_SECONDS`、`MAX_EFFECT_STACKS`、`MAX_EFFECT_CARD_PLAYS`
  - 充能/立牌：`CHARGE_MAX_STACKS`、`CHARGE_SIGN_COOLDOWN_CAP_SECONDS`、`CHARGE_EFFECT_CARD_COOLDOWN_CAP_SECONDS`、`SIGN_ACTIVE_COOLDOWN_SECONDS`、`SIGN_ACTIVE_COOLDOWN_TICKS`、`SKILL_WAIT_SECONDS`
  - 角色数值：`JASMINE_MAX_BONUS`、`LULU_ACTIVE_RANGE`、`PADMAN_REFRESH_SECONDS`、`PARUNAN_PASSIVE_INTERVAL_SECONDS`、`HAND_FAN_BIG_RANGE`
  - 骰神赐福/诅咒之剑：`DICE_BLESSING_DURATION_SECONDS`、`DICE_BLESSING_DURATION_TICKS`、`CURSED_SWORD_BONUS_MAX`
  - 卡牌槽位：`CARD_SLOTS_PER_SIDE`、`CARD_SLOTS_TOTAL`、`MAX_CARD_COST`、`cardCostForStar(int)`
  - 战斗/治疗：`COMBAT_DAMAGE_CALC_INTERVAL_TICKS`、`SPELL_DAMAGE_CALC_INTERVAL_TICKS`、`HEALING_TIMER_TICKS`
- 迁移方式：主模组 common 新增 `component/AstralGameplayConstants`；174 个文件把 `starengine.component.GameplayConstants` 的 import 改成新类（只改使用内容字段的文件；135 个通用文件不动）。库 `GameplayConstants` 保留通用字段与 `applyConfig`，库内 8 个文件零改动。
- 配置 seam 的边界：`GameplayConfigValues`（26 行）由 5 个 `ModCommonConfig` 构造，`applyConfig` 共 10 个调用点；`GIVE_GUIDE_BOOK_ON_FIRST_JOIN` 只在主模组 10 个文件使用，但它是 record 的第一个字段。**建议本批先不动 record**，把 `GIVE_GUIDE_BOOK` 留在库配置 seam，避免破坏契约；以后单独一批再把该字段移到主模组（需改 5 个 config + record + 10 个调用点）。
- 影响：高，但机械；174 个 import 是主要 diff。

### 2.4 StarCoinWalletState（可选）

- 已在 §1.3 说明。若按“库只做契约”处理，搬回主模组：1 个库文件 + 15 个主模组 import；无 TLMDice 影响。**当前建议留库。**

## 3. 建议执行顺序

1. **批 A（低风险）— 已执行（`.96` / `.17`）**：effect 18 类搬回主模组 → 五线 assemble；主模组 CHANGELOG / 库 CHANGELOG；库版本 `.17`、主模组 `.96`。
2. **批 B（中低）— 已执行（`.96` / `.17`）**：Rarity + 五线 AstralRarities 搬回；改 10 条 `enumextensions.json`；25 + 30 import；五线 assemble；jar 内 json 与类路径校验。
3. **批 C（高）— 已执行（`.97` / `.18`）**：GameplayConstants 拆分；179 个文件改写（143 content + 36 mixed）；库 8 个文件不动；五线 assemble + jar 校验。
4. **已裁决**：StarCoinWalletState 留库（m02749），不再作为待办。

每批都应遵守：主模组/库各自 bump `mod_version`、写双语 CHANGELOG、五线 assemble 取证；TLMDice 当前只依赖 `StarEngineEconomy` 与 `api.astral`，A/B/C 都不需要改 TLMDice（除非库版本 pin 要跟随）。

## 4. 证据索引

- 审计主文档：`docs/features/api-audit/00-master-audit.md` §11（F1–F5）、§12（P0/P1）
- 设计文档：`docs/design/07-api-ownership.md`
- 库 effect（历史位置，已于 `.17` 搬出）：`starengine_next/common/src/main/java/com/merlinkitsune/starengine/effect/*.java`；迁移后：`astral_dice_multiloader-next/common/src/main/java/com/merlinkitsune/astral_dice/effect/*.java`
- 库 Rarity（历史位置，已于 `.17` 搬出）：`starengine_next/common/src/main/java/com/merlinkitsune/starengine/item/Rarity.java:43-57`；迁移后：`astral_dice_multiloader-next/common/src/main/java/com/merlinkitsune/astral_dice/item/Rarity.java:43-57`
- NeoForge 枚举扩展：`astral_dice_multiloader-next/neoforge-1.21.1/src/main/resources/META-INF/enumextensions.json:8`（`"class"` 已改指 `com/merlinkitsune/astral_dice/item/AstralRarities`）
- 库 GameplayConstants（C 批后仅剩通用 seam）：`starengine_next/common/src/main/java/com/merlinkitsune/starengine/component/GameplayConstants.java:17-53`
- 主模组内容常量（C 批新增）：`astral_dice_multiloader-next/common/src/main/java/com/merlinkitsune/astral_dice/component/AstralGameplayConstants.java`
- 库经济：`starengine_next/common/src/main/java/com/merlinkitsune/starengine/economy/{StarEngineEconomy,StarCoinCommand,StarCoinWalletState}.java`
- 库平台存储注册：`starengine_next/{fabric-1.20.1,fabric-1.21.1,forge-1.20.1,neoforge-1.21.1,neoforge-26.1.2}/src/main/java/com/merlinkitsune/starengine/economy/*EconomyStorage.java`

## 5. 裁决结果（2026-11-19）

1. `StarCoinWalletState`：**留库**（用户 m02749）。
2. `GameplayConstants`：**C 批已执行**（主模组 `.97` / 库 `.18`，见 §7）；用户 m02749 原裁决为「constants拆分，下一批做」。
3. `GIVE_GUIDE_BOOK_ON_FIRST_JOIN`：C 批仍留库，作为配置 seam；内容常量已搬出。
4. 本报告随 A/B 代码一起提交到 astral-next 的 `docs/design/`。

## 6. A/B 执行记录（26.4-SNAPSHOT.96 / 库 26.4-SNAPSHOT.17）

- 库侧：删除 `common/src/main/java/com/merlinkitsune/starengine/effect/` 下 18 个 `MobEffect` 子类、`common/.../item/Rarity.java`、五线 `*/src/main/java/com/merlinkitsune/starengine/item/AstralRarities.java`；旧引用 grep 为 0。
- 主模组：新增 `common/src/main/java/com/merlinkitsune/astral_dice/effect/` 18 类（package `com.merlinkitsune.astral_dice.effect`）、`common/.../astral_dice/item/Rarity.java`、五线 `*/src/main/java/com/merlinkitsune/astral_dice/item/AstralRarities.java`（package `com.merlinkitsune.astral_dice.item`）。
- 引用：45 个 Java 文件改写；删除 95 条同包冗余 import（每线 `ModEffects` 18 条 + `ChipReadyEffect` 1 条）；两份 NeoForge `META-INF/enumextensions.json` 各 5 条改指 `com/merlinkitsune/astral_dice/item/AstralRarities`；六条源码根旧 FQN 残留 0。
- 版本：库五线 `lib_version`/`mod_version` = `26.4-SNAPSHOT.17`；主模组五线 `mod_version` = `26.4-SNAPSHOT.96+<loader>`，`starengine_version` = `26.4-SNAPSHOT.17`，fabric 区间 `>=26.4-SNAPSHOT.17 <27.0.0`。
- 构建：库 `clean publishToMavenLocal --offline`（JDK 21）SUCCESSFUL，五份 `.17` jar 均无旧类；主模组五线 `clean assemble --offline` SUCCESSFUL（26 s，EXIT=0），五份 `.96` jar 经 `work/verify_ab.sh` 全部 `AB_VERIFY_OK`。
- 边界：未跑实机；NeoForge 枚举扩展只在类/路径层校验，仍需真实启动确认 `Rarity` 常量注入。`GameplayConstants`（C 批）与 `StarCoinWalletState` 未动。

## 7. C 执行记录（26.4-SNAPSHOT.97 / 库 26.4-SNAPSHOT.18）

- 用户裁决：m02891「继续做C」；m02749「constants拆分，下一批做」。
- 拆分：库 `GameplayConstants` 只保留 7 个通用字段 + `GIVE_GUIDE_BOOK_ON_FIRST_JOIN` 配置位；25 个内容字段与 `cardCostForStar(int)` 搬到主模组 `com.merlinkitsune.astral_dice.component.AstralGameplayConstants`，全部 `public static final`；`GameplayConfigValues` 的 7 字段 record 未动。
- 引用：179 个文件（143 content + 36 mixed），615 insertions / 579 deletions；content 文件改 import/FQN/简单名，mixed 文件保留库 import、新增新类 import 并只把内容符号改走新类；旧内容字段残留 0。
- 版本与构建：库 `.18` `clean compileJava publishToMavenLocal --offline` 16 s SUCCESSFUL；主模组 `.97` 五线 `clean assemble --offline` 26 s SUCCESSFUL（EXIT=0）。
- 产物验收：五份主模组 jar 均含 `AstralGameplayConstants.class`（25 个 `public static final int` 字段）、不含库侧 `GameplayConstants.class`，依赖区间均为 `.18`；库五份 jar 用 `javap` 确认 `GameplayConstants` 无内容字段与 `cardCostForStar`；`work/verify_c.sh` 返回 0。
- 边界：未跑实机；配置重载与 179 个改写引用只有编译/字节码取证。TLMDice 不引用这些类，未改。
