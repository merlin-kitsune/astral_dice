package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
        public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU,
                        AstralDiceMod.MODID);

        public static final Supplier<MenuType<CardInventoryMenu>> CARD_INVENTORY = MENU_TYPES.register("card_inventory",
                        () -> new MenuType<>(CardInventoryMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
