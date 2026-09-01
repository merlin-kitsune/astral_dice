package com.merlinkitsune.astral_dice.client;

import com.merlinkitsune.astral_dice.AstralDiceMod;
import com.merlinkitsune.astral_dice.network.OpenCardInventoryPayload;
import com.merlinkitsune.astral_dice.network.SignActivatePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
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

    // 目标选择器:键盘确认键(默认 Enter;选择期间 Enter 不再打开聊天,用于确认目标)
    public static final KeyMapping CONFIRM_TARGET_KEY = new KeyMapping(
            "key.astral_dice.confirm_target",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_ENTER,
            "key.categories.astral_dice"
    );

    @EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            while (ACTIVATE_SIGN_KEY.consumeClick()) {
                if (TargetSelectionClient.isActive()) {
                    // 目标选择期间再次按下主动技能键 = 取消选择(不触发立牌技能)
                    TargetSelectionClient.cancel("key");
                } else {
                    PacketDistributor.sendToServer(new SignActivatePayload());
                }
            }
            while (OPEN_CARD_INVENTORY_KEY.consumeClick()) {
                if (!TargetSelectionClient.isActive()) {
                    PacketDistributor.sendToServer(new OpenCardInventoryPayload());
                }
            }
        }
    }
}
