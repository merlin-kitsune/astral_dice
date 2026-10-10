package com.merlinkitsune.astral_dice.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 大侦探立牌主动技能：<b>关闭事件选择界面</b>（S2C，1.20.1 线）。
 *
 * <p>与 1.21.1 的 {@code EventChoiceClosePayload} 同语义：由服务端在「已确认」与
 * 「强制触发第一个选项」（超时 / 死亡 / 登出）两条路径上发出；带 token 避免误关玩家后来打开的**其它**界面。
 */
public final class EventChoiceCloseMessage {
    private final int token;

    public EventChoiceCloseMessage(int token) {
        this.token = token;
    }

    public static void encode(EventChoiceCloseMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.token);
    }

    public static EventChoiceCloseMessage decode(FriendlyByteBuf buf) {
        return new EventChoiceCloseMessage(buf.readVarInt());
    }

    public static void handle(EventChoiceCloseMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.closeIfToken(msg.token);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
