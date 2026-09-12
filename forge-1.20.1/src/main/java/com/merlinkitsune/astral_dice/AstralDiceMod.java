package com.merlinkitsune.astral_dice;

import com.merlinkitsune.starenginelib.client.StarEngineConfigScreen;
import com.merlinkitsune.starenginelib.config.StarEngineConfigs;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.effect.ModEnchantments;
import com.merlinkitsune.astral_dice.event.AstralEvents;
import com.merlinkitsune.astral_dice.init.ModCreativeTabs;
import com.merlinkitsune.astral_dice.item.ModItems;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import com.merlinkitsune.astral_dice.recipe.ModRecipeSerializers;
import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
        // 配置:Cloth Config 的 AutoConfig 全接管(含文件读写)。
        // schema 在共享库 starengine_lib(StarEngineCommonConfig),本方法内部依次完成
        // 「旧 astral_dice-common.toml 迁移 → 读盘 → 推送到 GameplayConstants」,
        // 因此这里调用后配置值即为真实值,不再需要旧 CommonSetup 里的 refresh 时机。
        StarEngineConfigs.register();
        // 模组列表的 Config 按钮:Forge 用 ConfigScreenHandler.ConfigScreenFactory 扩展点;
        // 屏幕内容由库提供(库内持有 AutoConfig holder),这里只做平台接线。
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, parent) -> StarEngineConfigScreen.create(parent)));
        }
        // Iron 的法术与魔法书联动:仅在模组加载时注册其事件处理器(类引用只在加载条件下触发)
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
            MinecraftForge.EVENT_BUS.register(com.merlinkitsune.astral_dice.event.IronSpellbooksCompat.class);
        }
        // Waystones 传送联动:仅在模组加载时反射注册事件,未安装时静默跳过
        com.merlinkitsune.astral_dice.event.WaystoneWarpCompat.init();
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
            // 卡牌类型注册表初始化(战斗牌定义集中管理)
            com.merlinkitsune.astral_dice.combat.CardRegistry.init();
            LOGGER.info("Astral Dice mod loaded.");
            LOGGER.info("May the god of the dice be with you!");
        });
    }
}
