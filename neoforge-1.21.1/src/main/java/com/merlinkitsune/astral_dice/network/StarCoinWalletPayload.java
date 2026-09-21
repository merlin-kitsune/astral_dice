package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.economy.StarCoinWalletActions;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：星币钱包的一次点击意图（动作序数）。
 *
 * <p>客户端只表达「玩家点了哪个按钮」，**不带任何数量/金额** —— 存多少、能取多少
 * 全部由服务端按真实物品栏与账本决定（见 {@link StarCoinWalletActions}）。
 * 序数越界（版本不一致或伪造包）时服务端静默丢弃，不报错也不产生任何状态改动。
 */
public record StarCoinWalletPayload(int action) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StarCoinWalletPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "star_coin_wallet"));

    public static final StreamCodec<ByteBuf, StarCoinWalletPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StarCoinWalletPayload::action,
            StarCoinWalletPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StarCoinWalletPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                StarCoinWalletActions.execute(player, StarCoinWalletActions.Action.byOrdinal(payload.action()));
            }
        });
    }
}
