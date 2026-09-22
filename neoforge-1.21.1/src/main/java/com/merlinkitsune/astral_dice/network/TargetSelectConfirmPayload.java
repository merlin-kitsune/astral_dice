package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.merlinkitsune.astral_dice.target.TargetSelectionManager;

/**
 * 客户端 → 服务端：确认目标选择（token + 目标实体 id）。
 * 服务端做权威校验（token/时效/类型/距离）后施加效果。
 */
public record TargetSelectConfirmPayload(int token, int targetId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TargetSelectConfirmPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "target_select_confirm"));

    public static final StreamCodec<ByteBuf, TargetSelectConfirmPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TargetSelectConfirmPayload::token,
            ByteBufCodecs.VAR_INT, TargetSelectConfirmPayload::targetId,
            TargetSelectConfirmPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TargetSelectConfirmPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TargetSelectionManager.confirm(player, payload.token(), payload.targetId());
            }
        });
    }
}
