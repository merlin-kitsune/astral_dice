package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.shop.ChipShopMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 筹码商店 C→S：购买第 {@code offerIndex} 个候选（界面侧**唯一**购买出口；判据正本
 * {@code docs/chip-shop-logic-plan.md} §5）。
 *
 * <p><b>平台差异（forge-1.20.1）</b>：1.21.1 蓝本是 {@code CustomPacketPayload} record +
 * {@code StreamCodec.composite} + {@code PayloadRegistrar}，本线没有那套网络层（1.20.1 Forge 是
 * {@code SimpleChannel} + {@code FriendlyByteBuf} 六参 {@code encode/decode/handle}）⇒ 本类为**顶层类**
 * （守门 G16 要求屏幕侧出现字面 {@code new ChipShopBuyPayload(}），由 {@link ModNetwork#register()} 末尾
 * 追加注册（协议 id 21，**顺序即协议**）。收包侧校验口径逐字照抄本线既有的
 * {@code ModNetwork.CardShopBuyMessage}：{@code containerMenu instanceof ChipShopMenu} 才处理。
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
