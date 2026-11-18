package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.screen.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenCardInventoryPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenCardInventoryPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "open_card_inventory"));

    public static final StreamCodec<FriendlyByteBuf, OpenCardInventoryPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {},
            buf -> new OpenCardInventoryPayload()
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCardInventoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ModMenuTypes.openCardInventory(serverPlayer);
            }
        });
    }
}
