package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.choice.CardChoiceManager;
import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * 「选卡界面」：<b>确认已选卡牌</b>（C2S，1:1 移植自 forge-1.20.1 同名文件，2026-10-17）。
 *
 * <p>载荷只有 token，所选卡牌由服务端直接读 {@code CardChoiceMenu} 的后备容器，
 * 客户端无法伪造选择结果。
 *
 * <p><b>平台差异（fabric-1.20.1）</b>：forge 线的 {@code NetworkEvent} →
 * 本线垫片 {@link com.merlinkitsune.astral_dice.platform.network.NetworkEvent}；
 * {@code getSender()/enqueueWork/setPacketHandled} 三件套语义与 forge 一致，方法体逐字照抄。
 * 注册见 {@link ModNetwork#register()}（本线 id 20，对应 forge 的 id 24；顺序即协议，只追加不插队）。
 */
public final class CardChoiceConfirmMessage {
    private final int token;

    public CardChoiceConfirmMessage(int token) {
        this.token = token;
    }

    public static void encode(CardChoiceConfirmMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.token);
    }

    public static CardChoiceConfirmMessage decode(FriendlyByteBuf buf) {
        return new CardChoiceConfirmMessage(buf.readVarInt());
    }

    public static void handle(CardChoiceConfirmMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CardChoiceManager.confirm(player, msg.token);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
