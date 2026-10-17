package com.merlinkitsune.astral_dice.screen;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.item.CuriosCompat;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AstralDiceMod.MODID);

    public static final RegistryObject<MenuType<CardInventoryMenu>> CARD_INVENTORY = MENU_TYPES.register("card_inventory",
            () -> new MenuType<>(CardInventoryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** 卡牌商店菜单(方块交互与经商立牌主动技能共用;见 shop/CardShopMenu)。 */
    public static final RegistryObject<MenuType<com.merlinkitsune.astral_dice.shop.CardShopMenu>> CARD_SHOP =
            MENU_TYPES.register("card_shop",
                    () -> new MenuType<>(com.merlinkitsune.astral_dice.shop.CardShopMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** 筹码商店菜单(逻辑批已完整实现;见 shop/ChipShopMenu)。 */
    public static final RegistryObject<MenuType<com.merlinkitsune.astral_dice.shop.ChipShopMenu>> CHIP_SHOP =
            MENU_TYPES.register("chip_shop",
                    () -> new MenuType<>(com.merlinkitsune.astral_dice.shop.ChipShopMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /**
     * 通用「选卡界面」菜单（2026-10-17）：槽位数（1/3）与卡牌类型由**打开参数**决定，
     * 必须走 {@link IForgeMenuType#create} + extra data（token + slotCount + type.ordinal()），
     * 不能用 2 参 {@code MenuType::new}（否则客户端会构造出 0 槽 / 类型未知的菜单）。
     */
    public static final RegistryObject<MenuType<CardChoiceMenu>> CARD_CHOICE =
            MENU_TYPES.register("card_choice", () -> IForgeMenuType.create(CardChoiceMenu::new));

    /** 服务端打开卡牌栏(原 OpenCardInventoryPayload 的服务端逻辑;需佩戴骰子)。 */
    public static void openCardInventory(ServerPlayer serverPlayer) {
        var curios = CuriosCompat.getCuriosInventory(serverPlayer);
        if (curios.isEmpty() || curios.get().findFirstCurio(DiceCurioItem::isDiceItem).isEmpty()) {
            com.merlinkitsune.astral_dice.network.ModNetwork.sendToPlayer(serverPlayer,
                    new com.merlinkitsune.astral_dice.network.ModNetwork.ActionBarMessage(
                            Component.translatable("msg.astral_dice.no_dice_equipped").withStyle(net.minecraft.ChatFormatting.RED),
                            com.merlinkitsune.starenginelib.component.GameplayConstants.ACTIONBAR_DURATION_TICKS));
            return;
        }
        serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, player) -> new CardInventoryMenu(containerId, inventory),
                Component.translatable("gui.astral_dice.card_inventory")
        ));
    }
}
