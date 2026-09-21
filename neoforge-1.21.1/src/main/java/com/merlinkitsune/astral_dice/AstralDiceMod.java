package com.merlinkitsune.astral_dice;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.init.ModCompatibilityCheck;
import com.merlinkitsune.astral_dice.init.ModCreativeTabs;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.recipe.ModRecipeSerializers;
import com.merlinkitsune.astral_dice.screen.CardInventoryScreen;
import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import com.merlinkitsune.astral_dice.combat.CardRegistry;
import com.merlinkitsune.astral_dice.event.IronSpellbooksCompat;

@Mod(AstralDiceMod.MODID)
public class AstralDiceMod {
    public static final String MODID = "astral_dice";
    private static final Logger LOGGER = LoggerFactory.getLogger(AstralDiceMod.class);

    public AstralDiceMod(IEventBus modEventBus, ModContainer modContainer) {
        // 不兼容模组黑名单(Magic Coins / SG-Economy):命中即拒绝启动(提示见 ModCompatibilityCheck)。
        // 必须放在**一切注册之前** —— 越早失败,玩家看到的错误越干净,也不会留下半注册状态。
        ModCompatibilityCheck.verifyOrThrow();
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::registerScreens);
        }
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

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.CARD_INVENTORY.get(), CardInventoryScreen::new);
    }

    @SubscribeEvent
    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 配置已加载:把配置值打成快照推给库的 GameplayConstants(库不读配置文件,见 config/ModCommonConfig)
            GameplayConstants.applyConfig(ModCommonConfig.snapshot());
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
