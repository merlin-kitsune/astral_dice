package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import com.merlinkitsune.astral_dice.shop.ChipShopMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * 筹码商店 C→S：刷新候选（**无字段**；判据正本 {@code docs/chip-shop-logic-plan.md} §6）。
 *
 * <p><b>平台差异（fabric-1.20.1，基线 forge-1.20.1 同名文件）</b>：1.21.1 蓝本是空 record +
 * {@code StreamCodec.unit}；本线同一语义 —— 载荷为空、{@code encode} 不写任何字节（照抄本线
 * 既有无字段消息 {@code ModNetwork.SignActivateMessage} 的写法）。本次落线为协议 id 13，对应
 * forge 的 id 22，**顺序即协议**。
 */
public final class ChipShopRefreshPayload {

    /** 无字段（1.21.1 蓝本 = {@code StreamCodec.unit}）：不写任何字节 */
    public static void encode(ChipShopRefreshPayload msg, FriendlyByteBuf buf) {
    }

    public static ChipShopRefreshPayload decode(FriendlyByteBuf buf) {
        return new ChipShopRefreshPayload();
    }

    public static void handle(ChipShopRefreshPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.get().getSender();
            if (serverPlayer != null
                    && serverPlayer.containerMenu instanceof ChipShopMenu menu) {
                menu.handleRefresh(serverPlayer);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

