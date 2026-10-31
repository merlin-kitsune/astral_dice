package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.shop.CardShopMenu;
import com.merlinkitsune.astral_dice.shop.ChipShopMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
        public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU,
                        AstralDiceMod.MODID);

        public static final Supplier<MenuType<CardInventoryMenu>> CARD_INVENTORY = MENU_TYPES.register("card_inventory",
                        () -> new MenuType<>(CardInventoryMenu::new, FeatureFlags.DEFAULT_FLAGS));

        public static final Supplier<MenuType<CardShopMenu>> CARD_SHOP = MENU_TYPES.register("card_shop",
                        () -> new MenuType<>(CardShopMenu::new, FeatureFlags.DEFAULT_FLAGS));

        public static final Supplier<MenuType<ChipShopMenu>> CHIP_SHOP = MENU_TYPES.register("chip_shop",
                        () -> new MenuType<>(ChipShopMenu::new, FeatureFlags.DEFAULT_FLAGS));

        /**
         * 通用「选卡界面」菜单（2026-10-17）：槽位数（1/3）与卡牌类型由**打开参数**决定，
         * 因此必须用 {@link IMenuTypeExtension#create} 走 extra data（token + slotCount + type.ordinal()），
         * 不能用 2 参 {@code MenuType::new}（那会让客户端构造出 0 槽 / 类型未知的菜单）。
         */
        public static final Supplier<MenuType<CardChoiceMenu>> CARD_CHOICE =
                        MENU_TYPES.register("card_choice",
                                        () -> IMenuTypeExtension.create(CardChoiceMenu::new));
}
