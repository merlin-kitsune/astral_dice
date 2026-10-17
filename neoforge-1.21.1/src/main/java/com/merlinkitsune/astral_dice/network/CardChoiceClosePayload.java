package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 「选卡界面」：<b>服务端权威关闭</b>（S2C，2026-10-17 用户裁决）。
 *
 * <p>带 token 是为了只关**本会话**的界面（避免误关玩家后来打开的其它界面）。
 * 与事件选择界面同一先例：本类双端都会加载，故客户端逻辑必须经
 * {@code client/CardChoiceClientHandler} 转发，本方法体里不得出现任何
 * {@code net.minecraft.client.*} 的 direct reference（详见
 * {@code network/EventChoiceStartPayload#handle} 的类注释）。
 */
public record CardChoiceClosePayload(int token) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CardChoiceClosePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_choice_close"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, CardChoiceClosePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CardChoiceClosePayload::token,
                    CardChoiceClosePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CardChoiceClosePayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.CardChoiceClientHandler.closeIfToken(payload.token());
            }
        });
    }
}
