package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * S2C：卡牌商店「二级转账界面」的候选玩家名单（**下标即转账目标下标**）。
 *
 * <p>每项 = UUID + 玩家名 + 星币余额（2026-10-08 二级面板定稿：行内要画真实皮肤头像与余额，
 * 故名单必须带 UUID 与余额；余额由服务端 {@code StarEngineEconomy.getBalance} 现算，客户端拿不到别人的）。
 * 名单 = 除自己外的全部在线玩家，顺序 = {@code PlayerList#getPlayers()} 的同一次遍历
 * （与 {@code CardShopMenu.candidateIds} 同源同序 ⇒ 客户端下标的语义 = 服务端下标）。
 */
public record CardShopTransferListPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CardShopTransferListPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_shop_transfer_list"));

    /** 单项：UUID（客户端据此取皮肤）+ 玩家名 + 星币余额 */
    public record Entry(UUID id, String name, long balance) {

        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
                Entry::id,
                ByteBufCodecs.STRING_UTF8,
                Entry::name,
                ByteBufCodecs.VAR_LONG,
                Entry::balance,
                Entry::new
        );
    }

    public static final StreamCodec<ByteBuf, CardShopTransferListPayload> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
            CardShopTransferListPayload::entries,
            CardShopTransferListPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
