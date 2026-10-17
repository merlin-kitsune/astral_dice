package com.merlinkitsune.astral_dice.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 「选卡界面」：<b>服务端权威关闭</b>（S2C，1.20.1 线，2026-10-17）。
 *
 * <p>带 token 只关本会话界面；客户端逻辑经 {@code client/CardChoiceClientHandler} 转发，
 * 本类方法体不出现任何 {@code net.minecraft.client.*} 符号（与 {@code EventChoiceCloseMessage} 同口径）。
 */
public final class CardChoiceCloseMessage {
    private final int token;

    public CardChoiceCloseMessage(int token) {
        this.token = token;
    }

    public static void encode(CardChoiceCloseMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.token);
    }

    public static CardChoiceCloseMessage decode(FriendlyByteBuf buf) {
        return new CardChoiceCloseMessage(buf.readVarInt());
    }

    public static void handle(CardChoiceCloseMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.CardChoiceClientHandler.closeIfToken(msg.token);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
