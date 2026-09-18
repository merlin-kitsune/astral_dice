package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端：目标选择会话开始。
 * 客户端据 {@code targetType}/{@code radius}/{@code durationTicks} 进入选择模式
 * （准星过滤、射线距离、HUD 提示），token 用于确认/取消回传校验；
 * {@code allowSelf} = 本次会话是否允许对自身使用（消费方接口
 * {@code com.merlinkitsune.astral_dice.target.SelfTargetable#allowSelf()} 的取值，
 * 当前无任何动作实现该接口 ⇒ 恒 false），客户端据此选择 actionbar 口径与右键行为。
 *
 * <p><b>26.1.2 平台改写点</b>：1.21.1 的 {@code net.minecraft.resources.ResourceLocation}
 * 在 26.1.2 改名为 {@code net.minecraft.resources.Identifier}（{@code fromNamespaceAndPath} 同名同签名）。
 * {@code StreamCodec.composite}（6 元重载）、{@code ByteBufCodecs.VAR_INT/DOUBLE/STRING_UTF8/BOOL}
 * 与 {@code ByteBuf} 作 codec 泛型参数在 26.1.2 逐字可用（同版本既有 {@code ActionBarPayload} 即用
 * {@code StreamCodec<ByteBuf, …>}）。
 */
public record TargetSelectStartPayload(int token, int targetType, double radius, int durationTicks, String actionId,
                                      boolean allowSelf)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TargetSelectStartPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "target_select_start"));

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
