# B12 · forge-1.20.1 ↔ fabric-1.20.1 同名类语义漂移审计

- 仓库：D:/MCProject/astral_dice_multiloader，分支 multi-main（工作区实读，含 fabric 线并行批次的未提交改动）
- 审计时间：2026-11-01；性质：只读（本报告是唯一写入的文件）
- 目标：找出 fabric 线的**功能性滞后**（forge 有、fabric 无，或两线行为不同）

## §1 审计口径与文件集

**文件集**：两侧 src/main/java 下**相对路径相同**的 .java 文件（comm -12）。

（注：任务书写的「各约 259 个」与实读不符，实读数字如下，以此为准。）

```
$ find forge-1.20.1/src/main/java -name '*.java' | wc -l      # 403
$ find fabric-1.20.1/src/main/java -name '*.java' | wc -l     # 519
$ comm -12 <forge相对路径排序> <fabric相对路径排序> | wc -l    # 393
$ # 剔除 5 个另行修复的文件后
                                              审计目标         # 388
```

- 仅 forge 有（10）：component/AstralData.java、component/ModCapabilities.java、datagen/DataGenerators.java、event/IronSpellbooksCompat.java、init/MixinRuntimeGate.java、item/sign/MosesEnigmaticLink.java、item/sign/SignActiveLockMachine.java、loot/AddTableLootModifier.java、loot/AstralLootModifiers.java、trade/MerchantOfferRawAccess.java
- 仅 fabric 有（126）：platform/** 全量 Forge API 垫片（event.LoaderBus/DeferredRegister/RegistryObject/fml.network）、compat/curios/* 垫片、mixin/bridge/*（21 个桥 mixin）、combat/EliteTargets.java、combat/PartyRelations.java、component/SignAttachments.java、crafting/*、event/EffectTimerGuard.java、loot/FabricLootInjector.java、target/HoldToSelect.java、trade/FixedItemListing.java 等 —— 均属平台实现，不参与本审计。
- 排除（另行修复，本报告不报）：event/WeirdDiceHandler.java、item/ChargeManager.java、item/card/FateGuidanceCardItem.java、item/sign/BaseSignItem.java、component/ModAttachments.java

**平台差异剔除口径**（判定前先归一化，这些不算漂移）：import/package 行；`level.isClientSide`(字段)↔`level.isClientSide()`；`ResourceLocation.fromNamespaceAndPath(a,b)`↔`new ResourceLocation(a,b)`；`DeferredRegister`/`RegistryObject` 垫片；`@EventBusSubscriber`↔`LoaderBus.register`；`SimpleChannel`↔FAPI networking；`AttachmentType`↔`AttachedDataKey`；`ForgeRegistries.X`↔`BuiltInRegistries.X`；`ModXxx.Y.get()`↔`ModXxx.Y`；注释/javadoc 中文措辞。

**归一化后实测**：
- 逐行完全一致：**179** 个文件
- 去注释后「方法名集合 + 字符串字面量集合」完全一致：**291** 个
- 有结构差异（进入人工逐句比对）：**102** 个

## §2 真漂移清单

每条格式：文件:forge 行号 → fabric 行号｜差异｜影响面｜证据。

### D-01 event/PlayerEffectProtection.java:117/130 → fabric 同名类同名方法｜装配链断裂（类在、无人调用）
- 两线该类**逐字相同**（forge 206 行 / fabric 205 行），唯一差异 `ForgeRegistries.MOB_EFFECTS` ↔ `BuiltInRegistries.MOB_EFFECT`（平台）与 forge 多一行 `import starengine.event.EffectTimerGuard`。
- 但 fabric 侧 `tick()`/`onExternalRemoval()`/`clear()` **零调用者**（forge 侧 `tick` 1 处、`onExternalRemoval` 1 处、`clear` 3 处）。
- 影响：`OBSERVED` 只写不读、`PENDING_RESTORE` 永空 ⇒ 2026-10-23「玩家侧效果抗外部清除、下一 tick 补回」裁决在 fabric 线**完全不生效**；`astral_dice:marked` 之外的 mod 效果被外部移除后不会补回。
- 证据：
```
$ grep -rn "PlayerEffectProtection.tick(" fabric-1.20.1/src/main/java   # 0 行
$ grep -rn "PlayerEffectProtection.tick(" forge-1.20.1/src/main/java    # event/PlayerTickEvents.java:38
$ grep -rn "onExternalRemoval(" fabric-1.20.1/src/main/java             # 仅 PlayerEffectProtection.java:117 定义
$ grep -rn "PlayerEffectProtection.clear(" fabric-1.20.1/src/main/java  # 0 行
```

### D-02 event/ModEffectEvents.java:88-99/105-116 → fabric:全文｜缺 3 个事件处理器
- forge 独有方法：`onPlayerLoggedOut(PlayerLoggedOutEvent)`（→`PlayerEffectProtection.clear`）、`onPlayerClone(Clone)`（→`clear`）、`onExternalRemovalRestore(MobEffectEvent.Remove, priority=LOWEST)`（→`onExternalRemoval`，含 `isCanceled()`/`isDeadOrDying()` 早退）。
- forge:25-30 死亡分支有 `if (event.getEntity() instanceof Player deadPlayer) PlayerEffectProtection.clear(deadPlayer);`；fabric:21 只有 `if (event.getEntity().isDeadOrDying()) return;`。
- forge:45-46 `String effectId = …MOB_EFFECTS.getKey(...).toString(); if (PlayerEffectProtection.isDeclaredClearable(effectId)) return;`；fabric:41 硬编码 `if (effect.getEffect() == ModEffects.MARKED.get()) return;`（当前白名单等价，结构不同，仅登记）。
- 影响：与 D-01 同一功能的另一侧断点；退出/重生时也不清理观测表。
- 证据：`grep -n "PlayerLoggedOutEvent\|PlayerEvent.Clone\|onExternalRemovalRestore" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/ModEffectEvents.java` → 0 行。

### D-03 event/PlayerTickEvents.java:38/119-124/108-109 → fabric:35/117/109-111｜三处调用缺失 + 一处口径相反
- (a) forge:38 `PlayerEffectProtection.tick(player);`；fabric:35 只有 `EffectTimerGuard.tick(player);`（缺一半）。
- (b) forge:119-121 每 10t `LinglingSignManager.reconcileInventory(player);`（2026-10-27「立牌依附对账」）；forge:122-124 每 10t `LinglingSignManager.tickPassive(player);`。fabric 两处全无（fabric:117 直接进入 `% 20` 早退）。
- 影响：怪力乱神立牌被动在 fabric 彻底断链 —— `tickPassive` 在 fabric `item/sign/LinglingSignManager.java:218` 有定义，但全树 0 调用者；`reconcileInventory` 在 fabric 只剩拾取路径（`event/LinglingSignEvents.java:50`）而缺周期对账。
- (c) 充能图标口径相反：forge:107-109 注释「2026-10-21 用户裁决:删除「未装备充能类筹码时隐藏图标」的闸门 —— 有充能就显示」+ `setIconVisibility(player, true)`；fabric:106-111 仍是 `setIconVisibility(player, ChargeManager.isChargeChipEquipped(player))`（2026-10-03 旧裁决）。
- 证据：
```
$ grep -rn "tickPassive" fabric-1.20.1/src/main/java   # 仅 item/sign/LinglingSignManager.java:218 定义
$ grep -rn "tickPassive" forge-1.20.1/src/main/java    # PlayerTickEvents.java:123 + 定义
$ grep -n "reconcileInventory" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/*.java
                                                       # 仅 LinglingSignEvents.java:50
```

### D-04 event/PlayerTickEvents.java:185-187 → fabric:188-190｜反向滞后：fabric 仍保留 forge 已删的「电磁炮就位」指示器
- forge javadoc:185-187「2026-10-22 用户裁决：**电磁炮的「就位」指示器已删除**（充能 ≥ 6 时不再挂图标），故本方法由 6 枚降为 5 枚」；fabric:171-173 javadoc 仍写「6 枚…电磁炮 = 充能 ≥ 6（+5 生效，"就位"）」，且 fabric:188-190 实际调用 `refreshIndicatorInfinite(player, ModEffects.RAILGUN_READY.get(), RailgunChipItem.isEquipped(player) && …)`。
- 影响：fabric 上充能满 6 时仍显示「电磁炮就位」图标，与 forge 侧用户已裁决的界面不一致（指示器枚数 6 vs 5）。

### D-05 item/chip/PerpetualMotionChipItem.java:35/41/55-73 → fabric:15｜常量数值 + 攻速修饰器整段缺失
- forge:35 `CHARGE_TARGET = 8`（2026-10-15 裁决 6→8）↔ fabric:15 `CHARGE_TARGET = 6`。
- forge:41 `ATTACK_SPEED_MULTIPLIER = 1.0D`、forge:55-57 `attackSpeedModifierId()`、forge:65-73 覆写 `getAttributeModifiers` 挂 `Attributes.ATTACK_SPEED` ×`MULTIPLY_TOTAL`（= 近战冷却 -50%）；fabric 三样全无（该类只剩 CHARGE_TARGET/构造器/isEquipped/onBlessingStart）。
- 配套 lang：forge `tooltip.astral_dice.chip.perpetual_motion` 含「装备期间近战冷却 §e-50%%§7」、`guide.entry.perpetual_motion_chip.1` 含「近战冷却减半」、`guide.entry.battle_system_changes.3`（攻击速度不影响骰战）；fabric 同键无该行、第三键 0 命中。
- 影响：fabric 玩家少拿 2 点充能、且永动机筹码的近战冷却减半整条不存在（提示文案也没说，属「静默少功能」）。
- 证据：`grep -rn "ATTACK_SPEED\|attack_speed" fabric-1.20.1/src/main/java` → 0 命中；forge 命中 PerpetualMotionChipItem.java:56/:70。

### D-06 combat/DiceCombatModifiers.java:197-229 → fabric:205-213/920｜防御力↔护甲折算数值不同（1:1 vs ×2.0）
- forge:197-200 `PLAYER_ARMOR_PER_DEFENSE = 1.0 / CombatFormula.PLAYER_ARMOR_COEF`（0.30 ⇒ 1 防御力 ≈ 3.333 护甲）、`MOB_ARMOR_PER_DEFENSE = 1.0 / MOB_ARMOR_COEF`（0.40 ⇒ 2.5）；forge:206/211/218/229 提供 `armorValueForDefensePoints`（2 参 + LivingEntity 版）、反函数 `defensePointsForArmor`、`effectiveArmorPoints`（取 ARMOR 属性 **double** 终值）。
- fabric:211 `double armor = defensePoints * 2.0;`、fabric:920 `Math.max(0, player.getArmorValue() + modifierDefense * 2.0)`（`getArmorValue()` 为 int 截断）；fabric 自己的注释 :201-202 承认「2 点护甲只兑现 0.6 点防御力 ⇒ 传入的 defensePoints 实际只落地 60%」。
- 影响：fabric 侧所有「防御力 +X」（效果牌/立牌/筹码/事件）实际只兑现 0.6X；显示与结算仍按 ×2.0 与 int 护甲读。
- 证据：`grep -rn "armorValueForDefensePoints" fabric-1.20.1/src/main/java` → 0；forge → DiceCombatModifiers.java:206/218 + DiceCombatEvents.java:1084。

### D-07 event/AnvilUpgradeHandler.java:64-95 → fabric:全文｜铁砧「取走」授予筹码商店刷新次数的入口整段缺失
- forge 方法集 [onAnvilUpdate, onAnvilRepair, isDice, starCoinCost]；fabric 方法集只有 [onAnvilUpdate]（fabric:52 之后只剩注释）。
- forge:90-94 `ChipShopState next = ChipShopState.get(player).withRefreshGranted(); ChipShopState.store(player, next);` + LOGGER「CHIP_SHOP_REFRESH_GRANTED」。
- 影响：fabric 上从铁砧取走升星成品不再 +1 商店刷新次数（该玩法入口整条断链）。fabric `shop/ChipShopState.java:30/:197` javadoc 仍写「唯一调用点 = AnvilUpgradeHandler#onAnvilRepair」，文档与实现不符。
- 证据：`grep -rn "withRefreshGranted" fabric-1.20.1/src/main/java` → 仅 ChipShopState.java:200 定义，0 调用。

### D-08 item/card/TemporaryCardUtil.java:181-224/720/739 → fabric:全文｜临时牌获得者绑定 + 非法持有巡查整段缺失
- forge-only：`ownerOf`/`ownerKeyOf`/`isOwnedBy`/`isIllegalHold`/`purgeEquippedWhere`/`isLegalStoneOwner`/`purgeIllegalEquipped`/`purgeIllegalHoldings`；写入点为 `component/ModDataComponents.java:89 TEMPORARY_CARD_OWNER (ItemDataKey<UUID>)`。fabric 该类 682 行，上述全无，`TEMPORARY_CARD_OWNER` 全树 0。
- 连带断点：fabric `event/TemporaryCardEvents.java` 只有 `onItemToss`（缺 `onItemPickup` 的 `isIllegalHold` 拦截）；`item/card/BaseEffectCardItem.java` fabric 无 `isIllegalHold` 使用拦截（forge:402/:515）；`screen/CardInventoryMenu.java` fabric 无 `ownerKeyOf`（forge:306）。
- 影响：fabric 上临时牌（绿洲女王 nardis）不记录获得者 ⇒ 可转手他人使用/持有；2026-10-27「把骰子转手再卸下把别人的牌洗成自己的」漏洞在 fabric 仍敞开。
- 证据：`grep -rn "TEMPORARY_CARD_OWNER\|isIllegalHold\|purgeIllegalHoldings" fabric-1.20.1/src/main/java` → 0 行。

### D-09 event/EventEffects.java:356 → fabric:359-397｜事件效果「雷暴将至」夜间刷怪 ×2 在 fabric 被整段注释（自证失效）
- fabric 用 `/* … */` 包住整个 `onFinalizeSpawn`，并自述（:360-372）：「本线 platform/event/** 与全部 mixin 里都**没有** MobSpawnEvent 垫片，照抄即编译失败」「`AP_EVENT_EFFECT: storm_night_spawn_x2` 永不打印，即夜间刷怪 ×2 静默失效」。
- 影响：fabric 上 12 条事件效果里这 1 条永久无效；helper `placeStormCopy`/`hasStormWatcherNear` 保留但无调用者。
- 证据：`grep -n "storm_night_spawn_x2" fabric-1.20.1/src/main/java/com/merlinkitsune/astral_dice/event/EventEffects.java` → 仅注释内 :371/:395。

### D-10 combat/DiceCombatEvents.java:315-363/436/555 → fabric 无对应实现｜吸收(黄心)重对齐 / 幻影补偿 / 骰战 settle / 七咒联动
- forge 有 `PreHurtSnapshot`(`absorption`,`shield`)、`onLivingHurtRecordAbsorption`、`consumePreHurtAbsorption`、`compensateAbsorption`、`registerPhantom`/`phantomOf`/`withPhantomPeeled`、`onServerTickReconcilePhantoms`、`settleDiceDamage`、`applyAlanaBigHit`（调用点 :396/:414/:422）、以及骰战路径上 `RenShieldManager.remainingShieldAbsorption(defender)` 钳制终值（:346/:363）。
- fabric 同文件 `absorption|Absorption|phantom|Phantom` 匹配 **0**，`settleDiceDamage|applyAlanaBigHit|remainingShieldAbsorption` 也 0；fabric 只保留 :578-608 的骰战攻击力计算与 :1126-1129 `isVanillaCriticalHit`。
- forge `event/ChipDamageHandler.java:54-66` 注释即说明为何必须有重对齐：「骰战 settle 写回的吸收里含幻影 H，金额被改后平台 :1684 的扣减量就不再等于 H（气囊归零时 :1681 整段跳过 ⇒ 黄心白涨 H）⇒ 必须在所有出口（含早退）统一把吸收重对齐一次」，代码用 `try/finally` 收口；fabric 的 `event/ChipDamageHandler.java` 把同类逻辑内联（:46-75+），但没有 finally 段（fabric 无 `compensateAbsorption`/`settleBiteHeal`）。
- 影响：fabric 玩家带吸收（黄心/修女盾）时，骰战终值被改写后吸收扣减量与预期不符（黄心白涨或反向吞掉）；闸门/立牌类减伤分支同理。
- 另：forge:83-90 整段 `enigmaticlegacy` 七咒联动（`cursed_ring`/`the_acknowledgment`/`the_twist`/`the_bless` + `AP_CURSE_APPLY` 证据行）fabric 无，fabric:1526-1527 明写「2026-10-04 用户裁决:1.20.1 fabric 端排除全部第三方模组联动 ⇒ 原清单中的 enigmaticlegacy:eldritch_pan 已随该联动整体移除」⇒ 该子项属**已裁决裁剪**，仅登记；吸收/幻影/settle 部分不属联动，是可移植的功能缺口。

### D-11 event/ModTooltipHandler.java:779-819 → fabric 同文件 0 引用｜5 张筹码的 tooltip 块整段缺失
- forge 有 `FLASHLIGHT_BURST_CHIP`（:779-787，星光计数）、`FORTUNE_STICK_CHIP`（:789-797）、`ABYSS_REAGENT_CHIP`（:799-806，治疗点计数）、`INFINITE_GRID_CHIP`（:808-813，充能计数）、`SENSOR_CHIP`（:815-819）五块；fabric `ModTooltipHandler.java` 内 `ModItems.` 引用数分别为 2/1、1/0、1/0、1/0、1/0（forge/fabric）。
- 键 `tooltip.astral_dice.chip.{flashlight_burst,fortune_stick,abyss_reagent,infinite_grid,sensor}` 只在 forge 的 zh_cn.json:599-603 / en_us.json:599-603 出现，fabric 三语 **0**；物品本身 fabric 存在（ModItems/ModItemModelProvider/ModRecipeProvider/ModCreativeTabs 均有引用）。
- 另 `tooltip.astral_dice.weird_dice_heal`（forge:455-460 诡异骰子回血行，zh_cn/en_us:664）fabric 0。
- 影响：fabric 上这 5 张筹码无功能描述、无计数器提示；诡异骰子少一行说明。

### D-12 command/AstralPartyCommand.java:67/125/139 → fabric 无｜两个管理子命令整段缺失
- forge 有 `/astralparty killbarricade [radius|all]` 与 `killrepulsion`（方法 `killEntityNode`/`killEntities`，常量 `DEFAULT_KILL_RADIUS=64`/`MAX_KILL_RADIUS=1024`/`KILL_LOG_PREFIX="AFTERM"`，键 `command.astral_dice.astralparty.killbarricade.success`）。
- 证据：`grep -rn "killbarricade" fabric-1.20.1` → **0**（Java 与三语 lang 全无）；forge → AstralPartyCommand.java:67/:125/:139 + lang en_us:789 / ja_jp:788 / zh_cn:789。
- fabric 侧命令字面量只有 cleareffect / clearcardeffect / dump / finishsigncooldown / resetcardlock（fabric:153-170）。
- 影响：fabric 服管无法用命令清理滞留的路障/拒止卡牌实体（只能逐个 kill）。

### D-13 component/DeathPreservedBonuses.java:56/95/131 → fabric 无｜死亡保留少 4 类状态
- forge 有 `KANGEL_FAN_COUNT=5`、`AME_LOVE_LAYERS=6`、`AME_LOVE_CAP=7`、`PRESERVED_TARGETS`，快照含 `getKangelFanCount/getAmeLoveLayers/getAmeLoveCap/getKangelFanTargets`，恢复段三组「取大值」+ 目标串回填；fabric 只快照/恢复 `getCursedSwordBonus`。
- 影响：fabric 死亡后 Kangel 扇计数与目标、Ame 爱层数/上限全部丢失（forge 保留）。
- 说明：根因是 `component/ModAttachments.java`（排除名单，并行批次在处理）fabric 侧尚无这些附件键；本文件自身也不在排除名单，故在此登记为「待上游补齐后可移植」。

### D-14 network/VersionGate.java:56/86 → fabric:73｜SNAPSHOT 互通判定条件不同（行为差异确定，方向需裁决）
- forge：`isSnapshot() || isSnapshot(remote)` 时要求**完整版本串**相等（`"SNAPSHOT:"+全串`），否则才比 major.minor；fabric 恒为 `INTEROP_VERSION.equals(majorMinor(remoteVersion))`。
- 影响：SNAPSHOT 构建之间，fabric 允许不同 SNAPSHOT 小版本互联（更宽松），forge 拒绝。两线 javadoc 也各写一套（forge:「含 SNAPSHOT 的构建只与完整版本串相同的快照互通」；fabric:「仅二号位相同才允许互联」）。
- git：两线该文件最后提交都停在 2026-10-19 66d4d053，非近期单侧新增 ⇒ 需产品裁决以哪侧为准。

## §3 疑似但证据不足（请判）

1. event/DamageEffectCardHandler.java：forge `HIGHEST` + `foldBonusBypassingArmor`（40 次二分反解，使 bonus 不吃护甲，仍在 `LivingHurtEvent` 内）vs fabric `NORMAL` + `APPLYING_TRUE_BONUS` 独立真伤通道（`ModDamageTypes.trueDamage` + `target.hurt`）。两线对「法术加成是否吃抗性/魔法吸收/无敌帧」结论不同，但都是有意实现，需人工判哪边符合设计。
2. item/sign/PaparaSignItem.java 受击回血：forge 已改为 `ChipDamageHandler` finally 显式 `settleBiteHeal(event)`（单一出口、按最终金额）；fabric 仍是独立 `@SubscribeEvent(priority=LOWEST) onPaparaBiteHurtHeal`（fabric:69-78，`heal = max(1, amount/2)`）。同优先级两条监听器的执行顺序由注册顺序决定 ⇒ 可能读到筹码减伤前金额。功能上都有，属「顺序不确定」，不敢断言必坏。
3. client/CombatPowerHud.java：forge:31/74 取消 `overloadedarmorbar:overloadedarmorbar` 图层；fabric 只有 `mixin/client/ArmorBarCancelMixin.java:29` 注释承认该差异，未见等价取消 ⇒ 装了 OAB 时 fabric 可能双护甲条。需实机/字节码复核。
4. mixin/trade/MerchantContainerMixin.java:48 + MerchantMenuMixin.java:40 的 `astralDice$filterEmeraldExchange`（forge）在 fabric 全树 0 命中；但 fabric 有 `mixin/bridge/*` 21 个桥 mixin，未逐个比对是否等价实现。
5. event/NewSignEvents.java：forge 有 `onEffectApplicable(MobEffectEvent.Applicable)`（:49）门控；fabric 无。Fabric 1.20.1 无可取消的等价事件，疑为平台限制，但也可能是本该用 mixin 补而未补。
6. datagen/ModRecipeProvider.java（forge 1183 行 / fabric 1060 行）：forge 有 `chipRecipeConditions/withChipCondition/ChipConditionalRecipe` + `"astral_dice:chip_recipes_enabled"`，fabric 有 `healingPotion/regenerationPotion` 与更多 advancement（`has_card_medium`/`attack_card_large_from_medium`/`defense_card_epic_from_large` 等）。配方数量两线 17 vs 14 —— 需逐条比对哪些配方 fabric 少产，未完成。
7. item/sign/MamushiSignItem.java：两线冷却机制不同（forge `startActiveLockOnUse/skipCooldownAfterLock/handleDiceBlessingTriggered`… vs fabric `forcedActiveCooldownTicks/notifyForcedCooldown` + 键 `msg.astral_dice.mamushi_cooldown_locked`）。疑似 fabric 保留了已废弃机制，未定方向。
8. trade/EmeraldDiceTrade.java（forge `isEmeraldToStarCoin/rawCostA/filteredFor` vs fabric `discount`）、item/chip/BankCardChipItem.java（`getStarlightAmount/grantBitForStarlight` vs `getBaseStarlight/grantBitForBase`）、client/TargetSelectionClient.java（forge `closeFromServer` + `"undercover"`）—— 命名分歧，未逐句核语义是否等价。

## §4 已确认非漂移的典型样本（证明口径未放宽）

1. recipe/condition/ChipRecipesEnabledCondition.java（fabric 74 行 / forge 85 行）：fabric 自述「占位重写，不是 1:1 移植」「Fabric 1.20.1 没有任何对应机制」，`register()` 为空。**但** `data/astral_dice/recipes` 下含 `"conditions"`/`chip_recipes_enabled` 的 JSON 两线都是 0 个 ⇒ 该条件从未被任何配方引用，行为等价，不算漂移。
2. AstralDiceMod.java：forge 有 `ModList.get().isLoaded("starengine")` 否则 `throw IllegalStateException("Missing required dependency: StarEngine (starengine)")`；fabric 无此代码，但 `fabric.mod.json` 的 depends 已声明 `"starengine": "${lib_version_range}"` ⇒ 由 loader 强制校验，属平台差异。fabric 多出的 `onInitialize/setupConfig/backupOldConfigIfNeeded` 是 Fabric 入口与配置备份，属平台入口差异。
3. item/CurioSlotUtil.java（forge 220 行 / fabric 178 行）：forge 的 `allEquipped`/`forEachCurioStack` 在 fabric 类里没有，但 Fabric 侧在 `event/CurioDeathKeepHandler.java:49`、`shop/ChipShopRoller.java:259` 就地内联了等价实现（注释明写「对应 forge 蓝本」「本线没有该方法，故就地实现」）；差异仅为 copy vs 槽位实例（只读取值）与空栈是否回调（两处调用方均对空栈免疫）⇒ 非漂移。
4. combat/SpellDamageRegistry.java + event/IronSpellbooksCompat.java：fabric 明确注释「2026-09-29 裁剪：原第 5 项 Iron 的法术与魔法书整段删除」，并有独立裁剪说明 ⇒ 用户已裁决的裁剪，非滞后。
5. event/PlayerEffectProtection.java 之外的 291 个文件：去注释后方法名集合与字符串字面量集合**完全一致**，另有 179 个文件归一化后逐行全等 —— 这些不列入 §2。
