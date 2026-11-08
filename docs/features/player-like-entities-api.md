# 类玩家生物能力面（player-like entities API）

> 状态：2026-11-08 落地第一版（P0 = `neoforge-1.21.1`；其余四线的迁移映射见 §6）。
> 目标读者：本仓维护者 + 需要接入的附属模组（首个接入方 = TLMDice / 车万女仆）。

## 1. 三种实体

| 种类 | 判定 | 能力来源 |
|---|---|---|
| 原版玩家 | `entity instanceof Player` | 恒真，不受注册影响 |
| 注册的类玩家生物 | `PlayerLikeTargets.register(...)` 且 `Handler.matches(entity)` | 由注册方的 handler 能力位决定 |
| 未注册实体 | 其余全部 | 全假 ⇒ 所有路径退回原有「玩家专有」行为 |

注册面是**唯一入口**：附属自己不注册，本模组不会把任何生物当成玩家。

## 2. 能力矩阵（P0 当前状态）

| 能力 | 玩家 | 注册生物 | 未注册 | 说明 |
|---|---|---|---|---|
| 被「选玩家」技能选中 | ✅ | ✅ | ❌ | `PlayerLikeTargets.matchesSelection`，服务端确认与客户端两处过滤同源 |
| 收卡（主动发卡 / 你有我有） | ✅ | ✅ | ❌ | `PlayerLikeTargets.giveCard` → `Handler.giveCard` |
| 收牌触发「给牌」类被动（少主 A 面） | ✅ | ✅ | ❌ | 注册生物路径同样调 `AlSignItem.onCardGivenToOther` |
| 持有星光（attachment） | ✅ | ✅ | ⚠️ | `PLAYER_STARLIGHT` 无宿主谓词，但只有前两类会被业务读取 |
| 装备立牌（curios `stand`） | ✅ | ✅ | ❌ | 数据文件 `curios/entities/player_like_maid.json` 授予女仆槽位 |
| 装备筹码（curios `chip`） | ✅ | ✅ | ❌ | 同上（3 槽）；存量 33 个 `isEquipped(Player)` 仍只认玩家 |
| 立牌被动（击杀 / 受击钩子） | ✅ | ⚠️ 需立牌覆写 | ❌ | `invokeKillHooks/invokeHurtHooks(LivingEntity…)` 已通；各立牌需覆写 `onKillEntity/onHurtEntity` |
| 立牌主动 | ✅ | ⚠️ 需立牌覆写 | ❌ | `BaseSignItem#activateFor(LivingEntity)` → `activateForEntity`（默认 WARN 返回 false） |
| 筹码商店 | ✅ | ✅（有钱包时） | ❌ | `ChipShopService`，扣款走 `PlayerLikeWallet` |
| 筹码数值效果 | ✅ | ⚠️ 部分 | ❌ | `ChipEquip.equippedChips` 提供统一枚举，具体加成点按筹码逐个接 |
| 骰战参与者 | ✅ | ✅（既有） | ❌ | `DiceCombatParticipants`，TLMDice 已注册 |
| 卡牌使用 | ✅ | ⚠️ 需卡牌覆写 | ❌ | `CardUseService.useCardFor`，非玩家默认 WARN 返回 false |

## 3. 接口清单（P0 实际签名）

```java
// target/PlayerLikeTargets
public static void register(ResourceLocation id, Handler handler);
public interface Handler {
    boolean matches(LivingEntity entity);
    boolean giveCard(ServerPlayer giver, LivingEntity receiver, ItemStack card);
    default boolean giveItem(LivingEntity receiver, ItemStack stack) { return false; }
    default boolean supportsSigns() { return false; }
    default boolean supportsChips() { return false; }
    default boolean supportsShop()  { return false; }
}
public enum Capability { SIGNS, CHIPS, SHOP }
public static boolean isPlayerLike(LivingEntity entity);
public static boolean matchesSelection(TargetType type, Player selector, LivingEntity target);
public static Handler find(LivingEntity entity);
public static boolean supports(LivingEntity entity, Capability capability);
public static boolean giveCard(ServerPlayer giver, LivingEntity receiver, ItemStack card);
public static boolean giveItem(LivingEntity receiver, ItemStack stack);

// compat/playerlike/PlayerLikeWallets + PlayerLikeWallet
public interface PlayerLikeWallet {
    boolean isAvailable();
    long balance(LivingEntity holder);
    boolean withdraw(LivingEntity holder, int amount);
}
public static void register(ResourceLocation id, Predicate<LivingEntity> matches, PlayerLikeWallet wallet);
public static PlayerLikeWallet resolve(LivingEntity holder);   // 玩家恒走 StarEngineEconomy

// compat/playerlike/PlayerLikeAbilities（门面）
canEquipSigns / canEquipChips / canUseShop / hasWallet / wallet / giveCard / giveItem

// shop/ChipShopService（无界面商店）
public enum Result { OK, INVALID, NO_OFFER, NO_WALLET, NO_COINS, DELIVERY_FAILED }
public record Quote(int basePrice, int discount, int price, long balance, boolean affordable) {}
public static Quote quote(LivingEntity holder);
public static Result purchase(LivingEntity holder, int offerIndex);
public static boolean grantRefresh(LivingEntity holder);
public static void grantDiscount(LivingEntity holder, int amount, long durationTicks);

// shop/ChipShopState（宿主放宽）
public static ChipShopState get(LivingEntity holder);
public static void store(LivingEntity holder, ChipShopState state);

// item/chip/ChipEquip
public static boolean isEquipped(LivingEntity entity, Supplier<? extends Item> item);
public static List<ItemStack> equippedChips(LivingEntity entity);

// item/card/CardUseService
public static boolean useCardFor(LivingEntity user, ItemStack stack);
public static boolean useCardForEntity(LivingEntity user, ItemStack stack);  // 扩展点，默认 WARN

// item/sign/BaseSignItem
public static void invokeKillHooks(LivingEntity killer, LivingEntity killed);
public static void invokeHurtHooks(LivingEntity entity, DamageSource source, float amount);
protected void onKillEntity(LivingEntity killer, LivingEntity killed);   // 默认委派 onKill（Player）
protected void onHurtEntity(LivingEntity entity, DamageSource source, float amount);
public boolean activateFor(LivingEntity owner);
protected boolean activateForEntity(LivingEntity owner);                 // 默认 WARN false

// item/sign/SignHookDispatcher
public static void fireKillHooks(LivingEntity killer, LivingEntity killed);
public static void fireHurtHooks(LivingEntity entity, DamageSource source, float amount);
```

## 4. 附属接入示例（TLMDice）

```java
private static final ResourceLocation MAID_LIKE =
        ResourceLocation.fromNamespaceAndPath("touhou_little_maid_astra_dice", "maid");

// 模组构造期（FMLCommonSetupEvent 或 mod 构造器）
PlayerLikeTargets.register(MAID_LIKE, new PlayerLikeTargets.Handler() {
    @Override public boolean matches(LivingEntity e) { return e instanceof EntityMaid; }
    @Override public boolean giveCard(ServerPlayer giver, LivingEntity receiver, ItemStack card) {
        return ((EntityMaid) receiver).getMaidBackpackType() != null && insertIntoBackpack(receiver, card);
    }
    @Override public boolean giveItem(LivingEntity receiver, ItemStack stack) { return insertIntoBackpack(receiver, stack); }
    @Override public boolean supportsSigns() { return true; }
    @Override public boolean supportsChips() { return true; }
    @Override public boolean supportsShop()  { return true; }
});

// 钱包（从主人账户代扣，或女仆自有账户）
PlayerLikeWallets.register(MAID_LIKE, e -> e instanceof EntityMaid, new PlayerLikeWallet() {
    @Override public boolean isAvailable() { return true; }
    @Override public long balance(LivingEntity h) { return ownerWallet(h).balance(h); }
    @Override public boolean withdraw(LivingEntity h, int amount) { return ownerWallet(h).withdraw(h, amount); }
});
```

> 未注册 = 女仆保持「不是玩家」的旧行为；注册后能力位按需打开，**不会**因为注册就自动获得全部玩家语义。

## 5. 已知限制（本版刻意留口）

1. **立牌被动/主动、卡牌效果、筹码数值**：接口与分发已通，但每个具体实现仍需逐项覆写（需要从 `Player` 语义里拆出「谁持有、谁付费、谁吃增益」三件事）。批量迁移顺序见 §6.2。
2. **星币**：`StarEngineEconomy` 是玩家账户；注册生物的消费必须由附属提供 `PlayerLikeWallet`，本模组不代建账户。
3. **目标选择器的「敌对女仆」**：`SelectorTargets` 的敌对判定走关系表，B-10（带 viewer 的可叠加探针）未做。
4. **购买后重抽**：`ChipShopService` 只推进购买次数，换一批候选需要调用方按 UI 生命周期处理（`ChipShopState#withOffers`）。
5. **掉落语义**：非玩家交付失败时 `spawnAtLocation` 兜底；若附属希望进背包/容器，必须实现 `Handler.giveItem`。

## 6. 其余四线的迁移映射与步骤

### 6.1 逐线差异

| 线 | 附件 | Curios | 事件面 | 备注 |
|---|---|---|---|---|
| neoforge-1.21.1（P0，已完成） | `AttachmentType` | `CuriosApi.getCuriosInventory(LivingEntity)`（Curios 9） | neoforge 事件 | 本文件即此线实测口径 |
| forge-1.20.1 | `AstralData`（kept 白名单） | `top.theillusivec4.curios.api.CuriosApi`（Curios 5.x） | Forge 事件 | 无 `AttachmentType`；`ChipShopState` 存取改走 `AstralData` |
| neoforge-26.1.2 | `AttachmentType` | Curios 15（`ICurioStacksHandler` 已移除 `grow/shrink`） | neoforge 事件 | `setData/getData` 之外无差异；优先复制 P0 |
| fabric-1.20.1 / fabric-1.21.1 | 自建 `ItemDataKey` / `AttachmentSync` shim | **Trinkets**（槽位语义不同） | Fabric 事件 + `LoaderBus` | `CuriosApi` 调用点要换成 Trinkets 桥，槽位判定改 `TrinketsApi` |

### 6.2 迁移步骤（每条线同一套）

1. **复制 P0 的新增类**（无平台 API）：`compat/playerlike/{PlayerLikeWallet,PlayerLikeWallets,PlayerLikeAbilities}`、`shop/ChipShopService`、`item/chip/ChipEquip`、`item/card/CardUseService`。这些只依赖 `LivingEntity`/`ResourceLocation`/SLF4J，编译期零平台差异。
2. **`ChipShopState.get/store`**：把 `Player` 形参改 `LivingEntity`；forge 线同时把 `AstralData.get/set` 的调用形态对齐（kept 白名单里的键名不变）。
3. **`PlayerLikeTargets`**：整文件复制（P0 无平台依赖），确认 `TargetSelectionManager` 与 `TargetSelectionClient` 两处 `matchesSelection` 调用同样落地（客户端那处最容易漏）。
4. **立牌钩子**：`BaseSignItem` 的 `invokeKillHooks/invokeHurtHooks(LivingEntity…)` 与 `SignHookDispatcher` 重载整段复制；fabric 线 `CuriosApi` 换成 Trinkets 桥的「遍历装备」入口。
5. **商店服务**：`ChipShopService.deliver` 的玩家分支按各线物品栏 API 改（fabric 无 `Inventory#add` 差异，注意 `drop` 返回类型）。
6. **数据文件**：`data/astral_dice/curios/entities/player_like_maid.json` 直接复制；fabric 线改写到 `data/trinkets/entities/*.json`（`touhou_little_maid:maid` 的 `stand`/`chip` 槽）。
7. **门禁**：`./gradlew :<line>:compileJava` + 行尾（CRLF/bareLF=0）+ `check_lang_sync`；新增类不含 lang 键，无需 `verify_sign_display_names`。
8. **实机验收（需用户放行）**：注册/未注册对照、装备槽、收卡、星光、商店购买与刷新、立牌被动/主动、卡牌使用；断言全部锚机器行（`AP_PLAYER_LIKE*` / `AP_CHIP_SHOP_SERVICE`）。

### 6.3 需要逐项改写的实现清单（P1 排序）

| 顺序 | 面 | 文件 | 改法 |
|---|---|---|---|
| A | 立牌被动 | `AmeSignItem` / `LuluSignItem` / `KangelSignItem` / `BonnieSignItem` | 覆写 `onHurtEntity` / `onKillEntity`，内部把 `Player` 参数改为 `LivingEntity` + `Player` 分支保留 |
| B | 立牌主动 | 各 `*SignItem#handleUse` | 增 `activateForEntity`：选择器型立牌需要「无 ServerPlayer owner」的会话形态（未设计，暂不支持） |
| C | 卡牌 | `BaseEffectCardItem` 家族 | 把 `applyEffect(Player)` 拆成 `applyEffect(LivingEntity)`，玩家入口委派 |
| D | 筹码 | 33 个 `isEquipped(Player)` 家族 | 改调 `ChipEquip.isEquipped(LivingEntity, …)`；逐个核对加成是否依赖玩家属性面 |
| E | 骰战 | `DiceCombatEvents` 内 20+ 处 `instanceof Player` | 按 §5.1 已知限制逐条放宽（卡牌成本、成对结算、筹码加成） |

## 7. P1 同步清单（用户裁决 2026-11-08：P1 更新完成后，本批全部落点必须同步进 P1）

> 口径：P0（`neoforge-1.21.1`）是唯一先行线；**P1 侧正在进行的重构完成后**，下列提交必须逐条落到
> `forge-1.20.1` / `neoforge-26.1.2` / `fabric-1.20.1` / `fabric-1.21.1`，不得只在 P0 存在。

| 提交 | 主题 | P1 落地要点 |
|---|---|---|
| `090a8c5f` | 客户端相邻目标细边放行类玩家生物 | 客户端 `TargetSelectionClient` 两处过滤（准星 + 细边）都要同源改，漏一处会出现「能瞄准、选不中」 |
| `a8a2fcf6` | 选卡器 1~3 张变长下限 | `CardChoiceManager` / `CardChoiceMenu` / `CardChoiceType` 三处 + 三语键同步 |
| `34d2d3a3` | 类玩家能力层（wallet / abilities / ChipEquip / CardUseService） | 纯平台无关类，整目录复制；`PlayerLikeWallets` 的玩家默认钱包在各线同走 `StarEngineEconomy` |
| `831ec4d8` | 立牌钩子放宽 `LivingEntity` | Curios→Trinkets（fabric 两线）装备遍历入口需换成 Trinkets 桥；forge 线包名 `top.theillusivec4.curios` 一致可直接复制 |
| `03ab1b86` | 商店服务 + 状态宿主放宽 | forge 线 `ChipShopState.get/store` 走 `AstralData` 的 kept 白名单；fabric 两线走 `ItemDataKey` shim |
| `842b17a8` | 女仆槽位数据 | fabric 线改写为 `data/trinkets/entities/*.json`；其余线直接复制 `curios/entities/player_like_maid.json` |
| `83ac4ced` | 本文档 | 随代码一起进各线文档目录 |

**同步完成判据**：四线各跑 `compileJava`；`grep -rn "class PlayerLikeWallet\|class ChipShopService"` 每线各 1 处；
`docs/features/player-like-entities-api.md` 在四线都存在且 §7 改为「已同步」。

### 7.1 落地进度（2026-11-08）

| 线 | 状态 | 说明 |
|---|---|---|
| `neoforge-1.21.1`（P0） | 主体已落地 | `090a8c5f` / `a8a2fcf6` / `34d2d3a3` / `831ec4d8` / `03ab1b86` / `842b17a8` / `83ac4ced` 全在；G6 于 2026-11-08 落地（`e9a8856d` + `99fc9db1`） |
| `forge-1.20.1` | 已落地（本批） | `051fc865` 类玩家面 + 目标选择放行、`f6499965` 商店折扣/服务、`0c9d86de` 立牌钩子实体级化、`55bdf174` 女仆槽位数据、`2f926f4c` 选卡器 1~3 张、`7f14ed50` 少主立牌 al（含资源/三语/手册） |
| `neoforge-26.1.2` / `fabric-1.20.1` / `fabric-1.21.1` | 待 P1 重构后同步 | 仍按 §6.2 / §6.3 执行 |

`forge-1.20.1` 与 P0 的**有意差异**（不是遗漏，改写时保持）：

1. **给牌钩子中立化**：`target/PlayerLikeTargets#giveCard` 与 `VitaminPillChipItem#giveCard` 只触发
   `item/sign/CardGivenHooks`，不硬引用具体立牌；`AlSignItem` 通过 `CardGivenHooks.subscribe(...)`
   订阅（1.21.1 是漏斗内直调）。新增立牌只需订阅同一钩子，不必再动调用点。
2. **商店服务不重抽候选**：`shop/ChipShopService#purchase` 只扣款 + 入包 + `withPurchased`（无界面场景无货架可重抽）；
   `ChipShopMenu#handleBuy` 保持原有的「购买后重抽 + 冷却」行为，两者价格口径同源（`ChipShopDiscounts`）。
3. **`forge` 的非玩家持久化走 ForgeData**：`AttachedDataKey#store` 对 Player 用 `AstralData` capability、
   其他实体用 `holder.getPersistentData()`，因此星光 / 商店状态 / 折扣键对注册生物天然可用（无需新机制）。
4. **`Curios 5.x`**：`ChipEquip` 用 `getStacksHandler("chip")` + 逐槽比较（1.21.1 是 `findCurios`）；
   `ResourceLocation` 用 `new ResourceLocation(ns, path)`。

`forge-1.20.1` 仍欠（同步 P0 时一并补）：

- G6 已落地（P0 `e9a8856d` + `99fc9db1`；forge `930b6ce9` + `c53798e2`）：`DiceCombatContext.attacker`
  与筹码/费用入口放宽到 `LivingEntity`，注册参与者吃攻击修饰器与战斗牌、按同口径扣耐久；
  玩家专属加成由 `playerOrNull` 守卫跳过。
- P1 线 G6 已同步（2026-11-08）：`neoforge-26.1.2` `26a41640`、`fabric-1.21.1` `b0459993`、`fabric-1.20.1` `9e9dbdf9`，口径与 P0/forge 一致（`DiceCombatContext.attacker` → `LivingEntity`、筹码/费用入口放宽、注册参与者吃攻击修饰器与战斗牌、防御侧掷防御牌；玩家专属加成由 `playerOrNull` 守卫跳过）。fabric 两线仍**未做**类玩家能力面（`PlayerLikeTargets` 等只存在于 P0/forge）。

`forge-1.20.1` AL 落地的三处平台差异（与 P0 不同、已验证可编译）：

- 属性修饰符枚举是 `AttributeModifier.Operation.ADDITION`（1.21.1 是 `ADD_VALUE`）；
- `StarEngineEconomy.deposit` 无 `level() instanceof ServerLevel` 守卫（1.20.1 直接调用）；
- 「赠刷新」放宽到任意 `LivingEntity`（本线商店状态非玩家走 ForgeData），P0 仍只对玩家。
