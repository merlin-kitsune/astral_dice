package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.shop.ChipShopMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：筹码商店「点确认按钮购买」——玩家点了条带最左的确认按钮，购买第 {@code offerIndex} 格候选。
 *
 * <p><b>只是意图，不是结算</b>：价格 / 余额 / 候选 / 落点一律以服务端为准，服务端在
 * {@link ChipShopMenu#handleBuy} 内二次校验（该格仍有候选 + 余额足够 + 物品栏有空位）后才扣款发货。
 * 客户端界面里**只有**确认按钮那一个分支会发这个包（拖拽 / 双击都没有通路，见计划表 §5）。
 *
 * @param offerIndex 候选格索引 0..{@link ChipShopMenu#OFFER_SLOTS}-1（条带中间三个筹码格）
 */
public record ChipShopBuyPayload(int offerIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChipShopBuyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_shop_buy"));

    public static final StreamCodec<ByteBuf, ChipShopBuyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ChipShopBuyPayload::offerIndex,
            ChipShopBuyPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChipShopBuyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && serverPlayer.containerMenu instanceof ChipShopMenu menu) {
                menu.handleBuy(serverPlayer, payload.offerIndex());
            }
        });
    }
}
