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

/**
 * 末影骰子·不死图腾动画:客户端以末影骰子图标播放原版不死图腾同款动画
 * (图腾粒子 + 音效 + 手持高亮图标,图标替换为末影骰子)。
 */
public record EnderDieTotemPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnderDieTotemPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "ender_die_totem"));

    public static final StreamCodec<ByteBuf, EnderDieTotemPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EnderDieTotemPayload::entityId,
            EnderDieTotemPayload::new
    );

    /** 向目标及所有追踪客户端广播图腾动画(含目标本人) */
    public static void send(LivingEntity target) {
        if (target.level().isClientSide()) return;
        var packet = new EnderDieTotemPayload(target.getId());
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
