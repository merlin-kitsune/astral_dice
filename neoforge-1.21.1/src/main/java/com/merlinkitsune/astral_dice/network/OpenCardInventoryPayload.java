package com.merlinkitsune.astral_dice.network;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.item.dice.DiceCurioItem;
import com.merlinkitsune.astral_dice.screen.CardInventoryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.theillusivec4.curios.api.CuriosApi;

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
                var curios = CuriosApi.getCuriosInventory(serverPlayer);
                if (curios.isEmpty() || curios.get().findFirstCurio(DiceCurioItem::isDiceItem).isEmpty()) {
                    serverPlayer.displayClientMessage(Component.translatable("msg.astral_dice.no_dice_equipped"), true);
                    return;
                }
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, player) -> new CardInventoryMenu(containerId, inventory),
                        Component.translatable("gui.astral_dice.card_inventory")
                ));
            }
        });
    }
}
