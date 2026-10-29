package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.event.EventChoiceManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：<b>取消事件选择</b>（2026-10-29 用户裁决 D2 —— ESC = 放弃这次主动技能）。
 *
 * <p>服务端按 token 撤掉会话、关界面，**不触发**事件、不结算、不发倒计时；该次主动技能等同未使用。
 * 幂等：会话已不在 / token 不符 / 已经结算时，服务端只记机器行后忽略 —— ESC 与服务端超时结算撞在
 * 同一 tick 时，先到的一方结算、后到的那条必然落在这里被忽略。
 *
 * <p>注意：服务端关界面走 {@code Minecraft#setScreen(null)}，它只回调 {@code Screen#removed()}、
 * **不回调** {@code onClose}，故 {@link EventChoiceClosePayload} 那条路径**不会**补发本包。
 */
public record EventChoiceCancelPayload(int token) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EventChoiceCancelPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "event_choice_cancel"));

    public static final StreamCodec<ByteBuf, EventChoiceCancelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EventChoiceCancelPayload::token,
            EventChoiceCancelPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EventChoiceCancelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                EventChoiceManager.cancel(player, payload.token(), "esc");
            }
        });
    }
}
