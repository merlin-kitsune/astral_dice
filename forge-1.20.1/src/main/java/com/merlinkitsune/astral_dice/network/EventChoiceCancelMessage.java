package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.event.EventChoiceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 大侦探立牌主动技能：<b>玩家退出事件选择界面</b>（C2S，1.20.1 线；用户裁决 2026-10-29 D1/D2 ——
 * ESC 两段式确认后的退出、界面被别的屏幕顶掉（换维度 / 死亡 / 断线）都走这一条意图包）。
 *
 * <p>语义已于 2026-10-29 从「取消 / 不触发」改为「<b>退出即结算</b>」：服务端按 token 校验会话后调
 * {@link EventChoiceManager#exitWithoutChoice}，<b>强制触发第一个事件</b>（与超时 / 死亡 / 登出同一口径），
 * 而不是把这次主动技能白丢掉。类名保留（改名会扩大两条线的 diff），只有语义位翻转。
 * 幂等：会话已不在 / token 不符 / 已经结算时，服务端只记机器行后忽略 —— 退出包与服务端超时结算撞在
 * 同一 tick 时，先到的一方结算、后到的那条必然落在这里被忽略。
 *
 * <p>注意：服务端关屏走 {@code Minecraft#setScreen(null)}，它只回调 {@code Screen#removed()}、
 * **不回调** {@code onClose}；但该路径关屏前会先调 {@code EventChoiceScreen#markClosedByServer()}
 * （见 {@code EventChoiceClientHandler#closeIfToken}），使 {@code removed()} 的补发被抑制，
 * 故 {@link EventChoiceCloseMessage} 那条路径**不会**补发本包。
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
                EventChoiceManager.exitWithoutChoice(player, msg.token, "esc");
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
