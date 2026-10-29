package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.event.EventChoiceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 大侦探立牌主动技能：<b>取消本次选择</b>（C2S，1.20.1 线；用户裁决 2026-10-29 D2）。
 *
 * <p>客户端按 ESC 关闭选择界面时发送：服务端**丢弃会话、不触发任何事件、不再下发倒计时**
 * （{@link EventChoiceManager#cancel}）；token 与会话不一致的包被丢弃。
 *
 * <p>协议纪律：与 {@link EventChoiceConfirmMessage} 同形（只带 token），注册 id 必须**追加在末尾**
 * ——顺序即协议，插队会让旧客户端错包。
 */
public final class EventChoiceCancelMessage {
    private final int token;

    public EventChoiceCancelMessage(int token) {
        this.token = token;
    }

    public static void encode(EventChoiceCancelMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.token);
    }

    public static EventChoiceCancelMessage decode(FriendlyByteBuf buf) {
        return new EventChoiceCancelMessage(buf.readVarInt());
    }

    public static void handle(EventChoiceCancelMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                EventChoiceManager.cancel(player, msg.token, "esc");
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
