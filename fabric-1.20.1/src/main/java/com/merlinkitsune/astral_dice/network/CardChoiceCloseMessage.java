package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.platform.api.distmarker.Dist;
import com.merlinkitsune.astral_dice.platform.fml.loading.FMLEnvironment;
import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * 「选卡界面」：<b>服务端权威关闭</b>（S2C，1:1 移植自 forge-1.20.1 同名文件，2026-10-17）。
 *
 * <p>带 token 只关本会话界面；客户端逻辑经 {@code client/CardChoiceClientHandler} 转发，
 * 本类方法体不出现任何 {@code net.minecraft.client.*} 符号。
 *
 * <p><b>平台差异（fabric-1.20.1）</b>：forge 线的 {@code api.distmarker.Dist} /
 * {@code fml.loading.FMLEnvironment} 走本线平台垫片
 * （{@code platform/api/distmarker/Dist}、{@code platform/fml/loading/FMLEnvironment}），
 * 语义与 forge 的 {@code FMLEnvironment.dist == Dist.CLIENT} 一致：客户端类引用只在客户端分支里解析，
 * 专用服务端不会加载 {@code client/CardChoiceClientHandler}（写法同 {@code BeamFxPayload}）。
 * 注册见 {@link ModNetwork#register()}（本线 id 21，对应 forge 的 id 25）。
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
