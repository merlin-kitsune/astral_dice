package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.platform.api.distmarker.Dist;
import com.merlinkitsune.astral_dice.platform.fml.loading.FMLEnvironment;
import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * 大侦探立牌主动技能：<b>关闭事件选择界面</b>（S2C，1:1 移植自 forge-1.20.1 同名文件，2026-11-01）。
 *
 * <p>与 1.21.1 的 {@code EventChoiceClosePayload} 同语义：由服务端在「已确认」与
 * 「强制触发第一个选项」（超时 / 死亡 / 登出）两条路径上发出；带 token 避免误关玩家后来打开的**其它**界面。
 *
 * <p><b>平台差异（fabric-1.20.1）</b>：forge 线的 {@code api.distmarker.Dist} /
 * {@code fml.loading.FMLEnvironment} / {@code network.NetworkEvent} 全部走本线平台垫片
 * （写法规格见同目录 {@code CardChoiceCloseMessage}）；{@code FMLEnvironment.dist == Dist.CLIENT}
 * 语义与 forge 一致，专用服务端不会加载 {@code client/EventChoiceClientHandler}。
 * 注册见 {@link ModNetwork#register()}（本线 id 25，对应 forge 的 id 19）。
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
