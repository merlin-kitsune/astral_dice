# B 组 API 化审计：物品 / 卡牌 / 骰子 / 筹码 / 立牌 / 界面

> 基线：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`（下表简写省略该前缀）；`common/` 开头者指 `common/src/main/java/com/merlinkitsune/astral_dice/`。分支 `multi-dev-next`，审计日 2026-11-18。
> 口径：只通过 `AstralDiceApi` 现有 public static 方法（或 `api/` 包公开类型）能完成 = 已覆盖；必须 import 内部包 = 缺口。
> ⚠ 标记 = 该类在部分加载器线不存在（实测 glob），门面若要直连，必须先在缺线补同签名类，否则 `common/api` 编译不过。

**结论：B 组共 40 项功能面，已覆盖 6 项、部分覆盖 4 项、缺失 30 项（P0 3 / P1 15 / P2 12）。** 最痛的是卡牌闭环四块——对生物用卡、卡牌↔骰子装配、骰子卡牌负载读写、装配结果写回——addon 现在只能反射或照抄 `CardInventoryMenu` 的私有逻辑；其次是「注册面有、消费面不认」：`registerSign` / `registerChip` 写进 StarEngine 的 `Signs` / `Chips` 表，而本线全源码对这两个表**零引用**，注册了也不生效。

| 功能面 | 证据（文件:行） | 现状（内部可达性） | 门面覆盖 | 建议 API 签名 | 优先级 |
|---|---|---|---|---|---|
| **卡牌** 物品类型判定（任意/战斗/效果） | `item/ModItems.java:1251` `isCardItem`、`:1256` `isCombatCard`、`:1261` `isEffectCard`（标签判定） | 内部 public static，addon 必须 import `item.ModItems` | 无 | `boolean isCardItem(ItemStack)`、`boolean isCombatCard(ItemStack)`、`boolean isEffectCard(ItemStack)`（直连 ModItems，五线皆有） | P1 |
| 按 id 取卡牌 | `item/card/BaseEffectCardItem.java:115` `cardByTypeId`（switch 19 分支，default 静默回退 `EFFECT_CARD_KING_POWER`） | 内部 public static；无 id 全集，未命中给错牌 | 无 | `List<String> cardTypeIds()` + `ItemStack cardById(String typeId)`（未命中返回 `ItemStack.EMPTY`，不保留 default 回退） | P1 |
| 读卡牌 typeId | `item/card/CardItem.java:10` `private final String cardType`（无 getter）；`item/card/BaseEffectCardItem.java:94` `protected abstract cardTypeId()` | 只能 instanceof 内部类，或猜物品注册路径 | 无 | `@Nullable String cardTypeId(ItemStack)`（每线在 BaseEffectCardItem 加 public static，门面直连） | P1 |
| 卡牌分类（伤害/治疗/FX/selector） | `item/card/BaseEffectCardItem.java:102` `isDamageEffectCard`（硬编码 5 个 ModItems）、`:165` `isHealingCard`（protected，无 static 等价物）、`:598` `isDamageFxCard`、`:271` `isEffectCardAction` | 前两个可见但类在内部包；治疗判定无 static 版本 | 无 | `boolean isDamageCard(ItemStack)`、`boolean isHealingCard(ItemStack)`、`boolean isCardAction(ItemStack)` | P2 |
| 卡池枚举 | `item/card/EffectCardUtil.java:21` `getRandomEffectCardPool` | 只有随机池，不是按类别枚举 | 无 | `List<ItemStack> effectCardPool()` | P1 |
| 对生物/玩家使用卡牌 | `item/card/CardUseService.java:25` `useCardFor`；`:40` `useCardForEntity` 是 **static 桩**（只 WARN + return false，无调用者/覆写者）⚠ 该类仅 neo-1.21.1 / forge-1.20.1 有 | 玩家分支可反射或复制 `stack.use(...)`；非玩家分支是死通道 | 无（`giveCardTo` 只给牌不结算） | `boolean useCardOn(LivingEntity user, ItemStack card)`；门面玩家路径直接 `card.use(level, player, InteractionHand.MAIN_HAND).consumesAction()`，非玩家走新增 `AstralDicePlatform#useCardOnEntity(LivingEntity, ItemStack)`（default false） | **P0** |
| 卡牌装配进骰子 | `item/card/CardInventoryMenu.java:302` `saveToDice()` private、`:256` `findEquippedDice()` private；装配即销毁卡牌物品（注释 `:337-338`） | 必须开 GUI 才触发，addon 无法编程装配 | 无 | `boolean equipCard(ItemStack dice, ItemStack card)`；实现 = 五线新增 `item/card/DiceCardLoadout#equip(ItemStack, ItemStack, Player)` 服务类，门面直连 | **P0** |
| 读骰子已装卡列表 | `item/card/CardInventoryMenu.java:263` `loadFromDice()` private；`component/AppliedStone.java:29` | 只能读 `WeaponEnhancement.appliedStones()`（内部组件 + 内部记录类型） | 无 | `DiceLoadout diceLoadout(ItemStack dice)`；`record DiceLoadout(int starLevel, int cardSlots, int usedAttackCost, int maxAttackCost, int usedDefenseCost, int maxDefenseCost, List<InstalledCard> cards)` | **P0** |
| 卡牌耐久/次数读写 | `component/ModDataComponents.java:32` `CARD_USES`；`item/card/CardItem.java:20` 用其算耐久条；`component/AppliedStone.java:79` `defaultUses` | 组件 key 是 DeferredHolder，addon 要拿内部注册表对象 | 无 | `int cardUses(ItemStack)`、`void setCardUses(ItemStack, int)` | P1 |
| 临时卡 / 专属卡标记 | `item/card/TemporaryCardUtil.java:108/114/160/189`；`item/card/ExclusiveCardUtil.java:20/38/44` | 内部工具（五线皆有），addon 可反射但需内部类型 | 无 | `boolean isTemporaryCard(ItemStack)`、`void markTemporaryCard(ItemStack, @Nullable UUID owner)`、`boolean isExclusiveCard(ItemStack)`、`void bindExclusiveOwner(ItemStack, Player)` | P1 |
| 卡牌归还玩家 | `item/CardReturnHelper.java:28` `returnCards(Player, List<AppliedStone>)` | 参数是内部组件类型，addon 构造不出 | 无 | `int returnCards(Player, List<InstalledCard>)` | P1 |
| **骰子** 物品判定 | `item/dice/DiceCurioItem.java:40`；门面 `api/AstralDiceApi.java:394` | 已封装 | ✅ `isDiceItem` | — | — |
| 星级读取 | `item/dice/DiceCurioItem.java:50` `configStarLevel`；`api/AstralDiceApi.java:399` | 已封装 | ✅ `diceStarLevel` | — | — |
| 卡槽数读取 | `item/dice/DiceCurioItem.java:57`；`api/AstralDiceApi.java:404` | 已封装 | ✅ `diceCardSlots` | — | — |
| 筹码槽刷新 | `item/dice/DiceCurioItem.java:132` `refreshChipSlotCountForEntity`；`api/AstralDiceApi.java:409` | 已封装（实体版） | ✅ `refreshDiceChipSlots` | — | — |
| 骰子阶级注册 | `api/AstralDiceApi.java:85` 返回主模组 `DiceTier`；`:104 diceTier(id)` / `:109 diceTiers()` 返回 **StarEngine** 类型（无 item / matches / targetChipSlots） | 注册面有，消费面 `isDiceItem/diceStarLevel/diceCardSlots` 全走 `instanceof DiceCurioItem` ⇒ 传普通 Item 的 tier 不可玩 | 部分 | 补 `DiceTierInfo diceTierInfo(ItemStack)`；`registerDiceTier` javadoc 写明「物品必须满足 DiceCurioItem 语义」，或提供 `registerDiceItem(ResourceLocation, Supplier<Item>)` 让四个读方法认表 | P1 |
| 骰子阶级反查 | `common/item/dice/DiceTierRegistry.java:54` `get(ItemStack)`、`:80` `isDice`、`:85` `all`；`common/item/dice/DiceTier.java:31` `targetChipSlots`、`:35` `matches` | 全内部（common 包但在 api 之外） | 无 | `@Nullable DiceTierInfo diceTierInfo(ItemStack)`、`List<DiceTierInfo> diceTierInfos()`、`int diceTargetChipSlots(ItemStack, int starLevel)`；`record DiceTierInfo(String id, int cardSlots)` | P1 |
| 骰子星级写入 | 只读点 `item/dice/DiceCurioItem.java:50`；写点 `component/WeaponEnhancement.java:12` 的 starLevel | 无公开写入口 | 无 | `boolean setDiceStarLevel(ItemStack dice, int starLevel)`（内部新增 DiceCurioItem.setStarLevel，写完必须重算筹码槽） | P1 |
| 骰子升级/重置 | `item/DiceStarUpgrade.java:112` `resultFor`、`:119` `normalUpgraded`、`:138` `resetCraftedResult` | 内部，行为绑铁砧/配方 | 无 | 不新增（由 `setDiceStarLevel` + 配方覆盖） | P2 |
| **筹码** 注册 | `api/AstralDiceApi.java:128` `registerChip`；`shop/ChipShopCatalog.java:307` `byItem` 硬闸 `astral_dice` 命名空间、`:163` `itemId()` 硬编码 MODID；全源码 `Signs.`/`Chips.` **零引用** | 写进库表但本线无读取者 ⇒ 注册了不生效，也没有物品绑定 | 部分（名义） | `void registerChip(ResourceLocation id, Supplier<? extends Item> item, ChipSchool school, ChipCategory category, int price, int weight)`（绑定物品，供 isChipItem/已装备列表识别；商店侧不接） | P1 |
| 筹码反查 | `api/AstralDiceApi.java:134` `chip(id)` 只查库表；`shop/ChipShopCatalog.java:307` 命名空间闸 | addon 筹码拿不到自己的 descriptor | 无 | `@Nullable String chipId(ItemStack)`、`boolean isChipItem(ItemStack)` | P1 |
| 已装备筹码列表 | `item/chip/ChipEquip.java:23` `isEquipped`、`:33` `equippedChips`（全仓无调用者）⚠ 该类仅 neo-1.21.1 / forge-1.20.1 | 内部；fabric 三线缺类 | 无 | `List<ItemStack> equippedChips(LivingEntity)`、`boolean isChipEquipped(LivingEntity, ItemStack)`；缺线补 ChipEquip，或把过滤下移到五线皆有的 `item/CurioSlotUtil.java` | P1 |
| 筹码自动装备 / 槽位 | `item/CurioSlotUtil.java:155` `tryAutoEquip`、`:254` `hasDiceEquipped`；`item/dice/DiceCurioItem.java:161` `refreshChipSlotCount(Player)` | 内部 | 无 | `boolean autoEquipChip(Player, ItemStack)`、`int chipSlotCount(LivingEntity)` | P2 |
| 筹码效果结算钩子 | `item/chip/BaseChipItem.java:36`（抽象类）、`:43` canEquip 只认 `chip` 槽、`:62` curioTick 为空；效果分散在 61 个子类与事件监听 | 没有统一钩子层；结算靠子类自身 curioTick / 属性 / 伤害事件 | 无 | 不新增通用钩子（见不建议）；用 H1 的物品绑定补齐「认得这个筹码」 | P2 |
| 筹码商店 | `shop/ChipShopOpener.java:41/61`、`shop/ChipShopCatalog.java:163`、`shop/ChipShopRoller.java:51` | 内部，且 `ChipShopService` fabric 缺类 | 无 | 不入面 | P2 |
| **立牌** 物品判定 | `api/AstralDiceApi.java:416` `isSignItem`（判 `instanceof BaseSignItem`） | 已封装 | ✅ `isSignItem` | — | — |
| 立牌注册 | `api/AstralDiceApi.java:116` `registerSign`；`item/sign/BaseSignItem.java:401` `activeCooldownBaseTicks(player)`（descriptor 的 `activeCooldownTicks` 全源码零命中） | 写进库表但本线不读；冷却由立牌子类覆写决定 | 部分（名义） | `int signActiveCooldownTicks(Player, ItemStack)` 暴露实际冷却；`registerSign` 的 cooldown 参数在 javadoc 标为「本线不生效」 | P1 |
| 立牌主动激活 | `api/AstralDiceApi.java:426` `activateSign`；`item/sign/BaseSignItem.java:560` `activateFor`（玩家早退）、`:567` `activateForEntity`（未覆写默认 false + WARN） | 只返回 boolean；玩家路径恒 false | 部分 | `SignActivation activateSignChecked(LivingEntity, ItemStack)`；`record SignActivation(boolean success, String reason, int cooldownTicksRemaining)` | P1 |
| 激活失败原因 | `item/sign/SignEntitySkills.java:29` `deny(signId, owner, reason)`（只打 `AP_SIGN_ACTIVE_ENTITY_DENIED` 与 actionbar） | reason 被吞进主模组日志，外部拿不到；返回值恒 false | 无 | 同上 record 的 `reason`；公开常量 `DENIED_NEEDS_TARGET / _SESSION / _WALLET / _CARD_CHANNEL / _TEAM` | P1 |
| 立牌战斗钩子 | `item/sign/SignHooks.java:18`（10 个嵌套接口）、`item/sign/SignHookDispatcher.java:38` 硬编码优先级 / `:50` `forEachSign` 用 instanceof ⚠ 两文件 fabric 三线缺 | 只有继承内部 BaseSignItem 的立牌才会被分发 | 无 | 不新增 SignHooks 面（见不建议）；用既有 `registerDamageHook` / `registerCombatStatHook` 组合 | P2 |
| 立牌装备 / 冷却查询 | `item/CurioSlotUtil.java:254` `hasDiceEquipped`；`component/ModAttachments.java`（SignState 冷却附件） | 内部附件，五线皆有 | 无 | `boolean hasDiceEquipped(LivingEntity)`、`boolean isSignEquipped(LivingEntity)`、`int signCooldownRemaining(Player)` | P2 |
| **组件** 装配结果写回 | `component/WeaponEnhancement.java:12`、`component/AppliedStone.java:29`；唯一写点在 `item/card/CardInventoryMenu.java:331` | record 是 public 但包在内部；写回逻辑只在菜单私有方法里 | 无 | `boolean writeDiceLoadout(ItemStack dice, DiceLoadout loadout)`（覆盖式，需 javadoc 声明会作废现有已装卡与临时卡归属） | P1 |
| 装配容量数值 | `component/WeaponEnhancement.java:52` `freeCost`；`GameplayConstants.cardCostForStar` | 内部数值规则 | 无 | `int diceFreeCost(ItemStack)`、`int cardCostForStar(int starLevel)` | P2 |
| 立牌私有组件 | `component/ModDataComponents.java:26` MISAKI_SIGN_STACKS、`:38/44/51/58` PADMAN_*、`:64/70` JASMINE_* | 单立牌内部计时/叠层 | 无 | 不入面（见不建议） | P2 |
| **界面** 按玩家打开卡牌装配界面 | `network/OpenCardInventoryPayload.java:30` `handle` 校验 Curios 有骰子（否则 actionbar `no_dice_equipped`）→ `:41` `openMenu(new SimpleMenuProvider(... CardInventoryMenu ...))`；该 payload 是 C2S 空包 ⚠ 仅 neo 两线有 | 服务端无公开「按玩家开界面」方法，只能让客户端发 C2S 包 | 无 | `boolean openCardInventory(ServerPlayer player)`（返回 false = 未装骰子）；`SimpleMenuProvider` + `CardInventoryMenu` 五线皆有，可直接落 common | P1 |
| 打开卡牌商店 / 筹码商店 | `shop/CardShopOpener.java:14/25`；`shop/ChipShopOpener.java:41/61` | 内部 opener；筹码店拒绝原因只在 actionbar/日志 | 无 | 不入面（见不建议） | P2 |
| 选卡会话（服务端编程发起） | `choice/CardChoiceManager.java:94` `start(ServerPlayer, int, CardChoiceType, Consumer<List<ItemStack>>)`、`:108` 五参版、`:119/129` 自带 token 版、`:187 registerHandler`；`choice/CardChoiceType.java:21` enum COMBAT/EFFECT/ANY | 全内部；外部无法构造 `CardChoiceType`，回调语义也拿不到会话状态 | 无 | `int startCardChoice(ServerPlayer, int slotCount, int minSlots, CardChoiceSlotType type, Consumer<List<ItemStack>> handler)` + `int startCardChoice(ServerPlayer, int slotCount, CardChoiceSlotType type, Consumer<List<ItemStack>> handler)`；新增 `enum CardChoiceSlotType { COMBAT, EFFECT, ANY }`（api/choice，五线皆有底层类） | P1 |
| 客户端界面注册 | `screen/` 9 类（`CardInventoryScreen`、`CardChoiceScreen`、`ChipShopScreen`、`CardShopScreen`、`AstralAnvilScreen`、`ModMenuTypes`） | 客户端渲染/widget | 无 | 不入面（见不建议） | P2 |
| 数据包扩展（商店/铁砧/战利品） | `data/ShopDataReloadListener.java:22` `astral_dice/shop`、`data/AnvilUpgradeReloadListener.java:28` `astral_dice/anvil`、`data/LootInjectionReloadListener.java:33` `astral_dice/loot` | 数据包目录本身即扩展通道，无需 API | ✅（非 API 通道） | — | — |
| **物品** 属性/标记结算管理器 | `item/MarkManager.java`、`ChargeManager.java`、`HealingManager.java`、`StarLightManager.java`、`CounterStateManager.java`、`AbyssMarkManager.java`、`RenShieldManager.java`、`EmpowerManager.java` | 主模组战斗结算内部状态 | 无 | 不入面（见不建议） | P2 |

## 建议实现顺序

**阶段 0（前置事实，先确认再动手）**：五个候选内部类在五线并不齐全——`CardUseService`（缺 fabric-1.20.1 / fabric-1.21.1 / neoforge-26.1.2）、`ChipEquip`（缺 fabric 两线 + 26.1.2）、`SignHooks`+`SignHookDispatcher`（缺 fabric 两线）、`OpenCardInventoryPayload`（缺 forge/fabric 三线）。因此新面一律优先落在**五线都有的类**上：`CardInventoryMenu`、`CurioSlotUtil`、`CardChoiceManager`、`CardChoiceType`、`ModDataComponents`、`BaseEffectCardItem`、`ModItems`、`CardReturnHelper`、`TemporaryCardUtil`、`DiceCurioItem`、`BaseSignItem`、`SignEntitySkills`（以上均五线实测存在）。

**阶段 1（P0，先定数据契约）**：新增 `api/data/DiceLoadout.java` 与 `api/data/InstalledCard.java`（只含 vanilla 类型：`String`、`int`、`UUID`、`List`，不得引用 `component.*`），门面加 `diceLoadout(ItemStack)` / `writeDiceLoadout(ItemStack, DiceLoadout)`。这是后面所有卡牌面的地基。

**阶段 2（P0，卡牌闭环）**：每线新增 `item/card/DiceCardLoadout`（五线同签名：`read(ItemStack)`、`write(ItemStack, Player, List<AppliedStone>)`、`equip(ItemStack, ItemStack, Player)`、`unequipAll(ItemStack, Player)`），门面出 `equipCard` / `unequipAllCards` / `returnCards`。语义必须与 `CardInventoryMenu#saveToDice` 逐行等价：装配销毁卡牌物品、临时卡走 `TemporaryCardUtil.mark`、卸下走 `CardReturnHelper.returnCards`（保留 `TeruSignItem.onAttackCardsEquipped` 回调）。

**阶段 3（P0，用卡）**：`useCardOn(LivingEntity, ItemStack)`。玩家走 `ItemStack#use` 直连（common 可编译，1.20.1/1.21.1 均有）；非玩家经 `AstralDicePlatform#useCardOnEntity` default false，缺线补 `CardUseService` 后再接桩，语义与 `CardUseService.java:40` 一致（当前恒 false）。

**阶段 4（P1，卡牌识别）**：`isCardItem/isCombatCard/isEffectCard` → `cardTypeIds/cardById` → `cardTypeId` → `effectCardPool` → `cardUses` 读写 → 临时/专属卡。`cardById` 必须提供严格语义（未命中 EMPTY），不能复用内部 default 回退。

**阶段 5（P1，骰子与筹码）**：`diceTierInfo/diceTierInfos/diceTargetChipSlots/setDiceStarLevel`；`registerChip` 重载绑定物品 + `chipId/isChipItem` + `equippedChips`（后者需先补齐 `ChipEquip` 三条缺线，或把过滤下移到 `CurioSlotUtil`）。

**阶段 6（P1，立牌）**：先出 `SignActivation` record + 五个 reason 常量，再 `signActiveCooldownTicks` / `signCooldownRemaining` / `hasDiceEquipped`。`activateSign` 保持 boolean 兼容，javadoc 注明玩家恒 false。

**阶段 7（P1，界面）**：`openCardInventory(ServerPlayer)`（复制 `OpenCardInventoryPayload:34-41` 的服务端语义到 common，顺带补上 forge/fabric 三线）与 `startCardChoice(...)` + `api/choice/CardChoiceSlotType`。

**阶段 8（P2）**：`autoEquipChip`、`chipSlotCount`、`diceFreeCost`、`cardCostForStar`。

**落地纪律**：① 只加「确定签名 + 返回类型」，不承诺内部类可见性；② 新面不注册内容表，`freezeContent()` 语义不变；③ 改产品代码后按仓规五线统一 bump `mod_version` 并在 `CHANGELOG.md` / `CHANGELOG_ZH.md` 记录；④ 每阶段验一条 KubeJS 只读读数（骰子阶数/星级/已装卡数/立牌冷却），防止静默回退。

## 明确不建议 API 化的项

1. **`SignHooks` 十个嵌套接口**：`SignHooks` / `SignHookDispatcher` 在 fabric 两线不存在；分发靠 `instanceof BaseSignItem` + 硬编码优先级表（`SignHookDispatcher.java:38`），开放等于把内部战斗数据流固化成 ABI。替代：`registerDamageHook` + `registerCombatStatHook` 已能覆盖绝大多数立牌伤害/数值需求。
2. **通用「筹码效果钩子」**：61 个子类效果分别落在 `curioTick`、属性修改器、伤害/治疗事件里，没有统一 stage；造一个 `onChipTick` 只会给出错误心智模型。附属若要自定义筹码，自己实现 `ICurioItem` 并用 H1 的物品绑定让主模组认得即可。
3. **商店打开与购买链路**（`CardShopOpener` / `ChipShopOpener` / `ChipShopRoller` / `ChipShopCatalog`）：2026-11-18 裁决 TLMDice 不接商店；且 `ChipShopService` 在 fabric 两线缺类，筹码池硬编码 `astral_dice` 命名空间。暴露只会把内部经济规则（`priceOf = min(100, 10+5*count)`）变成对外承诺。
4. **客户端 screen 注册**（`screen/` 9 类）：渲染、widget、标题 lang 键都属实现细节；服务端只需一个 `openMenu` 入口（阶段 7），客户端屏幕由主模组自己绑 `ModMenuTypes`。
5. **立牌私有组件**（`MISAKI_SIGN_STACKS`、`PADMAN_*`、`JASMINE_*`）：单立牌的计时/叠层私有状态，无跨模组语义；暴露后每次调平衡都要改 ABI。
6. **属性/标记管理器**（`MarkManager`/`ChargeManager`/`HealingManager`/`StarLightManager`/`CounterStateManager`/`AbyssMarkManager`/`RenShieldManager`/`EmpowerManager`）：主模组战斗结算内部状态，暴露等于承诺「这些标记的数值口径永不改」。
7. **`DiceStarUpgrade` 的升级/重置内部**（`resultFor` / `normalUpgraded` / `resetCraftedResult`）：行为绑铁砧配方与实验开关，addon 真正需要的是 `setDiceStarLevel`。
8. **配方序列化与配方条件**（`recipe/ModRecipeSerializers`、`recipe/condition/ChipRecipesEnabledCondition`）：属加载器注册机制；条件已可被外部 JSON 复用（`astral_dice:chip_recipes_enabled`）。
9. **数据 reload listener**（`data/*ReloadListener`）：数据包目录本身就是扩展通道，再加 API 只是重复。
10. **把 `registerSign` / `registerChip` 的描述符字段改成 public 或增字段**：不在本审计口径内；真正的问题是「本线消费链路不读库表」，属实现层修复，不是 API 面扩充。

## 附：六个重点问题的直答

1. **卡牌**：现有门面只有 `giveCardTo`（给牌）与 `giveItemTo`，卡牌**识别/取值/使用/装配/读负载**全缺。`CardUseService#useCardForEntity` 是 static 桩（`CardUseService.java:40`），不构成扩展点；`CardInventoryMenu#saveToDice` / `loadFromDice` 是 private，且装配会销毁卡牌物品。`WeaponEnhancement.AppliedStone` 的读写需要：`api/data/InstalledCard` 镜像 + 每线 `DiceCardLoadout` 服务（组件类型不能出现在 `common/api` 的公开签名里，否则等于把 `component.*` 拉进 ABI）。
2. **骰子**：已进面的是 `isDiceItem`、`diceStarLevel`、`diceCardSlots`、`refreshDiceChipSlots`；`diceTier/diceTiers` 返回的是 StarEngine 类型，拿不到 `matches(ItemStack)` / `targetChipSlots(int)` / item supplier。未进面：`DiceTier` 本体语义、`DiceTierRegistry` 反查、`configStarLevel` 的写侧。addon 自定义骰子物品目前**必须 `extends DiceCurioItem`**（内部类），因为四个读方法全走 `instanceof`；否则 tier 注册了也没人认。
3. **筹码**：`ChipSchool` / `ChipCategory` / `ChipDescriptor` 来自 StarEngine（库侧 public 类型，不是本仓内部类），但本线对 `Chips` 表**零读取**，结算链路走 `BaseChipItem` 子类与事件。所以注册筹码本身**不需要**额外效果钩子——但也**不会**因此生效：必须绑定一个真正实现行为的物品，且被 `isChipItem` / 已装备列表认出来。价格与 `registerChip` 的 price 参数无关（商店走 `ChipShopCatalog.priceOf`）。
4. **立牌**：`SignEntitySkills.deny` 的 reason 只进 `AP_SIGN_ACTIVE_ENTITY_DENIED` 日志与 actionbar，外部读不到；`activateSign` 只给 boolean 且玩家恒 false。需要 `SignActivation(boolean success, String reason, int cooldownTicksRemaining)` 结果类型，并把 `needs_player_target / needs_player_session / needs_player_wallet / needs_player_card_channel / needs_player_team` 提为公开常量。
5. **组件**：应作为公开**数据契约**，但用 api 侧镜像记录（`DiceLoadout` / `InstalledCard`）而不是直接暴露 `component.WeaponEnhancement` / `AppliedStone`——后者字段随版本变（`starLevel`、`freeCost` 公式），且会把 `component` 包拖进公共 ABI。
6. **界面**：`screen/` 与 `choice/` 在 addon 侧**没有任何可编程入口**；`network/` 的 `OpenCardInventoryPayload` 是纯 C2S 空包，服务端没有「按玩家打开」的方法。可行落点：`Player#openMenu` 五线通用 ⇒ `openCardInventory(ServerPlayer)` 可直接落 common；选卡需 `CardChoiceManager.start` + api 侧枚举；商店按裁决不入面。
