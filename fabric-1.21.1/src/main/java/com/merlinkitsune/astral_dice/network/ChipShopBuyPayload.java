package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.platform.network.NetworkEvent;
import com.merlinkitsune.astral_dice.shop.ChipShopMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * 筹码商店 C→S：购买第 {@code offerIndex} 个候选（界面侧**唯一**购买出口；判据正本
 * {@code docs/chip-shop-logic-plan.md} §5）。
 *
 * <p><b>平台差异（fabric-1.20.1，基线 forge-1.20.1 同名文件）</b>：1.21.1 蓝本是
 * {@code CustomPacketPayload} record + {@code StreamCodec.composite} + {@code PayloadRegistrar}，
 * 本线既无那套网络层也无 1.20.5+ 的 payload 注册表 ⇒ 与 forge 线同形：**顶层类** +
 * {@link ModNetwork#register()} 末尾追加注册（本线本次落线为协议 id 12，对应 forge 的 id 21；
 * **顺序即协议**，绝对号随其它批次追加顺序顺延，对照表见 {@code porting/fabric-1.20.1/B1-NETWORK-REPORT.md}）。
 * 收包侧校验口径逐字照抄本线既有的 {@code ModNetwork.StarCoinWalletMessage}：
 * {@code containerMenu instanceof ChipShopMenu} 才处理。
 */
public final class ChipShopBuyPayload {

    private final int offerIndex;

    public ChipShopBuyPayload(int offerIndex) {
        this.offerIndex = offerIndex;
    }

    /** 候选格序号（0..{@code ChipShopMenu.OFFER_SLOTS - 1}；越界由 {@code handleBuy} 二次校验后拒绝） */
    public int offerIndex() {
        return this.offerIndex;
    }

    public static void encode(ChipShopBuyPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.offerIndex);
    }

    public static ChipShopBuyPayload decode(FriendlyByteBuf buf) {
        return new ChipShopBuyPayload(buf.readVarInt());
    }

    public static void handle(ChipShopBuyPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.get().getSender();
            if (serverPlayer != null
                    && serverPlayer.containerMenu instanceof ChipShopMenu menu) {
                menu.handleBuy(serverPlayer, msg.offerIndex);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

