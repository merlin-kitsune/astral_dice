# AstralDiceApi 全功能面覆盖审计（2026-11-18）

> 触发：用户 2026-11-18「astral dice 所有功能面全部需要 API 化，检查还有哪些项是缺失的」。
> 范围：neoforge-1.21.1 线全部源码（478 个 java 文件）+ common/ 共享根 + 五线同名类可直连性核对。
> 方法：四路只读子审计（A 战斗/伤害/效果、B 物品/卡牌/骰子/立牌/界面、C 经济/世界/数据/兼容、D 客户端/平台/api 包）+ 本文件 §8 的抽查复核（结构性结论全部重新 grep 过）。
> 判定口径：**只用 AstralDiceApi 现有 public static 或 api/ 公开类型就能完成 = 已覆盖**；必须 import 内部包（item.* / combat.* / effect.* / component.* / screen.* / target.* / event.* / economy.*）= 缺失。
> 分册（含逐项证据表与「不建议 API 化」逐条理由）：
> [A 战斗/伤害/效果](combat-damage-effects.md) · [B 物品/卡牌/骰子/立牌/界面](items-cards-dice-signs.md) · [C 经济/世界/数据/兼容](economy-world-data-compat.md) · [D 客户端/平台/api 包](client-platform-api-package.md)

## 1. 总账

| 组 | 覆盖范围 | 功能面 | 已覆盖 | 部分覆盖 | 缺失 | 明确不 API 化 |
|---|---|---|---|---|---|---|
| A | combat / damage / effect / event / entity / target | 35 | 6 | 2 | 27 | 0 |
| B | item(card/dice/chip/sign) / component / screen / choice / data | 40 | 6 | 4 | 30 | 0 |
| C | economy / shop / trade / world / block / data / command / config / network / compat | 27 | 12 | 3 | 8 | 4 |
| D | client / platform / api 包 / mixin / fixes | 20 | 5 | 4 | 9 | 2 |
| **合计** | | **122** | **29** | **13** | **74** | **6** |

已覆盖的 29 项集中在：内容四表注册与查询、钱包、伤害大类与四阶段钩子、类玩家（含发牌/发物品/能力位）、骰子物品识别与卡槽/星级、立牌识别与激活、骰战参与者、敌对探针、暴击/穿透 stat 注册。
缺失的 74 项里，**真正每天卡住 addon 的是 §3 的十条 P0**，其余是常见扩展需求（§4）与锦上添花（分册 P2）。

## 2. 结构性结论（比逐项缺口更重要）

| # | 结论 | 证据 | 影响 |
|---|---|---|---|
| S1 | **注册面有、消费面不认**：registerSign / registerChip 只写 StarEngine 的 Signs / Chips 表，本线**零处读取**这两张表 | 全仓 Signs. / Chips. 共 17 处命中，**全部在 AstralDiceApi.java 自身**（:117 :122 :130 :135 :156 :161 :166 :171 :181 :617…） | 附属注册立牌/筹码描述符后**不会生效**；立牌冷却实际由 item/sign/BaseSignItem.java:401 activeCooldownBaseTicks(Player) 决定，描述符里的 activeCooldownTicks 无人读 |
| S2 | **门面泄漏内部类型**（B6 已收口，见 §6.10）：5 参 registerDiceTier 返回 com.merlinkitsune.astral_dice.item.dice.DiceTier（非 api 包）；diceTier(id) 返回的却是 StarEngine 的同名异类 | AstralDiceApi.java:85-86、:104；common/.../item/dice/DiceTier.java:26 是 record | 两个 DiceTier 同名不同包，addon 极易混用；且 DiceTier 携带 Supplier<Item>，不适合作为稳定契约 |
| S3 | **五线可直连性不齐**（B6 已收口：ChipEquip 补满五线，SignHooks 声明仅三线，见 §6.10）：CardUseService（2/5，仅 neo-1.21.1 + forge-1.20.1）、ChipEquip（2/5）、SignHooks（3/5）、OpenCardInventoryPayload（2/5） | 五线 glob 实测 | 这四类**不能**写进 common/api 直连；卡牌使用/筹码装配/立牌回调必须走平台 default 或先在缺线补齐 |
| S4 | **玩家发物品走不通**：giveItemTo 对真玩家恒 false，且绕过中央漏斗 ResourceConversion.giveItem | target/PlayerLikeTargets.java:167 if (receiver instanceof Player) return false;；resource/ResourceConversion.java:58-65 | addon 给玩家发星币只能自己 inventory.add，不会自动入袋，与模组内所有发币路径分叉 |
| S5 | **第三方骰子没有原生 tooltip**：判据是 13 个 ModItems.*_DICE 的硬编码 stack.is(...) 串联 | event/ModTooltipHandler.java:348-353 | 经 registerDiceTier 注册的骰子星级/费用/石子提示全丢；换成 hasDiceTier(stack) 即可，零签名变更 |
| S6 | **门面零侧别契约**：common 全包 isClientSide / Dist.CLIENT / assertServer / LogicalSide **0 命中** | 实测 grep | refreshDiceChipSlots（改 Curios 槽位）、giveCardTo、钱包四方法都是服务端语义，既无 javadoc 也无校验，addon 易在客户端误调 |
| S7 | **没有可订阅的自定义事件**：全仓唯一 .post( 在 item/sign/BaseSignItem.java:169-174、:284，且不对外 | event/ 43 个文件全是内部事件系统（星界事件卡是抽卡玩法，不是对外事件总线） | 外部不是「订阅要 import 内部类」，而是**没有事件可订**；需要回调门面（registerCombatResolutionListener 一类） |
| S8 | **卡牌闭环全在 GUI 私有方法里**：装配 = screen/CardInventoryMenu.java:302 saveToDice()（private），读取 = :263 loadFromDice()（private），关闭菜单时 :447 removed() → :449 saveToDice() | 同上 | addon 无法编程装配/读取骰子卡牌；且装配即**销毁卡牌物品栈**（:337 注释），语义必须逐行等价复制，不能各写一套 |

## 3. P0 缺口（十条，addon 现在就得靠反射/照抄）

| # | 能力 | 现状与证据 | 建议 API | 涉及线 |
|---|---|---|---|---|
| P0-1 | 卡牌 → 骰子装配 / 卸下 / 负载读取 | screen/CardInventoryMenu.java:302/:263/:256 全 private；数据在 component/WeaponEnhancement.java:12 + component/AppliedStone.java:29 | boolean equipCard(ItemStack dice, ItemStack card)、boolean unequipAllCards(ItemStack dice)、DiceLoadout diceLoadout(ItemStack dice)、List<ItemStack> returnAllCards(ItemStack dice)；配套 api mirror record DiceLoadout / InstalledCard（只用 String/int/UUID） | 五线（新增同签名 item/card/DiceCardLoadout 服务类后 common 直连） |
| P0-2 | 对生物 / 玩家使用效果牌 | item/card/CardUseService.java:25 useCardFor 要 ServerPlayer；:40 useCardForEntity 是 static 桩（只 WARN + false，无覆写者） | boolean useCardOn(LivingEntity user, ItemStack card)；玩家走 ItemStack#use，非玩家经 AstralDicePlatform#useCardOnEntity default false | 五线（玩家分支纯 common；非玩家分支缺 3 线） |
| P0-3 | 骰战攻击 / 防御 / 额外 / 受击修饰器 | combat/DiceCombatModifiers.java:81/:86/:153/:188 public static，但上下文 DiceCombatContext 是内部类型且吃 LivingDamageEvent.Pre | registerAttackModifier / registerDefenseModifier / registerExtraDamageModifier / registerVictimDamageModifier，上下文改成纯 MC 类型 record DiceAttackContext | 纯 common（5/5 同名） |
| P0-4 | 骰战最终伤害因子 | combat/DiceCombatEvents.java:1753（接口）:1760（注册）:865-866（应用），注释明写「附属可注册」 | registerFinalDamageFactor(DiceFinalDamageFactor) | 纯 common |
| P0-5 | 法术伤害作用域 / 修饰器 | combat/SpellDamageRegistry.java:112/:116/:131/:524 public static；SpellDamageMatcher 是内部嵌套接口，SpellDamageContext 吃 LivingIncomingDamageEvent + Curios | registerSpellDamageMatcher(SpellDamageMatcher)、registerSpellDamageModifier(...) + record SpellDamageView(Player, LivingEntity, DamageSource, Entity) | 纯 common |
| P0-6 | 目标选择会话 start / confirm / cancel | target/TargetSelectionManager.java:189/:237/:132/:167 public static，须 import target.*；confirm 走网络包 | int startTargetSelection(ServerPlayer, String actionId)、boolean isSelectingTarget(Player)、void cancelTargetSelection(Player, String reason) | 纯 common（5/5） |
| P0-7 | 骰子筹码格数 / 阶层视图 | item/dice/DiceCurioItem.java:223 targetChipSlots 为 private static；门面只有 diceCardSlots | int diceChipSlots(ItemStack)、boolean hasDiceTier(ItemStack)、String diceTierId(ItemStack)、record DiceTierView(String id, int cardSlots, IntUnaryOperator chipSlots) | 纯 common |
| P0-8 | 第三方骰子 tooltip | event/ModTooltipHandler.java:348-353 硬编码 13 个 ModItems.*_DICE | 不是新 API：把判据换成新 hasDiceTier(stack)（P0-7） | 五线各改一处（纯内部实现） |
| P0-9 | 星币物品面 | economy/StarCoinCurrency.java:48 valueOf、:64 isCurrency、:92 countCarriedValue 全 public static 但在 economy.*；门面钱包只收 long | boolean isStarCoin(ItemStack)、long starCoinValue(ItemStack)（星币 1 / 星币袋 9 / 其它 0）、long countCarriedStarCoins(Player) | 五线（经 AstralDicePlatform default 0 转发，避免缺类） |
| P0-10 | 门面侧别契约 | common 0 处侧别判断（§2 S6） | 全部服务端方法标 [服务端] javadoc + 私有 requireServer(operation)；双端方法标 [双端] | 纯 common |

## 4. P1 缺口（常见扩展需求，合并同类后 12 条）

| # | 能力 | 证据 | 建议 API |
|---|---|---|---|
| P1-1 | 状态效果读层数/剩余时长/施加/目录 | effect/ChargeEffect.java:29/:45、effect/ModEffects.java:441；门面只有 hasDiceBlessing bool | effectLevel(LivingEntity, String id)、effectRemainingTicks、applyAstralEffect(...)、effectIds()（须经平台 default，五线 DeferredHolder/RegistryObject 类型不同） |
| P1-2 | 星界事件注册 / 触发 / 查询 | event/AstralEventRegistry.java:47/:56、event/AstralEventDispatcher.java:52/:63 | registerAstralEvent(...)、triggerAstralEvent(Player, String id, ...)、astralEvent(String) |
| P1-3 | 伤害类型与直接结算入口 | damage/ModDamageTypes.java:14-17/:26-29/:50-53/:73-76、combat/DamageCardFxScheduler.java:622 | DamageType damageType(String)、dealUnreducibleDamage(...)、dealCardSpellDamage(ServerPlayer, LivingEntity, int) |
| P1-4 | 友方判定与收集 | combat/TeamTargets.java:24/:41 | isFriendly(Player, Entity)、collectFriendlyLiving(Player, double radius) |
| P1-5 | 战斗结算回调 | §2 S7；现无任何对外事件 | registerCombatResolutionListener(CombatResolutionListener)（回调，不新造 NeoForge 事件） |
| P1-6 | 立牌激活结果要带原因 | SignEntitySkills.deny 5 个 reason；门面 activateSign 只回 boolean，玩家恒 false | SignActivation(boolean ok, String reason, int cooldownTicksRemaining) activateSignResult(...)（保留旧 boolean 方法） |
| P1-7 | 给玩家发物品的中央漏斗 | §2 S4 | boolean giveItemToPlayer(Player, ItemStack)（内部走 ResourceConversion.giveItem） |
| P1-8 | 配置读取面 | config/ModCommonConfig.java:94/:95 等在加载器线源码根，common 引用不到 | walletFeatureEnabled()、autoDepositOnObtain()、actionBarDurationTicks() 一类（经平台 default） |
| P1-9 | 打开卡牌装配界面 | network/OpenCardInventoryPayload 仅 2/5 线 | openCardInventory(ServerPlayer)（Player#openMenu 五线通用，可落 common） |
| P1-10 | 客户端 HUD 层 / 按键注册 | client/ 39 文件无公开注册器 | registerHudLayer(...)、registerKeyMapping(...)（平台 default，客户端侧） |
| P1-11 | 平台 default 静默 no-op 可观测性 | AstralDicePlatform 三个新默认方法失败即静默返回 false | 失败记一次 debug 或提供 platformCapabilities() 查询 |
| P1-12 | 三选一卡牌选择开窗 | choice/CardChoiceManager.java:94/:108/:187 public static，须 import choice.* | int startCardChoice(ServerPlayer, int slots, ...) + 回调注册 |

## 5. 明确不 API 化（6 项 + 理由要点）

- **商店 / 筹码购买**（shop/ 13 文件）：会连菜单协议（ChipShopMenu.handleBuy:356 ↔ 12 条 C2S）、存档形状（ChipShopState CODEC）、候选概率（ChipShopCatalog.pickWeighted:376）一起冻成契约；且用户 2026-11-18 已裁决 TLMDice 不用商店、远程商店后续单独设计。
- **mixin / fixes**（26 个 mixin）：逐版本签名不同，API 化等于给每次 MC 升级加兼容包袱。
- **粒子 179 变体 / 音效库**：美术与音效编号，暴露即兼容承诺。
- **骰战语义判据族**、**事件阶级闸门与 LEGACY_IDS**、**选择会话 token 结构**：口径随版本漂移，属内部语义。
- **ModEffects 的 DeferredHolder / RegistryObject**：五线类型不一致，只能经平台方法表达，不直接暴露。
- **实体内部状态**（BarricadeEntity.released/needsBlockCheck 等）与 **AOE/递归防护开关**：调试用的内部旋钮。

## 6. 建议实施批次

每批都是「可独立验证、可独立提交」的单元；按仓库版本纪律，每批改产品代码后五线 mod_version 统一 bump（本审计落盘后从 .85 起），CHANGELOG 双语同步。

| 批次 | 内容 | 改哪里 | 验收 |
|---|---|---|---|
| **B1 数据契约 + 卡牌闭环（P0-1/2/7/8）** | 新增 api/data/DiceLoadout、api/data/InstalledCard；五线新增同签名 item/card/DiceCardLoadout（read/write/equip/unequipAll，语义逐行等价 CardInventoryMenu#saveToDice）；门面 diceLoadout / equipCard / unequipAllCards / useCardOn / diceChipSlots / hasDiceTier；ModTooltipHandler 判据换 hasDiceTier | common + 五线 | 五线 assemble；KubeJS 探针「装卡→读负载→卸卡→卡牌回到背包」逐值对账 |
| **B2 战斗注册口（P0-3/4/5）** | api/combat 新增 4 个修饰器接口 + 2 个法术接口 + DiceAttackContext/SpellDamageView record；门面 6 个注册方法转发 | ~~纯 common~~ **五线各一份适配器**（§6.2 修正） | 五线 assemble；用一个测试 addon/KubeJS 注册后打一次骰战与法术，读 final 变化 |
| **B3 选择器 / 效果 / 事件（P0-6 + P1-1/2/5/12）** | startTargetSelection 等三方法；效果面经 AstralDicePlatform 新 default + 五线实现；registerAstralEvent；registerCombatResolutionListener；startCardChoice | common + 五线（效果） | 五线 assemble；实机目标选择一次 + 效果层数读数 |
| **B4 经济与配置（P0-9 + P1-7/8）** | isStarCoin / starCoinValue / countCarriedStarCoins / giveItemToPlayer / 配置读取面（平台 default） | common + 五线 | 五线 assemble（重点 fabric 两线：先确认 StarCoinCurrency 是否缺失，再走 default） |
| **B5 客户端面（P1-9/10/11 + P0-10）** | openCardInventory；registerHudLayer / registerKeyMapping 平台 seam；门面侧别 javadoc + requireServer | common + 五线 | 客户端启动 + 一次界面打开；client 类只被客户端类加载（避免专用服务器炸类） |
| **B6 类型整理（S2/S3）** | registerDiceTier 返回 DiceTierView（保留旧方法 @Deprecated）；缺线的 CardUseService / ChipEquip / SignHooks 要么补线要么正式声明「仅 neo/forge」 | 纯 common (+ 缺线) | 五线 assemble + 旧签名保留检查 |

### 6.1 B1 落地记录（26.4-SNAPSHOT.86，2026-11-18）

- **新增数据契约**（common）：`api/data/InstalledCard`（type / uses / temporary / owner / defense）、`api/data/DiceLoadout`（starLevel / cardSlots / attackSlots / 攻防两侧已用与上限 / cards，`EMPTY` 常量）。
- **新增服务**（common，一份）：`item/card/DiceCardLoadout` —— `read` / `canEquip` / `equip` / `unequipAll`，语义逐行对齐 `screen/CardInventoryMenu#saveToDice`（:302）与 `#loadFromDice`（:263）：攻/防各占一半槽位、各自独立计费、上限取 `GameplayConstants.cardCostForStar(DiceCurioItem.configStarLevel(dice))`、持久化写骰子**实际**星级、临时牌标记随 `AppliedStone` 保存。
- **新增门面**（`AstralDiceApi`）：`diceLoadout` / `canEquipCard` / `equipCard` / `unequipAllCards` / `useCardOn` / `diceChipSlots` / `hasDiceTier`。
- **五线补齐**：`item/card/CardUseService` 此前只有 neo-1.21.1 与 forge-1.20.1，本批补 fabric-1.20.1 / fabric-1.21.1 / neoforge-26.1.2（26.1.2 的 `ItemStack#use` 返回 `InteractionResult`，该线单独一份实现）；`DiceCurioItem#chipSlots` 五线新增公开只读入口（内部仍走 `targetChipSlots`）。
- **版本差异垫片**：数据组件 API 与掉落签名在五线不一致（neo-1.21.1 / neoforge-26.1.2 用原版 `DataComponentType`；forge-1.20.1 与两线 fabric 用 starengine `ItemDataKey` NBT shim；26.1.2 的 `spawnAtLocation` 需要 `ServerLevel`）。新增每线一份 `item/dice/DiceLoadoutAccess`（`enhancement` / `setEnhancement` / `cardUses` / `setCardUses` / `drop`），common 侧因此保持单份。
- **tooltip 判据**：五线 `event/ModTooltipHandler` 的 13 连硬编码 `stack.is(ModItems.*_DICE)` 换成 `AstralDiceApi.hasDiceTier(stack)`（13 件骰子全部登记在 `DiceTierRegistry`，附属骰子从此也有费用 / 星级 / 卡牌行）。
- **仍未闭环（诚实边界）**：`useCardOn` 对非玩家实体仍落到 `CardUseService.useCardForEntity` 的默认 `false` —— 效果牌尚不支持类玩家宿主，属 P0-2 的另一半，需 B3（或单独一批）把 `BaseEffectCardItem` 的 `applyEffect` 目标放宽；`equipCard` 只改骰子数据、**不扣卡牌栈**（界面里「装了就没了」由调用方扣）；`unequipAllCards` 对非玩家宿主无法还原临时牌标记（标记 API 需 `Player`）。
- **验收状态**：五线 `assemble --offline` BUILD SUCCESSFUL（.86 五件产物均含新类与七个新门面方法，已用 `javap` 核对）；审计定的「KubeJS 探针 装卡→读负载→卸卡→卡牌回到背包 逐值对账」**尚未跑**，需一次 dev 客户端实机。

### 6.2 B2 落地记录（26.4-SNAPSHOT.87，2026-11-18）

- **新增只读视图**（common）：`api/combat/DiceBattleView`（attacker / target / baseDice / diceStack / starLevel / triggeredBlessing / misakiBurst / misakiStar / misakiStacks，null 骰子折成 `ItemStack.EMPTY`）、`api/combat/SpellDamageView`（attacker / target / source / directEntity）。
- **新增函数式接口**（common）：`AttackPowerModifier`、`DefensePowerModifier`、`ExtraDamageModifier`、`VictimDamageModifier`、`DiceCombatFactor`、`SpellDamageMatcher`、`SpellDamageModifier`（`isActive` / `apply` / `onHit` 三钩子，前两个有默认实现）。
- **新增门面**：`registerAttackModifier` / `registerDefenseModifier` / `registerExtraDamageModifier` / `registerVictimDamageModifier` / `registerDiceCombatFactor` / `registerSpellDamageMatcher` / `registerSpellDamageModifier`；非 null 才转发，各打一条 `AP_COMBAT_API: registered=<kind>`，平台未安装时 `requirePlatform` 抛带调用名的 `IllegalStateException`。
- **计划修正（重要）**：审计把 B2 定为「纯 common」不成立。`DiceCombatContext` / `SpellDamageContext` / `DiceCombatModifiers` / `SpellDamageRegistry` / `DiceCombatEvents` **每线各一份**（挂着各自的 `LivingDamageEvent.Pre` / `LivingIncomingDamageEvent`），common 无法引用。故每线新增等价 `combat/CombatApiAdapters`（视图 ↔ 本线上下文）、`AstralDicePlatform` 加 7 个 default、五线平台实现转发。五份适配器内容一致，只依赖五线同名同签名的内部接口。
- **语义对齐**：攻击 / 防御修饰器按注册顺序作用于数值；`ExtraDamageModifier` 只算命中后的独立加伤，不并入攻击力（避免污染依赖攻击力快照的效果）；`VictimDamageModifier` 覆盖**任何来源**的伤害且须实时判定；`DiceCombatFactor` 是骰战最终伤害乘算；法术三钩子顺序 isActive → apply（累积加算）→ onHit。
- **诚实边界**：视图只读，附属不能回写内部上下文的「结果写入区」字段（targetEnhancement / attackCardSum / hasShadowStrike 等）；`DiceCombatFactor` 与法术两接口的 attacker 是 `Player`，类玩家宿主不在通道内；注册须在初始化阶段（内部表是普通 `ArrayList`）；**未做**「让生物发起远程 / 魔法攻击」的发起入口（P0-5 只解决识别与加成），本批未跑实机。
- **验收状态**：五线 `assemble --offline` BUILD SUCCESSFUL；`javap` 核对产物含 7 个门面方法、7 个平台 default、`CombatApiAdapters` 与 9 个 `api/combat` 类。

### 6.3 实机验收记录（B1 / B2，26.4-SNAPSHOT.86/.87，2026-11-18）

- **环境**：`./gradlew.bat :neoforge-1.21.1:runClient -Pquickplay=probe --offline`（存档 probe），AutoJS `127.0.0.1:25585/mcp`，玩家 Dev（op）；探针全部在 server 侧 Rhino eval 里跑，直接调门面而非 GUI。
- **B1 读数**（`astral_dice:nether_star_dice`）：`hasDiceTier=true`、`diceChipSlots=4`；空骰 `{star:0, cards:0, cardSlots:12, attackSlots:6, maxAttackCost:3, maxDefenseCost:3}`；`canEquipCard(attack_card_medium)=true`、`canEquipCard(defense_card_medium)=true`；两次 `equipCard` 均 `true`，装后 `cards=2`（`medium uses=10 def=false`、`defense_medium uses=150 def=true`）、`usedAttackCost=1`、`usedDefenseCost=1`、`starLevel` 仍 0；`unequipAllCards` 返回 2，`cards=0` 且两项费用归零，玩家背包 `attack_card_medium` 0→1（卡牌确实回到背包）。**B1 全部通过。**
- **B2 注册锚点**：七个门面各注册一次，日志七条 `AP_COMBAT_API: registered=attack_modifier / defense_modifier / extra_damage_modifier / victim_damage_modifier / dice_combat_factor / spell_damage_matcher / spell_damage_modifier`。`SpellDamageModifier` 只有一个抽象方法（`apply`），Rhino 可直接传函数，不必 `Java.extend`。
- **B2 行为断言**（每项都是门面注册后由游戏内真实事件消费）：

  | 通道 | 注册的回调 | 触发方式 | 读数 |
  |---|---|---|---|
  | `VictimDamageModifier` | 目标带 tag `b2v` 返回 3.0 | `/damage 4 minecraft:generic` | 带标签掉 **12**、对照掉 **4**（恰好 ×3，覆盖任意来源） |
  | `ExtraDamageModifier` | 目标带 tag `b2big` 返回 1000 | 玩家持钻石剑 + curios 骰子近战 | 骰战一击打死（掉 20），对照同条件掉 **1** |
  | `DiceCombatFactor` | 目标带 tag `b2fac` 返回 `damage+1000` | 同上 | 骰战一击打死 |
  | `AttackPowerModifier` | 目标带 tag `b2atk` 返回 `power+1000` | 先把 `attack_card_large` 用 `equipCard` 装进骰子（`cards=1`）再近战 | 骰战一击打死（B1/B2 互操作） |

- **骰战确实发生**：攻击后玩家获得 `effect.astral_dice.dice_blessing`（`p.getActiveEffects()` 出现该描述 id），同一条件下掉血 5.41 / 1（骰点随机，故不用逐值对拍，用「+1000 必杀」这种不易被噪声淹没的信号）。
- **未做行为断言的通道**（诚实边界）：`DefensePowerModifier`（需防御牌在受击侧骰战里结算）、`SpellDamageMatcher` / `SpellDamageModifier`（需第三方法术源）——只验证了注册锚点，未做行为断言。
- **取证坑（下次直接用）**：Rhino 里 `player.level` 是属性不是方法（写 `p.level()` 报 `Cannot call property level ... it is "object"`）；1.21.1 的 `LivingEntity#hurt` 是 protected，脚本里只能改用 `/damage` 命令；白天僵尸会被晒死污染读数，改 `minecraft:husk`；`/attribute ... base set 200` 只改上限不改当前血量，需要用 `data merge entity ... {Health:200f}` 或直接按 20 血对拍。

### 6.4 B3 落地记录（26.4-SNAPSHOT.88，2026-11-18）

- **新 common 契约**：`api/combat/CombatResolutionView`（record：attacker/target/finalDamage/landingDamage/extraDamage/diceStack/starLevel，空栈归一为 `ItemStack.EMPTY`）与 `CombatResolutionListener`（`onResolution(view)`）；`CombatResolutionListeners` 负责注册与派发，`CopyOnWriteArrayList` 保存监听器，`ThreadLocal` 重入闸门阻止监听器在自身回调里再触发自己，异常只记 `AP_COMBAT_API: combat_resolution_listener failed`。
- **门面新增 16 个方法**：目标选择五个（`startTargetSelection` / `isSelectingTarget` / `targetSelectionToken` / `confirmTargetSelection` / `cancelTargetSelection`）；选卡两个（槽位数、最小槽位数两个重载，返回 token，拒绝返回 `-1`）；事件四个（`registerAstralEvent` / `astralEvent` / `triggerAstralEvent` 带与不带星级拥有者）；结算监听一个；效果四个（`effectLevel` / `effectRemainingTicks` / `applyAstralEffect` / `astralEffectIds`）。效果、事件查找与事件触发经 `AstralDicePlatform` 六个新 default 落到五线平台实现。
- **五线差异与垫片**：`ModEffects.ALL` 在 neo 两线是 `DeferredHolder`、forge/fabric-1.20.1 是 `net.minecraftforge.registries.RegistryObject` / 平台自己的 `platform.registry.RegistryObject`、fabric-1.21.1 走 `Effects.holder(...)`；事件 id 解析在 26.1.2 用 `Identifier.tryParse`、其余用 `ResourceLocation.tryParse`。平台实现里对 holder 统一走 `Holder<MobEffect>` 接口（neo 两线需要一次 `(Holder<MobEffect>) (Holder<?>)` 强转）。
- **结算触发点**：两条骰战路径（玩家攻击与生物攻击）的额外加伤在旧代码里是块内局部变量，B3 在其外层新增 `extraSettled` / `g6ExtraSettled` 快照，随后在赐福与攻击牌耐久步之后 `fire` 一次；玩家路径的 `landingDamage` 取各线已有落地量（neo `landing`、forge/fabric `applied`、26.1.2 直接用 `finalDmg`），生物路径现算 `finalDmg - min(finalDmg, absorption)`（26.1.2 同样直接用 `finalDmg`）。
- **诚实边界**：结算视图是只读快照，不能改伤害；监听器只在骰战触发，原生伤害不触发；目标选择 / 选卡只暴露 token，不提供界面控制；`triggerAstralEvent` 仍受内部派发器冷却与资格约束；`startTargetSelection` 等需要 `ServerPlayer`。
- **验收状态**：五线 `assemble --offline` BUILD SUCCESSFUL；`javap` 核对五份产物均含 16 个 B3 门面方法；实机断言见 §6.5（目标选择、选卡、效果、结算监听、星界事件五条通道全部通过）。

### 6.5 实机验收记录（B3，26.4-SNAPSHOT.88，2026-11-18）

- **环境**：`./gradlew.bat :neoforge-1.21.1:runClient -Pquickplay=probe --offline`（存档 probe，日志 `work/runclient-88.log`），AutoJS `127.0.0.1:25585/mcp`，玩家 Dev（op，默认创造）；探针全部在 server 侧 Rhino eval 直调门面。
- **目标选择（通过）**：`startTargetSelection(p, "moses_apply_broken")` → `true`；`isSelectingTarget=true`、`targetSelectionToken=654025795` 与 `TargetSelectionManager.sessionTokenForTests(p)` 一致；错误 token 确认 → 会话保留、仍 selecting；正确 token 确认 → selecting 清除、目标获得 `effect.astral_dice.moses_broken`（Duration 2400）；再 start 后 `cancelTargetSelection(token)` → 会话清除；未知动作 id → `false`。选 `teru_descent`（PLAYER 型）打尸壳会得到 `confirm FAIL: target_type_mismatch`，属目标类型约束而非缺陷。
- **选卡（通过）**：`startCardChoice(p, 99, 1, ANY, cb)` → `-1`（槽位数越界）；`startCardChoice(p, 2, 1, ANY, cb)` → token `2051823661`，`CardChoiceMenu` 的 token/slotCount/minSlots 与请求一致；重复 start → `-1`（`already_open`）；`quickMoveStack` 填 1 张后 `isComplete=true`，`confirm` 回调收到 `astral_dice:attack_card_medium ×1`、会话关闭、菜单槽清空；minSlots=2 只填 1 张时 `confirm` 被拒（`confirm_ignored:reason=incomplete`，回调 0 次、会话保留）；`abandon` 后会话清除。日志与 `AP_CARD_CHOICE: open/confirm/abandon` 三条一致。
- **效果（通过）**：`astralEffectIds().size()==75`；`applyAstralEffect(husk, "astral_dice:moses_broken", 400, 2)` → `true`，随后 `effectLevel=3`（amplifier+1）、`effectRemainingTicks=400`；未知 id → level `0`（不抛异常）。
- **结算监听（通过）**：`registerCombatResolutionListener` 后日志出现 `AP_COMBAT_API: registered=combat_resolution_listener`；B1 门面把 `nether_star_dice` 放进 curios dice 槽、`equipCard(attack_card_medium)` 成功（`cards=1`），`p.attack(husk)` 一次攻击收到 12 条视图：`finalDamage=14 / landingDamage=14 / extraDamage=0 / starLevel=0 / attacker=Dev / target=尸壳`，另有僵尸 13 与 8、7 两档；尸壳 20→6。
- **星界事件（通过）**：`astralEvent("astral_dice:red_heat_warning")` 返回完整对象（polarity=POSITIVE、radius=64、duration=3600、`allows(ITEM_USE)=true`、`allows(INVESTIGATION_KILL)=false`）；未知 id → `null`。`triggerAstralEvent(p, "astral_dice:battle_frenzy", ITEM_USE)` → `true`（该公开事件效果为 NOOP 空壳，故效果栏不变）；未知 id → `false`；带星级拥有者重载（`p,p,swift_stride,CUSTOM`）→ `true`。自定义事件：用 `HashSet` 构造 `AstralEvent`（id `astral_dice:b3_custom_event`、只允许 CUSTOM、radius 16、duration 100）经 `registerAstralEvent` 注册，日志 `AP_EVENT_API: registered=astral_dice:b3_custom_event`，`astralEvent` 能查回且 `allows(CUSTOM)=true / allows(ITEM_USE)=false`；`triggerAstralEvent(..., CUSTOM)=true`、错用 ITEM_USE=`false`。
- **观察到的既有行为（不是 B3 门面缺陷）**：创造模式下选卡会净 +1 张——`hasInfiniteMaterials()` 使 `ItemStack.consume` 成为 no-op，而 picked 副本照发、`removed()` 又把余量退回；同一流程切生存后 before=1 / during=0 / after=0 / picked=1，消耗正确。
- **诚实边界**：效果牌内部 `SelectorAction` 是 CREATURE 型，但 `apply` 依赖 `SignSelectionGate.Pending`，直接 API `startTargetSelection` 没有 pending 时会空转——纯 API 可断言的路径目前是立牌 ENEMY 动作；结算监听只在骰战触发、原生伤害不触发；事件触发仍受派发器冷却与资格约束。
- **取证坑（本批新增）**：Rhino 里 try 块内的 `function` 声明不提升（用 `var f = function(){}`）；全局没有 `java`（`java.util.ArrayList` 报 undefined，Consumer 回调用 JS 数组 + 函数即可）；`stack.is(item)` 二义性报错，改用 `String(st.getItem())` 比较；`menu.getClass()`、`player.closeContainer()` 不可调用（protected），判菜单用 `containerMenu.token()` try/catch；`/summon` 的 `Health:200f` 与 `Attributes[max_health=200]` 都不会把尸壳血量抬过 20，选目标必须靠 `Tags` 过滤而不是血量。

### 6.6 B4 落地记录（26.4-SNAPSHOT.89，2026-11-18）

- **新增门面（10 个）**：`isStarCoin`、`starCoinValue`（1 / 9 / 0）、`countCarriedStarCoins`（物品栏 36 格 + 副手，按面额折算）、`depositCarriedStarCoins`（入账并移除，返回实际入账额）、`giveItemToPlayer`（中央漏斗），以及配置只读 `walletFeatureEnabled` / `autoDepositOnObtain` / `firearmDamageAllowed` / `actionBarDurationTicks` / `actionBarFadeTicks`。
- **平台 seam**：`AstralDicePlatform` 新增 8 个 default（`starCoinValue`、`depositCarriedStarCoins`、`giveItemToPlayer`、三个配置读取、两个 actionbar 时长），五线平台实现各 8 个 `@Override`，分别转调 `StarCoinCurrency.valueOf` / `StarCoinCurrency.depositAllFromInventory` / `ResourceConversion.giveItem` / `StarCoinCurrency.isWalletEnabled` / `StarCoinCurrency.isAutoDepositEnabled` / `ModCommonConfig.ALLOW_FIREARM_DAMAGE.get()` 与两个常量。
- **为什么不把统计放平台**：`countCarriedStarCoins` 只需 `starCoinValue` 与 `Player#getInventory`，留在 common 一份即可，避免五线重复实现同一段遍历逻辑。审计预判「重点 fabric 两线先确认 StarCoinCurrency 是否缺失」——实读五线都有 `economy/StarCoinCurrency.java` 且签名一致，故没有走「缺线用 default」的兜底。
- **语义对齐**：星币面额来自 `StarCoinCurrency.STAR_COIN_VALUE=1L` / `STAR_COIN_BAG_VALUE=9L`；`depositCarriedStarCoins` 与 `depositAllFromInventory` 同语义（先入账、后清物）；`giveItemToPlayer` 走 `ResourceConversion.giveItem`，星币吸收、入包、掉地三段顺序与内部一致。
- **诚实边界**：平台 default 未接线时静默返回 `0` / `false`（门面无法区分「不是货币」与「没接线」）；`giveItemToPlayer` 在客户端或空栈时返回 `false`；`starCoinValue` 只认星币/星币袋；配置只读，无 setter；铁砧费用、战利品规则、actionbar 显示助手（P2 备选）未做。
- **验收状态**：五线 `assemble --offline` BUILD SUCCESSFUL；`javap` 核对五份产物均含 10 个 B4 门面方法与 8 个平台 default。KubeJS 实机读数尚未跑（发币入袋 / 携带面额 / 配置开关）。

### 6.7 实机验收记录（B4，26.4-SNAPSHOT.89，2026-11-18）

- **环境**：`./gradlew.bat :neoforge-1.21.1:runClient -Pquickplay=probe --offline`（存档 probe，日志 `work/runclient-89.log`），AutoJS `127.0.0.1:25585/mcp` server 侧 Rhino eval，玩家 Dev。
- **配置只读（通过）**：`walletFeatureEnabled=true`、`autoDepositOnObtain=true`、`firearmDamageAllowed=false`（该配置默认关闭，不是门面缺陷）、`actionBarDurationTicks=60`、`actionBarFadeTicks=10`。
- **面额识别（通过）**：`starCoinValue` 对星币 = 1、星币袋 = 9、苹果 = 0；`isStarCoin` 相应为 `true/true/false`。
- **携带面额与入袋（通过）**：清空背包后直接放入 3 星币 + 2 星币袋，`countCarriedStarCoins=21`（面额折算，不是 5 件）；`walletBalance=0` 时调 `depositCarriedStarCoins` 返回 21，钱包 0→21，背包再统计为 0。
- **发币漏斗（通过）**：`giveItemToPlayer(p, 苹果×3)` 返回 `true`，背包出现 3 个苹果；`giveItemToPlayer(p, 星币×5)` 返回 `true`，钱包 21→26（`walletDelta=5`），背包里星币数量为 0 —— 星币确实走了「吸收进钱包」而不是进包。
- **诚实边界**：本轮只断言了 neoforge-1.21.1 的 server 侧；其它四线只做到编译与 `javap` 核对，未逐线进游戏。手枪伤害开关的读数只证明「读到配置」，没有触发枪弹伤害做行为断言。

### 6.8 B5 落地记录（26.4-SNAPSHOT.90，2026-11-18）

- 新契约（common）：`api/client/AstralHudLayer`（函数式，`render(Object graphics, float partialTick)`）、`AstralHudLayers`（字符串 id、拒绝空/重复、`pathOf`、`registered()` 有序快照、`Registration` record）、`AstralKeyBindings`（`Request(id, defaultKeyCode)`、`translationKey()` 取 path）。
- 平台 seam：`AstralDicePlatform.openCardInventory(ServerPlayer)` default 返回 `false`；五线实现转发本线 `screen/ModMenuTypes.openCardInventory`。neo 两条线原本只有 payload handler，已把逻辑搬进 `ModMenuTypes` 并让 handler 转调；forge 1.20.1 与两条 fabric 原本是 `void`，改为 `boolean`（未佩戴骰子提示并返回 `false`）。
- 客户端注册：五线 `client/ModClientEvents` 在既有 GUI 注册方法末尾追加 HUD 循环（锚 `CROSSHAIR`），在既有按键注册方法末尾追加按键循环；1.20.1 形状用 `IGuiOverlay` lambda，1.21.1 用 `LayeredDraw.Layer`，26.1.2 用 `GuiLayer`。
- 门面：新增 `openCardInventory`；新增私有 `requireServer(String, Entity)`；类 javadoc 增加「侧别」段。已加守卫：`giveCardTo` / `giveItemTo` / `refreshDiceChipSlots` / `walletDeposit` / `walletWithdraw` / `walletTransfer` / `playerLikeWalletWithdraw` / `playerLikeWalletDeposit` / `openCardInventory`。
- 五线差异与垫片：common 不能引用 `GuiGraphics` / `GuiGraphicsExtractor` / `Identifier`，故 HUD id 用 `String`；1.20.1 的 overlay id 只接受 path、命名空间由 Forge 拼 `modid`，故五线统一取 path；26.1.2 的按键分类用 `KeyBindingSetup.CATEGORY`，其余三线用字符串 `key.categories.astral_dice`。
- 诚实边界：`render` 的 graphics 参数是 `Object`，附属必须按本线强转；HUD 只有准星一个锚点，不能选择插在其它原版层之间；注册必须早于客户端 setup；晚注册只记录不显示；附属按键翻译键为 `key.astral_dice.<path>`，lang 需自带；卡牌商店按 P1 裁决不出面。
- 验收状态：五线 `assemble --offline` 通过，产物含三个 `api/client` 类与 `openCardInventory`；客户端启动 + 一次界面打开见 §6.9。

### 6.9 实机验收记录（B5，26.4-SNAPSHOT.90，2026-11-18）

- 环境：neoforge-1.21.1 dev 客户端、存档 probe、玩家 Dev、AutoJS 两端 available（server/client 均 scripts loaded）。
- 界面打开：已佩戴 `astral_dice:nether_star_dice` 时 `openCardInventory(player)` 返回 `true`，`player.containerMenu` 为 `com.merlinkitsune.astral_dice.screen.CardInventoryMenu`；把骰子槽清空后同一调用返回 `false`（走「未佩戴骰子」提示分支）；恢复骰子后槽位内容读回仍为 `astral_dice:nether_star_dice`。
- 注册表：`AstralHudLayers.registered()` 起始 0，连续两次 `register("b5_test", ...)` 后仍为 1（重复被拒）；空白 id 抛 `IllegalArgumentException: id must not be blank`；`pathOf("other_mod:foo")` 返回 `foo`。`AstralKeyBindings` 注册 `b5_key` 两次后 size 为 1，`translationKey()` 为 `key.astral_dice.b5_key`。
- 侧别守卫（client 侧 eval）：`walletDeposit(player, 1)` 抛 `IllegalStateException: AstralDiceApi.walletDeposit is server-side only`；`giveItemTo(player, apple)` 抛同形式 `... giveItemTo is server-side only`。
- 未覆盖：客户端 HUD / 按键注册循环这次以**空注册表**启动（环境里没有在 mod 构造期注册的测试 addon），所以适配器 lambda 的真实显示与按键出现在控制菜单里没有截图取证；专用服务器未启动，`api/client` 类不引用任何客户端类型（只依赖 `Object`）作为静态保证。
- 取证坑：`AstralHudLayers.register` 对空白 id 是**抛异常**而不是返回 false，探针必须 try/catch；Rhino 的 `JSON.stringify` 会丢掉返回 `undefined` 的字段，注册方法若无返回值就不会出现在结果里。

### 6.10 B6 落地记录（26.4-SNAPSHOT.91，2026-11-18）

- **S2 收口**：新增 `api/data/DiceTierView`（record：id / cardSlots / chipSlots 为 `IntUnaryOperator`，方法 `chipSlotsAt(int)`；构造期拒绝空白 id、负格数与空函数）；门面新增 `registerDiceTierView(String, Supplier<Item>, int, IntUnaryOperator)`、`diceTierView(String)`、`diceTierViews()`。旧 5 参 / 4 参 `registerDiceTier`、`diceTier`、`diceTiers` 全部保留并标 `@Deprecated` —— Java 不能仅靠返回类型重载，直接改返回类型会打断所有现存 addon。
- **S3 收口（ChipEquip）**：补 fabric-1.20.1 / fabric-1.21.1 / neoforge-26.1.2 三份 `item/chip/ChipEquip`，判据统一为 Curios `chip` 槽 + `BaseChipItem`；fabric 两线走兼容层静态 `CuriosApi.getStacksHandler(entity, "chip")`，26.1.2 走官方 `CuriosApi.getCuriosInventory(...).get().getStacksHandler("chip")`。
- **S3 收口（声明不补线）**：`SignHooks` / `SignHookDispatcher` 只存在于三条有立牌子系统的线（neo-1.21.1 / forge-1.20.1 / neo-26.1.2），**正式声明仅这三线可用**；fabric 两线不提供立牌子系统，单独补 SignHooks 等于引入半个立牌实现。立牌行为扩展继续建议用既有 `registerDamageHook` / `registerCombatStatHook` 组合。`CardUseService` 已在 B1 补满五线；`OpenCardInventoryPayload` 已被 B5 的平台 seam 取代，不再是门面可达面。
- **兼容性**：所有旧签名保留（`javap` 五份产物均可查到 5 参 `registerDiceTier`、`diceTier`、`diceTiers`），deprecation 只产生编译警告；无注册表或数据格式变更。
- **验证**：五线 `assemble --offline` BUILD SUCCESSFUL（`work/assemble-b6a.log`，28 tasks）；产物同时含 `DiceTierView` 与三新方法；`ChipEquip` 五线均有同名类。未跑实机（本批无运行期行为变化，纯类型/补线整理）。

### 6.11 LT-4 实机验收与门面缺陷修复（26.4-SNAPSHOT.92，2026-11-19）

- 目的：B2 的 `SpellDamageMatcher` / `SpellDamageModifier` 当时只有注册锚点、没有行为断言（欠账表 LT-4）。本批用「注册自定义 matcher/modifier → 同一次伤害前后对拍」做断言，并补一发真实第三方法术做外部效度。
- **查出缺陷**：门面 `AstralDiceApi.registerSpellDamageModifier`（common/.../api/AstralDiceApi.java:871）转发到 `AstralDicePlatform.registerSpellDamageModifier`（common/.../platform/AstralDicePlatform.java:102），而该 default 方法体为空；五线平台实现（`neoforge-1.21.1/.../platform/NeoForgeAstralDicePlatform.java`、`forge-1.20.1/.../ForgeAstralDicePlatform.java`、`fabric-1.20.1|fabric-1.21.1/.../FabricAstralDicePlatform.java`、`neoforge-26.1.2/.../NeoForgeAstralDicePlatform.java`）都只覆写到 `registerSpellDamageMatcher` 为止，**漏了 modifier 那一支**。调用照样打 `AP_COMBAT_API: registered=spell_damage_modifier`，但 `SpellDamageRegistry.registerModifier` 从未被调 ⇒ 附属注册被静默丢弃，且没有任何告警。`registerSpellDamageMatcher` 是通的，这也是 B2 从源码面看不出来的原因。
- 修复：五线各补一段 `@Override public void registerSpellDamageModifier(...) { CombatApiAdapters.registerSpellDamageModifier(modifier); }`，与既有 matcher 覆写同形。五线 `assemble --offline` BUILD SUCCESSFUL（`work/assemble-92.log`，28 tasks）。
- 实机读数一（人工伤害曲线，neoforge-1.21.1 dev 客户端，超平坦存档 `testworld`，尸壳 armor 0 / MAX_HEALTH 1000，伤害经 `damage <sel> 10 <type> by @s` 注入）：基线 `minecraft:player_attack` 1000 → 990；经门面注册 +40 修饰器但无 matcher → 仍 990（matcher 门控生效）；注册 `src.type().msgId() == "player"` 的 matcher 后 → 950（10 + 40）；换成不匹配的 `minecraft:mob_attack` → 990。`SpellDamageRegistry.modifiers()` 10 → 11；**修复前同一段脚本读到的始终是 10**。
- 实机读数二（真实法术，同一只尸壳、同一发法术）：`irons_spellbooks:firebolt` 等级 1，无修饰器 1000 → 992（8 点）；经门面注册 +40 后重新施放 1000 → 952（48 = 8 + 40）。该伤害已被内置 `fire_magic` matcher 命中，无需自定义 matcher —— 这同时证明「第三方法术源 → 内置白名单 → 修饰器加成 → 折叠进同一次伤害」整条链成立。
- 判据说明：`isSpellDamage`（neoforge-1.21.1/.../combat/SpellDamageRegistry.java:131-137）首个命中即短路，Firebolt 的 `fire_magic` 已在内置白名单（同文件 :52-96，13 个 irons_spellbooks 伤害类型）⇒ 后追加的观察型 matcher 永远不会被调用。探针要断言 matcher，必须用**能改变结果**的 matcher，不能靠观察型日志。
- 诚实边界：`SpellDamageModifier.onHit` 未取证 —— Rhino 的 `JavaAdapter` 不覆写接口 default 方法（直接调 `m.onHit(view, 1)` 返回 `undefined` 并走空实现）；`isActive` / `apply` 是抽象方法，覆写正常且本次已断言。`onHit` 需要 Java 侧实现者才能验证。
- 探针坑（同批沉淀）：① `DamageSource` 在 Rhino 里没有 `getMsgId`，用 `src.type().msgId()`；② `LivingEntity.hurt` / `actuallyHurt` 在该映射下不存在，叫 `damage`，而 `h.damage(src, n)` 会被 KubeJS 的 `DamageSourceWrapper` 抢参数（`Missing key in ... minecraft:10.0`）⇒ 用 `/damage` 命令注入；③ 锚点类是 `net.minecraft.commands.arguments.EntityAnchorArgument.Anchor`，不是 `EntityAnchor`；④ 只有走完整伤害管线的 `/damage` 才会派发 `LivingIncomingDamageEvent`，手工调 `damage` 也会，但参数包装不可控。
**批次顺序理由**：B1 直接解 TLMDice「牌术士自动装牌 / 自动用效果牌」两条 seam；B2 让第三方能改骰战数值（当前只能靠改本仓源码）；B3/B4 是常见扩展需求；B5/B6 是收口与兼容。

## 7. 需要裁决的决策点

1. **先做哪批**：建议 B1（TLMDice 正卡在这里），其次 B2。
2. **Signs / Chips 表要不要接到消费面**（§2 S1）：现在注册描述符不生效。接到消费面是**改行为**不只是补 API，需要单独设计（冷却来源、价格来源、权重来源三处都要定）。
3. **第三方内容物扩展点**：立牌/筹码行为只能靠子类化内部 BaseSignItem / BaseChipItem。要不要在 api/ 出正式基类或行为接口（SignBehavior / ChipBehavior）？这决定「注册面有、消费面不认」是补消费面还是补行为面。
4. **类型整理是否接受破坏性变更**：已裁决为**非破坏**——新增 `DiceTierView` 与 view 方法，旧方法保留并 `@Deprecated`（§6.10，26.4-SNAPSHOT.91）。
5. **B2 门面漏覆写算不算行为变更**：按缺陷修（`.92`，§6.11）。修前经门面的 `registerSpellDamageModifier` 是静默丢弃，修后同一调用会真的改变伤害数值；不存在依赖「注册无效」的正当用法，故不宣布破坏性变更。

## 8. 抽查复核记录（本文件 §2/§3 的证据都重新验证过）

- Signs. / Chips. 全仓 17 处命中，全部落在 common/.../api/AstralDiceApi.java；neoforge-1.21.1 线自身 0 处读取。
- 五线存在性（glob **/<类名>.java）：SpellDamageRegistry / DiceCombatModifiers / TargetSelectionManager / AstralEventRegistry / CardInventoryMenu / DiceCurioItem / BaseSignItem / SignEntitySkills / BaseChipItem / CardChoiceManager = **5/5**；CardUseService = 2（neo-1.21.1、forge-1.20.1）；ChipEquip = 2；SignHooks = 3（+neoforge-26.1.2）；OpenCardInventoryPayload = 2（neo-1.21.1、neoforge-26.1.2）。
- screen/CardInventoryMenu.java：findEquippedDice:256、loadFromDice:263、saveToDice:302、removed:447 → :449 saveToDice() 均为 private/内部。
- target/PlayerLikeTargets.java:167：if (receiver instanceof Player) return false;（giveItem 对真玩家恒 false）。
- common 全包 isClientSide|Dist.CLIENT|assertServer|LogicalSide = **0 命中**。
- event/ModTooltipHandler.java:348-353：13 个 ModItems.*_DICE 硬编码。
- economy/StarCoinCurrency.java：STAR_COIN_VALUE:38、STAR_COIN_BAG_VALUE:40、valueOf:48、isCurrency:64、isWalletEnabled:71、isAutoDepositEnabled:80、countCarriedValue:92、depositAllFromInventory:115、withdraw:148。
- AstralDiceApi.java:85-86 返回类型全限定写死 com.merlinkitsune.astral_dice.item.dice.DiceTier；:104 diceTier(String) 返回 StarEngine DiceTier。

## 9. 包收口记录（26.4-SNAPSHOT.93，2026-11-19）

- 目的：对外类型一律收进 `api.*` 命名空间，消除「门面在 api、类型在内部包」的半开裂；同时让库侧（StarEngine）与本体都只引用 dev 线产物，不做跨分支引用。
- **库侧 StarEngine（26.4-SNAPSHOT.14）**：`target/TargetType` → `api/target/TargetType`、`combat/HostileTargets` → `api/combat/HostileTargets`（git mv，包声明同步）；库内 8 个引用文件补 import：TargetSelectionAction、SelfTargetable（javadoc）、SelectorTargets、TargetCategory、PlayerHostilityTracker、PartyRelations、EliteTargets（javadoc）、CreatureTargets。五子项目 `lib_version` 升 `26.4-SNAPSHOT.14`，`publishToMavenLocal --offline` 成功。
- **本体搬包（五线）**：`event/api/*`（AstralEvent / AstralEventEffect / AstralEventPolarity / AstralEventTier / AstralEventTrigger）→ `api/event/`；`choice/CardChoiceType` → `api/choice/`（五线各一份）。`AstralEvent` 依赖同线 `event/AstralEventSystem`，故只做同包改名、不进 common。
- **本体搬包（common）**：`platform/AstralDicePlatform` → `api/platform/`；`AstralDicePlatforms` 与五线实现类（NeoForge/Forge/Fabric `*AstralDicePlatform`）留在原 `platform` 包，只搬接口。
- 引用改写：全局 FQN 替换 60 个文件；同包引用处补显式 import —— 五线 `choice/CardChoiceManager.java` 加 `api.choice.CardChoiceType`，`platform` 包内 6 个文件加 `api.platform.AstralDicePlatform`。另把 231 个文件里的 `starengine.target.TargetType` / `starengine.combat.HostileTargets` 改成 `api` 包，残留 grep 清零。
- **依赖钉版**：五线 `starengine_version` = `26.4-SNAPSHOT.14`；`neoforge.mods.toml`（neo-1.21.1:149、neo-26.1.2:147）与 `mods.toml`（forge-1.20.1:159）区间由 `[${starengine_version},2.0.0)` 改为 `[${starengine_version},27.0.0)` —— 下界 26.4 已高于原上界 2.0.0，原区间是空集；fabric 两线走 `fabric.mod.json:45` 的 `lib_version_range`，未改。
- **顺带修复**：`AstralDiceApi.java:236` `diceTierIds()` 原读库侧 `DiceTiers`，与 `diceTierViews()`（读 `DiceTierRegistry`）不同源；改为同读 `DiceTierRegistry`。`AstralDiceApi` 类 javadoc 补「附属须同时依赖 astral_dice 与 starengine 两个 jar」。
- **验证**：五线 `assemble --offline` BUILD SUCCESSFUL（`work/assemble-93.log`）；`jar tf` 五份产物含 `astral_dice/api/{event,choice,platform}/`，无 `astral_dice/event/api/`、`choice/CardChoiceType.class`、`platform/AstralDicePlatform.class` 残留；打进去的 `neoforge.mods.toml` 读作 `versionRange="[26.4-SNAPSHOT.14,27.0.0)"`。产物 sha256：neo-1.21.1 `3c93306c…ec2939a`（类数 673）、forge-1.20.1 `11ba614c…68f314a8`（类数 698，SRG 305）。
- **破坏性声明**：包路径改名不看旧路径 —— 附属必须改 import（或用新 FQN）并同时把 `starengine` 钉到 `26.4-SNAPSHOT.14`；只保留旧签名、不保留旧包路径别名。
- 消费方级联：TLMDice 两线 pin 换 `.93` + StarEngine `26.4-SNAPSHOT.14`，`mod_version` 升 `26.4-SNAPSHOT.88`；只用到 `AstralDiceApi` 与 `api.playerlike.*`，代码零改动，两线 `assemble` 通过。

## 10. API 归属翻转（26.4-SNAPSHOT.94，2026-11-19）

- 用户约定（本批新增，2026-11-19）：**main 线跟随 main 库、dev-next 线跟随 next 库，禁止跨区调用**；调用 API 一律走 StarEngine，**禁止再把 api 放在主模组**；**主模组必须是库 API 的实现方**。
- 目标形态与映射表：`docs/design/07-api-ownership.md`；执行计划：`docs/superpowers/plans/2026-11-19-api-ownership-migration.md`。
- **库侧（StarEngine 26.4-SNAPSHOT.15）**：新增 `com.merlinkitsune.starengine.api.astral.*` —— 36 个接口/枚举（client 3、combat 20、data 3、choice 1、event 5、platform 1、playerlike 4）+ `AstralDiceApi` 门面（776 行，保留 100 个静态方法）+ `AstralDiceService` SPI（179 行，上述方法的实例版）。门面 `install(service)` / `service()`：未安装时任何调用抛 `IllegalStateException`。
- **摘掉的两个方法**：`platform()`（全仓零调用，且把模组内部类型写进对外签名）与已弃用的 `registerDiceTier(String,Supplier,int,IntUnaryOperator)`（返回模组内部 `item.dice.DiceTier`，签名无法进库）。同名的库侧 `registerDiceTier(String,int,IntUnaryOperator)` 保留。
- **主模组（26.4-SNAPSHOT.94）**：`common/.../api/**`（31 文件 + 门面）与五线 `*/api/{event,choice}/**` 整批删除；新增 `apiimpl.AstralDiceServiceImpl`（约 1170 行，实现 100 个方法 + `matchesCardChoice`，私有 `platform()` 转接 `AstralDicePlatforms.get()`），`PlayerLikeTargetsAdapter` 移入 `apiimpl`。146 个源码文件 382 处 `astral_dice.api.` FQN 改为 `starengine.api.astral.`。
- **两处耦合拆除**：`CardChoiceType.matches(ItemStack)` 不再直接读 `item.ModItems`（fabric 两条线甚至没有 isCombatCard/isEffectCard），改为委托门面新方法 `AstralDiceApi.matchesCardChoice(type, stack)`；`AstralEvent` javadoc 不再 `{@link}` 模组类 `AstralEventSystem`，改纯文本说明。
- **`AstralEvent` 双份**：四条线用 `ResourceLocation`、26.1.2 用 `Identifier`。库侧把 ResourceLocation 版放 `common/src/main/java-rl`（四条线的 build.gradle 各自加该 srcDir），26.1.2 线单放一份仅这两个字段类型不同的副本。Gradle 把 `fileTree`+exclude 摊进 srcDir 会报 `Source directory ... is not a directory`，故用独立目录而不是过滤。
- **安装点**：五线 `AstralDiceMod` 构造器在 `AstralDicePlatforms.install(new XxxAstralDicePlatform())` 之后调 `AstralDiceApi.install(new AstralDiceServiceImpl())`（neoforge-1.21.1:51、forge-1.20.1:54、neoforge-26.1.2:53、fabric-1.21.1:55、fabric-1.20.1:55 附近）。
- **依赖钉版**：五线 `starengine_version` = `26.4-SNAPSHOT.15`；NeoForge/Forge 区间 `[${starengine_version},27.0.0)`；Fabric 两线 `starengine_version_range` = `>=26.4-SNAPSHOT.15 <27.0.0`（`fabric-1.20.1/gradle.properties:42`、`fabric-1.21.1/gradle.properties:32`）。
- **验证**：库五线 `compileJava --offline` 与 `publishToMavenLocal --offline` 均 BUILD SUCCESSFUL（`work/publish-15.log`）；主模组五线 `assemble --offline` BUILD SUCCESSFUL；`unzip -l` 五份产物 `com/merlinkitsune/astral_dice/api/` 类数为 0（只剩 `apiimpl/` 5 类），无 `AstralDiceApi.class`，包内区间为 `[26.4-SNAPSHOT.15,27.0.0)` / `>=26.4-SNAPSHOT.15 <27.0.0`。
- **顺带纠正**：`.93` 的 fabric 两线仍残留 `>=1.2.0-pre.1 <2.0.0` 区间（与实际定的 .14 依赖矛盾），本批一并改为 `>=26.4-SNAPSHOT.15 <27.0.0`。
- **破坏性声明**：附属 import 从 `astral_dice.api.*` 改为 `starengine.api.astral.*`，且必须同时依赖 StarEngine `>=26.4-SNAPSHOT.15`；`AstralDiceApi.platform()` 消失；门面在 mod 构造期安装，附属不得更早调用。主线（multi-main + StarEngine 1.2.0-pre.1）本批不动，dev-next 实机验证后再以 1.2.1 搬。
- 消费方级联：TLMDice 两线 14 处 import 改 `starengine.api.astral.*`（6 文件），pin 换 `.94` + StarEngine `.15`，`mod_version` 升 `26.4-SNAPSHOT.89`。

## 11. StarEngine 下沉审计（2026-11-19）

- 触发：用户约定（2026-11-19）——main 线跟随 main 库、dev-next 线跟随 next 库，禁止跨区；调用 API 一律走 StarEngine，禁止再把 api 放在主模组；主模组必须是库 API 的实现方。随后要求（同日）：检查 StarEngine 库，看是否有本应在主模组实现的功能被下沉到库中，导致调用混乱。
- 范围：`starengine_next`（dev 线，`26.4-SNAPSHOT.15`）与 `astral_dice_multiloader-next`（`.94`）两仓源码；**只读审计，未改任何代码**。
- 方法：逐包清点（common 共享源 + 五条平台线 + vendored 排除）、主模组五线 `starengine.*` import 统计、门面/SPI 与实现的逐方法对照、库 README/AGENTS 自述与代码现状对照。

### 11.1 结论摘要

1. **契约层（`api.astral`）是干净的**：37 个类 2289 行，`AstralDiceApi` 门面 100 个静态方法全部委托 `AstralDiceService`；service 实现只在主模组 `apiimpl.AstralDiceServiceImpl`（101 个 public 方法 = 100 SPI + `matchesCardChoice`），库内无实现。
2. **平台接口错位**：`api/astral/platform/AstralDicePlatform` 放在库契约包，但库从不使用它；它是主模组内部架构（loader 实现 + 服务定位），应搬回主模组。
3. **同名类双实现/分线分叉**：`PartyRelations`、`EffectTimerGuard`、`EliteTargets`、`HoldToSelect`、`SelfTargetable` 在部分线保留主模组副本；其中 `fabric-1.20.1` 同一条线同时有 31 个文件用本地 `PartyRelations`、5 个文件用库版，属于真实的调用混乱。
4. **星骰内容实现留在库**：18 个 `MobEffect` 子类、`Rarity`/`AstralRarities`、`GameplayConstants`、`StarCoinCommand`/`StarCoinWalletState` 都是星骰专属内容，但类体、常量、命令与 UI 缓存都在库里，主模组只负责注册/注入。
5. **库的自述与实现不一致**：`README_ZH.md` §1.1 写「库不新增玩法注册内容」，但 §3 的下沉记录引用的 `temp/sink-manifest-20260917.md` 在仓内不存在；库实际定位是「共享实现库」，不是纯契约库。

### 11.2 发现与证据

**F1 平台接口混入契约包（明确应整改）**

- 文件：`common/src/main/java/com/merlinkitsune/starengine/api/astral/platform/AstralDicePlatform.java`，207 行、29 个 default 空实现（`install` / `registerPlayerLike` / `activateSign` / `hasDiceBlessing` / 7 个战斗注册口 / 效果与星界事件 / 星币与配置读 / `openCardInventory`）。
- 类 javadoc（:22、:24）自称「主项目 common 源码根里唯一允许出现的平台身份概念」，并说明各子项目在 `AstralDiceMod` 注册实现——这是主模组架构描述，不是对外契约。
- 库内引用只有两处**未使用的 import**：`api/astral/AstralDiceApi.java:25`、`api/astral/AstralDiceService.java:25`；库内没有任何方法以该类型为参数或返回值。
- 实际消费者全在主模组：`common/.../apiimpl/AstralDiceServiceImpl.java:621/:633/:809/:1084/:1181`、`common/.../platform/AstralDicePlatforms.java`、五线 `platform/*AstralDicePlatform.java` 与 `AstralDiceMod` 安装点。

**F2 同名类多实现（调用混乱的直接来源）**

| 类 | 库版 | 主模组本地副本 | 使用分布 |
|---|---|---|---|
| `PartyRelations` | `common/combat/PartyRelations.java` 221 行，同队判定转发 `EventTargetCollector.isSameTeamAmongEnabledSystems`（:182-184） | `fabric-1.20.1/.../combat/PartyRelations.java` 572 行，自带 Ftb/Opac 反射 | fabric-1.20.1：31 文件 import 本地版 + 5 文件 import 库版；fabric-1.21.1：39 文件库版；neo/forge：各 39 文件库版；neoforge-26.1.2：另有一份 582 行本地副本但 0 引用（死文件） |
| `EffectTimerGuard` | `common/event/EffectTimerGuard.java` 212 行，经 `TimerSource` 读写计时表，平台差异由 `EffectTimerAccess` 吸收 | `fabric-1.20.1` 与 `fabric-1.21.1` 各 175 行，直接 import `component.ModAttachments` | 两条 fabric 线各 19 文件用本地版、0 用库版；neo 12、forge 12、neo26 16 用库版 |
| `EliteTargets` | `common/combat/EliteTargets.java` 57 行，委托 `StarengineThirdParty.isEliteOrBoss`（反射 Apotheosis，fabric 无 `getPersistentData` 时降级 false） | `fabric-1.20.1` / `fabric-1.21.1` 各 70 行，直接返回 false | fabric 两线各 2 文件用本地版；neo26 本地副本 0 引用（死文件） |
| `HoldToSelect` | 28 行 | 34 行 | neo26 1、fabric-1.20.1 1、fabric-1.21.1 1 文件用本地版 |
| `SelfTargetable` | 26 行 | 36 行 | neo26 5、fabric-1.20.1 4、fabric-1.21.1 5 文件用本地版 |

- 反例（属预期形态）：五线各一份 19–20 行的 `AttackPowerModifier` / `DefensePowerModifier` / `SpellDamageModifier`，是主模组对库侧 `api.astral.combat` 接口的适配器，不算分叉。
- 风险：同一个逻辑在不同线的行为可能不同。例如 `EffectTimerGuard` 的库版要求消费方在初始化时 `setTimerSource` 注入计时表，本地版直接读写 `ModAttachments`；若把 fabric 切到库版而不补注入，守卫会静默失效（库版未注入时全部安全返回、不报错）。

**F3 星骰内容/玩法实现下沉到库**

- 效果：库 `common/effect/` 有 18 个 `MobEffect` 子类（`BerserkEffect`、`PandamanTauntEffect`、`MosesBrokenEffect` 等，9–18 行，含角色名与时长/颜色注释），全部由主模组 `effect/ModEffects` 注册（`neoforge-1.21.1/.../effect/ModEffects.java:35` king_power、`:38` berserk、`:44` marked、`:64` dice_blessing、`:73` misaki_burst、`:76` living_page、`:84` weak_mark、`:88` fate_guidance、`:92` papara_bite、`:96` undercover_investigation、`:100` investigation_bonus、`:110` healing、`:114` cutter_ready、`:194` nancy_lu_hack、`:198` revenge_halberd、`:217` moses_broken、`:221` pandaman_taunt；其余线同构）。
- 稀有度：库 `item/Rarity.java` 223 行，`RARE("ASTRAL_DICE_RARE", "astral_dice:rare", 0x55FFFF)` 与 EPIC/LEGENDARY/PINNACLE/BIZARRE 共 5 档（:43-57），含 `RAINBOW_CYCLE_MILLIS = 3000L`、`hsvToRgb`、彩虹描边起止；javadoc 自称「本模组（含消费方 mod）的稀有度等级定义」，是内容命名与配色的权威。per-loader `item/AstralRarities.java` 持有 `EnumProxy`，主模组 import 26 次。
- 数值：库 `component/GameplayConstants.java` 130 行（星光点上限、标记层数、效果牌冷却、充能上限等星骰玩法数值）+ `GameplayConfigValues` 26 行；配置 schema 在主模组，库只提供 seam。
- 经济：库 `economy/StarCoinCommand.java` 189 行，完整 `/starcoin` 命令（add/set/remove 权限 2、get、rank 含离线玩家），由库的平台存储自行注册（`FabricEconomyStorage.java:174/:183`、`ForgeEconomyStorage.java:121`、`NeoForgeEconomyStorage.java:126/:147` 调 `StarCoinCommand.register`）；`StarCoinWalletState.java` 35 行客户端余额缓存，javadoc:5 写明「2026-09-22 自主模组 economy.StarCoinWalletState 下沉;口径逐字未变」，主模组 12 处 import。`StarEngineEconomy`（101 行）是真共享（TLMDice 也在用）。

**F4 边界执行情况（consumers）**

- 主模组大量直连库的 legacy 包：`GameplayConstants` 281 处、`ModEffectRemoval` 164、`PartyRelations` 158、`TargetType` 142、`CuriosCompat` 79、`HostileTargets` 102、`StarEngineEconomy` 40、`TargetSelectionRegistry` 70、`AstralRarities` 26、`EffectTimerGuard` 40、`Rarity` 21、`StarCoinWalletState` 12。契约层 `api.astral` 只覆盖了后加的 36 个类型，legacy 包没有对应的 `api.*` 门面。
- TLMDice 只用到：`api.astral.playerlike.*`（14 处）、`event.EventTargetCollector`（2）、`economy.StarEngineEconomy`（2）、`combat.TargetCategory`（2）。⇒ legacy 包中真正被第二个 mod 消费的只有三个类，其余是主模组自用。

**F5 库内无消费方引用的类（不等于死代码）**

- 全仓（两消费方）显式引用为 0：`ApotheosisMarkers`、`AstralHudLayer`、`CommandPermissionGate`、`ContentDescriptor`、`ContentRegistry`、`DefaultedFloat`、`EconomyStorage`、`EventInvoker`、`PlayerWallet`、`StarCoinCommand`、`StarEnginePlatform`、`StarEnginePlatforms`、`StarengineThirdParty`。
- 其中多数是库内自用：`ApotheosisMarkers` ← `StarengineThirdParty` 静态块；`CommandPermissionGate` / `StarCoinCommand` ← 平台经济存储；`EconomyStorage` ← 各平台存储；`StarEnginePlatform` ← `StarEnginePlatforms`。真正待复核的是 `AstralHudLayers` / `AstralKeyBindings` 在主模组五线 client 的消费（上批 grep 输出被截断，未定论）。

**F6 文档/清单缺口**

- `README_ZH.md:310` 引用 `temp/sink-manifest-20260917.md` 作为「其余 52 个同源候选的排除理由」，该文件在 `starengine_next` 内不存在（`temp/` 只有 `msg_forgepin.txt`、`msg_gecko.txt`）。
- `README_ZH.md` §1.1「库不新增玩法注册内容」与 §2 要求平台线提供 `AstralRarities`、`ModEffectRemoval`、`ActionBarManager` 等星骰专属 shim 自相矛盾；平台清单仍写「四平台」，实际仓内五条线（多 `fabric-1.21.1`）。

### 11.3 归属判定

| 类别 | 现状 | 判定 |
|---|---|---|
| `api.astral` 36 契约 + 门面 + SPI | 库 | **保留**：符合「API 走库、主模组实现」 |
| `AstralDicePlatform` | 库契约包 | **搬回主模组**：库无消费者，且 javadoc 描述的是主模组内部架构 |
| 五类同名本地副本（`PartyRelations` / `EffectTimerGuard` / `EliteTargets` / `HoldToSelect` / `SelfTargetable`） | 部分线本地、部分线库版 | **统一到库版并删除本地副本**；切换前逐类核对行为差异（尤其 `EffectTimerGuard` 的 `setTimerSource` 注入） |
| 18 个 `MobEffect` 子类 | 库 | **建议搬回主模组**（内容专属）；若保留，须在 README 明确「库可承载消费方内容类」并给出规则 |
| `Rarity` / `AstralRarities` | 库 | **建议搬回主模组**：`astral_dice:rare` 等注册名与配色是内容权威，不是通用引擎 |
| `GameplayConstants` / `GameplayConfigValues` | 库 | 通用部分（配置 seam、跨线共享常量）可留；星骰专属数值应回主模组——**需裁决切分线** |
| `StarCoinCommand` / `StarCoinWalletState` | 库，且由库平台存储自动注册 | **搬回主模组**：星币是星骰功能，命令注册应由主模组做；`StarEngineEconomy` 保留（TLMDice 共用） |
| legacy `target` / `combat` / `event` 通用引擎 | 库 | **保留**，但补一条外部消费白名单：附属只允许 `api.astral.*` + `EventTargetCollector` / `TargetCategory` / `StarEngineEconomy` |

### 11.4 整改建议（分阶段，均需重新编译与版本 bump）

> 2026-11-19 更新：P0、P1 已执行，见 §12；以下为当时的建议原文。

- **P0 平台接口归位**（改动小、无行为变化）：把 `AstralDicePlatform.java` 搬回主模组（建议 `common/.../apiimpl/platform/` 或原 `platform` 包），删掉库内两个未使用 import；更新 `AstralDiceServiceImpl` 五处引用与五线 loader 实现。库升 `26.4-SNAPSHOT.16`、主模组升 `.95`；TLMDice 不引用该类型，只需跟随 pin（`.90`）。
- **P1 去重**：先在 fabric 两线补 `EffectTimerGuard.setTimerSource` 注入（若当前未注入），再逐类 diff 后删除五类本地副本、统一 import 库版；清掉 neoforge-26.1.2 的两个 0 引用死文件。必须五线 `assemble` + 行为核对（尤其队伍判定与计时守卫）。
- **P2 内容归属**（改动大、影响 TLMDice 与后续 Extra）：按 11.3 把效果/稀有度/星币命令与缓存搬回主模组，或反向裁决「库=共享实现库」并补文档与稳定 API 白名单。**此项需用户先定方向**。
- **P3 文档**：修 `README_ZH.md` §1.1/§2/§3/§4 的自述与平台清单、补回或删除 `sink-manifest` 引用、在库 `AGENTS.md` 写明「什么可以下沉、什么必须留在消费方」。

### 11.5 未决与待复核

- `AstralHudLayers` / `AstralKeyBindings` 在五线 client 的消费未复核（上批 grep 被文件数截断）。
- `PartyRelations` 库版与 fabric 本地版的行为等价性未逐行 diff；库版依赖 `EventTargetCollector` 的反射后端状态。
- fabric 两线当前是否已调用 `EffectTimerGuard.setTimerSource` 未核（决定 P1 是否必须先补注入）。
- 18 个效果类是否有第三方（Extra/TLMDice）直接 import 未核；TLMDice 的 import 画像显示只碰 `api.astral` + 三个 legacy 类，理论无影响。
- 本审计只读，未改代码、未 bump、未提交。

## 12. P0/P1 整改记录（26.4-SNAPSHOT.95 / 库 .16，2026-11-19）

> §11.4 的 P0、P1 已执行；P2（内容归属）与 P3（文档）未动。以下只记执行结果与验证读数。

### 12.1 P0 平台接口归位

- 库侧删除 `common/src/main/java/com/merlinkitsune/starengine/api/astral/platform/AstralDicePlatform.java`（207 行 / 29 个 default），同时删掉 `api/astral/AstralDiceApi.java:25`、`api/astral/AstralDiceService.java:25` 两条未使用 import。
- 主模组新增 `common/src/main/java/com/merlinkitsune/astral_dice/platform/AstralDicePlatform.java`，package 为 `com.merlinkitsune.astral_dice.platform`；`apiimpl/AstralDiceServiceImpl.java` 的 import 改指新包；`platform/AstralDicePlatforms` 与五线 `platform/*AstralDicePlatform` 同包，删除多余 import。
- 复核：全仓 `starengine.api.astral.platform.AstralDicePlatform` 残留 0；`AstralDiceApi` / `AstralDiceService` 不再引用平台类型。
- 版本：库五平台 .15 → .16（`publishToMavenLocal` 成功，`~/.m2` 五件 `26.4-SNAPSHOT.16`）；主模组 .94 → .95，`starengine_version` .15 → .16，Fabric 两线区间 `>=26.4-SNAPSHOT.16 <27.0.0`。

### 12.2 P1 五类去重

- 删除 13 个本地副本：`fabric-1.20.1` 的 `combat/PartyRelations`、`event/EffectTimerGuard`、`combat/EliteTargets`、`target/HoldToSelect`、`target/SelfTargetable`；`fabric-1.21.1` 后四者；`neoforge-26.1.2` 的 `combat/PartyRelations`、`combat/EliteTargets`、`target/HoldToSelect`、`target/SelfTargetable`。
- 134 个源码文件改写 import / FQN，或为原同包引用补 `com.merlinkitsune.starengine.{combat,event,target}.*` import；残留 FQN grep 为空。
- 计时 seam：两条 Fabric 原来**未**调用 `setTimerSource`；现于 `fabric-1.20.1/.../AstralDiceMod.java:61-63` 与 `fabric-1.21.1/.../AstralDiceMod.java:61-63` 的门面 install 之后注入 `new SignTimerSourceAdapter()`。适配器改为库版 `TimerEntry` 直接类型拷贝，`getTimers` 返回副本、`setTimers` 写副本、`now=level().getGameTime()`；此前的双 `TimerEntry` 转换删除。
- 队伍判定：`fabric-1.20.1` 改调库版 `PartyRelations`，诊断前缀 `AP_FAB_PARTY:` → `AP_PARTY:`（库 `combat/PartyRelations.java:206-207`）；`scripts/test/cases/PARTY-BACKENDS-fabric-1.20.1.json:20`、`scripts/test/fabric/cases/FAB-PARTY-BACKENDS.json:18`、`scripts/test/fabric/README.md`、`docs/agent/AGENTS-full.md:474`、`KNOWN-ISSUES.md:527` 已同步。
- 死文件清理：`neoforge-26.1.2` 的两份 0 引用副本随删除清单移除。

### 12.3 构建与产物验证

- 命令：`JAVA_HOME='C:/Program Files/Microsoft/jdk-21.0.13.102-hotspot' ./gradlew.bat assemble --offline --continue --no-daemon`（日志 `work/assemble-p1.log`），BUILD SUCCESSFUL in 37s，EXIT=0。
- 五份 `astral_dice-26.4-SNAPSHOT.95+<loader>.jar` 均在 `build/libs`；逐 jar 检查五类本地 class 均不存在（排除增量残留）。
- `neoforge-1.21.1` jar 636 个 `.class` 条目、`forge-1.20.1` 660；`META-INF/astral_dice/build-info.json` 在包内。
- `git diff --check` 无输出。

### 12.4 边界与未完成

- 本批未跑实机。`fabric-1.20.1` 的 FTB/OPAC 反射实现换成库版后，只有实机装对应后端才能确认 `AP_PARTY` 报 `on`；计时 seam 若某条线漏装，库版守卫会静默退回空实现。
- TLMDice 已跟随 pin 到 `.95` / StarEngine `.16` 并升 `.90`（两线 clean assemble 9 s，EXIT=0；不引用平台类型，无源码改动）；P2 内容归属分类已完成（`docs/design/08-p2-sink-classification.md`），A/B 已执行（主模组 `.96` / 库 `.17`），C（`GameplayConstants` 拆分）待下一批，P3 文档修正未开始。

## 13. P2 A/B 执行记录（26.4-SNAPSHOT.96 / 库 .17，2026-11-19）

- 用户裁决（m02749，原文）：「先做AB，另外裁决Wallet留库，constants拆分，下一批做」。
- A：库删 18 个 `MobEffect` 子类（`common/src/main/java/com/merlinkitsune/starengine/effect/`），主模组新增 `com.merlinkitsune.astral_dice.effect` 同 18 类；主模组 15 个使用文件改 import，库内旧引用 0。
- B：库删 `common/.../item/Rarity.java` 与五线 `item/AstralRarities.java`；主模组新增 `common/.../astral_dice/item/Rarity.java` 与五线同包 `AstralRarities.java`；主模组 45 个 Java 文件改写（含 A/B），清 95 条同包 import；两份 NeoForge `META-INF/enumextensions.json` 各 5 条改指 `com/merlinkitsune/astral_dice/item/AstralRarities`；六条源码根旧 FQN 残留 0。
- 版本与区间：库 `.17`；主模组 `.96`、`starengine_version=26.4-SNAPSHOT.17`、fabric `>=26.4-SNAPSHOT.17 <27.0.0`。
- 验证：库 clean publish 9 s SUCCESSFUL，五份 `.17` jar 无旧类；主模组五线 clean assemble 26 s SUCCESSFUL，五份 `.96` jar 经 `work/verify_ab.sh` 全部 `AB_VERIFY_OK`。
- 边界：未跑实机；NeoForge 枚举扩展仍需真实启动确认。`StarCoinWalletState` 按裁决留库；`GameplayConstants` C 批未做；TLMDice 不引用这些类，未改。
