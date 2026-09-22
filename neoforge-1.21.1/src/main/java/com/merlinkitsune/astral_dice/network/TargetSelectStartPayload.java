package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：目标选择会话开始。
 * 客户端据 {@code targetType}/{@code radius}/{@code durationTicks} 进入选择模式
 * （准星过滤、射线距离、HUD 提示），token 用于确认/取消回传校验；
 * {@code allowSelf} = 本次会话是否允许对自身使用（消费方接口
 * {@code com.merlinkitsune.astral_dice.target.SelfTargetable#allowSelf()} 的取值，
 * 当前 {@code ren_privilege} 与三张可自用效果牌动作（express_delivery / luxury_feast / berserk）为 true，其余动作 false），客户端据此选择 actionbar 口径与右键行为；
 * {@code holdToSelect} = 本会话由**主手手持物品**驱动且**没有倒计时**（消费方接口
 * {@code com.merlinkitsune.astral_dice.target.HoldToSelect} 的取值，当前 = 四张效果牌动作）——
 * 为真时 {@code durationTicks} 恒为 0，客户端不显示「（剩余 N 秒）」、提示口径改为「移出手持退出选择」，
 * 并在物品离开主手时自行退出选择模式。
 */
public record TargetSelectStartPayload(int token, int targetType, double radius, int durationTicks, String actionId,
                                      boolean allowSelf, boolean holdToSelect)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TargetSelectStartPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "target_select_start"));

    // ⚠️ 7 个字段超过 StreamCodec.composite 的重载上限(6)⇒ 手写 of(encoder, decoder);
    //    缓冲区类型必须是 FriendlyByteBuf(writeVarInt/readVarInt 是它的方法,netty ByteBuf 没有),
    //    它也是 RegistryFriendlyByteBuf 的父类 ⇒ playToClient 的签名照旧成立。
    public static final StreamCodec<FriendlyByteBuf, TargetSelectStartPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.token());
                buf.writeVarInt(payload.targetType());
                buf.writeDouble(payload.radius());
                buf.writeVarInt(payload.durationTicks());
                buf.writeUtf(payload.actionId());
                buf.writeBoolean(payload.allowSelf());
                buf.writeBoolean(payload.holdToSelect());
            },
            buf -> new TargetSelectStartPayload(buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                    buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
