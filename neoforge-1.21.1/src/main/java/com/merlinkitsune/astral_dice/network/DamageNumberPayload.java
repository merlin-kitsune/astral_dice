package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public record DamageNumberPayload(int entityId, int bonusDamage, int color) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DamageNumberPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "damage_number"));

    public static final StreamCodec<ByteBuf, DamageNumberPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DamageNumberPayload::entityId,
            ByteBufCodecs.VAR_INT, DamageNumberPayload::bonusDamage,
            ByteBufCodecs.INT, DamageNumberPayload::color,
            DamageNumberPayload::new
    );

    /**
     * 向目标追踪客户端(含目标本人)发送跳数字。
     * 全部跳数字发送统一走本方法,避免各调用点重复实现分发规则。
     */
    public static void send(LivingEntity target, int bonusDamage, int color) {
        if (target.level().isClientSide()) return;
        var packet = new DamageNumberPayload(target.getId(), bonusDamage, color);
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
