package com.merlinkitsune.astral_dice;

import com.merlinkitsune.astral_dice.compat.curios.TrinketBridge;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.astral_dice.init.ModCompatibilityCheck;
import com.merlinkitsune.astral_dice.recipe.condition.ChipRecipesEnabledCondition;
import com.merlinkitsune.astral_dice.platform.FabricBridges;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.fml.event.lifecycle.FMLCommonSetupEvent;
import com.merlinkitsune.astral_dice.platform.fml.loading.FMLPaths;
import com.merlinkitsune.starengine.component.GameplayConstants;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 《星之骰戏》Fabric 1.20.1 入口。
 *
 * <h2>与 Forge 侧的结构对应</h2>
 * <table border="1">
 *   <caption>入口时序对照</caption>
 *   <tr><th>步骤</th><th>Forge 1.20.1</th><th>Fabric 1.20.1</th></tr>
 *   <tr><td>1. 提交注册</td><td>构造器里逐个 {@code ITEMS.register(modBus)}</td>
 *       <td>{@link #onInitialize()} 里逐个 {@code .register(BUS)}(同一形态)</td></tr>
 *   <tr><td>2. 注册事件监听</td><td>{@code @Mod.EventBusSubscriber} 注解自动注册 63 个类</td>
 *       <td>显式 {@code LoaderBus.INSTANCE.register(X.class)}(注解在 Fabric 无对应机制)</td></tr>
 *   <tr><td>3. 配置注册</td><td>{@code ModLoadingContext.registerConfig(COMMON, SPEC)}</td>
 *       <td>{@code ModCommonConfig.SPEC.load(...)} + 首次写出默认文件</td></tr>
 *   <tr><td>4. 通用初始化</td><td>{@code FMLCommonSetupEvent}</td>
 *       <td>自建总线上派发同名事件(触发 {@link #onCommonSetup})</td></tr>
 *   <tr><td>5. FAPI 桥接</td><td>—</td><td>{@link FabricBridges#install()}</td></tr>
 * </table>
 *
 * <p><b>已删除的 Forge 专属件</b>:{@code MixinRuntimeGate}(Sinytra Connector 二选一门控 ——
 * Fabric Loader 自带 Mixin,该门控无意义)、{@code mods.toml} 模板、{@code mixinbooster} 依赖、
 * Curios IMC 槽位注册(改数据包 {@code data/trinkets/slots/**})、{@code registerDisplayTest}
 * (版本门槛改由 {@code VersionGate} 的握手包承担)。
 */
public class AstralDiceMod implements ModInitializer {
    public static final String MODID = "astral_dice";
    public static final Logger LOGGER = LoggerFactory.getLogger("AstralDice");

    /** 注册/事件总线(bus 参数保留 Forge 形状:本实现不往总线注册东西,提交即注册)。 */
    public static final LoaderBus BUS = LoaderBus.INSTANCE;

    @Override
    public void onInitialize() {
        // ⚠️ 必须是第一条语句:下面第 7 步的 TrinketBridge.registerAll() 会触碰
        //    dev.emi.trinkets.api.*,而 Trinkets 缺席时那个类**无法加载**。
        //    先做「Trinkets 或 Accessories 二选一」判定,玩家看到的就是完整的中文说明
        //    而不是一个 NoClassDefFoundError 堆栈(2026-09-29 用户裁决)。
        ModCompatibilityCheck.verifyAccessoryProviderOrThrow();
        // ⚠️ 必须在这里(**mod 初始化期**)强制附件键注册 —— 它的静态初始化是惰性的,
        //    若推迟到「第一次读写附件」(= 玩家登录处理器)才跑,则玩家 NBT 反序列化时
        //    注册表里还没有这些 id,Fabric 会逐条 "Unknown attachment type ... skipping"
        //    **静默丢弃**全部持久化值(实测一次登录丢 33 个键,含 guide_book_given
        //    ⇒ 手册每次登录补发一本)。详见 ModAttachments#ensureRegistered 与 KI-F17。
        com.merlinkitsune.astral_dice.component.ModAttachments.ensureRegistered();
        // 同上理由:筹码商店的持久键内联在 ChipShopState 里(B1-CHIPSHOP),其静态初始化同样是惰性的。
        // 不在初始化期触发 ⇒ 玩家 NBT 反序列化时注册表里没有 chip_shop_state,
        // Fabric 逐条 "Unknown attachment type ... skipping" **静默丢弃**(冷却/刷新次数全丢)。
        com.merlinkitsune.astral_dice.shop.ChipShopState.CHIP_SHOP_STATE.name();
        FabricBridges.installEarly();
        // 战利品注入(FAPI LootTableEvents.MODIFY):替代 Forge 侧的 GLM + LootTableLoadEvent 两条通道
        com.merlinkitsune.astral_dice.loot.FabricLootInjector.register();
        commitRegistrations();
        // B0 平台注册链(顺序敏感):POI 建 BlockState→PoiType 映射表依赖方块已注册;
        // 职业依赖 POI 的 ResourceKey 存在;交易表依赖职业已 commit(否则 RegistryObject.get() 抛异常)。
        com.merlinkitsune.astral_dice.world.ModVillagerProfessions.bootstrap();
        com.merlinkitsune.astral_dice.world.ModPoiTypes.bootstrap();
        com.merlinkitsune.astral_dice.trade.CardTraderTrades.register();
        com.merlinkitsune.astral_dice.trade.ChipTraderTrades.register();
        // 实体属性:Fabric 无 EntityAttributeCreationEvent,改走 FabricDefaultAttributeRegistry。
        com.merlinkitsune.astral_dice.event.ModEntityEvents.bootstrap();
        // 自建的「NBT 约束配方」序列化器在 1.21.1 已删除:两条「指定药水」配方改用
        // fabric:components 自定义 ingredient 表达组件约束,不再需要自定义 RecipeSerializer
        // (1.21.1 的 RecipeSerializer 只剩 codec()/streamCodec(),没有 fromJson/toNetwork 可覆写)。
        registerListeners();
        // 「青之诅咒」附魔(内部标记)的隐藏:Fabric 侧用 FAPI 的 ItemGroupEvents 摘掉原版
        // 为每个附魔自动生成的附魔书条目(创造栏「材料」页 + 「搜索」页,JEI 随之消失)。
        // tooltip 侧的整行抹除在 ModTooltipHandler#onItemTooltip 内完成。
        com.merlinkitsune.astral_dice.event.HiddenCurseEnchantment.register();
        // ⚠️ 必须装桥,否则**全部**事件永不派发(2026-09-29 修:此前只调了 installEarly(),
        //    install() 从未被调用 ⇒ tick / 登录登出 / 命令 / 伤害 / Puzzles 那一整套
        //    都处于「代码在、但没接上」的静默失效状态)。位置 = 监听器注册之后,
        //    这样桥第一次派发时订阅者一定已在总线上。
        FabricBridges.install();
        // 饰品通道(2026-11-05 用户裁决:后端只做 Trinkets;Accessories / ACL 属不兼容)。
        // ⚠️ 守卫放在**调用点**而不是方法体内 —— TrinketBridge 直接引用
        //    dev.emi.trinkets.api.*(含内部 record Adapter implements Trinket),
        //    Trinkets 缺席时必须让这个类**永不被加载**。
        if (ModCompatibilityCheck.isTrinketsPresent()) {
            TrinketBridge.registerAll();
        }
        // Waystones 传送联动:仅在该模组加载时反射注册事件,未安装时静默跳过
        // (对应 Forge 侧构造器末尾的 WaystoneWarpCompat.init();内部自带 isLoaded("waystones") 守卫,
        //  反射不兼容时 init 自身 try/catch 兜底,不会影响启动)。
        com.merlinkitsune.astral_dice.event.WaystoneWarpCompat.init();
        setupConfig();
        // 筹码配方开关条件(需求 1):把 astral_dice:chip_recipes_enabled 注册进
        // fabric-resource-conditions-api-v1 的注册表。位置要求 = 任何数据包加载之前;
        // onInitialize 是客户端与专用服务器都会执行、且早于 datapack 装载的唯一合适时机。
        // 不能挪进 AstralDiceClient —— 专用服务器上未注册 => 67 + 67 个带条件的 JSON
        // 会因 Unknown condition 被 fail-closed 静默丢弃(只有一条 WARN)。
        ChipRecipesEnabledCondition.register();
        // 通用初始化(Forge 的 FMLCommonSetupEvent 等价物)
        BUS.post(new FMLCommonSetupEvent());
        // 订阅类审计(服务端视角):扫描本包下带 @SubscribeEvent 的类,列出「带注解却从未登记」的
        // —— 补上 dispatchReport() 的盲区(它只列已注册的事件类)。客户端包在此被排除,
        // 因为 AstralDiceClient 的 register 晚于本入口,由客户端侧的 verifyClientSide() 收口。
        com.merlinkitsune.astral_dice.platform.event.SubscriptionAudit.verifyServerSide();
        LOGGER.info("Astral Dice mod loaded.");
        LOGGER.info("May the god of the dice be with you!");
    }

    /** 逐个提交 DeferredRegister(声明面与 Forge 侧构造器一致)。 */
    private static void commitRegistrations() {
        // ⚠️ 方块必须最先注册:ModItems 里的 BlockItem 供应商在 commit 时会读 ModBlocks.X.get(),
        //    方块尚未绑定会抛 IllegalStateException(而不是静默失败)。
        com.merlinkitsune.astral_dice.block.ModBlocks.BLOCKS.register(BUS);
        com.merlinkitsune.astral_dice.item.ModItems.ITEMS.register(BUS);
        com.merlinkitsune.astral_dice.effect.ModEffects.EFFECTS.register(BUS);
        com.merlinkitsune.astral_dice.audio.ModSounds.SOUNDS.register(BUS);
        // ⚠️ 1.21.1 起附魔是数据驱动注册表(data/<ns>/enchantment/*.json),没有「代码注册附魔」这条路径;
        //    1.20.1 的 ModEnchantments.ENCHANTMENTS.register(BUS) 在此删除(neoforge-1.21.1 线亦无此行)。
        com.merlinkitsune.astral_dice.recipe.ModRecipeSerializers.RECIPE_SERIALIZERS.register(BUS);
        com.merlinkitsune.astral_dice.init.ModCreativeTabs.CREATIVE_TABS.register(BUS);
        com.merlinkitsune.astral_dice.screen.ModMenuTypes.MENU_TYPES.register(BUS);
        com.merlinkitsune.astral_dice.init.ModParticles.PARTICLE_TYPES.register(BUS);
        com.merlinkitsune.astral_dice.entity.ModEntityTypes.ENTITY_TYPES.register(BUS);
    }

    /**
     * 注册全部事件监听类。
     *
     * <p>Forge 侧由 {@code @Mod.EventBusSubscriber} 注解自动完成;Fabric 无该机制 ⇒ 显式列全。
     * ⚠️ **新增订阅类时必须在此登记**,否则其 {@code @SubscribeEvent} 方法不会被调用(静默失效)。
     */
    private static void registerListeners() {
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.DamageCardFxScheduler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.DamageNumberAggregator.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.DiceCombatEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.OrbitalBombardmentManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.PlayerHostilityTrackerEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.SherryThrowManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.ShootingStarManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.command.AstralPartyCommand.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.economy.StarCoinPickupHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.AnvilUpgradeHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.BarricadeAggroHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.ChipDamageHandler.class);
        // B2-8 批次:死亡保底 —— onPlayerDrops(LivingDropsEvent, LOWEST) 决定「死亡不掉落饰品」、
        // onPlayerClone(PlayerEvent.Clone) 决定「重生时放回饰品」;漏登记 = 两条都静默失效(饰品掉落且不归还)。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.CurioDeathKeepHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.DamageEffectCardHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.EnderDiceHandler.class);
        // B2-8 批次:弹卡动画锁 —— onPlayerDeath(LivingDeathEvent)/onPlayerLogout(PlayerLoggedOutEvent) 解除锁定;
        // 漏登记 = 玩家在弹卡窗口内死亡或掉线后操作被永久冻结(只有重启服务端才恢复)。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.EventAnimationLock.class);
        // B2-8 批次:事件牌选卡会话 —— onPlayerTick(END) 推进 15s 等待期并超时结算,
        // 死亡/登出/换维度各有一条强制结算兜底;漏登记 = 等待期永不推进、兜底全失效。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.EventChoiceManager.class);
        // B2-8 批次:事件牌结算 —— onPlayerTick 每 tick 驱动 EventCardService.drain + 定时器清扫,
        // onHeal(LivingHealEvent) 拦「本次治疗被禁止」的场合;漏登记 = 弹卡后永不结算(卡面挂死)。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.EventEffects.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.FirstLootChestHandler.class);
        // B4-SIGNS 批次:立牌事件的宿主类(forge 由 @Mod.EventBusSubscriber 自动订阅)
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.LinglingSignEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.LivingPageFlightScheduler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.LootInjectionHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.ModEffectEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.ModTooltipHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.NetherrackDiceHandler.class);
        // B4-SIGNS 批次:铁处女/摸鱼免伤、多萝西温暖叠层、修女被动清除的宿主类
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.NewSignEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.ObsidianDiceHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.PlayerLifecycleHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.PlayerTickEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.RailgunStrikeScheduler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.RepulsionCreeperGuard.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.RepulsionTeleportGuard.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.TemporaryCardEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.InvestigationEventUtil.class);
        // 魔渊幼体立牌(sykes)的被动与「渊蚀印记」降层:AbyssMarkManager 带 @SubscribeEvent
        // (onMarkExpired / onServerTick)、SykesSignItem 带 @SubscribeEvent(onAttackHostile / onWardenDamage)
        // ⇒ 两者都必须显式登记;漏登记 = 印记不降层、被动加伤/施印与监守者减伤全部静默失效。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.AbyssMarkManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.MarkManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.RenShieldManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.card.FateGuidanceCardItem.class);
        // 筹码事件钩子(B1-CHIPS-WIRING):AbyssReagentChipItem 带 @SubscribeEvent onPlayerDeath ⇒ 必须显式登记。
        // 受击充能(2026-10-15 用户裁决):AirbagChipItem 带 @SubscribeEvent onIncomingDamage(LivingHurtEvent)
        // ⇒ 必须显式登记;漏登记 = 受击不再攒充能(静默失效,编译与构建都不报错)。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.AirbagChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.AbyssReagentChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.AdrenalineChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.CursedSwordChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.ElectricSwordChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.FlashlightChipItem.class);
        // 同上:FortuneStickChipItem 带 @SubscribeEvent onHostileKill ⇒ 必须显式登记。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.FortuneStickChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.SmartWatchChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.WarpEngineChipItem.class);
        // B4-SIGNS 批次:以下 5 个立牌类自身带 @SubscribeEvent,XenForge 的订阅者注解在本线不存在
        // ⇒ 必须逐类显式登记;漏登记 = 编译通过、相关被动/伤害钩子永不触发。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.AlanaSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.AmeSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.BonnieSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.FannySignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.DeepseekSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.DorothySignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.HaiqingSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.HannaSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.JillSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.KangelSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.KomachiSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.LinglingSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.MimiSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.NardisSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.PandamanSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.PaparaSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.SherrySignItem.class);
        // 魔渊幼体(sykes):主动走 handleUse(无需登记),但被动 onAttackHostile(LivingDamageEvent) 与
        // onWardenDamage(LivingHurtEvent) 是 @SubscribeEvent ⇒ 必须在此登记。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.SykesSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.TeruSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.ZhaoSignItem.class);
        // 卡牌商店货架周期(B1-CARDSHOP):forge 靠 @Mod.EventBusSubscriber 自动订阅,Fabric 必须显式登记;
        // 漏登记 = 换牌/补差全部静默停摆(不报错,只是货架永不刷新)。
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.shop.CardShopCycleTicker.class);
        // 选卡会话的死亡/掉线兜底（LoaderBus 不递归内部类，必须显式登记；漏登记 = 兜底静默失效）
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.choice.CardChoiceManager.class);
        // ⚠️ 本类自身的处理器(见 onCommonSetup)。必须**注册本类**而不是某个接口 ——
        //    2026-09-29 修:此处原为 `register(IEventBus.class)`,而 IEventBus 是接口、不含任何
        //    @SubscribeEvent 方法 ⇒ 扫描结果为空集;叠加 onCommonSetup 当时是**实例方法**
        //    (LoaderBus.scan 对 instance==null 的非静态方法直接 continue)⇒ 该事件派发到空链,
        //    其内部三个初始化(兼容性校验 / 网络通道注册 / 卡牌注册表)全部从未执行。
        LoaderBus.INSTANCE.register(AstralDiceMod.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.target.TargetSelectionManager.class);
    }

    /** 客户端订阅类(由 AstralDiceClient 登记,避免专用服务端加载客户端类)。 */
    /**
     * 配置:先备份旧版本文件,再读取;文件缺失时写出默认值。
     * (Forge 侧由 FML 的配置系统承担,逻辑等价。)
     */
    private static void setupConfig() {
        java.nio.file.Path configDir = FMLPaths.CONFIGDIR.get();
        java.nio.file.Path configPath = configDir.resolve("astral_dice-common.toml");
        backupOldConfigIfNeeded(configPath, ModCommonConfig.CONFIG_VERSION);
        boolean existed = ModCommonConfig.SPEC.load(configPath);
        ModCommonConfig.SPEC.save(configPath);
        if (!existed) {
            LOGGER.info("[Astral Dice] 已生成默认配置 {}", configPath);
        }
        // 配置已加载:把配置值打成快照推给库的 GameplayConstants(库不读配置文件)
        GameplayConstants.applyConfig(ModCommonConfig.snapshot());
    }

    /** 配置文件版本号低于当前值时备份旧文件(口径与 Forge 侧一致)。 */
    private static void backupOldConfigIfNeeded(java.nio.file.Path configPath, int currentVersion) {
        try {
            if (!java.nio.file.Files.exists(configPath)) {
                return;
            }
            int fileVersion = readConfigVersion(configPath);
            if (fileVersion >= currentVersion) {
                return;
            }
            java.nio.file.Path backup = configPath.resolveSibling(configPath.getFileName() + ".bak");
            java.nio.file.Files.copy(configPath, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // ⚠️ 2026-10-03 修复:备份之后必须把版本号**写回**文件 —— 否则该键永不更新,
            //    且每次启动都会重复备份一份内容相同的 .bak(详见 rewriteConfigVersion 的说明)。
            rewriteConfigVersion(configPath, currentVersion);
            LOGGER.info("[Astral Dice] 配置 {} 版本过旧(v{} < v{}),已备份至 {}", configPath.getFileName(),
                    fileVersion, currentVersion, backup);
        } catch (Exception e) {
            LOGGER.warn("[Astral Dice] 备份旧配置失败: {}", e.toString());
        }
    }

    private static int readConfigVersion(java.nio.file.Path configPath) {
        try {
            String content = java.nio.file.Files.readString(configPath, java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("config_version\\s*=\\s*(\\d+)").matcher(content);
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 把配置文件里的 {@code config_version} 就地改写为当前版本号。
     *
     * <p>⚠️ 2026-10-03 修复(用户实报「配置文件变动的版本号不会更新」):此前只做备份、**不更新
     * 文件里的版本号** —— 该键由 {@code ModCommonConfig} 用
     * {@code define("config_version", CONFIG_VERSION)} 定义,而框架对「已存在且校验通过」的键
     * **原样保留文件中的值**(NeoForge {@code ModConfigSpec.correct} / Forge
     * {@code ForgeConfigSpec.correct} 都只在**键缺失**或**值未通过校验**时才写入默认值)
     * ⇒ 版本号永远停在旧值,并且**每次启动都会重复备份**一份内容相同的 {@code .bak}。
     * 这里在备份之后把版本号显式写回,使「一个版本号 = 一次迁移」成立。
     *
     * <p>只做**一处文本替换**(保留其余字节、缩进与换行原样);匹配不到该键时**不动文件**
     * (下次启动按老逻辑再备份一次,不引入比改动前更坏的行为)。
     */
    private static void rewriteConfigVersion(java.nio.file.Path configPath, int version) {
        try {
            String content = java.nio.file.Files.readString(configPath, java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("config_version\\s*=\\s*\\d+").matcher(content);
            if (!m.find()) {
                LOGGER.warn("[Astral Dice] 配置 {} 中未找到 config_version 键,跳过版本号写回", configPath.getFileName());
                return;
            }
            String updated = m.replaceFirst("config_version = " + version);
            if (!updated.equals(content)) {
                java.nio.file.Files.writeString(configPath, updated, java.nio.charset.StandardCharsets.UTF_8);
                LOGGER.info("[Astral Dice] 配置 {} 版本号已更新为 v{}", configPath.getFileName(), version);
            }
        } catch (Exception e) {
            LOGGER.warn("[Astral Dice] 写回配置版本号失败: {}", e.toString());
        }
    }

    /**
     * 通用初始化(Forge 的 {@code FMLCommonSetupEvent} 处理器,方法体保持原样)。
     *
     * <p>⚠️ 顺序有意保持不变:{@link ModCompatibilityCheck#verifyOrThrow()} 仍在
     * {@code enqueueWork} 之前**同步**执行 —— 不兼容组合必须尽早、干净地失败。
     *
     * <p>⚠️ 必须是 {@code static}(2026-09-29 修):本模组的自建总线用反射扫描
     * {@code @SubscribeEvent} 方法,而 {@code LoaderBus.scan} 对**非静态**方法在
     * {@code instance == null} 时会直接跳过(见 {@code LoaderBus.java} 的
     * {@code if (!isStatic && instance == null) continue;})。此前该方法是实例方法、
     * 且 {@link #registerListeners()} 从未注册本类 ⇒ 派发落到空链,
     * {@link ModCompatibilityCheck#verifyOrThrow()}、网络通道注册、卡牌注册表三者**全部从未执行**。
     * 现有调用方均无实例状态依赖,故改为静态是安全的最小修法。
     */
    @com.merlinkitsune.astral_dice.platform.event.SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        ModCompatibilityCheck.verifyOrThrow();
        // 队伍后端接入状态诊断(machine line: AP_FAB_PARTY)。
        // ⚠️ 放这里而不是 onInitialize():三条队伍系统的**启用开关**由公共配置驱动
        //    (GameplayConstants.applyConfig),此阶段配置已加载,读数才是真实生效值。
        //    三个后端全是反射,契约不符时会静默退回原版计分板 —— 这条机器行让
        //    「装了 FTB Teams / OPAC 却没生效」在日志里一眼可断言(2026-10-01 新增)。
        com.merlinkitsune.astral_dice.combat.PartyRelations.reportBackends();
        event.enqueueWork(() -> {
            // 网络通道注册(Fabric Networking;协议版本握手见 VersionGate)
            com.merlinkitsune.astral_dice.network.ModNetwork.register();
            // 卡牌类型注册表初始化(战斗牌定义集中管理)
            com.merlinkitsune.astral_dice.combat.CardRegistry.init();
        });
    }
}
