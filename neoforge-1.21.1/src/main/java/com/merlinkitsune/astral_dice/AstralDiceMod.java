package com.merlinkitsune.astral_dice;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.init.ModCompatibilityCheck;
import com.merlinkitsune.astral_dice.init.ModCreativeTabs;
import com.merlinkitsune.astral_dice.init.ModParticles;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.recipe.ModRecipeSerializers;
import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

@Mod(AstralDiceMod.MODID)
public class AstralDiceMod {
    public static final String MODID = "astral_dice";
    private static final Logger LOGGER = LoggerFactory.getLogger(AstralDiceMod.class);

    public AstralDiceMod(IEventBus modEventBus, ModContainer modContainer) {
        // ⚠️ 不兼容模组黑名单(Magic Coins / SG-Economy)的检查**不能放在这里**:它是否拒绝取决于
        //    「星币钱包」开关(config/ModCommonConfig 的 enable_star_coin_wallet),而配置要到**本阶段之后**
        //    才加载(装载顺序:构造 -> 注册表初始化 -> Config loading),此处读配置会抛「配置尚未加载」。
        //    ⇒ 检查已移到 onCommonSetup 的 ModCompatibilityCheck.verifyOrThrow()(详见该类类头)。
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        com.merlinkitsune.astral_dice.audio.ModSounds.SOUNDS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        // 配置版本检查:旧版本配置文件先备份,再由 NeoForge 继承旧值写入新配置(仅公共配置;client 配置已移除)
        backupOldConfigIfNeeded("astral_dice-common.toml", ModCommonConfig.CONFIG_VERSION);
        modContainer.registerConfig(ModConfig.Type.COMMON, ModCommonConfig.SPEC);
        modEventBus.register(this);
        // Iron 的法术与魔法书联动:仅在模组加载时注册其事件处理器(类引用只在加载条件下触发)
        if (net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS
                    .register(com.merlinkitsune.astral_dice.event.IronSpellbooksCompat.class);
        }
        // Waystones 传送联动:仅在模组加载时反射注册事件,未安装时静默跳过
        com.merlinkitsune.astral_dice.event.WaystoneWarpCompat.init();
        // ⚠️ 客户端菜单界面(ModMenuTypes.CARD_INVENTORY → CardInventoryScreen)的注册**不在这里**。
        // CardInventoryScreen 继承 AbstractContainerScreen、签名里带 net.minecraft.client.gui.GuiGraphics,
        // 属纯客户端类;而本类是**主模组入口,专用服务端同样会加载**。
        // 在此处用 `FMLEnvironment.dist == Dist.CLIENT` 分支守卫只能拦住**执行**,拦不住**符号解析**:
        // 只要本方法被调用(JIT/解释器进入且未内联),`CardInventoryScreen::new` 所在的方法引用常量
        // 就会要求解析其声明类型 ⇒ NoClassDefFoundError。故注册点必须整体搬到**永不加载于服务端**的类:
        // 见 client/ModClientEvents#registerScreens(@EventBusSubscriber(Dist.CLIENT, bus = MOD))。
        // (forge-1.20.1 侧从一开始就是这个写法,此处为对齐修正)
    }

    // 若配置文件版本号低于当前版本(新增了配置项):备份旧文件,由 NeoForge 加载时继承旧值并补齐新项
    private static void backupOldConfigIfNeeded(String fileName, int currentVersion) {
        try {
            java.nio.file.Path configPath = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(fileName);
            if (!java.nio.file.Files.exists(configPath))
                return;
            int fileVersion = readConfigVersion(configPath);
            if (fileVersion >= currentVersion)
                return;
            java.nio.file.Path backup = configPath.resolveSibling(fileName + ".bak");
            java.nio.file.Files.copy(configPath, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // ⚠️ 2026-10-03 修复:备份之后必须把版本号**写回**文件 —— 否则该键永不更新,
            //    且每次启动都会重复备份一份内容相同的 .bak(详见 rewriteConfigVersion 的说明)。
            rewriteConfigVersion(configPath, currentVersion);
            LOGGER.info("[Astral Dice] 配置 {} 版本过旧(v{} < v{}),已备份至 {}", fileName, fileVersion, currentVersion, backup);
        } catch (Exception e) {
            LOGGER.warn("[Astral Dice] 备份旧配置 {} 失败: {}", fileName, e.toString());
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

    @SubscribeEvent
    private void onCommonSetup(FMLCommonSetupEvent event) {
        // 不兼容模组黑名单(Magic Coins / SG-Economy):**只有在「星币钱包」启用时才拒绝启动**
        // (2026-09-22 用户裁决),因此必须等到配置加载完成才能判定 —— 本事件就在 Config loading 之后。
        // 命中即抛 ModLoadingException ⇒ 游戏停在加载错误界面并显示提示原文(链路见 ModCompatibilityCheck 类头)。
        // 放在 enqueueWork **之前**:同步执行,不与其它 mod 的延迟任务交错,失败得越干净越好。
        ModCompatibilityCheck.verifyOrThrow();
        event.enqueueWork(() -> {
            // 配置已加载:把配置值打成快照推给库的 GameplayConstants(库不读配置文件,见 config/ModCommonConfig)
            GameplayConstants.applyConfig(ModCommonConfig.snapshot());
            // 队伍后端接入状态诊断(machine line: AP_PARTY)。
            // ⚠️ 挂在 applyConfig **之后**:三个队伍系统的启用开关由公共配置驱动
            //    (GameplayConstants.applyConfig),此前的读数只是默认值,不是真实生效值。
            //    三个后端全是反射,契约不符时会静默退回原版计分板 —— 这条机器行让
            //    「装了 FTB Teams / OPAC 却没生效」在日志里一眼可断言(2026-10-01 新增)。
            com.merlinkitsune.astral_dice.combat.PartyRelations.reportBackends();
            // 卡牌类型注册表初始化(战斗牌定义集中管理)
            com.merlinkitsune.astral_dice.combat.CardRegistry.init();
            LOGGER.info("Astral Dice mod loaded.");
            LOGGER.info("May the god of the dice be with you!");
            CuriosApi.registerCurio(ModItems.DICE.get(), (ICurioItem) ModItems.DICE.get());
            CuriosApi.registerCurio(ModItems.GOLDEN_DICE.get(), (ICurioItem) ModItems.GOLDEN_DICE.get());
            CuriosApi.registerCurio(ModItems.GLASS_DICE.get(), (ICurioItem) ModItems.GLASS_DICE.get());
            CuriosApi.registerCurio(ModItems.DIAMOND_DICE.get(), (ICurioItem) ModItems.DIAMOND_DICE.get());
            CuriosApi.registerCurio(ModItems.NETHERITE_DICE.get(), (ICurioItem) ModItems.NETHERITE_DICE.get());
            CuriosApi.registerCurio(ModItems.EMERALD_DICE.get(), (ICurioItem) ModItems.EMERALD_DICE.get());
            CuriosApi.registerCurio(ModItems.PARUNAN_SIGN.get(), (ICurioItem) ModItems.PARUNAN_SIGN.get());
            CuriosApi.registerCurio(ModItems.JASMINE_SIGN.get(), (ICurioItem) ModItems.JASMINE_SIGN.get());
            CuriosApi.registerCurio(ModItems.MISAKI_SIGN.get(), (ICurioItem) ModItems.MISAKI_SIGN.get());
            CuriosApi.registerCurio(ModItems.MIMI_SIGN.get(), (ICurioItem) ModItems.MIMI_SIGN.get());
            CuriosApi.registerCurio(ModItems.LULU_SIGN.get(), (ICurioItem) ModItems.LULU_SIGN.get());
            CuriosApi.registerCurio(ModItems.KOMACHI_SIGN.get(), (ICurioItem) ModItems.KOMACHI_SIGN.get());
            CuriosApi.registerCurio(ModItems.FLASHLIGHT_CHIP.get(), (ICurioItem) ModItems.FLASHLIGHT_CHIP.get());
            CuriosApi.registerCurio(ModItems.CUTTER_CHIP.get(), (ICurioItem) ModItems.CUTTER_CHIP.get());
            CuriosApi.registerCurio(ModItems.SCOPE_CHIP.get(), (ICurioItem) ModItems.SCOPE_CHIP.get());
            CuriosApi.registerCurio(ModItems.EAGLE_SCOPE_CHIP.get(), (ICurioItem) ModItems.EAGLE_SCOPE_CHIP.get());
            CuriosApi.registerCurio(ModItems.MEDKIT_EMERGENCY_CHIP.get(),
                    (ICurioItem) ModItems.MEDKIT_EMERGENCY_CHIP.get());
            CuriosApi.registerCurio(ModItems.MEDKIT_COMPLETE_CHIP.get(),
                    (ICurioItem) ModItems.MEDKIT_COMPLETE_CHIP.get());
            CuriosApi.registerCurio(ModItems.TARGET_CHIP.get(), (ICurioItem) ModItems.TARGET_CHIP.get());
            CuriosApi.registerCurio(ModItems.EIGHT_SIDED_DICE.get(), (ICurioItem) ModItems.EIGHT_SIDED_DICE.get());
            CuriosApi.registerCurio(ModItems.PADMAN_SIGN.get(), (ICurioItem) ModItems.PADMAN_SIGN.get());
            CuriosApi.registerCurio(ModItems.FANNY_SIGN.get(), (ICurioItem) ModItems.FANNY_SIGN.get());
            CuriosApi.registerCurio(ModItems.RIN_SIGN.get(), (ICurioItem) ModItems.RIN_SIGN.get());
        });
    }
}
