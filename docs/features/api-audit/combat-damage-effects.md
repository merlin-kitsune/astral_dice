# A 组 API 审计：战斗 / 伤害 / 效果

> 基线：`neoforge-1.21.1/src/main/java/com/merlinkitsune/astral_dice/`（下文路径未标注 `common:` 的均相对该根）；共享根为 `common/src/main/java/com/merlinkitsune/astral_dice/`。
> 唯一判据：外部 addon 能否只靠 `AstralDiceApi` 现有 public static 或 `api/` 公开类型完成；必须 import 内部包即为缺口。
> 审计只读，未改产品代码。

**结论：A 组共 35 项功能面 —— 已覆盖 6 项、部分覆盖 2 项、缺失 27 项。** 缺失高度集中在「骰战修饰器链 / 法术伤害注册 / 状态效果读写 / 星界事件注册 / 目标选择会话」五处：这些能力内部都已有 **public static 注册口或公开构造器**，但全部落在 `combat.*`、`effect.*`、`event.*`、`target.*` 内部包，门面（625 行的 `AstralDiceApi`）完全没转发，addon 只能硬 import 内部类。

| # | 功能面 | 证据（文件:行） | 现状（内部可达性） | 门面覆盖 | 建议 API 签名 | 优先级 |
|---|---|---|---|---|---|---|
| 1 | 骰战攻击力修饰器注册 | `combat/DiceCombatModifiers.java:81`、`:90`、`:101`；`combat/DiceCombatContext.java:73` | public static 可注册，但签名吃内部 `DiceCombatContext`（含 `LivingDamageEvent.Pre`），必须 import combat.* | 无 | `public static void registerAttackModifier(DiceAttackModifier m)`；`api/combat` 新增 `@FunctionalInterface DiceAttackModifier { double modify(DiceAttackContext ctx, double attackPower); }` 与 `record DiceAttackContext(LivingEntity attacker, LivingEntity target, int baseDice, ItemStack diceStack, boolean triggeredBlessing, int misakiStar, int misakiStacks, boolean displaySnapshot)` | P0 |
| 2 | 骰战防御力修饰器注册 | `combat/DiceCombatModifiers.java:86`、`:158` | 同上 | 无 | `public static void registerDefenseModifier(DiceDefenseModifier m)`（`double modify(DiceAttackContext, double defensePower)`） | P0 |
| 3 | 额外加伤修饰器 | `combat/DiceCombatModifiers.java:112`、`:153` | public static，int 返回值，吃内部上下文 | 无 | `public static void registerExtraDamageModifier(DiceExtraDamageModifier m)`（`int modify(DiceAttackContext)`） | P1 |
| 4 | 受击方伤害倍率修饰器 | `combat/DiceCombatModifiers.java:174`、`:188`、`:215` | public static，吃内部接口 | 无 | `public static void registerVictimDamageModifier(DiceVictimModifier m)`（`double multiplier(LivingEntity victim, DamageSource source)`） | P1 |
| 5 | 骰战最终伤害因子 | `combat/DiceCombatEvents.java:1753`（接口）、`:1760`（注册）、`:865-866`（应用） | public static `registerDiceCombatFactor`，接口在内部类里；注释明写「附属内容可注册」 | 无 | `public static void registerFinalDamageFactor(DiceFinalDamageFactor f)`（`double modify(Player attacker, LivingEntity target, double damage)`） | P0 |
| 6 | 非玩家掷骰能力注册 | `combat/DiceCombatAbilities.java:39`（RollContext）、`:66`（DiceRollAbility）、`:103`（register） | public static，但 `RollContext` 是内部嵌套类 | 无 | `public static void registerDiceRollAbility(Predicate<ItemStack> dieMatcher, DiceRollAbility a)`；`record DiceRollContext(LivingEntity holder, ItemStack die, LivingEntity opponent, boolean attackerSide, int sides, int defaultRoll)` | P1 |
| 7 | 骰战参与者登记 / 查询 | `common:api/AstralDiceApi.java:448`、`:453` | 门面直通 `DiceCombatParticipants` | ✔ 已覆盖 | — | — |
| 8 | 骰战数值只读（护甲↔防御点数、有效护甲、防御加成） | `combat/DiceCombatModifiers.java:229`、`:242`、`:247`、`:254`、`:265`、`:281` | 全是 public static，但需 import combat.* | 无 | `public static double defensePointsForArmor(double armor, boolean player)`、`public static double armorForDefensePoints(double points, boolean player)`、`public static double effectiveArmorPoints(LivingEntity entity)`。**`:281 setDefenseArmorBonus 不建议暴露（写战斗值）** | P1 |
| 9 | 暴击 / 穿透等战斗 stat | `common:api/combat/CombatStat.java:8`、`:10`；`combat/DiceCombatEvents.java:642`、`:1462`（crit ×1.5 只在注释） | stat 注册口已通（`:299/:309`），但枚举只有 ATTACK/DEFENSE；内部无暴击 stat 读取点 | 部分 | 先补「读当前值」：`public static double combatStatValue(LivingEntity attacker, LivingEntity target, DamageSource source, CombatStat stat)`；新增 CRIT/PENETRATION 暂缓 | P2 |
| 10 | 骰战语义判据族（是否骰战结算 / 是否赐福目标 / 是否敌对攻击 / 训练假人 …） | `combat/DiceCombatEvents.java:1821`、`:2036`、`:2047`、`:2085`、`:254`、`:1845`、`:2111`、`:1966` | public static，需 import combat.* | 无 | 见「不建议 API 化」一节 | P2 |
| 11 | 法术伤害作用域注册（matcher） | `combat/SpellDamageRegistry.java:100-102`、`:112` | public static，但 matcher 类型是内部类嵌套接口 | 无 | `public static void registerSpellDamageMatcher(SpellDamageMatcher m)`；`@FunctionalInterface SpellDamageMatcher { boolean matches(DamageSource source, Entity direct); }`（放 `api/combat`） | P0 |
| 12 | 法术伤害加成修饰器注册 | `combat/SpellDamageRegistry.java:116`、`:120`；`combat/SpellDamageModifier.java:14`、`:25`、`:34`；`combat/SpellDamageContext.java:22-30`、`:34`、`:44`、`:52` | 接口 public，但上下文吃 `LivingIncomingDamageEvent` + Curios | 无 | `public static void registerSpellDamageModifier(SpellDamageModifier m)`；`record SpellDamageView(Player attacker, LivingEntity target, DamageSource source, Entity directEntity)`；接口 `boolean isActive(SpellDamageView)` / `double apply(SpellDamageView,double)` / `default void onHit(SpellDamageView,double)` | P0 |
| 13 | 法术伤害判定查询 | `combat/SpellDamageRegistry.java:131` | public static，需 import combat.* | 无 | `public static boolean isSpellDamage(DamageSource source, Entity direct)` | P1 |
| 14 | 立牌专属法伤加成数值 | `combat/SpellDamageRegistry.java:155`、`:158`、`:170`、`:189`、`:205` | public static，绑定忍者/调查员立牌内容 | 无 | 见「不建议 API 化」 | P2 |
| 15 | 伤害大类分类 | `common:api/AstralDiceApi.java:254`、`:259`、`:264`；`AstralDiceMod.java:184-185`（装 resolver） | 门面 + 五线实现已通 | ✔ 已覆盖 | — | — |
| 16 | 四阶段伤害钩子（INCOMING/AFTER_ARMOR/POST/DEATH） | `common:api/AstralDiceApi.java:148`、`:279`；`combat/StarEngineDamageBridge.java:20-52` | 门面直通库 `DamageHooks` | ✔ 已覆盖 | — | — |
| 17 | 伤害类型 key 暴露 | `damage/ModDamageTypes.java:14-17`、`:26-29`、`:50-53`、`:73-76`、`:78` | public static `ResourceKey<DamageType>`，但需 import damage.* | 无 | `public static ResourceKey<DamageType> damageType(String path)`（拼 `astral_dice:<path>`）；`public static DamageSource cardSpellDamage(Level level, Player caster)` | P1 |
| 18 | 无视护甲固定伤害 | `damage/ModDamageTypes.java`（`unreducibleDamage`，被 `combat/DiceCombatEvents.java:1739` 调用） | 内部静态方法返回 DamageSource | 无 | `public static boolean dealUnreducibleDamage(LivingEntity target, float amount, Entity attacker)` | P1 |
| 19 | 法伤独立结算入口 | `combat/DamageCardFxScheduler.java:622` `applySpellDamageAmount(ServerLevel,ServerPlayer,LivingEntity,int,boolean)` | public static（2026-10-21 起），但需 import combat.* 且要懂 `gloveSplash`/AOE 窗口 | 无 | `public static boolean dealCardSpellDamage(ServerPlayer caster, LivingEntity target, int amount)`（内部走 `gloveSplash=false`） | P1 |
| 20 | 骰神赐福存在性 | `common:api/AstralDiceApi.java:437`；`platform/NeoForgeAstralDicePlatform.java:34-36` | 门面只有 boolean | 部分 | `public static boolean hasEffect(LivingEntity, ResourceLocation)` + 下面两项 | P1 |
| 21 | 效果层数 / 剩余时间查询 | `effect/ChargeEffect.java:45`（`getStacks` 仅充能）；`effect/ModEffects.java:63-64`、`:441` | 每个效果各有自己的 getter（`addStacks`/`consume` 等），无统一入口；层数=amplifier+1 的约定只写在注释 | 无 | `public static int effectLevel(LivingEntity entity, ResourceLocation effectId)`（0=无）、`public static int effectRemainingTicks(LivingEntity entity, ResourceLocation effectId)`（-1=无限/无）。**必须经 `AstralDicePlatform` 新 default 方法桥接，五线各实现一次** | P1 |
| 22 | 效果施加 / 移除 | 只有 `effect/ChargeEffect.java:29` `addStacks` 这类单效果入口 | 无通用施加/移除；addon 只能自己 `new MobEffectInstance` + 注册表查 Holder | 无 | `public static boolean applyEffect(LivingEntity entity, ResourceLocation effectId, int durationTicks, int level)`、`public static boolean clearEffect(LivingEntity entity, ResourceLocation effectId)` | P1 |
| 23 | 效果 id 目录 | `effect/ModEffects.java:441` `ALL = EFFECTS.getEntries()`（约 74 个） | 有集合，但类型是 `DeferredHolder`（五线注册器类型不一致），addon 只能硬编码 id 字符串 | 无 | `public static List<ResourceLocation> effectIds()`（向 addon 暴露 id 而非注册器对象） | P1 |
| 24 | 效果到期 / 被动层衰减通知 | `combat/DiceCombatEvents.java:1566`、`:1664`、`:1683` | 内部事件处理器，无对外通知 | 无 | 并入 #29 的回调门面 | P2 |
| 25 | 星界事件注册 | `event/AstralEventRegistry.java:47` `register(AstralEvent)`；类型在 `event/api/AstralEvent.java:27-38` | 类型已 public 且零内部依赖，但注册入口在 internal `event` 包 | 无 | `public static void registerAstralEvent(AstralEvent event)`（重复 id 抛 `IllegalStateException`，与内部一致） | P1 |
| 26 | 星界事件触发 | `event/AstralEventDispatcher.java:52`、`:63` | public static，需 import event.* | 无 | `public static boolean triggerAstralEvent(Player triggerer, ResourceLocation id, AstralEventTrigger trigger)` + `(Player triggerer, Player tierOwner, ResourceLocation id, AstralEventTrigger trigger)` | P1 |
| 27 | 星界事件查询 / 公共池 | `event/AstralEventRegistry.java:56`、`:62`、`:73`、`:79`、`:99`、`:115` | public static，需 import event.* | 无 | `public static Optional<AstralEvent> astralEvent(ResourceLocation id)`、`public static List<AstralEvent> astralEvents()`、`public static List<AstralEvent> publicEventPool(Player player)` | P2 |
| 28 | 事件提示类别覆写 | `target/TargetPromptCategories.java:27-32`（词表）、`:39`（BY_ACTION）、`:48` | 内部 Map，附属只能改自己 actionId 的文案类别 | 无 | `public static void setTargetPromptCategory(String actionId, String category)` | P2 |
| 29 | 骰战 / 伤害结果对外事件 | 全仓无自定义 `Event` 子类；唯一 `.post(` 在 `item/sign/BaseSignItem.java:169-174`、`:284`（转发库 `SignActiveTriggeredEvent`） | 外部只能挂原版/NeoForge 事件，拿不到「骰战结算完成/最终伤害」这一层 | 无 | `public static void registerCombatResolutionListener(CombatResolutionListener l)`；`@FunctionalInterface { void onResolved(Player attacker, LivingEntity target, double finalDamage, boolean diceBlessing); }`（回调，不必新造 NeoForge 事件类） | P1 |
| 30 | 目标选择会话 start / confirm / cancel | `target/TargetSelectionManager.java:189`（start）、`:237`（confirm）、`:132`（isSelecting）、`:167`（cancelForTests） | public static，但必须 import `target.*`；confirm 走网络包 | 无 | `public static boolean startTargetSelection(ServerPlayer player, String actionId)`、`public static boolean isSelectingTarget(Player player)`、`public static void cancelTargetSelection(Player player)` | P0 |
| 31 | 目标选择动作注册 | `TargetSelectionRegistry.java:22`（starengine）、`target/TargetSelectionManager.java:196` | 库侧公开 API，**不需要**内部包 | ✔ 已覆盖 | — | — |
| 32 | 目标类型过滤（7 种 TargetType） | `TargetSelectionManager.java:261`；`common:api/AstralDiceApi.java:345` | 库 `TargetType` + 门面 `matchesPlayerSelection` | ✔ 已覆盖 | — | — |
| 33 | 友方 / 同队目标收集 | `combat/TeamTargets.java:24`、`:41` | public static，需 import combat.* | 无 | `public static boolean isFriendly(Player viewer, Entity other)`、`public static List<LivingEntity> collectFriendlyLiving(Player player, double radius)` | P1 |
| 34 | 敌意判定 / 自定义探针 | `common:api/AstralDiceApi.java:548`、`:557`、`:562` | 门面聚合探针 | ✔ 已覆盖 | — | — |
| 35 | 本模组实体的生成 / 识别 | `entity/ModEntityTypes.java:29-30`、`:33-103`；构造器 public，如 `entity/BarricadeEntity.java:117`、`entity/GrapplingHookEntity.java:66` | 无门面，但可用注册表 id + `EntityType.create(Level)` 绕过（不 import 内部类） | 无 | `public static boolean isAstralDiceEntity(Entity entity)`；`public static Optional<EntityType<?>> entityType(String path)` | P2 |

## 建议实现顺序

1. **批次 A（P0，纯转发，零平台改动）**：#1、#2、#5、#11、#12（连同 #4 一起做更省事）。新增 `api/combat` 下 5 个 `@FunctionalInterface` 与 2 个 record（`DiceAttackContext`、`SpellDamageView`），`AstralDiceApi` 里每个方法体不超过 2 行转发。上下文 record 必须只由 MC 类型组成（`LivingEntity`/`ItemStack`/`DamageSource`/`Entity`），**不得**出现 `LivingDamageEvent`/`LivingIncomingDamageEvent`，否则 common 在 fabric 线编译不过。
2. **批次 B（P1，需要 `AstralDicePlatform` 扩接口）**：#21、#22、#20（补 `hasEffect`）。平台接口加 3 个 default（`effectLevel`/`effectRemainingTicks`/`applyEffect`），五线各实现一次；`effectIds()`（#23）最后做，因为要遍历各线注册器，且 26.1.2 线注册器类型不同。
3. **批次 C（P0/P1，选择器 + 友军）**：#30、#33。注意 `AstralDiceApi` 在 common 里直接 import `target.TargetSelectionManager` 只在该类五线同名时成立——**先 grep 五线确认**，否则同样走平台 default（返回 false 的保守实现）。
4. **批次 D（P1，事件）**：#25、#26，再补 #29 回调。`event/api` 已有 5 个公开类型（含 `AstralEventTier`/`AstralEventPolarity`），无需新造类型，只缺注册入口。
5. **批次 E（P1，伤害类型/结算入口）**：#17、#18、#19，全部是对 `ModDamageTypes` / `DamageCardFxScheduler` 的一行转发。
6. **批次 F（P2，查询与诊断）**：#8、#27、#28、#35、#9 的只读部分。

> 顺序理由：A 组最痛的是「扩展点存在但必须 import 内部包」，而这些扩展点本身已是「public static 注册表」，转发成本最低、收益最大；效果门面虽然同样高频，但要动五线平台接口，成本高一个量级，所以排第二。事件只用类型转发、不动结算，排第三。

## 明确不建议 API 化的项

- **#10 骰战语义判据族**（`isDiceBattleSettledDamage`/`isBlessingTarget`/`isHostileAttack`/`hasEnigmaticCurse`）：这些是结算管线的内部口径，随版本频繁修正（源码里已有 2026-10-27 的 F2 订正记录）；一旦进 API 就把实现细节冻成契约。addon 想判语义请用已覆盖的伤害钩子 + `damageCategory`。
- **#14 立牌专属法伤数值**（`effectCardDamageBonus`/`livingPageBonusPages`/`livingPageImpactDamage`/`cappedSignDamageBonus`）：绑定忍者/调查员立牌与书签筹码的具体数值，是产品内容而非扩展面；暴露会让「数值上限 120」这类内部裁决变成公共承诺。
- **#9 新增 CRIT / PENETRATION 枚举值**：全仓没有暴击 stat 的读取点（`DiceCombatEvents.java:642` 的 `if (crit) f *= 1.5` 只在注释里，`:1462` 的 crit 是落地判定），先加枚举等于承诺不存在的语义。
- **`ModEffects` 的 `DeferredRegister` / `DeferredHolder` 直接暴露**：五线注册器类型不一致（`effect/ModEffects.java:31-32` vs 26.1.2 线），必须只暴露 `ResourceLocation` + 平台方法。
- **实体的具体构造 / 内部状态读写**（`BarricadeEntity` 的 `released`、`needsBlockCheck`，`SouriDrinkEntity` 的 variant 等）：生命周期与 owner/despawn/存档耦合，外部直接摆实体必然踩不变量。只保留 #35 的识别/类型查询。
- **内部管线窗口**（`DiceCombatEvents.beginAoe/endAoe/runAsGloveSplashInstance/runAsCardSplashResolution/markCardSplashHit/injectCounterDamage/applyDodgeCancel`）：这些是递归防护与拆分结算的开关，暴露给外部等于允许破坏骰战不变量；对外只保留 #19 一个「结算一次法伤」入口。
- **`AstralEventDispatcher` 的阶级闸门 / `GlobalEventGate` / `LEGACY_IDS`**：属于玩法策略（只有 `INVESTIGATION_KILL` 能升阶、ITEM_USE 20t 去重），不应让 addon 绕过。
- **`TargetSelectionManager.Session` 结构与 `confirm(player, token, targetId)`**：token 是反作弊边界（`:237-259` 三段校验），只对外给 `start/isSelecting/cancel`。
- **法术伤害的军火黑名单**（`SpellDamageRegistry.FIREARM_PROJECTILE_PACKAGES`/`allow_firearm_damage`）：配置驱动的一致行为，不允许 addon 覆写或绕过。
- **`setDefenseArmorBonus`**（`DiceCombatModifiers.java:281`）：直接改骰战防御值，先只读（#8）。

## 重点问题速答（对应任务书 1–5）

1. **伤害结算链暴露程度**：`DamageCategory/DamagePhase/DamageHook` 已覆盖（#15/#16，NEoForge 线 2026-11-18 已装 resolver，`AstralDiceMod.java:184-186`）。**法术伤害可以注册**——`SpellDamageRegistry.registerMatcher(:112)`/`registerModifier(:116)`/`isSpellDamage(:131)`/`damageCategory(:524)` 全是 public static，但 `SpellDamageMatcher` 是内部嵌套接口、`SpellDamageModifier` 吃内部 `SpellDamageContext`（含 NeoForge 事件 + Curios）⇒ **必须 import `combat.*`，门面缺口**。`DiceCombatContext`（公开构造器 `:73`）、`DiceCombatAbilities`（`register:103`）、`DiceCombatModifiers`（`:81/:86/:153/:188`）、`DiceCombatEvents.registerDiceCombatFactor(:1760)` 都有对外注册口，全部同样卡在内部包。
2. **状态效果**：门面只有 `hasDiceBlessing` 一个 boolean（`common:api/AstralDiceApi.java:437`，实现 `platform/NeoForgeAstralDicePlatform.java:34-36`）。层数/剩余时间/施加/移除/目录 **全部没有门面**；`ModEffects` 有约 74 个效果与 `ALL` 集合（`:441`），但类型随线变化，addon 只能硬编码 id 或 import `effect.*`。层数=amplifier+1 的约定只存在于注释（`effect/ChargeEffect.java:11`、`:45`）。
3. **自定义事件**：本模组**没有**自定义 NeoForge 事件类；唯一 `.post(` 是 `item/sign/BaseSignItem.java:169-174`/`:284` 转发前置库 `SignActiveTriggeredEvent`。因此「外部订阅骰战结算」目前根本无事件可订——不是要 import 内部类，而是**能力不存在**；建议用 #29 的回调门面补，而不是新造事件类（新事件类还要处理五线注册器差异）。`event/api` 的 5 个类型本身干净，只缺注册入口（#25）。
4. **实体 / 目标**：`entity/` 有 6 个实体（`ModEntityTypes.java:33-103`），构造器 public，但无生成/查询辅助；不过用注册表 id + `EntityType.create(Level)` 可绕开内部 import ⇒ 归 P2（#35）。`target/` 的选择器能力**已够用**：库 `TargetType` 7 值 + `AstralDiceApi.matchesPlayerSelection` + `registerPlayerLike`；缺的只是「开一局选择会话」（`TargetSelectionManager.start:189` 在内部包，P0/#30）。动作注册走库侧 `TargetSelectionRegistry`，不算内部包。
5. **战斗数值 stat**：现有入口只覆盖 ATTACK/DEFENSE 两轴 × 4 阶段（`api/combat/CombatStatStage.java:14-20`），**够用于攻防但是缺查询面**（读不到当前值）与暴击/穿透轴（内部无读取点）。建议先补 `combatStatValue(...)` 只读，不要急着加枚举（理由见不建议一节）。
