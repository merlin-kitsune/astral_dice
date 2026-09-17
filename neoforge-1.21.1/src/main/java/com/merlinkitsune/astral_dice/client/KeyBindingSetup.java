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

    // 目标选择器无独立键盘确认键：确认 = 鼠标左键、取消 = 右键+潜行 / ESC 菜单
    // （Create 强力胶式语义；旧的 Enter 确认键与客户端键盘拦截 Mixin 已删除）

    @EventBusSubscriber(modid = AstralDiceMod.MODID, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            while (ACTIVATE_SIGN_KEY.consumeClick()) {
                if (TargetSelectionClient.isActive()) {
                    // 目标选择期间再次按下主动技能键 = 取消选择(不触发立牌技能)
                    TargetSelectionClient.logPrompt("j", "cancel");
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
