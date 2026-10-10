package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.shop.CardShopMenu;
import com.merlinkitsune.astral_dice.client.CardShopClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：卡牌商店「请求被拒」信号（2026-10-09 用户裁决 §4）。
 *
 * <p>服务端在 **余额不足**（买牌 / 转账）或 **物品栏已满**（双击快速移入）时下发；客户端把它交给
 * {@link CardShopClientState#notifyDeny(int)} 起一段 {@code DENY_BLINK_TICKS}(30t) 的倒计时，
 * {@code CardShopScreen} 据此把钱包余额数字做 3 次红白交替（5t 一相）。
 *
 * <p>⚠️ 只在**服务端真实拒绝**时下发（客户端不得自己猜）：客户端本地只用「余额 &lt; 价格」做即时过滤
 * （连拖拽都不起步），真正的结算一律以服务端为准。
 *
 * @param reason {@link CardShopMenu#DENY_NO_COIN}(0) = 余额不足；{@link CardShopMenu#DENY_INVENTORY_FULL}(1) = 物品栏已满
 */
public record CardShopDenyPayload(int reason) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CardShopDenyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "card_shop_deny"));

    public static final StreamCodec<ByteBuf, CardShopDenyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            CardShopDenyPayload::reason,
            CardShopDenyPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CardShopDenyPayload payload, IPayloadContext context) {
        // 纯客户端表现层：enqueueWork 保证在主线程改状态（与既有载荷同口径）
        context.enqueueWork(() -> CardShopClientState.notifyDeny(payload.reason()));
    }
}
