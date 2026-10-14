package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.client.ChipShopClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 筹码商店 S→C：本次请求被拒 + 原因码（{@code ChipShopMenu.DENY_NO_COIN} = 0 /
 * {@code DENY_NO_REFRESH} = 1 / {@code DENY_INVENTORY_FULL} = 2；{@code DENY_NONE} = -1 不会发出来）。
 *
 * <p>客户端收到后起 30t 红闪（{@code ChipShopClientState#denyBlinkRed()}）：**花费文本**认 0/2、
 * **N/N 文本**认 1。1.21.1 蓝本 = {@code record(int reason)} + {@code StreamCodec.composite}；
 * 本线同一载荷、同一落点（{@code ChipShopClientState.notifyDeny(int)}，照抄本线既有的
 * {@code ModNetwork.CardShopDenyMessage}），协议 id 23，**顺序即协议**。
 *
 * <p>⚠️ 2026-10-14（需求 2）：原因码 2（{@code DENY_INVENTORY_FULL}）**不再是购买路径的结果** ——
 * 满包购买改为照常成交 + 装不下的筹码掉落在玩家身边 ⇒ 本载荷不会再携带 2。码 2 仍保留给屏幕侧的
 * 「花费文本」判定（与码 0 同相，守门 G23 按字面校验），并与 1.21.1 蓝本保持一致。
 */
public final class ChipShopDenyPayload {

    private final int reason;

    public ChipShopDenyPayload(int reason) {
        this.reason = reason;
    }

    /** 拒绝原因码（见类文档；服务端为唯一权威） */
    public int reason() {
        return this.reason;
    }

    public static void encode(ChipShopDenyPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.reason);
    }

    public static ChipShopDenyPayload decode(FriendlyByteBuf buf) {
        return new ChipShopDenyPayload(buf.readVarInt());
    }

    public static void handle(ChipShopDenyPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                ChipShopClientState.notifyDeny(msg.reason));
        ctx.get().setPacketHandled(true);
    }
}
