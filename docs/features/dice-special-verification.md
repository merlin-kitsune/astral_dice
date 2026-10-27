# 骰子特殊功能独立验证报告（13 骰 + 八面骰筹码）

## 0. 元信息与判定口径

- **审查对象**：`F:\MCProject\astral_dice_multiloader` 的 P0 两线 `neoforge-1.21.1`（MC 1.21.1 NeoForge）与 `forge-1.20.1`（MC 1.20.1 Forge）。P1 线 `neoforge-26.1.2`、`fabric-1.20.1` **不在本次范围**。
- **分支事实（与任务描述不一致，须登记）**：任务书称作业分支为 `multi-dev-next`（HEAD 56208923），实读工作树为 **`multi-main`，HEAD `ba1e1e30`（2026-10-27 23:28）**；`git status` 只有 `?? models/astral_anvil.bbmodel` 一条未跟踪项（与任务描述相符，未触碰）。本报告全部结论基于 `ba1e1e30` 工作树。
- **只读声明**：未修改任何源码/资源，未运行 gradle，未启动 Minecraft，未执行任何 git 写操作；唯一写入文件 = 本报告。
- **取证方法**：`grep` 定位符号 → `read` 取原文行号 → 共享库 `starengine` 用 `javap -cp <jar>` 反编译实证（源码不在本仓；实物 jar 在 `C:/Users/xmace/.m2/repository/com/merlinkitsune/starengine/starengine-<line>/1.1.0/`，对应 `gradle.properties` 的 `starengine_version=1.1.0`）。
- **判定口径**：`SUPPORTED` = 实现存在**且**有明确调用点、行为与设计一致；`REFUTED` = 实现不存在、或存在但行为/数值与设计不符、或只有方法定义无调用点；`UNVERIFIED` = 证据不足（逐条列出缺什么）。**注释与 javadoc 一律不作为实现证据**，只在「文档漂移」段落引用。
- **路径约定**：`neo/` = `neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`；`forge/` = `forge-1.20.1/src/main/java/com/merlinkitsune/astral_dice/`；`neo/res/` = `neoforge-1.21.1/src/main/resources/`。

## 1. 共性层：阶层 / 筹码栏 / 卡牌栏（后文反复引用）

### 1.1 筹码栏公式（与设计表一致）

`neo/item/ModItems.java:243-284` 静态块逐条注册 `DiceTier`，第二参数即筹码栏 = `star + 常量`：
```java
DiceTierRegistry.register(new DiceTier("dice",           () -> DICE.get(),             s -> s));      // :246-247
DiceTierRegistry.register(new DiceTier("golden_dice",    () -> GOLDEN_DICE.get(),      s -> 1 + s));  // :249-250
DiceTierRegistry.register(new DiceTier("glass_dice",     () -> GLASS_DICE.get(),       s -> 1 + s));  // :252-253
DiceTierRegistry.register(new DiceTier("diamond_dice",   () -> DIAMOND_DICE.get(),     s -> 2 + s));  // :255-256
DiceTierRegistry.register(new DiceTier("netherite_dice", () -> NETHERITE_DICE.get(),   s -> 3 + s));  // :258-259
DiceTierRegistry.register(new DiceTier("emerald_dice",   () -> EMERALD_DICE.get(),     s -> 2 + s));  // :261-262
DiceTierRegistry.register(new DiceTier("obsidian_dice",  () -> OBSIDIAN_DICE.get(),    s -> 2 + s));  // :264-265
DiceTierRegistry.register(new DiceTier("netherrack_dice",() -> NETHERRACK_DICE.get(),  s -> 1 + s));  // :267-268
DiceTierRegistry.register(new DiceTier("weird_dice",     () -> WEIRD_DICE.get(),       s -> 2 + s));  // :270-271
DiceTierRegistry.register(new DiceTier("crimson_dice",   () -> CRIMSON_DICE.get(),     s -> 3 + s));  // :273-274
DiceTierRegistry.register(new DiceTier("amethyst_dice",  () -> AMETHYST_DICE.get(),    s -> 2 + s));  // :276-277
DiceTierRegistry.register(new DiceTier("ender_dice",     () -> ENDER_DICE.get(),       s -> 3 + s));  // :279-280
DiceTierRegistry.register(new DiceTier("nether_star_dice",() -> NETHER_STAR_DICE.get(), s -> 4 + s)); // :282-283
```
落地：`neo/item/dice/DiceCurioItem.java:190` `return tier.targetChipSlots(starLevel(stack));` → `:253-272` `applySlotCount()` 用**自有 id 的绝对 AttributeModifier + `handler.update()`**（`:269-271` `addPermanentModifier(new AttributeModifier(CHIP_SLOT_MODIFIER, wanted, ADD_VALUE))`）。
阶层标签实读 `neo/res/data/astral_dice/tags/item/`：`dice_t0=[dice]`、`dice_t1=[golden_dice, glass_dice, netherrack_dice]`、`dice_t2=[diamond_dice, emerald_dice, obsidian_dice, weird_dice, amethyst_dice]`、`dice_t3=[netherite_dice, crimson_dice, ender_dice]`、`dice_t4=[nether_star_dice]` —— 与筹码公式同阶，**无矛盾**。

### 1.2 卡牌栏：设计表口径与实装**冲突**（重要，影响 3 个骰子判定）

设计表（源自 `neo/item/ModItems.java:162-238` 注册注释）写：golden「卡牌放置栏固定攻防各 4(共 8)」(`:167`)、diamond「攻防各 5(共 10)」(`:179`)、netherite「攻防各 6(共 12)」(`:185`)。实装已改成「按星级、与品阶无关」：
```java
// neo/item/dice/DiceCurioItem.java:43-46
/** 卡牌放置栏总槽位数:由"卡牌配置星级"决定,与骰子品阶无关—0★=4(攻防各2)、1★=6(各3)、2★=8(各4)、3★=12(各6) */
private static final int[] CARD_SLOTS_BY_STAR = {4, 6, 8, 12};
```
`neo/item/dice/DiceCurioItem.java:49-54` `configStarLevel()`（nether_star 恒 3）、`:56-58` `getCardSlots()`；唯一调用点 `neo/screen/CardInventoryMenu.java:69-74`（`totalCardSlots = DiceCurioItem.getCardSlots(equippedDice)`，攻防各半）与 `:249-255`（费用上限 `GameplayConstants.cardCostForStar(starLevel)`）。新骰子 0★：`neo/component/WeaponEnhancement.java:13` `EMPTY = new WeaponEnhancement(0, 3, 0, 3, 0, List.of())`。
`ModItems.java:244` 已自认改口径：「卡牌栏格数不再按品阶区分,仅由星级决定(见 DiceCurioItem.getCardSlots)」⇒ **注释没跟上改版**，设计表三条「固定各 4/5/6」在实装里不存在。

## 2. dice（基础骰子）

**应然**：无特殊；筹码栏 0+s。
**实现证据**：`neo/item/ModItems.java:246-247` `s -> s`（0★0/1★1/2★2/3★3）；全仓无任何以 `DICE`（非 GOLDEN/…）为条件的特殊分支，`DICE` 常量各引用点均为注册/创造栏/模型/配方。
**判定：SUPPORTED**。**风险**：无。
**实机判据**：装备 `astral_dice:dice` → Curios `chip` 栏尺寸 = 星级数（0/1/2/3）。探针读 curios handler 的尺寸。

## 3. golden_dice（金骰子）

**应然**：筹码栏 1+s；卡牌栏固定攻防各 4。
**实现证据**：筹码 `neo/item/ModItems.java:249-250` `s -> 1 + s` —— 成立。卡牌栏**无**按品阶实现：`neo/item/dice/DiceCurioItem.java:46` 只按星级 `{4, 6, 8, 12}`，`golden_dice` 在卡牌栏路径上没有任何分支；0★ 时攻防各 **2** 而非各 4。
**判定：筹码栏 SUPPORTED；「卡牌栏固定攻防各 4」REFUTED**（实装按星级：0★各2、1★各3、2★各4、3★各6）。
**风险**：设计表与实装结构性冲突；若以 `DiceCurioItem.java:43-46` 为最新设计，应同步更新设计表与 `ModItems.java:167` 注释，否则验收必然误判。
**实机判据**：装备 golden（0★）→ 打开卡牌界面 → 槽数 = 4（攻防各 2）而非 8；探针读 `CardInventoryMenu.java:401` `return cardSlots;`。

## 4. glass_dice（玻璃骰子）

**应然**：与金同阶（筹码 1+s）；战斗牌点数始终取最大值；死亡丢失该骰子与已装备卡牌。
**实现证据**：
1) 筹码栏 `neo/item/ModItems.java:252-253` `s -> 1 + s` —— 与金同阶成立（阶层 tag `dice_t1` 同组）。
2) 「最大点数」落在**结算层**而非显示层：`neo/combat/DiceCombatContext.java:38-39` 字段 `attackerCardsMax`（javadoc：攻击者是否佩戴玻璃骰子，战斗牌点数始终取最大值），`:87` `this.attackerCardsMax = diceStack != null && !diceStack.isEmpty() && diceStack.is(ModItems.GLASS_DICE.get());`；攻击侧 `neo/combat/DiceCombatModifiers.java:398-401` `sum += CardRegistry.roll(stone.type(), ctx, ctx.attackerCardsMax);`；受击侧 `:824-825` 用 `ctx.targetCardsMax`（赋值点 `neo/combat/DiceCombatEvents.java:728` 与 `:1348`）⇒ **攻守两侧**都生效。
3) 取最大值实现 `neo/combat/CardRegistry.java:109-112`：
```java
if (maxMode && isRandomRollCard(typeId)) return maxRoll(typeId);   // :109-111
return t.roller().roll(ctx);                                      // :112
```
`isRandomRollCard`（`:116-122`）= medium/large/epic/defense_medium/defense_large/defense_epic/meito；`maxRoll`（`:131-144`）medium=3、large=6、epic=10、shadow_strike=3、meito=20、charge=5、full_power=6、bite/dragon_roar=3。定值牌（shadow_strike/charge/full_power）本来无随机性，走固定值。
4) 死亡丢失：`neo/item/dice/DiceCurioItem.java:296-315` `removeGlassDiceOnDeath()`（`:303` `if (!dice.isEmpty() && dice.is(ModItems.GLASS_DICE.get()))`，清槽 + 播 `GLASS_DICE_BROKEN` + 筹码栏 `forceRemove` 归零），**调用点** `neo/event/PlayerLifecycleHandler.java:45`；卡牌存在骰子自身的 `WEAPON_ENHANCEMENT` 组件（`neo/component/ModDataComponents.java:19-23` 注册名 `weapon_enhancement`；写回点 `neo/screen/CardInventoryMenu.java:330-332`）⇒ 骰子被拿走时卡牌一并丢失。
**判定：SUPPORTED**（三条全中）。
**风险**：①数值放大可观（epic 恒 10 vs 均值 5.5、meito 恒 20 vs 均值 10.5），与「同阶」平衡承诺有张力；②死亡丢失走 `PlayerLifecycleHandler` 单点，未与 `LivingDeathEvent` 双触发；③只覆盖 7 种随机掷点卡，其余卡牌无变化（符合预期，验收需按卡型分组）。
**实机判据**：装备 glass + 挂 1 张 `epic` 攻击牌 → 连打 10 次，骰战日志中卡牌点数应恒为 10（对照无骰时 6~10 分布）；死亡一次后 Curios `dice` 槽为空、`weapon_enhancement` 组件随之消失。

## 5. diamond_dice（钻石骰子）

**应然**：卡牌栏攻防各 5（筹码栏设计表未给，实装 2+s）。
**实现证据**：筹码 `neo/item/ModItems.java:255-256` `s -> 2 + s`；卡牌栏无品阶分支，`DiceCurioItem.java:46` 只按星级，攻防严格各半（4/6/8/12 → 2/3/4/6），**任何星级都不会出现「各 5」**（10 格这种奇数总槽位不可达）。
**判定：REFUTED**。
**风险**：`CARD_SLOTS_BY_STAR` 全偶数 + 攻防严格各半 ⇒ **任何奇数总槽位设计都无法实现**。
**实机判据**：装备 diamond（任意星级）→ 卡牌界面攻防槽数 ∈ {2,3,4,6}，永不等于 5；探针读 `CardInventoryMenu.java:69-74` 的 `attackSlots/defenseSlots`。

## 6. netherite_dice（合金骰子）

**应然**：卡牌栏攻防各 6（筹码栏 3+s）。
**实现证据**：`neo/item/ModItems.java:258-259` `s -> 3 + s`；卡牌栏同上——**只有 3★** 才 `12/2 = 6` 各 6，0★/1★/2★ 分别各 2/3/4。「各 6」是**星级 3 的通用结果**，与 netherite 品阶无关（3★ 的金骰子同为各 6）。
**判定：REFUTED**（作为 netherite 的「特殊」不成立）。
**风险**：同 diamond；「合金 0★ 也各 6」的预期会落空（实际各 2）。
**实机判据**：同一 3★ 分别换 golden / diamond / netherite → 卡牌槽数**恒等** = 12（各 6），证明槽位只跟星级走。

## 7. emerald_dice（绿宝石骰子）

**应然**：村民交易费用改为星币并 20% 折扣。
**实现证据**（两线同构）：
```java
// neo/trade/EmeraldDiceTrade.java
new ItemCost(ModItems.STAR_COIN.get(), discount(count))                                          // :64-68 transform
private static int discount(int count) { return Math.max(1, (int) Math.floor(count * 0.8)); }     // :71-73
// :53-58 hasEmeraldDice = curios findFirstCurio(s -> s.is(ModItems.EMERALD_DICE.get()))
```
上下文开合 `begin(Player)` `:29-36`（仅 hasEmeraldDice 才进）、`end()` `:38-47`、`isSwapActive()` `:49-51`、防刷 `filteredFor` `:98-113`、`resendOffers` `:129-137`。**调用点（mixin，已启用）**：`neo/mixin/trade/ServerPlayerMixin.java:16-19`（`sendMerchantOffers` HEAD → `begin`）、`:25` `end`、`:35` `filteredFor`；`MerchantMenuMixin.java:24/30/39`、`MerchantContainerMixin.java:24/30/38`、`MerchantResultSlotMixin.java:24/29/36`、`MerchantOfferMixin.java:37-109`（`getItemCostA`/`getCostA`/`satisfiedBy`/构造）。启用证据 `neo/res/astral_dice.mixins.json:12-16` 五项 `trade.*`。
**判定：SUPPORTED**。
**风险**：①只判「费用物品 = `Items.EMERALD`」，**不限村民身份** ⇒ 本仓自营 trader（`neo/trade/ChipTraderTrades.java:142`、`neo/trade/CardTraderTrades.java:150`，同样以绿宝石定价）也被改写成星币 8 折，等于多出一批折扣入口；②`Math.floor(0.8)` 对 count=1 得 0，被 `Math.max(1,…)` 夹到 1 ⇒ 单个绿宝石交易不打折；③逐条 cost 打折，多 cost 配方无整体取整。
**实机判据**：装备 emerald → 与村民开界面 → A 项物品 id 变 `astral_dice:star_coin`、数量 = `max(1, floor(n*0.8))`；卸下恢复绿宝石。探针读 `MerchantOffer.getItemCostA().getItem()` 与 `getCount()`。

## 8. obsidian_dice（黑曜石骰子）

**应然**：基础防御 +3（折算 +6 护甲）、火焰伤害 -70%。
**实现证据**：
1) 防御 → 护甲换算：
```java
// neo/item/dice/ObsidianDiceItem.java:27-31
private static final int DEFENSE_BONUS = 3;
private static final double ARMOR_BONUS = DEFENSE_BONUS * DiceCombatModifiers.PLAYER_ARMOR_PER_DEFENSE;
private static final float EXPLOSION_DAMAGE_REDUCTION = 0.5F;
// :38-46 getAttributeModifiers → Attributes.ARMOR, ADD_VALUE, ARMOR_BONUS
```
`neo/combat/DiceCombatModifiers.java:198` `PLAYER_ARMOR_PER_DEFENSE = 1.0 / CombatFormula.PLAYER_ARMOR_COEF`；同文件 `:203-205` javadoc 实证「玩家与生物两侧的护甲系数不同(**0.30** / 0.40)」⇒ `3 ÷ 0.30 = **10.0 护甲**`（设计表写 +6；`neo/item/ModItems.java:197` 注释也写 +6；`neo/item/dice/ObsidianDiceItem.java:28` javadoc 自认「≈10」）⇒ **三方不一致，实装 10 而非 6**。
2) 「火焰 -70%」**无实现**：`neo/event/ObsidianDiceHandler.java:29-37` 方法名 `onFireDamage` 但判的是**爆炸**：
```java
if (!ObsidianDiceItem.isExplosionDamage(event.getSource())) return;                        // :33
float reduced = event.getAmount() * (1.0F - ObsidianDiceItem.EXPLOSION_DAMAGE_REDUCTION); // :35
event.setAmount(reduced);                                                                 // :36
```
`isExplosionDamage` = `DamageTypeTags.IS_EXPLOSION`。全仓 `grep -E "IS_FIRE|ON_FIRE|IN_FIRE|isFireDamage|lava"` 在 `neo/combat/` = **0 命中**，`ObsidianDiceHandler` 内也无火焰判定 ⇒ 火焰/lava 伤害完全没被削减。用户面 tooltip 也写爆炸（`neo/event/ModTooltipHandler.java:449-454`，`obsidian_dice_explosion`）。
**判定：两项均 REFUTED**（防御实装 +10 护甲、应 +6；火焰 -70% 无任何实现，实装的是**爆炸 -50%**）。
**风险**：①防御被高估 67%（3 防御本应 6 护甲，实为 10）⇒ 数值膨胀；②设计表「火焰」与实装「爆炸」不是同一效果，说明设计表更像旧规格，需裁决以哪个为准；③爆炸减免是独立 `@SubscribeEvent`，黑曜石不参与骰战减伤管线，未见重复应用。
**实机判据**：①读属性：无甲 + 装备 obsidian → `player.getAttributeValue(Attributes.ARMOR)` 增量 = **10.0**（设计应 6.0）；②爆炸：`/summon tnt` 贴脸 vs 远处对照，HP 差 = 原伤害 ×0.5；③火焰：站火里 10 秒，HP 差与无骰对照组**相同**（证明 -70% 不存在）。

## 9. netherrack_dice（下界岩骰子）

**应然**：下界挖矿概率掉星币/星盘；猪灵保持中立。
**实现证据**：
```java
// neo/event/NetherrackDiceHandler.java
public static final float STAR_PLATE_CHANCE = 0.05F;   // :25
public static final float STAR_COIN_CHANCE  = 0.30F;   // :27
// :45-61 onOreMined(BlockEvent.BreakEvent)
if (!(level instanceof ServerLevel)) return;                 // :46
if (player.isSpectator()) return;                            // :48
if (level.dimension() != Level.NETHER) return;               // :50
float roll = player.getRandom().nextFloat();                 // :55
if (roll < STAR_PLATE_CHANCE) Blocks.STONE.popResource(level, pos, new ItemStack(ModItems.STAR_PLATE.get()));  // :56-57
else if (roll < STAR_COIN_CHANCE) Blocks.STONE.popResource(level, pos, new ItemStack(ModItems.STAR_COIN.get())); // :58-59
```
矿种白名单 `:38-42` `isNetherOre` = `NETHER_QUARTZ_ORE` / `NETHER_GOLD_ORE` / `ANCIENT_DEBRIS`。猪灵中立 = mixin：`neo/mixin/PiglinAiMixin.java:19-31` `@Mixin(PiglinAi)` `@Inject(method="isWearingGold", at=@At("RETURN"), cancellable=true)`，`:28-29` `!isClientSide && NetherrackDiceHandler.hasNetherrackDice(player)` ⇒ `cir.setReturnValue(true)`；**已在** `neo/res/astral_dice.mixins.json:17` 启用（同文件 `:12-16` 为 trade 五项）。
**判定：SUPPORTED**（概率掉落 + 猪灵中立，两处都有调用点/启用证据）。
**风险**：①掉落用 `Blocks.STONE.popResource(...)` ⇒ **掉在方块位置的地面**，不会直接进背包；②只认 3 种下界矿；③每块独立 5%/30%，连锁挖掘模组会放大期望；④星盘 5% 与星币 30% 是**互斥**分支（`roll<0.05` 优先），总掉落率 30%。
**实机判据**：下界用 netherrack 装备后循环挖 200 块下界石英矿（`setblock` + `destroy`，或直接派发 `BlockEvent.BreakEvent`）→ 数地面掉落物 id；猪灵：装备后靠近 `piglin`，探针调 `PiglinAi.isWearingGold(player)` 应为 true（mixin 生效）。

## 10. weird_dice（诡异骰子）

**应然**：立牌主动冷却 -50%；战斗骰低点(1-3)概率 +50%。
**实现证据**：
1) 低点偏置 —— SUPPORTED：
```java
// neo/event/WeirdDiceHandler.java
public static final float LOW_FACE_WEIGHT = 3.0F;              // :24
float total = 3 * LOW_FACE_WEIGHT + 3 * 1.0F;                  // :62  P(低)=9/12=75%
float w = face <= 3 ? LOW_FACE_WEIGHT : 1.0F;                  // :65
// 调用点 neo/combat/DiceCombatEvents.java:1694 else if (WeirdDiceHandler.hasWeirdDice(roller)) roll = WeirdDiceHandler.rollD6(roller);
```
50%→75% = 相对 +50%，与 tooltip 文案一致；与 crimson 是 `else if` 互斥链（`neo/combat/DiceCombatEvents.java:1681-1697`），**不争用同一随机源**。
2) 立牌冷却 -50% —— **骰子级已删除，但 4 个立牌子类仍残留**：
```java
// neo/event/WeirdDiceHandler.java:39-49
/** ... **不提供**诡异骰子的 -50%(用户需求 2026-10-21:该能力已删除) ... */
public static int signCooldownTicks(Player player) {
    return (int) ChargeManager.signCooldownTicks(player, GameplayConstants.SIGN_ACTIVE_COOLDOWN_TICKS);
}
```
`neo/item/sign/BaseSignItem.java:395-397` 默认 `activeCooldownBaseTicks()` = 上面这个方法 ⇒ 走默认链的立牌（Jill/Haiqing/Lulu/Ren/Teru/Zhao/Bonnie 等）**没有** -50%。但以下四类**自己重写了减半**：
```java
// 完全相同的一段，出现在：
// neo/item/sign/MegasSignItem.java:171-180     :176 if (WeirdDiceHandler.hasWeirdDice(player))
// neo/item/sign/MosesSignItem.java:132-141     :137
// neo/item/sign/ParunanSignItem.java:72-82 / :89-99   :78 / :95
// neo/item/sign/SherrySignItem.java:208-218 / :225-235 :214 / :231
if (WeirdDiceHandler.hasWeirdDice(player)) { ticks = Math.max(1, ticks / 2); }
```
**判定：低点 +50% SUPPORTED；「立牌主动冷却 -50%」REFUTED（骰子级不成立，仅 4 类立牌残留）**。
**风险**：**跨立牌行为不一致**——同戴诡异骰子，枪匠/摩西/帕露南/雪莉 冷却减半，其余立牌不减半；且与库层 javadoc（`:41` 明确「已删除」）直接矛盾，说明 2026-10-21 的删除只改了中心方法、没清理这 4 处复制粘贴。另 `:86-92` javadoc 说走 `HealingManager`，实装是 `player.heal(HEALTH_ON_ACTIVE_END=5)`（`:92`），文档漂移。
**实机判据**：①低点：装备 weird → 连打 60 次，读骰战日志 roll 分布，P(1-3) 应 ≈75%（对照无骰 ≈50%）；②冷却：分别用 MegasSign 与 JillSign 放主动，读 `ModAttachments.getSignActiveMaxCooldown(player)`（`neo/item/sign/BaseSignItem.java:183/191` 写入）→ Megas 应为基值 1/2，Jill 应为满值。

## 11. crimson_dice（绯红骰子）

**应然**：战斗骰高点(4-6)概率 +50%；骰出 1 立即受 6 点伤害。
**实现证据**：
```java
// neo/event/CrimsonDiceHandler.java
public static final float HIGH_FACE_WEIGHT = 3.0F;    // :22
public static final float SELF_DAMAGE_ON_ONE = 6.0F;  // :23
float total = 3 * 1.0F + 3 * HIGH_FACE_WEIGHT;        // :49  P(高)=9/12=75%
float w = face >= 4 ? HIGH_FACE_WEIGHT : 1.0F;        // :53
if (result == 1 && !roller.level().isClientSide() && roller.isAlive())               // :63-64
    roller.hurt(ModDamageTypes.diceDamage(roller.level(), roller), SELF_DAMAGE_ON_ONE); // :65
```
调用点：`neo/combat/DiceCombatEvents.java:1694-1697`（`else if (CrimsonDiceHandler.hasCrimsonDice(roller)) roll = CrimsonDiceHandler.rollD6(roller);`）；`rollCombatDie` 四个调用点 `:502`（攻击方）/`:718`（受击方防御牌）/`:1101`（生物近战路径 `playerRoll = rollCombatDie(target)`）/`:2093`（反击链）⇒ 四种上下文都可能触发自伤。
**判定：SUPPORTED**（两项都成立）。
**风险**：①自伤走**嵌套 `hurt()`**（不是 `setAmount` 折叠）：`neo/damage/ModDamageTypes.java:127-128` `diceDamage(new DamageSource(holder(level, DICE_DAMAGE), source))`，`:95` 注释登记 `DICE_DAMAGE` 自 2026-10-27 起在 `bypasses_cooldown` 标签（穿无敌帧）但**不** `bypasses_armor` / 不 `bypasses_invulnerability` ⇒ 实伤 = 6 经护甲削减、**创造模式完全免疫**（实机容易误判「自伤没生效」）；②该嵌套 hurt 已被 `DeathTriggerGuard` 与 `counterDepth` 段包住（`neo/combat/DiceCombatEvents.java:2050-2072`，`:2056` `DeathTriggerGuard.claimPair(attacker, player, "counter")`），且自伤不进骰战（`:1654` `if (attacker == target) return false;`）⇒ **不重现刚修掉的死亡双触发/护甲二次结算**；③受击方防御掷出 1 也会自伤（设计只说「骰出 1」，未限定攻击骰，属边界误读风险）；④权重常量 3.0 与 javadoc 写的「×1.5」不符（`:38`），文档漂移。
**实机判据**：装备 crimson（非创造、护甲 0）→ 连掷 60 次统计 P(4-6)≈75%；HP 设 20，循环调 `CrimsonDiceHandler.rollD6` 直到 1，读 HP 差 = **6**；同实验在创造模式读 HP 差 = 0（证明免疫）。

## 12. amethyst_dice（紫晶骰子）——重点

**应然**：远程/魔法攻击也触发战斗骰并追加骰点伤害（不触发骰神赐福）。

### 12.1 实现落点（全仓唯一功能性引用）
```java
// neo/combat/SpellDamageRegistry.java:385-396
/** 紫晶骰子:远程/魔法攻击命中时也触发战斗骰(1-6)并追加骰点伤害;不触发骰神赐福、不消耗卡牌耐久 */
registerModifier(new SpellDamageModifier() {
    public boolean isActive(SpellDamageContext ctx) { return ctx.hasCurio(ModItems.AMETHYST_DICE.get()); }  // :389
    public float apply(SpellDamageContext ctx, float bonus) {
        return bonus + AmethystDiceHandler.rollD6(ctx.attacker);                                           // :394
    }
```
`hasCurio` = `neo/combat/SpellDamageContext.java:52-54` `curios().map(h -> h.findFirstCurio(s -> s.is(item)).isPresent())`（扫全部 curio 槽，不限 dice 槽名）。全仓 `AMETHYST_DICE` 其余引用 = 注册/创造栏/模型/配方/tooltip/铁砧升级，**骰战管线内 0 命中**。

### 12.2 四条链逐条
**(a) `SpellDamageRegistry.java:389` 的用途与调用者**：它是「法伤加成修饰器」的开关条件。调用者 = `neo/event/DamageEffectCardHandler.java:112-135`：
```java
@SubscribeEvent(priority = EventPriority.HIGHEST)                                          // :112
public void onLivingIncomingDamagePre(LivingIncomingDamageEvent event) {                  // :113
    if (!(source.getEntity() instanceof Player player)) return;                          // :118
    if (!DiceCombatEvents.isBlessingTarget(target, player)) return;                      // :120
    if (!SpellDamageRegistry.isSpellDamage(source, direct)) return;                       // :123
    SpellDamageContext ctx = new SpellDamageContext(...);                                 // :125
    // :130-135 单遍遍历所有修饰器：isActive → apply 累加 bonus
```
**(b) 「远程/魔法也触发」的入口**：判定器 `neo/combat/SpellDamageRegistry.java:130-136` `isSpellDamage(source, direct)`，`:131` 先过 config 闸门 `if (!GameplayConstants.ALLOW_FIREARM_DAMAGE && isFirearmDamage(source)) return false;`（`neo/config/ModCommonConfig.java:95` 默认 **false** ⇒ **枪械弹丸默认不算**；弓箭不受影响：`isFirearmDamage`（`:520-544`）只认 `FIREARM_DAMAGE_TYPES` / 弹丸包名 / 关键词 `bullet|gun|firearm|cannon|shell|missile`）。matcher 列表（`:214-237`）：`direct instanceof AbstractArrow || direct instanceof ThrowableProjectile`（`:217`）、msgId `magic`/`indirectMagic`（`:219-222`）、`MAGIC_DAMAGE_TYPES`（`:224-229`）、`source.is(ModDamageTypes.CARD_SPELL)`（`:233`）；`:234-237` 明确**禁止**把 `skill_damage` 纳入。投射物 owner 的取法 = **不靠** `directEntity instanceof Player`：攻击者取 `source.getEntity() instanceof Player`（`:118`），`direct`（弹丸本身）只用于类型判定 ⇒ 箭/投掷物/魔法用施放者作为 `ctx.attacker`。
**(c) 「追加骰点伤害」怎么加**：**同一伤害事件内折叠**，不是嵌套 hurt：
```java
if (bonus > 0) {                                                                          // :170
    DamageNumberAggregator.tag(target, source, Group.SPELL);                               // :174
    event.setAmount(foldBonusBypassingArmor(target, source, event.getAmount(), bonus));    // :180
}
```
`foldBonusBypassingArmor`（`:92-110`）：源带 `BYPASSES_ARMOR` 时直接相加（`:94`）；否则 `want = CombatRules.getDamageAfterAbsorb(target, base, source, armor, toughness) + bonus`（`:98`）后**二分 40 次**求逆（`:101-108`），返回 `hi`（`:109`）——效果上等价于「骰点伤害绕护甲」，抗性/保护/受击倍率仍整段只算一次。`:138-167` 注释登记这是 2026-10-27 的方案 A（历史 `true_damage` 段已删），`:155-158` 举证 `LivingIncomingDamageEvent` 每次 `hurt` 只派发一次 ⇒ **不会**重现「死亡事件双触发 / 护甲二次结算」。
**(d) 「不触发骰神赐福」的证据**：两道，均**不靠**赐福条件里写排除，而是**法伤在赐福分支之前退出骰战管线**：
```java
// neo/combat/DiceCombatEvents.java:358-364
// 法伤链不参与骰战结算(2026-10-27 折叠批复核 R5 补口)…若法伤误入骰战,settle 会覆盖本次折叠值
if (SpellDamageRegistry.isSpellDamage(source, directEntity)) return;                      // :364
```
该早退在 `:368` `if (!isMeleeWeaponAttack(player)) return;` **之前**，更在 `:399-407` 赐福施加块（`:405` `player.addEffect(new MobEffectInstance(ModEffects.DICE_BLESSING, ...));`、`:407` `triggeredBlessing = true;`）之前。第二道：`:1646-1660` `isDiceBattleSettledDamage()` 内 `:1653` `if (SpellDamageRegistry.isSpellDamage(source, direct)) return false;`。另 `:338` `if (target == player) return;` ⇒ 自伤不参与。
**判定：SUPPORTED**（触发、追加、不触发赐福三条链全部有调用点与落地代码；**语义偏差见风险①**）。
**风险**：
1. **「战斗骰」语义偏差（高）**：`rollD6` 在共享库 `starengine`（`com.merlinkitsune.starengine.event.AmethystDiceHandler`，`javap` 实证 = `ThreadLocalRandom.current().nextInt(1,7)` 后 `return`，**入参 `player` 完全未使用**）⇒ 纯均匀 d6，**不读星级、不读筹码、不读攻防骰池**，也不受 weird/crimson 偏置影响。设计写「触发战斗骰」若指骰战引擎骰点口径（含星级加成），则此项**不成立**；若只要求「多掷一颗 d6 并加伤」，则成立。两种读法对平衡影响差异很大（3★ 骰战骰点远大于 d6 均值 3.5）。
2. **多段法伤 = 多次 d6（无次数上限）**：每次 `LivingIncomingDamageEvent` 都 `apply` 一次，多段/持续法伤线性叠加，且不消耗卡牌耐久（`:385` 注释自述）。
3. **本模组自产法伤也吃**：`CARD_SPELL` 在 matcher 白名单（`:233`）⇒ 活体书页/效果牌 FX 每次命中都追加 d6，形成额外乘区。
4. 枪械默认不触发（`ALLOW_FIREARM_DAMAGE=false`）：设计说「远程」，但**枪械弹丸默认被闸门挡掉**，只有弓箭/投掷物/魔法触发 —— 验收必须用这组口径。
**实机判据**：装备 amethyst → 用**弓**射僵尸，读 `DamageNumberAggregator` 的 SPELL 分段或服务端伤害日志，命中伤害应比无骰时高 1~6（20 次采样均值 ≈ +3.5）；同时读目标 `MobEffects` 确认**无** `astral_dice:dice_blessing`；换枪械重复应**无**加成。

## 13. ender_dice（末影骰子）

**应然**：致命伤害触发不死图腾（冷却 5:00）；雨中/水下受伤 +40%。
**实现证据**：
```java
// neo/event/EnderDiceHandler.java
public static final int  TOTEM_COOLDOWN_TICKS = 20 * 60 * 5;    // :55
public static final float RAIN_WATER_DAMAGE_MULTIPLIER = 1.4F;  // :57
// :148-155 @SubscribeEvent(priority = HIGH) onLivingDamagePre
event.setNewDamage((float) DiceCombatModifiers.applyVictimDamageModifiers(entity, event.getSource(), event.getNewDamage())); // :153
// :158-214 onLivingDeath
if (event.isCanceled()) return;                                                    // :163
if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;         // :165
if (!hasEnderDie(player)) return;                                                  // :166
if (isTotemOnCooldown(player)) return;                                             // :167
event.setCanceled(true);                                                           // :169
player.setHealth(1.0F);                                                            // :178
// :179-196 只清 HARMFUL（:191-193 带 MARKED 时补清 GLOWING）
// :197-202 REGEN II 900t / ABSORPTION II 100t / FIRE_RESISTANCE 800t
EnderDieTotemPayload.send(...)                                                     // :204
tryTeleportToSafeGround(player, 16); playTeleportEffects(...); WarpEngineChipItem.onTeleport(...) // :206-210
ModAttachments.setEnderDieTotemCooldownEnd(player, now + TOTEM_COOLDOWN_TICKS);    // :212-213
```
+40% 的唯一应用点：`neo/combat/DiceCombatModifiers.java:179-185` `applyVictimDamageModifiers`（`:182-183` 登记实例倍率，`:184` `damage > 0 ? damage * factor : damage`）、`:187-195` `instanceVictimFactor` 只搬运不重算、`:832-843` `registerVictimDamageModifier`（`:840` `hasEnderDie`、`:841` `player.isInWaterRainOrBubble()`、`:842` 返回 `RAIN_WATER_DAMAGE_MULTIPLIER`）⇒ **每实例只算一次**；骰战路径 `neo/combat/DiceCombatEvents.java:288` `double victimFactor = DiceCombatModifiers.instanceVictimFactor(target);` 搬运同一倍率，不二次消费。原版图腾路径 `:217-229` `onLivingUseTotem` 只附加瞬移、**不写**末影冷却 ⇒ 与不死图腾不叠加/不重复消耗（且原版图腾在 `hurt` 内先触发会阻止死亡事件，两条路径不会在同一击双双消耗）。
**判定：SUPPORTED**。
**风险**：①冷却与触发状态存在 `player` attachment `ender_die_totem_cooldown_end`（`neo/component/ModAttachments.java:787-800`），玩家死亡是否复制该 attachment 未取证 ⇒ **跨死亡冷却可能被重置**（见 §18）；②`BYPASSES_INVULNERABILITY` 源（虚空/`/kill`）不救，符合预期但验收口径要写明；③只清 HARMFUL，不动 `HEALTH_BOOST` 等；④`tryTeleportToSafeGround` 找不到安全点时的降级分支未逐字核。
**实机判据**：装备 ender → HP 压到 1 后用 `/damage` 致死 → 读 HP = 1、`ModAttachments.getEnderDieTotemCooldownEnd(player) - level.getGameTime()` ≈ 6000、坐标位移/安全点；5 分钟内再打致死伤害应**真死**（冷却闸门）。雨中对照：站雨里 `/damage 4` → HP 差 = 5.6（4×1.4）。

## 14. nether_star_dice（下界之星骰子，T4）

**应然**：卡牌槽/费用恒最高档、筹码栏 +1（相对 T3）、星级附加攻防。
**实现证据**：
```java
// neo/item/dice/DiceCurioItem.java:49-54
public static int configStarLevel(ItemStack stack) {
    if (stack.is(ModItems.NETHER_STAR_DICE.get())) return 3;   // 恒 3★
    ... WeaponEnhancement.starLevel() ...
}
// neo/item/dice/NetherStarDiceItem.java
private static final int ATTACK_PER_STAR = 2, DEFENSE_PER_STAR = 2, MAX_STAR = 3;  // :26-34
// :41-44 actualStar 从 WeaponEnhancement.starLevel() 取并钳 0..3
// :52-59 curioTick 每 20 tick → applyStarAttributes
setDefenseArmorBonus(player, ARMOR_MODIFIER_KEY, star * DEFENSE_PER_STAR);          // :72-79
setAttackBonus(player, star * ATTACK_PER_STAR);
// :81-94 Attributes.ATTACK_DAMAGE 的 ADD_VALUE 瞬态修饰器（id astral_dice:dice_nether_star_dice_attack）
// :61-70 onUnequip 清零
```
卡牌槽/费用：`configStarLevel` 恒 3 → `getCardSlots` = `CARD_SLOTS_BY_STAR[3]` = **12（攻防各 6）**；费用上限 `neo/screen/CardInventoryMenu.java:249-255` `maxAttackCost = maxDefenseCost = GameplayConstants.cardCostForStar(starLevel)`，库侧 `cardCostForStar(int) = min(6, 3 + max(0, star))`（javap 实证）⇒ 恒 **6**（最高档）。筹码栏 `neo/item/ModItems.java:282-283` `s -> 4 + s` ⇒ 同星级比 T3 多 1。
**判定：SUPPORTED**（三条全中；`DiceTierRegistry` 的 4+s 与 `configStarLevel` 恒 3 合起来确实覆盖了星级加成，无互相抵消）。
**风险**：①星级攻防走**每 20 tick 的 `curioTick` 刷新**（`:52-59`），卸下置零靠 `onUnequip`，重登/复制的窗口内加成可能短暂滞后；②加成按**星级**（0★ 无加成），「T4 恒满档」指的是槽位/费用，不是加成 ⇒ 文案要写清。
**实机判据**：装备 nether_star → 卡牌界面槽数 = 12、费用上限攻防各 6；3★ 时读 `player.getAttributeValue(Attributes.ATTACK_DAMAGE)` 增量 = **6**、护甲增量 = `6 * PLAYER_ARMOR_PER_DEFENSE` ≈ 20；卸下后两属性回基线（证明 `onUnequip` 清零）；筹码栏 = 4 + 星级。

## 15. eight_sided_dice_chip（八面骰筹码）

**实测**（`neo/combat/DiceCombatEvents.java:595-616`）：仅在本次攻击**触发骰神赐福**时执行 `:600 int roll = rollDice(10);`；`rollDice(int max)` = `:1668-1670` `ThreadLocalRandom.current().nextInt(1, max + 1)` ⇒ **不吃** weird/crimson 的 d6 偏置，掷 1 也**不**触发绯红自伤（后者只在 `rollCombatDie` 的 d6 路径里）。`roll == 8` 给 8 星币；累计 8 点 +1 星光（上限 `StarLightManager.getCap()`，满时不归零），累计存 `neo/component/ModAttachments.java:40` `eight_sided_roll_accum`。
**判定：SUPPORTED（与各骰交互均为独立、不冲突）**。**风险**：骰子偏置不作用于它，验收时别拿它验证 weird/crimson；它只在赐福分支内结算，非赐福命中不掷。
**实机判据**：装备八面骰 + 任意骰子，触发赐福时读 `eight_sided_roll_accum` 增量（KubeJS 直接调 `DiceCombatEvents.rollDice(10)` 亦可）；累计到 8 读 `player_starlight` +1。

## 16. 判定汇总表

| # | 骰子 | 设计特殊（应然） | 判定 | 唯一证据锚点 |
|---|---|---|---|---|
| 1 | dice | 无特殊（筹码栏 0+s） | **SUPPORTED** | `neo/item/dice/DiceTierRegistry.java` 0★ 档 + `item/dice/DiceCurioItem.java:43-46` 0★ 卡牌 4 槽仅作底 |
| 2 | golden_dice | 筹码 1+s；卡牌栏攻防各 4 | 筹码 **SUPPORTED** / 卡牌栏 **REFUTED** | `DiceTierRegistry` 1★=1+s；卡牌实装按星取档 `DiceCurioItem.java:46` 1★=6 ⇒ 各 3 |
| 3 | glass_dice | 与金同阶；战斗牌取最大点；死亡丢骰+卡牌 | **SUPPORTED**（三点全中） | `glass_dice` 走 1★；`DiceCurioItem.java:296-315` 死亡清槽 |
| 4 | diamond_dice | 卡牌栏攻防各 5 | **REFUTED** | 2★ 档 = 8 ⇒ 各 4（`DiceCurioItem.java:46`） |
| 5 | netherite_dice | 卡牌栏攻防各 6 | **REFUTED**（数值巧合） | 3★ 档 = 12 ⇒ 各 6，但这是「3★ 通用结果」，非 netherite 专属 |
| 6 | emerald_dice | 村民交易改星币 + 20% 折扣 | **SUPPORTED**（范围过宽） | `neo/trade/EmeraldDiceTrade.java:60-73` + `mixin/trade/*` |
| 7 | obsidian_dice | 防御 +3（折算 +6 护甲）；火焰伤害 -70% | **REFUTED ×2** | 护甲实得 +10（`DiceCombatModifiers.java:198`）；火焰判定不存在，实装是爆炸 -50%（`ObsidianDiceHandler.java:33/:35`） |
| 8 | netherrack_dice | 下界挖矿掉星币/星盘；猪灵中立 | **SUPPORTED**（掉落位置有瑕疵） | `NetherrackDiceHandler.java:38-61`；`mixin/PiglinAiMixin.java:28-29` |
| 9 | weird_dice | 立牌冷却 -50%；战斗骰低点 +50% | 低点 **SUPPORTED** / 立牌 **REFUTED** | `WeirdDiceHandler.java:62-70` P(低)=75%；仅 4 类立牌残留硬编码 `/2` |
| 10 | crimson_dice | 高点 +50%；骰 1 受 6 点 | **SUPPORTED** | `CrimsonDiceHandler.java:22/:49/:53/:63-65` |
| 11 | amethyst_dice | 远程/魔法也触发战斗骰并追加骰点伤害（不触发赐福） | **SUPPORTED**（语义偏差） | `SpellDamageRegistry.java:389-396` + `DamageEffectCardHandler.java:170-181`；赐福由 `DiceCombatEvents.java:364` 早退排除 |
| 12 | ender_dice | 致命伤害触图腾（冷却 5:00）；雨中/水下 +40% | **SUPPORTED**（冷却不跨死亡） | `EnderDiceHandler.java:55/:153/:158-214` |
| 13 | nether_star_dice | T4：卡槽/费用最高档、筹码 +1、星级附加攻防 | **SUPPORTED** | `NetherStarDiceItem.java:26-34/:52-59/:81-94` |
| 14 | eight_sided_dice_chip | 参与骰点计算，与各骰交互 | **SUPPORTED**（独立随机源） | `DiceCombatEvents.java:595-616` |

> 汇总：13 个骰子条目中 **SUPPORTED 8 项**（dice / glass / emerald / netherrack / crimson / amethyst / ender / nether_star）、**部分 SUPPORTED 2 项**（golden、weird）、**REFUTED 3 项**（diamond、netherite、obsidian）。另有 1 个参与骰点计算的筹码（eight_sided）SUPPORTED。**设计表与实装不一致的根因集中在两处**：卡牌栏改按「星档」计算（`DiceCurioItem.java:43-46`，`ModItems.java:244` 自认改口径）、黑曜石改用爆炸减伤（`ObsidianDiceHandler.java:33`）。

---

## 17. 疑似缺陷清单（按严重度）

> 只列「代码事实与设计口径不一致 / 会造成验收误判」的项；不含风格问题。每条带 file:line，可直接跳到源码核对。**本次为只读审查，未做任何修复。**

### 17.1 P0（设计条目整体落空或口径必须先裁决，否则验收结论无效）

**P0-1　黑曜石骰子的「火焰伤害 -70%」没有任何实现**
- `neo/event/ObsidianDiceHandler.java:29-37` 的减伤分支判定的是**爆炸**，不是火焰：
  - `:33` `if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {`
  - `:35` `float reduced = amount * ObsidianDiceItem.EXPLOSION_DAMAGE_REDUCTION;`
  - `:36` `event.setAmount(reduced);`
- 全仓在 `combat/`、`event/`、`item/dice/`、`mixins/` 内对 `IS_FIRE` / `LAVA` / `ON_FIRE` / `inFire` 的检索**零命中**（`neo`、`forge` 两线皆然）⇒ 火焰/岩浆伤害不受任何减免。
- 常量名本身就是爆炸：`neo/item/dice/ObsidianDiceItem.java:27-31` `DEFENSE_BONUS = 3`、`ARMOR_BONUS = DEFENSE_BONUS * PLAYER_ARMOR_PER_DEFENSE`、`EXPLOSION_DAMAGE_REDUCTION = 0.5F`。
- 影响：设计表写「火焰 -70%」，实装是「爆炸 -50%」，**两个维度（伤害类型、减伤幅度）都不同**，实机验证「站火里不掉血」必然失败。
- 建议方向：先裁决应然口径（火焰 or 爆炸），再决定是补 `IS_FIRE` 分支（-70%）还是修设计表。

**P0-2　黑曜石骰子的「折算 +6 护甲」实得 +10 护甲**
- `neo/item/dice/ObsidianDiceItem.java:27` `public static final float DEFENSE_BONUS = 3.0F;`
- `neo/combat/DiceCombatModifiers.java:198` `public static final double PLAYER_ARMOR_PER_DEFENSE = 1.0 / CombatFormula.PLAYER_ARMOR_COEF;`
- `neo/combat/DiceCombatModifiers.java:203-205` 的 javadoc 明确：玩家侧 `PLAYER_ARMOR_COEF = 0.30` ⇒ `1 / 0.30 = 3.333` 护甲/点防御 ⇒ 3 点防御 = **+10.0 护甲**（不是设计写的 +6）。
- 影响：防御向平衡按 +6 设计、按 +10 落地，等于白送约 66% 额外减伤基础；若实机读 `armor` 属性得 10 而文档写 6，会误判成「实现错了」。
- 建议方向：要么把设计表改成 +10，要么把 `DEFENSE_BONUS` / 修饰器量纲改成按「护甲值」直接给 6。

**P0-3　紫晶骰子「触发战斗骰」实为独立均匀 d6，与骰战管线完全脱钩**
- `neo/combat/SpellDamageRegistry.java:389` `if (ctx.hasCurio(ModItems.AMETHYST_DICE.get())) {` → `:394` 追加 `AmethystDiceHandler.rollD6(ctx.attacker)` 的点数。
- `starengine` 的 `AmethystDiceHandler.rollD6(Player)` 是**纯均匀 `nextInt(1, 7)`**，`player` 形参未被使用 ⇒ 它**不读**骰战的骰面、不吃 `weird`/`crimson` 的权重偏置、不吃七咒倍率、不算暴击、不掷八面骰、不触发骰神赐福分支里的任何修饰。
- 追加方式是「同一个伤害事件内加数」（`neo/event/DamageEffectCardHandler.java:180` `event.setAmount(foldBonusBypassingArmor(...))`），不是嵌套 `hurt()` ⇒ **没有**重现「死亡事件双触发 / 护甲二次结算」老问题（这点是好的）。
- 但同一入口 `SpellDamageRegistry.java:233` 把本模组自己产出的 `CARD_SPELL` 伤害也算作 spell ⇒ **效果牌的每次结算都会额外叠一个 1-6 点固定伤害**，且多段法术（多 `spellDamage` 调用）**线性叠加**，无法从「一次攻击」口径上收敛。
- 影响：设计写「追加骰点伤害」容易被读成「按本次战斗骰骰面追加」，实装是「每次法术事件 + 均匀 1-6」；对多段法术的收益远高于设计预期。
- 建议方向：裁决「骰点」= 独立 d6（现实现）还是 = 骰战骰面（需接 `rollCombatDie`），并决定多段法术是否只取首段。

**P0-4　golden / diamond / netherite 的「卡牌栏攻防各 N」在设计口径上不可达**
- 注释侧（应然）：`neo/item/ModItems.java:167`（golden「卡牌栏固定攻防各 4」）、`:179`（diamond「卡牌栏攻防各 5」）、`:185`（netherite「卡牌栏攻防各 6」）。
- 实装侧：`neo/item/dice/DiceCurioItem.java:43-46` `CARD_SLOTS_BY_STAR = {4, 6, 8, 12}`（0★/1★/2★/3★，攻防**严格各半**）、`:49-54` `configStarLevel`（nether_star 恒 3★）、`:56-58` `getCardSlots`；调用点 `neo/screen/CardInventoryMenu.java:69-74` 与 `:249-255`。
- 于是：golden(1★)=6 ⇒ 各 3；diamond(2★)=8 ⇒ 各 4；netherite(3★)=12 ⇒ 各 6 **但这是 3★ 档的通用结果**（`nether_star_dice` 同样归 3★，也拿 12）。
- `neo/item/ModItems.java:244` 自身留下改口径痕迹（承认卡牌栏已改按星档计算）。
- 影响：三个骰子的设计条目里有两条（golden 各 4、diamond 各 5）在现实现下**永远达不到**；netherite 的数字虽对，但不是它的专属效果 ⇒ 验收必须先裁决「以注释为准」还是「以星档公式为准」。

**P0-5　诡异骰子的「立牌冷却 -50%」被声明删除，却仍有 4 类立牌硬编码保留（两线同病）**
- 声明侧：`neo/event/WeirdDiceHandler.java:41` javadoc 明写「不提供 -50%，2026-10-21 已删除」；默认链路 `neo/item/sign/BaseSignItem.java:395-397` 走 `WeirdDiceHandler.signCooldownTicks`（`:47-49` 仅转调 `ChargeManager`，无减半）。
- 残留侧（neo）：`item/sign/MegasSignItem.java:176`、`item/sign/MosesSignItem.java:137`、`item/sign/ParunanSignItem.java:78` 与 `:95`、`item/sign/SherrySignItem.java:214` 与 `:231`，每处形如 `if (WeirdDiceHandler.hasWeirdDice(player)) { ticks = Math.max(1, ticks / 2); }`。
- 残留侧（forge，逐行对应）：`item/sign/MegasSignItem.java:173-174`、`item/sign/MosesSignItem.java:139-140`、`item/sign/ParunanSignItem.java:89-90` 与 `:106-107`、`item/sign/SherrySignItem.java:222-223` 与 `:239-240`。
- 影响：诡异骰子的立牌加成变成「看立牌类型」——4 类立牌仍 -50%，其余（走默认链的 Jill/Haiqing/Lulu/Ren/Teru/Zhao/Bonnie 等）完全没有；设计条目既不能说 SUPPORTED 也不能说完全 REFUTED，且实机换个立牌就复现不了。

### 17.2 P1（局部行为与设计/直觉不符，会造成实机误判或收益外溢）

**P1-1　绿宝石骰子的折扣/星币化范围过宽：不限村民，连本仓自营交易点也吃折扣**
- `neo/trade/EmeraldDiceTrade.java:60-62` 的判定只看物品是不是 `Items.EMERALD`，**没有**「交易方是 `AbstractVillager`」的白名单；`:53-58` 做价格换算，`:64-68` 施加 20% 折扣。
- 于是本仓自己的筹码/卡牌商人也被打折：`ChipTraderTrades.java:142`、`CardTraderTrades.java:150` 走同一 `EmeraldDiceTrade` 静态方法。
- 影响：设计写「村民交易费用」，实装是「所有绿宝石计价交易」⇒ 自营商店成本下浮 20%，属于计划外经济外溢。

**P1-2　末影骰子的 5:00 图腾冷却不跨死亡，可被「死一次」重置**
- `neo/component/ModAttachments.java:787-792`：`ender_die_totem_cooldown_end` 的 builder 只有 `.serialize(Codec.LONG)` + `.sync(ByteBufCodecs.VAR_LONG)`，**没有 `.copyOnDeath()`**。
- 同文件 `:1818-1825` 与 `:1958-1966` 自己写明这条平台语义：「1.21.1 的非 copyOnDeath 附件在重生后的**新实体**上回默认值」。
- 对照：文件里 14 处 `.copyOnDeath()`（`:224 :328 :646 :958 :1300 :1825 :1888 :1920 :1966 :2127 :2150 :2170 :2190 :2230`）说明这是有意区分而非遗漏。
- 影响：玩家在冷却期内真死一次（`/kill`、虚空、绕过图腾的其他致命源）⇒ 重生后冷却归 0 ⇒ 5 分钟冷却形同虚设，可反复白嫖图腾。

**P1-3　绯红骰子的「骰 1 自伤 6 点」会被护甲削减、创造模式免疫，实机极易误判**
- `neo/event/CrimsonDiceHandler.java:63-65` 走**嵌套** `roller.hurt(diceDamage(...), 6.0F)`；`:22` `HIGH_FACE_WEIGHT = 3.0F`、`:23` `SELF_DAMAGE_ON_ONE = 6.0F`。
- 伤害类型 `neo/damage/ModDamageTypes.java:127-128` `diceDamage`；`:95` 注释明确它 `bypasses_cooldown` 但**不** bypass `armor` / `invulnerability` ⇒ 实际掉血 = 6 × 护甲减伤系数（满护甲约 2 点），且创造模式完全免疫。
- 嵌套 `hurt()` 已被 `neo/combat/DiceCombatEvents.java:2050-2072` 的 `injectCounterDamage` 收口，`:2056` `DeathTriggerGuard.claimPair(...)` 保证不会双触发死亡事件 ⇒ **老问题未复现**，但「6 点」这个文案数字与实机 HP 差不等。
- 另：受击方（防守方）掷出 1 也会自伤，见 `DiceCombatEvents.java:718` 的 `rollCombatDie` 调用 —— 验收时别只看攻击方。

**P1-4　紫晶骰子对「远程」的覆盖默认不含枪械**
- `neo/config/ModCommonConfig.java:95` `ALLOW_FIREARM_DAMAGE` **默认 false**；`neo/combat/SpellDamageRegistry.java:130-136` 的 `isSpellDamage` 在 `:131` 直接用它把关。
- 关键词表 `SpellDamageRegistry.java:520-544`（`isFirearmDamage`）只在显式开启后才参与判定。
- 影响：装枪械模组时「远程攻击追加骰点」默认不生效；设计写「远程/魔法」，实机可能被当成 bug。

**P1-5　下界岩骰子的掉落生成在地面，可能被岩浆/掉出视线销毁，且只认 3 种矿**
- `neo/event/NetherrackDiceHandler.java:56-59` 用 `Blocks.STONE.popResource(...)` 直接**掉落在世界上**（不是塞进背包）。
- `:50` 限定仅 `Level.NETHER`；`:38-42` 只认 3 种矿石方块 ⇒ 其他下界矿（如粗金块形态、模组矿）不触发；`:24-27` 概率 `0.05F` / `0.30F`。
- 影响：站在岩浆边上挖矿时掉落物可能直接烧掉；「挖了没掉」不能直接判 REFUTED。

### 17.3 P2（注释/文案与实装漂移，会误导后续验收与文档）

**P2-1　绯红骰子方法级 javadoc 与常量不一致（两线同病）**
- `neo/event/CrimsonDiceHandler.java:38`（方法 javadoc）写「高点数(4-6)权重 **×1.5**」；`:14-15`（类 javadoc）与 `:22` 常量却是**权重 3.0**、P 由 50% → 75%（相对提升 50%）。
- `forge/event/CrimsonDiceHandler.java:38` 同文同错。⇒ 行为正确、注释错，但极易被当成「实现只有 ×1.5」。

**P2-2　诡异骰子的 `HealingManager` 注释与 `player.heal` 实装不一致**
- `neo/event/WeirdDiceHandler.java:86-92` 的 javadoc 提到 `HealingManager`，`:92` 实际调用 `player.heal(HEALTH_ON_ACTIVE_END = 5)`。

**P2-3　`ModItems.java` 注册注释与实装口径冲突（已在 `:244` 自认）**
- `neo/item/ModItems.java:167/:179/:185` 仍写「卡牌栏攻防各 4/5/6」，与 `DiceCurioItem.java:43-46` 的星档公式冲突；`:244` 的后续注释承认口径已改。这类「注释先于代码」的漂移是 P0-4 的根源，建议把注释改成指向 `CARD_SLOTS_BY_STAR`。

---

## 18. 两线漂移清单（neoforge-1.21.1 ↔ forge-1.20.1）

> 结论先说：**13 个骰子的功能行为在两线之间没有发现漂移**。逐条判据都能在两线取到同构证据（事件类名/行号不同，逻辑一一对应）。上面列出的 P0/P1 缺陷**两线同病**，不是漂移造成的。

| 关注点 | neoforge-1.21.1 | forge-1.20.1 | 判定 |
|---|---|---|---|
| 骰战主入口 | `combat/DiceCombatEvents.java`（`resolveMobMeleeAttack` ~L1095-1160、`isDiceBattleSettledDamage` ~L1646-1660、伤害因子 ~L1597-1608） | 同名类，事件类型为 `LivingHurtEvent`/`LivingDamageEvent` | 等价（平台 API 差异） |
| 紫晶附加伤害应用点 | `event/DamageEffectCardHandler.java:170-181`（`@HighestPriority` 的 incoming-damage 前置事件） | 同名类 `:172` 附近，改用 1.20.1 的 hurt 前置事件 | 等价，逻辑同构（同一 `foldBonusBypassingArmor` 两分折叠） |
| 紫晶骰点随机源 | 库 `starengine` 的 `AmethystDiceHandler.rollD6` | 同一个库（共享依赖） | **零漂移**（随机源在库里，不在各线） |
| 诡异骰残留硬编码 | `item/sign/{Megas,Moses,Parunan,Sherry}SignItem.java` 6 处 `ticks / 2` | 同名 6 处，行号偏移 +2~+4 | **两线一致（同病）** |
| 绯红骰自伤 | `event/CrimsonDiceHandler.java:63-65` 嵌套 `hurt` | 同名同构（`null.check` 平台差异） | 一致 |
| 末影骰附件 | `component/ModAttachments.java:788-792` 无 `copyOnDeath` | 1.20.1 无 AttachmentType，用玩家 NBT + 白名单 | **平台机制不同**：forge 侧需单独核白名单是否含该键（见 §19 U2） |
| 黑曜石减伤 | `event/ObsidianDiceHandler.java:33` 判 `IS_EXPLOSION` | 同名同判 | 一致（同病） |
| 绿宝石 mixin | `res/astral_dice.mixins.json:12-16` 启用 5 个 trade mixin | `astral_dice.mixins.json` 对应段 | 一致 |
| 下界岩/猪灵 | `mixin/PiglinAiMixin.java:28-29`；`NetherrackDiceHandler.java:56-59` | 同名同构 | 一致 |
| 星骰卡槽/费用 | `item/dice/NetherStarDiceItem.java:52-59` 12 槽 / 费用 6 | 同名同构 | 一致 |

**唯一实质性的「文档级漂移」**：`event/CrimsonDiceHandler.java:38` 的「×1.5」注释两线都有（不是漂移）；`forge` 侧 `BaseSignItem` 未发现残留的 -50% 注释（已 grep 排除，勿再报）。

---

## 19. UNVERIFIED 清单（缺什么证据 / 需要谁裁决）

| # | 待定项 | 现状与已取证据 | 缺什么才能定论 |
|---|---|---|---|
| U1 | 紫晶「追加骰点伤害」的**设计原意**是「独立 d6」还是「本轮骰战骰面」 | 代码行为已完全确定（均匀 d6，见 P0-3） | 设计裁决；代码侧无法再证 |
| U2 | 末影图腾冷却在 **forge-1.20.1** 是否跨死亡 | neo 侧已证不跨死亡（P1-2）；forge 用玩家 NBT 而非附件，需要看 forge 的持久化白名单是否登记该键 | forge 的玩家 NBT 键白名单（`PlayerLifecycleHandler` / 存档读写处）尚未逐字核对 |
| U3 | 黑曜石应然口径 = 火焰还是爆炸 | 实现是爆炸 -50%，火焰零实现（P0-1） | 设计裁决（两者只能保一个，或都补） |
| U4 | 卡牌栏应然口径 = 注释（4/5/6）还是星档公式（4/6/8/12） | 两套口径并存且冲突（P0-4） | 设计裁决；`ModItems.java:244` 倾向星档 |
| U5 | 枪械是否属于设计里的「远程」 | config 默认 false（P1-4） | 设计裁决 + 发布配置确认 |
| U6 | 玻璃骰子「死亡丢失」是否应生成掉落物 | 实装**直接销毁**、卡牌一并消失（`DiceCurioItem.java:303-311`），不 `drop` | 设计裁决（销毁 vs 掉在地上） |
| U7 | 下界岩掉落应入包还是落地 | 实装落地（P1-5） | 设计裁决 |
| U8 | `starengine` 库里 `AmethystDiceHandler` 的其它成员（是否有未接入的「按骰面追加」旧实现） | 未读该库全类 | 需要 `starengine` 源码/字节码逐类核对（本次只看被调用点） |

---

## 20. 排除性检索记录（为什么能判 SUPPORTED）

为避免「没找到就当没有」，每个判 SUPPORTED 的骰子都做过同一套排除动作，记录在此：

1. **常量全仓检索**：对每个骰子用两个模式扫全仓（`ModItems.<CONST>` 与字面 `"<id>_dice"`），扫描范围 = `combat/`、`event/`、`item/dice/`、`item/sign/`、`mixins/`、`component/`、`screen/`、`trade/`、`config/` 以及两线的 `resources/`（含 `astral_dice.mixins.json`、`data/**` JSON）。命中的每个文件都读了调用点，只有定义没有调用的一律降级（本次无此情况）。
2. **config 开关排查**：逐个确认没有 `ModCommonConfig` / `ModServerConfig` 开关把功能默认关掉；唯一命中默认关闭的是 `ALLOW_FIREARM_DAMAGE`（P1-4），已单列。
3. **`CurioSlotUtil.hasDiceEquipped` 闸门（已核调用面，非「单骰分流」）**：定义在 `item/CurioSlotUtil.java:207`，实际是**筹码/立牌类效果的「必须装备了骰子」总闸门** —— `item/chip/*ChipItem.java`（约 40 处，首行 `if (!CurioSlotUtil.hasDiceEquipped(player)) return false;`）、`item/sign/SignEquipCheck.java:33`、`item/sign/BaseSignItem.java:87`、`combat/DiceCombatModifiers.java:366`、`target/TargetSelectionManager.java:291`、`event/PlayerTickEvents.java:62` 与 `:155`。13 个骰子的**自身**特效一律走 `hasCurio(ModItems.X)` 精确判定，因此不存在「装了别的骰子就顶替」的短路；但凡是「经筹码/立牌落地」的骰子特效，都以 hasDiceEquipped 为前置（装备骰子本身即满足，不影响判定）。
4. **单侧生效排查**：每条判据都分别在 neoforge-1.21.1 与 forge-1.20.1 取到实现证据（见 §18 表），不存在「只有一侧有代码」的条目。
5. **注释 vs 实现分离**：所有判定只采信可执行语句（`if` / 赋值 / 事件注册 / 调用点），javadoc 与注册注释只作为「应然」对照；凡两者冲突的都已进 P0/P2 清单。

---

## 附：本次审查的边界

- **未运行任何东西**：没有跑 `gradlew`、没有启动/关闭 Minecraft、没有做任何 git 写操作；工作树的未跟踪文件 `models/astral_anvil.bbmodel` 未触碰。
- **未修改源码**：本报告是本次唯一的写操作（`docs/features/dice-special-verification.md`，UTF-8 + CRLF）。
- 实机判据只是「怎么证明」的方案，**没有执行**；P0-1/P0-3/P0-4 三条的实机验证必须等设计口径裁决后再做，否则会测出「实现与注释不符」而不是「实现有 bug」。
