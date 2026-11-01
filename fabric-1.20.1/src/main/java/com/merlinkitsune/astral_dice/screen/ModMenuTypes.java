package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.compat.curios.CuriosApi;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import com.merlinkitsune.astral_dice.platform.registry.DeferredRegister;
import com.merlinkitsune.astral_dice.platform.registry.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AstralDiceMod.MODID);

    public static final RegistryObject<MenuType<CardInventoryMenu>> CARD_INVENTORY = MENU_TYPES.register("card_inventory",
            () -> new MenuType<>(CardInventoryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** 卡牌商店菜单（逻辑批已完整实现；见 shop/CardShopMenu）。 */
    public static final RegistryObject<MenuType<com.merlinkitsune.astral_dice.shop.CardShopMenu>> CARD_SHOP =
            MENU_TYPES.register("card_shop",
                    () -> new MenuType<>(com.merlinkitsune.astral_dice.shop.CardShopMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** 筹码商店菜单（逻辑批已完整实现；见 shop/ChipShopMenu）。 */
    public static final RegistryObject<MenuType<com.merlinkitsune.astral_dice.shop.ChipShopMenu>> CHIP_SHOP =
            MENU_TYPES.register("chip_shop",
                    () -> new MenuType<>(com.merlinkitsune.astral_dice.shop.ChipShopMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** 选卡界面（B1-SCREEN-CHOICE）：带 extra data（token/slotCount/type），必须用 Fabric 的 ExtendedScreenHandlerType。 */
    public static final RegistryObject<MenuType<CardChoiceMenu>> CARD_CHOICE = MENU_TYPES.register("card_choice",
            () -> new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<CardChoiceMenu>(
                    CardChoiceMenu::new));

    /** 服务端打开卡牌栏(原 OpenCardInventoryPayload 的服务端逻辑;需佩戴骰子)。 */
    public static void openCardInventory(ServerPlayer serverPlayer) {
        var curios = CuriosApi.getCuriosInventory(serverPlayer);
        if (curios.isEmpty() || curios.get().findFirstCurio(DiceCurioItem::isDiceItem).isEmpty()) {
            com.merlinkitsune.astral_dice.network.ModNetwork.sendToPlayer(serverPlayer,
                    new com.merlinkitsune.astral_dice.network.ModNetwork.ActionBarMessage(
                            Component.translatable("msg.astral_dice.no_dice_equipped").withStyle(net.minecraft.ChatFormatting.RED),
                            com.merlinkitsune.starengine.component.GameplayConstants.ACTIONBAR_DURATION_TICKS));
            return;
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, player) -> new CardInventoryMenu(containerId, inventory),
                Component.translatable("gui.astral_dice.card_inventory")
        ));
    }
}
