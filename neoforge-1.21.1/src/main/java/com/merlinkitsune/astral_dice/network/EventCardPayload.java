package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 事件效果卡（S2C）：让客户端弹出「事件卡」动画。
 *
 * <p>动画 = 进场 0.5s → 展示 0.5s → 收起 0.5s（scale 与 alpha 同曲线）；**事件真正结算在服务端
 * 弹卡开始后 40t（2.0s）执行，并同刻播放音效**（用户裁决 2026-10-10）。
 *
 * @param eventId       事件 id（客户端据此取图标 `textures/gui/event/<path>.png` 与语言键 `event.astral_dice.<path>.name`）
 * @param durationTicks 计时器时长（0 = 无计时器 ⇒ 不画第二行）
 * @param tier          当前事件阶级（>0 时图标取 `<id>_<suffix>`，用于「调查阶段」4 张分阶图）
 */
public record EventCardPayload(ResourceLocation eventId, int durationTicks, int tier) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EventCardPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "event_card"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EventCardPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, EventCardPayload::eventId,
            ByteBufCodecs.VAR_INT, EventCardPayload::durationTicks,
            ByteBufCodecs.VAR_INT, EventCardPayload::tier,
            EventCardPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
