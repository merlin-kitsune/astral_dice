package com.merlinkitsune.astral_dice;

import com.merlinkitsune.astral_dice.compat.curios.TrinketBridge;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.astral_dice.init.ModCompatibilityCheck;
import com.merlinkitsune.astral_dice.platform.FabricBridges;
import com.merlinkitsune.astral_dice.platform.event.LoaderBus;
import com.merlinkitsune.astral_dice.platform.fml.event.lifecycle.FMLCommonSetupEvent;
import com.merlinkitsune.astral_dice.platform.fml.loading.FMLPaths;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
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
        FabricBridges.installEarly();
        // 战利品注入(FAPI LootTableEvents.MODIFY):替代 Forge 侧的 GLM + LootTableLoadEvent 两条通道
        com.merlinkitsune.astral_dice.loot.FabricLootInjector.register();
        commitRegistrations();
        registerListeners();
        // ⚠️ 必须装桥,否则**全部**事件永不派发(2026-09-29 修:此前只调了 installEarly(),
        //    install() 从未被调用 ⇒ tick / 登录登出 / 命令 / 伤害 / Puzzles 那一整套
        //    都处于「代码在、但没接上」的静默失效状态)。位置 = 监听器注册之后,
        //    这样桥第一次派发时订阅者一定已在总线上。
        FabricBridges.install();
        TrinketBridge.registerAll();
        // Accessories(软依赖)在场时,再挂一条饰品通道:槽位验证器 + 物品适配器。
        // ⚠️ 守卫不可省 —— AccessoriesCompat 直接引用 io.wispforest.accessories.*,
        //    软依赖缺席时必须让它**永不被加载**(见 CuriosApi 的类加载隔离说明)。
        if (com.merlinkitsune.astral_dice.compat.curios.CuriosApi.isAccessoriesPresent()) {
            com.merlinkitsune.astral_dice.compat.accessories.AccessoriesCompat.register();
            // 启动自检:把三条「物品→槽位」准入链的判据打进日志(数据包加载后执行)
            com.merlinkitsune.astral_dice.compat.accessories.AccessoriesCompat.installSlotDiagnostics();
        }
        setupConfig();
        // 通用初始化(Forge 的 FMLCommonSetupEvent 等价物)
        BUS.post(new FMLCommonSetupEvent());
        LOGGER.info("Astral Dice mod loaded.");
        LOGGER.info("May the god of the dice be with you!");
    }

    /** 逐个提交 DeferredRegister(声明面与 Forge 侧构造器一致)。 */
    private static void commitRegistrations() {
        com.merlinkitsune.astral_dice.item.ModItems.ITEMS.register(BUS);
        com.merlinkitsune.astral_dice.effect.ModEffects.EFFECTS.register(BUS);
        com.merlinkitsune.astral_dice.audio.ModSounds.SOUNDS.register(BUS);
        com.merlinkitsune.astral_dice.effect.ModEnchantments.ENCHANTMENTS.register(BUS);
        com.merlinkitsune.astral_dice.recipe.ModRecipeSerializers.RECIPE_SERIALIZERS.register(BUS);
        com.merlinkitsune.astral_dice.init.ModCreativeTabs.CREATIVE_TABS.register(BUS);
        com.merlinkitsune.astral_dice.screen.ModMenuTypes.MENU_TYPES.register(BUS);
        com.merlinkitsune.astral_dice.init.ModParticles.PARTICLE_TYPES.register(BUS);
    }

    /**
     * 注册全部事件监听类。
     *
     * <p>Forge 侧由 {@code @Mod.EventBusSubscriber} 注解自动完成;Fabric 无该机制 ⇒ 显式列全。
     * ⚠️ **新增订阅类时必须在此登记**,否则其 {@code @SubscribeEvent} 方法不会被调用(静默失效)。
     */
    private static void registerListeners() {
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.DiceCombatEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.OrbitalBombardmentManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.PlayerHostilityTrackerEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.SherryThrowManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.combat.ShootingStarManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.command.AstralPartyCommand.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.economy.StarCoinPickupHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.AnvilUpgradeHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.ChipDamageHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.DamageEffectCardHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.EnderDiceHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.FirstLootChestHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.LivingPageFlightScheduler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.LootInjectionHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.ModEffectEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.ModTooltipHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.NetherrackDiceHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.ObsidianDiceHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.PlayerLifecycleHandler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.PlayerTickEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.RailgunStrikeScheduler.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.event.TemporaryCardEvents.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.InvestigationEventUtil.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.MarkManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.RenShieldManager.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.card.FateGuidanceCardItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.AdrenalineChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.CursedSwordChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.ElectricSwordChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.FlashlightChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.FriendshipBadgeChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.SatelliteChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.SmartWatchChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.VitaminPillChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.chip.WarpEngineChipItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.BonnieSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.FannySignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.HaiqingSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.HannaSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.KomachiSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.MimiSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.NardisSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.PandamanSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.PaparaSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.SherrySignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.TeruSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.ZhaoSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.platform.event.IEventBus.class);
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
     * 通用初始化(Forge 的 {@code FMLCommonSetupEvent} 处理器,方法体保持原样)。
     *
     * <p>⚠️ 顺序有意保持不变:{@link ModCompatibilityCheck#verifyOrThrow()} 仍在
     * {@code enqueueWork} 之前**同步**执行 —— 不兼容组合必须尽早、干净地失败。
     */
    @com.merlinkitsune.astral_dice.platform.event.SubscribeEvent
    public void onCommonSetup(FMLCommonSetupEvent event) {
        ModCompatibilityCheck.verifyOrThrow();
        event.enqueueWork(() -> {
            // 网络通道注册(Fabric Networking;协议版本握手见 VersionGate)
            com.merlinkitsune.astral_dice.network.ModNetwork.register();
            // 卡牌类型注册表初始化(战斗牌定义集中管理)
            com.merlinkitsune.astral_dice.combat.CardRegistry.init();
        });
    }
}
