package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 大侦探立牌主动技能：<b>事件选择界面倒计时同步</b>（服务端 → 客户端，S2C）。
 *
 * <p>由 {@code EventChoiceManager#tick} 在「剩余秒数发生变化」时下发（15 秒等待期共约 16 次），
 * 让标题条倒计时**以服务端剩余 tick 为唯一权威**（2026-10-11 用户裁决：别各算一套）。
 *
 * <p>⚠️ 本类**双端都会加载**（NeoForge 注册阶段即触碰），故界面更新必须经
 * {@code client/EventChoiceClientHandler} 转发：本方法体里**不得**出现
 * {@code Minecraft} / {@code EventChoiceScreen} 等客户端专属类型的 direct reference，
 * 否则专服会在 {@code RuntimeDistCleaner} 阶段直接崩服（详见该 helper 类头）。
 */
public record EventChoiceTickPayload(int token, int remainingTicks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EventChoiceTickPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "event_choice_tick"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, EventChoiceTickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EventChoiceTickPayload::token,
                    ByteBufCodecs.VAR_INT, EventChoiceTickPayload::remainingTicks,
                    EventChoiceTickPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EventChoiceTickPayload payload,
                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.updateCountdown(
                        payload.token(), payload.remainingTicks());
            }
        });
    }
}
