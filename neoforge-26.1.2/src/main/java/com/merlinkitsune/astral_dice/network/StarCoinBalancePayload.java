package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.starenginelib.economy.StarCoinWalletState;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：钱包余额（余额条显示用）。
 *
 * <p>只承载**一个显示用数字**：客户端拿它渲染余额条，不参与任何判定。
 * 每玩家最多每秒一次、且只在值变化时发送（见 {@code StarCoinBalanceSync}）。
 *
 * <p>handler 落到 {@link StarCoinWalletState}（无客户端专有类型的一般类），
 * 因此本类在专用服务端也能安全加载。
 */
public record StarCoinBalancePayload(long balance) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StarCoinBalancePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(AstralDiceMod.MODID, "star_coin_balance"));

    public static final StreamCodec<ByteBuf, StarCoinBalancePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, StarCoinBalancePayload::balance,
            StarCoinBalancePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StarCoinBalancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> StarCoinWalletState.setBalance(payload.balance()));
    }
}
