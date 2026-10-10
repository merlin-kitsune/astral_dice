package com.merlinkitsune.astral_dice.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 大侦探立牌主动技能：<b>打开事件选择界面</b>（S2C，1.20.1 线）。
 *
 * <p>与 1.21.1 的 {@code EventChoiceStartPayload}（record + STREAM_CODEC）同语义：载荷 = 会话 token +
 * 两个事件选项（含各自展示用的 {@code defaultDurationTicks}）+ 等待期总 tick 数（{@code timeoutTicks}，
 * 与 {@code EventChoiceManager#CHOICE_TIMEOUT_TICKS} 同源）。两个选项**允许相同**（不做去重/平衡）。
 *
 * <p>⚠️ 本包只送「选项」，不含任何结算信息 —— 结算走服务端既有的
 * {@code AstralEventDispatcher#trigger} → {@code EventCardService#showThenRun}。
 *
 * <p>客户端处理经 {@code client/EventChoiceClientHandler} 转发，方法体里不出现
 * {@code Minecraft} / {@code EventChoiceScreen} 等客户端专属类型的 direct reference
 * （与 1.21.1 同口径；见该 helper 类头）。
 */
public final class EventChoiceStartMessage {
    private final int token;
    private final ResourceLocation firstEvent;
    private final ResourceLocation secondEvent;
    private final int firstDurationTicks;
    private final int secondDurationTicks;
    private final int timeoutTicks;

    public EventChoiceStartMessage(int token, ResourceLocation firstEvent, ResourceLocation secondEvent,
                                   int firstDurationTicks, int secondDurationTicks, int timeoutTicks) {
        this.token = token;
        this.firstEvent = firstEvent;
        this.secondEvent = secondEvent;
        this.firstDurationTicks = firstDurationTicks;
        this.secondDurationTicks = secondDurationTicks;
        this.timeoutTicks = timeoutTicks;
    }

    public static void encode(EventChoiceStartMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.token);
        buf.writeResourceLocation(msg.firstEvent);
        buf.writeResourceLocation(msg.secondEvent);
        buf.writeVarInt(msg.firstDurationTicks);
        buf.writeVarInt(msg.secondDurationTicks);
        buf.writeVarInt(msg.timeoutTicks);
    }

    public static EventChoiceStartMessage decode(FriendlyByteBuf buf) {
        return new EventChoiceStartMessage(buf.readVarInt(), buf.readResourceLocation(),
                buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EventChoiceStartMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.open(
                        msg.token, msg.firstEvent, msg.secondEvent,
                        msg.firstDurationTicks, msg.secondDurationTicks, msg.timeoutTicks);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
