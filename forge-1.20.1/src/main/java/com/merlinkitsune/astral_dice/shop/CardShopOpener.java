package com.merlinkitsune.astral_dice.shop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;

/**
 * 打开卡牌商店菜单（方块交互与经商立牌主动技能共用的唯一入口）。
 *
 * <p>1.20.1 Forge 移植：与 1.21.1 版**逐字相同**（{@code Player#level().isClientSide()}、
 * {@code ServerPlayer#openMenu(MenuProvider)} 两线同名同签名）。
 */
public final class CardShopOpener {

    private CardShopOpener() {}

    public static void open(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> new CardShopMenu(containerId, inventory),
                    Component.translatable("gui.astral_dice.card_shop")
            ));
        }
    }
}
