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
 * C2S：卡牌商店「拖拽购买」——玩家把货架第 {@code shelfIndex} 格的卡拖到玩家物品栏第 {@code targetSlot} 格并松手。
 *
 * <p><b>只是意图，不是结算</b>：售价 / 余额 / 槽内容一律以服务端为准，服务端在
 * {@link CardShopMenu#handleBuy} 内二次校验（槽仍在售 + 目标格为空 + 余额足够）后才扣款发货。
 *
 * @param shelfIndex 货架格索引 0..{@link CardShopMenu#SHELF_SIZE}-1（屏幕左侧 6 个卡框）
 * @param targetSlot 目标格 = <b>玩家物品栏索引</b> 0..35（0..8 = 快捷栏，9..35 = 主物品栏），
 *                   与 {@link CardShopMenu#playerSlotOffset(int)} 的入参同口径；
 *                   或 {@link CardShopMenu#QUICK_MOVE_SLOT}(-1) = 双击「快速移入物品栏」，
 *                   由服务端挑第一个空物品栏格（满包 ⇒ 拒绝 + 触发「背包已满」提示与余额闪烁）
 */
public record CardShopBuyPayload(int shelfIndex, int targetSlot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CardShopBuyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_shop_buy"));

    public static final StreamCodec<ByteBuf, CardShopBuyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            CardShopBuyPayload::shelfIndex,
            ByteBufCodecs.VAR_INT,
            CardShopBuyPayload::targetSlot,
            CardShopBuyPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CardShopBuyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && serverPlayer.containerMenu instanceof CardShopMenu menu) {
                menu.handleBuy(serverPlayer, payload.shelfIndex(), payload.targetSlot());
            }
        });
    }
}
