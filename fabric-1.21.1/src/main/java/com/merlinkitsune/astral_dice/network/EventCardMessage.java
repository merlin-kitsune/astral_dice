package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.platform.api.distmarker.Dist;
import com.merlinkitsune.astral_dice.platform.fml.loading.FMLEnvironment;
import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * 事件效果卡（S2C，1:1 移植自 forge-1.20.1 同名文件，2026-11-01）：与 1.21.1 的 EventCardPayload 同语义。
 *
 * <p><b>平台差异（fabric-1.20.1）</b>：forge 线的 {@code NetworkEvent} 走本线垫片
 * {@link com.merlinkitsune.astral_dice.platform.network.NetworkEvent}；{@code api.distmarker.Dist} /
 * {@code fml.loading.FMLEnvironment} 走本线平台垫片（写法规格见 {@code network/CardChoiceCloseMessage}）。
 * {@code enqueueWork/getSender/setPacketHandled} 三件套语义与 forge 一致，方法体逐字照抄。
 *
 * <p>注册见 {@link ModNetwork#register()}（本线 id 22，对应 forge 的 id 16）。
 */
public final class EventCardMessage {
    private final ResourceLocation eventId;
    private final int durationTicks;
    private final int tier;

    public EventCardMessage(ResourceLocation eventId, int durationTicks, int tier) {
        this.eventId = eventId;
        this.durationTicks = durationTicks;
        this.tier = tier;
    }

    public static void encode(EventCardMessage msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.eventId);
        buf.writeVarInt(msg.durationTicks);
        buf.writeVarInt(msg.tier);
    }

    public static EventCardMessage decode(FriendlyByteBuf buf) {
        return new EventCardMessage(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EventCardMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.EventCardOverlay.show(msg.eventId, msg.durationTicks, msg.tier);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
