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
 * 客户端 → 服务端：取消目标选择（Esc / 再次按主动技能键 / 第三方界面打开时）。
 * 服务端立即清除会话，允许玩家立刻重新触发。
 */
public record TargetSelectCancelPayload(int token) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TargetSelectCancelPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "target_select_cancel"));

    public static final StreamCodec<ByteBuf, TargetSelectCancelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TargetSelectCancelPayload::token,
            TargetSelectCancelPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TargetSelectCancelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TargetSelectionManager.cancel(player, payload.token());
            }
        });
    }
}
