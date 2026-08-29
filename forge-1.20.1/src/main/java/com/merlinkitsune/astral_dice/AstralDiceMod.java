package com.merlinkitsune.astral_dice;

import com.merlinkitsune.astral_dice.component.GameplayConstants;
import com.merlinkitsune.astral_dice.config.ModClientConfig;
import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.ModEnchantments;
import com.merlinkitsune.astral_dice.event.AstralEvents;
import com.merlinkitsune.astral_dice.init.ModCreativeTabs;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.recipe.ModRecipeSerializers;
import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypeMessage;


@Mod(AstralDiceMod.MODID)
public class AstralDiceMod {
    public static final String MODID = "astral_dice";
    private static final Logger LOGGER = LoggerFactory.getLogger(AstralDiceMod.class);

    public AstralDiceMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModEnchantments.ENCHANTMENTS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        AstralEvents.init();
        modEventBus.register(this);
        // 配置版本检查:旧版本配置文件先备份,再由 Forge 继承旧值写入新配置
        backupOldConfigIfNeeded("astral_dice-common.toml", ModCommonConfig.CONFIG_VERSION);
        backupOldConfigIfNeeded("astral_dice-client.toml", ModClientConfig.CONFIG_VERSION);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModCommonConfig.SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ModClientConfig.SPEC);
        // Iron 的法术与魔法书联动:仅在模组加载时注册其事件处理器(类引用只在加载条件下触发)
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
            MinecraftForge.EVENT_BUS.register(com.merlinkitsune.astral_dice.event.IronSpellbooksCompat.class);
        }
    }

    // 若配置文件版本号低于当前版本(新增了配置项):备份旧文件,由 Forge 加载时继承旧值并补齐新项
    private static void backupOldConfigIfNeeded(String fileName, int currentVersion) {
        try {
            java.nio.file.Path configPath = FMLPaths.CONFIGDIR.get().resolve(fileName);
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
            return m.find() ? java.lang.Integer.parseInt(m.group(1)) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @SubscribeEvent
    public void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 网络通道注册(1.20.1 SimpleChannel)
            ModNetwork.register();
            // Curios 槽位类型注册(1.20.1 经 IMC;对应 1.21 的 curios JSON 槽位注册)
            // dice=骰子(1)、stand=立牌(1)、chip=筹码(默认 0,按骰子星级动态增长)
            InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                    () -> new SlotTypeMessage.Builder("dice").size(1)
                            .icon(new net.minecraft.resources.ResourceLocation(AstralDiceMod.MODID, "slot/empty_dice_slot")).build());
            InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                    () -> new SlotTypeMessage.Builder("stand").size(1)
                            .icon(new net.minecraft.resources.ResourceLocation(AstralDiceMod.MODID, "slot/empty_stand_slot")).build());
            InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                    () -> new SlotTypeMessage.Builder("chip").size(0)
                            .icon(new net.minecraft.resources.ResourceLocation(AstralDiceMod.MODID, "slot/empty_chip_slot")).build());
            // 配置已加载:将配置值刷新到 GameplayConstants
            GameplayConstants.refresh();
            // 卡牌类型注册表初始化(战斗牌定义集中管理)
            com.merlinkitsune.astral_dice.combat.CardRegistry.init();
            LOGGER.info("Astral Dice mod loaded.");
            LOGGER.info("May the god of the dice be with you!");
        });
    }
}
