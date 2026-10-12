package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.shop.ChipShopMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：筹码商店「点刷新按钮」——消耗 1 次刷新、重抽三格候选（计划表 §6 / 裁决 2）。
 *
 * <p>无字段：请求本身不带任何客户端数值（次数与候选全在服务端附件里），客户端**不得**把本地读数当依据。
 * 次数为 0 时客户端**不发包**（只红闪），服务端同样兜底拒绝（回 {@link ChipShopDenyPayload#reason()} =
 * {@link ChipShopMenu#DENY_NO_REFRESH}）。
 */
public record ChipShopRefreshPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChipShopRefreshPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "chip_shop_refresh"));

    public static final StreamCodec<ByteBuf, ChipShopRefreshPayload> STREAM_CODEC =
            StreamCodec.unit(new ChipShopRefreshPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChipShopRefreshPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && serverPlayer.containerMenu instanceof ChipShopMenu menu) {
                menu.handleRefresh(serverPlayer);
            }
        });
    }
}
