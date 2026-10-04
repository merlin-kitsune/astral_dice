# 26.1.2 完整移植 · 移植前审查与准备报告

> 生成时间：2026-09-21
> 基准线：`neoforge-1.21.1`（同为 NeoForge 端口，`21.1.235` / MC 1.21.1 / Java 21）
> 目标线：`neoforge-26.1.2`（`26.1.2.109` / MC 26.1.2 / **Java 25**）
> 工作树：`F:\MCProject\astral_dice_multiloader-next`（HEAD `418f2773`，工作区干净）
> 前置库：`F:\MCProject\starengine_lib`（HEAD `02da9d0`，已 bump `.15` 并发布 mavenLocal）
>
> **本报告仅为准备材料。移植操作需经用户确认后方可执行。**

---

## 0. 结论速览

| 项 | 数量 | 说明 |
|---|---|---|
| `.java` 总数 | 1.21.1 **242** / 26.1.2 **207** | 同名文件 197 个 |
| **仅 1.21.1 有**（需移植进来） | **45** | 该线未同步期间新增的全部功能 |
| **仅 26.1.2 有** | **10** | **4 删除** + **6 保留**（平台适配） |
| 同名但内容不同（需合并） | **123** | 逐字节相同 74 个 |
| **无需移植**（上游已修复） | 3 个文件 | `fixes/` 包 + fixs mixin（见 §2.4） |

**移植性质判定：这不是文件拷贝，是「45 个新文件 + 123 个文件的增量合并 + 10 个文件的取舍」。**

**关键前置结论（已实测验证）：**

1. ✅ 前置库 `.15` **已包含全部下沉项**，且 `starengine_lib-neoforge-26.1.2:1.0.0-SNAPSHOT.15` 制品已存在于 mavenLocal —— **本轮移植无需再 bump 库版本**，只需把 26.1.2 的 pin 从 `.11` 改为 `.15`。
2. ✅ NeoForge **26.1.2.109 已上游修复** `damageContainers` 泄漏（PR #3101 已 backport）—— `fixes/` 包与 `astral_dice.neoforge_fixes.mixins.json` **必须不移植**（详见 §2.4 证据）。
3. ⚠️ 26.1.2 的 `event/ChipSlotMigrationHandler.java` **已完整正确**（含 HIGHEST/LOWEST 两半），**原样保留、勿重建**。

---

## 1. 先决条件与版本对齐

### 1.1 版本号变更（`gradle.properties`）

| 键 | 当前值 | 目标值 | 原因 |
|---|---|---|---|
| `starengine_lib_version` | `1.0.0-SNAPSHOT.11` | `1.0.0-SNAPSHOT.15` | 移植功能依赖 `.15` 下沉符号 |
| `starengine_lib_version_range` | `[1.0.0-SNAPSHOT.11,2.0)` | `[1.0.0-SNAPSHOT.15,2.0)` | 下界随 bump 收紧 |
| `mod_version` | `1.2.1-beta.2+neoforge_26.1.2` | **待用户裁定** | 见下 |

**`mod_version` 对齐建议：** 1.21.1 与 1.20.1 均为 `2.0.0-SNAPSHOT.10`，26.1.2 停留在 `1.2.1-beta.2`。建议直接对齐为 `2.0.0-SNAPSHOT.10+neoforge_26.1.2`，理由是移植完成后三条线的**内容集合将首次趋于一致**，版本号继续分叉会让 CHANGELOG 的「同版本一一对应」约定失效。

> ⚠️ 此项会改变发布语义（1.2.1 → 2.0.0），**需用户明确确认**。备选方案：保留 `1.2.1-beta.3+neoforge_26.1.2`，只做内容移植不动发布号。

### 1.2 库版本 `.11 → .15` 的安全性（已核验）

- `.15` 的 26.1.2 制品已存在于 mavenLocal（`starengine_lib-neoforge-26.1.2-1.0.0-SNAPSHOT.15.jar`，51910 字节）。
- 该 jar 内含全部下沉类：`combat/{HostileTargets, InternalDamageWindows, PlayerHostilityTracker}`、`target/{SelectorTargets, SignSelectionGate, TargetType}`、`economy/{StarCoinWalletState, StarEngineEconomy, CommandPermissionGate}` 等。
- 26.1.2 当前**仅**引用了库的 `target.TargetType` 等旧符号（`TargetSelectionClient`、`TargetSelectionTestCommand`、各 `*SignItem`），**没有任何 `.11` 独有的符号会被 `.15` 移除** ⇒ bump 安全。
- ⚠️ 库版本不以 `-SNAPSHOT` 之外的语义标记，Gradle 不视作 changing module ⇒ **必须靠 bump 触发重新解析**（本机 `--refresh-dependencies` 因代理 TLS 失败不可用，已验证）。

---

## 2. 需要变更或重写的项目（逐项）

### 2.1 新增移植（45 个仅 1.21.1 存在的文件）

按子系统分组。⚠️ 全部需要**平台 API 复核**（见 §3 风险表），不是直接拷贝。

**A. 经济系统（新增整个 `economy/` 包 —— 26.1.2 当前完全没有）4 个**

| 文件 | 迁移注意 |
|---|---|
| `economy/StarCoinCurrency.java` | 面额口径，无平台 API 风险 |
| `economy/StarCoinBalanceSync.java` | 每 20 tick 轮询 + S2C 发包；需核对 26.1.2 的 `PacketDistributor` / `CustomPacketPayload` 签名 |
| `economy/StarCoinPickupHandler.java` | 拾取事件：neo `ItemEntityPickupEvent.Pre`；**26.1.2 需核实该事件仍存在** |
| `economy/StarCoinWalletActions.java` | C2S 意图处理，纯逻辑 |

**B. 立牌（6 个）**

`item/sign/{Hanna, Mamushi, Nardis, Sherry, Teru, Zhao}SignItem.java`
—— ⚠️ 依赖 `BaseSignItem` 的新增钩子（`addSignNoteLines` 备注区），必须先完成 `BaseSignItem` 的合并，否则编译失败。

**C. 效果（11 个）**

`effect/{DragonRoarBreak, HannaDollComplete, HannaDollCraft, HannaFloat, Huguang, MamushiDragon, Misfortune, NardisPrivilege, SherryReasoning, TeruDescent, ZhaoBlessing}Effect.java`
—— 需核对 26.1.2 的 `MobEffect` 构造与 `MobEffectCategory` 签名（库内 `HealingEffect` 已是同形，可作为参照）。

**D. 卡牌（5 个）**

`item/card/{DragonCardUtil, FuCardItem, HuoCardItem, TemporaryCardPermissiveSlot, TemporaryCardUtil}.java`
—— ⚠️ `TemporaryCardUtil` + `TemporaryCardPermissiveSlot` 是 **§2.6 容器守卫 mixin 的判据唯一入口**，二者与 mixin 必须同时移植。

**E. 战斗与目标（4 个）**

| 文件 | 迁移注意 |
|---|---|
| `combat/EliteTargets.java` | 唯一精英/Boss 入口；**import 需改为 `starenginelib.combat.HostileTargets`** |
| `combat/PlayerHostilityTrackerEvents.java` | **平台钩子（新文件）**，替换 26.1.2 将被删除的 `PlayerHostilityTracker` 内联事件 |
| `combat/SherryThrowManager.java` | 投掷物管理 |
| `combat/ShootingStarManager.java` | 依赖 `ShootingStarChipItem` |

**F. 客户端与 GUI（3 个）**

| 文件 | 迁移注意 |
|---|---|
| `client/ClientKeyNames.java` | **Finding 3 客户端类型隔离的落地类**，必须移植（否则 `ModTooltipHandler` 再次引入 client 类型） |
| `client/gui/StarCoinWalletButtons.java` | 钱包按钮注入；依赖 7 张贴图与 `StarCoinBalancePayload` |
| `mixin/client/EffectRenderingInventoryScreenMixin.java` | ⚠️ **不能直接移植** → 见 §2.2 |

**G. 网络（2 个）**：`network/StarCoinBalancePayload.java`、`network/StarCoinWalletPayload.java`

**H. 初始化与兼检（2 个）**：`init/ModCompatibilityCheck.java`（不兼容黑名单，启动即拒）、`event/IronSpellbooksCompat.java`（⚠️ 26.1.2 **未移植 Iron's Spells**，此文件及其调用点需决定是否随行）

**I. 其他（8 个）**：`datagen/DataGenerators.java`（⚠️ **反向操作**：26.1.2 已拆成两个类，1.21.1 的这个**不应移植**）、`effect/...`（已列）、`event/TemporaryCardEvents.java`、`item/chip/ShootingStarChipItem.java`、`network/...`（已列）

> 📌 **修正说明**：上表 I 项中 `datagen/DataGenerators.java` 属于「不要移植」项，实际移植数为 **44**。完整 45 名单以 §4 附录为准。

### 2.2 ⚠️ 需要「重写」而非「移植」的项目（最高风险）

| # | 项目 | 1.21.1 实现 | 26.1.2 必须改写为 |
|---|---|---|---|
| 1 | **物品模型 datagen** | `ModItemModelProvider extends ItemModelProvider`（NeoForge 类，构造器带 `ExistingFileHelper`，`registerModels()` 无参） | 该 NeoForge 类**已被删除**；26.1.2 版已改为 `extends ModelProvider`（原版）+ `registerModels(BlockModelGenerators, ItemModelGenerators)` + `generateFlatItem(item, ModelTemplates.FLAT_ITEM)`。**26.1.2 侧已写好，保留即可**；输出路径由 `models/item/*.json` 变为 `assets/astral_dice/items/*.json` |
| 2 | **mixin 重定向** | `EffectRenderingInventoryScreenMixin` → `@Mixin(EffectRenderingInventoryScreen.class)`，注入 `getEffectName` | Mojang 2026 GUI 重组：`getEffectName` **已移至新类 `EffectsInInventory`**，方法签名不变 ⇒ **26.1.2 侧已有的 `EffectsInInventoryMixin` 即正确版本，保留；1.21.1 的版本不要移植** |
| 3 | **轮廓捕获** | `TargetOutlineCapture` 走 `RenderLivingEvent.Post` + 自行重建基变换 | 26.1.2 的 `RenderLivingEvent.Post` **不再携带实体**（只有 `getRenderState()`），且 `EntityRenderState` 无通用实体 id ⇒ 必须走 `RegisterRenderStateModifiersEvent` + `registerEntityModifier(TypeToken, BiConsumer)` + `LivingEntityRenderer#submit` mixin（在 `poseStack.popPose()` 前注入）。**26.1.2 侧已实现（`OutlineCaptureRenderStateModifier` + `LivingEntityOutlineCaptureMixin`），保留；同名 `TargetOutlineCapture.java` 属于 123 个「同名不同内容」之一，需逐方法合并** |
| 4 | **datagen 事件拆分** | 单个 `DataGenerators.java` + `GatherDataEvent` + `includeClient()/includeServer()` | `GatherDataEvent` 已**抽象化**，拆为 `GatherDataEvent.Client` / `.Server` ⇒ 必须两个类、两次运行、**两个不同输出根**（`src/generated/resources` / `src/generated/clientResources`），否则 `HashCache#purgeStaleAndWrite` 会互删产物。**26.1.2 侧的两个类已正确，保留** |
| 5 | **容器守卫 mixin** | `mixin/container/{ContainerMoveGuardMixin, SlotPlaceGuardMixin}` | 目标方法 `AbstractContainerMenu#moveItemStackTo` / `Slot#mayPlace` 在 26.1.2 **仍需核实签名未变**（微改即需改描述符）。这是「临时牌不得离开玩家背包」硬约束的实现，**必须移植并验证** |
| 6 | **`AstralDiceMod` 客户端泄露** | 已在 Finding 3 修复：`registerScreens` 移至 `client/ModClientEvents` | ⚠️ 26.1.2 当前**仍有** `if (FMLEnvironment.getDist() == Dist.CLIENT) modEventBus.addListener(this::registerScreens);` + `import ...screen.CardInventoryScreen` ⇒ **按 Finding 3 同款方案修掉**（`getstatic` 的符号解析不受运行时 dist 分支保护） |

### 2.3 删除项（4 个仅 26.1.2 存在的旧副本）

这 4 个文件在 1.21.1 上**已被下沉到库中并删除**，26.1.2 因冻结而未同步：

| 文件 | 处置 |
|---|---|
| `combat/HostileTargets.java` (64 行) | **删除** → import 改为 `com.merlinkitsune.starenginelib.combat.HostileTargets` |
| `combat/PlayerHostilityTracker.java` (107 行) | **删除**（旧版含 4 个内联事件处理器）→ 改用库表 + 新增 `PlayerHostilityTrackerEvents` 平台钩子 |
| `target/SelectorTargets.java` (73 行) | **删除** → 库内同名类；⚠️ 其 javadoc 自称与 1.21.1 逐字一致，删除前需核对库版本是否等价 |
| `target/SignSelectionGate.java` (64 行) | **删除** → 库内同名类 |

> ⚠️ 这 4 处删除会产生 **30 个引用点需要改 import**（1.21.1 侧统计），其中 7 个文件在 26.1.2 上还没有，属于「新增即用新路径」，无需二次改写。

### 2.4 🚫 不移植项（上游已修复 —— 本次审查的关键发现）

**`fixes/` 包（2 个）+ `LivingEntityDamageContainerMixin` + `astral_dice.neoforge_fixes.mixins.json`**

1.21.1 存在一个为 NeoForge 21.1.x 打补丁的 `fixes/` 包：`fixes/DamageStackSanitizer.java`、`fixes/NeoForgeFixesLog.java`，配合 `mixin/fixes/LivingEntityDamageContainerMixin.java` 与一个 `"required": false` 的独立 mixin 配置。

**实测证据（直接对 userdev patch 取证）：**

```java
// NeoForge 21.1.235 —— patches/net/minecraft/world/entity/LivingEntity.java.patch
this.damageContainers.push(new DamageContainer(p_21016_, p_21017_));
if (CommonHooks.onEntityIncomingDamage(this, this.damageContainers.peek())) return false;   // ← 不 pop，泄漏
```

```java
// NeoForge 26.1.2.109 —— 同一文件
this.damageContainers.push(new DamageContainer(source, damage));
if (CommonHooks.onEntityIncomingDamage(this, this.damageContainers.peek())) {
    this.damageContainers.pop();      // ← PR #3101 已 backport，泄漏已修
    return false;
}
```

**结论：26.1.2 上该泄漏不存在 ⇒ `fixes/` 包与对应 mixin 配置一律不移植。**
若强行移植，`@Shadow` 的 `damageContainers` 仍能解析（字段仍在），但补丁退化为 no-op，纯属死代码，且平添一个 mixin 配置文件的维护负担。

### 2.5 mixin 配置差异（需新建/调整）

| 配置项 | 1.21.1 | 26.1.2 现状 | 动作 |
|---|---|---|---|
| `compatibilityLevel` | `JAVA_21` | `JAVA_25` | ✅ 已正确 |
| `mixins` 列表 | 含 `container.ContainerMoveGuardMixin`、`container.SlotPlaceGuardMixin` | **缺失** | ➕ 移植后需补两条 |
| `fixes` 配置 | 独立文件 `astral_dice.neoforge_fixes.mixins.json` | 不存在 | 🚫 **不新建**（见 §2.4） |
| `client` 列表 | `AstralUseItemGuardMixin`, `EffectRenderingInventoryScreenMixin`, `GuiMixin` | `AstralUseItemGuardMixin`, `EffectsInInventoryMixin`, `GuiMixin`, `LivingEntityOutlineCaptureMixin` | ✅ 已正确（`EffectsInInventoryMixin` 为 `EffectRenderingInventoryScreenMixin` 的 26.1.2 对应物） |
| Mixin 依赖 | — | `net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7`（`maven.fabricmc.net`） | ✅ 已正确 |

### 2.6 同名文件内容差异（123 个需合并）

74 个同名文件逐字节一致，直接跳过；**123 个需要逐方法合并**。高相似度文件（大部分骨架相同、累积增量大）：

| 文件 | 相似度 | 合并策略 |
|---|---|---|
| `DiceCombatEvents` | 0.945 | 增量合并；⚠️ 需补 `InternalDamageWindows.install(...)` 静态块 |
| `ModDataComponents` | 0.922 | 增量合并 |
| `TargetSelectionClient` | 0.916 | 增量合并 |
| `ModTooltipHandler` | 0.870 | 增量合并；⚠️ 需接 `client.ClientKeyNames` |
| `ModRecipeProvider` | 0.853 | 增量合并（注意 §3 第 4 项资源布局变化） |
| `BaseSignItem` | 0.846 | 增量合并（新增 `addSignNoteLines` 备注区） |
| `KeyBindingSetup` | 0.819 | 增量合并（注意 26.1.2 的 `KeyMapping` 注册时机） |
| `ModEffects` | 0.795 | 增量合并 |
| `ModItems` | 0.792 | 增量合并；⚠️ 新增物品须**短类名 + import** |
| `ModAttachments` | 0.733 | 增量合并 |
| `ModClientEvents` | 0.707 | 增量合并；⚠️ 需补 `registerScreens`（Finding 3 方案） |
| `TargetOutlineCapture` | — | **算法级重写**（见 §2.2 第 3 项） |

---

## 3. 平台 API 风险清单（移植时逐项验证）

| # | 风险点 | 影响面 | 验证方式 |
|---|---|---|---|
| 1 | **Java 21 → Java 25** | 全量 | toolchain 已配 25；注意库 `common/` 仍禁 Java 21+ 独有语法 |
| 2 | **Curios 9.5.1 → 15+** | 所有 `ICurioItem`、槽标签 | `curios_version_range=[15,)`；neo 9.5.1 走 JSON 槽 + `validators:["curios:tag"]` ⇒ **缺标签 = 无法装备**，需复核 15 语义 |
| 3 | **`GatherDataEvent` 抽象化** | datagen | 已拆分（26.1.2 侧已就绪） |
| 4 | **资源布局变化** | 模型/配方产物 | `models/item/` → `items/`；`generated/resources` + `generated/clientResources` 双根 |
| 5 | **`ItemModelProvider` 删除** | 模型 datagen | 已改用原版 `ModelProvider` |
| 6 | **`EffectsInInventory` 迁移** | 效果名 mixin | 已重定向 |
| 7 | **`RenderLivingEvent.Post` 减参** | 轮廓捕获 | 已改 `RegisterRenderStateModifiersEvent` |
| 8 | **`EventBusSubscriber` 签名收窄** | 全部事件类 | 26.1.2 只剩 `value()/modid()`；`RegisterMenuScreensEvent` / `RegisterRenderStateModifiersEvent` 现为 `IModBusEvent` |
| 9 | ~~**`ItemEntityPickupEvent.Pre`**~~ | `StarCoinPickupHandler` | ✅ **已核验：API 完全一致**（`TriState.FALSE` / `getPlayer()` / `getItemEntity()`），低风险 |
| 10 | **KubeJS `26.1.2-8.0.6`** | 测试脚手架 | 仅影响冒烟，本次不涉及 |
| 11 | **Iron's Spells 未移植** | `IronSpellbooksCompat` | 需决定：一并移植待用，或暂缓 |

### 3.1 已核验的 API 稳定性（降风险项）

对 NeoForge 26.1.2.109 sources jar 直接取证，以下 API **与 1.21.1 同形**，移植风险低：

| API | 核验结果 |
|---|---|
| `ItemEntityPickupEvent.Pre` | ✅ 存在；`getPlayer()` / `getItemEntity()` / `setCanPickup(TriState)` 签名一致（1.21.1 已用 `TriState.FALSE`） |
| `LivingDamageEvent.Pre` | ✅ 存在；`getSource()` / `getEntity()` / `getNewDamage()` / `setNewDamage(float)` 一致 |
| `LivingDamageEvent.Post` | ✅ 存在（新增 `getHealthDamage()` / `getBlockedDamage()` / `getPostAttackInvulnerabilityTicks()` 等，属扩展不属破坏） |
| `LivingDeathEvent` / `PlayerEvent` | ✅ 存在 |
| `RegisterRenderStateModifiersEvent` | ✅ 存在（`net.neoforged.neoforge.client.renderstate`） |
| `GatherDataEvent` | ✅ 存在（已抽象化，见 §2.2 第 4 项） |
| `RegisterMenuScreensEvent` | ✅ 存在（现为 `IModBusEvent`） |

> 📌 **重要含义**：`DiceCombatEvents`（123 个待合并文件中最大、相似度 0.945）的伤害链访问器**逐行同形**（两者都在 L150±/L189± 用同一套 `getSource/getEntity/getNewDamage/setNewDamage`）⇒ 该文件的合并是**纯增量**，不需 API 改写。

---

## 4. 执行顺序建议（待确认后执行）

1. **版本对齐**：`starengine_lib_version` → `.15`；`mod_version` 待裁定。
2. **删除 4 个旧副本**（§2.3），改 import 到 `starenginelib.*`。
3. **新增平台钩子**：`combat/PlayerHostilityTrackerEvents.java` + 在 `DiceCombatEvents` 静态块注入 `InternalDamageWindows.install(...)`。
4. **合并 123 个同名文件**（按 §2.6 优先序，`DiceCombatEvents` → `ModItems` → `BaseSignItem` → ...）。
5. **移植 44 个新文件**（§2.1 A–I，绕开 §2.2/§2.4 的「不移植」项）。
6. **mixin 配置补两条** `container.*`（§2.5）；**不新建** fixes 配置。
7. **修 `AstralDiceMod` 客户端类型泄露**（§2.2 第 6 项）。
8. **构建验证三件套**：`BUILD SUCCESSFUL` + jar 时间戳 + 开 jar 核符号；另加 `javap` 常量池核对（确认无 `net.minecraft.client.*` 泄露）。
9. **代码审查**（按要求**不做冒烟测试**）。

---

## 5. 附录：文件清单

### 5.1 仅 1.21.1 存在（45 个，移植候选）

```
client/ClientKeyNames.java
client/gui/StarCoinWalletButtons.java
combat/EliteTargets.java
combat/PlayerHostilityTrackerEvents.java
combat/SherryThrowManager.java
combat/ShootingStarManager.java
datagen/DataGenerators.java                      ← 🚫 不移植（26.1.2 已拆分）
economy/StarCoinBalanceSync.java
economy/StarCoinCurrency.java
economy/StarCoinPickupHandler.java
economy/StarCoinWalletActions.java
effect/DragonRoarBreakEffect.java
effect/HannaDollCompleteEffect.java
effect/HannaDollCraftEffect.java
effect/HannaFloatEffect.java
effect/HuguangEffect.java
effect/MamushiDragonEffect.java
effect/MisfortuneEffect.java
effect/NardisPrivilegeEffect.java
effect/SherryReasoningEffect.java
effect/TeruDescentEffect.java
effect/ZhaoBlessingEffect.java
event/IronSpellbooksCompat.java
event/TemporaryCardEvents.java
fixes/DamageStackSanitizer.java                  ← 🚫 不移植（上游已修）
fixes/NeoForgeFixesLog.java                      ← 🚫 不移植（上游已修）
init/ModCompatibilityCheck.java
item/card/DragonCardUtil.java
item/card/FuCardItem.java
item/card/HuoCardItem.java
item/card/TemporaryCardPermissiveSlot.java
item/card/TemporaryCardUtil.java
item/chip/ShootingStarChipItem.java
item/sign/HannaSignItem.java
item/sign/MamushiSignItem.java
item/sign/NardisSignItem.java
item/sign/SherrySignItem.java
item/sign/TeruSignItem.java
item/sign/ZhaoSignItem.java
mixin/client/EffectRenderingInventoryScreenMixin.java   ← 🚫 不移植（26.1.2 用 EffectsInInventoryMixin）
mixin/container/ContainerMoveGuardMixin.java
mixin/container/SlotPlaceGuardMixin.java
mixin/fixes/LivingEntityDamageContainerMixin.java       ← 🚫 不移植（上游已修）
network/StarCoinBalancePayload.java
network/StarCoinWalletPayload.java
```

**实际移植 = 45 − 5（4 个 🚫 + `datagen/DataGenerators`）= 40 个文件。**

### 5.2 仅 26.1.2 存在（10 个）

**保留（6 个，平台适配）**
```
client/OutlineCaptureRenderStateModifier.java
datagen/ClientDataGenerators.java
datagen/ServerDataGenerators.java
event/ChipSlotMigrationHandler.java      ← 已完整正确，原样保留
mixin/client/EffectsInInventoryMixin.java
mixin/client/LivingEntityOutlineCaptureMixin.java
```

**删除（4 个，已被库取代）**
```
combat/HostileTargets.java
combat/PlayerHostilityTracker.java
target/SelectorTargets.java
target/SignSelectionGate.java
```

---

## 6. 待用户裁定事项

1. **`mod_version`**：`1.2.1-beta.2` → 对齐 `2.0.0-SNAPSHOT.10`，还是保留 `1.2.1-beta.3`？
2. **`IronSpellbooksCompat`**：Iron's Spells 未移植，此文件随行（悬浮待用）还是暂缓？
3. **`EventBusSubscriber` 相关 helper**：1.21.1 存在的两个 helper event 在 26.1.2 已迁移，需确认无遗漏调用点。
4. **是否重建 `fixes/`**：本报告建议**不建**（上游已修），请确认接受。
