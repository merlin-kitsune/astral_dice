package com.merlinkitsune.astral_dice.shop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;

/** 打开卡牌商店菜单（方块交互与经商立牌主动技能共用的唯一入口） */
public final class CardShopOpener {

    private CardShopOpener() {}

    /** 方块交互入口（任务T §3 起：方块与主动技能区分结算 —— 方块沿用「每批至多一次」的封堵） */
    public static void open(Player player) {
        open(player, false);
    }

    /**
     * 打开卡牌商店菜单的唯一服务端入口。
     *
     * @param viaActiveSkill true = 由经商立牌主动技能「网络购物」打开（任务T §3 用户裁决）：
     *                       无视本批卡是否已领取，进店**必定**获得 1 星币，并把本批状态置为已领取
     *                       （与商店本身的那一次**不叠加**：两条路径互斥，一次开店只发 1 枚）
     */
    public static void open(Player player, boolean viaActiveSkill) {
        if (player.level().isClientSide()) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, p) -> new CardShopMenu(containerId, inventory, viaActiveSkill),
                    Component.translatable("gui.astral_dice.card_shop")
            ));
        }
    }
}
