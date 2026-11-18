# AstralDiceApi 缺口补齐（TLMDice 重写前置）

> 状态：2026-11-18 设计稿（待批准后进入实现计划）。
> 目标读者：本仓维护者 + TLMDice 重写执行者。
> 触发：TLMDice 需在 `.78` 之上**完全重写**，且只能走 `AstralDiceApi`；现有 9 个内部类引用必须全部收口。
> 后续：2026-11-18 已做**全功能面覆盖审计**（122 项功能面，缺 74 项），见 [api-audit/00-master-audit.md](api-audit/00-master-audit.md)；本文只覆盖 TLMDice 重写所需的那批面。
> 裁决记录（2026-11-18 用户）：
> 1. 敌对探针**要**入门面，common 侧直连 starengine，把 TLMDice 的类初始化 hack 删干净；
> 2. TLMDice **完全删除商店意图**，`ChipShopService` 从 TLMDice 消失，远程商店后续单独设计；
> 3. 女仆能力位 = **立牌 + 筹码 + 钱包**，无商店。

## 1. 前置事实：门面能不能直连内部类

`common/` **不是** Gradle 子项目（`settings.gradle` 只 include 五个加载器线），它是被五条线 `sourceSets.main.java.srcDir` 共享的源码根：

| 线 | 接线位置 |
|---|---|
| neoforge-1.21.1 | `neoforge-1.21.1/build.gradle:15` |
| forge-1.20.1 | `forge-1.20.1/build.gradle:14` |
| fabric-1.20.1 | `fabric-1.20.1/build.gradle:33` |
| fabric-1.21.1 | `fabric-1.21.1/build.gradle:28` |
| neoforge-26.1.2 | `neoforge-26.1.2/build.gradle:16` |

⇒ `common/api/*` 里写的任何调用，必须**五条线都能编译**。实测九个内部类的存在性：

| 内部类 | neoforge-1.21.1 | forge-1.20.1 | neoforge-26.1.2 | fabric-1.20.1 | fabric-1.21.1 |
|---|---|---|---|---|---|
| `target/PlayerLikeTargets` | Y | Y | Y | Y | Y |
| `item/dice/DiceCurioItem` | Y | Y | Y | Y | Y |
| `item/sign/BaseSignItem` | Y | Y | Y | Y | Y |
| `effect/ModEffects` | Y | Y | Y | Y | Y |
| `combat/DiceCombatParticipants` | Y | Y | Y | Y | Y |
| `combat/DiceCombatEvents` | Y | Y | Y | Y | Y |
| `compat/playerlike/PlayerLikeWallet` | Y | Y | Y | **n** | **n** |
| `compat/playerlike/PlayerLikeWallets` | Y | Y | Y | **n** | **n** |
| `shop/ChipShopService` | Y | Y | Y | **n** | **n** |

结论：**前六个可直连**（不加安装缝、不加桥），**钱包必须由 api 自持注册表**（不能引用缺类的实现），**商店不入面**。

## 2. 新增公开面总表

| # | 内部类 | 门面入口 | 实现方式 |
|---|---|---|---|
| 1 | `PlayerLikeTargets` | `registerPlayerLike` / `isPlayerLike` / `matchesPlayerSelection` / `supportsPlayerLikeCapability` / `giveCardTo` / `giveItemTo` | 直连 + `api/playerlike` 两个公共类型 |
| 2 | `DiceCurioItem` | `isDiceItem` / `diceStarLevel` / `diceCardSlots` / `refreshDiceChipSlots` | 直连 |
| 3 | `BaseSignItem` | `isSignItem` / `activateSign` | 直连 |
| 4 | `ModEffects.DICE_BLESSING` | `hasDiceBlessing` | 每线 `ModEffects` 增一个方法（五线类型不同） |
| 5 | `DiceCombatParticipants` | `registerDiceCombatParticipant` / `isDiceCombatParticipant` / `clearDiceCombatParticipantsForTests` | 直连 |
| 6 | `PlayerLikeWallets` + `PlayerLikeWallet` | `registerPlayerLikeWallet` / `playerLikeWallet` / `playerLikeWalletAvailable` / `playerLikeWalletBalance` / `playerLikeWalletWithdraw` / `playerLikeWalletDeposit` | **api 自持注册表**；P0 三线的 `PlayerLikeWallets.resolve` 委托 api |
| 7 | `HostileTargets` seam | `registerHostileProbe` / `isHostileTarget` / `isHostileTarget(viewer, target)` | api 持**复合**探针，只装一次 |

## 3. 逐项设计

### 3.1 类玩家（`api/playerlike/`）

新增两个公共类型（不含任何加载器依赖）：

```java
package com.merlinkitsune.astral_dice.api.playerlike;

public interface PlayerLikeHandler {
    boolean matches(LivingEntity entity);
    boolean giveCard(ServerPlayer giver, LivingEntity receiver, ItemStack card);
    default boolean giveItem(LivingEntity receiver, ItemStack stack) { return false; }
    default boolean supportsSigns() { return false; }
    default boolean supportsChips() { return false; }
}
```

- **没有** `supportsShop()`：女仆不带商店能力位，适配层恒返回 `false`（见 §5）。
- `enum PlayerLikeCapability { SIGNS, CHIPS }` —— 与内部 `PlayerLikeTargets.Capability` 一一对应，`SHOP` 不暴露。

门面方法（`AstralDiceApi` 内）：

```java
public static void registerPlayerLike(ResourceLocation id, PlayerLikeHandler handler);
public static boolean isPlayerLike(LivingEntity entity);
public static boolean matchesPlayerSelection(TargetType type, Player selector, LivingEntity target);
public static boolean supportsPlayerLikeCapability(LivingEntity entity, PlayerLikeCapability capability);
public static boolean giveCardTo(ServerPlayer giver, LivingEntity receiver, ItemStack card);
public static boolean giveItemTo(LivingEntity receiver, ItemStack stack);
```

实现要点（对应 `neoforge-1.21.1/.../target/PlayerLikeTargets.java`）：

- `registerPlayerLike` 内部包一层 `PlayerLikeTargets.Handler` 适配器：`matches` / `giveCard` / `giveItem` 直转发；`supportsSigns` / `supportsChips` 按枚举映射；`supportsShop` 恒 `false`。
- `giveCardTo` 与 `giveItemTo` 直接转发 —— 注册生物的收卡路径原本就会补调 `AlSignItem.onCardGivenToOther`（`PlayerLikeTargets.java:123`），这一点门面不得绕过。
- `matchesPlayerSelection` 的 `TargetType` 取 `com.merlinkitsune.starengine.target.TargetType`（`PlayerLikeTargets.java:4` 已如此）。类型作参数属契约允许范围。
- `registerPlayerLike` **必须在内容冻结前**调用；同 id 重复注册仍按内部规则「后者覆盖」并保留 `AP_PLAYER_LIKE: registered id=` 日志。

### 3.2 骰子物品

```java
public static boolean isDiceItem(ItemStack stack);        // DiceCurioItem.java:40
public static int diceStarLevel(ItemStack stack);         // DiceCurioItem.java:50
public static int diceCardSlots(ItemStack stack);         // DiceCurioItem.java:57
public static void refreshDiceChipSlots(LivingEntity entity); // DiceCurioItem.java:132
```

- `refreshDiceChipSlots` 是**副作用**接口：女仆装备骰子后必须调用才能重算筹码格数（TLMDice `MaidChipSlotEvents` 现用途）。
- 玩家专用重载（`refreshChipSlotCount(Player)`、`enforceNoDiceState`、`removeGlassDiceOnDeath`）**不入门面**：属主模组内部生命周期，附属无需驱动。

### 3.3 立牌主动

```java
public static boolean isSignItem(ItemStack stack);
public static boolean activateSign(LivingEntity owner, ItemStack sign);
```

- 实现：`sign.getItem() instanceof BaseSignItem item ? item.activateFor(owner) : false`，直连 `BaseSignItem.java:560`。
- ⚠️ **玩家调用恒返回 `false`**（`activateFor` 对 `Player` 直接早退，`BaseSignItem.java:560-566`）：玩家主动技能走原版交互路径。门面在 javadoc 里写明，避免附属误用。
- 未覆写 `activateForEntity` 的立牌仍按 `BaseSignItem.java:567` 打 `[Astral Dice][SignSkill] activateForEntity 未被覆写: owner=` 并返回 `false` —— 门面不改这个语义。

### 3.4 赐福效果

两边字段类型不同（neo `ModEffects.java:63` `DeferredHolder<MobEffect, MobEffect>`；forge `ModEffects.java:60` `RegistryObject<MobEffect>`），故**每线的 `ModEffects` 各增一个统一方法**：

```java
public static boolean hasDiceBlessing(LivingEntity entity); // = entity.hasEffect(DICE_BLESSING)
```

门面 `public static boolean hasDiceBlessing(LivingEntity entity)` 只转调它。这样 common 代码不接触 holder 类型，五线各自编译通过。

### 3.5 骰战参与者

```java
public static void registerDiceCombatParticipant(String owner, Predicate<LivingEntity> predicate);
public static boolean isDiceCombatParticipant(LivingEntity entity);
public static void clearDiceCombatParticipantsForTests();
```

- 直连 `DiceCombatParticipants.registerExtraAttacker(String, Predicate)`（幂等、`CopyOnWriteArrayList`）。
- `String owner` 保留原形参名语义：只作日志/调试归属，不参与判定（`DiceCombatParticipants: extra attacker predicate registered by <owner>`）。
- 回归红线照旧：**未注册 ⇒ 恒 `false` ⇒ 原版伤害照常**；「不结算 ≠ 不掉血」。

### 3.6 类玩家钱包（api 自持注册表）

fabric 两线没有 `compat/playerlike/*`，所以门面**不能**引用它。改为 api 内自持：

```java
package com.merlinkitsune.astral_dice.api.playerlike;

public interface PlayerLikeWallet {          // 公共契约，与内部同名接口语义一致
    boolean isAvailable(LivingEntity holder);
    long balance(LivingEntity holder);
    boolean withdraw(LivingEntity holder, int amount);
    default boolean deposit(LivingEntity holder, int amount) { return false; }
}
```

`AstralDiceApi` 内新增（私有 `CopyOnWriteArrayList<Registration>`，`Registration = record(ResourceLocation id, Predicate<LivingEntity> matches, PlayerLikeWallet wallet)`）：

```java
public static void registerPlayerLikeWallet(ResourceLocation id, Predicate<LivingEntity> matches, PlayerLikeWallet wallet);
public static PlayerLikeWallet playerLikeWallet(LivingEntity holder);      // 未命中返回 null
public static boolean playerLikeWalletAvailable(LivingEntity holder);
public static long playerLikeWalletBalance(LivingEntity holder);           // 未命中 0
public static boolean playerLikeWalletWithdraw(LivingEntity holder, int amount);
public static boolean playerLikeWalletDeposit(LivingEntity holder, int amount);
```

**P0 三线接线**（neoforge-1.21.1 / forge-1.20.1 / neoforge-26.1.2 各一处）：`PlayerLikeWallets.resolve(LivingEntity)` 在遍历内部注册表**之后**追加一次 api 查询，命中即返回适配器 —— 保证主模组既有内置注册（以及未来可能的内部实现）优先级不变，附属钱包只作兜底。

**fabric 两线**：无内部消费者，门面查询仍可用（api 注册表本身五线都有），只是「主模组内部路径看不见附属钱包」。这是既有 P1 缺口的延续，记入 `player-like-entities-api.md` 的缺口表，不在本轮扩大。

> 与既有 `registerWallet(...)`（→ StarEngine `WalletRegistry` / `EntityWallet`）是**两套东西**：那一套是「谁有经济账户」，这一套是「类玩家实体的钱包怎么扣/存」。门面必须两套名字都可区分，不能合并。

### 3.7 敌对探针（`registerHostileProbe`）

**坑**：starengine 的 seam 是**单值、后装覆盖**——

- `starengine_next/common/src/main/java/com/merlinkitsune/starengine/combat/HostileTargets.java:105` `private static volatile ExtraHostileProbe extraHostileProbe = target -> false;`
- `:112-114` `installExtraHostileProbe`，注释原文「重复调用以最后一次为准」。
- `:175` 判定链末尾 `return extraHostileProbe.isHostile(entity);`

主模组自己的 `DiceCombatEvents.java:129-130` 正往里装 `DiceCombatEvents::isTrainingDummy`。若门面把 `registerHostileProbe` 直连转发，**第二个调用者会把试验假人判定顶掉**（假人不再算敌对目标 ⇒ 立牌/筹码选敌范围静默缩水）。这正是 TLMDice 现在要调 `isTrainingDummy(null)` 逼类初始化的根因。

**定案**：api 持复合探针，只装一次。

```java
private static final List<Predicate<Entity>> HOSTILE_PROBES = new CopyOnWriteArrayList<>();

public static void registerHostileProbe(Predicate<Entity> probe);   // null 忽略
public static boolean isHostileTarget(Entity target);               // HostileTargets.isHostile(target)
public static boolean isHostileTarget(Entity viewer, Entity target); // HostileTargets.isHostile(viewer, target)
```

- 复合体在 api 静态初始化时装进 starengine：`HostileTargets.installExtraHostileProbe(t -> { for (Predicate<Entity> p : HOSTILE_PROBES) if (p.test(t)) return true; return false; })`。
- 主模组 `DiceCombatEvents` 静态块（`:121-131`）改为**向 api 注册**而不是直接 install：`AstralDiceApi.registerHostileProbe(DiceCombatEvents::isTrainingDummy)`。
- 顺序从此无关：谁先谁后都能共存；TLMDice 的 `MaidHostilityProbe` 只留一个注册调用，删掉逼初始化与手工组合判定。
- 判定链语义不变：原版三通道（`Enemy` / `NeutralMob`(非宠物) / 可激怒可驯服动物）仍是早退，复合探针只在末尾附加。

## 4. 迁移对照（TLMDice 现有引用 → 门面）

| TLMDice 文件 | 现用内部面 | 改用 |
|---|---|---|
| `compat/MaidPlayerLikeHandler` | `PlayerLikeTargets.Handler` / `register` | `api.playerlike.PlayerLikeHandler` / `registerPlayerLike` |
| `intent/MaidTransferIntent` | `PlayerLikeTargets.giveCard/giveItem` | `giveCardTo` / `giveItemTo` |
| `command/MaidAstraCommand` | `PlayerLikeTargets.isPlayerLike` | `isPlayerLike` |
| `data/MaidDiceEquipment`、`client/MaidDiceTooltipEvents` | `DiceCurioItem.isDiceItem` | `isDiceItem` |
| `compat/MaidChipSlotEvents` | `DiceCurioItem.refreshChipSlotCountForEntity` | `refreshDiceChipSlots` |
| `intent/MaidActiveSkillIntent` | `BaseSignItem.activateFor` | `activateSign` |
| `combat/MaidDiceCombatEvents` | `ModEffects.DICE_BLESSING` | `hasDiceBlessing` |
| `compat/MaidWallet` | `PlayerLikeWallet` / `PlayerLikeWallets.register` | `api.playerlike.PlayerLikeWallet` / `registerPlayerLikeWallet` |
| `combat/MaidDiceParticipants` | `DiceCombatParticipants.registerExtraAttacker` | `registerDiceCombatParticipant` |
| `relation/MaidHostilityProbe` | `DiceCombatEvents.isTrainingDummy(null)` + `installExtraHostileProbe` | `registerHostileProbe`（无逼初始化） |
| `intent/MaidShopIntent` | `ChipShopService` | **整文件删除** |

验收门禁：TLMDice 源码里 `import com.merlinkitsune.astral_dice.` 下除 `.api.` 之外**零命中**。

## 5. 不做的部分（明确划界）

- **商店 / 筹码购买**：`ChipShopService` 五线缺 fabric，且用户已裁决 TLMDice 不用商店 ⇒ 不入门面、不补 fabric、不在本轮讨论远程商店设计。
- **`supportsShop` 能力位**：门面 `PlayerLikeHandler` 不含；适配层恒 `false`。主模组内部 `PlayerLikeTargets.Handler.supportsShop` 保留不动（其他宿主可能用）。
- **内部类不删**：`PlayerLikeTargets` / `DiceCurioItem` / `BaseSignItem` / `ModEffects` / `DiceCombatParticipants` / `PlayerLikeWallets` 全部保留为内部实现层。门面是**唯一对外入口**，不是内部实现的替代品（`docs/agent/AGENTS-full.md` L135-143 的强制 API 规范不变）。
- **`AstralDicePlatform.install()` 保持空实现**：本轮所有新增面都不需要加载器差异装配（类型差异已在 §3.4 用每线方法吃掉），不引入新安装缝。

## 6. 时机与冻结

- `freezeContent()` 只冻结内容四表（`DiceTierRegistry` / `DiceTiers` / `Signs` / `Chips`）；§3.1 / §3.5 / §3.6 / §3.7 的注册表**不受冻结影响**，但约定仍在 Setup / mod 构造期注册（与 `docs/features/tlmdice-participant-support.md` §7 一致）。
- `registerHostileProbe` 允许运行期追加（读多写少、`CopyOnWriteArrayList`），但探针必须是纯判定、不得抛异常：抛异常的探针在复合体内 `try/catch` 吞掉并记 debug（与 `DiceCombatParticipants.isParticipant` 同口径）。

## 7. 验证计划

1. 五线 `assemble`（`--offline`）全绿；重点看 fabric 两线（无钱包实现类，靠 api 自持注册表编译）。
2. 门禁 grep：TLMDice 侧零 `.api.` 之外引用（见 §4 末）。
3. KubeJS 探针（沿用 `scripts/test/resources/kubejs/.../astral_tlmdice_probe.js` 骨架）追加读数：
   - `playerLike`：注册后 `isPlayerLike` 为真、未注册实体为假；
   - `hostileProbe`：注册后 `isHostileTarget(被探针命中的实体)` 为真，**且**试验假人仍为真（验证复合不覆盖）；
   - `wallet`：`playerLikeWalletBalance/Withdraw/Deposit` 走附属实现；未注册实体返回 0/false；
   - 既有读数不得回退：`signs=36;chips=70;diceTiers=13;frozen=true;categorySupported=true;statSupported=true`。
4. 文档同步：`docs/features/player-like-entities-api.md` 的接口清单改成「门面清单 + 内部实现保留说明」；缺口表补 fabric 钱包条目。

## 8. 版本纪律

本轮**改产品代码** ⇒ 按 `~/.dsh/AGENTS.md` 与仓内规则：

- 五线 `mod_version` 统一 bump 到 `26.4-SNAPSHOT.79`（`neoforge-1.21.1/gradle.properties`、`forge-1.20.1/gradle.properties`、`neoforge-26.1.2/gradle.properties`、`fabric-1.20.1/gradle.properties`、`fabric-1.21.1/gradle.properties` 各自的 `mod_version`；根 `gradle.properties` 的 astral 版本号同步）。
- `CHANGELOG.md` / `CHANGELOG_ZH.md` 写 `## 26.4-SNAPSHOT.79` 段：门面新增面逐条、复合敌对探针、fabric 钱包缺口声明。
- 本轮**不**改 StarEngine 版本（`HostileTargets` 1.2.0-pre.1 已有，无需新 API）。
