package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：目标选择会话开始。
 * 客户端据 {@code targetType}/{@code radius}/{@code durationTicks} 进入选择模式
 * （准星过滤、射线距离、HUD 提示），token 用于确认/取消回传校验；
 * {@code allowSelf} = 本次会话是否允许对自身使用（消费方接口
 * {@code com.merlinkitsune.astral_dice.target.SelfTargetable#allowSelf()} 的取值，
 * 当前唯一实现者 {@code ren_privilege}=true，其余动作 false），客户端据此选择 actionbar 口径与右键行为。
 */
public record TargetSelectStartPayload(int token, int targetType, double radius, int durationTicks, String actionId,
                                      boolean allowSelf)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TargetSelectStartPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "target_select_start"));

    public static final StreamCodec<ByteBuf, TargetSelectStartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TargetSelectStartPayload::token,
            ByteBufCodecs.VAR_INT, TargetSelectStartPayload::targetType,
            ByteBufCodecs.DOUBLE, TargetSelectStartPayload::radius,
            ByteBufCodecs.VAR_INT, TargetSelectStartPayload::durationTicks,
            ByteBufCodecs.STRING_UTF8, TargetSelectStartPayload::actionId,
            ByteBufCodecs.BOOL, TargetSelectStartPayload::allowSelf,
            TargetSelectStartPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
