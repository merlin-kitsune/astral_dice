package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.shop.CardShopMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：二级转账界面的「确认转账」（索引取 {@code CardShopTransferListPayload} 的同序名单）。
 *
 * <p>金额只允许 5..20（4 档 5/10/15/20 + 自定义）；服务端在
 * {@link CardShopMenu#handleTransfer} 内二次校验（含 60 秒玩家级冷却与余额）。
 */
public record CardShopTransferPayload(int targetIndex, int amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CardShopTransferPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_shop_transfer"));

    public static final StreamCodec<ByteBuf, CardShopTransferPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            CardShopTransferPayload::targetIndex,
            ByteBufCodecs.VAR_INT,
            CardShopTransferPayload::amount,
            CardShopTransferPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CardShopTransferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && serverPlayer.containerMenu instanceof CardShopMenu menu) {
                menu.handleTransfer(serverPlayer, payload.targetIndex(), payload.amount());
            }
        });
    }
}
