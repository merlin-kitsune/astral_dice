package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.platform.api.distmarker.Dist;
import com.merlinkitsune.astral_dice.platform.fml.loading.FMLEnvironment;
import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * 大侦探立牌主动技能：<b>事件选择界面倒计时同步</b>（S2C，1:1 移植自 forge-1.20.1 同名文件，2026-11-01）。
 *
 * <p>与 1.21.1 的 {@code EventChoiceTickPayload} 同语义：由 {@code EventChoiceManager#tick} 在
 * 「剩余秒数发生变化」时下发（15 秒等待期共约 16 次），让标题条倒计时以**服务端剩余 tick 为唯一权威**。
 *
 * <p><b>平台差异（fabric-1.20.1）</b>：{@code NetworkEvent} / {@code Dist} / {@code FMLEnvironment}
 * 均走本线平台垫片（写法规格见 {@code network/CardChoiceCloseMessage}），方法体逐字照抄。
 * 注册见 {@link ModNetwork#register()}（本线 id 26，对应 forge 的 id 20）。
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
