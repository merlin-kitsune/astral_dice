package com.merlinkitsune.astral_dice;

import com.merlinkitsune.astral_dice.component.ModAttachments;
import com.merlinkitsune.astral_dice.component.ModDataComponents;
import com.merlinkitsune.starenginelib.client.StarEngineConfigScreen;
import com.merlinkitsune.starenginelib.config.StarEngineConfigs;
import com.merlinkitsune.astral_dice.effect.ModEffects;
import com.merlinkitsune.astral_dice.event.AstralEvents;
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
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
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
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        AstralEvents.init();
        // 配置:Cloth Config 的 AutoConfig 全接管(含文件读写)。
        // schema 在共享库 starengine_lib(StarEngineCommonConfig),本方法内部依次完成
        // 「旧 astral_dice-common.toml 迁移 → 读盘 → 推送到 GameplayConstants」,
        // 因此这里调用后配置值即为真实值,不再需要旧 CommonSetup 里的 refresh 时机。
        StarEngineConfigs.register();
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
            // 模组列表的 Config 按钮:NeoForge 用 IConfigScreenFactory 扩展点。
            // 屏幕内容由库提供(库内持有 AutoConfig holder),这里只做平台接线。
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (container, parent) -> StarEngineConfigScreen.create(parent));
        }
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.CARD_INVENTORY.get(), CardInventoryScreen::new);
    }

    @SubscribeEvent
    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
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
