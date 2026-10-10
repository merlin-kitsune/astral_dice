package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 大侦探立牌主动技能：<b>打开事件选择界面</b>（服务端 → 客户端，S2C）。
 *
 * <p>载荷 = 会话 token + 两个事件选项（含各自展示用的 {@code defaultDurationTicks}）+ 等待期总 tick 数
 * （{@code timeoutTicks}，与 {@code EventChoiceManager#CHOICE_TIMEOUT_TICKS} 同源，供客户端画倒计时）。
 * 两个选项**允许相同**（用户裁决 2026-10-10：不做去重、不做正/负/中性平衡）。
 *
 * <p>⚠️ 本包只送「选项」，**不含**任何结算信息 —— 结算完全走服务端既有的
 * {@code AstralEventDispatcher#trigger} → {@code EventCardService#showThenRun}。
 */
public record EventChoiceStartPayload(int token,
                                     ResourceLocation firstEvent,
                                     ResourceLocation secondEvent,
                                     int firstDurationTicks,
                                     int secondDurationTicks,
                                     int timeoutTicks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EventChoiceStartPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "event_choice_start"));

    /** 6 个字段仍可走 composite；用 RegistryFriendlyByteBuf 以复用 ResourceLocation 的标准编解码。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, EventChoiceStartPayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, EventChoiceStartPayload::token,
                    ResourceLocation.STREAM_CODEC, EventChoiceStartPayload::firstEvent,
                    ResourceLocation.STREAM_CODEC, EventChoiceStartPayload::secondEvent,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, EventChoiceStartPayload::firstDurationTicks,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, EventChoiceStartPayload::secondDurationTicks,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, EventChoiceStartPayload::timeoutTicks,
                    EventChoiceStartPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：打开事件选择界面（重新投递时直接顶替旧界面）。
     *
     * <p>⚠️ 本类**双端都会加载**（NeoForge 注册阶段即触碰），故界面逻辑必须经
     * {@code client/EventChoiceClientHandler} 转发：本方法体里**不得**出现
     * {@code Minecraft} / {@code EventChoiceScreen} 等客户端专属类型的 direct reference，
     * 否则专服会在 RuntimeDistCleaner 阶段直接崩服（2026-10-11 实机确证）。详见该 helper 类头。
     */
    public static void handle(EventChoiceStartPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                com.merlinkitsune.astral_dice.client.EventChoiceClientHandler.open(
                        payload.token(), payload.firstEvent(), payload.secondEvent(),
                        payload.firstDurationTicks(), payload.secondDurationTicks(), payload.timeoutTicks());
            }
        });
    }
}
