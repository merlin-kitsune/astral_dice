package com.merlinkitsune.astral_dice.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 大侦探立牌主动技能：<b>事件选择界面倒计时同步</b>（S2C，1.20.1 线）。
 *
 * <p>与 1.21.1 的 {@code EventChoiceTickPayload} 同语义：由 {@code EventChoiceManager#tick} 在
 * 「剩余秒数发生变化」时下发（15 秒等待期共约 16 次），让标题条倒计时以**服务端剩余 tick 为唯一权威**。
 */
public final class EventChoiceTickMessage {
    private final int token;
    private final int remainingTicks;

    public EventChoiceTickMessage(int token, int remainingTicks) {
        this.token = token;
        this.remainingTicks = remainingTicks;
    }

    public static void encode(EventChoiceTickMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.token);
        buf.writeVarInt(msg.remainingTicks);
    }

    public static EventChoiceTickMessage decode(FriendlyByteBuf buf) {
        return new EventChoiceTickMessage(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EventChoiceTickMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.updateCountdown(msg.token, msg.remainingTicks);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
