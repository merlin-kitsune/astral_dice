package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AstralDiceMod.MODID);

    public static final RegistryObject<MenuType<CardInventoryMenu>> CARD_INVENTORY = MENU_TYPES.register("card_inventory",
            () -> new MenuType<>(CardInventoryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** 服务端打开卡牌栏(原 OpenCardInventoryPayload 的服务端逻辑;需佩戴骰子)。 */
    public static void openCardInventory(ServerPlayer serverPlayer) {
        var curios = CuriosCompat.getCuriosInventory(serverPlayer);
        if (curios.isEmpty() || curios.get().findFirstCurio(DiceCurioItem::isDiceItem).isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable("msg.astral_dice.no_dice_equipped"), true);
            return;
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, player) -> new CardInventoryMenu(containerId, inventory),
                Component.translatable("gui.astral_dice.card_inventory")
        ));
    }
}
