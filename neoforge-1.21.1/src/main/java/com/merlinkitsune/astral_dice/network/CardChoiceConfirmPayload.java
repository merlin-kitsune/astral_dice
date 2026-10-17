package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.choice.CardChoiceManager;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「选卡界面」：<b>确认已选卡牌</b>（C2S，2026-10-17 用户裁决）。
 *
 * <p>载荷只有会话 token —— 所选卡牌**不由客户端上报**：服务端直接读
 * {@code CardChoiceMenu} 的后备容器（卡牌在打开期间已被真实移入该容器），
 * 因此客户端无法伪造「我选了哪张牌」。服务端在
 * {@link CardChoiceManager#confirm} 内做 token / 菜单 / 放满 / 类型四重校验。
 */
public record CardChoiceConfirmPayload(int token) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CardChoiceConfirmPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_choice_confirm"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, CardChoiceConfirmPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CardChoiceConfirmPayload::token,
                    CardChoiceConfirmPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CardChoiceConfirmPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                CardChoiceManager.confirm(player, payload.token());
            }
        });
    }
}
