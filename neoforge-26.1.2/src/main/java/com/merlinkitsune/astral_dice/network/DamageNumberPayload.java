package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 跳数字(S→C)。
 *
 * <p>2026-09-26 起新增 <b>冻结坐标</b>({@code x/y/z}):由服务端 {@code combat/DamageNumberAggregator}
 * 在命中那一刻取好,客户端不再自行读实体坐标 ——
 * ① 满足「数字不跟随目标跳动」;② 聚合包在 tick 末尾才发,击杀那一下目标可能已被客户端移除,
 * 若靠客户端取实体就会整条丢数字。
 *
 * @param entityId     受击者实体 id(仅用作客户端分槽键的第一段)
 * @param bonusDamage  该组别本 tick 的总值(名字沿用历史,语义 = 实际扣血量之和)
 * @param color        组别颜色,同时是分槽键的第二段
 * @param x            冻结的世界坐标 X
 * @param y            冻结的世界坐标 Y
 * @param z            冻结的世界坐标 Z
 */
public record DamageNumberPayload(int entityId, int bonusDamage, int color,
                                  float x, float y, float z) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DamageNumberPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "damage_number"));

    public static final StreamCodec<ByteBuf, DamageNumberPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DamageNumberPayload::entityId,
            ByteBufCodecs.VAR_INT, DamageNumberPayload::bonusDamage,
            ByteBufCodecs.INT, DamageNumberPayload::color,
            ByteBufCodecs.FLOAT, DamageNumberPayload::x,
            ByteBufCodecs.FLOAT, DamageNumberPayload::y,
            ByteBufCodecs.FLOAT, DamageNumberPayload::z,
            DamageNumberPayload::new
    );

    /**
     * 向目标追踪客户端(含目标本人)发送跳数字。
     * 全部跳数字发送统一走本方法,避免各调用点重复实现分发规则。
     *
     * <p>⚠️ 唯一调用方是 {@code combat/DamageNumberAggregator} —— 各伤害点不再各自发包,
     * 否则仍会在客户端互相覆盖(见该类的类头)。
     */
    public static void send(LivingEntity target, int damage, int color, double x, double y, double z) {
        if (target.level().isClientSide()) return;
        var packet = new DamageNumberPayload(target.getId(), damage, color, (float) x, (float) y, (float) z);
        PacketDistributor.sendToPlayersTrackingEntity(target, packet);
        if (target instanceof ServerPlayer serverTarget) {
            PacketDistributor.sendToPlayer(serverTarget, packet);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
