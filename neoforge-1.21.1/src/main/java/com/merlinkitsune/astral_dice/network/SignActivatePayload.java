package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.merlinkitsune.astral_dice.item.sign.BaseSignItem;
import com.merlinkitsune.astral_dice.item.sign.InkShadowSignManager;

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
            // 小猎手立牌「暗影之门」：放置窗口内的第二次按键先由立牌前置消费（放终点门），
            // 否则会被锁定态拦在 performSkill 之外、终点门永远放不出来。
            if (context.player() instanceof ServerPlayer serverPlayer
                    && InkShadowSignManager.tryPlaceEndFromKey(serverPlayer)) {
                return;
            }
            if (context.player() instanceof Player player) {
                BaseSignItem.performSkillForCurio(player);
            }
        });
    }
}
