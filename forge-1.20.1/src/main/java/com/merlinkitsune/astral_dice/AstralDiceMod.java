package com.merlinkitsune.astral_dice;

import com.merlinkitsune.astral_dice.config.ModCommonConfig;
import com.merlinkitsune.starenginelib.component.GameplayConstants;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.ModEnchantments;
import com.merlinkitsune.astral_dice.init.ModCompatibilityCheck;
import com.merlinkitsune.astral_dice.init.ModCreativeTabs;
import com.merlinkitsune.astral_dice.init.ModParticles;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.network.VersionGate;
import com.merlinkitsune.astral_dice.recipe.ModRecipeSerializers;
import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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
        // ⚠️ 不兼容模组黑名单(Magic Coins / SG-Economy)的检查**不能放在这里**:它是否拒绝取决于
        //    「星币钱包」开关(config/ModCommonConfig 的 enable_star_coin_wallet),而配置要到**本阶段之后**
        //    才加载(装载状态机:CONSTRUCT -> CONFIG_LOAD -> COMMON_SETUP),此处读配置会抛「配置尚未加载」。
        //    ⇒ 检查已移到 onCommonSetup 的 ModCompatibilityCheck.verifyOrThrow()(详见该类类头)。
        ModItems.ITEMS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        com.merlinkitsune.astral_dice.audio.ModSounds.SOUNDS.register(modEventBus);
        ModEnchantments.ENCHANTMENTS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        // 全局战利品修饰符序列化器:1.20.1 的 Forge 自带注册表里**没有任何内置项**
        // (没有 forge:add_table),必须由本模组注册 astral_dice:add_table,
        // 否则 data/astral_dice/loot_modifiers/*.json 全部解码失败(详见 loot/AstralLootModifiers)
        com.merlinkitsune.astral_dice.loot.AstralLootModifiers.SERIALIZERS.register(modEventBus);
        modEventBus.register(this);
        // 配置:配置项定义、TOML 读写与配置 GUI 全部留在本模组(见 config/ModCommonConfig)。
        // 旧版本配置文件先备份,再由 Forge 继承旧值写入新配置(仅公共配置;client 配置已移除)。
        backupOldConfigIfNeeded("astral_dice-common.toml", ModCommonConfig.CONFIG_VERSION);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, ModCommonConfig.SPEC);
        // 版本互通门槛(见 AGENTS.md):多人生服列表的「兼容」标记按 mod_version 的 major.minor 判定,
        // 与 SimpleChannel 的握手门槛同一判据。Forge 默认的 MATCH_VERSION 要求完整版本号完全相同,
        // 会把 1.2.0 ↔ 1.2.1 这类同二号位组合误标为不兼容,故显式注册本判据。
        net.minecraftforge.fml.ModLoadingContext.get().registerDisplayTest(
                VersionGate::interopVersion,
                (remoteVersion, isFromServer) -> VersionGate.accepts(remoteVersion));
        // Iron 的法术与魔法书联动:仅在模组加载时注册其事件处理器(类引用只在加载条件下触发)
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
            MinecraftForge.EVENT_BUS.register(com.merlinkitsune.astral_dice.event.IronSpellbooksCompat.class);
        }
        // Waystones 传送联动:仅在模组加载时反射注册事件,未安装时静默跳过
        com.merlinkitsune.astral_dice.event.WaystoneWarpCompat.init();
    }

    // 若配置文件版本号低于当前版本(新增了配置项):备份旧文件,由 Forge 加载时继承旧值并补齐新项
    private static void backupOldConfigIfNeeded(String fileName, int currentVersion) {
        try {
            java.nio.file.Path configPath = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(fileName);
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

    @SubscribeEvent
    public void onCommonSetup(FMLCommonSetupEvent event) {
        // 不兼容模组黑名单(Magic Coins / SG-Economy):**只有在「星币钱包」启用时才拒绝启动**
        // (2026-09-22 用户裁决),因此必须等到配置加载完成才能判定 —— 本事件就在 CONFIG_LOAD 之后。
        // 命中即抛 ModLoadingException ⇒ 游戏停在加载错误界面并显示提示原文(链路见 ModCompatibilityCheck 类头)。
        // 放在 enqueueWork **之前**:同步执行,不与其它 mod 的延迟任务交错,失败得越干净越好。
        ModCompatibilityCheck.verifyOrThrow();
        event.enqueueWork(() -> {
            // 配置已加载:把配置值打成快照推给库的 GameplayConstants(库不读配置文件,见 config/ModCommonConfig)
            GameplayConstants.applyConfig(ModCommonConfig.snapshot());
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
            // 卡牌类型注册表初始化(战斗牌定义集中管理)
            com.merlinkitsune.astral_dice.combat.CardRegistry.init();
            LOGGER.info("Astral Dice mod loaded.");
            LOGGER.info("May the god of the dice be with you!");
        });
    }
}
