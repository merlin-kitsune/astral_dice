package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.choice.CardChoiceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 「选卡界面」：<b>确认已选卡牌</b>（C2S，1.20.1 线，2026-10-17）。
 *
 * <p>与 1.21.1 的 {@code CardChoiceConfirmPayload} 同语义：载荷只有 token，所选卡牌由服务端
 * 直接读 {@code CardChoiceMenu} 的后备容器，客户端无法伪造选择结果。
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
