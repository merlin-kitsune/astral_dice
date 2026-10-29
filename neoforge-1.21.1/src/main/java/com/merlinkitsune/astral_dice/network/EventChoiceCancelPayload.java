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
 * 客户端 → 服务端：<b>玩家退出事件选择界面</b>（2026-10-29 用户裁决 D1/D2 —— ESC 两段式确认后的退出、
 * 界面被别的屏幕顶掉（换维度 / 死亡 / 断线）都走这一条意图包）。
 *
 * <p>语义已于 2026-10-29 从「取消 / 不触发」改为「<b>退出即结算</b>」：服务端按 token 校验会话后调
 * {@code EventChoiceManager#exitWithoutChoice}，<b>强制触发第一个事件</b>（与超时 / 死亡 / 登出同一口径），
 * 而不是把这次主动技能白丢掉。
 * 幂等：会话已不在 / token 不符 / 已经结算时，服务端只记机器行后忽略 —— 退出包与服务端超时结算撞在
 * 同一 tick 时，先到的一方结算、后到的那条必然落在这里被忽略。
 *
 * <p>注意：服务端关屏走 {@code Minecraft#setScreen(null)}，它只回调 {@code Screen#removed()}、
 * **不回调** {@code onClose}；但该路径关屏前会先调 {@code EventChoiceScreen#markClosedByServer()}
 * （见 {@code EventChoiceClientHandler#closeIfToken}），使 {@code removed()} 的补发被抑制，
 * 故 {@link EventChoiceClosePayload} 那条路径**不会**补发本包。
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
                EventChoiceManager.exitWithoutChoice(player, payload.token(), "esc");
            }
        });
    }
}
