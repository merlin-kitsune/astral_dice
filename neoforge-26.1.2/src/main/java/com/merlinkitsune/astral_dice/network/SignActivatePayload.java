package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;

public record SignActivatePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SignActivatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AstralDiceMod.MODID, "sign_activate"));

    public static final StreamCodec<FriendlyByteBuf, SignActivatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {},
            buf -> new SignActivatePayload()
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SignActivatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof Player player) {
                BaseSignItem.performSkillForCurio(player);
            }
        });
    }
}
