package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.event.EventChoiceManager;
import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * 大侦探立牌主动技能：<b>确认所选事件</b>（C2S，1:1 移植自 forge-1.20.1 同名文件，2026-11-01）。
 *
 * <p>与 1.21.1 的 {@code EventChoiceConfirmPayload} 同语义。服务端权威：token 必须与当前会话一致，
 * 且会话尚未进入结算（见 {@link EventChoiceManager#confirm}）；重复包被丢弃。
 *
 * <p><b>平台差异（fabric-1.20.1）</b>：只把 forge 的 {@code network.NetworkEvent} 换成本线垫片
 * {@link com.merlinkitsune.astral_dice.platform.network.NetworkEvent}；{@code getSender()/enqueueWork/
 * setPacketHandled} 语义一致，方法体逐字照抄。注册见 {@link ModNetwork#register()}
 * （本线 id 24，对应 forge 的 id 18）。
 */
public final class EventChoiceConfirmMessage {
    private final int token;
    private final int optionIndex;

    public EventChoiceConfirmMessage(int token, int optionIndex) {
        this.token = token;
        this.optionIndex = optionIndex;
    }

    public static void encode(EventChoiceConfirmMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.token);
        buf.writeVarInt(msg.optionIndex);
    }

    public static EventChoiceConfirmMessage decode(FriendlyByteBuf buf) {
        return new EventChoiceConfirmMessage(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EventChoiceConfirmMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                EventChoiceManager.confirm(player, msg.token, msg.optionIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
