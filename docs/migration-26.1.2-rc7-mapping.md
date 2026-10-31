# 26.1.2 线移植 mapping（1.5.0-rc.7 → `neoforge-26.1.2`）

> 本文件由 `python3 tools/port_26_12.py doc --plan temp/p1/plan.json` 生成，`plan` 的数字来自实读；**状态列手工回填**，每波完成后更新。

## 1. 版本矩阵

| 项 | neoforge-1.21.1（源） | neoforge-26.1.2（目标） |
|---|---|---|
| `mod_version` | `1.5.0-rc.7+neoforge_1.21.1` | `1.5.0-rc.7+neoforge_26.1.2` |
| `starengine_version` | `1.1.0` | `1.1.0` |
| `minecraft_version` | `1.21.1` | `26.1.2` |
| `neo_version` | `21.1.235` | `26.1.2.109` |
| java 文件 | 411（A 149 缺 / B 190 需并 / C1 1 纯适配 / C0 71 相同） | 273 |

## 2. 文件分类总账

| 类别 | 文件数 | 行数 | 波次 |
|---|---|---|---|
| A 缺失（整文件移植） | 149 | 22210 | P2–P5 |
| B 需增量合并 | 190 | 4559 | P2–P5 |
| C1 纯适配（不迁移） | 1 | - | - |
| C0 完全一致（不动） | 71 | - | - |
| 26.1.2 独有 | 11 | - | - |
| 资源缺失 | 279 | - | P6 |

## 3. API 差异字典

机械规则来自 96 组「同文件两侧仅因适配而不同」的对拍，`tools/port_26_12.py codemod` 按此执行；未覆盖的高危符号进人工清单。

| 类别 | 1.21.1 写法 | 26.1.2 写法 | 取证 |
|---|---|---|---|
| 标识符类型 | `ResourceLocation` | `Identifier` | `26.1.2/.../audio/ModSounds.java:5` |
| 构造标识符 | `new ResourceLocation(ns, path)` | `Identifier.fromNamespaceAndPath(ns, path)` | `ModSounds.java:107` |
| 物品交互返回 | `InteractionResultHolder<ItemStack>` + `.success(x)` | `InteractionResult` + `.SUCCESS` | `26.1.2/.../item/card/BaseEffectCardItem.java:81` |
| 客户端判定 | `level.isClientSide`（字段） | `isClientSide()`（方法） | `26.1.2/.../client/CombatPowerHud.java` |
| 背包遍历 | `player.getInventory().items` | `getInventory().getNonEquipmentItems()` | 本次 `plan` 的 96 个纯适配对 |
| 消息发送 | `player.displayClientMessage(text, overlay)` | `sendOverlayMessage` / `sendSystemMessage` | 26.1.2 侧 13 处改写 |
| 服务端取用 | `entity.getServer()` | `entity.level().getServer()` | 26.1.2 侧 17 处改写 |
| 注册物品 | `new Item.Properties()` 直传 | `registerItem(name, props -> new XxxItem(...))` | `26.1.2/.../item/ModItems.java:157` |
| 容器点击 | `ClickType` | `ContainerInput` | `docs/compat-26.1.2-neoforge.md` §2 |
| 药水/组件 | `stack.get(DataComponents.FOOD)` 语义 | 26.1.2 data components 调整 | 同上 §2 |
| mixin 级别 | `JAVA_21` | `compatibilityLevel JAVA_25` | `docs/compat-26.1.2-neoforge.md` §3 |
| 事件注解 | `@EventBusSubscriber` + 任意 `@SubscribeEvent` | 无监听方法的类不得带注解（26.1.2 抛异常） | 同上 §4 |
| datagen 入口 | 单 `GatherDataEvent` | `.Client` / `.Server` 双入口 + 双输出根 | 同上 §5 |


## 4. A 类：缺失文件（整文件移植）

| 波次 | 相对 `src/main/java` 路径 | 行数 | 状态 |
|---|---|---|---|
| P2 | com/merlinkitsune/astral_dice/block/BarricadeBlock.java | 98 |  |
| P2 | com/merlinkitsune/astral_dice/block/CardShopBlock.java | 81 |  |
| P2 | com/merlinkitsune/astral_dice/block/ChipShopBlock.java | 148 |  |
| P2 | com/merlinkitsune/astral_dice/block/EventDrawBlock.java | 150 |  |
| P2 | com/merlinkitsune/astral_dice/block/ModBlocks.java | 55 |  |
| P2 | com/merlinkitsune/astral_dice/choice/CardChoiceManager.java | 274 |  |
| P2 | com/merlinkitsune/astral_dice/choice/CardChoiceType.java | 49 |  |
| P2 | com/merlinkitsune/astral_dice/client/BarricadeModel.java | 85 |  |
| P2 | com/merlinkitsune/astral_dice/client/BarricadeRenderer.java | 43 |  |
| P2 | com/merlinkitsune/astral_dice/client/BeamFxRenderer.java | 248 |  |
| P2 | com/merlinkitsune/astral_dice/client/CardChoiceClientHandler.java | 24 |  |
| P2 | com/merlinkitsune/astral_dice/client/CardShopClientState.java | 76 |  |
| P2 | com/merlinkitsune/astral_dice/client/ChipShopClientState.java | 265 |  |
| P2 | com/merlinkitsune/astral_dice/client/EventCardOverlay.java | 287 |  |
| P2 | com/merlinkitsune/astral_dice/client/EventChoiceClientHandler.java | 62 |  |
| P2 | com/merlinkitsune/astral_dice/client/EventChoiceScreen.java | 356 |  |
| P2 | com/merlinkitsune/astral_dice/client/RarityTooltipFrame.java | 84 |  |
| P2 | com/merlinkitsune/astral_dice/client/RepulsionModel.java | 132 |  |
| P2 | com/merlinkitsune/astral_dice/client/RepulsionRenderer.java | 43 |  |
| P2 | com/merlinkitsune/astral_dice/combat/DeathTriggerGuard.java | 97 |  |
| P2 | com/merlinkitsune/astral_dice/combat/ElectricShockFx.java | 82 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jade/AstralDiceJadePlugin.java | 50 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jade/EventDrawerCostProvider.java | 35 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jade/ShopCooldownClientProvider.java | 56 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jade/ShopCooldownServerDataProvider.java | 75 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jei/CardShopCategory.java | 157 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jei/CardShopJeiPlugin.java | 66 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jei/CardShopJeiRecipes.java | 39 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jei/CardShopRecipe.java | 19 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jei/ChipShopCategory.java | 166 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jei/ChipShopJeiPlugin.java | 69 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jei/ChipShopJeiRecipes.java | 39 |  |
| P2 | com/merlinkitsune/astral_dice/compat/jei/ChipShopRecipe.java | 21 |  |
| P2 | com/merlinkitsune/astral_dice/component/SignState.java | 151 |  |
| P2 | com/merlinkitsune/astral_dice/component/SignTimerSourceAdapter.java | 40 |  |
| P2 | com/merlinkitsune/astral_dice/datagen/DataGenerators.java | 29 |  |
| P2 | com/merlinkitsune/astral_dice/effect/AlanaIronMaidenEffect.java | 13 |  |
| P2 | com/merlinkitsune/astral_dice/effect/AmeLoveEffect.java | 62 |  |
| P2 | com/merlinkitsune/astral_dice/effect/AmeOverdoseEffect.java | 49 |  |
| P2 | com/merlinkitsune/astral_dice/effect/DeepseekFishingEffect.java | 16 |  |
| P2 | com/merlinkitsune/astral_dice/effect/DorothySocialiteEffect.java | 30 |  |
| P2 | com/merlinkitsune/astral_dice/effect/DorothyWarmthEffect.java | 18 |  |
| P2 | com/merlinkitsune/astral_dice/effect/JillMixedEffect.java | 15 |  |
| P2 | com/merlinkitsune/astral_dice/effect/KangelFanEffect.java | 55 |  |
| P2 | com/merlinkitsune/astral_dice/effect/KangelFanIndicatorEffect.java | 68 |  |
| P2 | com/merlinkitsune/astral_dice/effect/LinglingSurgeEffect.java | 25 |  |
| P2 | com/merlinkitsune/astral_dice/effect/ProblemDeliveryEffect.java | 20 |  |
| P2 | com/merlinkitsune/astral_dice/effect/RedHeatWarningEffect.java | 20 |  |
| P2 | com/merlinkitsune/astral_dice/effect/StormApproachingEffect.java | 20 |  |
| P2 | com/merlinkitsune/astral_dice/entity/BarricadeEntity.java | 446 |  |
| P2 | com/merlinkitsune/astral_dice/entity/ModEntityTypes.java | 56 |  |
| P2 | com/merlinkitsune/astral_dice/entity/RepulsionEntity.java | 1114 |  |
| P2 | com/merlinkitsune/astral_dice/event/AstralEventDispatcher.java | 168 |  |
| P2 | com/merlinkitsune/astral_dice/event/AstralEventIds.java | 39 |  |
| P2 | com/merlinkitsune/astral_dice/event/AstralEventRegistry.java | 223 |  |
| P2 | com/merlinkitsune/astral_dice/event/BarricadeAggroHandler.java | 70 |  |
| P2 | com/merlinkitsune/astral_dice/event/CurioDeathKeepHandler.java | 86 |  |
| P2 | com/merlinkitsune/astral_dice/event/DorothyWarmthHealContext.java | 78 |  |
| P2 | com/merlinkitsune/astral_dice/event/EventAnimationLock.java | 134 |  |
| P2 | com/merlinkitsune/astral_dice/event/EventCardService.java | 127 |  |
| P2 | com/merlinkitsune/astral_dice/event/EventChoiceManager.java | 308 |  |
| P2 | com/merlinkitsune/astral_dice/event/EventEffects.java | 610 |  |
| P2 | com/merlinkitsune/astral_dice/event/EventTimerStore.java | 158 |  |
| P2 | com/merlinkitsune/astral_dice/event/GlobalEventGate.java | 163 |  |
| P2 | com/merlinkitsune/astral_dice/event/InvestigationArbitration.java | 115 |  |
| P2 | com/merlinkitsune/astral_dice/event/IronSpellbooksCompat.java | 28 |  |
| P2 | com/merlinkitsune/astral_dice/event/LinglingSignEvents.java | 76 |  |
| P2 | com/merlinkitsune/astral_dice/event/ModEntityEvents.java | 44 |  |
| P2 | com/merlinkitsune/astral_dice/event/NewSignEvents.java | 120 |  |
| P2 | com/merlinkitsune/astral_dice/event/PlayerEffectProtection.java | 204 |  |
| P2 | com/merlinkitsune/astral_dice/event/RepulsionCreeperGuard.java | 96 |  |
| P2 | com/merlinkitsune/astral_dice/event/RepulsionTeleportGuard.java | 55 |  |
| P2 | com/merlinkitsune/astral_dice/event/api/AstralEvent.java | 54 |  |
| P2 | com/merlinkitsune/astral_dice/event/api/AstralEventEffect.java | 31 |  |
| P2 | com/merlinkitsune/astral_dice/event/api/AstralEventPolarity.java | 26 |  |
| P2 | com/merlinkitsune/astral_dice/event/api/AstralEventTier.java | 44 |  |
| P2 | com/merlinkitsune/astral_dice/event/api/AstralEventTrigger.java | 20 |  |
| P2 | com/merlinkitsune/astral_dice/fixes/DamageStackSanitizer.java | 152 |  |
| P2 | com/merlinkitsune/astral_dice/fixes/NeoForgeFixesLog.java | 35 |  |
| P2 | com/merlinkitsune/astral_dice/item/EventDrawItem.java | 71 |  |
| P2 | com/merlinkitsune/astral_dice/item/ModTags.java | 35 |  |
| P2 | com/merlinkitsune/astral_dice/item/RepulsionItem.java | 202 |  |
| P2 | com/merlinkitsune/astral_dice/item/RepulsionPlacement.java | 72 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/BarricadeCardItem.java | 175 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/RepulsionCardItem.java | 166 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/AbyssReagentChipItem.java | 142 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/ExtraBatteryChipItem.java | 67 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/FlashlightBurstChipItem.java | 121 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/FortuneStickChipItem.java | 127 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/InfiniteGridChipItem.java | 98 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/SensorChipItem.java | 75 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/AlanaSignItem.java | 91 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/AmeSignItem.java | 424 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/DeepseekSignItem.java | 189 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/DorothySignItem.java | 252 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/JillSignItem.java | 251 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/KangelSignItem.java | 388 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/LinglingSignItem.java | 70 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/LinglingSignManager.java | 476 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/MosesEnigmaticLink.java | 379 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/SignActionBar.java | 60 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/SignActiveLockMachine.java | 213 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/SignAttributeUtil.java | 49 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/SignEquipCheck.java | 36 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/SignHookDispatcher.java | 176 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/SignHooks.java | 71 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/HealOrHarmMobEffectMixin.java | 52 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/RegenerationMobEffectMixin.java | 34 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/client/EffectRenderingInventoryScreenMixin.java | 37 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/client/EntityRendererInvoker.java | 14 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/client/SignTranslucencyMixin.java | 81 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/fixes/LivingEntityDamageContainerMixin.java | 79 |  |
| P2 | com/merlinkitsune/astral_dice/network/BeamFxPayload.java | 53 |  |
| P2 | com/merlinkitsune/astral_dice/network/CardChoiceClosePayload.java | 41 |  |
| P2 | com/merlinkitsune/astral_dice/network/CardChoiceConfirmPayload.java | 42 |  |
| P2 | com/merlinkitsune/astral_dice/network/CardShopBuyPayload.java | 51 |  |
| P2 | com/merlinkitsune/astral_dice/network/CardShopDenyPayload.java | 45 |  |
| P2 | com/merlinkitsune/astral_dice/network/CardShopTransferListPayload.java | 51 |  |
| P2 | com/merlinkitsune/astral_dice/network/CardShopTransferPayload.java | 45 |  |
| P2 | com/merlinkitsune/astral_dice/network/ChipShopBuyPayload.java | 47 |  |
| P2 | com/merlinkitsune/astral_dice/network/ChipShopDenyPayload.java | 52 |  |
| P2 | com/merlinkitsune/astral_dice/network/ChipShopRefreshPayload.java | 41 |  |
| P2 | com/merlinkitsune/astral_dice/network/EventCardPayload.java | 35 |  |
| P2 | com/merlinkitsune/astral_dice/network/EventChoiceCancelPayload.java | 51 |  |
| P2 | com/merlinkitsune/astral_dice/network/EventChoiceClosePayload.java | 45 |  |
| P2 | com/merlinkitsune/astral_dice/network/EventChoiceConfirmPayload.java | 41 |  |
| P2 | com/merlinkitsune/astral_dice/network/EventChoiceStartPayload.java | 63 |  |
| P2 | com/merlinkitsune/astral_dice/network/EventChoiceTickPayload.java | 46 |  |
| P2 | com/merlinkitsune/astral_dice/network/TargetSelectClosePayload.java | 53 |  |
| P2 | com/merlinkitsune/astral_dice/recipe/condition/ChipRecipesEnabledCondition.java | 65 |  |
| P2 | com/merlinkitsune/astral_dice/screen/CardChoiceMenu.java | 263 |  |
| P2 | com/merlinkitsune/astral_dice/screen/CardChoiceScreen.java | 609 |  |
| P2 | com/merlinkitsune/astral_dice/screen/CardShopScreen.java | 2157 |  |
| P2 | com/merlinkitsune/astral_dice/screen/ChipShopScreen.java | 715 |  |
| P2 | com/merlinkitsune/astral_dice/shop/CardShopCatalog.java | 223 |  |
| P2 | com/merlinkitsune/astral_dice/shop/CardShopCycleTicker.java | 94 |  |
| P2 | com/merlinkitsune/astral_dice/shop/CardShopMenu.java | 1061 |  |
| P2 | com/merlinkitsune/astral_dice/shop/CardShopOpener.java | 36 |  |
| P2 | com/merlinkitsune/astral_dice/shop/CardShopShelf.java | 348 |  |
| P2 | com/merlinkitsune/astral_dice/shop/CardTradeFilter.java | 116 |  |
| P2 | com/merlinkitsune/astral_dice/shop/ChipShopCatalog.java | 365 |  |
| P2 | com/merlinkitsune/astral_dice/shop/ChipShopMenu.java | 529 |  |
| P2 | com/merlinkitsune/astral_dice/shop/ChipShopOpener.java | 102 |  |
| P2 | com/merlinkitsune/astral_dice/shop/ChipShopRoller.java | 233 |  |
| P2 | com/merlinkitsune/astral_dice/shop/ChipShopState.java | 213 |  |
| P2 | com/merlinkitsune/astral_dice/trade/CardTraderTrades.java | 168 |  |
| P2 | com/merlinkitsune/astral_dice/trade/ChipTraderTrades.java | 155 |  |
| P2 | com/merlinkitsune/astral_dice/world/ModPoiTypes.java | 83 |  |
| P2 | com/merlinkitsune/astral_dice/world/ModVillagerProfessions.java | 58 |  |

## 5. B 类：需增量合并（按待并行数排序）

合并方式：`base` = 该文件在 26.1.2 侧最后一次被改动那次提交里的 **1.21.1 版本**；`git show <base>:neoforge-1.21.1/src/main/java/<rel>` 取 base，与两侧当前内容做三路合并，冲突人工确认。

| 波次 | 相对路径 | 待并行数 | base | 状态 |
|---|---|---|---|---|
| P2 | com/merlinkitsune/astral_dice/component/ModAttachments.java | 694 | 392b5dad |  |
| P2 | com/merlinkitsune/astral_dice/datagen/ModRecipeProvider.java | 466 | e60dd348 |  |
| P2 | com/merlinkitsune/astral_dice/event/DamageEffectCardHandler.java | 204 | 19be7f0e |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/ElectricGloveChipItem.java | 169 | 392b5dad |  |
| P2 | com/merlinkitsune/astral_dice/item/card/TemporaryCardUtil.java | 167 | e7a2721c |  |
| P2 | com/merlinkitsune/astral_dice/combat/DiceCombatModifiers.java | 166 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/init/ModCreativeTabs.java | 133 | 994a96e1 |  |
| P2 | com/merlinkitsune/astral_dice/event/AnvilUpgradeHandler.java | 130 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/command/AstralPartyCommand.java | 112 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/component/DeathPreservedBonuses.java | 99 | b21f69c0 |  |
| P2 | com/merlinkitsune/astral_dice/combat/SpellDamageRegistry.java | 95 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/combat/DamageCardFxScheduler.java | 89 | b618e43d |  |
| P2 | com/merlinkitsune/astral_dice/event/PlayerTickEvents.java | 88 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/StarLightManager.java | 79 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/ModItems.java | 72 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/BaseSignItem.java | 67 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/NancyLuSignItem.java | 65 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/event/RailgunStrikeScheduler.java | 61 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/PerpetualMotionChipItem.java | 61 | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/network/VersionGate.java | 60 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/component/ModDataComponents.java | 58 | 2e0cddc5 |  |
| P2 | com/merlinkitsune/astral_dice/trade/EmeraldDiceTrade.java | 57 | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/event/ModEffectEvents.java | 51 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/event/WeirdDiceHandler.java | 48 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/ChargeManager.java | 45 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/VitaminPillChipItem.java | 45 | 581f7fcf |  |
| P2 | com/merlinkitsune/astral_dice/event/ModTooltipHandler.java | 43 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/FateGuidanceCardItem.java | 40 | a469633f |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/BankCardChipItem.java | 40 | 3de36fdb |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/MemberRecommendationChipItem.java | 38 | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/component/AppliedStone.java | 37 | 581f7fcf |  |
| P2 | com/merlinkitsune/astral_dice/screen/CardInventoryMenu.java | 34 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/AtmChipItem.java | 30 | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/CursedSwordChipItem.java | 29 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/target/TargetSelectionManager.java | 29 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/FenSignItem.java | 28 | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/ExclusiveCardUtil.java | 27 | 8c2f2833 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/jade/ArmorValueProviderMixin.java | 26 | 0a29b9c4 |  |
| P2 | com/merlinkitsune/astral_dice/item/InvestigationEventUtil.java | 25 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/BaseEffectCardItem.java | 25 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/MagicQuiverChipItem.java | 24 | 755c3621 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/BonnieSignItem.java | 23 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/ParunanSignItem.java | 22 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/resource/ResourceConversion.java | 21 | 8c2f2833 |  |
| P2 | com/merlinkitsune/astral_dice/client/TargetSelectionClient.java | 20 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/effect/ModEffects.java | 20 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/SherrySignItem.java | 20 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/LuluSignItem.java | 18 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/network/ModPayloads.java | 18 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/screen/ModMenuTypes.java | 18 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/audio/SoundPlayback.java | 17 | 9729da33 |  |
| P2 | com/merlinkitsune/astral_dice/event/EnderDiceHandler.java | 17 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/datagen/ModItemModelProvider.java | 16 | eeeef967 |  |
| P2 | com/merlinkitsune/astral_dice/event/ChipDamageHandler.java | 16 | 1a74323f |  |
| P2 | com/merlinkitsune/astral_dice/event/TemporaryCardEvents.java | 16 | 581f7fcf |  |
| P2 | com/merlinkitsune/astral_dice/item/CurioSlotUtil.java | 16 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/combat/DiceCombatContext.java | 15 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/BaseChipItem.java | 15 | 41d45552 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/MotoHelmetChipItem.java | 15 | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/item/dice/DiceTier.java | 15 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/WarpEngineChipItem.java | 14 | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/trade/MerchantMenuMixin.java | 14 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/event/PlayerLifecycleHandler.java | 13 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/HaiqingSignItem.java | 13 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/PaparaSignItem.java | 13 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/AstralDiceMod.java | 12 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/dice/DiceCurioItem.java | 12 | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/trade/ServerPlayerMixin.java | 12 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/effect/DragonRoarBreakEffect.java | 11 | 581f7fcf |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/EagleScopeChipItem.java | 11 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/item/dice/NetherStarDiceItem.java | 11 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/HannaSignItem.java | 11 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/trade/MerchantContainerMixin.java | 11 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/audio/ModSounds.java | 10 | 9cef1731 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/JasmineSignItem.java | 10 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/MosesSignItem.java | 10 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/RenShieldManager.java | 9 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/BankCardUnlimitedChipItem.java | 9 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/dice/ObsidianDiceItem.java | 9 | d393cb66 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/MamushiSignItem.java | 9 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/NardisSignItem.java | 9 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/combat/SpellDamageContext.java | 8 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/FightPoisonWithPoisonCardItem.java | 8 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/StarCoinHammerChipItem.java | 8 | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/MegasSignItem.java | 8 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/MarkManager.java | 7 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/UnwaveringCardItem.java | 6 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/PiercingGunChipItem.java | 6 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/combat/LivingPageImpact.java | 4 | de033604 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/ExpressDeliveryCardItem.java | 4 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/AdrenalineChipItem.java | 4 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/BookmarkChipItem.java | 4 | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/MisakiSignItem.java | 4 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/PandamanSignItem.java | 4 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/RenSignItem.java | 4 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/RinSignItem.java | 4 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/TeruSignItem.java | 4 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/ZhaoSignItem.java | 4 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/trade/MerchantOfferMixin.java | 4 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/trade/MerchantResultSlotMixin.java | 4 | 8744ba81 |  |
| P2 | com/merlinkitsune/astral_dice/network/TargetSelectStartPayload.java | 4 | 127fabd5 |  |
| P2 | com/merlinkitsune/astral_dice/effect/ConcealmentEffect.java | 3 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/ElectricSwordChipItem.java | 3 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/FlashlightChipItem.java | 3 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/SmartWatchChipItem.java | 3 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/damage/ModDamageTypes.java | 2 | ee5c84f2 |  |
| P2 | com/merlinkitsune/astral_dice/effect/UnwaveringEffect.java | 2 | 48089460 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/DragonCardUtil.java | 2 | 581f7fcf |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/CurrentCoreChipItem.java | 2 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/FriendshipBadgeChipItem.java | 2 | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/PrimordialCoreChipItem.java | 2 | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/RevengeHalberdChipItem.java | 2 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/PadmanSignItem.java | 2 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/combat/DamageNumberAggregator.java | 1 | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/client/ClientTickHandler.java | ? | 46986687 |  |
| P2 | com/merlinkitsune/astral_dice/client/CombatPowerHud.java | ? | 112ddfb4 |  |
| P2 | com/merlinkitsune/astral_dice/client/GlowingDustParticle.java | ? | 74597994 |  |
| P2 | com/merlinkitsune/astral_dice/client/IcebergTooltipCacheGuard.java | ? | d12813ef |  |
| P2 | com/merlinkitsune/astral_dice/client/KeyBindingSetup.java | ? | ca2edea4 |  |
| P2 | com/merlinkitsune/astral_dice/client/LibraryParticle.java | ? | 37d30068 |  |
| P2 | com/merlinkitsune/astral_dice/client/ModClientEvents.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/client/ModernUITooltipCompat.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/client/NancyLuClientEvents.java | ? | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/client/RenShieldRenderer.java | ? | d275b4fe |  |
| P2 | com/merlinkitsune/astral_dice/client/TargetOutlineCapture.java | ? | 0767aa73 |  |
| P2 | com/merlinkitsune/astral_dice/client/TargetSelectOverlay.java | ? | 3744a7a8 |  |
| P2 | com/merlinkitsune/astral_dice/client/TargetSelectionHighlighter.java | ? | 127fabd5 |  |
| P2 | com/merlinkitsune/astral_dice/client/gui/StarCoinWalletButtons.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/combat/OrbitalBombardmentManager.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/combat/ShootingStarManager.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/config/ModCommonConfig.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/damage/RailgunBolts.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/economy/StarCoinPickupHandler.java | ? | 581f7fcf |  |
| P2 | com/merlinkitsune/astral_dice/effect/BlueCurseEffect.java | ? | 07a70cf1 |  |
| P2 | com/merlinkitsune/astral_dice/effect/ChargeEffect.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/effect/EmpowerEffect.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/effect/HannaFloatEffect.java | ? | 581f7fcf |  |
| P2 | com/merlinkitsune/astral_dice/effect/JasmineSweepEffect.java | ? | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/effect/RenCounterEffect.java | ? | 127fabd5 |  |
| P2 | com/merlinkitsune/astral_dice/effect/RenShieldEffect.java | ? | d275b4fe |  |
| P2 | com/merlinkitsune/astral_dice/effect/TeruDescentEffect.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/effect/ZhaoBlessingEffect.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/event/AstralEventSystem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/event/FirstLootChestHandler.java | ? | 23e13af8 |  |
| P2 | com/merlinkitsune/astral_dice/event/HiddenCurseEnchantment.java | ? | 07a70cf1 |  |
| P2 | com/merlinkitsune/astral_dice/event/LivingPageFlightScheduler.java | ? | 392b5dad |  |
| P2 | com/merlinkitsune/astral_dice/event/LootInjectionHandler.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/event/NetherrackDiceHandler.java | ? | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/init/ModParticles.java | ? | b3245122 |  |
| P2 | com/merlinkitsune/astral_dice/item/HealingManager.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/BerserkCardItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/CardItem.java | ? | 79a1a65f |  |
| P2 | com/merlinkitsune/astral_dice/item/card/EffectCardPeriod.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/FuCardItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/HuoCardItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/LuxuryFeastCardItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/RandomCardHandler.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/card/YouHaveIHaveCardItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/AirbagChipItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/BigBowlStewChipItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/EnergyRecyclerChipItem.java | ? | 3c8f0d36 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/FanBigChipItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/FanSmallChipItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/RailgunChipItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/SandwichChipItem.java | ? | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/SatelliteChipItem.java | ? | 749457fc |  |
| P2 | com/merlinkitsune/astral_dice/item/chip/SpeedSkatesChipItem.java | ? | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/FannySignItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/KomachiSignItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/item/sign/MimiSignItem.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/PiglinAiMixin.java | ? | 3a6e1abc |  |
| P2 | com/merlinkitsune/astral_dice/mixin/client/GuiMixin.java | ? | 731e3855 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/client/LocalPlayerDropGuardMixin.java | ? | 146a7cd6 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/container/ContainerClickGuardMixin.java | ? | 146a7cd6 |  |
| P2 | com/merlinkitsune/astral_dice/mixin/jade/DisplayHelperArmorIconMixin.java | ? | 0a29b9c4 |  |
| P2 | com/merlinkitsune/astral_dice/network/ActionBarPayload.java | ? | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/network/DamageNumberPayload.java | ? | 9a01559b |  |
| P2 | com/merlinkitsune/astral_dice/network/EnderDieTotemPayload.java | ? | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/network/OpenCardInventoryPayload.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/network/RenShieldStatePayload.java | ? | d275b4fe |  |
| P2 | com/merlinkitsune/astral_dice/network/SignActivatePayload.java | ? | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/network/StarCoinBalancePayload.java | ? | 15d5ff25 |  |
| P2 | com/merlinkitsune/astral_dice/network/StarCoinWalletPayload.java | ? | 581f7fcf |  |
| P2 | com/merlinkitsune/astral_dice/network/TargetSelectCancelPayload.java | ? | ca2edea4 |  |
| P2 | com/merlinkitsune/astral_dice/network/TargetSelectConfirmPayload.java | ? | c32586b6 |  |
| P2 | com/merlinkitsune/astral_dice/particle/GlowingDustOptions.java | ? | 3bce4063 |  |
| P2 | com/merlinkitsune/astral_dice/particle/LibraryParticleOptions.java | ? | b3245122 |  |
| P2 | com/merlinkitsune/astral_dice/recipe/DiceUpgradeShapedRecipe.java | ? | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/recipe/ModRecipeSerializers.java | ? | d02f3ae2 |  |
| P2 | com/merlinkitsune/astral_dice/screen/CardInventoryScreen.java | ? | d02f3ae2 |  |

## 6. 26.1.2 独有文件（不迁移，逐条确认保留理由）

| 相对路径 | 保留理由 |
|---|---|
| com/merlinkitsune/astral_dice/client/OutlineCaptureRenderStateModifier.java |  |
| com/merlinkitsune/astral_dice/combat/EliteTargets.java |  |
| com/merlinkitsune/astral_dice/combat/PartyRelations.java |  |
| com/merlinkitsune/astral_dice/datagen/ClientDataGenerators.java |  |
| com/merlinkitsune/astral_dice/datagen/ServerDataGenerators.java |  |
| com/merlinkitsune/astral_dice/event/ChipSlotMigrationHandler.java |  |
| com/merlinkitsune/astral_dice/event/EffectTimerGuard.java |  |
| com/merlinkitsune/astral_dice/mixin/client/EffectsInInventoryMixin.java |  |
| com/merlinkitsune/astral_dice/mixin/client/LivingEntityOutlineCaptureMixin.java |  |
| com/merlinkitsune/astral_dice/target/HoldToSelect.java |  |
| com/merlinkitsune/astral_dice/target/SelfTargetable.java |  |

## 7. 注册表对账

- **effects**：1.21.1 67 → 26.1.2 53；缺 15，多 1
  - 缺：`airbag_guard_cd`, `alana_iron_maiden`, `ame_love`, `ame_overdose`, `deepseek_fishing`, `dorothy_socialite`, `dorothy_warmth`, `jill_mixed`, `kangel_fan`, `kangel_fan_indicator`, `lingling_surge`, `problem_delivery`, `red_heat_warning`, `storm_approaching`, `whetstone_guard_cd`
  - 多：`railgun_ready`
- **items**：1.21.1 154 → 26.1.2 137；缺 17，多 0
  - 缺：`abyss_reagent_chip`, `alana_sign`, `ame_sign`, `deepseek_sign`, `dorothy_sign`, `effect_card_barricade`, `effect_card_repulsion`, `event_ticket`, `extra_battery_chip`, `flashlight_burst_chip`, `fortune_stick_chip`, `infinite_grid_chip`, `jill_sign`, `kangel_sign`, `lingling_sign`, `repulsion`, `sensor_chip`
- **attachments**：1.21.1 137 → 26.1.2 106；缺 32，多 1
  - 缺：`abyss_reagent_granted`, `airbag_hit_charge_cooldown_end`, `alana_passive_bonus`, `ame_love_cap`, `ame_love_gain_cd_end`, `ame_love_layers`, `chip_shop_state`, `deepseek_buff_end`, `deepseek_idle_since`, `deepseek_last_pos`, `deepseek_last_tick`, `deepseek_move_ticks`, `dorothy_attack_bonus`, `dorothy_heal_cooldown_end`, `dorothy_partners_once`, `dorothy_window_end`, `event_drawer_blank_insert_at`, `event_drawer_cooldown_until`, `flashlight_burst_targets`, `fortune_stick_blessing_claimed`, `jill_buff_end`, `jill_buff_target`, `jill_pending_target`, `kangel_fan_count`, `kangel_fan_targets`, `lingling_granted`, `shop_promotion_until`, `shop_shelf`, `shop_transfer_cooldown_end`, `shop_transfer_used`, `sign_active_max_cooldown_base`, `sign_state`
  - 多：`mamushi_forced_cooldown_until`

## 8. 资源与 lang

| 资源目录 | 缺失数 |
|---|---|
| assets/astral_dice/textures | 218 |
| assets/astral_dice/patchouli_books | 27 |
| assets/astral_dice/models | 8 |
| assets/astral_dice/sounds | 8 |
| assets/astral_dice/blockstates | 4 |
| data/astral_dice/recipe | 4 |
| data/astral_dice/loot_table | 3 |
| data/minecraft/tags | 3 |
| data/astral_dice/tags | 2 |
| astral_dice.neoforge_fixes.mixins.json | 1 |
| data/astral_dice/advancement | 1 |
| assets/astral_dice/items | 0 |

- `zh_cn.json`：1.21.1 1070 键 → 26.1.2 836 键，缺 253，多 19

- `en_us.json`：1.21.1 1070 键 → 26.1.2 836 键，缺 253，多 19

- `ja_jp.json`：1.21.1 1070 键 → 26.1.2 836 键，缺 253，多 19

## 9. 波次与门禁

| 波 | 内容 | 规模 | 状态 |
|---|---|---|---|
| P0 | 基线快照（本文件 §1/§2） | 0 | 已完成 |
| P1 | `tools/port_26_12.py` + `tools/check_lang_sync.py` + 本文件 | 2 新文件 | 已完成 |
| P2 | 地基：ModAttachments/ModDataComponents/ModEffects/ModItems/ModPayloads/ModCreativeTabs + 本地 PartyRelations·EliteTargets 回沉 | ~3k 行 | 待做 |
| P3 | 系统层：event/network/combat/world/trade/command/recipe | ~5k 行 | 待做 |
| P4 | 商店与界面：shop/screen/client/block/entity/mixin/compat/choice | ~13k 行 | 待做 |
| P5 | 物品层：item/sign、item/chip、item/card、item | ~4.5k 行 | 待做 |
| P6 | 资源与 datagen（需授权） | 279 + 253×3 | 待做 |
| P7 | 门禁：编译 + 静态门禁 + 实机（需授权）+ 文档 | - | 待做 |

每波出口：`JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew :neoforge-26.1.2:classes` → `BUILD SUCCESSFUL`，且 `git status` 只出现本波预期文件。


## 10. 回滚点

每波一个提交（若获授权）；失败 `git reset --hard <上一波>`。回滚点登记：

| 波 | 提交 | 备注 |
|---|---|---|
| P1 | （待提交） | |
