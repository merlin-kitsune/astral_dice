package com.merlinkitsune.astral_dice.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 事件效果卡（S2C，1.20.1 线）：与 1.21.1 的 EventCardPayload 同语义。 */
public final class EventCardMessage {
    private final ResourceLocation eventId;
    private final int durationTicks;

    public EventCardMessage(ResourceLocation eventId, int durationTicks) {
        this.eventId = eventId;
        this.durationTicks = durationTicks;
    }

    public static void encode(EventCardMessage msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.eventId);
        buf.writeVarInt(msg.durationTicks);
    }

    public static EventCardMessage decode(FriendlyByteBuf buf) {
        return new EventCardMessage(buf.readResourceLocation(), buf.readVarInt());
    }

    public static void handle(EventCardMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.EventCardOverlay.show(msg.eventId, msg.durationTicks);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
