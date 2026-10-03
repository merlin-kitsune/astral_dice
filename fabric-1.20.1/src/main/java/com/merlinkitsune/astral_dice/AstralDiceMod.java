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
        FabricBridges.installEarly();
        // 战利品注入(FAPI LootTableEvents.MODIFY):替代 Forge 侧的 GLM + LootTableLoadEvent 两条通道
        com.merlinkitsune.astral_dice.loot.FabricLootInjector.register();
        commitRegistrations();
        // 自建配方序列化器(astral_dice:nbt_shaped;用于「指定药水」的 NBT 精确匹配)。
        // ⚠️ 必须在数据包加载之前 —— 否则带 astral_nbt 约束的两条配方会因找不到 serializer
        //    而整条加载失败(而不是退回宽松匹配)。onInitialize 全程早于 datapack 装载,故安全。
        com.merlinkitsune.astral_dice.crafting.AstralRecipeSerializers.register();
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
        // Trinkets 通道(硬→软:2026-09-29 起 Trinkets 与 Accessories **二选一**即可)。
        // ⚠️ 守卫放在**调用点**而不是方法体内 —— TrinketBridge 直接引用
        //    dev.emi.trinkets.api.*(含内部 record Adapter implements Trinket),
        //    Trinkets 缺席时必须让这个类**永不被加载**(与下面 AccessoriesCompat 同一纪律)。
        if (ModCompatibilityCheck.isTrinketsPresent()) {
            TrinketBridge.registerAll();
        }
        // Accessories(软依赖)在场时,再挂一条饰品通道:槽位验证器 + 物品适配器。
        // ⚠️ 守卫不可省 —— AccessoriesCompat 直接引用 io.wispforest.accessories.*,
        //    软依赖缺席时必须让它**永不被加载**(见 CuriosApi 的类加载隔离说明)。
        if (ModCompatibilityCheck.isAccessoriesPresent()) {
            com.merlinkitsune.astral_dice.compat.accessories.AccessoriesCompat.register();
            // 启动自检:把三条「物品→槽位」准入链的判据打进日志(数据包加载后执行)
            com.merlinkitsune.astral_dice.compat.accessories.AccessoriesCompat.installSlotDiagnostics();
        }
        setupConfig();
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
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.MosesEnigmaticLink.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.NancyLuSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.NardisSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.PandamanSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.PaparaSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.SherrySignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.TeruSignItem.class);
        LoaderBus.INSTANCE.register(com.merlinkitsune.astral_dice.item.sign.ZhaoSignItem.class);
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
