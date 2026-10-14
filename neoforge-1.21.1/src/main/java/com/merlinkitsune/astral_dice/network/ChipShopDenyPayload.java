package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.client.ChipShopClientState;
import com.merlinkitsune.astral_dice.shop.ChipShopMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：筹码商店「请求被拒」信号（计划表 §4）。
 *
 * <p>服务端在**余额不足** / **刷新次数为 0** 时下发；客户端把它交给
 * {@link ChipShopClientState#notifyDeny(int)}，起一段 {@code DENY_BLINK_TICKS}(30t) 的倒计时，
 * {@code ChipShopScreen} 据此把**花费文本**（余额不足）或 **N/N 刷新文本**（次数为 0）
 * 做 3 次红白交替（5t 一相）。
 *
 * <p>⚠️ 只在**服务端真实拒绝**时下发（客户端不得自己猜）：客户端只用本地读数做即时过滤
 * （次数为 0 时连包都不发），真正的结算一律以服务端为准。
 *
 * <p>⚠️ 2026-10-14（需求 2）：{@link ChipShopMenu#DENY_INVENTORY_FULL}(2) **不再是购买路径的结果**
 * （满包购买改为照常成交 + 装不下的筹码掉落在玩家身边）⇒ 本载荷不会再携带 2。常量保留：屏幕侧
 * 「花费文本」仍按 0/2 两码判定（与码 0 同相，守门 G23 按字面校验），且该码可留给将来其它「满包」类拒绝。
 *
 * @param reason {@link ChipShopMenu#DENY_NO_COIN}(0) = 余额不足；
 *               {@link ChipShopMenu#DENY_NO_REFRESH}(1) = 刷新次数为 0；
 *               {@link ChipShopMenu#DENY_INVENTORY_FULL}(2) = 物品栏已满（保留码，购买路径不再下发）
 */
public record ChipShopDenyPayload(int reason) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChipShopDenyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_shop_deny"));

    public static final StreamCodec<ByteBuf, ChipShopDenyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ChipShopDenyPayload::reason,
            ChipShopDenyPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChipShopDenyPayload payload, IPayloadContext context) {
        // 纯客户端表现层：enqueueWork 保证在主线程改状态（与既有载荷同口径）
        context.enqueueWork(() -> ChipShopClientState.notifyDeny(payload.reason()));
    }
}
