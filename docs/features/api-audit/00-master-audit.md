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
| S2 | **门面泄漏内部类型**：5 参 registerDiceTier 返回 com.merlinkitsune.astral_dice.item.dice.DiceTier（非 api 包）；diceTier(id) 返回的却是 StarEngine 的同名异类 | AstralDiceApi.java:85-86、:104；common/.../item/dice/DiceTier.java:26 是 record | 两个 DiceTier 同名不同包，addon 极易混用；且 DiceTier 携带 Supplier<Item>，不适合作为稳定契约 |
| S3 | **五线可直连性不齐**：CardUseService（2/5，仅 neo-1.21.1 + forge-1.20.1）、ChipEquip（2/5）、SignHooks（3/5）、OpenCardInventoryPayload（2/5） | 五线 glob 实测 | 这四类**不能**写进 common/api 直连；卡牌使用/筹码装配/立牌回调必须走平台 default 或先在缺线补齐 |
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

**批次顺序理由**：B1 直接解 TLMDice「牌术士自动装牌 / 自动用效果牌」两条 seam；B2 让第三方能改骰战数值（当前只能靠改本仓源码）；B3/B4 是常见扩展需求；B5/B6 是收口与兼容。

## 7. 需要裁决的决策点

1. **先做哪批**：建议 B1（TLMDice 正卡在这里），其次 B2。
2. **Signs / Chips 表要不要接到消费面**（§2 S1）：现在注册描述符不生效。接到消费面是**改行为**不只是补 API，需要单独设计（冷却来源、价格来源、权重来源三处都要定）。
3. **第三方内容物扩展点**：立牌/筹码行为只能靠子类化内部 BaseSignItem / BaseChipItem。要不要在 api/ 出正式基类或行为接口（SignBehavior / ChipBehavior）？这决定「注册面有、消费面不认」是补消费面还是补行为面。
4. **类型整理是否接受破坏性变更**：registerDiceTier 返回 DiceTierView、两个同名 DiceTier 收敛（§2 S2）。

## 8. 抽查复核记录（本文件 §2/§3 的证据都重新验证过）

- Signs. / Chips. 全仓 17 处命中，全部落在 common/.../api/AstralDiceApi.java；neoforge-1.21.1 线自身 0 处读取。
- 五线存在性（glob **/<类名>.java）：SpellDamageRegistry / DiceCombatModifiers / TargetSelectionManager / AstralEventRegistry / CardInventoryMenu / DiceCurioItem / BaseSignItem / SignEntitySkills / BaseChipItem / CardChoiceManager = **5/5**；CardUseService = 2（neo-1.21.1、forge-1.20.1）；ChipEquip = 2；SignHooks = 3（+neoforge-26.1.2）；OpenCardInventoryPayload = 2（neo-1.21.1、neoforge-26.1.2）。
- screen/CardInventoryMenu.java：findEquippedDice:256、loadFromDice:263、saveToDice:302、removed:447 → :449 saveToDice() 均为 private/内部。
- target/PlayerLikeTargets.java:167：if (receiver instanceof Player) return false;（giveItem 对真玩家恒 false）。
- common 全包 isClientSide|Dist.CLIENT|assertServer|LogicalSide = **0 命中**。
- event/ModTooltipHandler.java:348-353：13 个 ModItems.*_DICE 硬编码。
- economy/StarCoinCurrency.java：STAR_COIN_VALUE:38、STAR_COIN_BAG_VALUE:40、valueOf:48、isCurrency:64、isWalletEnabled:71、isAutoDepositEnabled:80、countCarriedValue:92、depositAllFromInventory:115、withdraw:148。
- AstralDiceApi.java:85-86 返回类型全限定写死 com.merlinkitsune.astral_dice.item.dice.DiceTier；:104 diceTier(String) 返回 StarEngine DiceTier。
