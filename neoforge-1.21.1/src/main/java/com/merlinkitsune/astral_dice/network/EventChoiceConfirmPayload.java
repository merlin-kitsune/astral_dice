package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.event.EventChoiceManager;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 大侦探立牌主动技能：<b>确认所选事件</b>（客户端 → 服务端，C2S）。
 *
 * <p>服务端权威：token 必须与当前会话一致，且会话尚未进入结算（见 {@link EventChoiceManager#confirm}）。
 */
public record EventChoiceConfirmPayload(int token, int optionIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EventChoiceConfirmPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "event_choice_confirm"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, EventChoiceConfirmPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EventChoiceConfirmPayload::token,
                    ByteBufCodecs.VAR_INT, EventChoiceConfirmPayload::optionIndex,
                    EventChoiceConfirmPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EventChoiceConfirmPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                EventChoiceManager.confirm(player, payload.token(), payload.optionIndex());
            }
        });
    }
}
