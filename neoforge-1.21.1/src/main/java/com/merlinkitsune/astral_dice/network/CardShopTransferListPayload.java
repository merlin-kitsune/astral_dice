package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** S2C：卡牌商店「二级转账界面」的候选玩家名列表（索引即转账目标索引） */
public record CardShopTransferListPayload(List<String> names) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CardShopTransferListPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_shop_transfer_list"));

    public static final StreamCodec<ByteBuf, CardShopTransferListPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
            CardShopTransferListPayload::names,
            CardShopTransferListPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
