package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.ModNetwork.OpenCardInventoryMessage;
import com.merlinkitsune.astral_dice.network.ModNetwork.SignActivateMessage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import com.merlinkitsune.astral_dice.network.ModNetwork;
import org.lwjgl.glfw.GLFW;

public class KeyBindingSetup {
    public static final KeyMapping ACTIVATE_SIGN_KEY = new KeyMapping(
            "key.astral_dice.activate_sign",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.astral_dice"
    );

    public static final KeyMapping OPEN_CARD_INVENTORY_KEY = new KeyMapping(
            "key.astral_dice.open_card_inventory",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.astral_dice"
    );

    @Mod.EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            ClientDamageNumbers.tick();
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            while (ACTIVATE_SIGN_KEY.consumeClick()) {
                ModNetwork.sendToServer(new ModNetwork.SignActivateMessage());
            }
            while (OPEN_CARD_INVENTORY_KEY.consumeClick()) {
                ModNetwork.sendToServer(new ModNetwork.OpenCardInventoryMessage());
            }
        }
    }
}
